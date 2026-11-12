# Musely

**Hear your music like it's the first time.**

Musely is a calm, offline music player for Android. It turns the songs already on your phone into a beautiful library: no accounts, no ads, nothing leaves your device.

- **Your library, organized**: songs, an album grid and artists, with instant search across all three.
- **A player that breathes**: big artwork that settles when you pause, a play button that morphs, and an "Up next" peek at the queue.
- **Always within reach**: a floating mini player with live progress, plus lock-screen and notification controls.
- **Made with Material 3**: a violet `#6755EF` and lime `#F4FF90` palette, in light and dark.

## Download

Grab the latest APK from [Releases](../../releases/latest) and open it on your phone (allow installs from your browser if asked).

## Build

Requires JDK 17+ and the Android SDK (platform 37).

```bash
./gradlew assembleDebug
```

Pushing a `v*` tag builds the release APK in CI and publishes it as a GitHub Release.
