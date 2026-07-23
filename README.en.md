---
name: i fitness README (English)
description: English GitHub entrypoint for the i fitness local-first Android fitness assistant.
version: 1.2.0
last_updated: 2026-07-11
maintained_by: shanqijie
---

<p align="right">
  <a href="./README.md">简体中文</a> | <strong>English</strong>
</p>

# i fitness

A local-first Android fitness assistant for managing venues, equipment, workout plans, exercise metadata, workout logs, food estimates, and AI-assisted suggestions. The app does not require an account or cloud sync by default, and core data is stored locally in SQLite.

## Project Status

- The native Compose MVP is complete, with exactly five primary destinations: `Home / Plan / Training / Food / Profile`.
- The core journey is implemented end to end: planning, exercise selection, active training, rest timing, workout summary, food logging, AI settings, and local backup.
- The manifest contains 1,324 exercise metadata records. Personal/local debug builds render the locally available exercise GIFs directly; public or commercial distribution remains a separate rights-review decision.
- Verified with real local data on a Pixel 8 Pro emulator: 29/29 JVM tests and 61/61 device tests pass, with no crash or ANR.

## Features

### Local First

- Local SQLite stores venues, equipment, exercise media, plans, workout logs, food logs, and AI drafts.
- No account, no default cloud sync, and no backend dependency.
- JSON backup export/import is available for moving local data.

### Workout Planning

- Generate workout plans based on the selected venue and available equipment.
- Weekly and monthly planning entry points.
- Edit plans, adjust exercises, skip workouts, copy workouts, and reschedule dates.
- Resume unfinished workout sessions.

### Workout Session

- Log sets, reps, weight, and training feedback.
- View exercise GIFs in personal/local builds, use rest timers, replace exercises, and skip exercises.
- Generate next-session adjustment suggestions from training history.
- Active-workout and workout-summary screens are immersive and hide global bottom navigation.

### Exercise Library

- Includes 1,324 MIT-licensed exercise metadata records; GIF binaries are not committed.
- Exercise media belongs to Gym Visual and is not covered by the metadata's MIT license.
- Filter by chest, back, legs, core, and other categories.
- Search exercises and view exercise details.
- The library uses virtualized rendering to avoid loading too many GIF rows at once.

### Food and AI

- Generate calorie-estimate drafts from a food photo or text description.
- AI output must be confirmed before it is saved to local food logs.
- Uses an OpenAI-compatible request format, with a DeepSeek configuration entry by default.
- API keys are stored with Android Keystore + AES/GCM and are not written into SQLite.
- When a user invokes AI, submitted text and a selected food image may be transmitted to the configured AI provider.

## Tech Stack

- Kotlin
- Jetpack Compose + Material 3
- Manual SQLite via `SQLiteOpenHelper`
- Android Keystore for encrypted AI credentials
- Coil + GIF decoder for exercise media
- Kotlinx Serialization JSON
- JUnit, AndroidX Test, Compose UI Test
- Gradle Android Plugin 8.11.1

## Project Structure

```text
.
├── app/
│   └── src/
│       ├── main/
│       │   ├── assets/exercise-media/     # Exercise manifest with remote GIF URLs
│       │   ├── java/com/shanqijie/fitnessapp/
│       │   │   ├── MainActivity.kt        # Android entry point mounting the native UI root
│       │   │   ├── ai/                    # AI request client
│       │   │   ├── data/                  # SQLite, repository, backup, credentials
│       │   │   ├── domain/                # Workout state, summaries, and local rules
│       │   │   └── ui/                    # Five-tab navigation, theme, components, and screens
│       │   └── res/
│       ├── test/                          # JVM unit tests
│       └── androidTest/                   # Instrumented Android tests
├── docs/superpowers/plans/                # Implementation plans and verification notes
├── scripts/                               # Asset download helpers
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Quick Start

### Requirements

- Android Studio or Android SDK command-line tools
- JDK 17
- Android SDK 36
- Android 8.0+ device or emulator; the app uses `minSdk = 26`

### Build a Debug APK

Exercise media is outside the dataset's MIT license. Use the downloader only if you have already obtained the necessary rights from the media owner:

```bash
python3 scripts/download-exercise-gifs.py --i-have-media-license
```

Download only the first 10 assets for a quick check:

```bash
python3 scripts/download-exercise-gifs.py --limit 10 --i-have-media-license
```

```bash
./gradlew :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Install on a Device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.shanqijie.fitnessapp/.MainActivity
```

## Testing

| Verification surface | Result |
| :--- | :--- |
| JVM unit tests | 29/29 passed |
| Pixel 8 Pro device/UI tests | 61/61 passed |
| Debug and AndroidTest APKs | Built successfully |
| Real SQLite workflow | Workout, set log, and food log verified |

Run JVM unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

Build Debug and AndroidTest APKs:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
```

Run connected-device tests:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Full verification command:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest
```

## Data and Privacy

- Workout and food data are stored locally by default.
- Backup files are exported or imported only by explicit user action.
- AI output is saved as a draft and only becomes a record after user confirmation.
- API keys are encrypted with Android Keystore.
- AI prompts and selected food images are sent to the configured third-party provider when the user invokes AI, subject to that provider's terms and privacy policy.
- The project has no default account system, social features, or cloud sync.

## Exercise Media Assets

This project does not commit third-party GIF binaries. Exercise metadata and exercise media have separate rights boundaries. The current manifest contains:

- `datasetRecordsWithMedia`: 1324
- `exercise-gifs-pack-001`: 1,324 assets, about 122.78 MB after a complete download

Asset manifest:

```text
app/src/main/assets/exercise-media/manifest.json
```

Download script:

```text
scripts/download-exercise-gifs.py
```

- Exercise metadata and structure: upstream MIT License.
- Exercise images and GIFs: © Gym visual, excluded from the MIT license.

See [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md) for attribution, license boundaries, and dependency references.

## Legal and Release Boundaries

- This is a public source repository; third-party GIF binaries are not pushed to GitHub.
- `i fitness` is a working product name; complete trademark clearance before commercialization or a public release.
- Workout, nutrition, and AI suggestions are informational and are not medical diagnosis, treatment, or individualized nutrition prescriptions.
- Before distributing an APK publicly, complete a privacy policy, media licensing, dependency-license inventory, and launcher-art provenance record.

## AI Configuration

The app provides a DeepSeek-style OpenAI-compatible configuration entry. When no API key is configured, AI-related flows use local fallbacks so the app remains usable offline.

## Design Principles

- The home screen focuses on today's workout and the most important next action.
- Complex forms such as venue, equipment, and plan editing use secondary pages to avoid crowded main screens.
- Workout mode prioritizes the current exercise, sets, weight, reps, feedback, and rest state.
- Local resource paths are not shown directly to users.

## Current Limitations

- This is a personal-use-first MVP and does not include accounts, cloud sync, or social features.
- AI suggestions are assistive and should not replace professional medical, nutrition, or training advice.
- Personal/local debug builds do not block locally available GIFs on a media-rights flag; public or commercial builds must not infer distribution rights from this local-use behavior.

## License

Project code is available under the [MIT License](./LICENSE). Third-party data, media, and dependency boundaries are documented in [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md) and [DEPENDENCY_NOTICES.md](./DEPENDENCY_NOTICES.md). See [PRIVACY_POLICY.md](./PRIVACY_POLICY.md) for privacy information.
