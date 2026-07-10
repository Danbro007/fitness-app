package com.shanqijie.fitnessapp.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class FitnessBackupPayload(
    val version: Int,
    val exportedAt: Long,
    val userProfile: UserProfileEntity?,
    val venues: List<TrainingVenueEntity>,
    val equipment: List<EquipmentEntity>,
    val venueEquipment: List<VenueEquipmentEntity> = emptyList(),
    val preferences: Map<String, String> = emptyMap(),
    val plannedWorkouts: List<PlannedWorkoutEntity>,
    val plannedExercises: List<PlannedExerciseEntity>,
    val workoutSessions: List<WorkoutSessionEntity>,
    val sessionExercises: List<WorkoutSessionExerciseEntity> = emptyList(),
    val setLogs: List<WorkoutSetLogEntity>,
    val foodLogs: List<FoodLogEntity>,
    val aiDrafts: List<AiDraftEntity>,
    val trainingAdjustments: List<TrainingAdjustmentEntity> = emptyList(),
    val aiProviders: List<AiProviderEntity>,
)

object FitnessBackupCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: FitnessBackupPayload): String =
        json.encodeToString(payload)

    fun decode(rawJson: String): FitnessBackupPayload =
        json.decodeFromString(rawJson)
}
