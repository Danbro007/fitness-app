package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.domain.HomePrimaryAction
import com.shanqijie.fitnessapp.domain.HomeSnapshot
import com.shanqijie.fitnessapp.ui.components.GifDecoderKind
import com.shanqijie.fitnessapp.ui.components.SharedInstance
import com.shanqijie.fitnessapp.ui.components.gifDecoderKindFor
import com.shanqijie.fitnessapp.ui.model.TrainingEvent
import com.shanqijie.fitnessapp.ui.model.TrainingExerciseUi
import com.shanqijie.fitnessapp.ui.model.TrainingPhase
import com.shanqijie.fitnessapp.ui.model.TrainingUiState
import com.shanqijie.fitnessapp.ui.model.reduce
import com.shanqijie.fitnessapp.ui.model.toHomeUiState
import com.shanqijie.fitnessapp.ui.navigation.AppRoute
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab
import com.shanqijie.fitnessapp.ui.theme.FitnessColors
import androidx.compose.ui.graphics.toArgb
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessUiModelsTest {
    @Test
    fun mainActivityUsesOnlyNativeFitnessRoot() {
        val source = listOf(
            File("src/main/java/com/shanqijie/fitnessapp/MainActivity.kt"),
            File("app/src/main/java/com/shanqijie/fitnessapp/MainActivity.kt"),
        ).first(File::isFile).readText()

        assertTrue(source.contains("FitnessTheme {\n                FitnessAppRoot()"))
        assertFalse(source.contains("AppTab"))
        assertFalse(source.contains("MainShell"))
    }

    @Test
    fun productionRootOwnsRepositoryBootstrap() {
        val source = listOf(
            File("src/main/java/com/shanqijie/fitnessapp/ui/FitnessAppRoot.kt"),
            File("app/src/main/java/com/shanqijie/fitnessapp/ui/FitnessAppRoot.kt"),
        ).first(File::isFile).readText()

        assertTrue(source.contains("val applicationContext = LocalContext.current.applicationContext"))
        assertTrue(source.contains("FitnessDatabase.get(applicationContext)"))
        assertTrue(source.contains("repository.bootstrap()"))
        assertTrue(source.contains("FitnessAppRoot(repository = repository, modifier = modifier)"))
    }

    @Test
    fun homeStateExposesExactlyOneActionForStartResumeAndResult() {
        val cases = listOf(
            HomePrimaryAction.Start("plan-1") to ("开始训练" to AppRoute.Primary(PrimaryTab.Training)),
            HomePrimaryAction.Resume("session-1") to ("继续训练" to AppRoute.TrainingActive("session-1")),
            HomePrimaryAction.Result("session-1") to ("查看训练总结" to AppRoute.WorkoutSummary("session-1")),
        )

        cases.forEach { (action, expected) ->
            val state = HomeSnapshot(
                action = action,
                completedThisWeek = 1,
                targetThisWeek = 3,
                nextWorkout = null,
            ).toHomeUiState()

            assertEquals(1, state.actions.size)
            assertEquals(expected.first, state.actions.single().label)
            assertEquals(expected.second, state.actions.single().route)
            assertEquals(action is HomePrimaryAction.Result, state.completedToday)
        }
    }

    @Test
    fun trainingReducerAdvancesAfterTargetSetsAndRest() {
        val initial = TrainingUiState(
            sessionId = "session-1",
            exercises = listOf(
                TrainingExerciseUi(id = "session-exercise-1", exerciseId = "0748", targetSets = 1),
                TrainingExerciseUi(id = "session-exercise-2", exerciseId = "0289", targetSets = 2),
            ),
        )

        val resting = initial.reduce(TrainingEvent.SetCompleted(restEndsAt = 60_000L))
        assertEquals(1, resting.exercises.first().completedSets)
        assertEquals(TrainingPhase.Resting(endsAt = 60_000L), resting.phase)

        val advanced = resting.reduce(TrainingEvent.RestFinished)
        assertEquals(1, advanced.currentExerciseIndex)
        assertEquals(TrainingPhase.Active, advanced.phase)
        assertTrue(advanced.currentExercise?.exerciseId == "0289")
    }

    @Test
    fun orangePrimaryUsesInkForAccessibleText() {
        assertEquals(FitnessColors.Ink, FitnessColors.OnOrange)
        assertTrue(contrastRatio(FitnessColors.Ink, FitnessColors.Orange) >= 4.5)
    }

    @Test
    fun workoutErrorUsesReadableContainerOnDarkHero() {
        val source = listOf(
            File("src/main/java/com/shanqijie/fitnessapp/ui/training/TrainingScreens.kt"),
            File("app/src/main/java/com/shanqijie/fitnessapp/ui/training/TrainingScreens.kt"),
        ).first(File::isFile).readText()
        val errorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6)
        val onErrorContainer = androidx.compose.ui.graphics.Color(0xFF690005)

        assertTrue(contrastRatio(onErrorContainer, errorContainer) >= 4.5)
        assertTrue(source.contains(".background(WorkoutErrorContainer)"))
        assertTrue(source.contains("color = WorkoutOnErrorContainer"))
    }

    @Test
    fun smartConnectionStatusUsesAccessibleInkPixelsOnTheLightSurface() {
        val source = listOf(
            File("src/main/java/com/shanqijie/fitnessapp/ui/settings/SettingsScreens.kt"),
            File("app/src/main/java/com/shanqijie/fitnessapp/ui/settings/SettingsScreens.kt"),
        ).first(File::isFile).readText()
        val smartSection = source
            .substringAfter("fun SmartSettingsScreen(")
            .substringBefore("fun BackupSettingsScreen(")

        assertEquals(0xFF11151B.toInt(), FitnessColors.Ink.toArgb())
        assertTrue(contrastRatio(FitnessColors.Ink, FitnessColors.Phone) >= 4.5)
        assertTrue(smartSection.contains("color = FitnessColors.Ink"))
        assertFalse(smartSection.contains("color = if (provider?.apiKeyStored == true) FitnessColors.Green"))
    }

    @Test
    fun trainingReducerCompletesSetsOnlyWhileActive() {
        val active = TrainingUiState(
            sessionId = "session-1",
            exercises = listOf(
                TrainingExerciseUi(id = "session-exercise-1", exerciseId = "0748", targetSets = 2),
            ),
        )
        val blockedPhases = listOf(
            TrainingPhase.Preparation,
            TrainingPhase.Resting(endsAt = 60_000L),
            TrainingPhase.Paused,
        )

        blockedPhases.forEach { phase ->
            val blocked = active.copy(phase = phase)
            assertEquals(
                blocked,
                blocked.reduce(TrainingEvent.SetCompleted(restEndsAt = 120_000L)),
            )
        }
    }

    @Test
    fun gifLoaderSingletonCreatesValueOnlyOnce() {
        val singleton = SharedInstance<Any>()
        var createCount = 0

        val first = singleton.get {
            createCount += 1
            Any()
        }
        val second = singleton.get {
            createCount += 1
            Any()
        }

        assertSame(first, second)
        assertEquals(1, createCount)
    }

    @Test
    fun gifLoaderSelectsDecoderForAndroidApiLevel() {
        assertEquals(GifDecoderKind.GifDecoder, gifDecoderKindFor(27))
        assertEquals(GifDecoderKind.ImageDecoder, gifDecoderKindFor(28))
        assertEquals(GifDecoderKind.ImageDecoder, gifDecoderKindFor(36))
    }

    private fun contrastRatio(foreground: androidx.compose.ui.graphics.Color, background: androidx.compose.ui.graphics.Color): Double {
        val lighter = maxOf(relativeLuminance(foreground), relativeLuminance(background))
        val darker = minOf(relativeLuminance(foreground), relativeLuminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: androidx.compose.ui.graphics.Color): Double {
        fun linear(channel: Float): Double =
            if (channel <= 0.04045f) {
                channel / 12.92
            } else {
                Math.pow((channel + 0.055) / 1.055, 2.4)
            }

        return 0.2126 * linear(color.red) +
            0.7152 * linear(color.green) +
            0.0722 * linear(color.blue)
    }
}
