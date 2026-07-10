# Local Planning Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the next local-first MVP slice: editable venue, venue equipment, calendar-style weekly plan visibility, and action-library entry points connected to the existing training screen.

**Architecture:** Keep a single local SQLite database as source of truth. Add store methods first, expose small repository state flows, then reshape the Compose shell into four compact tabs: training, calendar, venue, library. No cloud/account/AI work in this slice.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Android SQLiteOpenHelper, Coil GIF, kotlinx.coroutines Flow, Android instrumentation tests.

## Global Constraints

- Local-first only: no account, no cloud sync, no network dependency for core app behavior.
- SQLite remains the app storage boundary.
- AI scheduling and food recognition stay deferred until local plan, venue, equipment, and training logs are stable.
- One default venue is enough for now, but the venue name must be editable.
- GIF resources are local assets; do not add on-demand downloads in this slice.

---

### Task 1: SQLite Store Expansion

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessStore.kt`
- Modify: `app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessStoreInstrumentedTest.kt`

**Interfaces:**
- Produces: `venue(id: String): TrainingVenueEntity?`
- Produces: `updateVenueName(id: String, name: String, updatedAt: Long)`
- Produces: `allEquipment(): List<EquipmentEntity>`
- Produces: `plannedWorkouts(): List<PlannedWorkoutEntity>`
- Produces: `workoutSessions(): List<WorkoutSessionEntity>`
- Produces: `equipmentNamesForVenue(): List<String>` using the current single-venue constraint.

- [ ] Write failing instrumentation assertions that venue name updates, equipment lists, and planned workouts persist.
- [ ] Run `./gradlew :app:assembleDebugAndroidTest`.
- [ ] Implement the missing store methods with SQLite queries and updates.
- [ ] Run manual instrumentation on the emulator:
  `adb shell am instrument -w -r -e class com.shanqijie.fitnessapp.FitnessStoreInstrumentedTest com.shanqijie.fitnessapp.test/androidx.test.runner.AndroidJUnitRunner`.

### Task 2: Repository State API

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessRepository.kt`

**Interfaces:**
- Produces: `fun appState(): Flow<FitnessAppState>`
- Produces: `suspend fun renameDefaultVenue(name: String)`
- Produces: `suspend fun addDefaultEquipment(name: String, category: String)`
- Produces: `data class FitnessAppState(...)`

- [ ] Add JVM-safe state shape types next to `BootstrapResult`.
- [ ] Implement repository refresh using a `MutableStateFlow<Int>` signal.
- [ ] Keep bootstrap idempotent: seed defaults only when missing, never overwrite a user-renamed venue.
- [ ] Run `./gradlew :app:testDebugUnitTest`.

### Task 3: Compose Four-Tab MVP Shell

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/MainActivity.kt`

**Interfaces:**
- Consumes: `FitnessRepository.appState()`
- Consumes: `FitnessRepository.renameDefaultVenue(name)`
- Consumes: `FitnessRepository.addDefaultEquipment(name, category)`

- [ ] Replace the single long screen with four segmented tabs: `训练`, `日历`, `场地`, `动作`.
- [ ] Keep the existing training card and GIF playback in `训练`.
- [ ] Add `日历` view showing seven days and planned workouts.
- [ ] Add `场地` view with venue name edit controls and equipment chips/list.
- [ ] Add `动作` view showing local GIF count, Smith machine count, and Smith bench GIF card.
- [ ] Run `./gradlew :app:compileDebugKotlin`.

### Task 4: Verification

**Files:**
- Read: `app/build/outputs/apk/debug/app-debug.apk`
- Create: `.scratch/run-evidence/*.png`

- [ ] Run `./gradlew :app:testDebugUnitTest`.
- [ ] Run `./gradlew :app:assembleDebug`.
- [ ] Install and launch on `emulator-5554`.
- [ ] Confirm log contains `FitnessApp: bootstrap gifs=1324`.
- [ ] Run the manual instrumentation command from Task 1.
- [ ] Capture a screenshot into `.scratch/run-evidence/fitness-app-plan-foundation.png`.
