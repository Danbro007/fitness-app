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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shanqijie.fitnessapp.data.FitnessAppState
import com.shanqijie.fitnessapp.data.FitnessDatabase
import com.shanqijie.fitnessapp.data.FitnessRepository
import com.shanqijie.fitnessapp.data.FitnessStore
import com.shanqijie.fitnessapp.domain.ExerciseChineseNameTranslator
import com.shanqijie.fitnessapp.domain.HomePrimaryAction
import com.shanqijie.fitnessapp.domain.WorkoutSummary
import com.shanqijie.fitnessapp.ui.components.FitnessBottomNav
import com.shanqijie.fitnessapp.ui.components.FitnessPrimaryButton
import com.shanqijie.fitnessapp.ui.food.FoodScreen
import com.shanqijie.fitnessapp.ui.home.HomeDayUi
import com.shanqijie.fitnessapp.ui.home.HomeScreen
import com.shanqijie.fitnessapp.ui.library.ExerciseDetailScreen
import com.shanqijie.fitnessapp.ui.library.LibraryScreen
import com.shanqijie.fitnessapp.ui.model.HomeUiState
import com.shanqijie.fitnessapp.ui.model.toHomeUiState
import com.shanqijie.fitnessapp.ui.navigation.AppRoute
import com.shanqijie.fitnessapp.ui.navigation.FitnessNavState
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab
import com.shanqijie.fitnessapp.ui.plan.PlanDetailScreen
import com.shanqijie.fitnessapp.ui.plan.PlanEditScreen
import com.shanqijie.fitnessapp.ui.plan.PlanScreen
import com.shanqijie.fitnessapp.ui.profile.ProfileEditScreen
import com.shanqijie.fitnessapp.ui.profile.ProfileScreen
import com.shanqijie.fitnessapp.ui.settings.AboutScreen
import com.shanqijie.fitnessapp.ui.settings.BackupSettingsScreen
import com.shanqijie.fitnessapp.ui.settings.SmartSettingsScreen
import com.shanqijie.fitnessapp.ui.settings.VenueSettingsScreen
import com.shanqijie.fitnessapp.ui.theme.FitnessColors
import com.shanqijie.fitnessapp.ui.training.TrainingActiveScreen
import com.shanqijie.fitnessapp.ui.training.TrainingActiveScreenUi
import com.shanqijie.fitnessapp.ui.training.TrainingExerciseScreenUi
import com.shanqijie.fitnessapp.ui.training.TrainingPreparationScreen
import com.shanqijie.fitnessapp.ui.training.TrainingPreparationScreenUi
import com.shanqijie.fitnessapp.ui.training.WorkoutSummaryScreen
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun FitnessAppRoot(modifier: Modifier = Modifier) {
    val applicationContext = LocalContext.current.applicationContext
    val repository = remember(applicationContext) {
        FitnessRepository(
            context = applicationContext,
            store = FitnessStore(FitnessDatabase.get(applicationContext)),
        )
    }
    var bootstrapComplete by remember(repository) { mutableStateOf(false) }
    var bootstrapError by remember(repository) { mutableStateOf<String?>(null) }

    LaunchedEffect(repository) {
        runCatching { repository.bootstrap() }
            .onSuccess { bootstrapComplete = true }
            .onFailure { error ->
                bootstrapError = error.message ?: error::class.java.simpleName
            }
    }

    when {
        bootstrapError != null -> RoutePlaceholder(
            title = "无法启动 i Fitness",
            subtitle = bootstrapError.orEmpty(),
            modifier = modifier,
        )
        !bootstrapComplete -> Box(
            modifier = modifier
                .fillMaxSize()
                .background(FitnessColors.Phone),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = FitnessColors.Green)
        }
        else -> FitnessAppRoot(repository = repository, modifier = modifier)
    }
}

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
    val initialRoute = state.unfinishedSessions
        .sortedByDescending { it.updatedAt }
        .firstNotNullOfOrNull { session ->
            state.toTrainingActive(session.id)?.let { AppRoute.TrainingActive(session.id) }
        }
        ?: AppRoute.Primary(PrimaryTab.Home)
    FitnessAppRootContent(
        homeUiState = snapshot.toHomeUiState(),
        weekDays = state.toFourDayStrip(),
        heroAssetPath = state.heroAssetPath(snapshot.action),
        heroTitle = state.homeHeroTitle(snapshot.action),
        venueName = state.venue?.name ?: "本地训练",
        repository = repository,
        appState = state,
        initialRoute = initialRoute,
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
    repository: FitnessRepository? = null,
    appState: FitnessAppState? = null,
    initialRoute: AppRoute = AppRoute.Primary(PrimaryTab.Home),
) {
    var navState by rememberSaveable(stateSaver = FitnessNavStateSaver) {
        mutableStateOf(FitnessNavState(initialRoute))
    }
    var completedSummary by remember { mutableStateOf<WorkoutSummary?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val navigate: (AppRoute) -> Unit = { route -> navState = navState.navigateTo(route) }
    val activeRouteState = (navState.route as? AppRoute.TrainingActive)
        ?.let { route -> appState?.toTrainingActive(route.sessionId) }
    val activeRouteRecoverable = activeRouteState != null && repository != null
    BackHandler(
        enabled = navState.route !is AppRoute.Primary &&
            (navState.route !is AppRoute.TrainingActive || !activeRouteRecoverable),
    ) {
        navState = if (navState.route is AppRoute.TrainingActive && !activeRouteRecoverable) {
            navState.selectPrimary(PrimaryTab.Home)
        } else {
            navState.navigateTo(navState.backRoute())
        }
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
                PrimaryTab.Plan -> {
                    val currentState = appState
                    val fitnessRepository = repository
                    if (currentState == null || fitnessRepository == null) {
                        RoutePlaceholder(
                            title = "计划",
                            subtitle = "正在读取本地训练安排…",
                            modifier = Modifier.padding(contentPadding),
                        )
                    } else {
                        PlanScreen(
                            plans = currentState.plannedWorkouts,
                            plannedExerciseViews = currentState.plannedExerciseViews,
                            activeMonthlyDraft = currentState.aiDrafts
                                .firstOrNull { it.type == "weekly_plan" && it.status == "draft" },
                            onOpenPlan = { planId -> navigate(AppRoute.PlanDetail(planId)) },
                            onCreatePlan = { name, date ->
                                fitnessRepository.createWorkoutFromTemplate(
                                    name = name,
                                    scheduledDate = date,
                                    venueId = currentState.venue?.id.orEmpty(),
                                )
                            },
                            onGenerateMonthlyDraft = {
                                fitnessRepository.generateWeeklyPlanDraft()
                            },
                            onConfirmMonthlyDraft = { draftId ->
                                fitnessRepository.confirmFourWeekPlanDraft(draftId)
                            },
                            modifier = Modifier.padding(contentPadding),
                        )
                    }
                }
                PrimaryTab.Training -> TrainingPreparationScreen(
                    state = appState?.toTrainingPreparation(homeUiState.nextWorkout?.id),
                    onStartWorkout = { planId ->
                        repository?.let { fitnessRepository ->
                            coroutineScope.launch {
                                val session = fitnessRepository.startWorkout(planId)
                                navigate(AppRoute.TrainingActive(session.id))
                            }
                        }
                    },
                    modifier = Modifier.padding(contentPadding),
                )
                PrimaryTab.Food -> {
                    val currentState = appState
                    val fitnessRepository = repository
                    if (currentState == null || fitnessRepository == null) {
                        RoutePlaceholder(
                            title = "饮食",
                            subtitle = "正在读取本地饮食记录…",
                            modifier = Modifier.padding(contentPadding),
                        )
                    } else {
                        FoodScreen(
                            summary = fitnessRepository.nutritionSummary(currentState),
                            foodLogs = currentState.foodLogs,
                            activeDraft = currentState.aiDrafts
                                .firstOrNull { it.type == "food_estimate" && it.status == "draft" },
                            onSaveManualMeal = { name, calories, protein, carbs, fat ->
                                fitnessRepository.logFood(
                                    name = name,
                                    calories = calories,
                                    proteinGrams = protein,
                                    carbsGrams = carbs,
                                    fatGrams = fat,
                                )
                            },
                            onGeneratePhotoDraft = { input ->
                                fitnessRepository.generateFoodEstimateDraft(
                                    description = input.description,
                                    imageUri = input.imageUri,
                                    imageMimeType = input.imageMimeType,
                                    imageBase64 = input.imageBase64,
                                )
                            },
                            onConfirmPhotoDraft = { draftId ->
                                fitnessRepository.confirmFoodEstimateDraft(draftId)
                            },
                            modifier = Modifier.padding(contentPadding),
                        )
                    }
                }
                PrimaryTab.Profile -> {
                    val currentState = appState
                    if (currentState == null) {
                        RoutePlaceholder(
                            title = "我的",
                            subtitle = "正在读取本地档案…",
                            modifier = Modifier.padding(contentPadding),
                        )
                    } else {
                        ProfileScreen(
                            profile = currentState.userProfile,
                            completedWorkouts = currentState.workoutSessions.count { it.status == "completed" },
                            completedSets = currentState.workoutSetLogs.count { it.completed },
                            totalVolumeKg = currentState.workoutSetLogs
                                .filter { it.completed }
                                .sumOf { it.actualReps * it.actualWeightKg },
                            providerConnected = currentState.aiProviders
                                .firstOrNull { it.id == FitnessRepository.DEEPSEEK_PROVIDER_ID }
                                ?.apiKeyStored == true,
                            onOpenPreferences = { navigate(AppRoute.ProfileEdit) },
                            onOpenVenue = { navigate(AppRoute.VenueSettings) },
                            onOpenSmart = { navigate(AppRoute.SmartSettings) },
                            onOpenBackup = { navigate(AppRoute.DataBackup) },
                            onOpenAbout = { navigate(AppRoute.About) },
                            modifier = Modifier.padding(contentPadding),
                        )
                    }
                }
            }
            is AppRoute.Library -> {
                val currentState = appState
                if (currentState == null) {
                    RoutePlaceholder(
                        title = "动作库",
                        subtitle = "正在读取本地动作…",
                        modifier = Modifier.padding(contentPadding),
                    )
                } else {
                    LibraryScreen(
                        exercises = currentState.exercises,
                        onOpenExercise = { exerciseId ->
                            navigate(AppRoute.ExerciseDetail(exerciseId = exerciseId, origin = route))
                        },
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
            is AppRoute.ExerciseDetail -> {
                val currentState = appState
                val fitnessRepository = repository
                val exercise = currentState?.exercises?.firstOrNull { it.exerciseId == route.exerciseId }
                if (currentState == null || fitnessRepository == null || exercise == null) {
                    RoutePlaceholder(
                        title = "动作详情",
                        subtitle = "无法读取这个本地动作",
                        modifier = Modifier.padding(contentPadding),
                    )
                } else {
                    val origin = route.origin
                    val targetPlanId = origin.planId
                        ?: homeUiState.nextWorkout?.id?.takeIf { origin.origin == PrimaryTab.Home }
                    val targetLabel = when {
                        origin.sessionId != null -> "将添加到进行中的训练"
                        targetPlanId != null -> "将添加到当前计划"
                        else -> "请先选择一个训练计划"
                    }
                    ExerciseDetailScreen(
                        exercise = exercise,
                        actionContextLabel = targetLabel,
                        actionLabel = if (origin.sessionId != null) "用于本次训练" else "添加到计划",
                        onAddExercise = {
                            when {
                                origin.sessionId != null -> {
                                    fitnessRepository.addExerciseToSession(
                                        sessionId = origin.sessionId,
                                        exerciseId = exercise.exerciseId,
                                    )
                                    navigate(AppRoute.TrainingActive(origin.sessionId))
                                }
                                targetPlanId != null -> {
                                    fitnessRepository.addExerciseToPlan(
                                        planId = targetPlanId,
                                        exerciseId = exercise.exerciseId,
                                    )
                                    navigate(AppRoute.PlanEdit(targetPlanId))
                                }
                                else -> error("动作库缺少可添加的计划或训练")
                            }
                        },
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
            is AppRoute.PlanDetail -> {
                val currentState = appState
                val plan = currentState?.plannedWorkouts?.firstOrNull { it.id == route.planId }
                if (currentState == null || plan == null) {
                    RoutePlaceholder(
                        title = "计划详情",
                        subtitle = "这个本地计划已不存在",
                        modifier = Modifier.padding(contentPadding),
                    )
                } else {
                    PlanDetailScreen(
                        plan = plan,
                        exercises = currentState.plannedExerciseViews
                            .filter { it.plannedExercise.plannedWorkoutId == plan.id },
                        onEdit = { navigate(AppRoute.PlanEdit(plan.id)) },
                        onOpenLibrary = {
                            navigate(AppRoute.Library(origin = PrimaryTab.Plan, planId = plan.id))
                        },
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
            is AppRoute.PlanEdit -> {
                val currentState = appState
                val fitnessRepository = repository
                val plan = currentState?.plannedWorkouts?.firstOrNull { it.id == route.planId }
                if (currentState == null || fitnessRepository == null || plan == null) {
                    RoutePlaceholder(
                        title = "编辑计划",
                        subtitle = "这个本地计划已不存在",
                        modifier = Modifier.padding(contentPadding),
                    )
                } else {
                    PlanEditScreen(
                        plan = plan,
                        exercises = currentState.plannedExerciseViews
                            .filter { it.plannedExercise.plannedWorkoutId == plan.id },
                        onSave = { name, date ->
                            fitnessRepository.updatePlannedWorkoutDetails(plan.id, name, date)
                            navigate(AppRoute.PlanDetail(plan.id))
                        },
                        onOpenLibrary = {
                            navigate(AppRoute.Library(origin = PrimaryTab.Plan, planId = plan.id))
                        },
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
            is AppRoute.TrainingActive -> {
                val activeState = activeRouteState
                if (activeState == null || repository == null) {
                    UnrecoverableTrainingRoute(
                        onReturnHome = { navState = navState.selectPrimary(PrimaryTab.Home) },
                        modifier = Modifier.padding(contentPadding),
                    )
                } else {
                    val completeRest: () -> Unit = {
                        coroutineScope.launch {
                            repository.skipRest(activeState.sessionId)
                            val currentIndex = activeState.exercises.indexOfFirst {
                                it.exerciseId == activeState.currentExerciseId
                            }
                            val current = activeState.currentExercise
                            if (current.completedSets >= current.targetSets) {
                                activeState.exercises
                                    .drop(currentIndex + 1)
                                    .firstOrNull { it.completedSets < it.targetSets }
                                    ?.let { repository.selectWorkoutExercise(activeState.sessionId, it.exerciseId) }
                            }
                        }
                    }
                    TrainingActiveScreen(
                        state = activeState,
                        onSelectExercise = { exerciseId ->
                            coroutineScope.launch {
                                repository.selectWorkoutExercise(activeState.sessionId, exerciseId)
                            }
                        },
                        onRecordSet = { reps, weightKg, feeling ->
                            repository.recordWorkoutSet(
                                sessionId = activeState.sessionId,
                                exerciseId = activeState.currentExerciseId,
                                reps = reps,
                                weightKg = weightKg,
                                feeling = feeling,
                            )
                        },
                        onRestFinished = completeRest,
                        onSkipRest = completeRest,
                        onFinishWorkout = {
                            coroutineScope.launch {
                                completedSummary = repository.finishWorkout(activeState.sessionId)
                                navigate(AppRoute.WorkoutSummary(activeState.sessionId))
                            }
                        },
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
            is AppRoute.WorkoutSummary -> {
                LaunchedEffect(route.sessionId, repository) {
                    if (repository != null && completedSummary?.sessionId != route.sessionId) {
                        completedSummary = repository.workoutSummary(route.sessionId)
                    }
                }
                val summary = completedSummary?.takeIf { it.sessionId == route.sessionId }
                if (summary == null) {
                    RoutePlaceholder(
                        title = "正在整理训练总结",
                        subtitle = "汇总已保存的训练数据…",
                        modifier = Modifier.padding(contentPadding),
                    )
                } else {
                    WorkoutSummaryScreen(
                        summary = summary,
                        weeklyCompleted = homeUiState.completedThisWeek,
                        weeklyTarget = homeUiState.targetThisWeek,
                        onDone = {
                            completedSummary = null
                            navState = navState.selectPrimary(PrimaryTab.Home)
                        },
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
            AppRoute.ProfileEdit -> {
                val currentState = appState
                val fitnessRepository = repository
                if (currentState == null || fitnessRepository == null) {
                    RoutePlaceholder("训练偏好", "正在读取本地档案…", Modifier.padding(contentPadding))
                } else {
                    ProfileEditScreen(
                        profile = currentState.userProfile,
                        onSave = { name, birthYear, height, weight, goal, injuries, weeklyDays, minutes ->
                            fitnessRepository.saveUserProfile(
                                displayName = name,
                                birthYear = birthYear,
                                heightCm = height,
                                weightKg = weight,
                                goal = goal,
                                injuries = injuries,
                                weeklyTrainingDays = weeklyDays,
                                preferredMinutes = minutes,
                            )
                            navState = navState.selectPrimary(PrimaryTab.Profile)
                        },
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
            AppRoute.VenueSettings -> {
                val currentState = appState
                val fitnessRepository = repository
                val currentVenue = currentState?.venue
                if (currentState == null || fitnessRepository == null) {
                    RoutePlaceholder("场地与器械", "正在读取本地训练条件…", Modifier.padding(contentPadding))
                } else {
                    val venueId = currentVenue?.id
                    val explicitEquipment = venueId != null && currentState.venueEquipment.any { it.venueId == venueId }
                    val enabledEquipmentIds = if (explicitEquipment) {
                        currentState.venueEquipment
                            .filter { it.venueId == venueId && it.available }
                            .mapTo(mutableSetOf()) { it.equipmentId }
                    } else {
                        currentState.equipmentForSelectedVenue.mapTo(mutableSetOf()) { it.id }
                    }
                    VenueSettingsScreen(
                        currentVenue = currentVenue,
                        venues = currentState.venues,
                        equipment = currentState.equipment,
                        enabledEquipmentIds = enabledEquipmentIds,
                        onRenameVenue = { name ->
                            fitnessRepository.renameVenue(
                                id = currentVenue?.id ?: error("当前没有可编辑的场地"),
                                name = name,
                            )
                        },
                        onAddVenue = fitnessRepository::addVenue,
                        onSetDefaultVenue = fitnessRepository::setDefaultVenue,
                        onToggleEquipment = { equipmentId, available ->
                            fitnessRepository.bindEquipmentToVenue(
                                venueId = currentVenue?.id ?: error("当前没有可编辑的场地"),
                                equipmentId = equipmentId,
                                available = available,
                            )
                        },
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
            AppRoute.SmartSettings -> {
                val currentState = appState
                val fitnessRepository = repository
                if (currentState == null || fitnessRepository == null) {
                    RoutePlaceholder("智能设置", "正在读取本机密钥状态…", Modifier.padding(contentPadding))
                } else {
                    SmartSettingsScreen(
                        provider = currentState.aiProviders
                            .firstOrNull { it.id == FitnessRepository.DEEPSEEK_PROVIDER_ID },
                        onSaveApiKey = { apiKey ->
                            fitnessRepository.saveAiApiKey(FitnessRepository.DEEPSEEK_PROVIDER_ID, apiKey)
                        },
                        onTestConnection = {
                            fitnessRepository.testAiProvider(FitnessRepository.DEEPSEEK_PROVIDER_ID)
                        },
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
            AppRoute.DataBackup -> {
                val fitnessRepository = repository
                if (fitnessRepository == null) {
                    RoutePlaceholder("数据备份", "正在准备本地数据…", Modifier.padding(contentPadding))
                } else {
                    BackupSettingsScreen(
                        onExportBackup = fitnessRepository::exportBackupJson,
                        onImportBackup = fitnessRepository::importBackupJson,
                        onResetLocalData = fitnessRepository::resetLocalData,
                        onResetComplete = { navState = navState.selectPrimary(PrimaryTab.Home) },
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
            AppRoute.About -> AboutScreen(modifier = Modifier.padding(contentPadding))
        }
    }
}

@Composable
private fun UnrecoverableTrainingRoute(
    onReturnHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("无法恢复这次训练", style = MaterialTheme.typography.headlineLarge)
        Text("这条旧记录没有可恢复的动作快照。", style = MaterialTheme.typography.bodyLarge)
        FitnessPrimaryButton(
            text = "返回首页",
            onClick = onReturnHome,
        )
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

private fun FitnessAppState.toTrainingPreparation(planId: String?): TrainingPreparationScreenUi? {
    val plan = plannedWorkouts.firstOrNull { it.id == planId } ?: return null
    val exercisesForPlan = plannedExerciseViews
        .filter { it.plannedExercise.plannedWorkoutId == plan.id }
        .sortedBy { it.plannedExercise.orderIndex }
        .map { view ->
            TrainingExerciseScreenUi(
                sessionExerciseId = view.plannedExercise.id,
                exerciseId = view.plannedExercise.exerciseId,
                name = ExerciseChineseNameTranslator.translate(view.media.name),
                detail = listOf(view.media.bodyPart, view.media.equipment)
                    .filter(String::isNotBlank)
                    .joinToString(" · ") { ExerciseChineseNameTranslator.translate(it) },
                assetPath = view.media.localPath,
                targetSets = view.plannedExercise.targetSets,
                targetReps = view.plannedExercise.targetReps,
                targetWeightKg = view.plannedExercise.targetWeightKg,
                completedSets = 0,
            )
        }
    if (exercisesForPlan.isEmpty()) return null
    return TrainingPreparationScreenUi(
        planId = plan.id,
        planName = plan.name,
        estimatedMinutes = exercisesForPlan.sumOf { it.targetSets } * 3,
        exercises = exercisesForPlan,
    )
}

private fun FitnessAppState.toTrainingActive(sessionId: String): TrainingActiveScreenUi? {
    val session = workoutSessions.firstOrNull { it.id == sessionId && it.status == "in_progress" } ?: return null
    val sessionExercises = workoutSessionExercises
        .filter { it.sessionId == sessionId }
        .sortedBy { it.orderIndex }
    if (sessionExercises.isEmpty()) return null

    val exerciseUi = sessionExercises.map { sessionExercise ->
        val media = exercises.firstOrNull { it.exerciseId == sessionExercise.exerciseId }
            ?: plannedExerciseViews.firstOrNull { it.media.exerciseId == sessionExercise.exerciseId }?.media
        TrainingExerciseScreenUi(
            sessionExerciseId = sessionExercise.id,
            exerciseId = sessionExercise.exerciseId,
            name = ExerciseChineseNameTranslator.translate(media?.name.orEmpty()),
            detail = listOfNotNull(media?.bodyPart, media?.equipment)
                .filter(String::isNotBlank)
                .joinToString(" · ") { ExerciseChineseNameTranslator.translate(it) },
            assetPath = media?.localPath ?: exercises.firstOrNull()?.localPath.orEmpty(),
            targetSets = sessionExercise.targetSets,
            targetReps = sessionExercise.targetReps,
            targetWeightKg = sessionExercise.targetWeightKg,
            completedSets = workoutSetLogs.count { log ->
                log.sessionId == sessionId &&
                    log.exerciseId == sessionExercise.exerciseId &&
                    log.completed
            },
        )
    }
    val currentExerciseId = session.currentExerciseId
        ?.takeIf { current -> exerciseUi.any { it.exerciseId == current } }
        ?: exerciseUi.first().exerciseId
    val planName = session.plannedWorkoutId
        ?.let { id -> plannedWorkouts.firstOrNull { it.id == id }?.name }
        ?: "自由训练"
    return TrainingActiveScreenUi(
        sessionId = session.id,
        planName = planName,
        currentExerciseId = currentExerciseId,
        restEndsAt = session.restEndsAt,
        exercises = exerciseUi,
    )
}
