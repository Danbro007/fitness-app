package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.data.ExerciseMediaEntity
import com.shanqijie.fitnessapp.ui.library.ExerciseFilter
import com.shanqijie.fitnessapp.ui.library.SearchableExercise
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseFilterTest {
    @Test
    fun chestAndBackFiltersCoverChineseEnglishAndAnatomyAliases() {
        listOf("胸", "chest", "pector").forEach { anatomy ->
            assertTrue(ExerciseFilter.Chest.matches(exercise(anatomy)))
        }
        assertFalse(ExerciseFilter.Chest.matches(exercise("arms")))

        listOf("背", "back", "lat").forEach { anatomy ->
            assertTrue(ExerciseFilter.Back.matches(exercise(anatomy)))
        }
        assertFalse(ExerciseFilter.Back.matches(exercise("arms")))
    }

    @Test
    fun allAndRemainingFiltersUseTranslatedSearchableFields() {
        val legs = exercise("quadriceps")
        val core = exercise("abdominal")
        val unrelated = exercise("arms")
        assertTrue(ExerciseFilter.All.matches(legs))
        assertTrue(ExerciseFilter.Legs.matches(legs))
        assertFalse(ExerciseFilter.Legs.matches(unrelated))
        assertTrue(ExerciseFilter.Core.matches(core))
        assertFalse(ExerciseFilter.Core.matches(unrelated))
        assertTrue(legs.searchText.contains("quadriceps"))
        assertTrue(legs.name.isNotBlank())
        assertTrue(legs.bodyPart.isNotBlank())
        assertTrue(legs.equipment.isNotBlank())
        assertTrue(legs.target.isNotBlank())
    }

    @Test
    fun englishBackAndChestAliasesRemainSearchableWithoutTranslatedAnatomy() {
        assertTrue(ExerciseFilter.Chest.matches(rawOnlyExercise("chest")))
        assertTrue(ExerciseFilter.Chest.matches(rawOnlyExercise("pector")))
        assertTrue(ExerciseFilter.Back.matches(rawOnlyExercise("back")))
        assertTrue(ExerciseFilter.Back.matches(rawOnlyExercise("lat")))
    }

    private fun rawOnlyExercise(anatomy: String): SearchableExercise = exercise(anatomy).also { searchable ->
        listOf("bodyPart\$delegate", "target\$delegate").forEach { fieldName ->
            SearchableExercise::class.java.getDeclaredField(fieldName).apply {
                isAccessible = true
                set(searchable, lazy(LazyThreadSafetyMode.NONE) { "" })
            }
        }
    }

    private fun exercise(anatomy: String) = SearchableExercise(
        ExerciseMediaEntity(
            exerciseId = "exercise-$anatomy",
            name = "squat",
            bodyPart = anatomy,
            equipment = "barbell",
            target = anatomy,
            mediaId = "media-$anatomy",
            localPath = "$anatomy.gif",
            assetPackId = "pack",
            bytes = 1L,
            sha256 = anatomy,
        ),
    )
}
