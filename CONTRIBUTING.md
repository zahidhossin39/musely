# Contributing to Musely

Thanks for your interest in contributing to **Musely**! Musely is designed to be a calm, lightweight, 100% offline music player for Android. Keeping changes focused, readable, and dependency-light helps keep the app fast and maintainable.

## Getting Started

### Prerequisites

- **JDK 17+** (Temurin JDK 21 is used in GitHub Actions)
- **Android SDK** with **Android Platform 37** (`compileSdk = 37`, `minSdk = 26`, `targetSdk = 36`)
- **Android Studio** (recommended) or command-line Gradle via the included wrapper (`./gradlew`)

### Local Setup

1. Fork the repository and clone your fork:
   ```bash
   git clone https://github.com/<your-username>/musely.git
   cd musely
   ```
2. Ensure `ANDROID_HOME` is set or configure `local.properties` (ignored by Git):
   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```
3. Verify the project builds and passes unit tests:
   ```bash
   ./gradlew testDebugUnitTest assembleDebug
   ```
   On Windows, use `gradlew.bat`.

## Development Guidelines

### Design & Architecture Principles

- **Offline-first & privacy-preserving**: Do **not** add `INTERNET` permissions, analytics, crash trackers, or network dependencies.
- **Material 3 fidelity**: Respect Musely's brand palette (`MuselyViolet` `#6755EF` and `MuselyLime` `#F4FF90`) and `LocalSpotlight` contrast rules across light and dark modes.
- **Keep dependencies minimal**: Prefer standard AndroidX, Jetpack Compose, and Media3 APIs over adding external libraries.
- **Code style**: Follow Kotlin conventions and `.editorconfig` formatting rules (4-space indentation for Kotlin/Gradle/XML, 2-space indentation for YAML/Markdown).

### Testing Expectations

- Add or update unit tests in `app/src/test/kotlin/com/musely/app/` for non-UI logic (library grouping, search, artwork decoding/cache keys, formatting, detail builders).
- Ensure `./gradlew testDebugUnitTest` passes before opening a pull request.
- For UI or playback changes, verify behavior manually on an Android device or emulator (light/dark mode, permissions granted/declined, mini player, Now Playing, and media notification controls) and attach screenshots or screen recordings to your PR.

## Branching, Commits & Pull Requests

1. Create a topic branch from `main` using a clear prefix:
   - `feat/...` for new features
   - `fix/...` for bug fixes
   - `docs/...` for documentation updates
   - `chore/...` or `ci/...` for maintenance and workflow changes
   - `refactor/...` for internal code cleanup
2. Write clear, descriptive commit messages (Conventional Commits such as `feat: ...`, `fix: ...`, `docs: ...`, `chore: ...` are encouraged).
3. Open a pull request against `main`, fill out the PR template checklist, and confirm that the GitHub Actions `Build` workflow succeeds.

## Release Process (Maintainers)

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Update `CHANGELOG.md` and `.github/release-notes.md` with the release highlights and install instructions.
3. Merge changes into `main`.
4. Create and push an annotated version tag matching `v*`:
   ```bash
   git tag -a vX.Y.Z -m "Musely vX.Y.Z"
   git push origin vX.Y.Z
   ```
   Pushing a `v*` tag triggers [`.github/workflows/build.yml`](.github/workflows/build.yml) to run unit tests, build `musely-X.Y.Z.apk` with a `.sha256` checksum, and publish the GitHub Release.

> [!NOTE]
> Release builds in `app/build.gradle.kts` currently sign with the debug keystore so CI builds can be installed directly. Replace with a private release keystore before publishing to app stores.
