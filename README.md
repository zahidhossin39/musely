<p align="center">
  <img src="Logo/musely.png" alt="Musely logo" width="128" />
</p>

<h1 align="center">Musely</h1>

<p align="center">
  <strong>Hear your music like it's the first time.</strong><br />
  A calm, 100% offline music player for Android built with Jetpack Compose, Material 3, and Media3.
</p>

<p align="center">
  <a href="https://github.com/zahidhossin39/musely/actions/workflows/build.yml">
    <img src="https://github.com/zahidhossin39/musely/actions/workflows/build.yml/badge.svg" alt="Build Status" />
  </a>
  <a href="https://github.com/zahidhossin39/musely/releases/latest">
    <img src="https://img.shields.io/github/v/release/zahidhossin39/musely" alt="Latest Release" />
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/github/license/zahidhossin39/musely" alt="MIT License" />
  </a>
  <img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white" alt="Android 8.0+ (API 26)" />
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#download">Download</a> •
  <a href="#privacy--permissions">Privacy &amp; Permissions</a> •
  <a href="#tech-stack">Tech Stack</a> •
  <a href="#build--test">Build &amp; Test</a> •
  <a href="CONTRIBUTING.md">Contributing</a>
</p>

---

Musely turns the audio and video files already stored on your device into a fast, distraction-free listening library—playing video audio streams directly without copying files—and can download songs and videos from YouTube, YouTube Music, and Spotify links. No accounts, no ads, no analytics.

## Features

- **Organized local library**: Scans device audio and videos via Android `MediaStore` and observes changes automatically (`ContentObserver`), so newly added tracks appear without manual refreshes.
- **Play videos as audio**: Listen to local `.mp4`, `.mkv`, and `.webm` videos directly through the audio-only `ExoPlayer` pipeline from the **Videos** tab, playlists, **Liked Music**, search, Mini Player, or lock-screen controls—zero file duplication.
- **Bottom navigation**: Move between **Home**, **Playlists**, **Downloads**, and **Settings** tabs. Each tab remembers where you were, re-tapping a tab returns to its top, and back always leads home first.
- **Browse by Songs, Videos, Albums, or Artists**: Switch Home views with Material 3 filter chips, jump into album tracklists sorted by track number or artist discographies, and play or shuffle any collection in one tap.
- **Playlists & Liked Music**: Create custom playlists with YouTube-Music-inspired 2×2 cover artwork collages (for both songs and videos), multi-select and add tracks with `All` / `Songs` / `Videos` filters, reorder items, and star favorites into the pinned **Liked Music** playlist.
- **Downloads from YouTube, YouTube Music & Spotify**: Paste a link on the **Downloads** tab or tap **Share → Musely** in those apps. Audio (the default) is saved as tagged `.m4a` with cover art; video is saved as `.mp4` up to your chosen quality. Single tracks, YouTube playlists, and Spotify albums/playlists are supported, optionally saved straight into a Musely playlist. Spotify audio is DRM-protected, so Musely matches each Spotify track on YouTube Music instead.
- **Post-download options**: When a download finishes, play it, add it to a playlist or **Liked Music**, share it, view file info, or delete it right away. Downloads land in `Music/Musely` and `Movies/Musely` and appear in your library automatically.
- **Track management**: Inspect per-item **File Info** (filename, path, video audio-track detection, size, MIME type, duration, bitrate, sample rate, and modified date), **Hide/Unhide** songs or videos from the library, or **Delete** files from device storage with scoped confirmation.
- **Settings & GitHub Releases auto-updater**: The **Settings** tab checks `zahidhossin39/musely` GitHub Releases on launch, throttled resume, and on demand (with a badge on the tab when an update is available), then hands the APK URL to your browser via `Intent.ACTION_VIEW`. It also manages hidden tracks and links to permissions, source code, and issue reporting.
- **Instant multi-word search**: Filter across song titles, video titles, artists, and album names simultaneously in real time.
- **Expressive Now Playing view**: Breathing cover art or video frame artwork (`0.88x` spring settle when paused), a play/pause button with Material 3 corner-shape morphing, interactive scrubbing, shuffle and repeat (`Off` / `All` / `One`) toggles, and an **Up Next** queue preview.
- **Floating Mini Player**: Persistent bottom card with live progress bar across library and detail screens.
- **Background & lock-screen playback**: Powered by a `MediaSessionService` with audio-only `ExoPlayer`, audio focus handling, unplug/noisy-output pause, and media notification artwork.
- **Smart artwork loading**: Resolves `MediaStore` audio/video thumbnails, embedded pictures, video frames, album art URIs, and fallback album tracks with an in-memory LRU cache.
- **Material 3 identity**: Light and dark themes built from Musely Violet (`#6755EF`) and Musely Lime (`#F4FF90`), edge-to-edge layout, time-of-day greetings, and Android 13+ monochrome themed launcher icons.

## Download

1. Download `Musely.apk` (or `musely-<version>.apk`) from [GitHub Releases](https://github.com/zahidhossin39/musely/releases/latest).
2. Open the downloaded `.apk` file on your Android device (Android 8.0 / API 26 or newer).
3. Allow installation from unknown sources / your browser if prompted.

> [!NOTE]
> Release APKs published via CI are signed with a persistent release keystore when signing secrets are configured (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`), falling back to the debug signing config on PRs or forks. See [SECURITY.md](SECURITY.md) for verification details.

## Privacy & Permissions

Musely's library, playlists, artwork decoding, and playback remain **offline** with zero ads, analytics, telemetry, or accounts. Musely connects to the internet only to:

- check `zahidhossin39/musely` GitHub Releases for app updates (the APK opens in your browser; no `REQUEST_INSTALL_PACKAGES` permission is used), and
- look up and download the YouTube, YouTube Music, or Spotify links **you** paste or share. Requests go only to YouTube/Google and Spotify servers, and nothing about your library is sent.

> [!IMPORTANT]
> Downloading may be restricted by the terms of the services involved and by copyright law where you live. Only download content you have the right to keep.

| Permission | Android Version | Purpose |
| :--- | :--- | :--- |
| `READ_MEDIA_AUDIO` | Android 13+ (API 33+) | Read local audio files from `MediaStore` |
| `READ_MEDIA_VIDEO` | Android 13+ (API 33+) | Read local video files to play their audio streams directly |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Android 14+ (API 34+) | Support partial selected-video access and `"Select more"` picker |
| `READ_EXTERNAL_STORAGE` | Android 8.0–12 (API 26–32) | Read local audio and video files on pre-Android 13 devices |
| `WRITE_EXTERNAL_STORAGE` | Android 8.0–10 (API 26–29) | User-confirmed media file deletion on pre-Android 11 devices, and saving downloads on Android 8–9 (`maxSdkVersion="29"`) |
| `INTERNET` | Android 8.0+ (API 26+) | Check `zahidhossin39/musely` GitHub Releases for updates; look up and download links you choose |
| `FOREGROUND_SERVICE` | Android 9+ (API 28+) | Run a foreground service for continuous background playback |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Android 14+ (API 34+) | Declare media-playback foreground service type for system controls |
| `WAKE_LOCK` | Android 8.0+ (API 26+) | Prevent CPU sleep from interrupting audio playback |
| `FOREGROUND_SERVICE_DATA_SYNC` | Android 14+ (API 34+) | Keep downloads running in the background with a progress notification |
| `POST_NOTIFICATIONS` | Android 13+ (API 33+) | Show download progress (optional; asked once when you start a download) |

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language** | Kotlin (Compose Compiler `2.4.21`, JVM target 17) |
| **UI** | Jetpack Compose & Material 3 (`androidx.compose:compose-bom:2026.09.00`) |
| **Playback** | AndroidX Media3 ExoPlayer & MediaSession (`1.11.1`) |
| **Downloads** | [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) (`v0.27.1`), `MediaMuxer` remuxing, `MediaStore` publishing |
| **State & Lifecycle** | `AndroidViewModel`, Kotlin `StateFlow`, Lifecycle Runtime Compose (`2.11.0`) |
| **Build System** | Gradle `9.9.1` (Kotlin DSL), Android Gradle Plugin `9.5.1` |
| **SDK Targets** | `minSdk 26` • `targetSdk 36` • `compileSdk 37` |

## Project Structure

```text
.
├── .github/
│   ├── ISSUE_TEMPLATE/        # Bug report & feature request forms
│   ├── workflows/build.yml    # CI build, unit tests, APK packaging & release publishing
│   ├── CODEOWNERS             # Default review ownership
│   ├── PULL_REQUEST_TEMPLATE.md
│   ├── dependabot.yml         # Weekly Gradle & GitHub Actions dependency updates
│   └── release-notes.md       # Release notes used by tagged CI releases
├── Logo/
│   └── musely.png             # Source brand logo
├── app/
│   ├── build.gradle.kts       # App module config & dependencies
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/com/musely/app/
│       │   │   ├── AlbumArt.kt        # Audio/video artwork pipeline & LRU cache
│       │   │   ├── AppUpdater.kt      # GitHub Releases update checker
│       │   │   ├── Library.kt         # MediaStore audio/video query, grouping & search
│       │   │   ├── MainActivity.kt    # Edge-to-edge host activity
│       │   │   ├── MainViewModel.kt   # Library, playlist & MediaController state
│       │   │   ├── PlaybackService.kt # Background MediaSessionService & ExoPlayer
│       │   │   ├── PlaylistStore.kt   # Local JSON store for playlists, stars & hidden tracks
│       │   │   ├── SongActions.kt     # File info metadata & MediaStore media deletion
│       │   │   ├── download/          # Link parsing, extraction, download queue, tagging & saving
│       │   │   └── ui/                # Compose screens, tabs, mini player, components & M3 theme
│       │   └── res/                   # Vector icons, adaptive/monochrome mipmaps, themes
│       └── test/kotlin/com/musely/app/ # JUnit 4 unit tests
├── .editorconfig
├── .gitattributes
├── .gitignore
├── CHANGELOG.md
├── CODE_OF_CONDUCT.md
├── CONTRIBUTING.md
├── LICENSE
├── SECURITY.md
├── THIRD_PARTY_NOTICES.md     # Bundled libraries and their licenses
├── build.gradle.kts           # Root Gradle plugin versions
├── gradle.properties          # JVM & AndroidX Gradle properties
├── gradlew / gradlew.bat      # Gradle wrapper scripts
└── settings.gradle.kts        # Module & repository configuration
```

## Build & Test

### Prerequisites

- **JDK 17+** (CI runs on Temurin JDK 21)
- **Android SDK** with **Android Platform 37** (`compileSdk = 37`)
- Set `ANDROID_HOME` or create a local `local.properties` file:
  ```properties
  sdk.dir=/path/to/Android/Sdk
  ```

### Commands

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Run Android Lint
./gradlew lintDebug

# Build debug APK
./gradlew assembleDebug

# Build minified release APK (debug-signed for local testing)
./gradlew assembleRelease
```

On Windows, replace `./gradlew` with `gradlew.bat`.

### Releases

Pushing a version tag matching `v*` (for example `v3.0.0`) runs unit tests and Android Lint, builds the canonical `Musely.apk` alongside `musely-<version>.apk`, generates and verifies `.sha256` checksums, and publishes a GitHub Release automatically via [`.github/workflows/build.yml`](.github/workflows/build.yml).

For persistent release signing in CI, configure these repository secrets:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

## Contributing & Governance

- **Contributing guide**: [CONTRIBUTING.md](CONTRIBUTING.md)
- **Code of Conduct**: [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- **Changelog**: [CHANGELOG.md](CHANGELOG.md)
- **Security policy**: [SECURITY.md](SECURITY.md)

## License

Musely's source code is licensed under the [MIT License](LICENSE). Since v3.5.0 the release APK bundles [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0-or-later), so the APK as a whole is distributed under GPL-3.0 terms; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Copyright © 2026 [zahidhossin39](https://github.com/zahidhossin39) & [Masum Khan](https://github.com/8Masum).
