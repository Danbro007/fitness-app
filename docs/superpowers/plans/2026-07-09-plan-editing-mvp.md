# Plan Editing MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user open a planned workout from the Plan tab, edit its name/date and per-exercise target parameters on a secondary page, save those changes locally, and start training from the edited plan.

**Architecture:** SQLite remains the source of truth. `FitnessStore` owns update statements, `FitnessRepository` validates and refreshes app state, and `MainActivity.kt` adds a secondary Plan Edit screen rather than adding more controls to the calendar list.

**Tech Stack:** Kotlin, Jetpack Compose Material3, SQLiteOpenHelper, Kotlin Flow, Android instrumentation tests, Gradle.

## Global Constraints

- Local-first only: no account, no cloud sync, no backend.
- SQLite remains the app storage boundary.
- AI generation stays deferred; this slice prepares the local editing/confirmation target AI will reuse later.
- Plan editing must use a secondary page so the Plan tab remains scan-friendly.
- Every persistence behavior must be covered by a failing test before implementation.

---

### Task 1: Store Update Methods

**Files:**
- Modify: `app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessStoreInstrumentedTest.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessStore.kt`

**Interfaces:**
- Produces: `fun updatePlannedWorkoutDetails(id: String, name: String, scheduledDate: String, updatedAt: Long)`
- Produces: `fun updatePlannedExerciseTarget(id: String, targetSets: Int, targetReps: String, targetWeightKg: Double, note: String)`

- [x] Write failing instrumentation test:

```kotlin
@Test
fun updatesPlannedWorkoutDetailsAndExerciseTargets() {
    store.upsertPlannedWorkout(
        PlannedWorkoutEntity("plan-1", "胸部力量 A", "2026-07-09", "venue-1", "planned", 1000L, 1000L),
    )
    store.upsertPlannedExercise(
        PlannedExerciseEntity("plan-1-exercise-1", "plan-1", "0748", 1, 4, "8-12", 70.0, "主项"),
    )

    store.updatePlannedWorkoutDetails("plan-1", "胸背力量 A", "2026-07-10", updatedAt = 2000L)
    store.updatePlannedExerciseTarget("plan-1-exercise-1", targetSets = 5, targetReps = "6-8", targetWeightKg = 82.5, note = "加重")

    assertEquals("胸背力量 A", store.plannedWorkouts().single().name)
    assertEquals("2026-07-10", store.plannedWorkouts().single().scheduledDate)
    assertEquals(2000L, store.plannedWorkouts().single().updatedAt)
    assertEquals(5, store.plannedExercises("plan-1").single().targetSets)
    assertEquals("6-8", store.plannedExercises("plan-1").single().targetReps)
    assertEquals(82.5, store.plannedExercises("plan-1").single().targetWeightKg, 0.01)
}
```

- [x] Run red test:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: compilation fails because the two store methods do not exist.

- [x] Implement the two SQLite update methods in `FitnessStore`.

- [x] Run green test:

```bash
./gradlew :app:assembleDebugAndroidTest
```

Expected: Android test APK compiles.

### Task 2: Repository Save API

**Files:**
- Create: `app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessRepositoryInstrumentedTest.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessRepository.kt`

**Interfaces:**
- Consumes: `FitnessStore.updatePlannedWorkoutDetails(...)`
- Consumes: `FitnessStore.updatePlannedExerciseTarget(...)`
- Produces: `suspend fun updatePlannedWorkoutDetails(id: String, name: String, scheduledDate: String)`
- Produces: `suspend fun updatePlannedExerciseTarget(id: String, targetSets: Int, targetReps: String, targetWeightKg: Double, note: String)`

- [x] Write failing repository instrumentation test:

```kotlin
@Test
fun repositorySavesPlanEditorChangesAndRefreshesState() = runBlocking {
    store.upsertPlannedWorkout(
        PlannedWorkoutEntity("plan-1", "胸部力量 A", "2026-07-09", "venue-1", "planned", 1000L, 1000L),
    )
    store.upsertPlannedExercise(
        PlannedExerciseEntity("plan-1-exercise-1", "plan-1", "0748", 1, 4, "8-12", 70.0, "主项"),
    )

    repository.updatePlannedWorkoutDetails("plan-1", "胸背力量 A", "2026-07-10")
    repository.updatePlannedExerciseTarget("plan-1-exercise-1", 5, "6-8", 82.5, "加重")

    val state = repository.appState().first()
    assertEquals("胸背力量 A", state.plannedWorkouts.single().name)
    assertEquals("2026-07-10", state.plannedWorkouts.single().scheduledDate)
    assertEquals(5, state.plannedExercises.single().targetSets)
    assertEquals("6-8", state.plannedExercises.single().targetReps)
}
```

- [x] Run red test:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: compilation fails because repository methods do not exist.

- [x] Implement repository validation and refresh:

```kotlin
require(name.trim().isNotEmpty()) { "计划名称不能为空" }
LocalDate.parse(scheduledDate.trim())
require(targetSets in 1..20) { "目标组数需要在 1 到 20 之间" }
require(targetReps.trim().isNotEmpty()) { "目标次数不能为空" }
require(targetWeightKg >= 0.0) { "目标重量不能为负数" }
```

- [x] Run green test:

```bash
./gradlew :app:assembleDebugAndroidTest
```

Expected: Android test APK compiles.

### Task 3: Plan Edit Secondary Page

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/MainActivity.kt`

**Interfaces:**
- Consumes: `FitnessRepository.updatePlannedWorkoutDetails(...)`
- Consumes: `FitnessRepository.updatePlannedExerciseTarget(...)`
- Produces: `PlanEditScreen(...)`
- Produces: `PlanExerciseTargetCard(...)`

- [x] Add `editingPlanId` to the root screen state and hide bottom navigation while editing.
- [x] Change calendar plan rows to open the secondary Plan Edit page with a `查看` action.
- [x] Add a secondary Plan Edit screen with:
  - editable plan name
  - editable scheduled date string
  - per-exercise set count stepper
  - per-exercise reps field
  - per-exercise weight stepper
  - `保存计划` primary action
  - `开始训练` secondary action that opens the selected workout
- [x] Run compile verification:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: build succeeds.

### Task 4: End-To-End Verification

**Files:**
- Read: `app/build/outputs/apk/debug/app-debug.apk`
- Create: `.scratch/run-evidence/android-plan-edit-page.png`

- [x] Run full local verification:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

- [x] Install and launch on `emulator-5554`.
- [x] Navigate: Plan tab -> planned workout `查看` -> Plan Edit page.
- [x] Save at least one changed target and confirm the page remains usable.
- [x] Capture screenshot:

```bash
/Users/shanqijie/Library/Android/sdk/platform-tools/adb exec-out screencap -p > .scratch/run-evidence/android-plan-edit-page.png
```

Expected: screenshot shows the secondary plan editor with no bottom tab bar and no crowded inline calendar controls.
