---
name: i fitness Dependency Notices
version: 1.0.0
last_updated: 2026-07-11
---

# Dependency Notices

This inventory covers the direct Gradle dependencies declared by `app/build.gradle.kts` and the build tooling used to produce the Android app. Their upstream license files remain authoritative.

| Component | Declared version | Scope | License |
| --- | --- | --- | --- |
| AndroidX Activity, Compose, Lifecycle, DataStore, Test, Espresso | Versions managed by Compose BOM `2025.06.01` or declared in Gradle | Runtime / test | Apache-2.0 |
| Jetpack Compose Material 3, UI, Animation, Material Icons | Compose BOM `2025.06.01` | Runtime | Apache-2.0 |
| Coil Compose and Coil GIF | `2.7.0` | Runtime | Apache-2.0 |
| Kotlin and Kotlinx Serialization JSON | Kotlin plugin / `1.8.1` | Runtime / build | Apache-2.0 |
| JUnit 4 | `4.13.2` | JVM test | EPL-1.0 |
| Android Gradle Plugin / Gradle Wrapper | Project build configuration | Build only | Apache-2.0 |

Primary license sources: [Android Open Source Project](https://source.android.com/docs/setup/about/licenses), [Kotlin](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt), [Coil](https://github.com/coil-kt/coil/blob/main/LICENSE.txt), and [JUnit 4](https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt).

Before publishing an APK, generate and review the resolved runtime dependency tree for the exact release build, retain any notices required by transitive dependencies, and include this file with the distribution.
