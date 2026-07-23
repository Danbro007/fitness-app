package com.shanqijie.fitnessapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.captureToImage
import com.shanqijie.fitnessapp.domain.WorkoutSummary
import com.shanqijie.fitnessapp.ui.theme.FitnessTheme
import com.shanqijie.fitnessapp.ui.training.WorkoutSummaryScreen
import org.junit.Rule
import org.junit.Test

class WorkoutFeedbackUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun injuryGateExplainsConservativePlanAndCanBeResolved() {
        var resolved = false
        composeRule.setContent {
            FitnessTheme {
                WorkoutSummaryScreen(
                    summary = WorkoutSummary("feedback-ui", 2, 4, 120.0, 600, mapOf("合适" to 2)),
                    weeklyCompleted = 1,
                    weeklyTarget = 3,
                    onDone = {},
                    reviewDraft = null,
                    onGenerateReview = { _, _ -> },
                    onResolveReview = { _, _ -> },
                    injuryReviewRequired = true,
                    onResolveInjuryReview = { resolved = true },
                    modifier = androidx.compose.ui.Modifier,
                )
            }
        }

        composeRule.onNodeWithText("需要先完成伤病复核").assertIsDisplayed()
        composeRule.onNodeWithText("我已完成复核，解除门禁").performClick()
        composeRule.onRoot().captureToImage()
        composeRule.runOnIdle { check(resolved) }
    }
}
