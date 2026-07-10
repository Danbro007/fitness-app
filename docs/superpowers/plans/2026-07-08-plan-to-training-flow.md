# Plan To Training Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a calendar workout plan contain concrete exercises and let the user start training from that plan with GIF guidance and per-exercise set records.

**Architecture:** Add a local SQLite `planned_exercise` table linked to `planned_workout`. Seed three local workout plans with exercise rows from the existing GIF manifest. Expose selected plan exercises through the repository and update Compose so calendar plan selection drives the training screen.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Android SQLiteOpenHelper, Coil GIF, Kotlin Flow, Android instrumentation tests.

## Global Constraints

- Local-first only: no account, no cloud sync, no network dependency.
- SQLite remains the source of truth for plan and training data.
- GIF resources stay local assets.
- AI scheduling and food recognition remain deferred until this local plan-to-training workflow is stable.
- Keep scope personal-use and single-user.

---

### Task 1: Planned Exercise Persistence

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessEntities.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessDatabase.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessStore.kt`
- Modify: `app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessStoreInstrumentedTest.kt`

**Interfaces:**
- Produces: `data class PlannedExerciseEntity`
- Produces: `fun upsertPlannedExercise(entity: PlannedExerciseEntity)`
- Produces: `fun plannedExercises(plannedWorkoutId: String): List<PlannedExerciseEntity>`
- Produces: `fun allPlannedExercises(): List<PlannedExerciseEntity>`
- Produces: `fun completedSetCount(sessionId: String, exerciseId: String): Int`
- Produces: `fun setLogs(sessionId: String, exerciseId: String): List<WorkoutSetLogEntity>`

- [ ] Write failing instrumentation assertions for planned exercises and exercise-scoped set logs.
- [ ] Verify red with `./gradlew :app:compileDebugAndroidTestKotlin`.
- [ ] Add table/entity/store methods.
- [ ] Verify green with manual instrumentation on emulator.

### Task 2: Repository Plan State

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessRepository.kt`

**Interfaces:**
- Produces: `data class PlannedExerciseView`
- Produces: `plannedExercises: List<PlannedExerciseEntity>` in `FitnessAppState`
- Produces: `fun exerciseLogs(sessionId: String, exerciseId: String): Flow<List<WorkoutSetLogEntity>>`
- Produces: `fun completedSetCount(sessionId: String, exerciseId: String): Flow<Int>`
- Produces: `fun sessionIdFor(plannedWorkoutId: String, exerciseId: String): String`

- [ ] Seed each default plan with concrete exercise rows.
- [ ] Map planned exercise rows to exercise media in the UI layer through state.
- [ ] Keep existing `completeSet` but support exercise-scoped reads.
- [ ] Run `./gradlew :app:testDebugUnitTest`.

### Task 3: Calendar-To-Training UI

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/MainActivity.kt`

**Interfaces:**
- Consumes: `FitnessAppState.plannedExercises`
- Consumes: `FitnessRepository.sessionIdFor(plannedWorkoutId, exerciseId)`
- Consumes: `FitnessRepository.completedSetCount(sessionId, exerciseId)`
- Consumes: `FitnessRepository.exerciseLogs(sessionId, exerciseId)`

- [ ] Make calendar plan rows clickable.
- [ ] When a plan is selected, switch to training tab and show plan title.
- [ ] Show exercise chips/cards for the selected plan.
- [ ] Selecting an exercise changes the GIF card and set recorder.
- [ ] Per-exercise completed set count and history must update separately.

### Task 4: Verification

**Files:**
- Create: `.scratch/run-evidence/fitness-app-plan-to-training-*.png`

- [ ] Run `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`.
- [ ] Install and launch APK on `emulator-5554`.
- [ ] Confirm bootstrap log contains `gifs=1324`.
- [ ] Run manual instrumentation and confirm `OK`.
- [ ] Capture training and calendar screenshots.
