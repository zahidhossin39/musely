# Third-Party Notices

Musely's own source code is licensed under the [MIT License](LICENSE).

Since **v3.5.0**, the release APK also bundles the libraries below. Because it includes
**NewPipe Extractor**, which is licensed under the **GNU General Public License v3.0 or later**,
the APK as a whole is distributed under the terms of the GPL-3.0. The complete corresponding
source code is this repository, at the tag matching each release (for example `v3.5.0`).
MIT-licensed code is compatible with the GPL-3.0, so Musely's own files remain available under
the MIT License when used on their own.

## Download engine (bundled since v3.5.0)

| Library | Version | License | Source |
| :--- | :--- | :--- | :--- |
| NewPipe Extractor | v0.27.1 | GPL-3.0-or-later | https://github.com/TeamNewPipe/NewPipeExtractor |
| nanojson (TeamNewPipe fork) | e9d656d | Same as upstream nanojson | https://github.com/TeamNewPipe/nanojson |
| jsoup | 1.24.1 | MIT | https://jsoup.org |
| Mozilla Rhino | 1.8.1 | MPL-2.0 | https://github.com/mozilla/rhino |
| Protocol Buffers (Java Lite) | 4.36.2 | BSD-3-Clause | https://github.com/protocolbuffers/protobuf |
| JSR 305 annotations | 3.0.2 | Apache-2.0 | https://github.com/findbugsproject/findbugs |
| desugar_jdk_libs (NIO) | 2.1.5 | GPL-2.0 with Classpath Exception | https://github.com/google/desugar_jdk_libs |

## Android and Kotlin libraries

| Library | License | Source |
| :--- | :--- | :--- |
| AndroidX (Activity, Compose, Core, Lifecycle) | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX Media3 (ExoPlayer, Session) | Apache-2.0 | https://github.com/androidx/media |
| Kotlin standard library & kotlinx.coroutines | Apache-2.0 | https://github.com/JetBrains/kotlin |

## A note on content

Musely downloads media that the user chooses from YouTube, YouTube Music and, via matching on
YouTube Music, songs listed on Spotify. Spotify audio itself is never downloaded or decrypted.
You are responsible for making sure your use complies with the terms of those services and with
copyright law where you live.
