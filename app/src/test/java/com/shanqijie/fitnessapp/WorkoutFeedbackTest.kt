package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.domain.EquipmentAvailabilityScope
import com.shanqijie.fitnessapp.domain.WorkoutEarlyFinishReason
import com.shanqijie.fitnessapp.domain.WorkoutFeedback
import com.shanqijie.fitnessapp.domain.decideWorkoutFeedback
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutFeedbackTest {
    @Test
    fun discomfortAlwaysRequiresInjuryReview() {
        val decision = decideWorkoutFeedback(
            WorkoutFeedback("session", WorkoutEarlyFinishReason.DISCOMFORT, "右肩不适", createdAt = 1L),
        )
        assertTrue(decision.requiresInjuryReview)
        assertFalse(decision.disableEquipmentForVenue)
    }

    @Test
    fun equipmentUnavailableSplitsTemporaryAndPersistentScopes() {
        val temporary = decideWorkoutFeedback(
            WorkoutFeedback(
                "session",
                WorkoutEarlyFinishReason.EQUIPMENT_UNAVAILABLE,
                "器械维修",
                EquipmentAvailabilityScope.TEMPORARY,
                1L,
            ),
        )
        val persistent = decideWorkoutFeedback(
            WorkoutFeedback(
                "session",
                WorkoutEarlyFinishReason.EQUIPMENT_UNAVAILABLE,
                "场地移除",
                EquipmentAvailabilityScope.PERSISTENT,
                1L,
            ),
        )
        assertFalse(temporary.disableEquipmentForVenue)
        assertTrue(persistent.disableEquipmentForVenue)
    }

    @Test(expected = IllegalArgumentException::class)
    fun equipmentReasonRequiresScope() {
        decideWorkoutFeedback(
            WorkoutFeedback("session", WorkoutEarlyFinishReason.EQUIPMENT_UNAVAILABLE, "", createdAt = 1L),
        )
    }
}
