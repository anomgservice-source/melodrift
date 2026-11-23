# Melodrift v21

Melodrift is a local-first Android music and video player built with Capacitor.

## v21 additions
- Full-player synchronized lyrics panel (uses imported LRC lyrics).
- Landscape vinyl player layout with optional Android orientation lock.
- Multi-selection for audio tracks and videos: share selected files, hide them, or request Android batch deletion.
- Local JSON backup and restore for playlists, favorites, history, themes, and playback preferences. Media files themselves are not copied.
- Existing theme/accent palettes, search history, audio visualizer, video library/player, equalizer, and lyrics Studio retained.
- Android versionCode follows the GitHub Actions run number so each new APK can update an older installation.

## Build
Push to `main` or run the **Build APK** workflow manually in GitHub Actions. The workflow publishes `Melodrift.apk` as a release asset.

Some Android actions (orientation lock, file sharing, and batch deletion) depend on Android version and user permissions. The app asks Android to confirm deletion of selected files.
