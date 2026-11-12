# Lumen Explorer (v1.2.1)

A native Android file manager (Kotlin + Jetpack Compose) with a Windows‑11 look: tabs, address bar, command bar,
details pane, drag‑select, zip / unzip, recycle bin, storage analyzer, duplicate finder and the SecureVault web app built in.
Fully offline — the app declares **no INTERNET permission**.

## Build
1. Open this folder in **Android Studio Koala (2024.1) or newer** (JDK 17, Android SDK 34).
2. Let Gradle sync (needs internet once, to download dependencies), then **Run ▶** on a device, or
   `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
   **For real-world speed install the release variant** (`./gradlew assembleRelease` — it is signed with the debug key so it
   installs directly, and `installRelease` works from Android Studio's *Build Variants*). Compose **debug** builds are many times
   slower than release and are the main reason lists feel laggy while developing.
3. First launch: **Get started → Allow everything → Start**.

## What changed in v1.2
* **Selection** – tap the ✓ / tick a row to start selecting, then tap or drag across items to add more. Press‑and‑hold an
  item (touch) and drag to select a range; press‑and‑hold or drag on **blank space** for a rubber‑band box (edges auto‑scroll).
  Mouse: left‑drag on blank space = box, Ctrl+click toggles, Shift+click selects a range, right‑click = context menu.
* **Keyboard** (tablet keyboard / Bluetooth / USB): arrows move the cursor, Shift+arrows extend, Home/End/PageUp/PageDown,
  Enter open, Backspace / Alt+↑ up, Alt+←/→ back/forward, type letters to jump to a name, Ctrl+A/C/X/V, Ctrl+I invert,
  Delete (Shift+Delete = permanent), F2 rename, F5 / Ctrl+R refresh, Ctrl+L / Alt+D edit address, Ctrl+F search,
  Ctrl+T / Ctrl+W / Ctrl+Tab / Ctrl+1…9 tabs, Ctrl+Shift+N new folder, Ctrl+H hidden files, Alt+Enter properties,
  Ctrl+, settings, Esc closes whatever is on top. In the media viewer: ←/→ page, ↑ or I details, ↓ or Esc close, Del delete.
* **Speed** – single‑stat directory listing, folder item counts computed lazily per visible row, file index built on a
  background thread with incremental updates, thumbnails cached in memory *and on disk* (video frames are decoded at
  thumbnail size), big lists built off the main thread.
* **Native sheets & dialogs** – Material 3 bottom/top sheets (28 dp corners, handle, 32 % scrim, nested scrolling: content
  scrolls first, then the sheet takes over; fling to expand/collapse/dismiss), centered Material 3 dialogs everywhere.
* **Tabs** – phones: a tab‑count button next to the address bar opens a tab switcher sheet. Tablets / desktop (≥ 600 dp):
  Chrome‑style strip whose tabs share the width and shrink as more are opened. Pull down on the toolbar for quick settings.
* **SecureVault** opens full screen with only a slim top tab (title + close).
* **Media viewer** – drag down to close (the photo follows your finger and fades), drag up (or the ⓘ button) for a details
  sheet: size, dimensions, EXIF (camera, exposure, ISO, GPS) or video duration / frame rate / bitrate.
* **Icons everywhere** – Sort, View and Filter menus (and the context menus) now carry icons and check marks.

## What changed in v1.1 (earlier)
* **First‑run flow** – Welcome → permission page (All files, Photos/videos/audio, Install apps, Notifications, each with its own
  Allow button and an “Allow everything” shortcut) → **Start**. Re‑run any time from Settings → Storage → “Run setup again”.
* **Counts fixed** – folder item counts on Home/lists are recomputed as soon as permissions are granted and whenever storage
  changes, instead of being computed once (that was the cause of “0 items”).
* **Categories fixed** – Images, Videos, Audio, Documents, APKs, Archives… are built from a real walk of the file tree
  (skipping `Android/data`, `Android/obb`, dot‑folders and the bin), so APKs and other files now show up.
  Without All‑files access it falls back to MediaStore.
* **No more video loop** – an external player is launched only by an explicit tap (with a 2.5 s guard); returning from VLC etc.
  never re‑launches it. Formats the built‑in player can't handle (mov, avi, hevc…) open externally once.
* **Smooth scrolling** – lazy grids/lists with stable keys, synchronous row building, thumbnails decoded off‑thread
  (3 at a time, down‑sampled, LRU‑cached, short delay so fast fling doesn't start decodes).
* **Storage list** – only storage that is really mounted is shown (no SD card ⇒ no SD entry). USB drives appear automatically;
  phones/tablets over USB (MTP) are linked once through the system folder picker and then browsable (copy to/from phone).
* **SecureVault** – the web version is inside the app (Sidebar → More → SecureVault). It runs on a private
  `appassets` origin, can't reach the network, and file “downloads” are saved to `Downloads/SecureVault`.
  `SecureVault.html` itself also got a rebuilt dock (tap the round button to open/close, drag the handle, auto‑hide option).
* **Sheets** – bottom and top sheets can be dragged all the way to the top/bottom even with little content;
  drag toward their own edge to shrink, then dismiss.

## Known limits
* RAR can be listed/identified but **not extracted** (no free encoder/decoder bundled); ZIP, 7z, TAR, TAR.GZ/BZ2/XZ and
  AES‑256 ZIP are supported.
* Android 11+ blocks every third‑party app from `Android/data` and `Android/obb`.
* MTP phones/tablets need the one‑time “Link” step (Android doesn't expose them as normal storage).
* Video/audio codec support depends on the device; unsupported files show an “Open in another app” button.
* This project was assembled without access to an Android build environment: the file/archive logic was compiled and unit‑tested
  on a JVM, but the Compose UI (including all of the v1.2 gesture, sheet, tab and viewer code) has not been compiled yet. If Android Studio reports a compile error on first sync, it is most
  likely a small signature/import mismatch — please send the error text and it can be fixed quickly.
