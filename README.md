# Lumen Explorer (v1.1)

A native Android file manager (Kotlin + Jetpack Compose) with a Windows‑11 look: tabs, address bar, command bar,
details pane, drag‑select, zip / unzip, recycle bin, storage analyzer, duplicate finder and the SecureVault web app built in.
Fully offline — the app declares **no INTERNET permission**.

## Build
1. Open this folder in **Android Studio Koala (2024.1) or newer** (JDK 17, Android SDK 34).
2. Let Gradle sync (needs internet once, to download dependencies), then **Run ▶** on a device, or
   `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
3. First launch: **Get started → Allow everything → Start**.

## What changed in v1.1
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
  on a JVM, but the Compose UI has not been compiled yet. If Android Studio reports a compile error on first sync, it is most
  likely a small signature/import mismatch — please send the error text and it can be fixed quickly.
