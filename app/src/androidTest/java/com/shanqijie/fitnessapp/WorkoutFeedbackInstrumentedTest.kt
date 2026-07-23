package com.shanqijie.fitnessapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shanqijie.fitnessapp.data.FitnessDatabase
import com.shanqijie.fitnessapp.data.FitnessRepository
import com.shanqijie.fitnessapp.data.FitnessStore
import com.shanqijie.fitnessapp.data.PlannedExerciseEntity
import com.shanqijie.fitnessapp.data.PlannedWorkoutEntity
import com.shanqijie.fitnessapp.domain.EquipmentAvailabilityScope
import com.shanqijie.fitnessapp.domain.WorkoutEarlyFinishReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkoutFeedbackInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: FitnessDatabase
    private lateinit var store: FitnessStore
    private lateinit var repository: FitnessRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("workout-feedback-test.db")
        database = FitnessDatabase(context, "workout-feedback-test.db")
        store = FitnessStore(database)
        repository = FitnessRepository(context, store)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase("workout-feedback-test.db")
    }

    @Test
    fun discomfortFeedbackIsPersistedAndPersistentEquipmentIsDisabled() = runBlocking {
        repository.bootstrap()
        val plan = PlannedWorkoutEntity("feedback-plan", "反馈测试", "2026-07-23", FitnessRepository.DEFAULT_VENUE_ID, "planned", 1L, 1L)
        store.upsertPlannedWorkout(plan)
        val exercise = store.exerciseById(FitnessRepository.SMITH_BENCH_PRESS_ID)
        assertNotNull(exercise)
        store.upsertPlannedExercise(
            PlannedExerciseEntity("feedback-exercise", plan.id, FitnessRepository.SMITH_BENCH_PRESS_ID, 0, 2, "8", 20.0, ""),
        )
        val session = repository.startWorkout(plan.id)
        repository.saveWorkoutFeedback(
            sessionId = session.id,
            reason = WorkoutEarlyFinishReason.DISCOMFORT,
            note = "右肩不适",
        )
        val saved = repository.workoutFeedback(session.id)
        assertEquals(WorkoutEarlyFinishReason.DISCOMFORT, saved?.reason)
        assertEquals("右肩不适", saved?.note)
        assertEquals("true", repository.appState().first().preferences[FitnessRepository.INJURY_REVIEW_REQUIRED_KEY])

        repository.finishWorkout(session.id)
        val secondSession = repository.startWorkout(plan.id)
        repository.saveWorkoutFeedback(
            sessionId = secondSession.id,
            reason = WorkoutEarlyFinishReason.EQUIPMENT_UNAVAILABLE,
            note = "器械移除",
            equipmentScope = EquipmentAvailabilityScope.PERSISTENT,
        )
        val smithMapping = store.venueEquipment().first { it.venueId == FitnessRepository.DEFAULT_VENUE_ID && it.equipmentId == "equipment-smith-machine" }
        assertFalse(smithMapping.available)
        assertTrue(repository.workoutFeedback(secondSession.id) != null)
    }
}
