# StairPlay TV 📺✨

A premium, high-performance IPTV streaming application for Android, built with the latest modern technologies like **Jetpack Compose**, **Material 3**, and **Media3/ExoPlayer**.

## 🚀 Key Features

- **Premium UI/UX**: Ultra-modern dark mode design with Neon Purple accents, smooth transitions, and high-quality animations.
- **Smart Country Detection**: Automatically detects your location via Telephony Services to provide relevant local channels instantly.
- **Advanced Search**: An animated, integrated search bar in the header that allows you to find your favorite channels in real-time.
- **High-Performance Streaming**: Powered by ExoPlayer (Media3), optimized for low-latency playback and high-quality stream rendering.
- **Immersive Fullscreen**: True edge-to-edge playback experience that automatically hides UI elements and manages screen orientation (Landscape/Portrait).
- **Personalized Content**:
  - **Home Dashboard**: Quick access to your **Recently Watched** channels and **Favorites**.
  - **Explore Tab**: Browse through featured channels and category-based browsing (Movies, Sports, News, etc.).
- **Parental Lock & Safe Mode**: Integrated NSFW/Adult content filter protected by a secure 4-digit PIN.
- **Flexible Playlists**: Support for adding and managing multiple external M3U/M3U8 playlist sources.
- **Dual View Modes**: Seamlessly switch between a sleek **Grid Poster** view and a detailed **List** view.

## 🌍 Supported Countries (Out-of-the-box)

The app comes pre-configured with high-quality channel sources for the following regions:

- 🇵🇭 **Philippines**: Full access to local networks and regional broadcasts.
- 🇺🇸 **USA**: Popular news, entertainment, and sports networks.
- 🇯🇵 **Japan**: Variety shows, news, and specialized Japanese content.
- 🇩🇪 **Germany**: Top European networks and German-language programming.

*Note: You can manually override your country in the Settings or add any other country via the Custom Playlist feature.*

## 🛠 Tech Stack

- **Kotlin**: 100% modern programming language.
- **Jetpack Compose**: Declarative UI toolkit for smooth, efficient rendering.
- **Material 3**: The latest design standards from Google.
- **Media3 (ExoPlayer)**: Industry-leading media playback engine.
- **Room Database**: Persistent local storage for channels, favorites, and search history.
- **Coroutines & Flow**: Asynchronous programming for high responsiveness.
- **Coil**: Image loading library for fast thumbnail and logo rendering with smart caching.

## 🏗 How to Build

1. **Clone the repository**:
   ```bash
   git clone https://github.com/dragobb/SPTV.git
   ```
2. **Open in Android Studio**:
   - Recommended Version: **Android Studio Ladybug (2024.2.1)** or higher.
   - Recommended Java Version: **Java 17**.
3. **Build & Run**:
   - Sync project with Gradle files.
   - Click `Run 'app'` on your emulator or physical device.

## 📖 How to Use

1. **Auto-Setup**: On first launch, the app detects your region and fetches the latest channel list.
2. **Browsing**: Swipe through the **Featured Spotlight** in the Explore tab to find trending channels.
3. **Search**: Click the Search icon in the header. The title will animate away, giving you a full search field.
4. **Favorites**: Tap the Heart icon on any channel card. A **Hollow Heart** means it's not saved, while a **Solid Red Heart** means it's in your favorites list.
5. **Parental Control**: Go to **Settings -> Safe Mode**. Set or enter your PIN to toggle the adult content filter.
6. **Custom Sources**: Have your own link? Go to **Settings -> Manage Playlists** and paste your M3U URL.

## 🤝 Contributing

Contributions are welcome! If you'd like to improve the app:
1. Fork the project.
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`).
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

## 👨‍💻 Created by
Developed with ❤️ by **ABServices**.

---
*© 2026 StairPlay TV. All rights reserved.*
