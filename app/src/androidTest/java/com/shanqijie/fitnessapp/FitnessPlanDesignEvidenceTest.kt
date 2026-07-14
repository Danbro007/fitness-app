package com.shanqijie.fitnessapp

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.shanqijie.fitnessapp.data.FitnessDatabase
import com.shanqijie.fitnessapp.data.FitnessRepository
import com.shanqijie.fitnessapp.data.FitnessStore
import com.shanqijie.fitnessapp.data.BodyMeasurement
import com.shanqijie.fitnessapp.ui.FitnessAppRoot
import com.shanqijie.fitnessapp.ui.navigation.FitnessTestTags
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab
import com.shanqijie.fitnessapp.ui.plan.PlanTags
import com.shanqijie.fitnessapp.ui.theme.FitnessTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

class FitnessPlanDesignEvidenceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var database: FitnessDatabase
    private lateinit var repository: FitnessRepository
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "fitness-plan-design-${System.nanoTime()}.db"
        database = FitnessDatabase(context, databaseName)
        repository = FitnessRepository(context, FitnessStore(database))
        runBlocking {
            repository.bootstrap()
            repository.saveUserProfile(
                "山崎杰", 1994, 176.0, 76.5, "增肌", "右肩偶尔不适，避免过度外展。", 3, 35,
                BodyMeasurement(
                    bodyFatPercentage = 24.8,
                    bodyFatMassKg = 19.0,
                    bmi = 25.6,
                    skeletalMuscleKg = 32.5,
                    bodyWaterKg = 42.1,
                    basalMetabolismKcal = 1613,
                    waistHipRatio = 0.90,
                ),
            )
            repository.setOnboardingCompleted(true)
            repository.createWorkoutFromTemplate("胸部力量 A", LocalDate.now().toString(), FitnessRepository.DEFAULT_VENUE_ID)
        }
    }

    @After
    fun tearDown() {
        composeRule.runOnUiThread { composeRule.activity.setContent {} }
        composeRule.waitForIdle()
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun capturesPlanCalendarStatesAtOneNativeViewport() {
        composeRule.setContent { FitnessTheme { FitnessAppRoot(repository = repository) } }
        waitForTag(FitnessTestTags.HomePrimaryAction)
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Plan)).performClick()
        waitForTag(PlanTags.Screen)
        capture("plan-week-native.png")

        composeRule.onNodeWithTag("calendar-day-${LocalDate.now()}").performScrollTo().performClick()
        waitForTag(PlanTags.DayDetail)
        capture("plan-day-detail-native.png")
        androidx.test.espresso.Espresso.pressBack()
        waitForTag(PlanTags.Screen)
        composeRule.onNodeWithText("训练日历").performScrollTo()

        composeRule.onNodeWithText("月").performClick()
        capture("plan-month-native.png")
        composeRule.onNodeWithText("训练日历").performScrollTo()
        composeRule.onNodeWithText("年").performClick()
        capture("plan-year-native.png")
    }

    @Test
    fun capturesPlanDetailEditAndDraftAtOneNativeViewport() {
        composeRule.setContent { FitnessTheme { FitnessAppRoot(repository = repository) } }
        waitForTag(FitnessTestTags.HomePrimaryAction)
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Plan)).performClick()
        waitForTag(PlanTags.Screen)

        composeRule.onNodeWithTag(PlanTags.Spotlight).performClick()
        waitForTag(PlanTags.Detail)
        capture("plan-detail-native.png")

        composeRule.onNodeWithTag(PlanTags.EditPlan).performClick()
        waitForTag(PlanTags.Edit)
        capture("plan-edit-native.png")

        androidx.test.espresso.Espresso.pressBack()
        waitForTag(PlanTags.Detail)
        androidx.test.espresso.Espresso.pressBack()
        waitForTag(PlanTags.Screen)
        composeRule.onNodeWithTag(PlanTags.GenerateMonthlyDraft).performScrollTo().performClick()
        waitForTag(PlanTags.DraftScreen)
        capture("plan-draft-native.png")
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

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
