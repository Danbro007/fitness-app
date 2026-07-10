package com.shanqijie.fitnessapp

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import com.shanqijie.fitnessapp.data.FitnessDatabase
import com.shanqijie.fitnessapp.data.FitnessRepository
import com.shanqijie.fitnessapp.data.FitnessStore
import com.shanqijie.fitnessapp.data.WorkoutSessionEntity
import com.shanqijie.fitnessapp.domain.WorkoutSummary
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
import com.shanqijie.fitnessapp.ui.training.WorkoutSummaryScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
        runBlocking { repository.bootstrap() }
    }

    @After
    fun tearDown() {
        composeRule.runOnUiThread { composeRule.activity.setContent {} }
        composeRule.waitForIdle()
        database.close()
        assertTrue(context.deleteDatabase(databaseName))
    }

    @Test
    fun workoutFlowPersistsAcrossActivityRecreationAndUpdatesHome() {
        showRealRoot()
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Training)).performClick()
        composeRule.onNodeWithTag(FitnessTestTags.TrainingPrep).assertIsDisplayed()
        composeRule.onNodeWithTag(FitnessTestTags.StartWorkout).performClick()

        waitForTag(FitnessTestTags.TrainingActive)
        composeRule.onNodeWithTag(FitnessTestTags.BottomNav).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("增加重量").performClick()
        composeRule.onNodeWithText("吃力").performClick()
        composeRule.onNodeWithTag(FitnessTestTags.CompleteSet).performClick()

        waitForTag(FitnessTestTags.RestPanel)
        val restingState = runBlocking { repository.appState().first() }
        val activeSession = restingState.unfinishedSessions.single()
        val recordedLog = restingState.workoutSetLogs.single { it.sessionId == activeSession.id }
        assertNotNull(activeSession.restEndsAt)
        assertEquals(72.5, recordedLog.actualWeightKg, 0.01)
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
        composeRule.onNodeWithText("1 组").assertIsDisplayed()
        composeRule.onNodeWithText("580 kg").assertIsDisplayed()
        composeRule.onNodeWithTag(FitnessTestTags.SummaryDone).performClick()

        waitForTag(FitnessTestTags.WeeklyProgress)
        composeRule.onNodeWithTag(FitnessTestTags.WeeklyProgress).performScrollTo()
        val completedState = runBlocking { repository.appState().first() }
        val completedSnapshot = repository.homeSnapshot(completedState)
        composeRule.onNodeWithText(
            "${completedSnapshot.completedThisWeek} / ${completedSnapshot.targetThisWeek} 次",
        ).assertIsDisplayed()
        assertEquals(1, completedSnapshot.completedThisWeek)
        assertEquals("completed", completedState.workoutSessions.single().status)
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
                    onFinishWorkout = {},
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
    fun phoneSurfaceLabelsUseInkInsteadOfSuccessGreen() {
        showRealRoot()
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Training)).performClick()
        assertTextContainsColor("TRAINING / READY", FitnessColors.Ink)
        assertTextDoesNotContainColor("TRAINING / READY", FitnessColors.Green)
        assertTextContainsColor("01", FitnessColors.Ink)
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
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertTextContainsColor("WORKOUT / SAVED", FitnessColors.Ink)
        assertTextDoesNotContainColor("WORKOUT / SAVED", FitnessColors.Green)
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
                    onFinishWorkout = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { restFinishedCount.intValue == 1 }
        assertEquals(1, restFinishedCount.intValue)
    }

    private fun showRealRoot() {
        composeRule.setContent {
            FitnessTheme {
                FitnessAppRoot(repository = repository)
            }
        }
        waitForTag(FitnessTestTags.HomePrimaryAction)
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
