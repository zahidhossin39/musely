# Security Policy

## Supported Versions

Security updates are provided for the latest release line:

| Version | Supported |
| :--- | :--- |
| `3.0.x` | :white_check_mark: Supported |
| `< 3.0.0` | :x: Unsupported |

## Privacy & Threat Model

Musely is designed as an **offline-first** Android audio player:

- Audio and video library scanning, artwork/frame loading, playlists, and playback operate strictly on-device using local `MediaStore` and embedded media metadata (playing video audio streams directly without copying files).
- The `INTERNET` permission in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) is scoped solely to read-only update checks against `zahidhossin39/musely` GitHub Releases (and opening the release `.apk` URL in your system browser via `Intent.ACTION_VIEW`—no `REQUEST_INSTALL_PACKAGES` or in-app downloader is used).
- No background telemetry, crash reporting SDKs, ads, or accounts are included.

## Release APK Signing Notice

- Download APKs **only** from the official [GitHub Releases](https://github.com/zahidhossin39/musely/releases) page.
- As configured in [`app/build.gradle.kts`](app/build.gradle.kts) and [`.github/workflows/build.yml`](.github/workflows/build.yml), CI signs release APKs with a persistent release keystore when `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` secrets are configured (falling back to the Android debug keystore on PRs or forks).
- Releases produced by CI include `.sha256` checksums alongside `Musely.apk` and `musely-<version>.apk` so downloads can be verified before installation.

## Reporting a Vulnerability

Please **do not** disclose security vulnerabilities in public GitHub issues or pull requests.

Instead, please report suspected vulnerabilities privately:

1. Use **GitHub Private Vulnerability Reporting** via the **Security → Advisories** tab when enabled on the repository.
2. Otherwise, email the repository maintainer directly at [zahidhossin39@gmail.com](mailto:zahidhossin39@gmail.com) (see [@zahidhossin39](https://github.com/zahidhossin39)) before sharing details publicly.

When reporting an issue, please include:

- App version and Android OS version
- Steps to reproduce or proof of concept
- Potential impact (e.g., malformed media file parsing, intent/service exposure, permission handling)

We aim to acknowledge reports within **7 days** and coordinate a fix and release prior to public disclosure.
