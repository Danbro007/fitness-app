# Fitness Remaining Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the remaining fitness-app gaps beyond the local MVP draft: real AI request paths, vision-image request support, monthly planning, training adjustment suggestions, unfinished-session recovery, full library browsing, venue-equipment binding, onboarding state, and file-style backup entry points.

**Architecture:** Preserve local-first SQLite as the source of truth. AI calls use OpenAI-compatible request builders when credentials exist and deterministic local fallbacks when no key is available, so the app remains usable offline. UI continues in Compose while reusable behavior moves into small data/domain classes and Store/Repository methods.

**Tech Stack:** Kotlin, Jetpack Compose, manual SQLite, Android encrypted credential storage, Android activity result launchers, kotlinx.serialization JSON, Gradle unit and connected Android tests.

## Global Constraints

- No account, no cloud sync, no server persistence.
- API keys stay outside SQLite.
- AI output remains a draft until user confirmation.
- Do not show raw local image/resource paths in UI.
- Completion requires Gradle unit tests, debug APK build, androidTest APK build, connected tests, and emulator smoke evidence.

---

### Task 1: AI Request Completion

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/ai/AiChatClient.kt`
- Test: `app/src/test/java/com/shanqijie/fitnessapp/AiChatClientTest.kt`

- [x] Add tests for `buildVisionRequestJson(...)` including image data URL content and text prompt.
- [x] Add tests for `complete(...)` using a fake `AiHttpTransport`.
- [x] Implement `buildVisionRequestJson(...)` and `completeVision(...)`.
- [x] Run `./gradlew :app:testDebugUnitTest --tests com.shanqijie.fitnessapp.AiChatClientTest`.

### Task 2: Storage Completion

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessEntities.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessDatabase.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessStore.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessBackupCodec.kt`
- Test: `app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessStoreInstrumentedTest.kt`

- [x] Add red tests for venue-equipment binding, app preferences, training adjustment suggestions, and food image metadata.
- [x] Implement `venue_equipment`, `app_preference`, and `training_adjustment` tables.
- [x] Extend food logs with image metadata and provider/model fields.
- [x] Extend backup payload to include new tables.
- [x] Run focused connected store tests.

### Task 3: Repository Completion

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessRepository.kt`
- Test: `app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessRepositoryInstrumentedTest.kt`

- [x] Add red tests for monthly plan creation, unfinished-session recovery, full exercise search, venue-scoped equipment, training adjustment suggestion, onboarding completion, and image-food draft metadata.
- [x] Implement Repository methods for those flows.
- [x] Ensure AI-backed methods use provider credentials when available and fallback locally when unavailable.
- [x] Run focused connected repository tests.

### Task 4: Compose Flow Completion

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/MainActivity.kt`

- [x] Add first-run setup flow until the user marks setup complete.
- [x] Add full action-library search across all local exercises.
- [x] Add unfinished-training recovery card.
- [x] Add monthly plan controls and training-adjustment suggestion cards.
- [x] Make backup use Android document picker/create-document launchers in addition to text JSON.
- [x] Keep no local image/file paths visible in UI.

### Task 5: Verification

**Commands:**
- [x] `./gradlew :app:testDebugUnitTest`
- [x] `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`
- [x] `./gradlew :app:connectedDebugAndroidTest`
- [x] emulator install/launch/screenshot under `.scratch/run-evidence/`
