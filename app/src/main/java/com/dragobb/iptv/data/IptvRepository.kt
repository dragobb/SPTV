package com.dragobb.iptv.data

import android.content.Context
import android.telephony.TelephonyManager
import com.dragobb.iptv.data.local.ChannelDao
import com.dragobb.iptv.data.local.ChannelEntity
import com.dragobb.iptv.ui.models.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.Locale

class IptvRepository(private val context: Context, private val channelDao: ChannelDao) {

    fun getDetectedCountryCode(): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val countryCode = tm.networkCountryIso.lowercase()
        return if (countryCode.isNotBlank()) countryCode else Locale.getDefault().country.lowercase()
    }

    fun getCachedChannels(): Flow<List<Channel>> {
        return channelDao.getAllChannels().map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun refreshChannels(overrideCountryCode: String? = null, customUrls: List<String> = emptyList()) = withContext(Dispatchers.IO) {
        val countryCode = (overrideCountryCode ?: getDetectedCountryCode()).lowercase()

        val sources = mutableListOf<Pair<String, String?>>(
            "https://iptv-org.github.io/iptv/countries/$countryCode.m3u" to null
        )

        if (countryCode == "ph") {
            sources.add("https://raw.githubusercontent.com/Harleythetech/IPHTV/refs/heads/main/ph.m3u" to "PHILIPPINES")
        }

        // Add custom playlists
        customUrls.forEach { url ->
            sources.add(url to "Custom Playlist")
        }

        val remoteChannels = sources.map { (url, forcedCategory) ->
            async {
                try {
                    val content = URL(url).readText()
                    val parsed = parseM3U(content, countryCode, forcedCategory)

                    // Logic: If content isn't a playlist but is a direct stream link (m3u8)
                    if (parsed.isEmpty() && (url.contains(".m3u8", ignoreCase = true) || content.contains("#EXT-X-TARGETDURATION"))) {
                        val fileName = url.substringAfterLast("/").substringBefore("?").removeSuffix(".m3u8")
                        listOf(Channel(
                            id = url.hashCode().toString(),
                            name = if (fileName.isNotBlank()) fileName.replace("_", " ").uppercase() else "Custom Stream",
                            logoUrl = null,
                            streamUrl = url,
                            category = forcedCategory ?: "Custom",
                            country = countryCode.uppercase()
                        ))
                    } else {
                        parsed
                    }
                } catch (e: Exception) {
                    // Fallback for direct links that fail readText (some stream servers)
                    if (url.contains(".m3u8", ignoreCase = true) || url.contains("://")) {
                        val fileName = url.substringAfterLast("/").substringBefore("?").removeSuffix(".m3u8")
                        listOf(Channel(
                            id = url.hashCode().toString(),
                            name = if (fileName.isNotBlank()) fileName.replace("_", " ").uppercase() else "Direct Stream",
                            logoUrl = null,
                            streamUrl = url,
                            category = forcedCategory ?: "Custom",
                            country = countryCode.uppercase()
                        ))
                    } else {
                        emptyList<Channel>()
                    }
                }
            }
        }.awaitAll().flatten().distinctBy { it.streamUrl }

        if (remoteChannels.isNotEmpty()) {
            channelDao.clearAll()
            channelDao.insertChannels(remoteChannels.map { it.toEntity() })
        }
    }

    private fun parseM3U(m3uContent: String, countryCode: String, forcedCategory: String? = null): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = m3uContent.lines()
        var currentInfo: String? = null

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("#EXTINF:")) {
                currentInfo = trimmedLine
            } else if ((trimmedLine.startsWith("http") || trimmedLine.contains("://")) && currentInfo != null) {
                val name = currentInfo.substringAfterLast(",").trim()
                val logo = Regex("""tvg-logo="([^"]*)"""").find(currentInfo)?.groupValues?.get(1)
                val group = forcedCategory ?: Regex("""group-title="([^"]*)"""").find(currentInfo)?.groupValues?.get(1) ?: "General"

                val uniqueId = (name + trimmedLine).hashCode().toString()

                channels.add(
                    Channel(
                        id = uniqueId,
                        name = name,
                        logoUrl = logo,
                        streamUrl = trimmedLine,
                        category = group,
                        country = countryCode.uppercase()
                    )
                )
                currentInfo = null
            }
        }
        return channels
    }

    private fun Channel.toEntity() = ChannelEntity(id, name, logoUrl, streamUrl, category, country)
    private fun ChannelEntity.toModel() = Channel(id, name, logoUrl, streamUrl, category, country)
}