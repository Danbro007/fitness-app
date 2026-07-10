package com.shanqijie.fitnessapp

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
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
import com.shanqijie.fitnessapp.ui.FitnessAppRoot
import com.shanqijie.fitnessapp.ui.FitnessAppRootContent
import com.shanqijie.fitnessapp.ui.model.toHomeUiState
import com.shanqijie.fitnessapp.ui.navigation.AppRoute
import com.shanqijie.fitnessapp.ui.navigation.FitnessTestTags
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab
import com.shanqijie.fitnessapp.ui.theme.FitnessTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
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
    fun planHierarchyEditsRealPlansAndConfirmsFourWeekDraft() {
        showRealRoot()
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Plan)).performClick()
        waitForTag(PlanScreenTag)

        val weeklyTop = composeRule.onNodeWithTag(WeeklyScheduleTag)
            .fetchSemanticsNode().boundsInRoot.top
        val monthlyTop = composeRule.onNodeWithTag(MonthlyGeneratorTag)
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(weeklyTop < monthlyTop)
        composeRule.onAllNodesWithText("休息日").onFirst().assertIsDisplayed()

        val initialCount = currentState().plannedWorkouts.size
        composeRule.onNodeWithTag(NewPlanTag).performClick()
        composeRule.onNodeWithTag(PlanEditorTag).assertIsDisplayed()
        assertEquals(initialCount, currentState().plannedWorkouts.size)
        composeRule.onNodeWithTag(PlanNameInputTag).performTextReplacement("周末背部训练")
        composeRule.onNodeWithTag(PlanDateInputTag)
            .performTextReplacement(LocalDate.now().plusDays(1).toString())
        composeRule.onNodeWithTag(SaveNewPlanTag).performClick()

        waitUntilState { state -> state.plannedWorkouts.any { it.name == "周末背部训练" } }
        val createdPlan = currentState().plannedWorkouts.single { it.name == "周末背部训练" }
        composeRule.onNodeWithTag("plan-row-${createdPlan.id}").performScrollTo().performClick()
        waitForTag(PlanDetailTag)
        composeRule.onNodeWithTag(EditPlanTag).performClick()
        waitForTag(PlanEditTag)
        composeRule.onNodeWithTag(PlanEditNameTag).performTextReplacement("周末背部训练 B")
        composeRule.onNodeWithTag(SavePlanEditTag).performClick()
        waitUntilState { state -> state.plannedWorkouts.any { it.id == createdPlan.id && it.name.endsWith(" B") } }

        pressBack()
        waitForTag(PlanScreenTag)
        val beforeDraftState = currentState()
        composeRule.onNodeWithTag(GenerateMonthlyDraftTag).performScrollTo().performClick()
        waitForTag(MonthlyDraftTag)
        composeRule.onNodeWithText("确认后复制为 4 周").performScrollTo().assertIsDisplayed()
        val draftState = currentState()
        assertEquals(beforeDraftState.plannedWorkouts.size, draftState.plannedWorkouts.size)
        assertTrue(draftState.aiDrafts.any { it.type == "weekly_plan" && it.status == "draft" })

        val beforeConfirmIds = draftState.plannedWorkouts.mapTo(mutableSetOf()) { it.id }
        composeRule.onNodeWithTag(ConfirmMonthlyDraftTag).performScrollTo().performClick()
        waitUntilState { state -> state.plannedWorkouts.size == beforeDraftState.plannedWorkouts.size + 4 }
        val confirmedState = currentState()
        val generatedPlans = confirmedState.plannedWorkouts
            .filterNot { it.id in beforeConfirmIds }
            .sortedBy { it.scheduledDate }
        assertEquals(4, generatedPlans.size)
        assertEquals(4, generatedPlans.map { it.id }.distinct().size)
        generatedPlans.zipWithNext().forEach { (first, second) ->
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
        val planId = currentState().plannedWorkouts.first().id
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Plan)).performClick()
        waitForTag(PlanScreenTag)
        composeRule.onNodeWithTag("plan-row-$planId").performScrollTo().performClick()
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
        composeRule.onNodeWithContentDescription("绳索高位下拉全程").assertIsDisplayed()
        composeRule.onNodeWithTag(LibraryResult2330Tag).performClick()

        waitForTag(ExerciseDetailTag)
        composeRule.onNodeWithText("绳索高位下拉全程").assertIsDisplayed()
        composeRule.onNodeWithText("添加到计划").assertIsDisplayed()
        composeRule.onNodeWithTag(AddExerciseTag).performClick()
        waitUntilState { state ->
            state.plannedExercises.any { it.plannedWorkoutId == planId && it.exerciseId == "2330" }
        }
        assertFalse(currentState().workoutSessionExercises.any { it.exerciseId == "2330" })

        recreateRealRoot()
        assertTrue(
            currentState().plannedExercises.any { it.plannedWorkoutId == planId && it.exerciseId == "2330" },
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
        composeRule.onNodeWithText("绳索高位下拉全程 0/3").assertIsDisplayed()
    }

    @Test
    fun planLibraryBackReturnsToThePlanEditor() {
        showRealRoot()
        val planId = currentState().plannedWorkouts.first().id
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Plan)).performClick()
        waitForTag(PlanScreenTag)
        composeRule.onNodeWithTag("plan-row-$planId").performScrollTo().performClick()
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
        const val WeeklyScheduleTag = "weekly-schedule"
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
        const val MonthlyDraftTag = "monthly-plan-draft"
        const val ConfirmMonthlyDraftTag = "confirm-monthly-draft"
        const val LibraryScreenTag = "library-screen"
        const val LibrarySearchTag = "library-search"
        const val LibraryFilterBackTag = "library-filter-back"
        const val LibraryResult2330Tag = "library-result-2330"
        const val ExerciseDetailTag = "exercise-detail"
        const val AddExerciseTag = "add-exercise"
    }
}
