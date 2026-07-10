# i fitness Native Android Layout Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the approved five-tab interactive prototype into the real Compose APK while preserving and completing the local SQLite workout, plan, food, profile, GIF, AI-draft, and backup workflows.

**Architecture:** Add a persisted one-session-per-workout runtime to SQLite/Repository, then build testable state-plus-action Compose screens and switch `MainActivity` to a five-tab sealed-route shell. Existing data APIs remain authoritative; UI-only state is limited to navigation, search, and unsubmitted form values.

**Tech Stack:** Kotlin 2.x, Jetpack Compose Material3, Android SQLiteOpenHelper, Kotlin coroutines/Flow, Coil GIF, JUnit4, AndroidX instrumented tests, Compose UI tests, ADB/UIAutomator.

## Global Constraints

- Do not add accounts, cloud sync, social features, remote image dependencies, or a new backend.
- Keep SQLite, local-first storage, existing Repository/Store data, and all 1324 local GIF assets.
- AI output remains a draft and is written to formal plan/food data only after explicit confirmation.
- Do not change raw exercise/API/test values; translate UI-facing names through `ExerciseChineseNameTranslator`.
- Bottom navigation is exactly `首页`, `计划`, `训练`, `饮食`, `我的`, in that order.
- Home exposes exactly one state-driven primary workout action.
- Training-active and workout-summary routes hide the bottom navigation; `完成本组` is visible without scrolling on the Pixel 8 Pro emulator.
- Orange is only for primary actions; green is for progress, success, and selection; normal body text contrast is at least 4.5:1.
- Every interactive target is at least 48dp and every meaningful image has a Chinese content description.
- Use existing Compose Material icons and dependencies; do not add Navigation Compose or a remote font.
- Final acceptance requires unit tests, connected instrumented/UI tests, APK build/install, real emulator interaction, screenshots, logs, and SQLite evidence.

---

### Task 1: Persist a real multi-exercise workout runtime (database v7)

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessEntities.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessDatabase.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessStore.kt`
- Create: `app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessWorkoutRuntimeStoreInstrumentedTest.kt`

**Interfaces:**
- Produces: `WorkoutSessionExerciseEntity`, runtime fields on `WorkoutSessionEntity`, optional `sessionExerciseId` on `WorkoutSetLogEntity`, and Store CRUD used by Task 2.

- [ ] **Step 1: Write failing database creation and v6→v7 migration tests**

Create tests named:

```kotlin
@Test fun createsWorkoutRuntimeTablesAndUniqueSetIndex()
@Test fun migratesV6HistoryAndRemovesOnlyEmptySeedSession()
@Test fun persistsSessionExercisesRuntimeAndLinkedSetLogs()
```

The tests must assert the exact columns `current_exercise_id`, `rest_ends_at`, `paused_at`, table `workout_session_exercise`, nullable `session_exercise_id`, and uniqueness of `(session_id, session_exercise_id, set_index)` for non-null runtime rows. Migration must preserve a legacy session with logs and remove only `session-local-smith-bench` when it is `in_progress` with zero logs.

- [ ] **Step 2: Run RED**

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.shanqijie.fitnessapp.FitnessWorkoutRuntimeStoreInstrumentedTest
```

Expected: FAIL because the runtime schema and Store methods do not exist.

- [ ] **Step 3: Add the v7 entity and schema contracts**

Use these exact entity fields:

```kotlin
data class WorkoutSessionEntity(
    val id: String,
    val plannedWorkoutId: String?,
    val venueId: String,
    val exerciseId: String,
    val status: String,
    val startedAt: Long,
    val endedAt: Long?,
    val updatedAt: Long,
    val currentExerciseId: String? = null,
    val restEndsAt: Long? = null,
    val pausedAt: Long? = null,
)

data class WorkoutSessionExerciseEntity(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val targetSets: Int,
    val targetReps: String,
    val targetWeightKg: Double,
    val status: String,
)
```

Add `sessionExerciseId: String? = null` to `WorkoutSetLogEntity`. Bump `DATABASE_VERSION` to `7`; create the new table/indexes in `createTables()`, add missing columns in `onUpgrade`, and run the guarded empty-seed cleanup SQL.

- [ ] **Step 4: Add Store runtime methods**

Implement:

```kotlin
fun workoutSession(id: String): WorkoutSessionEntity?
fun upsertSessionExercise(entity: WorkoutSessionExerciseEntity)
fun sessionExercises(sessionId: String): List<WorkoutSessionExerciseEntity>
fun updateWorkoutRuntime(id: String, currentExerciseId: String?, restEndsAt: Long?, pausedAt: Long?, updatedAt: Long)
fun updateSessionExerciseStatus(id: String, status: String)
fun deleteSessionExercises(sessionId: String)
```

Update every session/set cursor and ContentValues mapping. Include `workout_session_exercise` in personal-data cleanup and backup inputs later.

- [ ] **Step 5: Run GREEN and existing store tests**

Run the focused command, then:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.shanqijie.fitnessapp.FitnessStoreInstrumentedTest
```

Expected: all focused and existing Store tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/shanqijie/fitnessapp/data/FitnessEntities.kt \
  app/src/main/java/com/shanqijie/fitnessapp/data/FitnessDatabase.kt \
  app/src/main/java/com/shanqijie/fitnessapp/data/FitnessStore.kt \
  app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessWorkoutRuntimeStoreInstrumentedTest.kt
git commit -m "feat: persist multi-exercise workout runtime"
```

---

### Task 2: Implement the Repository workout state machine and derived summaries

**Files:**
- Create: `app/src/main/java/com/shanqijie/fitnessapp/domain/FitnessSummaries.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessRepository.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/data/FitnessBackupCodec.kt`
- Modify: `app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessRepositoryInstrumentedTest.kt`

**Interfaces:**
- Consumes: Task 1 Store runtime methods.
- Produces: `startWorkout`, `recordWorkoutSet`, `selectWorkoutExercise`, `startRest`, `skipRest`, `addExerciseToSession`, `finishWorkout`, `workoutSummary`, `homeSnapshot`, `nutritionSummary`, `addExerciseToPlan`, and `resetLocalData`.

Add an injected `TimeProvider` with production default `System.currentTimeMillis()`; Repository tests use a fake provider so no test waits for a real rest duration.

- [ ] **Step 1: Add failing Repository flow tests**

Add tests named:

```kotlin
@Test fun freshBootstrapDoesNotCreateAnUnfinishedSession()
@Test fun startRecordRestResumeAndFinishShareOneSession()
@Test fun libraryExerciseJoinsThePersistedSessionAndSurvivesRefresh()
@Test fun homeAndNutritionSummariesUsePersistedData()
@Test fun resetClearsPersonalDataAndCredentialsWithoutCreatingFakeSession()
@Test fun backupV1StillImportsAndV2RoundTripsRuntime()
```

The main test must start a plan with two exercises, record one set, persist `restEndsAt`, reconstruct Repository state, skip rest, add `2330`, finish, and assert one session, linked logs, plan completion, volume, duration, feeling distribution, and weekly progress.

- [ ] **Step 2: Run RED**

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.shanqijie.fitnessapp.FitnessRepositoryInstrumentedTest
```

Expected: new API references fail to compile or assertions fail.

- [ ] **Step 3: Define summary and runtime contracts**

```kotlin
data class WorkoutSummary(
    val sessionId: String,
    val completedSets: Int,
    val targetSets: Int,
    val totalVolumeKg: Double,
    val durationSeconds: Long,
    val feelingCounts: Map<String, Int>,
)

data class HomeSnapshot(
    val action: HomePrimaryAction,
    val completedThisWeek: Int,
    val targetThisWeek: Int,
    val nextWorkout: PlannedWorkoutEntity?,
)

sealed interface HomePrimaryAction {
    data class Start(val planId: String) : HomePrimaryAction
    data class Resume(val sessionId: String) : HomePrimaryAction
    data class Result(val sessionId: String) : HomePrimaryAction
    data object CreatePlan : HomePrimaryAction
}

data class NutritionSummary(val calories: Int, val protein: Double, val carbs: Double, val fat: Double)
```

- [ ] **Step 4: Implement the Repository state machine**

Use one UUID session per workout. `startWorkout(planId)` snapshots ordered planned exercises into `workout_session_exercise`; `recordWorkoutSet` validates reps `1..50`, non-negative weight, next set index and target set limit before writing; it updates `restEndsAt`. Selecting/adding an exercise updates persisted current exercise. `finishWorkout` marks the session and plan completed and returns a summary from stored rows.

Remove `seedDefaultSession(now)` from bootstrap. Preserve old public methods as compatibility wrappers for existing tests/UI until Task 8 deletes the old UI.

- [ ] **Step 5: Add plan, reset, summary, and backup helpers**

Implement:

```kotlin
suspend fun addExerciseToPlan(planId: String, exerciseId: String): PlannedExerciseEntity
suspend fun resetLocalData()
fun homeSnapshot(state: FitnessAppState, today: LocalDate = LocalDate.now()): HomeSnapshot
fun nutritionSummary(state: FitnessAppState, date: LocalDate = LocalDate.now()): NutritionSummary
```

Reset deletes DeepSeek credentials, clears personal tables, reseeds venue/equipment/plans/providers, and does not create a session. Backup v2 includes session exercises and runtime fields; v1 decode uses defaults.

- [ ] **Step 6: Run GREEN and all data tests**

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.shanqijie.fitnessapp.FitnessRepositoryInstrumentedTest,com.shanqijie.fitnessapp.FitnessStoreInstrumentedTest,com.shanqijie.fitnessapp.AiCredentialStoreInstrumentedTest
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/shanqijie/fitnessapp/domain/FitnessSummaries.kt \
  app/src/main/java/com/shanqijie/fitnessapp/data/FitnessRepository.kt \
  app/src/main/java/com/shanqijie/fitnessapp/data/FitnessBackupCodec.kt \
  app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessRepositoryInstrumentedTest.kt
git commit -m "feat: add persisted workout state machine"
```

---

### Task 3: Add native navigation, UI models, tokens, and shared components

**Files:**
- Create: `app/src/main/java/com/shanqijie/fitnessapp/ui/navigation/FitnessNavigation.kt`
- Create: `app/src/main/java/com/shanqijie/fitnessapp/ui/model/FitnessUiModels.kt`
- Create: `app/src/main/java/com/shanqijie/fitnessapp/ui/theme/FitnessTheme.kt`
- Create: `app/src/main/java/com/shanqijie/fitnessapp/ui/components/FitnessComponents.kt`
- Create: `app/src/test/java/com/shanqijie/fitnessapp/FitnessNavigationTest.kt`
- Create: `app/src/test/java/com/shanqijie/fitnessapp/FitnessUiModelsTest.kt`

**Interfaces:**
- Produces: `PrimaryTab`, `AppRoute`, `FitnessNavState`, `FitnessTestTags`, state reducers, theme tokens, and common Compose components.

- [ ] **Step 1: Write failing navigation and derivation tests**

```kotlin
@Test fun primaryTabsAreExactlyHomePlanTrainingFoodProfile()
@Test fun trainingActiveAndSummaryHideBottomNavigation()
@Test fun secondaryRoutesReturnToTheirRecordedOrigin()
@Test fun homeStateExposesExactlyOneActionForStartResumeAndResult()
@Test fun trainingReducerAdvancesAfterTargetSetsAndRest()
```

- [ ] **Step 2: Run RED**

```bash
./gradlew :app:testDebugUnitTest --tests '*FitnessNavigationTest' --tests '*FitnessUiModelsTest'
```

- [ ] **Step 3: Implement navigation contracts**

```kotlin
enum class PrimaryTab(val title: String) { Home("首页"), Plan("计划"), Training("训练"), Food("饮食"), Profile("我的") }

sealed interface AppRoute {
    data class Primary(val tab: PrimaryTab) : AppRoute
    data class Library(val origin: PrimaryTab, val planId: String? = null, val sessionId: String? = null) : AppRoute
    data class ExerciseDetail(val exerciseId: String, val origin: Library) : AppRoute
    data class PlanDetail(val planId: String) : AppRoute
    data class PlanEdit(val planId: String) : AppRoute
    data class TrainingActive(val sessionId: String) : AppRoute
    data class WorkoutSummary(val sessionId: String) : AppRoute
    data object ProfileEdit : AppRoute
    data object VenueSettings : AppRoute
    data object SmartSettings : AppRoute
    data object DataBackup : AppRoute
    data object About : AppRoute
}
```

`FitnessNavState.showBottomNav` is false only for active/summary and secondary full-screen routes.

- [ ] **Step 4: Implement tokens/components**

Expose exact colors `Phone #F5F2EC`, `Surface #FFFDFA`, `Ink #11151B`, `Muted #5F6874`, `Orange #FF7426`, `Green #24C869`, `Hero #151B24`; provide 48dp minimum buttons/chips, page header, metric card, GIF image, primary button, and five-item bottom nav with stable test tags.

- [ ] **Step 5: Run GREEN and commit**

```bash
./gradlew :app:testDebugUnitTest --tests '*FitnessNavigationTest' --tests '*FitnessUiModelsTest'
git add app/src/main/java/com/shanqijie/fitnessapp/ui app/src/test/java/com/shanqijie/fitnessapp/FitnessNavigationTest.kt app/src/test/java/com/shanqijie/fitnessapp/FitnessUiModelsTest.kt
git commit -m "feat: add native fitness navigation contracts"
```

---

### Task 4: Build the five-tab shell and focused native home

**Files:**
- Create: `app/src/main/java/com/shanqijie/fitnessapp/ui/FitnessAppRoot.kt`
- Create: `app/src/main/java/com/shanqijie/fitnessapp/ui/home/HomeScreen.kt`
- Create: `app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessHomeNavigationUiTest.kt`

**Interfaces:**
- Consumes: Task 2 summaries and Task 3 navigation/components.
- Produces: `FitnessAppRoot`, `HomeScreen`, five-tab shell and root callbacks used by later screens.

- [ ] **Step 1: Write failing Compose tests**

```kotlin
@Test fun shellShowsExactlyFiveTabsInApprovedOrder()
@Test fun homeShowsExactlyOneStateDrivenPrimaryAction()
@Test fun homePrimaryActionRoutesToPreparationResumeOrSummary()
@Test fun homeQuickActionsOpenFoodAndLibrary()
```

Use tags `bottom-nav`, `home-primary-action`, `weekly-progress`, `open-food`, `open-library` and assert the old labels `动作` and `智能` are absent from bottom navigation.

- [ ] **Step 2: Run RED**

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.shanqijie.fitnessapp.FitnessHomeNavigationUiTest
```

- [ ] **Step 3: Implement the root shell**

`FitnessAppRoot` owns `rememberSaveable` navigation, collects `repository.appState()` with lifecycle, derives the selected current/today plan, and passes state/actions to screens. Primary tab content must not be duplicated in the bottom bar.

- [ ] **Step 4: Implement the approved home**

Render: greeting, one dark Hero with local GIF and one CTA, weekly `x / y 次`, four-day strip, `记饮食`, and `动作库`. Remove onboarding, resume cards, training queue, asset diagnostics, hard-coded streak and duplicate CTA. CTA label is derived only from `HomePrimaryAction`.

- [ ] **Step 5: Run GREEN and commit**

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.shanqijie.fitnessapp.FitnessHomeNavigationUiTest
git add app/src/main/java/com/shanqijie/fitnessapp/ui/FitnessAppRoot.kt app/src/main/java/com/shanqijie/fitnessapp/ui/home/HomeScreen.kt app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessHomeNavigationUiTest.kt
git commit -m "feat: build five-tab native home shell"
```

---

### Task 5: Build training preparation, immersive active training, rest, and summary

**Files:**
- Create: `app/src/main/java/com/shanqijie/fitnessapp/ui/training/TrainingScreens.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/ui/FitnessAppRoot.kt`
- Create: `app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessTrainingFlowUiTest.kt`

**Interfaces:**
- Consumes: Task 2 runtime APIs and Task 3 routes.
- Produces: preparation, active/resting, confirmation, and summary routes.

- [ ] **Step 1: Write the failing real UI flow**

Test: preparation → start → bottom nav absent → weight/feeling change → complete one set → rest visible and state persisted → recreate Activity → rest restored → skip rest → finish confirmation → summary → home weekly progress updated. Use tags `training-prep`, `start-workout`, `training-active`, `complete-set`, `rest-panel`, `skip-rest`, `request-finish`, `confirm-finish`, `workout-summary`, `summary-done`.

- [ ] **Step 2: Run RED**

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.shanqijie.fitnessapp.FitnessTrainingFlowUiTest
```

- [ ] **Step 3: Implement preparation and active layout**

Preparation shows plan name, expected duration, ordered exercises and start. Active route is a dark full-screen `Scaffold` with no global bottom bar; use a 4:3 GIF, horizontal exercise chips, persisted progress, 48dp steppers/feeling buttons and a bottom fixed orange `完成本组`.

- [ ] **Step 4: Implement rest, finish confirmation, and summary**

Rest seconds are derived from `restEndsAt - now`, not a decrement-only local integer. At zero call `skipRest`; completing the target advances to the next exercise. Finish requires `AlertDialog`; summary reads Task 2 `WorkoutSummary`, and `summary-done` returns home without clearing history.

- [ ] **Step 5: Run GREEN, unit/data regression, and commit**

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.shanqijie.fitnessapp.FitnessTrainingFlowUiTest,com.shanqijie.fitnessapp.FitnessRepositoryInstrumentedTest
git add app/src/main/java/com/shanqijie/fitnessapp/ui/training/TrainingScreens.kt app/src/main/java/com/shanqijie/fitnessapp/ui/FitnessAppRoot.kt app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessTrainingFlowUiTest.kt
git commit -m "feat: add immersive native workout flow"
```

---

### Task 6: Migrate plan hierarchy and secondary action library

**Files:**
- Create: `app/src/main/java/com/shanqijie/fitnessapp/ui/plan/PlanScreens.kt`
- Create: `app/src/main/java/com/shanqijie/fitnessapp/ui/library/LibraryScreens.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/ui/FitnessAppRoot.kt`
- Create: `app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessPlanLibraryUiTest.kt`

- [ ] **Step 1: Add failing plan/library tests**

Assert weekly schedule appears above monthly draft generation; non-training dates say `休息日`; plan opens detail/edit; library is absent from bottom navigation; search/filter produce full row buttons; detail from `背` adds `高位下拉` to plan or running session and survives Activity recreation.

- [ ] **Step 2: Run RED**

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.shanqijie.fitnessapp.FitnessPlanLibraryUiTest
```

- [ ] **Step 3: Implement plan screens**

Use real `plannedWorkouts` and `plannedExerciseViews`. New plan opens an editor sheet before `createWorkoutFromTemplate`; monthly generation creates/opens an `AiDraftEntity` and requires confirmation before formal plans are added. Keep venue settings out of the plan primary page.

- [ ] **Step 4: Implement secondary library**

Search/filter the existing 1324-item local dataset with translated labels. Each result is one 48dp+ row with GIF thumbnail/name/body part/equipment/chevron. Origin determines whether `用于本次训练` calls `addExerciseToSession` or `addExerciseToPlan`.

- [ ] **Step 5: Run GREEN and commit**

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.shanqijie.fitnessapp.FitnessPlanLibraryUiTest
git add app/src/main/java/com/shanqijie/fitnessapp/ui/plan app/src/main/java/com/shanqijie/fitnessapp/ui/library app/src/main/java/com/shanqijie/fitnessapp/ui/FitnessAppRoot.kt app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessPlanLibraryUiTest.kt
git commit -m "feat: migrate native plan and exercise library"
```

---

### Task 7: Migrate food, profile summary, settings, reset, and backup

**Files:**
- Create: `app/src/main/java/com/shanqijie/fitnessapp/ui/food/FoodScreens.kt`
- Create: `app/src/main/java/com/shanqijie/fitnessapp/ui/profile/ProfileScreens.kt`
- Create: `app/src/main/java/com/shanqijie/fitnessapp/ui/settings/SettingsScreens.kt`
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/ui/FitnessAppRoot.kt`
- Create: `app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessFoodProfileUiTest.kt`

- [ ] **Step 1: Add failing food/profile tests**

Test one `添加一餐` action, manual validation preserving fields, saved meal updating four totals and surviving recreation, photo mode producing a draft without immediate log, profile first screen containing no text fields, smart status consistency, reset confirmation, storage clearing, and restoration of a fresh start CTA.

- [ ] **Step 2: Run RED**

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.shanqijie.fitnessapp.FitnessFoodProfileUiTest
```

- [ ] **Step 3: Implement food**

Render real nutrition totals and newest-first timeline. `ModalBottomSheet` first chooses photo/manual; manual errors use field supporting text and retain values; photo picker calls existing draft API and requires confirmation.

- [ ] **Step 4: Implement profile/settings**

Profile primary screen is read-only summary plus training metrics and rows `训练偏好`, `场地与器械`, `智能设置`, `数据备份`, `关于`. Only ProfileEdit contains fields. Smart status is `已连接` only when Keystore-backed configuration is true. Backup keeps file launchers in a secondary screen. Reset uses confirmation and Task 2 `resetLocalData()`.

- [ ] **Step 5: Run GREEN and commit**

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.shanqijie.fitnessapp.FitnessFoodProfileUiTest
git add app/src/main/java/com/shanqijie/fitnessapp/ui/food app/src/main/java/com/shanqijie/fitnessapp/ui/profile app/src/main/java/com/shanqijie/fitnessapp/ui/settings app/src/main/java/com/shanqijie/fitnessapp/ui/FitnessAppRoot.kt app/src/androidTest/java/com/shanqijie/fitnessapp/FitnessFoodProfileUiTest.kt
git commit -m "feat: migrate native food and profile flows"
```

---

### Task 8: Switch the APK to the new root, remove old UI drift, and verify on emulator

**Files:**
- Modify: `app/src/main/java/com/shanqijie/fitnessapp/MainActivity.kt`
- Modify: all new `app/src/main/java/com/shanqijie/fitnessapp/ui/**` files for final polish
- Create: `.scratch/run-evidence/android-layout-migration/home.png`
- Create: `.scratch/run-evidence/android-layout-migration/plan.png`
- Create: `.scratch/run-evidence/android-layout-migration/training-prep.png`
- Create: `.scratch/run-evidence/android-layout-migration/training-active.png`
- Create: `.scratch/run-evidence/android-layout-migration/workout-summary.png`
- Create: `.scratch/run-evidence/android-layout-migration/food.png`
- Create: `.scratch/run-evidence/android-layout-migration/profile.png`

- [ ] **Step 1: Switch `MainActivity` and delete obsolete private UI**

`MainActivity` must only call `FitnessTheme { FitnessAppRoot() }`. Remove old seven-tab `MainShell`, `AppTab`, duplicate screens/tokens/components after all references are migrated. Preserve URI helpers only where new food/backup screens use them.

- [ ] **Step 2: Run the complete automated gate**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

Expected: every task test and all existing tests pass; `BUILD SUCCESSFUL` and zero failures.

- [ ] **Step 3: Install a clean APK and launch**

```bash
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" -s emulator-5554 shell pm clear com.shanqijie.fitnessapp
"$ADB" -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s emulator-5554 shell am start -W -n com.shanqijie.fitnessapp/.MainActivity
```

- [ ] **Step 4: Exercise the real flow and capture screenshots**

Use Compose/UIAutomator selectors rather than coordinates where possible. Capture the seven named screens with `adb exec-out screencap -p`. Reject loading, old seven-tab, wrong-state, cropped, or blank images. Active training must show no bottom bar and visible `完成本组`; summary must show at least one completed set.

- [ ] **Step 5: Verify runtime logs and SQLite**

```bash
"$ADB" -s emulator-5554 logcat -d -t 800 | ! rg 'FATAL EXCEPTION|AndroidRuntime: FATAL'
"$ADB" -s emulator-5554 exec-out run-as com.shanqijie.fitnessapp cat databases/fitness.db > /tmp/fitness-layout-migration.db
sqlite3 /tmp/fitness-layout-migration.db "SELECT status, current_exercise_id, rest_ends_at FROM workout_session ORDER BY started_at DESC LIMIT 1;"
sqlite3 /tmp/fitness-layout-migration.db "SELECT COUNT(*), ROUND(SUM(actual_reps * actual_weight_kg),1) FROM workout_set_log WHERE completed=1;"
sqlite3 /tmp/fitness-layout-migration.db "SELECT name, calories, protein_grams FROM food_log ORDER BY created_at DESC LIMIT 1;"
```

The UI summary, weekly progress, set count/volume, and food summary must match DB rows.

- [ ] **Step 6: Commit verified APK-facing work and evidence**

```bash
git add app/src/main/java/com/shanqijie/fitnessapp/MainActivity.kt
git add -u -- app/src/main/java app/src/test/java app/src/androidTest/java
git add .scratch/run-evidence/android-layout-migration
git commit -m "test: verify native layout migration on emulator"
```

## Plan Self-Review

- Spec coverage: database/runtime, exact five-tab navigation, one-action home, immersive training, plan/library, food/profile/settings, accessibility, persistence, backup/reset, and emulator/DB proof all map to Tasks 1–8.
- Placeholder scan: every implementation step names concrete files, interfaces, commands, expected behavior, and behavioral tests.
- Type consistency: the session ID and session-exercise ID introduced in Task 1 are used unchanged by Repository and UI tasks; routes and test tags are introduced before consumption.
- Scope: tasks preserve local-first product boundaries and do not expand into account/cloud/social/backend work.
