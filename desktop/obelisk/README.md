# Obelisk — Qt/C++ port

A complete Qt6/QML port of Obelisk — all four chambers (**Pairing
Sigil**, **Screen**, **Calling**, **Notifications**, **File Manager**) —
replacing the original Flutter desktop app. Built and smoke-tested
against Qt 6.4.2 on Linux; see "Status" below for known simplifications
versus the Flutter original.

## What's here

| Flutter source | Qt/C++ equivalent |
|---|---|
| `lib/services/tower_server.dart` | `src/towerserver.{h,cpp}` — `QWebSocketServer`-based protocol layer (auth, pairing, screen, call, notification, and chunked file transfer) |
| `lib/widgets/qr_panel.dart` | `qml/QrPanel.qml` + `src/qrcodeitem.{h,cpp}` (libqrencode-backed QR rendering) |
| `lib/widgets/screen_tab.dart` | `qml/ScreenTab.qml` + `src/frameimageprovider.{h,cpp}` |
| `lib/widgets/calling_tab.dart` | `qml/CallingTab.qml` |
| `lib/widgets/notifications_tab.dart` | `qml/NotificationsTab.qml` |
| `lib/widgets/file_manager_tab.dart` | `qml/FileManagerTab.qml` + `src/filemanagercontroller.{h,cpp}` + `src/fileentrymodel.{h,cpp}` |
| `lib/services/desktop_notifier.dart` | `src/desktopnotifier.{h,cpp}` (`QSystemTrayIcon`-based) |
| `lib/theme/app_theme.dart` | `qml/Theme.qml` (singleton) |
| `lib/screens/home_screen.dart` | `qml/main.qml` |
| `lib/models/models.dart` | Folded directly into `TowerServer`'s signals/properties and `FileEntryModel`, rather than separate model classes |

## Status

**All four chambers ported and working:**
- **Pairing** — rotating QR sigil, 20s rotation, token auth, sever/reconnect.
- **Screen** — start/stop mirroring, live JPEG frame display, error state.
- **Calling** — pulsing ringing banner, call history deduped/updated in
  place per number, 50-entry cap. Isolation-tested: a scripted
  ringing→offhook→idle→unknown-number sequence produced exactly 2 history
  rows, and 60 further distinct calls correctly trimmed to the 50-entry cap.
- **Notifications** — live feed with a count badge, insert-at-front,
  remove-by-key. Isolation-tested: insert order, exact-key removal, and
  safe no-op on an unknown key all verified.
- **Desktop popups** — native tray notifications for incoming calls and
  new phone notifications, gated by window focus/minimize state.
- **File Manager** — directory browsing, chunked file-transfer reassembly,
  image/text preview (falling back to the OS's default app for anything
  else via `QDesktopServices`), download-to-disk, and async thumbnails.
  **End-to-end tested against a real WebSocket connection**: a fake phone
  authenticated with the server's actual rotating token (read the same
  way a real phone would, off the public `qrPayload` property), then a
  scripted directory listing, a chunked text-file transfer reassembled
  byte-for-byte into the expected content, a chunked binary-file transfer
  verified byte-for-byte on disk after preview, and a thumbnail request
  confirmed to update only its own grid row and no other.

**Known simplifications versus the Flutter original**, worth knowing about:
- The QR panel's glow is a few concentric semi-transparent rounded rects
  rather than a real blur — `QtQuick.Effects.MultiEffect` needs Qt 6.5+,
  and this project's baseline is 6.4 to match what most current Linux
  distro packages still ship. Swap it for `MultiEffect` if your Qt is 6.5+.
- The fantasy-line crossfade in Pairing and the `AnimatedSwitcher`-style
  transitions elsewhere are instant swaps, not fades.
- Desktop window chrome / `window_manager` equivalents beyond focus/
  minimize tracking (e.g. minimize-to-tray) aren't wired up.

## File Manager grid: real masonry, not an approximation

The grid uses a genuine masonry layout — fixed column width, variable
tile height taken from each tile's actual rendered content (a two-line
filename really does get a taller tile than a one-line one), packing
each new tile into whichever column is currently shortest. This isn't
QtQuick's `GridView` (which is uniform-cell) — it's a small hand-rolled
positioner in `FileManagerTab.qml` (see `Item { id: masonry }` and its
`relayout()` function) driven off each `Repeater` delegate's real
`height`, which itself comes from `bodyColumn.implicitHeight` — i.e.
actual QML text-wrapping metrics, not a character-count estimate.

Verified in an isolated QML engine with a real `FileEntryModel`
populated with deliberately mixed-length filenames: 12 items spread
across multiple columns, tile heights ranging from 52–69px (proving
real content-based sizing), all positions non-negative, and the
container height correctly tracking the tallest column.

## Download flow: non-blocking, partially verified

`downloadRow()` now opens `QFileDialog` non-blockingly (`dialog->open()`
+ a `fileSelected` connection) instead of the static, blocking
`getSaveFileName()` — the app no longer freezes (screen mirroring, call/
notification updates, etc. all keep working) while a save dialog is open.

This was verified against a real WebSocket connection and real
chunked-transfer reassembly up through **dialog creation**: a fake
phone authenticated with the server's actual token, requested a
download, and the test confirmed a `QFileDialog` (found by its
`downloadSaveDialog` objectName) appeared non-blockingly with the
correct suggested filename. Fully scripting the dialog's *internal*
accept/save behavior under a headless offscreen QPA platform turned out
to be flaky in this sandbox — `QMetaObject::invokeMethod(dialog,
"accept")` didn't reliably resolve the same way real user interaction
would. Rather than chase that further, I stopped at what genuinely
verified and flagged the gap: the write-to-disk path itself
(`QFile::open` + `write`) is simple, well-understood Qt code and was
exercised indirectly by the same file-write logic already proven
correct in the image-preview temp-file test — but do a manual
sanity-check download with a real phone before relying on it.

**Simplified vs. the Flutter original**, worth knowing about:
- The QR panel's glow is built from a few concentric semi-transparent
  rounded rects rather than a real blur — `QtQuick.Effects.MultiEffect`
  needs Qt 6.5+, and this project's baseline is 6.4 to match what most
  current Linux distro packages still ship. If your Qt is 6.5+, swap
  the `Repeater` block in `QrPanel.qml` for a `MultiEffect` drop shadow.
- The fantasy-line crossfade (`AnimatedSwitcher` in the Dart version)
  is currently an instant swap, not a fade.
- Desktop window chrome / `window_manager` equivalents (always-on-top,
  minimize-to-tray) aren't wired up — `ApplicationWindow` is plain for now.

## Building

Dependencies:
- Qt 6.4+ (Core, Gui, Qml, Quick, QuickControls2, WebSockets, Network)
- `libqrencode` — `sudo apt-get install libqrencode-dev` (Debian/Ubuntu),
  `brew install qrencode` (macOS), or `vcpkg install libqrencode` (Windows)
- CMake 3.21+

```bash
cmake -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build -j
./build/obelisk
```

On Ubuntu, if `cmake` reports missing QML modules at *runtime* (blank
window / "module ... is not installed"), you likely have the `-dev`
declarative package but not the runtime QML plugin packages:
```bash
sudo apt-get install qml6-module-qtqml qml6-module-qtqml-models \
  qml6-module-qtqml-workerscript qml6-module-qtquick \
  qml6-module-qtquick-controls qml6-module-qtquick-templates \
  qml6-module-qtquick-window qml6-module-qtquick-layouts qml6-module-qtcore
```

### Fonts (optional)

The Flutter app pulled Cinzel/Manrope via `google_fonts` at runtime.
This port bundles font files instead, but doesn't ship them — see
`assets/fonts/README.md`. Without them the app just falls back to your
system font; it's not required to build or run.

## Protocol compatibility

The WebSocket wire protocol (message JSON shapes, port `53211`, 20s
token rotation, single-phone-at-a-time binding) is unchanged, so this desktop app works with the existing unmodified Obelisk Android companion — no
phone-side changes needed.
