package com.shanqijie.fitnessapp.ui.plan

import com.shanqijie.fitnessapp.data.FitnessAppState
import com.shanqijie.fitnessapp.data.PlanCycleEntity
import com.shanqijie.fitnessapp.domain.CandidateExercise
import com.shanqijie.fitnessapp.domain.CandidateTrainingDay
import com.shanqijie.fitnessapp.domain.PlanCandidateSource
import com.shanqijie.fitnessapp.domain.WeeklyPlanCandidate

fun FitnessAppState.suggestAdaptiveCandidate(cycle: PlanCycleEntity): WeeklyPlanCandidate? {
    val schedules = planScheduleDays.filter { it.cycleId == cycle.id }.sortedBy { it.orderIndex }
    if (schedules.isEmpty()) return null
    val excluded = actionPreferences.filter { it.preference == "exclude" }.map { it.exerciseId }.toSet()
    val equipmentByVenue = venueEquipment.filter { it.available }.groupBy { it.venueId }
        .mapValues { (_, mappings) -> mappings.map { mapping -> equipment.firstOrNull { it.id == mapping.equipmentId } }.filterNotNull() }
    val usedExercises = mutableSetOf<String>()
    val days = schedules.mapNotNull { schedule ->
        val venueEquipment = equipmentByVenue[schedule.venueId].orEmpty()
        val equipment = venueEquipment.firstOrNull { equipmentItem ->
            venueEquipmentLoads.any { it.venueId == schedule.venueId && it.equipmentId == equipmentItem.id }
        } ?: return@mapNotNull null
        val exercise = exercises.firstOrNull { media ->
            media.exerciseId !in excluded && media.exerciseId !in usedExercises &&
                equipmentMatches(media.equipment, equipment.id, equipment.name)
        } ?: return@mapNotNull null
        val weight = venueEquipmentLoads
            .filter { it.venueId == schedule.venueId && it.equipmentId == equipment.id }
            .minOfOrNull { it.weightKg } ?: return@mapNotNull null
        usedExercises += exercise.exerciseId
        CandidateTrainingDay(
            dayOfWeek = schedule.dayOfWeek,
            venueId = schedule.venueId,
            exercises = listOf(
                CandidateExercise(
                    exerciseId = exercise.exerciseId,
                    name = exercise.name,
                    equipmentId = equipment.id,
                    primaryMuscle = exercise.target.ifBlank { exercise.bodyPart },
                    targetSets = 3,
                    targetRepsPerSet = 8,
                    targetWeightKg = weight,
                ),
            ),
        )
    }
    return days.takeIf { it.size == schedules.size }?.let { WeeklyPlanCandidate(PlanCandidateSource.LOCAL, it) }
}

private fun equipmentMatches(raw: String, id: String, name: String): Boolean {
    val source = raw.lowercase()
    val tokens = "$id $name".lowercase()
    return source.contains("barbell") && (tokens.contains("杠铃") || tokens.contains("barbell")) ||
        source.contains("dumbbell") && (tokens.contains("哑铃") || tokens.contains("dumbbell")) ||
        source.contains("smith") && (tokens.contains("史密斯") || tokens.contains("smith")) ||
        source.contains("cable") && (tokens.contains("拉力") || tokens.contains("龙门") || tokens.contains("cable")) ||
        source.contains("machine") && name.isNotBlank() || source.contains("body")
}
