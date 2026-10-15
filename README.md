# Obelisk

A private, zero-cloud phone companion system operating exclusively over your local Wi-Fi network. 

Obelisk connects your Android phone directly to your desktop PC via a local WebSocket connection (`ws://<pc-ip>:53211`). No cloud relays, no external servers, no tracking, and no user accounts are required.

---

## Architecture & Features

The project is split into two complementary halves:

### 1. [Desktop Client](desktop/obelisk/) (`desktop/obelisk/`)
Built with **Qt 6 / C++ / QML** and CMake:
- **Pairing Sigil**: Hosts the local WebSocket server on port `53211` and displays a dynamic QR code that rotates every 20 seconds with a fresh authentication token.
- **Screen Chamber**: Live low-latency view of your phone screen streamed as adaptive JPEG frames (~9 fps) with dynamic orientation resizing.
- **Calling Chamber**: Live caller status (ringing, off-hook, idle) with phone contact name resolution and deduplicated call history.
- **Notifications Chamber**: Real-time notification feed with badge counters, app icon integration, and system tray popups.
- **File Manager Chamber**: Browse device storage with an adaptive masonry layout, view in-place image and text previews, and download files via non-blocking chunked streams.

### 2. [Android Companion](android/Obelisk/) (`android/Obelisk/`)
Built with **Kotlin**, Android SDK 35 (minSdk 26), and Gradle:
- **Sigil Scanner**: Uses CameraX and ML Kit to scan the desktop pairing QR code and initiate the direct WebSocket connection.
- **Screen Streamer**: Uses Android `MediaProjection` to capture frames, dynamically adapt to orientation changes, and stream compressed JPEGs.
- **Notification Relay**: Uses `NotificationListenerService` with a customizable per-app allowlist so only desired apps forward notifications.
- **Call State Monitor**: Merges redundant phone state broadcasts and resolves contacts from the system address book.
- **Chunked File Server**: Backpressure-aware chunked file transfer that prevents OOMs when transferring large files.

---

## Directory Structure

```text
obelisk/
├── android/
│   └── Obelisk/               # Android companion app (Kotlin, Gradle)
│       ├── app/               # Application sources, manifests, and layouts
│       ├── gradle/            # Gradle wrapper
│       └── build.gradle.kts   # Android build script
├── desktop/
│   └── obelisk/               # Desktop client (Qt6, C++, QML, CMake)
│       ├── assets/            # Fonts and bundled assets
│       ├── qml/               # QML UI views and components
│       ├── src/               # C++ backend services and models
│       └── CMakeLists.txt     # CMake build configuration
├── .gitignore                 # Root Git ignore rules
└── README.md                  # Project documentation
```

---

## Getting Started

### Prerequisites

- **Desktop**:
  - Qt 6.4+ (Core, Gui, Widgets, Qml, Quick, QuickControls2, WebSockets, Network)
  - CMake 3.21+
  - `libqrencode` (`sudo apt-get install libqrencode-dev` on Debian/Ubuntu)
  - C++17 compiler
- **Android**:
  - Android Studio Ladybug / Meerkat or Gradle with Android SDK (API 35, minSdk 26)
  - Java 17+

### Building the Desktop Client

```bash
cd desktop/obelisk
cmake -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build -j$(nproc)
./build/obelisk
```

### Building the Android Companion

1. Open `android/Obelisk` in Android Studio.
2. Allow Gradle to sync dependencies.
3. Build and install on your device:
   ```bash
   cd android/Obelisk
   ./gradlew installDebug
   ```

---

## Pairing & Usage

1. Launch **Obelisk** on your desktop. A rotating QR sigil will appear.
2. Launch **Obelisk** on your phone.
3. Tap **Scan the Sigil**, point the camera at your desktop screen, and approve the one-time screen capture prompt.
4. The status indicator will turn green once authenticated.

---

## License

This project is licensed under the **GNU General Public License v3.0** (GPLv3). See the [LICENSE](LICENSE) file for the full license text.

