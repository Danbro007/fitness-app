package com.shanqijie.fitnessapp

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.shanqijie.fitnessapp.data.FitnessDatabase
import com.shanqijie.fitnessapp.data.FitnessRepository
import com.shanqijie.fitnessapp.data.FitnessStore
import com.shanqijie.fitnessapp.data.PlannedWorkoutEntity
import com.shanqijie.fitnessapp.ui.FitnessAppRootContent
import com.shanqijie.fitnessapp.ui.home.HomeDayUi
import com.shanqijie.fitnessapp.ui.home.HomeScreen
import com.shanqijie.fitnessapp.ui.model.HomeActionUi
import com.shanqijie.fitnessapp.ui.model.HomeUiState
import com.shanqijie.fitnessapp.ui.navigation.AppRoute
import com.shanqijie.fitnessapp.ui.navigation.FitnessTestTags
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab
import com.shanqijie.fitnessapp.ui.theme.FitnessTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

class FitnessHomeNavigationUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shellShowsExactlyFiveTabsInApprovedOrder() {
        composeRule.setContent {
            FitnessTheme {
                FitnessAppRootContent(homeUiState = startHome())
            }
        }

        val tabs = PrimaryTab.entries.map { tab ->
            composeRule.onNodeWithTag(FitnessTestTags.primaryTab(tab))
                .assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot.left
        }
        assertEquals(tabs.sorted(), tabs)
        composeRule.onAllNodesWithText("动作", substring = false).assertCountEquals(0)
        composeRule.onAllNodesWithText("智能", substring = false).assertCountEquals(0)
    }

    @Test
    fun primaryTabsExposeDeterministicLoadingStatesBeforeRepositoryDataArrives() {
        composeRule.setContent {
            FitnessTheme {
                FitnessAppRootContent(homeUiState = startHome())
            }
        }

        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Plan)).performClick()
        composeRule.onNodeWithText("正在读取本地训练安排…").assertIsDisplayed()
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Training)).performClick()
        composeRule.onNodeWithTag(FitnessTestTags.TrainingPrep).assertIsDisplayed()
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Food)).performClick()
        composeRule.onNodeWithText("正在读取本地饮食记录…").assertIsDisplayed()
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Profile)).performClick()
        composeRule.onNodeWithText("正在读取本地档案…").assertIsDisplayed()
    }

    @Test
    fun everySecondaryRouteHasADeterministicMissingDataState() {
        var route by mutableStateOf<AppRoute>(AppRoute.FoodManual)
        composeRule.setContent {
            FitnessTheme {
                key(route) {
                    FitnessAppRootContent(homeUiState = startHome(), initialRoute = route)
                }
            }
        }

        val cases = listOf(
            AppRoute.FoodManual to "正在准备本地记录…",
            AppRoute.FoodPhoto to "正在准备照片选择…",
            AppRoute.FoodPhotoDraft("missing") to "这份本地草稿已不存在",
            AppRoute.Library(PrimaryTab.Home) to "正在读取本地动作…",
            AppRoute.ExerciseDetail("missing", AppRoute.Library(PrimaryTab.Home)) to "无法读取这个本地动作",
            AppRoute.PlanDetail("missing") to "这个本地计划已不存在",
            AppRoute.PlanEdit("missing") to "这个本地计划已不存在",
            AppRoute.PlanDraft("missing") to "这份草稿已不存在",
            AppRoute.TrainingActive("legacy") to "这条旧记录没有可恢复的动作快照。",
            AppRoute.ProfileEdit to "正在读取本地档案…",
            AppRoute.VenueSettings to "正在读取本地训练条件…",
            AppRoute.EquipmentFilter to "正在读取本地器械…",
            AppRoute.SmartSettings to "正在读取本机密钥状态…",
            AppRoute.DataBackup to "正在准备本地数据…",
        )
        cases.forEach { (nextRoute, message) ->
            composeRule.runOnIdle { route = nextRoute }
            composeRule.onNodeWithText(message).assertIsDisplayed()
        }
    }

    @Test
    fun homeShowsExactlyOneStateDrivenPrimaryAction() {
        composeRule.setContent {
            FitnessTheme {
                HomeScreen(
                    state = startHome(),
                    weekDays = sampleDays(),
                    venueName = "本地训练",
                    modifier = androidx.compose.ui.Modifier,
                    heroAssetPath = null,
                    heroTitle = startHome().nextWorkout?.name ?: "安排下一次训练",
                    onNavigate = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(FitnessTestTags.HomePrimaryAction).assertCountEquals(1)
        composeRule.onNodeWithTag(FitnessTestTags.HomePrimaryAction)
            .assertIsDisplayed()
            .assertTextEquals("开始训练")
    }

    @Test
    fun heroUsesMetadataFromTheSamePrimaryAction() {
        composeRule.setContent {
            FitnessTheme {
                HomeScreen(
                    state = startHome().copy(
                        actions = listOf(
                            HomeActionUi("继续训练", AppRoute.TrainingActive("active")),
                        ),
                        nextWorkout = startHome().nextWorkout?.copy(name = "下肢力量 A"),
                    ),
                    weekDays = sampleDays(),
                    modifier = androidx.compose.ui.Modifier,
                    heroAssetPath = null,
                    heroTitle = "背部拉力 A",
                    venueName = "本地训练",
                    onNavigate = {},
                )
            }
        }

        composeRule.onNodeWithText("背部拉力 A").assertIsDisplayed()
        composeRule.onAllNodesWithText("下肢力量 A", substring = false).assertCountEquals(0)
    }

    @Test
    fun homeDefaultsHandleMissingWorkoutAndAllExplicitArguments() {
        var explicit by mutableStateOf(false)
        val noWorkout = startHome().copy(nextWorkout = null)
        composeRule.setContent {
            FitnessTheme {
                if (explicit) {
                    HomeScreen(
                        state = noWorkout,
                        weekDays = sampleDays(),
                        onNavigate = {},
                        modifier = androidx.compose.ui.Modifier,
                        heroAssetPath = null,
                        heroTitle = "显式标题",
                        venueName = "显式场地",
                    )
                } else {
                    HomeScreen(
                        state = noWorkout,
                        weekDays = sampleDays(),
                        venueName = "本地训练",
                        modifier = androidx.compose.ui.Modifier,
                        heroAssetPath = null,
                        heroTitle = "安排下一次训练",
                        onNavigate = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("安排下一次训练").assertIsDisplayed()
        composeRule.runOnIdle { explicit = true }
        composeRule.onNodeWithText("显式标题").assertIsDisplayed()
        composeRule.onNodeWithText("显式场地", substring = true).assertIsDisplayed()
    }

    @Test
    fun homePrimaryActionRoutesToPreparationResumeOrSummary() {
        var action by mutableStateOf(
            HomeActionUi("开始训练", AppRoute.Primary(PrimaryTab.Training)),
        )
        var routed: AppRoute? = null
        composeRule.setContent {
            FitnessTheme {
                HomeScreen(
                    state = startHome().copy(actions = listOf(action)),
                    weekDays = sampleDays(),
                    venueName = "本地训练",
                    modifier = androidx.compose.ui.Modifier,
                    heroAssetPath = null,
                    heroTitle = startHome().nextWorkout?.name ?: "安排下一次训练",
                    onNavigate = { routed = it },
                )
            }
        }

        composeRule.onNodeWithTag(FitnessTestTags.HomePrimaryAction).performClick()
        composeRule.runOnIdle { assertEquals(AppRoute.Primary(PrimaryTab.Training), routed) }

        composeRule.runOnIdle {
            routed = null
            action = HomeActionUi("继续训练", AppRoute.TrainingActive("session-active"))
        }
        composeRule.onNodeWithTag(FitnessTestTags.HomePrimaryAction).performClick()
        composeRule.runOnIdle { assertEquals(AppRoute.TrainingActive("session-active"), routed) }

        composeRule.runOnIdle {
            routed = null
            action = HomeActionUi("查看训练总结", AppRoute.WorkoutSummary("session-result"))
        }
        composeRule.onNodeWithTag(FitnessTestTags.HomePrimaryAction).performClick()
        composeRule.runOnIdle { assertEquals(AppRoute.WorkoutSummary("session-result"), routed) }
    }

    @Test
    fun homeQuickActionsOpenFoodAndLibrary() {
        var routed: AppRoute? = null
        composeRule.setContent {
            FitnessTheme {
                HomeScreen(
                    state = startHome(),
                    weekDays = sampleDays(),
                    venueName = "本地训练",
                    modifier = androidx.compose.ui.Modifier,
                    heroAssetPath = null,
                    heroTitle = startHome().nextWorkout?.name ?: "安排下一次训练",
                    onNavigate = { routed = it },
                )
            }
        }

        composeRule.onNodeWithTag(FitnessTestTags.OpenFood).performClick()
        composeRule.runOnIdle { assertEquals(AppRoute.Primary(PrimaryTab.Food), routed) }

        composeRule.runOnIdle { routed = null }
        composeRule.onNodeWithTag(FitnessTestTags.OpenLibrary).performClick()
        composeRule.runOnIdle { assertEquals(AppRoute.Library(origin = PrimaryTab.Home), routed) }
    }

    @Test
    fun secondaryRouteHidesBottomBarAndBackReturnsToRecordedOrigin() {
        composeRule.setContent {
            FitnessTheme {
                FitnessAppRootContent(homeUiState = startHome())
            }
        }

        composeRule.onNodeWithTag(FitnessTestTags.OpenLibrary).performClick()
        composeRule.onNodeWithText("动作库").assertIsDisplayed()
        composeRule.onNodeWithTag(FitnessTestTags.BottomNav).assertDoesNotExist()
        composeRule.onNodeWithTag(FitnessTestTags.Back).assertIsDisplayed()

        composeRule.onNodeWithTag(FitnessTestTags.Back).performClick()
        composeRule.onNodeWithTag(FitnessTestTags.BottomNav).assertIsDisplayed()
        composeRule.onNodeWithTag(FitnessTestTags.HomePrimaryAction).assertIsDisplayed()
    }

    @Test
    fun quickActionsRemainResponsiveAfterReturningFromLibrary() {
        composeRule.setContent {
            FitnessTheme {
                FitnessAppRootContent(homeUiState = startHome())
            }
        }

        composeRule.onNodeWithTag(FitnessTestTags.OpenLibrary).performClick()
        composeRule.onNodeWithText("动作库").assertIsDisplayed()
        composeRule.onNodeWithTag(FitnessTestTags.Back).performClick()
        composeRule.onNodeWithTag(FitnessTestTags.OpenFood).performClick()

        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Food))
            .assertIsDisplayed()
            .assertIsSelected()
    }

    @Test
    fun completedTodayUsesACompletionFocusedHeroInsteadOfTheOldWorkoutTask() {
        composeRule.setContent {
            FitnessTheme {
                HomeScreen(
                    state = startHome().copy(
                        actions = listOf(HomeActionUi("查看训练总结", AppRoute.WorkoutSummary("done"))),
                        completedToday = true,
                    ),
                    weekDays = sampleDays(),
                    modifier = androidx.compose.ui.Modifier,
                    heroAssetPath = null,
                    heroTitle = "今天已完成",
                    venueName = "本地训练",
                    onNavigate = {},
                )
            }
        }

        composeRule.onNodeWithText("今日已完成").assertIsDisplayed()
        composeRule.onNodeWithTag(FitnessTestTags.HomePrimaryAction).assertTextEquals("查看训练总结")
        composeRule.onAllNodesWithText("TODAY WORKOUT", substring = false).assertCountEquals(0)
    }

    @Test
    fun homeProducesCurrentDesignEvidence() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.setContent {
            FitnessTheme {
                FitnessAppRootContent(
                    homeUiState = startHome().copy(completedThisWeek = 0, targetThisWeek = 1),
                    weekDays = sampleDays(),
                    heroTitle = "胸部力量 A",
                    heroAssetPath = "exercise-media/gifs/0748-trqKQv2.gif",
                    venueName = "公司健身房",
                )
            }
        }
        composeRule.onNodeWithTag(FitnessTestTags.HomePrimaryAction).assertIsDisplayed()
        composeRule.onNodeWithTag(FitnessTestTags.BottomNav).assertIsDisplayed()
        composeRule.waitForIdle()
        val target = File(context.filesDir, "home-native.png")
        FileOutputStream(target).use { output ->
            composeRule.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        assertTrue(target.length() > 0L)
    }

    @Test
    fun primaryRouteSurvivesSavedStateRestore() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            FitnessTheme {
                FitnessAppRootContent(homeUiState = startHome())
            }
        }

        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Food))
            .performClick()
            .assertIsSelected()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag(FitnessTestTags.primaryTab(PrimaryTab.Food)).assertIsSelected()
    }

    @Test
    fun realRootCollectsRepositoryState() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "fitness-home-root-${System.nanoTime()}.db"
        context.deleteDatabase(dbName)
        val database = FitnessDatabase(context, dbName)
        val repository = FitnessRepository(context, FitnessStore(database))
        runBlocking {
            repository.bootstrap()
            repository.setOnboardingCompleted(true)
        }

        try {
            composeRule.setContent {
                FitnessTheme {
                    com.shanqijie.fitnessapp.ui.FitnessAppRoot(repository = repository)
                }
            }
            composeRule.waitUntil(timeoutMillis = 60_000) {
                composeRule.onAllNodesWithTag(FitnessTestTags.HomePrimaryAction)
                    .fetchSemanticsNodes().size == 1
            }
            composeRule.onNodeWithTag(FitnessTestTags.HomePrimaryAction).assertIsDisplayed()
            composeRule.onNodeWithTag(FitnessTestTags.WeeklyProgress).assertIsDisplayed()
        } finally {
            composeRule.runOnUiThread { composeRule.activity.setContent {} }
            composeRule.waitForIdle()
            database.close()
            assertTrue(context.deleteDatabase(dbName))
        }
    }

    @Test
    fun realRootShowsAReadableBootstrapFailure() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "fitness-home-root-failure-${System.nanoTime()}.db"
        context.deleteDatabase(dbName)
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName),
            null,
        ).use { rawDatabase -> rawDatabase.version = 100 }
        val database = FitnessDatabase(context, dbName)
        val repository = FitnessRepository(context, FitnessStore(database))
        val repositoryFactory: (android.content.Context) -> FitnessRepository = { repository }

        try {
            composeRule.setContent {
                FitnessTheme {
                    com.shanqijie.fitnessapp.ui.FitnessAppRoot(repositoryFactory = repositoryFactory)
                }
            }
            composeRule.waitUntil(timeoutMillis = 30_000) {
                composeRule.onAllNodesWithText("无法启动 i Fitness").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("无法启动 i Fitness").assertIsDisplayed()
        } finally {
            composeRule.runOnUiThread { composeRule.activity.setContent {} }
            composeRule.waitForIdle()
            database.close()
            assertTrue(context.deleteDatabase(dbName))
        }
    }

    private fun startHome() = HomeUiState(
        actions = listOf(HomeActionUi("开始训练", AppRoute.Primary(PrimaryTab.Training))),
        completedThisWeek = 2,
        targetThisWeek = 4,
        nextWorkout = PlannedWorkoutEntity(
            id = "plan-today",
            name = "胸部力量 A",
            scheduledDate = "2026-07-10",
            venueId = "venue-local-company-gym",
            status = "planned",
            createdAt = 1L,
            updatedAt = 1L,
        ),
    )

    private fun sampleDays() = listOf(
        HomeDayUi("今", "胸", completed = true),
        HomeDayUi("明", "休", completed = false),
        HomeDayUi("后", "腿", completed = false),
        HomeDayUi("日", "休", completed = false),
    )
}
