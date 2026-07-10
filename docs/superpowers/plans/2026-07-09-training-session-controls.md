# Training Session Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the training screen skip the current exercise and finish the active planned workout, with all state persisted in local SQLite.

**Architecture:** Keep SQLite as the source of truth. `FitnessStore` exposes narrow status update methods, `FitnessRepository` owns session creation and refresh, and `MainActivity.kt` adds low-frequency controls below the set recorder without changing the core workout card layout.

**Tech Stack:** Kotlin, Jetpack Compose Material3, SQLiteOpenHelper, Kotlin Flow, Android instrumentation tests, Gradle.

## Global Constraints

- Local-first only: no account, no cloud sync, no backend.
- SQLite remains the app storage boundary.
- AI scheduling, food recognition, and backup/restore stay deferred.
- Training controls must keep the main set recorder focused; skip and finish are lower-priority actions.
- Every persistence behavior must be covered by a failing test before implementation.

---

### Task 1: Store Status Updates

**Files:**
- Modify: `app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessStoreInstrumentedTest.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessStore.kt`

**Interfaces:**
- Produces: `fun updateWorkoutSessionStatus(id: String, status: String, endedAt: Long?, updatedAt: Long)`
- Produces: `fun updatePlannedWorkoutStatus(id: String, status: String, updatedAt: Long)`

- [x] Write a failing instrumentation test that inserts one `planned_workout` and one `workout_session`, calls both new update methods, and asserts persisted status/ended timestamps.
- [x] Verify red with:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: compilation fails because `updateWorkoutSessionStatus` and `updatePlannedWorkoutStatus` do not exist.

- [x] Implement the two SQLite `UPDATE` methods.
- [x] Verify green with:

```bash
./gradlew :app:assembleDebugAndroidTest
```

Expected: Android test APK compiles.

### Task 2: Repository Session Controls

**Files:**
- Modify: `app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessRepositoryInstrumentedTest.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessRepository.kt`

**Interfaces:**
- Consumes: `FitnessStore.updateWorkoutSessionStatus(...)`
- Consumes: `FitnessStore.updatePlannedWorkoutStatus(...)`
- Produces: `suspend fun skipExercise(sessionId: String, plannedWorkoutId: String?, venueId: String, exerciseId: String, setIndex: Int, reason: String)`
- Produces: `suspend fun finishWorkoutSession(sessionId: String, plannedWorkoutId: String?, venueId: String, exerciseId: String)`

- [x] Write a failing repository instrumentation test that calls `skipExercise(...)` and asserts a non-completed set log with `跳过` in `feeling`.
- [x] Extend the same test to call `finishWorkoutSession(...)` and assert the matching workout session is `completed`, `endedAt` is not null, and the planned workout status is `completed`.
- [x] Verify red with:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: compilation fails because repository session-control methods do not exist.

- [x] Implement repository methods. Each method must create the `WorkoutSessionEntity` first if it does not exist, then update status or insert the skipped log.
- [x] Verify green with:

```bash
./gradlew :app:assembleDebugAndroidTest
```

Expected: Android test APK compiles.

### Task 3: Training Screen Controls

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/MainActivity.kt`

**Interfaces:**
- Consumes: `FitnessRepository.skipExercise(...)`
- Consumes: `FitnessRepository.finishWorkoutSession(...)`

- [x] Add `SessionControlCard(...)` below `WorkoutInputCard`.
- [x] Show current session status text: `训练进行中`, `动作已跳过`, or `本次训练已结束`.
- [x] Add secondary action `跳过动作` that records a skipped log and disables the main completion button for that exercise.
- [x] Add secondary action `结束训练` that marks the session and selected plan as completed.
- [x] Keep bottom navigation visible; this is still the training tab, not a new secondary page.
- [x] Verify compile with:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: build succeeds.

### Task 4: End-To-End Verification

**Files:**
- Read: `app/build/outputs/apk/debug/app-debug.apk`
- Create: `.scratch/run-evidence/android-training-session-controls.png`

- [x] Run full local verification:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

- [x] Install latest APK and androidTest APK on `emulator-5554`.
- [x] Run full instrumentation and confirm `OK`.
- [x] Launch the app, open the training tab, tap `跳过动作`, then tap `结束训练`.
- [x] Capture screenshot:

```bash
/Users/shanqijie/Library/Android/sdk/platform-tools/adb exec-out screencap -p > .scratch/run-evidence/android-training-session-controls.png
```

Expected: screenshot shows the training screen with session status feedback and no crowded primary recorder layout.
