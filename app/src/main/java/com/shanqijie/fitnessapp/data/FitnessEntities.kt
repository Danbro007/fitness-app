package com.shanqijie.fitnessapp.data

import kotlinx.serialization.Serializable

@Serializable
data class TrainingVenueEntity(
    val id: String,
    val name: String,
    val isDefault: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class EquipmentEntity(
    val id: String,
    val name: String,
    val category: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ExerciseMediaEntity(
    val exerciseId: String,
    val name: String,
    val bodyPart: String,
    val equipment: String,
    val target: String,
    val mediaId: String,
    val localPath: String,
    val assetPackId: String,
    val bytes: Long,
    val sha256: String,
)

@Serializable
data class PlannedWorkoutEntity(
    val id: String,
    val name: String,
    val scheduledDate: String,
    val venueId: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class PlannedExerciseEntity(
    val id: String,
    val plannedWorkoutId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val targetSets: Int,
    val targetReps: String,
    val targetWeightKg: Double,
    val note: String,
)

@Serializable
data class WorkoutSessionEntity(
    val id: String,
    val plannedWorkoutId: String?,
    val venueId: String,
    val exerciseId: String,
    val status: String,
    val startedAt: Long,
    val endedAt: Long?,
    val updatedAt: Long,
    val currentExerciseId: String? = null,
    val restEndsAt: Long? = null,
    val pausedAt: Long? = null,
)

@Serializable
data class WorkoutSessionExerciseEntity(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val targetSets: Int,
    val targetReps: String,
    val targetWeightKg: Double,
    val status: String,
)

@Serializable
data class WorkoutSetLogEntity(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val setIndex: Int,
    val actualReps: Int,
    val actualWeightKg: Double,
    val feeling: String,
    val completed: Boolean,
    val completedAt: Long,
    val sessionExerciseId: String? = null,
)

@Serializable
data class AiProviderEntity(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val model: String,
    val enabled: Boolean,
    val apiKeyStored: Boolean,
    val updatedAt: Long,
)

@Serializable
data class UserProfileEntity(
    val id: String,
    val displayName: String,
    val birthYear: Int,
    val heightCm: Double,
    val weightKg: Double,
    val goal: String,
    val injuries: String,
    val weeklyTrainingDays: Int,
    val preferredMinutes: Int,
    val updatedAt: Long,
)

@Serializable
data class FoodLogEntity(
    val id: String,
    val loggedDate: String,
    val name: String,
    val calories: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val source: String,
    val imageNote: String,
    val imageUri: String = "",
    val providerId: String = "",
    val model: String = "",
    val confirmed: Boolean,
    val createdAt: Long,
)

@Serializable
data class AiDraftEntity(
    val id: String,
    val type: String,
    val title: String,
    val content: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val metadataJson: String = "",
    val confirmedAt: Long?,
)

@Serializable
data class VenueEquipmentEntity(
    val venueId: String,
    val equipmentId: String,
    val available: Boolean,
    val updatedAt: Long,
)

@Serializable
data class TrainingAdjustmentEntity(
    val id: String,
    val exerciseId: String,
    val title: String,
    val content: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val confirmedAt: Long?,
)
