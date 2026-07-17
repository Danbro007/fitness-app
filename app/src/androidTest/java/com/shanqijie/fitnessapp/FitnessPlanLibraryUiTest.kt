package com.shanqijie.fitnessapp

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import com.shanqijie.fitnessapp.data.FitnessDatabase
import com.shanqijie.fitnessapp.data.FitnessRepository
import com.shanqijie.fitnessapp.data.FitnessStore
import com.shanqijie.fitnessapp.data.AiDraftEntity
import com.shanqijie.fitnessapp.data.ExerciseMediaEntity
import com.shanqijie.fitnessapp.data.PlannedWorkoutEntity
import com.shanqijie.fitnessapp.data.WorkoutSessionEntity
import com.shanqijie.fitnessapp.ui.FitnessAppRoot
import com.shanqijie.fitnessapp.ui.FitnessAppRootContent
import com.shanqijie.fitnessapp.ui.model.toHomeUiState
import com.shanqijie.fitnessapp.ui.navigation.AppRoute
import com.shanqijie.fitnessapp.ui.navigation.FitnessTestTags
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab
import com.shanqijie.fitnessapp.ui.library.ExerciseDetailScreen
import com.shanqijie.fitnessapp.ui.library.LibraryScreen
import com.shanqijie.fitnessapp.ui.library.LibraryTags
import com.shanqijie.fitnessapp.ui.plan.PlanScreen
import com.shanqijie.fitnessapp.ui.plan.PlanDraftScreen
import com.shanqijie.fitnessapp.ui.plan.PlanDetailScreen
import com.shanqijie.fitnessapp.ui.plan.PlanEditScreen
import com.shanqijie.fitnessapp.ui.plan.PlanTags
import com.shanqijie.fitnessapp.ui.theme.FitnessTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class FitnessPlanLibraryUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var database: FitnessDatabase
    private lateinit var repository: FitnessRepository
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "fitness-plan-library-ui-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        database = FitnessDatabase(context, databaseName)
        repository = FitnessRepository(context, FitnessStore(database))
        runBlocking {
            repository.bootstrap()
            repository.saveUserProfile(
                displayName = "测试用户",
                birthYear = 1994,
                heightCm = 176.0,
                weightKg = 75.0,
                goal = "保持体能",
                injuries = "",
                weeklyTrainingDays = 3,
                preferredMinutes = 35,
            )
            repository.setOnboardingCompleted(true)
            repository.createWorkoutFromTemplate(
                name = "测试训练",
                scheduledDate = LocalDate.now().toString(),
                venueId = FitnessRepository.DEFAULT_VENUE_ID,
            )
        }
    }

    @After
    fun tearDown() {
        composeRule.runOnUiThread { composeRule.activity.setContent {} }
        composeRule.waitForIdle()
        database.close()
        assertTrue(context.deleteDatabase(databaseName))
    }

    @Test
    fun weeklyCalendarKeepsNextTwoDaysReachableAndShowsEverySameDayPlan() {
        val tomorrow = LocalDate.now().plusDays(1)
        val plans = listOf("PlanA", "PlanB").mapIndexed { index, name ->
            PlannedWorkoutEntity(
                id = "same-day-$index",
                name = name,
                scheduledDate = tomorrow.toString(),
                venueId = FitnessRepository.DEFAULT_VENUE_ID,
                status = "planned",
                createdAt = index.toLong(),
                updatedAt = index.toLong(),
            )
        }
        composeRule.setContent {
            FitnessTheme {
                PlanScreen(
                    plans = plans,
                    plannedExerciseViews = emptyList(),
                    sessions = emptyList(),
                    setLogs = emptyList(),
                    weeklyTrainingDays = 3,
                    userProfile = null,
                    initialCalendarMode = "周",
                    onCalendarModeChange = {},
                    activeMonthlyDraft = null,
                    onOpenPlan = {},
                    onCreatePlan = { _, _ -> error("not used") },
                    onGenerateMonthlyDraft = { "" },
                    onOpenMonthlyDraft = {},
                    onConfirmMonthlyDraft = {},
                    onStartPlan = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithText("已完成 0 · 已计划 2").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar-day-$tomorrow").performScrollTo().performClick()
        composeRule.onAllNodesWithText("PlanA").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("PlanB").onFirst().performScrollTo().assertIsDisplayed()
    }

    @Test
    fun customDatePickerSupportsFallbackNavigationCancelAndConfirmation() {
        var createdDate: String? = null
        var openedPlanId: String? = null
        composeRule.setContent {
            FitnessTheme {
                PlanScreen(
                    plans = emptyList(),
                    plannedExerciseViews = emptyList(),
                    sessions = emptyList(),
                    setLogs = emptyList(),
                    weeklyTrainingDays = 3,
                    userProfile = null,
                    initialCalendarMode = "周",
                    onCalendarModeChange = {},
                    activeMonthlyDraft = null,
                    onOpenPlan = { openedPlanId = it },
                    onCreatePlan = { _, date ->
                        createdDate = date
                        "created-plan"
                    },
                    onGenerateMonthlyDraft = { "draft" },
                    onOpenMonthlyDraft = {},
                    onConfirmMonthlyDraft = {},
                    onStartPlan = { openedPlanId = it },
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithTag(PlanTags.NewPlan).performClick()
        composeRule.onNodeWithTag(PlanTags.DateInput).performTextReplacement("not-a-date")
        composeRule.onNodeWithContentDescription("选择训练日期").performClick()
        composeRule.onNodeWithTag(PlanTags.DatePicker).assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithTag(PlanTags.DatePicker).assertDoesNotExist()

        val initialDate = LocalDate.now().plusDays(1)
        composeRule.onNodeWithTag(PlanTags.DateInput).performTextReplacement(initialDate.toString())
        composeRule.onNodeWithContentDescription("选择训练日期").performClick()
        val nextMonth = YearMonth.from(initialDate).plusMonths(1)
        composeRule.onNodeWithContentDescription("下个月").performClick()
        composeRule.onNodeWithTag(PlanTags.datePickerDay(nextMonth.atDay(1).toString())).performClick()
        composeRule.onNodeWithContentDescription("上个月").performClick()
        composeRule.onNodeWithContentDescription("下个月").performClick()
        composeRule.onNodeWithTag(PlanTags.datePickerDay(nextMonth.atDay(1).toString())).performClick()
        composeRule.onNodeWithTag(PlanTags.DatePickerConfirm).performClick()
        composeRule.onNodeWithTag(PlanTags.DatePicker).assertDoesNotExist()
        composeRule.onNodeWithTag(PlanTags.SaveNewPlan).performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) { createdDate != null && openedPlanId != null }
        assertEquals(nextMonth.atDay(1).toString(), createdDate)
        assertEquals("created-plan", openedPlanId)
    }

    @Test
    fun newPlanEditorStaysOpenAndLockedUntilTheCreateTransactionCompletes() {
        val releaseCreate = CompletableDeferred<Unit>()
        var createStarted = false
        var openedPlanId: String? = null
        composeRule.setContent {
            FitnessTheme {
                PlanScreen(
                    plans = emptyList(),
                    plannedExerciseViews = emptyList(),
                    sessions = emptyList(),
                    setLogs = emptyList(),
                    weeklyTrainingDays = 3,
                    userProfile = null,
                    initialCalendarMode = "周",
                    onCalendarModeChange = {},
                    activeMonthlyDraft = null,
                    onOpenPlan = { openedPlanId = it },
                    onCreatePlan = { _, _ ->
                        createStarted = true
                        releaseCreate.await()
                        "created-plan"
                    },
                    onGenerateMonthlyDraft = { "unused" },
                    onOpenMonthlyDraft = {},
                    onConfirmMonthlyDraft = {},
                    onStartPlan = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithTag(PlanTags.NewPlan).performClick()
        composeRule.onNodeWithTag(PlanTags.SaveNewPlan).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { createStarted }
        composeRule.onNodeWithText("保存中…").assertIsDisplayed()
        composeRule.onNodeWithTag(PlanTags.SaveNewPlan).assertIsNotEnabled()
        pressBack()
        composeRule.onNodeWithTag(PlanTags.Editor).assertIsDisplayed()

        composeRule.runOnIdle { releaseCreate.complete(Unit) }
        composeRule.waitUntil(timeoutMillis = 5_000) { openedPlanId == "created-plan" }
    }

    @Test
    fun calendarSwitchesFromYearToMonthAndOpensCompletedDayDetails() {
        val today = LocalDate.now()
        val startedAt = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val plan = PlannedWorkoutEntity(
            id = "calendar-plan",
            name = "今日训练计划",
            scheduledDate = today.toString(),
            venueId = FitnessRepository.DEFAULT_VENUE_ID,
            status = "planned",
            createdAt = startedAt,
            updatedAt = startedAt,
        )
        val completed = WorkoutSessionEntity(
            id = "calendar-session",
            plannedWorkoutId = plan.id,
            venueId = plan.venueId,
            exerciseId = "0748",
            status = "completed",
            startedAt = startedAt,
            endedAt = startedAt + 30_000L,
            updatedAt = startedAt + 30_000L,
        )
        var selectedMode: String? = null
        composeRule.setContent {
            FitnessTheme {
                PlanScreen(
                    plans = listOf(plan),
                    plannedExerciseViews = emptyList(),
                    sessions = listOf(completed),
                    setLogs = emptyList(),
                    weeklyTrainingDays = 3,
                    userProfile = null,
                    initialCalendarMode = "周",
                    activeMonthlyDraft = null,
                    onCalendarModeChange = { selectedMode = it },
                    onOpenPlan = {},
                    onCreatePlan = { _, _ -> "unused" },
                    onGenerateMonthlyDraft = { "unused" },
                    onOpenMonthlyDraft = {},
                    onConfirmMonthlyDraft = {},
                    onStartPlan = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithContentDescription("日历视图：周")
            .assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("年").performClick()
        composeRule.onNodeWithText("全年概览").assertIsDisplayed()
        composeRule.onNodeWithText("${today.monthValue}月").performClick()
        composeRule.onNodeWithText("月度分布").assertIsDisplayed()
        composeRule.onAllNodesWithText(today.dayOfMonth.toString()).onFirst().performClick()
        composeRule.onNodeWithTag(PlanTags.DayDetail).assertIsDisplayed()
        composeRule.onNodeWithText("已完成训练 · 0 组").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("月", selectedMode) }
    }

    @Test
    fun weeklyCalendarHandlesInvalidPlansCompletedOnlyDaysAndEmptyDays() {
        val today = LocalDate.now()
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val otherDays = (0L..6L).map(weekStart::plusDays).filter { it != today }
        val completedOnlyDate = otherDays.first()
        val emptyDate = otherDays[1]
        fun epoch(date: LocalDate) = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val sessions = listOf(
            WorkoutSessionEntity("in-progress", null, FitnessRepository.DEFAULT_VENUE_ID, "0748", "in_progress", epoch(completedOnlyDate), null, epoch(completedOnlyDate)),
            WorkoutSessionEntity("wrong-day", null, FitnessRepository.DEFAULT_VENUE_ID, "0748", "completed", epoch(today), epoch(today) + 1_000L, epoch(today) + 1_000L),
            WorkoutSessionEntity("completed-only", null, FitnessRepository.DEFAULT_VENUE_ID, "0748", "completed", epoch(completedOnlyDate), epoch(completedOnlyDate) + 1_000L, epoch(completedOnlyDate) + 1_000L),
        )
        val invalidPlan = PlannedWorkoutEntity(
            id = "invalid-calendar-date",
            name = "无效日期计划",
            scheduledDate = "not-a-date",
            venueId = FitnessRepository.DEFAULT_VENUE_ID,
            status = "planned",
            createdAt = 1L,
            updatedAt = 1L,
        )
        composeRule.setContent {
            FitnessTheme {
                PlanScreen(
                    plans = listOf(invalidPlan),
                    plannedExerciseViews = emptyList(),
                    sessions = sessions,
                    setLogs = emptyList(),
                    weeklyTrainingDays = 3,
                    userProfile = null,
                    initialCalendarMode = "周",
                    onCalendarModeChange = {},
                    activeMonthlyDraft = null,
                    onOpenPlan = {},
                    onCreatePlan = { _, _ -> "unused" },
                    onGenerateMonthlyDraft = { "unused" },
                    onOpenMonthlyDraft = {},
                    onConfirmMonthlyDraft = {},
                    onStartPlan = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithTag("calendar-day-$completedOnlyDate").performClick()
        composeRule.onAllNodesWithText("已完成训练 · 0 组")
            .assertCountEquals(2)
            .onFirst()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("训练记录已保存")
            .assertCountEquals(2)
            .onFirst()
            .assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithTag("calendar-day-$emptyDate").performClick()
        composeRule.onAllNodesWithText("当天没有训练安排")
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun planDraftKeepsTheDraftVisibleWhenConfirmOrRegenerateFails() {
        val draft = AiDraftEntity(
            id = "error-draft",
            type = "weekly_plan",
            title = "四周训练草稿",
            content = "**第一周** 建立节奏",
            status = "draft",
            createdAt = 1L,
            updatedAt = 1L,
            confirmedAt = null,
        )
        composeRule.setContent {
            FitnessTheme {
                PlanDraftScreen(
                    draft = draft,
                    userProfile = null,
                    onConfirm = { error("确认失败：数据库繁忙") },
                    onRegenerate = { error("重新生成失败：网络不可用") },
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithTag(PlanTags.ConfirmMonthlyDraft).performScrollTo().performClick()
        composeRule.onNodeWithText("确认失败：数据库繁忙").assertIsDisplayed()
        composeRule.onNodeWithText("重新生成").performScrollTo().performClick()
        composeRule.onNodeWithText("重新生成失败：网络不可用").assertIsDisplayed()
        composeRule.onNodeWithTag(PlanTags.DraftScreen).assertIsDisplayed()
    }

    @Test
    fun planDraftHidesBlankAiTextAndLocksActionsWhileConfirming() {
        val releaseConfirm = CompletableDeferred<Unit>()
        var confirmStarted = false
        val draft = AiDraftEntity(
            id = "blank-busy-draft",
            type = "weekly_plan",
            title = "四周训练草稿",
            content = "",
            status = "draft",
            createdAt = 1L,
            updatedAt = 1L,
            confirmedAt = null,
        )
        composeRule.setContent {
            FitnessTheme {
                PlanDraftScreen(
                    draft = draft,
                    userProfile = null,
                    onConfirm = {
                        confirmStarted = true
                        releaseConfirm.await()
                    },
                    onRegenerate = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onAllNodesWithText("AI 生成建议").assertCountEquals(0)
        composeRule.onNodeWithTag(PlanTags.ConfirmMonthlyDraft).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { confirmStarted }
        composeRule.onNodeWithText("处理中…").assertIsDisplayed()
        composeRule.onNodeWithTag(PlanTags.ConfirmMonthlyDraft).assertIsNotEnabled()
        composeRule.runOnIdle { releaseConfirm.complete(Unit) }
        composeRule.onNodeWithTag(PlanTags.ConfirmMonthlyDraft).assertIsEnabled()
    }

    @Test
    fun planEditLocksSaveUntilPersistenceCompletes() {
        val releaseSave = CompletableDeferred<Unit>()
        var saveStarted = false
        val plan = PlannedWorkoutEntity(
            id = "busy-plan",
            name = "忙碌状态计划",
            scheduledDate = LocalDate.now().plusDays(1).toString(),
            venueId = FitnessRepository.DEFAULT_VENUE_ID,
            status = "planned",
            createdAt = 1L,
            updatedAt = 1L,
        )
        composeRule.setContent {
            FitnessTheme {
                PlanEditScreen(
                    plan = plan,
                    exercises = emptyList(),
                    onSave = { _, _ ->
                        saveStarted = true
                        releaseSave.await()
                    },
                    onOpenLibrary = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithTag(PlanTags.SaveEdit).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { saveStarted }
        composeRule.onNodeWithText("保存中…").assertIsDisplayed()
        composeRule.onNodeWithTag(PlanTags.SaveEdit).assertIsNotEnabled()
        composeRule.runOnIdle { releaseSave.complete(Unit) }
        composeRule.onNodeWithTag(PlanTags.SaveEdit).assertIsEnabled()
    }

    @Test
    fun planDetailCoversDefaultAndExplicitMetadataWithInvalidDateFallback() {
        val state = runBlocking { repository.appState().first() }
        val plan = state.plannedWorkouts.first()
        val views = state.plannedExerciseViews.filter { it.plannedExercise.plannedWorkoutId == plan.id }
        val explicit = androidx.compose.runtime.mutableStateOf(false)
        composeRule.setContent {
            FitnessTheme {
                if (explicit.value) {
                    PlanDetailScreen(
                        plan = plan.copy(name = "无效日期计划", scheduledDate = "not-a-date"),
                        exercises = emptyList(),
                        weeklyTrainingDays = 5,
                        goal = "保持体能",
                        venueName = "自定义场地",
                        preferredMinutes = 50,
                        onEdit = {},
                        onOpenLibrary = {},
                        modifier = androidx.compose.ui.Modifier,
                    )
                } else {
                    PlanDetailScreen(
                        plan = plan,
                        exercises = views,
                        onEdit = {},
                        onOpenLibrary = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(plan.name).assertIsDisplayed()
        composeRule.runOnIdle { explicit.value = true }
        composeRule.onNodeWithText("无效日期计划").assertIsDisplayed()
        composeRule.onNodeWithText("自定义场地", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun libraryBodyFiltersCoverAllMuscleGroups() {
        val exercises = listOf(
            exerciseMedia("chest", "chest press", "chest", "pectorals"),
            exerciseMedia("back", "cable row", "back", "lats"),
            exerciseMedia("legs", "bodyweight squat", "upper legs", "quads"),
            exerciseMedia("core", "crunch", "waist", "abs"),
        )
        composeRule.setContent {
            FitnessTheme { LibraryScreen(exercises = exercises, onOpenExercise = {}) }
        }

        listOf(
            "胸部" to "chest",
            "背部" to "back",
            "腿部" to "legs",
            "核心" to "core",
            "全部" to "chest",
        ).forEach { (filter, visibleExerciseId) ->
            composeRule.onNodeWithText(filter).performClick()
            composeRule.onNodeWithTag(LibraryTags.result(visibleExerciseId)).assertIsDisplayed()
        }
    }

    @Test
    fun exerciseDetailKeepsTheActionAvailableWhenAddingFails() {
        val exercise = exerciseMedia("blank-meta", "custom movement", "", "")
        var attempts = 0
        composeRule.setContent {
            FitnessTheme {
                ExerciseDetailScreen(
                    exercise = exercise,
                    actionContextLabel = "加入本次计划",
                    actionLabel = "添加到计划",
                    onAddExercise = {
                        attempts += 1
                        if (attempts == 1) error("添加动作失败：计划已删除")
                        throw Exception()
                    },
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithTag(LibraryTags.AddExercise).performScrollTo().performClick()
        composeRule.onNodeWithText("添加动作失败：计划已删除").assertIsDisplayed()
        composeRule.onNodeWithTag(LibraryTags.AddExercise).assertIsEnabled()
        composeRule.onNodeWithTag(LibraryTags.AddExercise).performScrollTo().performClick()
        composeRule.onNodeWithText("添加动作失败").assertIsDisplayed()
    }

    @Test
    fun planCalendarPresentsEveryPersistedStatusAndUsesFallbackErrors() {
        val today = LocalDate.now()
        val plans = listOf("in_progress", "completed", "skipped", "unexpected").mapIndexed { index, status ->
            PlannedWorkoutEntity(
                id = "status-$status",
                name = "状态计划 $index",
                scheduledDate = today.plusDays(index.toLong()).toString(),
                venueId = FitnessRepository.DEFAULT_VENUE_ID,
                status = status,
                createdAt = index.toLong(),
                updatedAt = index.toLong(),
            )
        }
        composeRule.setContent {
            FitnessTheme {
                PlanScreen(
                    plans = plans,
                    plannedExerciseViews = emptyList(),
                    sessions = emptyList(),
                    setLogs = emptyList(),
                    weeklyTrainingDays = 3,
                    userProfile = null,
                    initialCalendarMode = "周",
                    onCalendarModeChange = {},
                    activeMonthlyDraft = null,
                    onOpenPlan = {},
                    onCreatePlan = { _, _ -> throw IllegalStateException() },
                    onGenerateMonthlyDraft = { "unused" },
                    onOpenMonthlyDraft = {},
                    onConfirmMonthlyDraft = {},
                    onStartPlan = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        listOf("进行中", "已完成", "已跳过", "本地计划").forEach { label ->
            composeRule.onAllNodesWithText(label, substring = true).onFirst().performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithTag(PlanTags.NewPlan).performClick()
        composeRule.onNodeWithTag(PlanTags.SaveNewPlan).performScrollTo().performClick()
        composeRule.onAllNodesWithText("创建计划失败")[1].performScrollTo().assertIsDisplayed()
    }

    @Test
    fun planHierarchyEditsRealPlansAndConfirmsFourWeekDraft() {
        showRealRoot()
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Plan)).performClick()
        waitForTag(PlanScreenTag)

        composeRule.onNodeWithText("训练日历").assertIsDisplayed()
        composeRule.onAllNodesWithText("休息日").onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag(MonthlyGeneratorTag).performScrollTo().assertIsDisplayed()

        val initialCount = currentState().plannedWorkouts.size
        composeRule.onNodeWithTag(NewPlanTag).performScrollTo().performClick()
        composeRule.onNodeWithTag(PlanEditorTag).assertIsDisplayed()
        assertEquals(initialCount, currentState().plannedWorkouts.size)
        composeRule.onNodeWithTag(PlanDateInputTag).performTextReplacement("2026-99-99")
        composeRule.onNodeWithTag(SaveNewPlanTag).performClick()
        composeRule.onAllNodesWithText("请输入有效日期（格式 YYYY-MM-DD）")
            .onFirst()
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(initialCount, currentState().plannedWorkouts.size)
        composeRule.onNodeWithTag(PlanNameInputTag).performTextReplacement("周末背部训练")
        composeRule.onNodeWithTag(PlanDateInputTag)
            .performTextReplacement(LocalDate.now().plusDays(1).toString())
        composeRule.onNodeWithTag(SaveNewPlanTag).performClick()

        waitUntilState { state -> state.plannedWorkouts.any { it.name == "周末背部训练" } }
        val createdPlan = currentState().plannedWorkouts.single { it.name == "周末背部训练" }
        waitForTag(PlanDetailTag)
        composeRule.onNodeWithText("周末背部训练").assertIsDisplayed()
        composeRule.onNodeWithTag(EditPlanTag).performClick()
        waitForTag(PlanEditTag)
        composeRule.onNodeWithTag(PlanEditNameTag).performTextReplacement("周末背部训练 B")
        composeRule.onNodeWithTag(SavePlanEditTag).performClick()
        waitUntilState { state -> state.plannedWorkouts.any { it.id == createdPlan.id && it.name.endsWith(" B") } }

        waitForTag(PlanDetailTag)
        composeRule.onNodeWithTag(FitnessTestTags.Back).performClick()
        waitForTag(PlanScreenTag)
        val beforeDraftState = currentState()
        composeRule.onNodeWithTag(GenerateMonthlyDraftTag).performScrollTo().performClick()
        waitForTag(MonthlyDraftTag)
        composeRule.onNodeWithText("全部档案数据已参与").performScrollTo().assertIsDisplayed()
        val draftState = currentState()
        assertEquals(beforeDraftState.plannedWorkouts.size, draftState.plannedWorkouts.size)
        assertTrue(draftState.aiDrafts.any { it.type == "weekly_plan" && it.status == "draft" })

        val beforeConfirmIds = draftState.plannedWorkouts.mapTo(mutableSetOf()) { it.id }
        val weeklyDays = requireNotNull(draftState.userProfile).weeklyTrainingDays
        val expectedGeneratedCount = weeklyDays * 4
        composeRule.onNodeWithTag(ConfirmMonthlyDraftTag).performScrollTo().performClick()
        waitUntilState { state -> state.plannedWorkouts.size == beforeDraftState.plannedWorkouts.size + expectedGeneratedCount }
        val confirmedState = currentState()
        val generatedPlans = confirmedState.plannedWorkouts
            .filterNot { it.id in beforeConfirmIds }
            .sortedBy { it.scheduledDate }
        assertEquals(expectedGeneratedCount, generatedPlans.size)
        assertEquals(expectedGeneratedCount, generatedPlans.map { it.id }.distinct().size)
        generatedPlans.chunked(weeklyDays).map { it.first() }.zipWithNext().forEach { (first, second) ->
            assertEquals(
                7L,
                ChronoUnit.DAYS.between(LocalDate.parse(first.scheduledDate), LocalDate.parse(second.scheduledDate)),
            )
        }

        recreateRealRoot()
        waitForTag(FitnessTestTags.HomePrimaryAction)
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Plan)).performClick()
        waitForTag(PlanScreenTag)
        assertEquals(
            generatedPlans.map { it.id }.toSet(),
            currentState().plannedWorkouts.filter { it.id in generatedPlans.map { plan -> plan.id } }.map { it.id }.toSet(),
        )
    }

    @Test
    fun translatedLibraryAddsBackExerciseToPlanAndPersistsAfterRecreation() {
        showRealRoot()
        val plan = currentState().plannedWorkouts
            .firstOrNull { it.scheduledDate == LocalDate.now().toString() }
            ?: currentState().plannedWorkouts.first()
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Plan)).performClick()
        waitForTag(PlanScreenTag)
        composeRule.onNodeWithTag(PlanTags.Spotlight).performClick()
        waitForTag(PlanDetailTag)
        composeRule.onNodeWithTag(EditPlanTag).performClick()
        waitForTag(PlanEditTag)
        composeRule.onNodeWithTag(OpenPlanLibraryTag).performClick()

        waitForTag(LibraryScreenTag)
        composeRule.onNodeWithTag(FitnessTestTags.BottomNav).assertDoesNotExist()
        assertEquals(1324, currentState().exercises.size)
        composeRule.onNodeWithText("1324 个本地动作").assertIsDisplayed()
        composeRule.onNodeWithTag(LibraryFilterBackTag).performClick()
        composeRule.onNodeWithTag(LibrarySearchTag).performTextReplacement("高位下拉")
        waitForTag(LibraryResult2330Tag)
        composeRule.onNodeWithTag(LibraryResult2330Tag).assertHeightIsAtLeast(48.dp)
        if (BuildConfig.EXERCISE_MEDIA_ENABLED) {
            composeRule.onNodeWithContentDescription("绳索高位下拉全程").assertIsDisplayed()
        } else {
            composeRule.onAllNodesWithText("动作示范媒体需取得授权后启用")
                .onFirst()
                .assertIsDisplayed()
        }
        composeRule.onNodeWithTag(LibraryResult2330Tag).performClick()

        waitForTag(ExerciseDetailTag)
        composeRule.onNodeWithText("绳索高位下拉全程").assertIsDisplayed()
        composeRule.onNodeWithText("添加到计划").assertIsDisplayed()
        composeRule.onNodeWithTag(AddExerciseTag).performClick()
        waitUntilState { state ->
            state.plannedExercises.any { it.plannedWorkoutId == plan.id && it.exerciseId == "2330" }
        }
        assertFalse(currentState().workoutSessionExercises.any { it.exerciseId == "2330" })

        recreateRealRoot()
        assertTrue(
            currentState().plannedExercises.any { it.plannedWorkoutId == plan.id && it.exerciseId == "2330" },
        )
    }

    @Test
    fun libraryOriginAddsExerciseToRunningSessionAndPersistsAfterRecreation() {
        val planId = currentState().plannedWorkouts.first().id
        val session = runBlocking { repository.startWorkout(planId) }
        val state = currentState()
        composeRule.setContent {
            FitnessTheme {
                FitnessAppRootContent(
                    homeUiState = repository.homeSnapshot(state).toHomeUiState(),
                    repository = repository,
                    appState = state,
                    initialRoute = AppRoute.Library(
                        origin = PrimaryTab.Training,
                        sessionId = session.id,
                    ),
                )
            }
        }

        waitForTag(LibraryScreenTag)
        composeRule.onNodeWithTag(FitnessTestTags.BottomNav).assertDoesNotExist()
        composeRule.onNodeWithTag(LibraryFilterBackTag).performClick()
        composeRule.onNodeWithTag(LibrarySearchTag).performTextReplacement("高位下拉")
        waitForTag(LibraryResult2330Tag)
        composeRule.onNodeWithTag(LibraryResult2330Tag).performClick()
        waitForTag(ExerciseDetailTag)
        composeRule.onNodeWithText("用于本次训练").assertIsDisplayed()
        composeRule.onNodeWithTag(AddExerciseTag).performClick()

        waitUntilState { refreshed ->
            refreshed.workoutSessionExercises.any { it.sessionId == session.id && it.exerciseId == "2330" }
        }
        assertFalse(
            currentState().plannedExercises.any { it.plannedWorkoutId == planId && it.exerciseId == "2330" },
        )

        recreateRealRoot()
        waitForTag(FitnessTestTags.TrainingActive)
        assertTrue(
            currentState().workoutSessionExercises.any {
                it.sessionId == session.id && it.exerciseId == "2330"
            },
        )
    }

    @Test
    fun planLibraryBackReturnsToThePlanEditor() {
        showRealRoot()
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Plan)).performClick()
        waitForTag(PlanScreenTag)
        composeRule.onNodeWithTag(PlanTags.Spotlight).performClick()
        waitForTag(PlanDetailTag)
        composeRule.onNodeWithTag(EditPlanTag).performClick()
        waitForTag(PlanEditTag)
        composeRule.onNodeWithTag(OpenPlanLibraryTag).performClick()
        waitForTag(LibraryScreenTag)

        pressBack()

        waitForTag(PlanEditTag)
        composeRule.onNodeWithTag(FitnessTestTags.BottomNav).assertDoesNotExist()
    }

    @Test
    fun sessionLibraryBackReturnsToTheRunningWorkout() {
        val planId = currentState().plannedWorkouts.first().id
        val session = runBlocking { repository.startWorkout(planId) }
        val state = currentState()
        composeRule.setContent {
            FitnessTheme {
                FitnessAppRootContent(
                    homeUiState = repository.homeSnapshot(state).toHomeUiState(),
                    repository = repository,
                    appState = state,
                    initialRoute = AppRoute.Library(
                        origin = PrimaryTab.Training,
                        sessionId = session.id,
                    ),
                )
            }
        }
        waitForTag(LibraryScreenTag)

        pressBack()

        waitForTag(FitnessTestTags.TrainingActive)
        composeRule.onNodeWithTag(FitnessTestTags.BottomNav).assertDoesNotExist()
    }

    private fun exerciseMedia(id: String, name: String, bodyPart: String, target: String) =
        ExerciseMediaEntity(
            exerciseId = id,
            name = name,
            bodyPart = bodyPart,
            equipment = "body weight",
            target = target,
            mediaId = id,
            localPath = "",
            assetPackId = "test",
            bytes = 0L,
            sha256 = "",
        )

    private fun showRealRoot() {
        composeRule.setContent {
            FitnessTheme { FitnessAppRoot(repository = repository) }
        }
        waitForTag(FitnessTestTags.HomePrimaryAction)
    }

    private fun recreateRealRoot() {
        composeRule.activityRule.scenario.recreate()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                FitnessTheme { FitnessAppRoot(repository = repository) }
            }
        }
        composeRule.waitForIdle()
    }

    private fun currentState() = runBlocking { repository.appState().first() }

    private fun waitUntilState(predicate: (com.shanqijie.fitnessapp.data.FitnessAppState) -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 30_000) { predicate(currentState()) }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val PlanScreenTag = "plan-screen"
        const val MonthlyGeneratorTag = "monthly-plan-generator"
        const val NewPlanTag = "new-plan"
        const val PlanEditorTag = "plan-editor"
        const val PlanNameInputTag = "plan-name-input"
        const val PlanDateInputTag = "plan-date-input"
        const val SaveNewPlanTag = "save-new-plan"
        const val PlanDetailTag = "plan-detail"
        const val EditPlanTag = "edit-plan"
        const val PlanEditTag = "plan-edit"
        const val PlanEditNameTag = "plan-edit-name"
        const val SavePlanEditTag = "save-plan-edit"
        const val OpenPlanLibraryTag = "open-plan-library"
        const val GenerateMonthlyDraftTag = "generate-monthly-draft"
        const val MonthlyDraftTag = "plan-draft-screen"
        const val ConfirmMonthlyDraftTag = "confirm-monthly-draft"
        const val LibraryScreenTag = "library-screen"
        const val LibrarySearchTag = "library-search"
        const val LibraryFilterBackTag = "library-filter-back"
        const val LibraryResult2330Tag = "library-result-2330"
        const val ExerciseDetailTag = "exercise-detail"
        const val AddExerciseTag = "add-exercise"
    }
}
