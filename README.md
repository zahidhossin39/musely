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

Musely turns the audio files already stored on your device into a fast, distraction-free library—no accounts, no ads, no analytics, and nothing leaves your phone.

## Features

- **Organized local library**: Scans device audio via Android `MediaStore` and observes changes automatically (`ContentObserver`), so newly added tracks appear without manual refreshes.
- **Browse by Songs, Albums, or Artists**: Switch views with Material 3 filter chips, jump into album tracklists sorted by track number or artist discographies, and play or shuffle any collection in one tap.
- **Instant multi-word search**: Filter across song titles, artists, and album names simultaneously in real time.
- **Expressive Now Playing view**: Breathing cover artwork (`0.88x` spring settle when paused), a play/pause button with Material 3 corner-shape morphing, interactive scrubbing, shuffle and repeat (`Off` / `All` / `One`) toggles, and an **Up Next** queue preview.
- **Floating Mini Player**: Persistent bottom card with live progress bar across library and detail screens.
- **Background & lock-screen playback**: Powered by a `MediaSessionService` with audio-only `ExoPlayer`, audio focus handling, unplug/noisy-output pause, and media notification artwork.
- **Smart cover art loading**: Resolves `MediaStore` track thumbnails, embedded audio picture frames (`MediaMetadataRetriever`), album art URIs, and fallback album tracks with an in-memory LRU cache.
- **Material 3 identity**: Light and dark themes built from Musely Violet (`#6755EF`) and Musely Lime (`#F4FF90`), edge-to-edge layout, time-of-day greetings, and Android 13+ monochrome themed launcher icons.

## Download

1. Download the latest APK from [GitHub Releases](https://github.com/zahidhossin39/musely/releases/latest).
2. Open the downloaded `.apk` file on your Android device (Android 8.0 / API 26 or newer).
3. Allow installation from unknown sources / your browser if prompted.

> [!NOTE]
> Test/release APKs published via CI are currently signed with the debug signing config (`app/build.gradle.kts`) so builds can be installed directly. See [SECURITY.md](SECURITY.md) for verification details.

## Privacy & Permissions

Musely does **not** request the `INTERNET` permission—all scanning, artwork decoding, and playback happen strictly on-device.

| Permission | Android Version | Purpose |
| :--- | :--- | :--- |
| `READ_MEDIA_AUDIO` | Android 13+ (API 33+) | Read local audio files from `MediaStore` |
| `READ_EXTERNAL_STORAGE` | Android 8.0–12 (API 26–32) | Read local audio files on pre-Android 13 devices |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Android 9+ | Keep music playing in the background with media notification controls |
| `WAKE_LOCK` | Android 8.0+ | Prevent CPU sleep from interrupting audio playback |

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language** | Kotlin (Compose Compiler `2.4.21`, JVM target 17) |
| **UI** | Jetpack Compose & Material 3 (`androidx.compose:compose-bom:2026.09.00`) |
| **Playback** | AndroidX Media3 ExoPlayer & MediaSession (`1.11.1`) |
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
│       │   │   ├── AlbumArt.kt        # Artwork pipeline & LRU cache
│       │   │   ├── Library.kt         # MediaStore query, grouping & search
│       │   │   ├── MainActivity.kt    # Edge-to-edge host activity
│       │   │   ├── MainViewModel.kt   # Library state & MediaController bridge
│       │   │   ├── PlaybackService.kt # Background MediaSessionService & ExoPlayer
│       │   │   └── ui/                # Compose screens, mini player, components & M3 theme
│       │   └── res/                   # Vector icons, adaptive/monochrome mipmaps, themes
│       └── test/kotlin/com/musely/app/ # JUnit 4 unit tests
├── CHANGELOG.md
├── CODE_OF_CONDUCT.md
├── CONTRIBUTING.md
├── LICENSE
└── SECURITY.md
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

# Build debug APK
./gradlew assembleDebug

# Build minified release APK (debug-signed for local testing)
./gradlew assembleRelease
```

On Windows, replace `./gradlew` with `gradlew.bat`.

### Releases

Pushing a version tag matching `v*` (for example `v1.0.0`) runs unit tests, builds `musely-<version>.apk`, generates a `.sha256` checksum, and publishes a GitHub Release automatically via [`.github/workflows/build.yml`](.github/workflows/build.yml).

## Contributing & Governance

- **Contributing guide**: [CONTRIBUTING.md](CONTRIBUTING.md)
- **Code of Conduct**: [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- **Changelog**: [CHANGELOG.md](CHANGELOG.md)
- **Security policy**: [SECURITY.md](SECURITY.md)

## License

Licensed under the [MIT License](LICENSE). Copyright © 2026 [zahidhossin39](https://github.com/zahidhossin39).
