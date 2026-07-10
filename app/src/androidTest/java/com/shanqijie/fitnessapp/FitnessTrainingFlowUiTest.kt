package com.shanqijie.fitnessapp

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.shanqijie.fitnessapp.data.FitnessDatabase
import com.shanqijie.fitnessapp.data.FitnessRepository
import com.shanqijie.fitnessapp.data.FitnessStore
import com.shanqijie.fitnessapp.ui.FitnessAppRoot
import com.shanqijie.fitnessapp.ui.navigation.FitnessTestTags
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab
import com.shanqijie.fitnessapp.ui.theme.FitnessTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
    private lateinit var repository: FitnessRepository
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "fitness-training-ui-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        database = FitnessDatabase(context, databaseName)
        repository = FitnessRepository(context, FitnessStore(database))
        runBlocking { repository.bootstrap() }
        showRealRoot()
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
}
