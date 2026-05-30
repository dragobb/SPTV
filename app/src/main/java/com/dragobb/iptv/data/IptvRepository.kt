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

    suspend fun refreshChannels(overrideCountryCode: String? = null) = withContext(Dispatchers.IO) {
        val countryCode = (overrideCountryCode ?: getDetectedCountryCode()).lowercase()
        
        val sources = mutableListOf<Pair<String, String?>>(
            "https://iptv-org.github.io/iptv/countries/$countryCode.m3u" to null
        )
        
        if (countryCode == "ph") {
            sources.add("https://raw.githubusercontent.com/Harleythetech/IPHTV/refs/heads/main/ph.m3u" to "Philippines")
        }

        val remoteChannels = sources.map { (url, forcedCategory) ->
            async {
                try {
                    val content = URL(url).readText()
                    parseM3U(content, countryCode, forcedCategory)
                } catch (e: Exception) {
                    emptyList<Channel>()
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
            if (line.startsWith("#EXTINF:")) {
                currentInfo = line
            } else if (line.startsWith("http") && currentInfo != null) {
                val name = currentInfo.substringAfterLast(",").trim()
                val logo = Regex("""tvg-logo="([^"]*)"""").find(currentInfo)?.groupValues?.get(1)
                val group = forcedCategory ?: Regex("""group-title="([^"]*)"""").find(currentInfo)?.groupValues?.get(1) ?: "General"
                
                channels.add(
                    Channel(
                        id = line.trim().hashCode().toString(),
                        name = name,
                        logoUrl = logo,
                        streamUrl = line.trim(),
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
