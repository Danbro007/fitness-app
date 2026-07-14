package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.ui.toAppRoute
import com.shanqijie.fitnessapp.ui.toSaveableRoute
import com.shanqijie.fitnessapp.ui.secondaryAppBarText
import com.shanqijie.fitnessapp.ui.navigation.AppRoute
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FitnessRoutePersistenceTest {
    @Test
    fun everyRouteRoundTripsThroughTheSavedStatePayload() {
        val library = AppRoute.Library(PrimaryTab.Plan, planId = "plan-1", sessionId = "session-1")
        val routes = buildList {
            PrimaryTab.entries.forEach { add(AppRoute.Primary(it)) }
            add(library)
            add(AppRoute.Library(PrimaryTab.Home))
            add(AppRoute.ExerciseDetail("exercise-1", library))
            add(AppRoute.PlanDetail("plan-1"))
            add(AppRoute.PlanEdit("plan-1"))
            add(AppRoute.PlanDraft("draft-1"))
            add(AppRoute.TrainingActive("session-1"))
            add(AppRoute.WorkoutSummary("session-1"))
            add(AppRoute.ProfileEdit)
            add(AppRoute.FoodManual)
            add(AppRoute.FoodPhoto)
            add(AppRoute.FoodPhotoDraft("draft-1"))
            add(AppRoute.VenueSettings)
            add(AppRoute.EquipmentFilter)
            add(AppRoute.SmartSettings)
            add(AppRoute.DataBackup)
            add(AppRoute.About)
        }

        routes.forEach { route ->
            assertEquals(route, route.toSaveableRoute().toAppRoute())
        }
    }

    @Test
    fun missingOrUnknownSavedStateFallsBackWithoutInventingOptionalIds() {
        assertEquals(AppRoute.Primary(PrimaryTab.Home), emptyList<String>().toAppRoute())
        assertEquals(AppRoute.Primary(PrimaryTab.Home), listOf("unknown").toAppRoute())

        val library = listOf("library", PrimaryTab.Training.name, "", "").toAppRoute()
        assertEquals(AppRoute.Library(PrimaryTab.Training), library)
        assertNull((library as AppRoute.Library).planId)
        assertNull(library.sessionId)

        assertEquals(AppRoute.PlanDetail(""), listOf("plan-detail").toAppRoute())
        assertEquals(AppRoute.ExerciseDetail("", AppRoute.Library(PrimaryTab.Home)), listOf("exercise").toAppRoute())
    }

    @Test
    fun secondaryAppBarCopyIsDefinedForEveryRouteFamily() {
        val library = AppRoute.Library(PrimaryTab.Home)
        val routes = listOf(
            AppRoute.ProfileEdit,
            AppRoute.FoodManual,
            AppRoute.FoodPhoto,
            AppRoute.FoodPhotoDraft("draft"),
            AppRoute.VenueSettings,
            AppRoute.EquipmentFilter,
            AppRoute.SmartSettings,
            AppRoute.DataBackup,
            AppRoute.About,
            library,
            AppRoute.ExerciseDetail("exercise", library),
            AppRoute.PlanDetail("plan"),
            AppRoute.PlanEdit("plan"),
            AppRoute.PlanDraft("draft"),
            AppRoute.TrainingActive("session"),
            AppRoute.WorkoutSummary("session"),
            AppRoute.Primary(PrimaryTab.Home),
        )

        val labels = routes.map(AppRoute::secondaryAppBarText)
        assertEquals("训练偏好与体测", labels.first().first)
        assertEquals("训练总结", labels[15].first)
        assertEquals("i fitness", labels.last().first)
    }

    @Test
    fun everySavedRouteParserHandlesMissingBlankAndExtraSegments() {
        val routeKinds = listOf(
            "primary",
            "library",
            "exercise",
            "plan-detail",
            "plan-edit",
            "plan-draft",
            "training-active",
            "workout-summary",
            "profile-edit",
            "food-manual",
            "food-photo",
            "food-photo-draft",
            "venue-settings",
            "equipment-filter",
            "smart-settings",
            "data-backup",
            "about",
            "unknown",
        )
        routeKinds.forEach { kind ->
            (0..5).forEach { suppliedSegments ->
                val payload = buildList {
                    add(kind)
                    repeat(suppliedSegments) { index -> add(if (index % 2 == 0) "" else "value-$index") }
                }
                payload.toAppRoute().toSaveableRoute()
            }
        }
        PrimaryTab.entries.forEach { tab ->
            assertEquals(AppRoute.Primary(tab), listOf("primary", tab.name).toAppRoute())
        }
        assertEquals(AppRoute.Primary(PrimaryTab.Home), listOf("primary", "not-a-tab").toAppRoute())
    }
}
