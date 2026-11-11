# Lumen Explorer

A native Android file manager styled after Windows 11 File Explorer. Fully offline (no INTERNET permission), no login, no vault.

## Build
- Android Studio (Koala+) → Open this folder → Run, **or** `./gradlew assembleDebug` (JDK 17, Android SDK 34).
- Needs a one-time Gradle sync with network to fetch dependencies; the built app itself never uses the network.
- minSdk 26, target/compile 34. Package `com.lumen.explorer`.

## Features
- Real device files: internal storage, SD cards / USB OTG, Recycle Bin, Downloads, DCIM etc.
- Windows-style UI: tabs, back/forward/up, editable breadcrumb address bar, search (`ext:`, `kind:`, `size>`, wildcards), command bar, sidebar (drawer on phones), details pane, status bar.
- View modes: Extra large / Large / Medium / Small icons, List, Details, Tiles, Content; sort, group, filter; checkboxes.
- Bottom sheets (menus, context menu, actions), top sheet (quick settings: pull down on the tab strip).
- Copy / cut / paste with conflict choices, rename, delete (Recycle Bin + Undo), properties, hashes, share, open with.
- Compress / extract: ZIP, AES-256 ZIP (password), 7z, TAR, TAR.GZ; extract also TAR.BZ2/XZ, GZ.
- Gallery, Recent, Favorites, Quick access, categories, Storage analyzer, Large files, Duplicate finder.
- Viewers: images (zoom/rotate, HEIC/HEIF/AVIF), video/audio, PDF, text editor.
- Theme: System default (default) / Light / Dark, 8 accents, text size and interface size sliders.
- Hardware keyboard shortcuts (Ctrl+C/X/V/A/T/W/F/L, Del, F2, F5, Alt+←/→).
- Wide file-type support incl. Apple (HEIC, MOV, M4A, CAF, AIFF, Pages/Numbers/Key, DMG/IPA recognized).

## Permissions
All-files access (Android 11+), legacy storage permissions (older), media read, install packages (to open APKs). A "Continue with limited access" option exists.

## Known limitations
- RAR cannot be extracted (hands off to another app). DMG/IPA/Pages etc. are recognized and opened externally.
- Android 11+ blocks `/Android/data` and `/Android/obb` for all third-party apps.
- GIFs show as static in the viewer; video/audio codec support depends on the device.
- Not compiled in the authoring environment: if Gradle reports an unknown Material icon name, replace it with a nearby one.
