# Changelog

All notable changes to **Musely** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/zahidhossin39/musely/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/zahidhossin39/musely/compare/v0.2.0...v1.0.0
[0.2.0]: https://github.com/zahidhossin39/musely/releases/tag/v0.2.0
