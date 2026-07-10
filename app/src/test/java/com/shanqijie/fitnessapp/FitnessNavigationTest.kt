package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.ui.navigation.AppRoute
import com.shanqijie.fitnessapp.ui.navigation.FitnessNavState
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessNavigationTest {
    @Test
    fun primaryTabsAreExactlyHomePlanTrainingFoodProfile() {
        assertEquals(
            listOf("首页", "计划", "训练", "饮食", "我的"),
            PrimaryTab.entries.map { it.title },
        )
    }

    @Test
    fun trainingActiveAndSummaryHideBottomNavigation() {
        PrimaryTab.entries.forEach { tab ->
            assertTrue(FitnessNavState(AppRoute.Primary(tab)).showBottomNav)
        }

        assertFalse(FitnessNavState(AppRoute.TrainingActive("session-1")).showBottomNav)
        assertFalse(FitnessNavState(AppRoute.WorkoutSummary("session-1")).showBottomNav)
        assertFalse(FitnessNavState(AppRoute.ProfileEdit).showBottomNav)
    }

    @Test
    fun secondaryRoutesReturnToTheirRecordedOrigin() {
        val library = AppRoute.Library(
            origin = PrimaryTab.Training,
            sessionId = "session-1",
        )
        val detail = AppRoute.ExerciseDetail(
            exerciseId = "0748",
            origin = library,
        )

        assertEquals(library, FitnessNavState(detail).backRoute())
        assertEquals(
            AppRoute.Primary(PrimaryTab.Training),
            FitnessNavState(library).backRoute(),
        )
        assertEquals(
            AppRoute.Primary(PrimaryTab.Plan),
            FitnessNavState(AppRoute.PlanEdit("plan-1")).backRoute(),
        )
    }
}
