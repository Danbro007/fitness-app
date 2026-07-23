package com.shanqijie.fitnessapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.shanqijie.fitnessapp.data.AdaptiveDraftContent
import com.shanqijie.fitnessapp.data.AdaptiveDraftDayView
import com.shanqijie.fitnessapp.data.AdaptiveDraftExerciseView
import com.shanqijie.fitnessapp.data.PlanDraftExplanation
import com.shanqijie.fitnessapp.data.WeeklyPlanDraftEntity
import com.shanqijie.fitnessapp.ui.plan.AdaptivePlanDraftScreen
import com.shanqijie.fitnessapp.ui.plan.AdaptivePlanOverviewScreen
import com.shanqijie.fitnessapp.ui.plan.AdaptivePlanTags
import com.shanqijie.fitnessapp.ui.theme.FitnessTheme
import org.junit.Rule
import org.junit.Test

class AdaptivePlanUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun overviewShowsWeekRailAndSetupEntryWhenNoCycleExists() {
        composeRule.setContent {
            FitnessTheme {
                AdaptivePlanOverviewScreen(
                    cycle = null,
                    draft = null,
                    onGenerate = {},
                    onOpenDraft = {},
                )
            }
        }

        composeRule.onNodeWithTag(AdaptivePlanTags.WeekRail).assertDoesNotExist()
        composeRule.onNodeWithText("设置训练周期").assertIsDisplayed()
    }

    @Test
    fun draftShowsExplainableTargetsAndAllowsAtomicWeekConfirmation() {
        var confirmed = false
        var adjusted = false
        val draft = WeeklyPlanDraftEntity(
            id = "draft-ui",
            cycleId = "cycle-ui",
            weekIndex = 1,
            weekStartDate = "2026-07-27",
            payloadJson = "{}",
            inputHash = "hash",
            status = "draft",
            explanationsJson = "[]",
            createdAt = 1,
            updatedAt = 1,
            confirmedAt = null,
        )
        val draftContent = AdaptiveDraftContent(
            source = "LOCAL",
            days = listOf(AdaptiveDraftDayView(1, "gym", listOf(AdaptiveDraftExerciseView("bench", "杠铃卧推", 4, 8, 60.0)))),
            explanations = listOf(PlanDraftExplanation("bench", "最近历史不足三次，保持基线")),
        )
        composeRule.setContent {
            FitnessTheme {
                AdaptivePlanDraftScreen(
                    draft = draft,
                    content = draftContent,
                    onConfirm = { confirmed = true },
                    onRefresh = {},
                    onAdjustWeight = { _, _, _ -> adjusted = true; draftContent },
                )
            }
        }

        composeRule.onNodeWithText("杠铃卧推").assertIsDisplayed()
        composeRule.onNodeWithText("4 组 × 8 次").assertIsDisplayed()
        composeRule.onNodeWithText("重量（kg）").assertIsDisplayed()
        composeRule.onNodeWithText("应用本周").assertIsDisplayed()
        composeRule.onNodeWithText("应用本周").performClick()
        composeRule.runOnIdle { check(adjusted) }
        composeRule.onNodeWithText("依据：最近历史不足三次，保持基线").assertIsDisplayed()
        composeRule.onNodeWithTag(AdaptivePlanTags.ConfirmWeek).performClick()
        check(composeRule.onRoot().captureToImage().width > 0)
        composeRule.runOnIdle { check(confirmed) }
    }
}
