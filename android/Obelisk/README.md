# Obelisk — Phone Companion

*The vessel that answers the obelisk's call.*

Obelisk is the Android half of a two-part system for viewing and managing your phone from your PC over your **local network only** — no cloud, no relay server, no account. This app runs on your phone; its counterpart, the **Obelisk desktop app**, runs on your PC and is what you actually look at day to day.

This app's job is narrow and deliberate: mirror the screen, relay notifications and calls, and expose your files — all opt-in, all revocable, all confined to your own Wi-Fi.

---

## What it does

| Chamber | What you get |
|---|---|
| **Screen** | Live view of your phone's screen on the PC — a downscaled JPEG stream (~9 fps), automatically adapting to portrait/landscape rotation. View-only; nothing is injected back into the phone. |
| **Calling** | Incoming/ongoing/ended call state on the PC, with the caller's **saved contact name** resolved automatically (falls back to the raw number if unsaved). |
| **Notifications** | Your phone's notifications relayed live, with a **per-app allow-list** so you control exactly which apps get forwarded. |
| **File Manager** | Browse your phone's storage from the PC — folders, files, image thumbnails, preview-in-place, and download. No file size ceiling; transfers stream in chunks rather than loading anything whole into memory. |

Nothing here can *act* on your phone from the PC side — this build is strictly observe-and-relay.

---

## Pairing

1. Open **Obelisk** on your PC. It shows a QR "sigil" that **rotates every 20 seconds** until something binds to it.
2. On your phone, tap **Scan the Sigil** and point the camera at it.
3. Grant the screen-recording permission when Android asks (required once per session, by design of Android's MediaProjection API — the OS itself doesn't allow this to be remembered).
4. You're bound. The status card on the phone glows green for as long as the link holds.

Everything happens over a direct WebSocket to your PC's local IP — nothing leaves your network.

---

## Permissions — what's asked for and why

The **Rites of Preparation** section on the home screen lists each one with a live green/red indicator, so you always know what's outstanding:

| Permission | Why Obelisk needs it |
|---|---|
| Camera | Scanning the pairing QR code — used only during pairing, nothing else. |
| Phone State, Call Log, Contacts | Detecting call state and resolving caller names for the Calling tab. |
| Notifications (runtime) | Required on Android 13+ to show Obelisk's own persistent "link is active" notification. |
| **Notification Access** (special) | Lets Obelisk read notification content to relay it. This is the same permission class as any notification-mirroring app; you choose exactly which apps forward via **Choose Which Apps to Forward**. |
| **All-Files Access** (special) | Lets the File Manager tab actually browse and read your storage on Android 11+. |
| Screen Capture (MediaProjection) | Requested fresh each time you pair — this is how Android's screen-sharing consent works for every app, not a Obelisk quirk. |

Two permissions — Notification Access and All-Files Access — require a manual toggle in system Settings rather than a normal in-app prompt; Obelisk's buttons take you straight to the right screen.

---

## Design notes, for anyone reading the code

- **Screen capture is a JPEG frame stream, not H.264 video.** Simpler, more robust across devices, plenty smooth at ~9 fps over a LAN. A `DisplayManager.DisplayListener` watches for orientation changes and resizes the capture surface live, so rotating the phone (or a landscape video) doesn't get letterboxed into a stale portrait-shaped buffer.
- **File transfer is chunked**, not whole-file base64. The sender throttles itself against the socket's actual outgoing queue depth rather than firing chunks as fast as disk I/O allows — this is what keeps a multi-hundred-MB transfer from either timing out or OOM-crashing the app.
- **The WebSocket connection is a client, not a server.** The PC (Obelisk) hosts the WebSocket server; the phone dials in after reading the QR sigil. This keeps the phone from ever needing an open listening port.
- **Call-state broadcasts are coalesced.** Many devices genuinely deliver two `ACTION_PHONE_STATE_CHANGED` broadcasts for the same ringing/idle transition — one with caller info, one without, milliseconds apart. Obelisk merges these into a single event rather than showing duplicate cards on the PC.
- **One phone, one PC, one session.** Obelisk rejects a second pairing attempt while a phone is already bound.

---

## Tech stack

- Kotlin, minSdk 26, target/compileSdk 35
- OkHttp (WebSocket client)
- CameraX + ML Kit (QR scanning)
- Material Components (UI)
- No external analytics, no cloud dependency, no account system

---

## Building

Open `android/` in Android Studio as a project. If starting from a bare checkout with no launcher-icon assets, let Android Studio generate default ones first (New Project → Empty Views Activity, package `com.sorcerer.obelisk`), then bring these source files in over the generated scaffold.

```
Build → Clean Project
Build → Rebuild Project
```

is recommended after pulling changes that touch view IDs or the manifest.

---

*A green sigil means it's granted. A red one means it still needs your blessing.*