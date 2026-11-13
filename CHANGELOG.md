# Changelog

All notable changes to **Musely** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [3.0.0] - 2026-11-13

### Added
- **Videos** tab and direct video-as-audio playback via `MediaStore.Video.Media` (`loadVideos`, `videoTrackId`, `trackUri`) streamed through the audio-only `ExoPlayer` service without copying or extracting audio files
- Playlist, **Liked Music** starring, multi-word search, hide/unhide, and device deletion support for video tracks, with `All` / `Songs` / `Videos` filter chips in `AddSongsPickerSheet`
- Collision-free `"trackKeys"` (`"a:<id>"` / `"v:<mediaId>"`) JSON serialization alongside backward-compatible `"songIds"` and permission-aware pruning in `PlaylistStore`
- Video thumbnail/frame artwork extraction in `ArtCache` (`loadForVideo`, `loadVideoFrame`), `PlaylistArtwork`, `MiniPlayer`, `NowPlayingScreen`, and notifications (`CoverBitmapLoader`)
- Video audio-track inspection (`METADATA_KEY_HAS_AUDIO`) in `FileInfoSheet`, silent/unsupported audio track warnings (`MainViewModel.PlaybackMessages`), and `STATE_ENDED` / `STATE_IDLE` play recovery
- Android 13+ `READ_MEDIA_VIDEO` and Android 14+ `READ_MEDIA_VISUAL_USER_SELECTED` permission onboarding and `"Select more"` refresh flow
- Unit tests for video indexing/search, key encoding/pruning, video collage pairs, format helpers, and playback error/warning logic (`LibraryTest`, `PlaylistStoreTest`, `PlaylistLogicTest`, `DetailTest`, `FormatTest`, `VideoAudioReviewTest`)

### Changed
- Positioned `SnackbarHost` in the top overlay layer of `MuselyApp` so warnings and action feedback appear above `NowPlayingScreen` as well as library screens
- Fixed Android 10 (`RecoverableSecurityException`) deletion retry in `PlaylistSheets` so external media is actually deleted after confirmation
- Ignored `MediaStore.UNKNOWN_STRING` placeholders in `resolveVideoTitle` and used `ic_video` across empty/hidden video states
- Bumped default `versionCode` to `5` and `versionName` to `3.0.0` in `app/build.gradle.kts`

## [2.0.0] - 2026-11-13

### Added
- Custom playlists with YouTube-Music-inspired 2×2 cover artwork collages, multi-song add, track reordering, rename, and delete (`PlaylistStore`)
- Pinned **Liked Music** system playlist backed by starred songs across library, detail, and Now Playing views
- GitHub Releases auto-updater (`AppUpdater`) with top-bar update icon, unobtrusive availability badge, SemVer comparison, 403/429 HTML redirect fallback, and browser handoff via `Intent.ACTION_VIEW`
- Per-song management actions (`SongActions`): File Info sheet (`MediaStore` and `MediaMetadataRetriever` metadata), Hide/Unhide from library, and user-confirmed Delete from device storage
- Unit tests covering playlists, metadata formatting, and update checking
- Repository hygiene, community health/governance docs, issue/PR templates, `CODEOWNERS`, and `dependabot.yml`

### Changed
- Declared `INTERNET` permission (scoped solely to `zahidhossin39/musely` GitHub Releases update checks) and `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="29"`) for user-confirmed file deletion on Android 8.0–10
- Hardened `.github/workflows/build.yml` with persistent release keystore signing (`RELEASE_KEYSTORE_*` secrets with debug fallback for PRs/forks), `lintDebug`, test/lint report artifacts, canonical `Musely.apk` alongside `musely-<version>.apk`, and `.sha256` checksums
- Updated default `versionCode` to `4` and `versionName` to `2.0.0` in `app/build.gradle.kts`

## [1.0.0] - 2026-11-12

### Added
- Unit test suites covering album art sampling/scaling/caching (`AlbumArtTest`), library grouping and search (`LibraryTest`), and album/artist detail models (`DetailTest`)
- Standard, round, adaptive, and monochrome/themed launcher icon mipmaps generated from `Logo/musely.png`

### Changed
- Updated welcome screen emblem scaling to fill its container cleanly without letterboxing
- Refined adaptive launcher icon background bleed across all screen densities
- Improved notification and MediaSession cover artwork scaling and negative-lookup cache retry handling (`ArtCache.clearMissing`, `CoverBitmapLoader`)

### Fixed
- Cover artwork loading across Songs, Albums, Album Detail, Mini Player, Now Playing, Up Next, and playback notifications using MediaStore thumbnails, embedded audio picture frames, official album URIs, and legacy fallbacks

## [0.2.0] - 2026-11-12

### Added
- Rebranded the application from Waveloop to **Musely** (`com.musely.app`)
- Material 3 light and dark color schemes built from Musely Violet (`#6755EF`) and Musely Lime (`#F4FF90`)
- Onboarding welcome screen with audio permission request and app-settings recovery path
- Time-of-day library greeting, real-time multi-word search, and filter chips for Songs, Albums, and Artists
- Album and artist detail screens with track sorting, duration totals, Play, and Shuffle actions
- Floating mini player with live progress indicator and full-screen Now Playing view with breathing artwork, morphing play button, shuffle/repeat controls, and Up Next queue peek
- Automated GitHub Actions workflow to build release APKs and publish `v*` tag releases

## [0.1.0] - 2026-11-12

### Added
- Initial Android project skeleton (originally named Waveloop, `com.waveloop.player`) with Jetpack Compose UI, MediaStore library loading, in-memory search, and background Media3 `PlaybackService`
- MIT `LICENSE` and initial GitHub Actions build workflow

[Unreleased]: https://github.com/zahidhossin39/musely/compare/v3.0.0...HEAD
[3.0.0]: https://github.com/zahidhossin39/musely/compare/v2.0.0...v3.0.0
[2.0.0]: https://github.com/zahidhossin39/musely/compare/v1.0.0...v2.0.0
[1.0.0]: https://github.com/zahidhossin39/musely/compare/v0.2.0...v1.0.0
[0.2.0]: https://github.com/zahidhossin39/musely/compare/0361685...v0.2.0
[0.1.0]: https://github.com/zahidhossin39/musely/compare/659d5f5...0361685
