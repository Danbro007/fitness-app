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
        assertFalse(FitnessNavState(AppRoute.FoodManual).showBottomNav)
        assertFalse(FitnessNavState(AppRoute.FoodPhoto).showBottomNav)
        assertFalse(FitnessNavState(AppRoute.FoodPhotoDraft("draft-1")).showBottomNav)
    }

    @Test
    fun secondaryRoutesReturnToTheirRecordedOrigin() {
        val sessionLibrary = AppRoute.Library(
            origin = PrimaryTab.Training,
            sessionId = "session-1",
        )
        val detail = AppRoute.ExerciseDetail(
            exerciseId = "0748",
            origin = sessionLibrary,
        )
        val planLibrary = AppRoute.Library(
            origin = PrimaryTab.Plan,
            planId = "plan-1",
        )

        assertEquals(sessionLibrary, FitnessNavState(detail).backRoute())
        assertEquals(
            AppRoute.TrainingActive("session-1"),
            FitnessNavState(sessionLibrary).backRoute(),
        )
        assertEquals(
            AppRoute.PlanEdit("plan-1"),
            FitnessNavState(planLibrary).backRoute(),
        )
        assertEquals(
            AppRoute.PlanDetail("plan-1"),
            FitnessNavState(AppRoute.PlanEdit("plan-1")).backRoute(),
        )
        assertEquals(
            AppRoute.Primary(PrimaryTab.Plan),
            FitnessNavState(AppRoute.PlanDraft("draft-1")).backRoute(),
        )
        assertEquals(
            AppRoute.Primary(PrimaryTab.Food),
            FitnessNavState(AppRoute.FoodManual).backRoute(),
        )
        assertEquals(
            AppRoute.Primary(PrimaryTab.Food),
            FitnessNavState(AppRoute.FoodPhoto).backRoute(),
        )
        assertEquals(
            AppRoute.FoodPhoto,
            FitnessNavState(AppRoute.FoodPhotoDraft("draft-1")).backRoute(),
        )
        assertEquals(
            AppRoute.VenueSettings,
            FitnessNavState(AppRoute.EquipmentFilter).backRoute(),
        )
        assertEquals(
            PrimaryTab.Profile,
            FitnessNavState(AppRoute.EquipmentFilter).selectedPrimaryTab,
        )
    }

    @Test
    fun everySecondaryRouteSelectsItsOwningTabAndHasADeterministicBackTarget() {
        assertEquals(AppRoute.Primary(PrimaryTab.Home), FitnessNavState().route)
        assertEquals(
            AppRoute.Primary(PrimaryTab.Home),
            FitnessNavState().selectPrimary(PrimaryTab.Home).backRoute(),
        )
        val cases = listOf(
            AppRoute.Library(PrimaryTab.Home) to PrimaryTab.Home,
            AppRoute.ExerciseDetail("exercise", AppRoute.Library(PrimaryTab.Training)) to PrimaryTab.Training,
            AppRoute.PlanDetail("plan") to PrimaryTab.Plan,
            AppRoute.PlanEdit("plan") to PrimaryTab.Plan,
            AppRoute.PlanDraft("draft") to PrimaryTab.Plan,
            AppRoute.TrainingActive("session") to PrimaryTab.Training,
            AppRoute.WorkoutSummary("session") to PrimaryTab.Home,
            AppRoute.FoodManual to PrimaryTab.Food,
            AppRoute.FoodPhoto to PrimaryTab.Food,
            AppRoute.FoodPhotoDraft("draft") to PrimaryTab.Food,
            AppRoute.ProfileEdit to PrimaryTab.Profile,
            AppRoute.VenueSettings to PrimaryTab.Profile,
            AppRoute.EquipmentFilter to PrimaryTab.Profile,
            AppRoute.SmartSettings to PrimaryTab.Profile,
            AppRoute.DataBackup to PrimaryTab.Profile,
            AppRoute.About to PrimaryTab.Profile,
        )
        cases.forEach { (route, tab) ->
            assertEquals(tab, FitnessNavState().navigateTo(route).selectedPrimaryTab)
        }

        assertEquals(AppRoute.Primary(PrimaryTab.Home), FitnessNavState(AppRoute.Library(PrimaryTab.Home)).backRoute())
        assertEquals(AppRoute.Primary(PrimaryTab.Plan), FitnessNavState(AppRoute.PlanDetail("plan")).backRoute())
        assertEquals(AppRoute.Primary(PrimaryTab.Training), FitnessNavState(AppRoute.TrainingActive("session")).backRoute())
        assertEquals(AppRoute.Primary(PrimaryTab.Home), FitnessNavState(AppRoute.WorkoutSummary("session")).backRoute())
        listOf(AppRoute.ProfileEdit, AppRoute.VenueSettings, AppRoute.SmartSettings, AppRoute.DataBackup, AppRoute.About)
            .forEach { route ->
                assertEquals(AppRoute.Primary(PrimaryTab.Profile), FitnessNavState(route).backRoute())
            }
    }
}
