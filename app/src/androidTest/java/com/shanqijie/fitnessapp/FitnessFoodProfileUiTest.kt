package com.shanqijie.fitnessapp

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.shanqijie.fitnessapp.data.AiCredentialStore
import com.shanqijie.fitnessapp.data.AiDraftEntity
import com.shanqijie.fitnessapp.data.AiProviderEntity
import com.shanqijie.fitnessapp.data.BodyMeasurement
import com.shanqijie.fitnessapp.data.EquipmentEntity
import com.shanqijie.fitnessapp.data.FitnessAppState
import com.shanqijie.fitnessapp.data.FitnessDatabase
import com.shanqijie.fitnessapp.data.FoodLogEntity
import com.shanqijie.fitnessapp.data.FitnessRepository
import com.shanqijie.fitnessapp.data.FitnessStore
import com.shanqijie.fitnessapp.ui.FitnessAppRoot
import com.shanqijie.fitnessapp.ui.food.FoodScreen
import com.shanqijie.fitnessapp.ui.food.FoodEstimateConfirmation
import com.shanqijie.fitnessapp.ui.food.FoodManualScreen
import com.shanqijie.fitnessapp.ui.food.FoodPhotoDraftScreen
import com.shanqijie.fitnessapp.ui.food.FoodPhotoScreen
import com.shanqijie.fitnessapp.ui.food.FoodTags
import com.shanqijie.fitnessapp.ui.navigation.FitnessTestTags
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab
import com.shanqijie.fitnessapp.ui.profile.ProfileEditScreen
import com.shanqijie.fitnessapp.ui.settings.EquipmentFilterScreen
import com.shanqijie.fitnessapp.ui.settings.BackupSettingsScreen
import com.shanqijie.fitnessapp.ui.settings.SettingsTags
import com.shanqijie.fitnessapp.ui.settings.SmartSettingsScreen
import com.shanqijie.fitnessapp.ui.settings.VenueSettingsScreen
import com.shanqijie.fitnessapp.ui.theme.FitnessTheme
import com.shanqijie.fitnessapp.ai.AiTestResult
import com.shanqijie.fitnessapp.domain.NutritionSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap

class FitnessFoodProfileUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun venueSettingsSupportsTheTransientMissingVenueState() {
        composeRule.setContent {
            FitnessTheme {
                VenueSettingsScreen(
                    currentVenue = null,
                    venues = emptyList(),
                    equipment = emptyList(),
                    enabledEquipmentIds = emptySet(),
                    onRenameVenue = {},
                    onOpenEquipmentFilter = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTags.VenueScreen).assertIsDisplayed()
        composeRule.onNodeWithText("当前训练场地").assertIsDisplayed()
    }

    private lateinit var context: Context
    private lateinit var database: FitnessDatabase
    private lateinit var repository: FitnessRepository
    private lateinit var credentialStore: AiCredentialStore
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        credentialStore = AiCredentialStore(context)
        credentialStore.deleteApiKey(FitnessRepository.OPENAI_PROVIDER_ID)
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
        credentialStore.deleteApiKey(FitnessRepository.OPENAI_PROVIDER_ID)
        database.close()
        assertTrue(context.deleteDatabase(databaseName))
    }

    @Test
    fun smartProviderBrowsingDoesNotChangeActiveProviderUntilSave() {
        var selectionCount = 0
        var selectedProvider = ""
        val now = System.currentTimeMillis()
        composeRule.setContent {
            FitnessTheme {
                SmartSettingsScreen(
                    providers = listOf(
                        AiProviderEntity("openai", "OpenAI", "https://api.openai.com/v1", "gpt-5-mini", true, true, now),
                        AiProviderEntity("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.5-flash", false, false, now),
                    ),
                    onSelectProvider = { providerId, _, _ -> selectionCount += 1; selectedProvider = providerId },
                    onSaveApiKey = { _, _ -> },
                    onTestConnection = { AiTestResult(true, "连接成功") },
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithText("Gemini").performClick()
        composeRule.runOnIdle { assertEquals(0, selectionCount) }
        composeRule.onNodeWithTag(SettingsTags.SmartApiKey).performTextReplacement("test-key")
        composeRule.onNodeWithTag(SettingsTags.SaveSmartKey).performScrollTo().performClick()
        composeRule.waitUntil(5_000) { selectionCount == 1 }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("已连接").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("gemini", selectedProvider)
    }

    @Test
    fun smartSettingsShowsProviderPersistenceFailureAndReEnablesAction() {
        val now = System.currentTimeMillis()
        composeRule.setContent {
            FitnessTheme {
                SmartSettingsScreen(
                    providers = listOf(
                        AiProviderEntity("openai", "OpenAI", "https://api.openai.com/v1", "gpt-5-mini", true, false, now),
                    ),
                    onSelectProvider = { _, _, _ -> error("保存服务商失败") },
                    onSaveApiKey = { _, _ -> },
                    onTestConnection = { AiTestResult(true, "连接成功") },
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTags.SmartApiKey).performTextReplacement("test-key")
        composeRule.onNodeWithTag(SettingsTags.SaveSmartKey).performScrollTo().performClick()
        composeRule.onNodeWithText("保存服务商失败").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTags.SaveSmartKey).assertIsEnabled()
    }

    @Test
    fun smartSettingsFallsBackForMissingAndUnknownProviderCatalogEntries() {
        val providers = mutableStateOf(emptyList<AiProviderEntity>())
        val now = System.currentTimeMillis()
        composeRule.setContent {
            FitnessTheme {
                SmartSettingsScreen(
                    providers = providers.value,
                    onSelectProvider = { _, _, _ -> },
                    onSaveApiKey = { _, _ -> },
                    onTestConnection = { AiTestResult(true, "连接成功") },
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithText("尚未填写").assertIsDisplayed()
        composeRule.runOnIdle {
            providers.value = listOf(
                AiProviderEntity("unknown", "未知服务", "https://example.invalid/v1", "custom-model", true, false, now),
            )
        }
        composeRule.onNodeWithText("尚未填写").assertIsDisplayed()
    }

    @Test
    fun backupScreenShowsExportAndResetFailuresWithoutLeavingThePage() {
        composeRule.setContent {
            FitnessTheme {
                BackupSettingsScreen(
                    onExportBackup = { error("生成备份失败：数据库繁忙") },
                    onImportBackup = {},
                    onResetLocalData = { error("重置本地数据失败：数据库繁忙") },
                    onResetComplete = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithText("导出本机备份").performScrollTo().performClick()
        composeRule.onNodeWithText("生成备份失败：数据库繁忙").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag(RequestResetTag).performScrollTo().performClick()
        composeRule.onNodeWithTag(ConfirmResetTag).performClick()
        composeRule.onNodeWithText("重置本地数据失败：数据库繁忙").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(RequestResetTag).assertIsEnabled()
    }

    @Test
    fun backupResetDialogCannotDismissWhileTheResetTransactionIsRunning() {
        val releaseReset = CompletableDeferred<Unit>()
        var resetStarted = false
        composeRule.setContent {
            FitnessTheme {
                BackupSettingsScreen(
                    onExportBackup = { "{}" },
                    onImportBackup = {},
                    onResetLocalData = {
                        resetStarted = true
                        releaseReset.await()
                    },
                    onResetComplete = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithTag(RequestResetTag).performScrollTo().performClick()
        composeRule.onNodeWithTag(ConfirmResetTag).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { resetStarted }
        pressBack()
        composeRule.onNodeWithTag(SettingsTags.ResetDialog).assertIsDisplayed()

        composeRule.runOnIdle { releaseReset.complete(Unit) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(SettingsTags.ResetDialog).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun equipmentFilterSearchesByTypeAndSavesHiddenSelections() {
        val now = System.currentTimeMillis()
        val equipment = listOf(
            EquipmentEntity("machine", "胸推机", "machine", now, now),
            EquipmentEntity("dumbbell", "哑铃", "free-weight", now, now),
            EquipmentEntity("band", "弹力带", "accessory", now, now),
            EquipmentEntity("bike", "固定单车", "cardio", now, now),
            EquipmentEntity("elliptical", "椭圆机", "cardio", now, now),
            EquipmentEntity("body", "自重训练", "body-weight", now, now),
            EquipmentEntity("custom", "我的雪橇", "custom", now, now),
            EquipmentEntity("hidden", "隐藏器械", "machine", now, now),
        )
        var savedIds: Set<String>? = null
        composeRule.setContent {
            FitnessTheme {
                EquipmentFilterScreen(
                    equipment = equipment,
                    enabledEquipmentIds = setOf("hidden"),
                    onSave = { savedIds = it },
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithContentDescription("器械分类：全部")
            .assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("隐藏器械")
            .assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onAllNodesWithText("有氧器械")[0].assertIsDisplayed().performClick()
        composeRule.onNodeWithText("固定单车").assertIsDisplayed()
        composeRule.onAllNodesWithText("胸推机").assertCountEquals(0)
        composeRule.onNodeWithText("全部").performClick()
        val equipmentSearch = hasSetTextAction() and hasAnyAncestor(hasTestTag(SettingsTags.EquipmentSearch))
        composeRule.onNode(equipmentSearch, useUnmergedTree = true).performTextReplacement("椭圆仪")
        composeRule.onNodeWithText("椭圆机").assertIsDisplayed()
        composeRule.onNode(equipmentSearch, useUnmergedTree = true).performTextReplacement("")
        composeRule.onNodeWithText("全选当前").performClick()
        composeRule.onNodeWithText("清空当前").performClick()
        composeRule.onNodeWithText("隐藏器械").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("隐藏器械").performClick()
        composeRule.onNodeWithText("保存器械筛选").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { savedIds != null }
        assertTrue("hidden" in savedIds.orEmpty())
    }

    @Test
    fun foodOverviewShowsOnlyConfirmedLogsFromToday() {
        val today = java.time.LocalDate.now()
        fun log(id: String, date: String, confirmed: Boolean) = FoodLogEntity(
            id = id,
            loggedDate = date,
            name = id,
            calories = 100,
            proteinGrams = 10.0,
            carbsGrams = 10.0,
            fatGrams = 2.0,
            source = "manual",
            imageNote = "",
            confirmed = confirmed,
            createdAt = id.length.toLong(),
        )
        composeRule.setContent {
            FitnessTheme {
                FoodScreen(
                    summary = NutritionSummary(100, 10.0, 10.0, 2.0),
                    foodLogs = listOf(
                        log("未确认今日", today.toString(), false),
                        log("已确认昨日", today.minusDays(1).toString(), true),
                        log("已确认今日", today.toString(), true),
                    ),
                    activeDraft = null,
                    onOpenManual = {},
                    onOpenPhoto = {},
                    onOpenDraft = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithText("1 餐").assertIsDisplayed()
        composeRule.onNodeWithText("已确认今日").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("未确认今日").assertCountEquals(0)
        composeRule.onAllNodesWithText("已确认昨日").assertCountEquals(0)
    }

    @Test
    fun confirmedFoodCanBeEditedDeletedAndUndoneWithConfirmation() {
        val original = FoodLogEntity(
            id = "editable-food",
            loggedDate = java.time.LocalDate.now().toString(),
            name = "原餐食",
            calories = 500,
            proteinGrams = 30.0,
            carbsGrams = 50.0,
            fatGrams = 15.0,
            source = "manual",
            imageNote = "",
            confirmed = true,
            createdAt = 1L,
        )
        val logs = mutableStateOf(listOf(original))
        composeRule.setContent {
            FitnessTheme {
                FoodScreen(
                    summary = NutritionSummary(500, 30.0, 50.0, 15.0),
                    foodLogs = logs.value,
                    activeDraft = null,
                    onOpenManual = {},
                    onOpenPhoto = {},
                    onOpenDraft = {},
                    onUpdateFood = { id, confirmation ->
                        logs.value = logs.value.map { log ->
                            if (log.id == id) {
                                log.copy(name = confirmation.name, calories = confirmation.calories)
                            } else {
                                log
                            }
                        }
                    },
                    onDeleteFood = { id -> logs.value = logs.value.filterNot { it.id == id } },
                    onRestoreFood = { restored -> logs.value = logs.value + restored },
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithTag(FoodTags.edit(original.id)).performScrollTo().performClick()
        composeRule.onNodeWithTag(FoodTags.EditName, useUnmergedTree = true).performTextReplacement("修改后餐食")
        composeRule.onNodeWithTag(FoodTags.EditCalories, useUnmergedTree = true).performTextReplacement("620")
        composeRule.onNodeWithTag(FoodTags.SaveEdit).performClick()
        composeRule.waitUntil(5_000) { logs.value.singleOrNull()?.name == "修改后餐食" }

        composeRule.onNodeWithTag(FoodTags.delete(original.id)).performScrollTo().performClick()
        composeRule.onNodeWithTag(FoodTags.ConfirmDelete).performClick()
        composeRule.waitUntil(5_000) { logs.value.isEmpty() }
        composeRule.onNodeWithTag(FoodTags.DeleteUndo).assertIsDisplayed()
        composeRule.onNodeWithText("撤销删除").performClick()
        composeRule.waitUntil(5_000) { logs.value.singleOrNull()?.name == "修改后餐食" }
    }

    @Test
    fun discardingFoodDraftRequiresConfirmation() {
        var discarded = false
        composeRule.setContent {
            FitnessTheme {
                FoodPhotoDraftScreen(
                    draft = AiDraftEntity(
                        id = "discard-draft",
                        type = "food_estimate",
                        title = "饮食估算：午餐",
                        content = "约 500 千卡 · 蛋白质 30g · 碳水 50g · 脂肪 15g",
                        status = "draft",
                        createdAt = 1L,
                        updatedAt = 1L,
                        confirmedAt = null,
                    ),
                    onDiscard = { discarded = true },
                    onConfirm = {},
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithTag(FoodTags.DiscardDraft).performClick()
        assertFalse(discarded)
        composeRule.onNodeWithTag(FoodTags.ConfirmDiscardDraft).performClick()
        assertTrue(discarded)
    }

    @Test
    fun newUserMustFinishLocalSetupBeforeSeeingPrimaryNavigation() {
        composeRule.setContent {
            FitnessTheme { FitnessAppRoot(repository = repository) }
        }
        waitForTag(ProfileEditTag)
        composeRule.onNodeWithText("建立基础档案").assertIsDisplayed()
        composeRule.onNodeWithText("1 / 3 · 只保存在这台设备").assertIsDisplayed()
        composeRule.onAllNodesWithTag(FitnessTestTags.HomePrimaryAction).assertCountEquals(0)

        composeRule.onNodeWithTag(ProfileNameTag).performTextReplacement("山崎")
        composeRule.onNodeWithTag("profile-birth-year").performTextReplacement("1994")
        composeRule.onNodeWithTag("profile-height").performTextReplacement("176")
        composeRule.onNodeWithTag("profile-weight").performTextReplacement("75")
        composeRule.onNodeWithTag("save-profile").performScrollTo().performClick()
        composeRule.onNodeWithText("设置训练偏好").assertIsDisplayed()
        composeRule.onNodeWithTag("save-profile").performScrollTo().performClick()
        composeRule.onNodeWithText("补充身体参数").assertIsDisplayed()
        composeRule.onNodeWithText("可跳过").assertIsDisplayed()
        composeRule.onNodeWithTag("save-profile").performScrollTo().performClick()

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
        composeRule.onNodeWithText("今日热量").assertIsDisplayed()
        composeRule.onNodeWithTag(FoodCaloriesTotalTag).assertTextContains("500")
        composeRule.onNodeWithText("目标 2475 千卡 · 剩余 1975").assertIsDisplayed()
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
        composeRule.onAllNodesWithText("体型：偏胖型").assertCountEquals(0)

        composeRule.onNodeWithTag(ProfilePreferencesRowTag).performScrollTo().performClick()
        waitForTag(ProfileEditTag)
        composeRule.onNodeWithTag(BodyFatPercentageTag).performScrollTo().assertTextContains("24.8")
        composeRule.onNodeWithTag(SkeletalMuscleTag).assertTextContains("32.5")
        composeRule.onNodeWithTag(BasalMetabolismTag).assertTextContains("1613")
    }

    @Test
    fun profileEditorKeepsEnteredValuesAndReEnablesSaveAfterPersistenceFailure() {
        composeRule.setContent {
            FitnessTheme {
                ProfileEditScreen(
                    profile = null,
                    onSave = { _, _, _, _, _, _, _, _, _ -> error("保存档案失败：数据库繁忙") },
                )
            }
        }

        composeRule.onNodeWithTag(ProfileNameTag).performTextReplacement("山崎")
        composeRule.onNodeWithTag("save-profile").performScrollTo().performClick()
        composeRule.onNodeWithText("保存档案失败：数据库繁忙").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("save-profile").assertIsEnabled()
        composeRule.onNodeWithTag(ProfileNameTag).assertTextContains("山崎")
    }

    @Test
    fun profileEditorShowsAndLocksTheSavingStateUntilPersistenceCompletes() {
        val releaseSave = CompletableDeferred<Unit>()
        var saveStarted = false
        composeRule.setContent {
            FitnessTheme {
                ProfileEditScreen(
                    profile = null,
                    onSave = { _, _, _, _, _, _, _, _, _ ->
                        saveStarted = true
                        releaseSave.await()
                    },
                )
            }
        }

        composeRule.onNodeWithTag(ProfileNameTag).performTextReplacement("山崎")
        composeRule.onNodeWithTag("save-profile").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { saveStarted }
        composeRule.onNodeWithText("保存中…").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("save-profile").assertIsNotEnabled()

        composeRule.runOnIdle { releaseSave.complete(Unit) }
        composeRule.onNodeWithTag("save-profile").assertIsEnabled()
    }

    @Test
    fun initialProfileEditorAcceptsEveryExplicitOptionalArgument() {
        composeRule.setContent {
            FitnessTheme {
                ProfileEditScreen(
                    profile = null,
                    onSave = { _, _, _, _, _, _, _, _, _ -> },
                    onSaveAvatar = {},
                    isInitialSetup = true,
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithText("基础资料").assertIsDisplayed()
        composeRule.onNodeWithTag("save-profile").assertIsDisplayed()
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
        waitForTag("photo-food-draft-screen")
        composeRule.onNodeWithTag("photo-draft-name")
            .assertTextContains(draft.title.removePrefix("饮食估算："))

        composeRule.onNodeWithTag(ConfirmPhotoDraftTag).performClick()

        waitUntilState { state -> state.foodLogs.size == 1 }
        val confirmedState = currentState()
        assertEquals("confirmed", confirmedState.aiDrafts.single { it.id == draft.id }.status)
        assertTrue(confirmedState.foodLogs.single().confirmed)
    }

    @Test
    fun manualMealEditorShowsPersistenceFailureAndReEnablesSaving() {
        composeRule.setContent {
            FitnessTheme {
                FoodManualScreen(onSave = { _, _, _, _, _ -> error("本地餐食写入失败") })
            }
        }

        composeRule.onNodeWithTag(SaveManualMealTag).performScrollTo().performClick()
        composeRule.onNodeWithText("本地餐食写入失败").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SaveManualMealTag).assertIsEnabled()
    }

    @Test
    fun foodOverviewShowsThePendingAiDraftAndOpensItForReview() {
        val draft = AiDraftEntity(
            id = "pending-food-draft",
            type = "food_estimate",
            title = "饮食估算：轻食沙拉",
            content = "**约 380 千卡**\n- 蛋白质 24g",
            status = "draft",
            createdAt = 1L,
            updatedAt = 1L,
            confirmedAt = null,
        )
        var openedDraftId: String? = null
        composeRule.setContent {
            FitnessTheme {
                FoodScreen(
                    summary = NutritionSummary(0, 0.0, 0.0, 0.0),
                    foodLogs = emptyList(),
                    activeDraft = draft,
                    onOpenManual = {},
                    onOpenPhoto = {},
                    onOpenDraft = { openedDraftId = it },
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithTag(PhotoDraftTag).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("约 380 千卡", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(ConfirmPhotoDraftTag).performClick()
        composeRule.runOnIdle { assertEquals(draft.id, openedDraftId) }
    }

    @Test
    fun photoEditorValidatesEmptyInputAndShowsGenerationFailure() {
        composeRule.setContent {
            FitnessTheme {
                FoodPhotoScreen(
                    onGenerate = { error("食物识别服务暂时不可用") },
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithTag(PhotoDescriptionTag).performTextReplacement("")
        composeRule.onNodeWithTag(GeneratePhotoDraftTag).performScrollTo().performClick()
        composeRule.onNodeWithText("请选择照片或描述食物").assertIsDisplayed()

        composeRule.onNodeWithTag(PhotoDescriptionTag).performTextReplacement("鸡胸肉米饭")
        composeRule.onNodeWithTag(GeneratePhotoDraftTag).performScrollTo().performClick()
        composeRule.onNodeWithText("食物识别服务暂时不可用").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(GeneratePhotoDraftTag).assertIsEnabled()
    }

    @Test
    fun photoDraftKeepsEditableDraftWhenConfirmationFails() {
        val draft = AiDraftEntity(
            id = "food-draft-error",
            type = "food_estimate",
            title = "饮食估算：鸡胸肉米饭",
            content = "约 520 千卡；蛋白质 42g；碳水 55g；脂肪 14g",
            status = "draft",
            createdAt = 1L,
            updatedAt = 1L,
            metadataJson = "",
            confirmedAt = null,
        )
        composeRule.setContent {
            FitnessTheme {
                FoodPhotoDraftScreen(
                    draft = draft,
                    onDiscard = {},
                    onConfirm = { _ -> error("确认草稿失败：数据库繁忙") },
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithTag(ConfirmPhotoDraftTag).performScrollTo().performClick()
        composeRule.onNodeWithText("确认草稿失败：数据库繁忙").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ConfirmPhotoDraftTag).assertIsEnabled()
        composeRule.onNodeWithTag("photo-draft-name").assertTextContains("鸡胸肉米饭")
    }

    @Test
    fun photoDraftUsesEditableFallbacksForMalformedAiContent() {
        var confirmation: FoodEstimateConfirmation? = null
        composeRule.setContent {
            FitnessTheme {
                FoodPhotoDraftScreen(
                    draft = AiDraftEntity(
                        id = "malformed-ui-draft",
                        type = "food_estimate",
                        title = "饮食估算：未知餐食",
                        content = "模型未返回结构化营养数字",
                        status = "draft",
                        createdAt = 1L,
                        updatedAt = 1L,
                        metadataJson = "",
                        confirmedAt = null,
                    ),
                    onDiscard = {},
                    onConfirm = { confirmation = it },
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithTag("photo-draft-calories").assertTextContains("520")
        composeRule.onNodeWithTag("photo-draft-protein").assertTextContains("42")
        composeRule.onNodeWithTag(ConfirmPhotoDraftTag).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { confirmation != null }
        assertEquals(520, confirmation?.calories)
        assertEquals(42.0, confirmation?.proteinGrams)
    }

    @Test
    fun photoDraftPassesUserEditedValuesToConfirmation() {
        var confirmation: FoodEstimateConfirmation? = null
        composeRule.setContent {
            FitnessTheme {
                FoodPhotoDraftScreen(
                    draft = AiDraftEntity(
                        id = "editable-food-draft",
                        type = "food_estimate",
                        title = "饮食估算：旧名称",
                        content = "约 520 千卡；蛋白质 42g；碳水 55g；脂肪 14g",
                        status = "draft",
                        createdAt = 1L,
                        updatedAt = 1L,
                        metadataJson = "",
                        confirmedAt = null,
                    ),
                    onDiscard = {},
                    onConfirm = { confirmation = it },
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithTag("photo-draft-name").performTextReplacement("自定义晚餐")
        composeRule.onNodeWithTag("photo-draft-calories").performTextReplacement("680")
        composeRule.onNodeWithTag("photo-draft-protein").performTextReplacement("50.5")
        composeRule.onNodeWithTag("photo-draft-carbs").performTextReplacement("72")
        composeRule.onNodeWithTag("photo-draft-fat").performTextReplacement("18")
        composeRule.onNodeWithTag(ConfirmPhotoDraftTag).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) { confirmation != null }
        assertEquals("自定义晚餐", confirmation?.name)
        assertEquals(680, confirmation?.calories)
        assertEquals(50.5, confirmation?.proteinGrams)
        assertEquals(72.0, confirmation?.carbsGrams)
        assertEquals(18.0, confirmation?.fatGrams)
    }

    @Test
    fun foodEntryRoutesAreFullScreenAndProduceDesignEvidence() {
        runBlocking { repository.logFood("燕麦酸奶早餐", 420, 32.0, 40.0, 12.0) }
        showRealRoot()
        openPrimary(PrimaryTab.Food, FoodScreenTag)
        captureRoot("food-native.png")

        composeRule.onNodeWithTag(AddMealTag).performClick()
        waitForTag(ManualModeTag)
        captureSystem("meal-sheet-native.png")
        composeRule.onNodeWithTag(ManualModeTag).performClick()
        waitForTag(ManualEditorTag)
        composeRule.onAllNodesWithTag(FitnessTestTags.BottomNav).assertCountEquals(0)
        captureRoot("food-manual-native.png")
        pressBack()
        waitForTag(FoodScreenTag)

        composeRule.onNodeWithTag(AddMealTag).performClick()
        composeRule.onNodeWithTag(PhotoModeTag).performClick()
        waitForTag("photo-food-editor")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(PhotoModeTag).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodesWithTag(FitnessTestTags.BottomNav).assertCountEquals(0)
        captureRoot("food-photo-native.png")
        composeRule.onNodeWithTag(GeneratePhotoDraftTag).performClick()
        waitForTag("photo-food-draft-screen")
        composeRule.onAllNodesWithTag(FitnessTestTags.BottomNav).assertCountEquals(0)
        captureRoot("food-photo-draft-native.png")
    }

    @Test
    fun profileIsReadOnlyUntilEditAndSmartStatusUsesStoredCredential() {
        runBlocking { repository.importBackupJson(providerFlagBackup(apiKeyStored = true)) }
        assertFalse(
            currentState().aiProviders
                .single { it.id == FitnessRepository.OPENAI_PROVIDER_ID }
                .apiKeyStored,
        )
        showRealRoot()
        openPrimary(PrimaryTab.Profile, ProfileScreenTag)
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        listOf("训练偏好", "场地与器械", "连接 AI 服务", "本地数据备份", "关于").forEach { label ->
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
        composeRule.onNodeWithTag(SmartConnectionStatusTag).assertTextEquals("尚未填写")
        composeRule.onAllNodesWithText("已连接").assertCountEquals(0)
        composeRule.onNodeWithTag(SmartApiKeyTag).performTextReplacement("sk-local-ui-test")
        composeRule.onNodeWithTag(SaveSmartKeyTag).performScrollTo().performClick()

        waitUntilState { state ->
            state.aiProviders.single { it.id == FitnessRepository.OPENAI_PROVIDER_ID }.apiKeyStored
        }
        composeRule.onNodeWithTag(SmartConnectionStatusTag).assertTextEquals("已保存，尚未验证")
        assertEquals("sk-local-ui-test", credentialStore.loadApiKey(FitnessRepository.OPENAI_PROVIDER_ID))
        assertFalse(
            credentialStore.encryptedPayloadForTest(FitnessRepository.OPENAI_PROVIDER_ID)
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
            repository.saveAiApiKey(FitnessRepository.OPENAI_PROVIDER_ID, "sk-reset-ui-test")
        }
        showRealRoot()
        openPrimary(PrimaryTab.Profile, ProfileScreenTag)
        composeRule.onNodeWithTag(ProfileBackupRowTag).performScrollTo().performClick()
        waitForTag(BackupScreenTag)
        composeRule.onNodeWithText("导出本机备份").assertIsDisplayed()
        composeRule.onNodeWithText("从备份恢复").assertIsDisplayed()
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
                    .firstOrNull { it.id == FitnessRepository.OPENAI_PROVIDER_ID }
                    ?.apiKeyStored == false
        }
        waitForTag(ProfileEditTag)
        composeRule.onNodeWithText("建立基础档案").assertIsDisplayed()
        assertNull(credentialStore.loadApiKey(FitnessRepository.OPENAI_PROVIDER_ID))
        assertTrue(currentState().plannedWorkouts.isEmpty())
    }

    @Test
    fun dirtyManualMealUsesTheSameGuardForSystemAndTopBack() {
        showRealRoot()
        openPrimary(PrimaryTab.Food, FoodScreenTag)
        openManualMealEditor()
        composeRule.onNodeWithTag(ManualNameTag).performTextReplacement("未保存餐食")
        closeSoftKeyboard()
        composeRule.waitForIdle()

        pressBack()
        composeRule.onNodeWithTag("dirty-back-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("dirty-back-continue").performClick()
        composeRule.onNodeWithTag(ManualEditorTag).assertIsDisplayed()

        composeRule.onNodeWithTag(FitnessTestTags.Back).performClick()
        composeRule.onNodeWithTag("dirty-back-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("dirty-back-discard").performClick()
        waitForTag(FoodScreenTag)
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
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ManualModeTag).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun assertFoodTotals() {
        composeRule.onNodeWithTag(FoodCaloriesTotalTag).assertTextContains("650", substring = true)
        composeRule.onNodeWithTag(FoodProteinTotalTag).assertTextContains("42.5", substring = true)
        composeRule.onNodeWithTag(FoodCarbsTotalTag).assertTextContains("57", substring = true)
        assertEquals(15.0, repository.nutritionSummary(currentState()).fat, 0.01)
    }

    private fun captureRoot(fileName: String) {
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        val target = File(context.filesDir, fileName)
        FileOutputStream(target).use { output ->
            composeRule.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        assertTrue(target.length() > 0L)
    }

    private fun captureSystem(fileName: String) {
        composeRule.waitForIdle()
        Thread.sleep(600)
        val target = File(context.filesDir, fileName)
        FileOutputStream(target).use { output ->
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        assertTrue(target.length() > 0L)
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
            "id": "${FitnessRepository.OPENAI_PROVIDER_ID}",
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
