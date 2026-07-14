package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.ui.training.dominantFeeling
import com.shanqijie.fitnessapp.ui.training.initialRepsInput
import com.shanqijie.fitnessapp.ui.training.normalizeStepperCandidate
import com.shanqijie.fitnessapp.ui.training.parseTrainingInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingInputParsingTest {
    @Test
    fun derivesSafeInitialRepetitionsFromPlanText() {
        assertEquals("8", initialRepsInput("8-12"))
        assertEquals("1", initialRepsInput("0"))
        assertEquals("50", initialRepsInput("99"))
        assertEquals("8", initialRepsInput("不限"))
    }

    @Test
    fun parsesValidBoundariesAndClampsFallbackDisplayValues() {
        assertTrue(parseTrainingInputs("1", "0").valid)
        assertTrue(parseTrainingInputs("50", "500").valid)
        assertEquals(1, parseTrainingInputs("bad", "20").reps)
        assertEquals(1, parseTrainingInputs("0", "20").reps)
        assertEquals(50, parseTrainingInputs("51", "20").reps)
        assertEquals(0.0, parseTrainingInputs("8", "bad").weightKg, 0.0)
        assertEquals(0.0, parseTrainingInputs("8", "-1").weightKg, 0.0)
        assertEquals(500.0, parseTrainingInputs("8", "501").weightKg, 0.0)
    }

    @Test
    fun marksEveryMalformedOrOutOfRangeCombinationInvalid() {
        listOf(
            "bad" to "20",
            "0" to "20",
            "51" to "20",
            "8" to "bad",
            "8" to "-0.1",
            "8" to "500.1",
        ).forEach { (reps, weight) ->
            assertFalse(parseTrainingInputs(reps, weight).valid)
        }
    }

    @Test
    fun normalizesStepperCandidatesWithoutAcceptingInvalidText() {
        assertEquals("12.5", normalizeStepperCandidate("12,5", decimal = true, maximum = 500.0))
        assertEquals("", normalizeStepperCandidate("", decimal = true, maximum = 500.0))
        assertEquals("50", normalizeStepperCandidate("50", decimal = false, maximum = 50.0))
        assertNull(normalizeStepperCandidate("501", decimal = true, maximum = 500.0))
        assertNull(normalizeStepperCandidate("51", decimal = false, maximum = 50.0))
        assertNull(normalizeStepperCandidate("1.5", decimal = false, maximum = 50.0))
        assertNull(normalizeStepperCandidate("1234", decimal = true, maximum = 500.0))
        assertNull(normalizeStepperCandidate("abc", decimal = true, maximum = 500.0))
    }

    @Test
    fun selectsTheFirstMostFrequentFeelingWithAnEmptyFallback() {
        assertEquals("合适", dominantFeeling(emptyMap()))
        assertEquals("轻松", dominantFeeling(linkedMapOf("轻松" to 1)))
        assertEquals("吃力", dominantFeeling(linkedMapOf("轻松" to 1, "吃力" to 3)))
        assertEquals("轻松", dominantFeeling(linkedMapOf("轻松" to 3, "合适" to 2, "吃力" to 3)))
    }
}
