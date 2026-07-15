package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.ui.food.parseFoodEstimateConfirmation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodEstimateConfirmationTest {
    @Test
    fun parsesTrimmedUserConfirmedValues() {
        val confirmation = parseFoodEstimateConfirmation(
            name = "  自定义晚餐  ",
            calories = "680",
            protein = "50.5",
            carbs = "72",
            fat = "18",
        )

        assertEquals("自定义晚餐", confirmation.name)
        assertEquals(680, confirmation.calories)
        assertEquals(50.5, confirmation.proteinGrams, 0.0)
        assertEquals(72.0, confirmation.carbsGrams, 0.0)
        assertEquals(18.0, confirmation.fatGrams, 0.0)
    }

    @Test
    fun rejectsInvalidConfirmedValues() {
        val invalidInputs = listOf(
            arrayOf("", "680", "50", "72", "18"),
            arrayOf("晚餐", "5001", "50", "72", "18"),
            arrayOf("晚餐", "680", "NaN", "72", "18"),
            arrayOf("晚餐", "680", "50", "-1", "18"),
        )

        invalidInputs.forEach { input ->
            assertTrue(
                runCatching {
                    parseFoodEstimateConfirmation(input[0], input[1], input[2], input[3], input[4])
                }.exceptionOrNull() is IllegalArgumentException,
            )
        }
    }
}
