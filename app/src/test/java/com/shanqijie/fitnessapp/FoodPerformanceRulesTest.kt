package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.data.ExerciseMediaEntity
import com.shanqijie.fitnessapp.ui.food.calculateFoodPhotoInSampleSize
import com.shanqijie.fitnessapp.ui.library.SearchableExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodPerformanceRulesTest {
    @Test
    fun foodPhotoSamplingBoundsLargeInputsBeforeDecode() {
        assertEquals(1, calculateFoodPhotoInSampleSize(width = 1_600, height = 1_200))
        assertEquals(2, calculateFoodPhotoInSampleSize(width = 6_000, height = 4_000))
        assertEquals(4, calculateFoodPhotoInSampleSize(width = 12_000, height = 8_000))
    }

    @Test
    fun searchPrewarmBuildsChineseAndRawFieldsOnce() {
        val searchable = SearchableExercise(
            ExerciseMediaEntity(
                exerciseId = "0748",
                name = "smith bench press",
                bodyPart = "chest",
                equipment = "smith machine",
                target = "pectorals",
                mediaId = "media",
                localPath = "0748.gif",
                assetPackId = "pack",
                bytes = 1,
                sha256 = "hash",
            ),
        )

        searchable.prewarm()

        assertTrue(searchable.searchText.contains("smith bench press"))
        assertTrue(searchable.searchText.contains("史密斯机卧推"))
    }
}
