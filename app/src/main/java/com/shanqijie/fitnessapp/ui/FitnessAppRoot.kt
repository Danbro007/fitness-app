package com.shanqijie.fitnessapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shanqijie.fitnessapp.data.FitnessAppState
import com.shanqijie.fitnessapp.data.FitnessRepository
import com.shanqijie.fitnessapp.domain.HomePrimaryAction
import com.shanqijie.fitnessapp.ui.components.FitnessBottomNav
import com.shanqijie.fitnessapp.ui.home.HomeDayUi
import com.shanqijie.fitnessapp.ui.home.HomeScreen
import com.shanqijie.fitnessapp.ui.model.HomeActionUi
import com.shanqijie.fitnessapp.ui.model.HomeUiState
import com.shanqijie.fitnessapp.ui.model.toHomeUiState
import com.shanqijie.fitnessapp.ui.navigation.AppRoute
import com.shanqijie.fitnessapp.ui.navigation.FitnessNavState
import com.shanqijie.fitnessapp.ui.navigation.FitnessTestTags
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab
import com.shanqijie.fitnessapp.ui.theme.FitnessColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun FitnessAppRoot(
    repository: FitnessRepository,
    modifier: Modifier = Modifier,
) {
    val appState by repository.appState().collectAsStateWithLifecycle(initialValue = null)
    val state = appState
    if (state == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(FitnessColors.Phone),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = FitnessColors.Green)
        }
        return
    }

    val snapshot = repository.homeSnapshot(state)
    FitnessAppRootContent(
        homeUiState = snapshot.toHomeUiState(),
        weekDays = state.toFourDayStrip(),
        heroAssetPath = state.heroAssetPath(snapshot.action),
        heroTitle = state.homeHeroTitle(snapshot.action),
        venueName = state.venue?.name ?: "本地训练",
        modifier = modifier,
    )
}

@Composable
fun FitnessAppRootContent(
    homeUiState: HomeUiState,
    modifier: Modifier = Modifier,
    weekDays: List<HomeDayUi> = defaultFourDayStrip(),
    heroAssetPath: String? = null,
    heroTitle: String = homeUiState.nextWorkout?.name ?: "安排下一次训练",
    venueName: String = "本地训练",
) {
    var navState by rememberSaveable(stateSaver = FitnessNavStateSaver) {
        mutableStateOf(FitnessNavState())
    }
    val navigate: (AppRoute) -> Unit = { route -> navState = navState.navigateTo(route) }
    BackHandler(enabled = navState.route !is AppRoute.Primary) {
        navState = navState.navigateTo(navState.backRoute())
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FitnessColors.Phone,
        bottomBar = {
            if (navState.showBottomNav) {
                FitnessBottomNav(
                    selectedTab = navState.selectedPrimaryTab,
                    onTabSelected = { tab -> navState = navState.selectPrimary(tab) },
                )
            }
        },
    ) { contentPadding ->
        when (val route = navState.route) {
            is AppRoute.Primary -> when (route.tab) {
                PrimaryTab.Home -> HomeScreen(
                    state = homeUiState,
                    weekDays = weekDays,
                    heroAssetPath = heroAssetPath,
                    heroTitle = heroTitle,
                    venueName = venueName,
                    onNavigate = navigate,
                    modifier = Modifier.padding(contentPadding),
                )
                PrimaryTab.Plan -> RoutePlaceholder(
                    title = "计划",
                    subtitle = "查看和编辑本周训练安排",
                    modifier = Modifier.padding(contentPadding),
                )
                PrimaryTab.Training -> RoutePlaceholder(
                    title = "训练准备",
                    subtitle = homeUiState.nextWorkout?.name ?: "先在计划中安排一次训练",
                    modifier = Modifier
                        .padding(contentPadding)
                        .testTag(FitnessTestTags.TrainingPrep),
                )
                PrimaryTab.Food -> RoutePlaceholder(
                    title = "饮食",
                    subtitle = "记录今日营养与餐食",
                    modifier = Modifier.padding(contentPadding),
                )
                PrimaryTab.Profile -> RoutePlaceholder(
                    title = "我的",
                    subtitle = "档案、场地、智能与数据设置",
                    modifier = Modifier.padding(contentPadding),
                )
            }
            is AppRoute.Library -> RoutePlaceholder(
                title = "动作库",
                subtitle = "搜索、筛选并查看本地 GIF 动作",
                modifier = Modifier.padding(contentPadding),
            )
            is AppRoute.ExerciseDetail -> RoutePlaceholder(
                title = "动作详情",
                subtitle = route.exerciseId,
                modifier = Modifier.padding(contentPadding),
            )
            is AppRoute.PlanDetail -> RoutePlaceholder(
                title = "计划详情",
                subtitle = route.planId,
                modifier = Modifier.padding(contentPadding),
            )
            is AppRoute.PlanEdit -> RoutePlaceholder(
                title = "编辑计划",
                subtitle = route.planId,
                modifier = Modifier.padding(contentPadding),
            )
            is AppRoute.TrainingActive -> RoutePlaceholder(
                title = "训练进行中",
                subtitle = "已恢复本地训练进度",
                modifier = Modifier
                    .padding(contentPadding)
                    .testTag(FitnessTestTags.TrainingActive),
            )
            is AppRoute.WorkoutSummary -> RoutePlaceholder(
                title = "训练总结",
                subtitle = "查看本次训练数据",
                modifier = Modifier
                    .padding(contentPadding)
                    .testTag(FitnessTestTags.WorkoutSummary),
            )
            AppRoute.ProfileEdit -> RoutePlaceholder("编辑档案", "更新个人目标与训练偏好", Modifier.padding(contentPadding))
            AppRoute.VenueSettings -> RoutePlaceholder("场地与器械", "管理本地训练条件", Modifier.padding(contentPadding))
            AppRoute.SmartSettings -> RoutePlaceholder("智能设置", "AI 只生成草稿，确认后才保存", Modifier.padding(contentPadding))
            AppRoute.DataBackup -> RoutePlaceholder("数据备份", "导出或恢复本地数据", Modifier.padding(contentPadding))
            AppRoute.About -> RoutePlaceholder("关于", "i fitness 本地优先版", Modifier.padding(contentPadding))
        }
    }
}

@Composable
private fun RoutePlaceholder(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge)
    }
}

private val FitnessNavStateSaver = listSaver<FitnessNavState, String>(
    save = { state -> state.route.toSaveableRoute() },
    restore = { values -> FitnessNavState(values.toAppRoute()) },
)

private fun AppRoute.toSaveableRoute(): List<String> = when (this) {
    is AppRoute.Primary -> listOf("primary", tab.name)
    is AppRoute.Library -> listOf("library", origin.name, planId.orEmpty(), sessionId.orEmpty())
    is AppRoute.ExerciseDetail -> listOf(
        "exercise",
        exerciseId,
        origin.origin.name,
        origin.planId.orEmpty(),
        origin.sessionId.orEmpty(),
    )
    is AppRoute.PlanDetail -> listOf("plan-detail", planId)
    is AppRoute.PlanEdit -> listOf("plan-edit", planId)
    is AppRoute.TrainingActive -> listOf("training-active", sessionId)
    is AppRoute.WorkoutSummary -> listOf("workout-summary", sessionId)
    AppRoute.ProfileEdit -> listOf("profile-edit")
    AppRoute.VenueSettings -> listOf("venue-settings")
    AppRoute.SmartSettings -> listOf("smart-settings")
    AppRoute.DataBackup -> listOf("data-backup")
    AppRoute.About -> listOf("about")
}

private fun List<String>.toAppRoute(): AppRoute = when (firstOrNull()) {
    "primary" -> AppRoute.Primary(PrimaryTab.valueOf(getOrElse(1) { PrimaryTab.Home.name }))
    "library" -> AppRoute.Library(
        origin = PrimaryTab.valueOf(getOrElse(1) { PrimaryTab.Home.name }),
        planId = getOrNull(2).orNullIfBlank(),
        sessionId = getOrNull(3).orNullIfBlank(),
    )
    "exercise" -> AppRoute.ExerciseDetail(
        exerciseId = getOrElse(1) { "" },
        origin = AppRoute.Library(
            origin = PrimaryTab.valueOf(getOrElse(2) { PrimaryTab.Home.name }),
            planId = getOrNull(3).orNullIfBlank(),
            sessionId = getOrNull(4).orNullIfBlank(),
        ),
    )
    "plan-detail" -> AppRoute.PlanDetail(getOrElse(1) { "" })
    "plan-edit" -> AppRoute.PlanEdit(getOrElse(1) { "" })
    "training-active" -> AppRoute.TrainingActive(getOrElse(1) { "" })
    "workout-summary" -> AppRoute.WorkoutSummary(getOrElse(1) { "" })
    "profile-edit" -> AppRoute.ProfileEdit
    "venue-settings" -> AppRoute.VenueSettings
    "smart-settings" -> AppRoute.SmartSettings
    "data-backup" -> AppRoute.DataBackup
    "about" -> AppRoute.About
    else -> AppRoute.Primary(PrimaryTab.Home)
}

private fun String?.orNullIfBlank(): String? = this?.takeIf(String::isNotBlank)

private fun FitnessAppState.heroAssetPath(action: HomePrimaryAction): String? {
    val exerciseId = when (action) {
        is HomePrimaryAction.Resume -> workoutSessions
            .firstOrNull { it.id == action.sessionId }
            ?.currentExerciseId
        is HomePrimaryAction.Result -> workoutSessions
            .firstOrNull { it.id == action.sessionId }
            ?.currentExerciseId
        is HomePrimaryAction.Start -> plannedExerciseViews
            .firstOrNull { it.plannedExercise.plannedWorkoutId == action.planId }
            ?.media
            ?.exerciseId
        HomePrimaryAction.CreatePlan -> null
    }
    return exercises.firstOrNull { it.exerciseId == exerciseId }?.localPath
        ?: plannedExerciseViews.firstOrNull()?.media?.localPath
        ?: exercises.firstOrNull()?.localPath
}

private fun FitnessAppState.homeHeroTitle(action: HomePrimaryAction): String = when (action) {
    is HomePrimaryAction.Start -> plannedWorkouts
        .firstOrNull { it.id == action.planId }
        ?.name
        ?: "开始今日训练"
    is HomePrimaryAction.Resume -> workoutSessions
        .firstOrNull { it.id == action.sessionId }
        ?.plannedWorkoutId
        ?.let { planId -> plannedWorkouts.firstOrNull { it.id == planId }?.name }
        ?: "继续自由训练"
    is HomePrimaryAction.Result -> workoutSessions
        .firstOrNull { it.id == action.sessionId }
        ?.plannedWorkoutId
        ?.let { planId -> plannedWorkouts.firstOrNull { it.id == planId }?.name }
        ?: "本次自由训练"
    HomePrimaryAction.CreatePlan -> "安排下一次训练"
}

private fun FitnessAppState.toFourDayStrip(today: LocalDate = LocalDate.now()): List<HomeDayUi> =
    (0L..3L).map { offset ->
        val date = today.plusDays(offset)
        val plan = plannedWorkouts.firstOrNull { it.scheduledDate == date.toString() }
        val completed = workoutSessions.any { session ->
            session.status == "completed" && session.endedAt?.toLocalDate() == date
        }
        HomeDayUi(
            dayLabel = when (offset) {
                0L -> "今"
                1L -> "明"
                2L -> "后"
                else -> "${date.dayOfMonth}"
            },
            workoutLabel = plan?.name.toWorkoutLabel(),
            completed = completed,
        )
    }

private fun defaultFourDayStrip(today: LocalDate = LocalDate.now()): List<HomeDayUi> =
    (0L..3L).map { offset ->
        val date = today.plusDays(offset)
        HomeDayUi(
            dayLabel = when (offset) {
                0L -> "今"
                1L -> "明"
                2L -> "后"
                else -> "${date.dayOfMonth}"
            },
            workoutLabel = "休",
            completed = false,
        )
    }

private fun String?.toWorkoutLabel(): String = when {
    this == null -> "休"
    contains("胸") -> "胸"
    contains("腿") || contains("下肢") -> "腿"
    contains("背") || contains("拉") -> "背"
    else -> "练"
}

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
