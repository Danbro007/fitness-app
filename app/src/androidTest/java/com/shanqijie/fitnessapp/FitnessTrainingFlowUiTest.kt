package com.shanqijie.fitnessapp

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.pressBack
import com.shanqijie.fitnessapp.data.FitnessDatabase
import com.shanqijie.fitnessapp.data.FitnessRepository
import com.shanqijie.fitnessapp.data.FitnessStore
import com.shanqijie.fitnessapp.data.AiDraftEntity
import com.shanqijie.fitnessapp.data.WorkoutSessionEntity
import com.shanqijie.fitnessapp.domain.WorkoutSummary
import com.shanqijie.fitnessapp.domain.WorkoutAdjustmentDirection
import com.shanqijie.fitnessapp.domain.WorkoutReviewMetadata
import com.shanqijie.fitnessapp.domain.toJson
import com.shanqijie.fitnessapp.ui.FitnessAppRoot
import com.shanqijie.fitnessapp.ui.FitnessAppRootContent
import com.shanqijie.fitnessapp.ui.model.toHomeUiState
import com.shanqijie.fitnessapp.ui.navigation.AppRoute
import com.shanqijie.fitnessapp.ui.navigation.FitnessTestTags
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab
import com.shanqijie.fitnessapp.ui.theme.FitnessTheme
import com.shanqijie.fitnessapp.ui.theme.FitnessColors
import com.shanqijie.fitnessapp.ui.training.TrainingActiveScreen
import com.shanqijie.fitnessapp.ui.training.TrainingActiveScreenUi
import com.shanqijie.fitnessapp.ui.training.TrainingExerciseScreenUi
import com.shanqijie.fitnessapp.ui.training.TrainingPreparationScreen
import com.shanqijie.fitnessapp.ui.training.WorkoutSummaryScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class FitnessTrainingFlowUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var database: FitnessDatabase
    private lateinit var store: FitnessStore
    private lateinit var repository: FitnessRepository
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "fitness-training-ui-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        database = FitnessDatabase(context, databaseName)
        store = FitnessStore(database)
        repository = FitnessRepository(context, store)
        runBlocking {
            repository.bootstrap()
            repository.createWorkoutFromTemplate(
                name = "测试训练",
                scheduledDate = java.time.LocalDate.now().toString(),
                venueId = FitnessRepository.DEFAULT_VENUE_ID,
            )
            repository.setOnboardingCompleted(true)
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
    fun partialWorkoutPersistsAcrossActivityRecreationWithoutUpdatingWeeklyCompletion() {
        showRealRoot()
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Training)).performClick()
        composeRule.onNodeWithTag(FitnessTestTags.TrainingPrep).assertIsDisplayed()
        composeRule.onNodeWithTag(FitnessTestTags.StartWorkout).performClick()

        waitForTag(FitnessTestTags.TrainingActive)
        composeRule.onNodeWithTag(FitnessTestTags.BottomNav).assertDoesNotExist()
        composeRule.onNodeWithTag("training-weight-input").performTextReplacement("9999")
        composeRule.onNodeWithTag("training-reps-input").performTextReplacement("abc")
        composeRule.onNodeWithTag("training-weight-input").performTextReplacement("82.5")
        composeRule.onNodeWithTag("training-reps-input").performTextReplacement("12")
        composeRule.onNodeWithText("吃力").performClick()
        composeRule.onNodeWithTag(FitnessTestTags.CompleteSet).performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { repository.appState().first().workoutSetLogs.isNotEmpty() }
        }
        val recordedState = runBlocking { repository.appState().first() }
        val recordedSession = recordedState.unfinishedSessions.single()
        runBlocking { repository.startRest(recordedSession.id, durationSeconds = 300) }
        waitForTag(FitnessTestTags.RestPanel)
        val restingState = runBlocking { repository.appState().first() }
        val activeSession = restingState.unfinishedSessions.single()
        val recordedLog = restingState.workoutSetLogs.single { it.sessionId == activeSession.id }
        assertNotNull(activeSession.restEndsAt)
        assertEquals(82.5, recordedLog.actualWeightKg, 0.01)
        assertEquals(12, recordedLog.actualReps)
        assertEquals("吃力", recordedLog.feeling)

        composeRule.activityRule.scenario.recreate()
        composeRule.activityRule.scenario.onActivity {
            it.setContent {
                FitnessTheme {
                    FitnessAppRoot(repository = repository)
                }
            }
        }
        waitForTag(FitnessTestTags.TrainingActive)
        composeRule.onNodeWithTag(FitnessTestTags.RestPanel).assertIsDisplayed()
        composeRule.onNodeWithTag(FitnessTestTags.BottomNav).assertDoesNotExist()

        composeRule.onNodeWithTag(FitnessTestTags.SkipRest).performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag(FitnessTestTags.RestPanel).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(FitnessTestTags.RequestFinish).performClick()
        composeRule.onNodeWithTag(FitnessTestTags.ConfirmFinish).assertIsDisplayed().performClick()

        waitForTag(FitnessTestTags.WorkoutSummary)
        composeRule.onNodeWithText("部分完成").assertIsDisplayed()
        composeRule.onNodeWithText("/ 7 组", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("990 kg").assertIsDisplayed()
        composeRule.onNodeWithTag(FitnessTestTags.SummaryDone).performClick()

        waitForTag(FitnessTestTags.WeeklyProgress)
        composeRule.onNodeWithTag(FitnessTestTags.WeeklyProgress).performScrollTo()
        val completedState = runBlocking { repository.appState().first() }
        val completedSnapshot = repository.homeSnapshot(completedState)
        composeRule.onNodeWithText(
            "${completedSnapshot.completedThisWeek} / ${completedSnapshot.targetThisWeek} 次",
        ).assertIsDisplayed()
        assertEquals(0, completedSnapshot.completedThisWeek)
        assertEquals("partial", completedState.workoutSessions.single().status)
    }

    @Test
    fun legacyUnfinishedSessionWithoutExerciseSnapshotStartsOnHome() {
        val state = runBlocking { repository.appState().first() }
        val now = System.currentTimeMillis()
        store.upsertWorkoutSession(
            WorkoutSessionEntity(
                id = "legacy-unrecoverable-session",
                plannedWorkoutId = null,
                venueId = requireNotNull(state.venue).id,
                exerciseId = FitnessRepository.SMITH_BENCH_PRESS_ID,
                status = "in_progress",
                startedAt = now,
                endedAt = null,
                updatedAt = now,
                currentExerciseId = FitnessRepository.SMITH_BENCH_PRESS_ID,
            ),
        )

        composeRule.setContent {
            FitnessTheme { FitnessAppRoot(repository = repository) }
        }
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag(FitnessTestTags.HomePrimaryAction).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(FitnessTestTags.BottomNav).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(FitnessTestTags.TrainingActive).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(FitnessTestTags.HomePrimaryAction).assertIsDisplayed()
        composeRule.onNodeWithTag(FitnessTestTags.BottomNav).assertIsDisplayed()
        composeRule.onNodeWithTag(FitnessTestTags.TrainingActive).assertDoesNotExist()
    }

    @Test
    fun invalidSavedActiveRouteOffersReturnHomeAndAllowsSystemBack() {
        val state = runBlocking { repository.appState().first() }
        val homeUiState = repository.homeSnapshot(state).toHomeUiState()
        composeRule.setContent {
            FitnessTheme {
                FitnessAppRootContent(
                    homeUiState = homeUiState,
                    repository = repository,
                    appState = state,
                    initialRoute = AppRoute.TrainingActive("missing-session"),
                )
            }
        }

        waitForText("无法恢复这次训练")
        composeRule.onNodeWithText("返回首页").assertIsDisplayed()
        pressBack()
        waitForTag(FitnessTestTags.HomePrimaryAction)
    }

    @Test
    fun activeSnapshotWithoutRepositoryFallsBackHomeOnSystemBack() {
        val session = runBlocking {
            val planId = repository.appState().first().plannedWorkouts.first().id
            repository.startWorkout(planId)
        }
        val state = runBlocking { repository.appState().first() }
        val homeUiState = repository.homeSnapshot(state).toHomeUiState()
        composeRule.setContent {
            FitnessTheme {
                FitnessAppRootContent(
                    homeUiState = homeUiState,
                    repository = null,
                    appState = state,
                    initialRoute = AppRoute.TrainingActive(session.id),
                )
            }
        }

        waitForText("无法恢复这次训练")
        pressBack()
        waitForTag(FitnessTestTags.HomePrimaryAction)
    }

    @Test
    fun rapidDoubleTapOnCompleteSetWritesOnlyOneLog() {
        showRealRoot()
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Training)).performClick()
        composeRule.onNodeWithTag(FitnessTestTags.StartWorkout).performClick()
        waitForTag(FitnessTestTags.TrainingActive)

        composeRule.onNodeWithTag(FitnessTestTags.CompleteSet).performTouchInput {
            click()
            click()
        }

        waitForTag(FitnessTestTags.RestPanel)
        val state = runBlocking { repository.appState().first() }
        val session = state.unfinishedSessions.single()
        assertEquals(1, state.workoutSetLogs.count { it.sessionId == session.id })
    }

    @Test
    fun recordFailureIsShownAndDoesNotEscapeComposition() {
        val activeState = sampleActiveState()
        composeRule.setContent {
            FitnessTheme {
                TrainingActiveScreen(
                    state = activeState,
                    onSelectExercise = {},
                    onRecordSet = { _, _, _ -> error("保存训练组失败") },
                    onRestFinished = {},
                    onSkipRest = {},
                    onExtendRest = {},
                    onTogglePause = {},
                    onFinishWorkout = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithTag(FitnessTestTags.CompleteSet).performClick()
        composeRule.onNodeWithText("保存训练组失败").assertIsDisplayed()
        val errorContainer = Color(0xFFFFDAD6)
        val onErrorContainer = Color(0xFF690005)
        assertTrue(contrastRatio(onErrorContainer, errorContainer) >= 4.5f)
        assertTextContainsColor("保存训练组失败", errorContainer)
        assertTextContainsColor("保存训练组失败", onErrorContainer)
        composeRule.onNodeWithTag(FitnessTestTags.CompleteSet).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun phoneSurfaceLabelsUseNeutralInkInsteadOfSuccessGreen() {
        showRealRoot()
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Training)).performClick()
        assertTextContainsColor("训练准备 · 今日", FitnessColors.Muted)
        assertTextDoesNotContainColor("训练准备 · 今日", FitnessColors.Green)
        assertTextContainsColor("01", FitnessColors.Muted)
        assertTextDoesNotContainColor("01", FitnessColors.Green)

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                FitnessTheme {
                    WorkoutSummaryScreen(
                        summary = WorkoutSummary(
                            sessionId = "summary-contrast",
                            completedSets = 1,
                            targetSets = 4,
                            totalVolumeKg = 560.0,
                            durationSeconds = 60,
                            feelingCounts = mapOf("合适" to 1),
                        ),
                        weeklyCompleted = 1,
                        weeklyTarget = 3,
                        onDone = {},
                        reviewDraft = null,
                        onGenerateReview = { _, _ -> },
                        onResolveReview = { _, _ -> },
                        modifier = androidx.compose.ui.Modifier,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertTextContainsColor("训练已保存 · 本机", FitnessColors.Muted)
        assertTextDoesNotContainColor("训练已保存 · 本机", FitnessColors.Green)
    }

    @Test
    fun workoutSummaryCollectsRecoveryFeedbackBeforeShowingConfirmableAiReview() {
        val draftState = mutableStateOf<AiDraftEntity?>(null)
        var capturedFeeling = ""
        var adjustmentApplied = false
        composeRule.setContent {
            FitnessTheme {
                WorkoutSummaryScreen(
                    summary = WorkoutSummary(
                        sessionId = "summary-ai-review",
                        completedSets = 4,
                        targetSets = 4,
                        totalVolumeKg = 2_800.0,
                        durationSeconds = 1_800,
                        feelingCounts = mapOf("轻松" to 3, "合适" to 1),
                    ),
                    weeklyCompleted = 1,
                    weeklyTarget = 3,
                    reviewDraft = draftState.value,
                    onGenerateReview = { feeling, _ ->
                        capturedFeeling = feeling
                        draftState.value = AiDraftEntity(
                            id = "review-draft",
                            type = "workout_review",
                            title = "建议小幅加量",
                            content = "## 总结\n完成度良好，后续同动作可小幅加量。",
                            status = "draft",
                            createdAt = 1L,
                            updatedAt = 1L,
                            metadataJson = WorkoutReviewMetadata(
                                sessionId = "summary-ai-review",
                                direction = WorkoutAdjustmentDirection.INCREASE.name,
                                postWorkoutFeeling = feeling,
                                postWorkoutNote = "",
                                exerciseIds = listOf("0748"),
                            ).toJson(),
                            confirmedAt = null,
                        )
                    },
                    onResolveReview = { _, apply -> adjustmentApplied = apply },
                    onDone = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithText("训练后感受").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("状态很好").performClick()
        composeRule.onNodeWithText("生成 AI 训练总结").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { draftState.value != null }
        assertEquals("状态很好", capturedFeeling)
        composeRule.onNodeWithText("AI 训练复盘").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("完成度良好，后续同动作可小幅加量。", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("确认小幅加量").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { adjustmentApplied }
    }

    @Test
    fun workoutSummaryLocksReviewActionsWhileAiWorkIsRunning() {
        val releaseGenerate = CompletableDeferred<Unit>()
        val releaseResolve = CompletableDeferred<Unit>()
        val draftState = mutableStateOf<AiDraftEntity?>(null)
        var generateStarted = false
        var resolveStarted = false
        composeRule.setContent {
            FitnessTheme {
                WorkoutSummaryScreen(
                    summary = WorkoutSummary("summary-busy", 4, 4, 2_000.0, 1_200, mapOf("轻松" to 4)),
                    weeklyCompleted = 1,
                    weeklyTarget = 3,
                    reviewDraft = draftState.value,
                    onGenerateReview = { _, _ ->
                        generateStarted = true
                        releaseGenerate.await()
                        draftState.value = AiDraftEntity(
                            id = "busy-review",
                            type = "workout_review",
                            title = "建议小幅加量",
                            content = "训练完成度良好。",
                            status = "draft",
                            createdAt = 1L,
                            updatedAt = 1L,
                            metadataJson = WorkoutReviewMetadata(
                                sessionId = "summary-busy",
                                direction = WorkoutAdjustmentDirection.INCREASE.name,
                                postWorkoutFeeling = "状态很好",
                                postWorkoutNote = "",
                                exerciseIds = listOf("0748"),
                            ).toJson(),
                            confirmedAt = null,
                        )
                    },
                    onResolveReview = { _, _ ->
                        resolveStarted = true
                        releaseResolve.await()
                    },
                    onDone = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithText("生成 AI 训练总结").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { generateStarted }
        composeRule.onNodeWithText("正在分析…").assertIsDisplayed().assertIsNotEnabled()
        composeRule.runOnIdle { releaseGenerate.complete(Unit) }
        composeRule.onNodeWithText("AI 训练复盘").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("确认小幅加量").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { resolveStarted }
        composeRule.onNodeWithText("确认小幅加量").assertIsNotEnabled()
        composeRule.onNodeWithText("保持原计划").assertIsNotEnabled()
        composeRule.runOnIdle { releaseResolve.complete(Unit) }
        composeRule.onNodeWithText("确认小幅加量").assertIsEnabled()
    }

    @Test
    fun workoutSummaryKeepsUserOnThePageWhenAiReviewGenerationFails() {
        composeRule.setContent {
            FitnessTheme {
                WorkoutSummaryScreen(
                    summary = WorkoutSummary(
                        sessionId = "summary-ai-error",
                        completedSets = 3,
                        targetSets = 4,
                        totalVolumeKg = 1_800.0,
                        durationSeconds = 1_200,
                        feelingCounts = mapOf("合适" to 3),
                    ),
                    weeklyCompleted = 0,
                    weeklyTarget = 3,
                    onGenerateReview = { _, _ -> error("AI 服务暂时不可用") },
                    reviewDraft = null,
                    onResolveReview = { _, _ -> },
                    onDone = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithText("生成 AI 训练总结").performScrollTo().performClick()
        composeRule.onNodeWithText("AI 服务暂时不可用").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("生成 AI 训练总结").assertIsEnabled()
    }

    @Test
    fun workoutSummaryReportsBothPlanAdjustmentFailuresWithoutLosingTheDraft() {
        val draft = AiDraftEntity(
            id = "review-error-draft",
            type = "workout_review",
            title = "建议降低后续负荷",
            content = "## 总结\n本次恢复压力偏高。",
            status = "draft",
            createdAt = 1L,
            updatedAt = 1L,
            metadataJson = WorkoutReviewMetadata(
                sessionId = "summary-review-error",
                direction = WorkoutAdjustmentDirection.REDUCE.name,
                postWorkoutFeeling = "非常疲劳",
                postWorkoutNote = "最后一组动作变形",
                exerciseIds = listOf("0748"),
            ).toJson(),
            confirmedAt = null,
        )
        composeRule.setContent {
            FitnessTheme {
                WorkoutSummaryScreen(
                    summary = WorkoutSummary(
                        sessionId = "summary-review-error",
                        completedSets = 3,
                        targetSets = 4,
                        totalVolumeKg = 1_800.0,
                        durationSeconds = 1_200,
                        feelingCounts = mapOf("吃力" to 3),
                    ),
                    weeklyCompleted = 0,
                    weeklyTarget = 3,
                    reviewDraft = draft,
                    onGenerateReview = { _, _ -> },
                    onResolveReview = { _, apply ->
                        if (apply) error("调整计划失败：本地写入异常")
                        error("保留原计划失败：本地写入异常")
                    },
                    onDone = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithText("确认降低后续负荷").performScrollTo().performClick()
        composeRule.onNodeWithText("调整计划失败：本地写入异常").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("保持原计划").performScrollTo().performClick()
        composeRule.onNodeWithText("保留原计划失败：本地写入异常").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("AI 训练复盘").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun workoutSummaryCanExplicitlyConfirmMaintainingTheCurrentPlan() {
        val draft = AiDraftEntity(
            id = "maintain-review-draft",
            type = "workout_review",
            title = "建议保持当前计划",
            content = "完成度与疲劳反馈处于可接受范围。",
            status = "draft",
            createdAt = 1L,
            updatedAt = 1L,
            metadataJson = WorkoutReviewMetadata(
                sessionId = "summary-maintain",
                direction = WorkoutAdjustmentDirection.MAINTAIN.name,
                postWorkoutFeeling = "一般",
                postWorkoutNote = "",
                exerciseIds = listOf("0748"),
            ).toJson(),
            confirmedAt = null,
        )
        var applied: Boolean? = null
        composeRule.setContent {
            FitnessTheme {
                WorkoutSummaryScreen(
                    summary = WorkoutSummary("summary-maintain", 3, 4, 1_800.0, 1_200, mapOf("合适" to 3)),
                    weeklyCompleted = 1,
                    weeklyTarget = 3,
                    reviewDraft = draft,
                    onGenerateReview = { _, _ -> },
                    onResolveReview = { _, apply -> applied = apply },
                    onDone = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithText("确认保持当前计划").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(true, applied) }
    }

    @Test
    fun workoutSummaryRendersEmptyFeelingsAndResolvedReviewStatuses() {
        val summary = mutableStateOf(
            WorkoutSummary("summary-resolved", 0, 1, 0.0, 0, emptyMap()),
        )
        val draft = mutableStateOf(
            AiDraftEntity(
                id = "resolved-review",
                type = "workout_review",
                title = "训练复盘",
                content = "已完成复盘。",
                status = "confirmed",
                createdAt = 1L,
                updatedAt = 1L,
                metadataJson = "not-json",
                confirmedAt = 2L,
            ),
        )
        composeRule.setContent {
            FitnessTheme {
                WorkoutSummaryScreen(
                    summary = summary.value,
                    weeklyCompleted = 0,
                    weeklyTarget = 3,
                    reviewDraft = draft.value,
                    onGenerateReview = { _, _ -> },
                    onResolveReview = { _, _ -> },
                    onDone = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithText("合适").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("已应用").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle {
            summary.value = summary.value.copy(feelingCounts = linkedMapOf("吃力" to 3, "轻松" to 1))
            draft.value = draft.value.copy(status = "dismissed")
        }
        composeRule.onNodeWithText("吃力").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("保持原计划").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle {
            draft.value = draft.value.copy(
                status = "draft",
                metadataJson = WorkoutReviewMetadata(
                    sessionId = "summary-resolved",
                    direction = "INVALID_DIRECTION",
                    postWorkoutFeeling = "一般",
                    postWorkoutNote = "",
                    exerciseIds = emptyList(),
                ).toJson(),
            )
        }
        composeRule.onNodeWithText("确认保持当前计划").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle {
            summary.value = summary.value.copy(feelingCounts = linkedMapOf("轻松" to 1, "吃力" to 3))
        }
        composeRule.onNodeWithText("吃力").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle {
            summary.value = summary.value.copy(feelingCounts = linkedMapOf("轻松" to 1, "吃力" to 1))
        }
        composeRule.onNodeWithText("轻松").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun completedTrainingShowsEnabledFinishAndAiSummaryAction() {
        val base = sampleActiveState()
        val completedState = base.copy(
            exercises = base.exercises.map { it.copy(completedSets = it.targetSets) },
        )
        var finishRequested = false
        composeRule.setContent {
            FitnessTheme {
                TrainingActiveScreen(
                    state = completedState,
                    onSelectExercise = {},
                    onRecordSet = { _, _, _ -> error("不应继续记录训练组") },
                    onRestFinished = {},
                    onSkipRest = {},
                    onExtendRest = {},
                    onTogglePause = {},
                    onFinishWorkout = { finishRequested = true },
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithText("本动作已完成", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("全部训练组已完成").assertIsDisplayed()
        composeRule.onNodeWithTag("training-weight-input").assertDoesNotExist()
        composeRule.onNodeWithTag("training-reps-input").assertDoesNotExist()
        composeRule.onNodeWithText("完成训练并查看 AI 总结").assertIsDisplayed().assertIsEnabled().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { finishRequested }
    }

    @Test
    fun emptyTrainingPreparationAcceptsAnExplicitModifier() {
        composeRule.setContent {
            FitnessTheme {
                TrainingPreparationScreen(
                    state = null,
                    onStartWorkout = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithText("暂无可开始训练").assertIsDisplayed()
    }

    @Test
    fun naturalRestExpiryCallsRestFinishedOnce() {
        val activeState = sampleActiveState().copy(restEndsAt = System.currentTimeMillis() + 250L)
        val restFinishedCount = mutableIntStateOf(0)
        composeRule.setContent {
            FitnessTheme {
                TrainingActiveScreen(
                    state = activeState,
                    onSelectExercise = {},
                    onRecordSet = { _, _, _ -> },
                    onRestFinished = { restFinishedCount.intValue += 1 },
                    onSkipRest = {},
                    onExtendRest = {},
                    onTogglePause = {},
                    onFinishWorkout = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { restFinishedCount.intValue == 1 }
        assertEquals(1, restFinishedCount.intValue)
    }

    @Test
    fun trainingTimerPauseAndRestExtensionControlsAreActionable() {
        val now = System.currentTimeMillis()
        val activeState = androidx.compose.runtime.mutableStateOf(
            sampleActiveState().copy(startedAt = now - 65_000L),
        )
        var extensionCount = 0
        composeRule.setContent {
            FitnessTheme {
                TrainingActiveScreen(
                    state = activeState.value,
                    onSelectExercise = {},
                    onRecordSet = { _, _, _ -> },
                    onRestFinished = {},
                    onSkipRest = {},
                    onExtendRest = { extensionCount += 1 },
                    onTogglePause = {
                        activeState.value = activeState.value.copy(
                            pausedAt = if (activeState.value.pausedAt == null) System.currentTimeMillis() else null,
                        )
                    },
                    onFinishWorkout = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithText("00:00").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithContentDescription("暂停训练").performClick()
        composeRule.onNodeWithText("训练已暂停 · 点击右上角继续").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("继续训练").performClick()

        activeState.value = activeState.value.copy(restEndsAt = System.currentTimeMillis() + 30_000L)
        composeRule.onNodeWithContentDescription("延长休息 30 秒").performClick()
        composeRule.runOnIdle { assertEquals(1, extensionCount) }
    }

    @Test
    fun capturesTrainingDesignStatesThroughTheRealFlow() {
        showRealRoot()
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Training)).performClick()
        waitForTag(FitnessTestTags.TrainingPrep)
        captureScreen("training-prep-native.png")

        composeRule.onNodeWithTag(FitnessTestTags.StartWorkout).performClick()
        waitForTag(FitnessTestTags.TrainingActive)
        captureScreen("training-active-native.png")
        composeRule.onNodeWithTag("training-weight-input").performClick()
        captureScreen("training-weight-input-native.png")
        pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FitnessTestTags.CompleteSet).performClick()
        waitForTag(FitnessTestTags.RestPanel)
        captureScreen("training-rest-native.png")

        composeRule.onNodeWithTag(FitnessTestTags.SkipRest).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(FitnessTestTags.RestPanel).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(FitnessTestTags.RequestFinish).performClick()
        waitForTag(FitnessTestTags.ConfirmFinish)
        captureScreen("training-finish-dialog-native.png")
        composeRule.onNodeWithText("继续训练").performClick()
        composeRule.waitForIdle()
    }

    private fun showRealRoot() {
        composeRule.setContent {
            FitnessTheme {
                FitnessAppRoot(repository = repository)
            }
        }
        waitForTag(FitnessTestTags.HomePrimaryAction)
    }

    private fun captureScreen(fileName: String) {
        composeRule.waitForIdle()
        Thread.sleep(600)
        val file = File(context.filesDir, fileName)
        FileOutputStream(file).use { output ->
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        assertTrue(file.length() > 0L)
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun sampleActiveState(): TrainingActiveScreenUi {
        val state = runBlocking { repository.appState().first() }
        val view = state.plannedExerciseViews.first()
        return TrainingActiveScreenUi(
            sessionId = "screen-only-session",
            planName = "界面回归训练",
            startedAt = System.currentTimeMillis() - 65_000L,
            currentExerciseId = view.plannedExercise.exerciseId,
            restEndsAt = null,
            exercises = listOf(
                TrainingExerciseScreenUi(
                    sessionExerciseId = view.plannedExercise.id,
                    exerciseId = view.plannedExercise.exerciseId,
                    name = view.media.name,
                    detail = "胸部 · 史密斯机",
                    assetPath = view.media.localPath,
                    targetSets = view.plannedExercise.targetSets,
                    targetReps = view.plannedExercise.targetReps,
                    targetWeightKg = view.plannedExercise.targetWeightKg,
                    completedSets = 0,
                ),
            ),
        )
    }

    private fun assertTextContainsColor(text: String, expected: Color) {
        assertTrue(
            "Expected '$text' to contain ${expected.value.toString(16)}",
            nodeContainsColor(text, expected),
        )
    }

    private fun assertTextDoesNotContainColor(text: String, unexpected: Color) {
        assertFalse(
            "Expected '$text' not to contain ${unexpected.value.toString(16)}",
            nodeContainsColor(text, unexpected),
        )
    }

    private fun nodeContainsColor(text: String, expected: Color): Boolean {
        val pixels = composeRule.onNodeWithText(text).captureToImage().toPixelMap()
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val color = pixels[x, y]
                if (
                    kotlin.math.abs(color.red - expected.red) < 0.025f &&
                    kotlin.math.abs(color.green - expected.green) < 0.025f &&
                    kotlin.math.abs(color.blue - expected.blue) < 0.025f &&
                    color.alpha > 0.8f
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val foregroundLuminance = foreground.luminance()
        val backgroundLuminance = background.luminance()
        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
