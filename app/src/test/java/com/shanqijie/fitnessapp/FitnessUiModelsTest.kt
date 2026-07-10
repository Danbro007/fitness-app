package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.domain.HomePrimaryAction
import com.shanqijie.fitnessapp.domain.HomeSnapshot
import com.shanqijie.fitnessapp.ui.model.TrainingEvent
import com.shanqijie.fitnessapp.ui.model.TrainingExerciseUi
import com.shanqijie.fitnessapp.ui.model.TrainingPhase
import com.shanqijie.fitnessapp.ui.model.TrainingUiState
import com.shanqijie.fitnessapp.ui.model.reduce
import com.shanqijie.fitnessapp.ui.model.toHomeUiState
import com.shanqijie.fitnessapp.ui.navigation.AppRoute
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessUiModelsTest {
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
}
