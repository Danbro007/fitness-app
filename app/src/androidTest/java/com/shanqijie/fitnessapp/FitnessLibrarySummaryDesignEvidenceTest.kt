package com.shanqijie.fitnessapp

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.shanqijie.fitnessapp.data.FitnessDatabase
import com.shanqijie.fitnessapp.data.FitnessRepository
import com.shanqijie.fitnessapp.data.FitnessStore
import com.shanqijie.fitnessapp.domain.WorkoutSummary
import com.shanqijie.fitnessapp.ui.FitnessAppRootContent
import com.shanqijie.fitnessapp.ui.FitnessAppRoot
import com.shanqijie.fitnessapp.ui.library.LibraryTags
import com.shanqijie.fitnessapp.ui.model.toHomeUiState
import com.shanqijie.fitnessapp.ui.navigation.AppRoute
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab
import com.shanqijie.fitnessapp.ui.profile.ProfileTags
import com.shanqijie.fitnessapp.ui.theme.FitnessTheme
import com.shanqijie.fitnessapp.ui.training.WorkoutSummaryScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class FitnessLibrarySummaryDesignEvidenceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var database: FitnessDatabase
    private lateinit var repository: FitnessRepository
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "fitness-library-summary-evidence-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        database = FitnessDatabase(context, databaseName)
        repository = FitnessRepository(context, FitnessStore(database))
        runBlocking {
            repository.bootstrap()
            repository.saveUserProfile(
                displayName = "山崎杰",
                birthYear = 1994,
                heightCm = 176.0,
                weightKg = 76.5,
                goal = "增肌",
                injuries = "右肩偶尔不适，避免过度外展。",
                weeklyTrainingDays = 3,
                preferredMinutes = 35,
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
    fun capturesLibraryAndExerciseDetailAtSystemViewport() {
        val state = runBlocking { repository.appState().first() }
        composeRule.setContent {
            FitnessTheme {
                FitnessAppRootContent(
                    homeUiState = repository.homeSnapshot(state).toHomeUiState(),
                    repository = repository,
                    appState = state,
                    initialRoute = AppRoute.Library(origin = PrimaryTab.Home),
                )
            }
        }
        waitForTag(LibraryTags.Screen)
        capture("library-native.png")

        val exerciseId = state.exercises.first { it.exerciseId == "0748" }.exerciseId
        composeRule.onNodeWithTag(LibraryTags.result(exerciseId)).performClick()
        waitForTag(LibraryTags.Detail)
        capture("exercise-detail-native.png")
    }

    @Test
    fun capturesWorkoutSummaryAtSystemViewport() {
        composeRule.setContent {
            FitnessTheme {
                WorkoutSummaryScreen(
                    summary = WorkoutSummary(
                        sessionId = "summary-design-evidence",
                        completedSets = 1,
                        targetSets = 7,
                        totalVolumeKg = 560.0,
                        durationSeconds = 60,
                        feelingCounts = mapOf("合适" to 1),
                    ),
                    weeklyCompleted = 3,
                    weeklyTarget = 3,
                    onDone = {},
                    reviewDraft = null,
                    onGenerateReview = { _, _ -> },
                    onResolveReview = { _, _ -> },
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }
        composeRule.waitForIdle()
        capture("summary-native.png")
    }

    @Test
    fun capturesVenueSettingsAtSystemViewport() = captureRoute(AppRoute.VenueSettings, "venue-native.png")

    @Test
    fun capturesSmartSettingsAtSystemViewport() = captureRoute(AppRoute.SmartSettings, "smart-native.png")

    @Test
    fun capturesBackupSettingsAtSystemViewport() = captureRoute(AppRoute.DataBackup, "backup-native.png")

    @Test
    fun capturesAboutAtSystemViewport() = captureRoute(AppRoute.About, "about-native.png")

    @Test
    fun capturesProfileEditAtSystemViewport() = captureRoute(AppRoute.ProfileEdit, "profile-edit-native.png")

    @Test
    fun capturesProfileAndVisualModeAtSystemViewport() {
        captureRoute(AppRoute.Primary(PrimaryTab.Profile), "profile-native.png")
        composeRule.onNodeWithTag(ProfileTags.VisualRow).performClick()
        waitForTag(ProfileTags.VisualSheet)
        composeRule.onNodeWithText("暗夜").performClick()
        composeRule.onAllNodesWithText("暗夜").onFirst().assertIsDisplayed()
        capture("theme-sheet-native.png")
    }

    @Test
    fun capturesOnboardingAtSystemViewport() {
        runBlocking { repository.setOnboardingCompleted(false) }
        composeRule.setContent { FitnessTheme { FitnessAppRoot(repository = repository) } }
        waitForTag(ProfileTags.Edit)
        capture("onboarding-native.png")
    }

    private fun captureRoute(route: AppRoute, fileName: String) {
        val state = runBlocking { repository.appState().first() }
        composeRule.setContent {
            FitnessTheme {
                FitnessAppRootContent(
                    homeUiState = repository.homeSnapshot(state).toHomeUiState(),
                    repository = repository,
                    appState = state,
                    initialRoute = route,
                )
            }
        }
        composeRule.waitForIdle()
        capture(fileName)
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun capture(fileName: String) {
        composeRule.waitForIdle()
        Thread.sleep(1_500)
        val file = File(context.filesDir, fileName)
        FileOutputStream(file).use { output ->
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        assertTrue(file.length() > 0L)
    }
}
