package com.shanqijie.fitnessapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import org.junit.Rule
import org.junit.Test

class FitnessHomeNavigationUiTest {
    @get:Rule
    val composeRule = createComposeRule()

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
    fun homeShowsExactlyOneStateDrivenPrimaryAction() {
        composeRule.setContent {
            FitnessTheme {
                HomeScreen(
                    state = startHome(),
                    weekDays = sampleDays(),
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
