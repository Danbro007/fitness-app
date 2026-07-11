package com.shanqijie.fitnessapp

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import com.shanqijie.fitnessapp.data.AiCredentialStore
import com.shanqijie.fitnessapp.data.BodyMeasurement
import com.shanqijie.fitnessapp.data.FitnessAppState
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FitnessFoodProfileUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var database: FitnessDatabase
    private lateinit var repository: FitnessRepository
    private lateinit var credentialStore: AiCredentialStore
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        credentialStore = AiCredentialStore(context)
        credentialStore.deleteApiKey(FitnessRepository.DEEPSEEK_PROVIDER_ID)
        databaseName = "fitness-food-profile-ui-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        database = FitnessDatabase(context, databaseName)
        repository = FitnessRepository(context, FitnessStore(database))
        runBlocking { repository.bootstrap() }
    }

    @After
    fun tearDown() {
        composeRule.runOnUiThread { composeRule.activity.setContent {} }
        composeRule.waitForIdle()
        credentialStore.deleteApiKey(FitnessRepository.DEEPSEEK_PROVIDER_ID)
        database.close()
        assertTrue(context.deleteDatabase(databaseName))
    }

    @Test
    fun newUserMustFinishLocalSetupBeforeSeeingPrimaryNavigation() {
        composeRule.setContent {
            FitnessTheme { FitnessAppRoot(repository = repository) }
        }
        waitForTag(ProfileEditTag)
        composeRule.onNodeWithText("先完成训练设置").assertIsDisplayed()
        composeRule.onAllNodesWithTag(FitnessTestTags.HomePrimaryAction).assertCountEquals(0)

        runBlocking {
            repository.saveUserProfile(
                displayName = "山崎",
                birthYear = 1994,
                heightCm = 176.0,
                weightKg = 75.0,
                goal = "增肌减脂",
                injuries = "",
                weeklyTrainingDays = 3,
                preferredMinutes = 45,
            )
            repository.setOnboardingCompleted(true)
        }

        waitForTag(FitnessTestTags.HomePrimaryAction)
        assertTrue(currentState().onboardingCompleted)
        assertEquals("山崎", currentState().userProfile?.displayName)
        composeRule.onNodeWithTag(FitnessTestTags.HomePrimaryAction).assertTextContains("创建本周计划")
    }

    @Test
    fun manualMealValidationPreservesValuesAndTotalsSurviveRecreation() {
        showRealRoot()
        openPrimary(PrimaryTab.Food, FoodScreenTag)
        composeRule.onAllNodesWithText("添加一餐").assertCountEquals(1)

        openManualMealEditor()
        composeRule.onNodeWithTag(ManualNameTag).performTextReplacement("鸡胸饭")
        composeRule.onNodeWithTag(ManualCaloriesTag).performTextReplacement("abc")
        composeRule.onNodeWithTag(ManualProteinTag).performTextReplacement("32.5")
        composeRule.onNodeWithTag(ManualCarbsTag).performTextReplacement("45")
        composeRule.onNodeWithTag(ManualFatTag).performTextReplacement("12")
        composeRule.onNodeWithTag(SaveManualMealTag).performScrollTo().performClick()

        composeRule.onNodeWithText("请输入 0 到 5000 之间的热量").assertIsDisplayed()
        composeRule.onNodeWithTag(ManualNameTag).assertTextContains("鸡胸饭")
        composeRule.onNodeWithTag(ManualCaloriesTag).assertTextContains("abc")
        composeRule.onNodeWithTag(ManualProteinTag).assertTextContains("32.5")
        assertTrue(currentState().foodLogs.isEmpty())

        composeRule.onNodeWithTag(ManualCaloriesTag).performTextReplacement("520")
        composeRule.onNodeWithTag(SaveManualMealTag).performScrollTo().performClick()
        waitUntilState { state -> state.foodLogs.any { it.name == "鸡胸饭" } }

        openManualMealEditor()
        composeRule.onNodeWithTag(ManualNameTag).performTextReplacement("酸奶")
        composeRule.onNodeWithTag(ManualCaloriesTag).performTextReplacement("130")
        composeRule.onNodeWithTag(ManualProteinTag).performTextReplacement("10")
        composeRule.onNodeWithTag(ManualCarbsTag).performTextReplacement("12")
        composeRule.onNodeWithTag(ManualFatTag).performTextReplacement("3")
        composeRule.onNodeWithTag(SaveManualMealTag).performScrollTo().performClick()
        waitUntilState { state -> state.foodLogs.size == 2 }

        assertFoodTotals()
        val state = currentState()
        val yogurt = state.foodLogs.single { it.name == "酸奶" }
        val chicken = state.foodLogs.single { it.name == "鸡胸饭" }
        val yogurtTop = composeRule.onNodeWithTag("food-log-${yogurt.id}")
            .fetchSemanticsNode().boundsInRoot.top
        val chickenTop = composeRule.onNodeWithTag("food-log-${chicken.id}")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(yogurtTop < chickenTop)

        recreateRealRoot()
        waitForTag(FitnessTestTags.HomePrimaryAction)
        openPrimary(PrimaryTab.Food, FoodScreenTag)
        assertFoodTotals()
        composeRule.onNodeWithText("酸奶").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("鸡胸饭").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun foodShowsProfileBasedReferenceAndRemainingAmounts() {
        runBlocking {
            repository.saveUserProfile(
                displayName = "山崎",
                birthYear = 1994,
                heightCm = 176.0,
                weightKg = 75.0,
                goal = "增肌",
                injuries = "",
                weeklyTrainingDays = 3,
                preferredMinutes = 45,
            )
            repository.logFood("早餐", 500, 30.0, 60.0, 15.0)
            repository.setOnboardingCompleted(true)
        }
        composeRule.setContent {
            FitnessTheme { FitnessAppRoot(repository = repository) }
        }
        waitForTag(FitnessTestTags.HomePrimaryAction)
        openPrimary(PrimaryTab.Food, FoodScreenTag)

        composeRule.onNodeWithTag(NutritionReferenceTag).assertIsDisplayed()
        composeRule.onNodeWithText("今日参考摄入").assertIsDisplayed()
        composeRule.onNodeWithText("热量 500 / 2475 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("还可参考 1975 kcal").assertIsDisplayed()
    }

    @Test
    fun profileShowsSavedBodyMeasurementAndLetsTheUserUpdateIt() {
        runBlocking {
            repository.saveUserProfile(
                displayName = "山崎",
                birthYear = 1987,
                heightCm = 173.0,
                weightKg = 76.5,
                goal = "减脂",
                injuries = "",
                weeklyTrainingDays = 3,
                preferredMinutes = 45,
                bodyMeasurement = BodyMeasurement(
                    measuredAt = "2026-06-14",
                    bodyType = "偏胖型",
                    bodyFatPercentage = 24.8,
                    skeletalMuscleKg = 32.5,
                    basalMetabolismKcal = 1613,
                ),
            )
        }
        showRealRoot()
        openPrimary(PrimaryTab.Profile, ProfileScreenTag)
        composeRule.onNodeWithTag(BodyMeasurementSummaryTag).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("体型：偏胖型").assertIsDisplayed()

        composeRule.onNodeWithTag(ProfilePreferencesRowTag).performScrollTo().performClick()
        waitForTag(ProfileEditTag)
        composeRule.onNodeWithTag(BodyFatPercentageTag).performScrollTo().assertTextContains("24.8")
        composeRule.onNodeWithTag(SkeletalMuscleTag).assertTextContains("32.5")
        composeRule.onNodeWithTag(BasalMetabolismTag).assertTextContains("1613")
    }

    @Test
    fun photoEstimateCreatesOnlyDraftUntilExplicitConfirmation() {
        showRealRoot()
        openPrimary(PrimaryTab.Food, FoodScreenTag)
        composeRule.onNodeWithTag(AddMealTag).performClick()
        composeRule.onNodeWithTag(PhotoModeTag).performClick()
        composeRule.onNodeWithTag(PhotoDescriptionTag).performTextReplacement("鸡胸肉米饭照片")
        composeRule.onNodeWithTag(GeneratePhotoDraftTag).performClick()

        waitUntilState { state -> state.aiDrafts.any { it.type == "food_estimate" && it.status == "draft" } }
        val draftState = currentState()
        assertTrue(draftState.foodLogs.isEmpty())
        val draft = draftState.aiDrafts.single { it.type == "food_estimate" && it.status == "draft" }
        waitForTag(PhotoDraftTag)
        composeRule.onNodeWithText(draft.title).assertIsDisplayed()

        composeRule.onNodeWithTag(ConfirmPhotoDraftTag).performClick()

        waitUntilState { state -> state.foodLogs.size == 1 }
        val confirmedState = currentState()
        assertEquals("confirmed", confirmedState.aiDrafts.single { it.id == draft.id }.status)
        assertTrue(confirmedState.foodLogs.single().confirmed)
    }

    @Test
    fun profileIsReadOnlyUntilEditAndSmartStatusUsesStoredCredential() {
        runBlocking { repository.importBackupJson(providerFlagBackup(apiKeyStored = true)) }
        assertFalse(
            currentState().aiProviders
                .single { it.id == FitnessRepository.DEEPSEEK_PROVIDER_ID }
                .apiKeyStored,
        )
        showRealRoot()
        openPrimary(PrimaryTab.Profile, ProfileScreenTag)
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        listOf("训练偏好", "场地与器械", "智能设置", "数据备份", "关于").forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }

        composeRule.onNodeWithTag(ProfilePreferencesRowTag).performScrollTo().performClick()
        waitForTag(ProfileEditTag)
        composeRule.onNodeWithTag(ProfileNameTag).assertIsDisplayed()
        assertTrue(composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty())
        pressBack()
        waitForTag(ProfileScreenTag)

        composeRule.onNodeWithTag(ProfileSmartRowTag).performScrollTo().performClick()
        waitForTag(SmartScreenTag)
        composeRule.onNodeWithTag(SmartConnectionStatusTag).assertTextContains("未连接")
        composeRule.onAllNodesWithText("已连接").assertCountEquals(0)
        composeRule.onNodeWithTag(SmartApiKeyTag).performTextReplacement("sk-local-ui-test")
        composeRule.onNodeWithTag(SaveSmartKeyTag).performClick()

        waitUntilState { state ->
            state.aiProviders.single { it.id == FitnessRepository.DEEPSEEK_PROVIDER_ID }.apiKeyStored
        }
        composeRule.onNodeWithTag(SmartConnectionStatusTag).assertTextContains("已连接")
        assertEquals("sk-local-ui-test", credentialStore.loadApiKey(FitnessRepository.DEEPSEEK_PROVIDER_ID))
        assertFalse(
            credentialStore.encryptedPayloadForTest(FitnessRepository.DEEPSEEK_PROVIDER_ID)
                .orEmpty()
                .contains("sk-local-ui-test"),
        )
    }

    @Test
    fun backupResetConfirmationClearsPersonalStateAndReturnsFreshHomeAction() {
        runBlocking {
            repository.logFood("重置前餐食", 400, 30.0, 40.0, 12.0)
            repository.saveUserProfile(
                displayName = "山崎",
                birthYear = 1994,
                heightCm = 176.0,
                weightKg = 75.0,
                goal = "增肌",
                injuries = "",
                weeklyTrainingDays = 4,
                preferredMinutes = 50,
            )
            repository.saveAiApiKey(FitnessRepository.DEEPSEEK_PROVIDER_ID, "sk-reset-ui-test")
        }
        showRealRoot()
        openPrimary(PrimaryTab.Profile, ProfileScreenTag)
        composeRule.onNodeWithTag(ProfileBackupRowTag).performScrollTo().performClick()
        waitForTag(BackupScreenTag)
        composeRule.onNodeWithText("导出备份").assertIsDisplayed()
        composeRule.onNodeWithText("从文件恢复").assertIsDisplayed()
        composeRule.onNodeWithTag(RequestResetTag).performScrollTo().performClick()

        composeRule.onNodeWithTag(ResetDialogTag).assertIsDisplayed()
        assertEquals(1, currentState().foodLogs.size)
        assertTrue(currentState().userProfile != null)
        composeRule.onNodeWithTag(ConfirmResetTag).performClick()

        waitUntilState { state ->
                state.foodLogs.isEmpty() &&
                state.userProfile == null &&
                state.workoutSessions.isEmpty() &&
                state.aiProviders
                    .firstOrNull { it.id == FitnessRepository.DEEPSEEK_PROVIDER_ID }
                    ?.apiKeyStored == false
        }
        waitForTag(ProfileEditTag)
        composeRule.onNodeWithText("先完成训练设置").assertIsDisplayed()
        assertNull(credentialStore.loadApiKey(FitnessRepository.DEEPSEEK_PROVIDER_ID))
        assertTrue(currentState().plannedWorkouts.isEmpty())
    }

    private fun showRealRoot() {
        runBlocking { repository.setOnboardingCompleted(true) }
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

    private fun openPrimary(tab: PrimaryTab, destinationTag: String) {
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(tab)).performClick()
        waitForTag(destinationTag)
    }

    private fun openManualMealEditor() {
        composeRule.onNodeWithTag(AddMealTag).performClick()
        composeRule.onNodeWithTag(ManualModeTag).performClick()
        waitForTag(ManualEditorTag)
    }

    private fun assertFoodTotals() {
        composeRule.onNodeWithTag(FoodCaloriesTotalTag).assertTextContains("650")
        composeRule.onNodeWithTag(FoodProteinTotalTag).assertTextContains("42.5")
        composeRule.onNodeWithTag(FoodCarbsTotalTag).assertTextContains("57")
        composeRule.onNodeWithTag(FoodFatTotalTag).assertTextContains("15")
    }

    private fun currentState(): FitnessAppState = runBlocking { repository.appState().first() }

    private fun waitUntilState(predicate: (FitnessAppState) -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 30_000) { predicate(currentState()) }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun providerFlagBackup(apiKeyStored: Boolean): String =
        """
        {
          "version": 2,
          "exportedAt": 1000,
          "userProfile": null,
          "venues": [],
          "equipment": [],
          "plannedWorkouts": [],
          "plannedExercises": [],
          "workoutSessions": [],
          "setLogs": [],
          "foodLogs": [],
          "aiDrafts": [],
          "aiProviders": [{
            "id": "${FitnessRepository.DEEPSEEK_PROVIDER_ID}",
            "displayName": "DeepSeek",
            "baseUrl": "https://api.deepseek.com",
            "model": "deepseek-v4-flash",
            "enabled": true,
            "apiKeyStored": $apiKeyStored,
            "updatedAt": 1000
          }]
        }
        """.trimIndent()

    private companion object {
        const val FoodScreenTag = "food-screen"
        const val AddMealTag = "add-meal"
        const val ManualModeTag = "meal-mode-manual"
        const val PhotoModeTag = "meal-mode-photo"
        const val ManualEditorTag = "manual-meal-editor"
        const val ManualNameTag = "manual-meal-name"
        const val ManualCaloriesTag = "manual-meal-calories"
        const val ManualProteinTag = "manual-meal-protein"
        const val ManualCarbsTag = "manual-meal-carbs"
        const val ManualFatTag = "manual-meal-fat"
        const val SaveManualMealTag = "save-manual-meal"
        const val PhotoDescriptionTag = "photo-description"
        const val GeneratePhotoDraftTag = "generate-photo-draft"
        const val PhotoDraftTag = "photo-food-draft"
        const val ConfirmPhotoDraftTag = "confirm-photo-draft"
        const val FoodCaloriesTotalTag = "food-total-calories"
        const val FoodProteinTotalTag = "food-total-protein"
        const val FoodCarbsTotalTag = "food-total-carbs"
        const val FoodFatTotalTag = "food-total-fat"
        const val NutritionReferenceTag = "nutrition-reference"
        const val ProfileScreenTag = "profile-screen"
        const val ProfilePreferencesRowTag = "profile-row-preferences"
        const val ProfileSmartRowTag = "profile-row-smart"
        const val ProfileBackupRowTag = "profile-row-backup"
        const val ProfileEditTag = "profile-edit"
        const val ProfileNameTag = "profile-name"
        const val BodyMeasurementSummaryTag = "profile-body-measurement-summary"
        const val BodyFatPercentageTag = "profile-body-fat-percentage"
        const val SkeletalMuscleTag = "profile-skeletal-muscle"
        const val BasalMetabolismTag = "profile-basal-metabolism"
        const val SmartScreenTag = "smart-settings"
        const val SmartConnectionStatusTag = "smart-connection-status"
        const val SmartApiKeyTag = "smart-api-key"
        const val SaveSmartKeyTag = "save-smart-key"
        const val BackupScreenTag = "backup-screen"
        const val RequestResetTag = "request-reset"
        const val ResetDialogTag = "reset-dialog"
        const val ConfirmResetTag = "confirm-reset"
    }
}
