# Security Policy

## Supported Versions

Security updates are provided for the latest release line:

| Version | Supported |
| :--- | :--- |
| `1.0.x` | :white_check_mark: Supported |
| `< 1.0.0` | :x: Unsupported |

## Privacy & Threat Model

Musely is designed as an **offline-only** Android audio player:

- The app does **not** declare the `INTERNET` permission in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml).
- Audio library scanning and artwork loading operate strictly on local `MediaStore` and embedded audio file data.
- No telemetry, crash reporting SDKs, ads, or accounts are included.

## Release APK Signing Notice

- Download APKs **only** from the official [GitHub Releases](https://github.com/zahidhossin39/musely/releases) page.
- As documented in [`app/build.gradle.kts`](app/build.gradle.kts), current CI/test release APKs are signed with the Android debug keystore (`signingConfigs.getByName("debug")`) so test builds can be installed directly without external signing secrets.
- Future releases produced by CI include a `.sha256` checksum alongside the `.apk` asset so downloads can be verified before installation.

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
