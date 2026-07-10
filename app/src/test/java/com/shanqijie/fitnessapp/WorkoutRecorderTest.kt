package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.domain.WorkoutRecorder
import com.shanqijie.fitnessapp.domain.WorkoutSetInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRecorderTest {
    @Test
    fun completesSetsInOrder() {
        val recorder = WorkoutRecorder(targetSets = 2)

        val first = recorder.completeNextSet(WorkoutSetInput(reps = 8, weightKg = 70.0, feeling = "合适"))
        val second = recorder.completeNextSet(WorkoutSetInput(reps = 7, weightKg = 70.0, feeling = "吃力"))

        assertEquals(1, first.setIndex)
        assertEquals(2, second.setIndex)
        assertEquals(2, recorder.completedSets)
        assertTrue(recorder.isComplete)
        assertEquals(listOf(first, second), recorder.records())
    }

    @Test
    fun refusesInvalidSetInput() {
        val recorder = WorkoutRecorder(targetSets = 4)

        assertThrows(IllegalArgumentException::class.java) {
            recorder.completeNextSet(WorkoutSetInput(reps = 0, weightKg = 70.0, feeling = "合适"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            recorder.completeNextSet(WorkoutSetInput(reps = 8, weightKg = -1.0, feeling = "合适"))
        }
        assertFalse(recorder.isComplete)
    }

    @Test
    fun refusesMoreThanTargetSets() {
        val recorder = WorkoutRecorder(targetSets = 1)
        recorder.completeNextSet(WorkoutSetInput(reps = 8, weightKg = 70.0, feeling = "合适"))

        assertThrows(IllegalStateException::class.java) {
            recorder.completeNextSet(WorkoutSetInput(reps = 8, weightKg = 70.0, feeling = "合适"))
        }
    }
}
