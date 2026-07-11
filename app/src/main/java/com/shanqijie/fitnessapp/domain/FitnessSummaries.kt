package com.shanqijie.fitnessapp.domain

import com.shanqijie.fitnessapp.data.PlannedWorkoutEntity

data class WorkoutSummary(
    val sessionId: String,
    val completedSets: Int,
    val targetSets: Int,
    val totalVolumeKg: Double,
    val durationSeconds: Long,
    val feelingCounts: Map<String, Int>,
    val isFullyCompleted: Boolean = completedSets >= targetSets,
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
    val reference: NutritionReference? = null,
)

/**
 * A lightweight daily reference derived from the locally saved profile. It is deliberately
 * presented as a reference rather than a prescribed diet, so it never masquerades as medical
 * or nutritionist advice.
 */
data class NutritionReference(
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)
