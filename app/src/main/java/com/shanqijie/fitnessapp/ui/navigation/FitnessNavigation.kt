package com.shanqijie.fitnessapp.ui.navigation

enum class PrimaryTab(val title: String) {
    Home("首页"),
    Plan("计划"),
    Training("训练"),
    Food("饮食"),
    Profile("我的"),
}

sealed interface AppRoute {
    data class Primary(val tab: PrimaryTab) : AppRoute

    data class Library(
        val origin: PrimaryTab,
        val planId: String? = null,
        val sessionId: String? = null,
    ) : AppRoute

    data class ExerciseDetail(
        val exerciseId: String,
        val origin: Library,
    ) : AppRoute

    data class PlanDetail(val planId: String) : AppRoute
    data class PlanEdit(val planId: String) : AppRoute
    data class TrainingActive(val sessionId: String) : AppRoute
    data class WorkoutSummary(val sessionId: String) : AppRoute
    data object ProfileEdit : AppRoute
    data object VenueSettings : AppRoute
    data object SmartSettings : AppRoute
    data object DataBackup : AppRoute
    data object About : AppRoute
}

data class FitnessNavState(
    val route: AppRoute = AppRoute.Primary(PrimaryTab.Home),
) {
    val showBottomNav: Boolean
        get() = route is AppRoute.Primary

    val selectedPrimaryTab: PrimaryTab
        get() = when (val current = route) {
            is AppRoute.Primary -> current.tab
            is AppRoute.Library -> current.origin
            is AppRoute.ExerciseDetail -> current.origin.origin
            is AppRoute.PlanDetail,
            is AppRoute.PlanEdit,
            -> PrimaryTab.Plan
            is AppRoute.TrainingActive -> PrimaryTab.Training
            is AppRoute.WorkoutSummary -> PrimaryTab.Home
            AppRoute.ProfileEdit,
            AppRoute.VenueSettings,
            AppRoute.SmartSettings,
            AppRoute.DataBackup,
            AppRoute.About,
            -> PrimaryTab.Profile
        }

    fun navigateTo(destination: AppRoute): FitnessNavState = copy(route = destination)

    fun selectPrimary(tab: PrimaryTab): FitnessNavState = navigateTo(AppRoute.Primary(tab))

    fun backRoute(): AppRoute = when (val current = route) {
        is AppRoute.Primary -> current
        is AppRoute.Library -> when {
            current.sessionId != null -> AppRoute.TrainingActive(current.sessionId)
            current.planId != null -> AppRoute.PlanEdit(current.planId)
            else -> AppRoute.Primary(current.origin)
        }
        is AppRoute.ExerciseDetail -> current.origin
        is AppRoute.PlanDetail,
        is AppRoute.PlanEdit,
        -> AppRoute.Primary(PrimaryTab.Plan)
        is AppRoute.TrainingActive -> AppRoute.Primary(PrimaryTab.Training)
        is AppRoute.WorkoutSummary -> AppRoute.Primary(PrimaryTab.Home)
        AppRoute.ProfileEdit,
        AppRoute.VenueSettings,
        AppRoute.SmartSettings,
        AppRoute.DataBackup,
        AppRoute.About,
        -> AppRoute.Primary(PrimaryTab.Profile)
    }
}

object FitnessTestTags {
    const val BottomNav = "bottom-nav"
    const val HomePrimaryAction = "home-primary-action"
    const val WeeklyProgress = "weekly-progress"
    const val OpenFood = "open-food"
    const val OpenLibrary = "open-library"
    const val TrainingPrep = "training-prep"
    const val StartWorkout = "start-workout"
    const val TrainingActive = "training-active"
    const val CompleteSet = "complete-set"
    const val RestPanel = "rest-panel"
    const val SkipRest = "skip-rest"
    const val RequestFinish = "request-finish"
    const val ConfirmFinish = "confirm-finish"
    const val WorkoutSummary = "workout-summary"
    const val SummaryDone = "summary-done"
    const val Back = "secondary-back"

    fun primaryTab(tab: PrimaryTab): String = "primary-tab-${tab.name.lowercase()}"
}
