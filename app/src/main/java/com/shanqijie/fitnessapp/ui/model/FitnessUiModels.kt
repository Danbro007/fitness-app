package com.shanqijie.fitnessapp.ui.model

import com.shanqijie.fitnessapp.data.PlannedWorkoutEntity
import com.shanqijie.fitnessapp.domain.HomePrimaryAction
import com.shanqijie.fitnessapp.domain.HomeSnapshot
import com.shanqijie.fitnessapp.ui.navigation.AppRoute
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab

data class HomeActionUi(
    val label: String,
    val route: AppRoute,
)

data class HomeUiState(
    val actions: List<HomeActionUi>,
    val completedThisWeek: Int,
    val targetThisWeek: Int,
    val nextWorkout: PlannedWorkoutEntity?,
    val completedToday: Boolean = false,
) {
    init {
        require(actions.size == 1) { "首页必须且只能展示一个主要训练操作" }
    }

    val primaryAction: HomeActionUi
        get() = actions.single()
}

fun HomeSnapshot.toHomeUiState(): HomeUiState =
    HomeUiState(
        actions = listOf(action.toUiAction()),
        completedThisWeek = completedThisWeek,
        targetThisWeek = targetThisWeek,
        nextWorkout = nextWorkout,
        completedToday = action is HomePrimaryAction.Result,
    )

private fun HomePrimaryAction.toUiAction(): HomeActionUi = when (this) {
    is HomePrimaryAction.Start -> HomeActionUi(
        label = "开始训练",
        route = AppRoute.Primary(PrimaryTab.Training),
    )
    is HomePrimaryAction.Resume -> HomeActionUi(
        label = "继续训练",
        route = AppRoute.TrainingActive(sessionId),
    )
    is HomePrimaryAction.Result -> HomeActionUi(
        label = "查看训练总结",
        route = AppRoute.WorkoutSummary(sessionId),
    )
    HomePrimaryAction.CreatePlan -> HomeActionUi(
        label = "创建本周计划",
        route = AppRoute.Primary(PrimaryTab.Plan),
    )
}

data class TrainingExerciseUi(
    val id: String,
    val exerciseId: String,
    val targetSets: Int,
    val completedSets: Int = 0,
) {
    init {
        require(targetSets > 0) { "目标组数必须大于 0" }
        require(completedSets in 0..targetSets) { "完成组数必须在目标范围内" }
    }
}

sealed interface TrainingPhase {
    data object Preparation : TrainingPhase
    data object Active : TrainingPhase
    data class Resting(val endsAt: Long) : TrainingPhase
    data object Paused : TrainingPhase
    data object Completed : TrainingPhase
}

data class TrainingUiState(
    val sessionId: String,
    val exercises: List<TrainingExerciseUi>,
    val currentExerciseIndex: Int = 0,
    val phase: TrainingPhase = TrainingPhase.Active,
) {
    init {
        require(exercises.isNotEmpty()) { "训练至少需要一个动作" }
        require(currentExerciseIndex in exercises.indices) { "当前动作索引越界" }
    }

    val currentExercise: TrainingExerciseUi
        get() = exercises[currentExerciseIndex]
}

sealed interface TrainingEvent {
    data class SetCompleted(val restEndsAt: Long) : TrainingEvent
    data object RestFinished : TrainingEvent
}

fun TrainingUiState.reduce(event: TrainingEvent): TrainingUiState = when (event) {
    is TrainingEvent.SetCompleted -> completeCurrentSet(event.restEndsAt)
    TrainingEvent.RestFinished -> finishRest()
}

private fun TrainingUiState.completeCurrentSet(restEndsAt: Long): TrainingUiState {
    val current = currentExercise
    if (phase != TrainingPhase.Active || current.completedSets >= current.targetSets) return this

    val updatedExercises = exercises.toMutableList().apply {
        this[currentExerciseIndex] = current.copy(completedSets = current.completedSets + 1)
    }
    return copy(
        exercises = updatedExercises,
        phase = TrainingPhase.Resting(endsAt = restEndsAt),
    )
}

private fun TrainingUiState.finishRest(): TrainingUiState {
    if (phase !is TrainingPhase.Resting) return this
    val current = currentExercise
    if (current.completedSets < current.targetSets) return copy(phase = TrainingPhase.Active)

    val nextIndex = currentExerciseIndex + 1
    return if (nextIndex < exercises.size) {
        copy(currentExerciseIndex = nextIndex, phase = TrainingPhase.Active)
    } else {
        copy(phase = TrainingPhase.Completed)
    }
}
