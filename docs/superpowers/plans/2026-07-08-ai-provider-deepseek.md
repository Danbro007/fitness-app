# AI Provider DeepSeek Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a local AI Provider settings flow and verify DeepSeek connectivity without hardcoding API keys.

**Architecture:** Store provider metadata in SQLite and API keys in Android Keystore encrypted app-private storage. Use an OpenAI-compatible chat client so DeepSeek is the first provider and GPT/GLM-style providers can reuse the same request path later. Expose a Compose AI tab for saving a key and running a small test request.

**Tech Stack:** Kotlin, Jetpack Compose Material3, SQLiteOpenHelper, Android Keystore, HttpURLConnection, kotlinx.serialization JSON, Android instrumentation tests.

## Global Constraints

- Local-first only: no account, no cloud sync, no backend.
- API keys must never be hardcoded in source, logs, screenshots, or SQLite plaintext.
- DeepSeek model defaults to `deepseek-v4-flash`.
- AI scheduling and food recognition are not implemented in this task.
- Test with a small text prompt only.

---

### Task 1: OpenAI-Compatible DeepSeek Client

**Files:**
- Create: `app/src/main/java/com/shanqijie/fitnessapp/ai/AiChatClient.kt`
- Test: `app/src/test/java/com/shanqijie/fitnessapp/AiChatClientTest.kt`

**Interfaces:**
- Produces: `data class AiProviderConfig`
- Produces: `class AiChatClient`
- Produces: `interface AiHttpTransport`
- Produces: `fun AiChatClient.buildTestRequestJson(prompt: String): String`
- Produces: `fun AiChatClient.parseAssistantContent(responseJson: String): String`

- [x] Write failing unit tests for DeepSeek request JSON and assistant response parsing.
- [x] Run `./gradlew :app:testDebugUnitTest --tests com.shanqijie.fitnessapp.AiChatClientTest`.
- [x] Add minimal client and fake transport support.
- [x] Re-run the focused unit test.

### Task 2: Provider Metadata And Secure Key Storage

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessEntities.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessDatabase.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessStore.kt`
- Create: `app/src/main/java/com/shanqijie/fitnessapp/data/AiCredentialStore.kt`
- Test: `app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessStoreInstrumentedTest.kt`
- Test: `app/src/androidTest/java/com/shanqijie/fitnessapp/AiCredentialStoreInstrumentedTest.kt`

**Interfaces:**
- Produces: `data class AiProviderEntity`
- Produces: `fun upsertAiProvider(entity: AiProviderEntity)`
- Produces: `fun aiProvider(id: String): AiProviderEntity?`
- Produces: `class AiCredentialStore`
- Produces: `fun saveApiKey(providerId: String, apiKey: String)`
- Produces: `fun loadApiKey(providerId: String): String?`

- [x] Write failing instrumentation tests for provider persistence and encrypted key round-trip.
- [x] Run focused Android test and verify red.
- [x] Add DB table version 3 and secure key store.
- [x] Re-run focused Android test and verify green.

### Task 3: Repository And Compose AI Tab

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessRepository.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/MainActivity.kt`

**Interfaces:**
- Consumes: `AiChatClient`
- Consumes: `AiCredentialStore`
- Produces: `fun saveAiApiKey(providerId: String, apiKey: String)`
- Produces: `fun testAiProvider(providerId: String): AiTestResult`

- [x] Add `INTERNET` permission.
- [x] Seed DeepSeek provider metadata during bootstrap.
- [x] Add AI tab with API key input, save button, and test button.
- [x] Show only key status and test result, never the key itself.

### Task 4: Verification

**Files:**
- Create: `.scratch/run-evidence/fitness-app-ai-*.png`

- [x] Run `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`.
- [x] Install latest APK on `emulator-5554`.
- [x] Run instrumentation tests and confirm `OK`.
- [x] Launch from empty app data and capture AI tab screenshot.
- [x] Use the provided DeepSeek key for one live test request without storing it in source.
