# Fitness MVP Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the local-first MVP gaps: user profile, food logging, AI drafts, richer plan/training controls, exercise details, venue management, and local backup/restore.

**Architecture:** Keep SQLite as the source of truth and keep AI output as drafts that require confirmation before writing durable app data. Extend the current manual SQLite store and Repository facade instead of introducing Room during this pass. Compose screens remain inside `MainActivity.kt` for speed, while backup/AI request helpers live in focused Kotlin files.

**Tech Stack:** Kotlin, Jetpack Compose, manual SQLite via `SQLiteOpenHelper`, kotlinx.serialization JSON, encrypted AI key storage, Gradle Android tests.

## Global Constraints

- Local-first only: no account, no cloud sync, no remote persistence.
- AI output is advisory: generated plan/food/replacement suggestions must become local records only after user confirmation.
- Visible UI strings should be Chinese; raw action/equipment values remain translated through presentation helpers.
- Completion requires fresh Gradle build/test evidence and an emulator smoke check when feasible.

---

### Task 1: Storage Model Completion

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessEntities.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessDatabase.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessStore.kt`
- Test: `app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessStoreInstrumentedTest.kt`

**Interfaces:**
- Produces: `UserProfileEntity`, `FoodLogEntity`, `AiDraftEntity`
- Produces: `FitnessStore.userProfile()`, `upsertUserProfile(...)`, `insertFoodLog(...)`, `foodLogs()`, `upsertAiDraft(...)`, `aiDrafts()`, `updateAiDraftStatus(...)`, `deleteEquipment(...)`, `deletePlannedWorkout(...)`, `updatePlannedExerciseExercise(...)`

- [x] **Step 1: Write failing instrumentation tests**

Add tests proving profile, food logs, AI drafts, equipment deletion, workout deletion, and exercise replacement persist correctly.

- [x] **Step 2: Run store tests to verify RED**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.shanqijie.fitnessapp.FitnessStoreInstrumentedTest`

Expected: compile/test failure because the new entities and store methods do not exist.

- [x] **Step 3: Implement database schema and store methods**

Add SQLite tables for `user_profile`, `food_log`, and `ai_draft`, bump `DATABASE_VERSION`, and implement the methods listed above.

- [x] **Step 4: Run store tests to verify GREEN**

Run the same instrumentation class. Expected: all `FitnessStoreInstrumentedTest` tests pass.

### Task 2: Repository Business Completion

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessRepository.kt`
- Create: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessBackupCodec.kt`
- Modify: `app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessRepositoryInstrumentedTest.kt`

**Interfaces:**
- Produces: `FitnessRepository.saveUserProfile(...)`, `logFood(...)`, `generateFoodEstimateDraft(...)`, `confirmAiDraft(...)`, `createWorkoutFromTemplate(...)`, `copyWorkout(...)`, `deleteWorkout(...)`, `rescheduleWorkout(...)`, `skipWorkout(...)`, `replaceExercise(...)`, `resumeSession(...)`, `exportBackupJson()`, `importBackupJson(...)`
- Produces: `FitnessBackupCodec.encode(...)` and `FitnessBackupCodec.decode(...)`

- [x] **Step 1: Write failing repository tests**

Add tests for plan creation/copy/delete/reschedule, workout skip status, exercise replacement, food draft confirmation, profile save, and backup export/import.

- [x] **Step 2: Run repository tests to verify RED**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.shanqijie.fitnessapp.FitnessRepositoryInstrumentedTest`

Expected: compile/test failure because the Repository methods do not exist.

- [x] **Step 3: Implement Repository behavior**

Implement local-first mutations and refresh the app state after each write. Use deterministic local draft text when an AI key is absent so the feature remains usable offline.

- [x] **Step 4: Run repository tests to verify GREEN**

Run the same instrumentation class. Expected: all `FitnessRepositoryInstrumentedTest` tests pass.

### Task 3: AI Client General Completion

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/ai/AiChatClient.kt`
- Modify: `app/src/test/java/com/shanqijie/fitnessapp/AiChatClientTest.kt`

**Interfaces:**
- Produces: `AiChatClient.buildChatRequestJson(systemPrompt, userPrompt, temperature)`
- Produces: `AiChatClient.complete(apiKey, systemPrompt, userPrompt, temperature)`

- [x] **Step 1: Write failing unit tests**

Add tests for OpenAI-compatible non-test chat request JSON and assistant-content parsing.

- [x] **Step 2: Run unit tests to verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests com.shanqijie.fitnessapp.AiChatClientTest`

Expected: compile/test failure until the new client methods exist.

- [x] **Step 3: Implement general chat completion helpers**

Reuse the same transport and parsing path as the connection test.

- [x] **Step 4: Run unit tests to verify GREEN**

Run the same unit test command. Expected: all `AiChatClientTest` tests pass.

### Task 4: Compose MVP Screens

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/MainActivity.kt`

**Interfaces:**
- Consumes: Repository methods from Task 2.
- Produces: visible pages for Food and Profile, plus UI controls for plan create/copy/delete/reschedule/skip, exercise replacement, training resume/history, AI drafts, backup export/import.

- [x] **Step 1: Add Food and Profile tabs**

Add `饮食` and `我的` navigation entries with compact labels and icons.

- [x] **Step 2: Implement Food screen**

Provide food estimate draft creation, confirmation into local food logs, and recent food log display.

- [x] **Step 3: Implement Profile screen**

Provide user profile editing, training history summary, local backup export/import text controls, and AI settings entry.

- [x] **Step 4: Extend Plan and Training screens**

Add create/copy/delete/reschedule/skip plan controls, replacement action controls, rest timer, resume unfinished status, last-weight display, and richer exercise detail tips.

### Task 5: Verification

**Files:**
- Update: `docs/superpowers/plans/2026-07-09-fitness-mvp-completion.md`

- [x] **Step 1: Run focused unit tests**

Run: `./gradlew :app:testDebugUnitTest`

- [x] **Step 2: Run Android test APK build**

Run: `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`

- [x] **Step 3: Run connected instrumentation**

Run: `./gradlew :app:connectedDebugAndroidTest`

- [x] **Step 4: Emulator smoke check**

Install/launch the debug APK on the running emulator, navigate through 首页/训练/计划/饮食/智能/我的, and capture screenshot evidence under `.scratch/run-evidence/`.
