package com.shanqijie.fitnessapp.domain

import com.shanqijie.fitnessapp.data.PlannedWorkoutEntity

data class WorkoutSummary(
    val sessionId: String,
    val completedSets: Int,
    val targetSets: Int,
    val totalVolumeKg: Double,
    val durationSeconds: Long,
    val feelingCounts: Map<String, Int>,
)

data class HomeSnapshot(
    val action: HomePrimaryAction,
    val completedThisWeek: Int,
    val targetThisWeek: Int,
    val nextWorkout: PlannedWorkoutEntity?,
)

sealed interface HomePrimaryAction {
    data class Start(val planId: String) : HomePrimaryAction
    data class Resume(val sessionId: String) : HomePrimaryAction
    data class Result(val sessionId: String) : HomePrimaryAction
    data object CreatePlan : HomePrimaryAction
}

data class NutritionSummary(
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)
