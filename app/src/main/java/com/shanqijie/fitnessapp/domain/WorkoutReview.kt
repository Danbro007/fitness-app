package com.shanqijie.fitnessapp.domain

import com.shanqijie.fitnessapp.data.AiDraftEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class WorkoutAdjustmentDirection {
    INCREASE,
    MAINTAIN,
    REDUCE,
}

data class WorkoutReviewSignals(
    val completedSets: Int,
    val targetSets: Int,
    val feelingCounts: Map<String, Int>,
    val postWorkoutFeeling: String,
)

@Serializable
data class WorkoutReviewMetadata(
    val sessionId: String,
    val direction: String,
    val postWorkoutFeeling: String,
    val postWorkoutNote: String,
    val exerciseIds: List<String>,
)

fun decideWorkoutAdjustment(signals: WorkoutReviewSignals): WorkoutAdjustmentDirection {
    val completionRatio = signals.completedSets.toDouble() / signals.targetSets.coerceAtLeast(1)
    val hardSets = signals.feelingCounts.entries
        .filter { (feeling, _) -> feeling.contains("吃力") }
        .sumOf { it.value }
    val easySets = signals.feelingCounts.entries
        .filter { (feeling, _) -> feeling.contains("轻松") }
        .sumOf { it.value }

    return when {
        signals.postWorkoutFeeling in setOf("非常疲劳", "疼痛不适") -> WorkoutAdjustmentDirection.REDUCE
        completionRatio < 0.8 -> WorkoutAdjustmentDirection.REDUCE
        hardSets > signals.completedSets / 2 -> WorkoutAdjustmentDirection.REDUCE
        completionRatio >= 1.0 &&
            signals.postWorkoutFeeling == "状态很好" &&
            easySets >= (signals.completedSets.coerceAtLeast(1) + 1) / 2 -> WorkoutAdjustmentDirection.INCREASE
        else -> WorkoutAdjustmentDirection.MAINTAIN
    }
}

fun WorkoutReviewMetadata.toJson(): String = workoutReviewJson.encodeToString(this)

fun AiDraftEntity.workoutReviewMetadata(): WorkoutReviewMetadata? =
    if (type != "workout_review" || metadataJson.isBlank()) null
    else runCatching { workoutReviewJson.decodeFromString<WorkoutReviewMetadata>(metadataJson) }.getOrNull()

private val workoutReviewJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
