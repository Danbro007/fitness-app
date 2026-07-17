package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.data.FitnessBackupCodec
import com.shanqijie.fitnessapp.data.FitnessBackupPayload
import com.shanqijie.fitnessapp.data.EquipmentEntity
import com.shanqijie.fitnessapp.data.PlannedExerciseEntity
import com.shanqijie.fitnessapp.data.PlannedWorkoutEntity
import com.shanqijie.fitnessapp.data.TrainingVenueEntity
import com.shanqijie.fitnessapp.data.VenueEquipmentEntity
import com.shanqijie.fitnessapp.data.WorkoutSessionEntity
import com.shanqijie.fitnessapp.data.WorkoutSessionExerciseEntity
import com.shanqijie.fitnessapp.data.WorkoutSetLogEntity
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessBackupSecurityTest {
    @Test
    fun boundedReaderRejectsDeclaredAndStreamedOversizeWithoutReadingTheWholeFile() {
        val declaredFailure = runCatching {
            FitnessBackupCodec.readBounded(ByteArrayInputStream(byteArrayOf()), FitnessBackupCodec.MAX_BACKUP_BYTES + 1L)
        }.exceptionOrNull()
        var emitted = 0
        val oversizedStream = object : InputStream() {
            override fun read(): Int = if (emitted++ <= FitnessBackupCodec.MAX_BACKUP_BYTES) 'x'.code else -1
        }
        val streamedFailure = runCatching { FitnessBackupCodec.readBounded(oversizedStream) }.exceptionOrNull()

        assertTrue(declaredFailure is IllegalArgumentException)
        assertTrue(streamedFailure is IllegalArgumentException)
        assertTrue(emitted <= FitnessBackupCodec.MAX_BACKUP_BYTES + DEFAULT_BUFFER_SIZE + 1)
    }

    @Test
    fun previewValidatesBeforeReportingCountsAndRejectsOversizeBase64() {
        val payload = emptyPayload().copy(avatarBase64 = "A".repeat(FitnessBackupCodec.MAX_AVATAR_BASE64_CHARS + 1))
        val failure = runCatching { FitnessBackupCodec.validate(payload) }.exceptionOrNull()
        val preview = FitnessBackupCodec.preview(FitnessBackupCodec.encode(emptyPayload()))

        assertTrue(failure is IllegalArgumentException)
        assertEquals(4, preview.version)
        assertEquals(0, preview.planCount)
        assertEquals(0, preview.sessionCount)
    }

    @Test
    fun currentBackupRejectsEveryMajorDanglingReference() {
        val payload = linkedPayload()
        fun rejects(candidate: FitnessBackupPayload) =
            assertTrue(runCatching { FitnessBackupCodec.validate(candidate) }.isFailure)

        FitnessBackupCodec.validate(payload)
        rejects(payload.copy(venueEquipment = listOf(payload.venueEquipment.single().copy(venueId = "missing"))))
        rejects(payload.copy(venueEquipment = listOf(payload.venueEquipment.single().copy(equipmentId = "missing"))))
        rejects(payload.copy(plannedWorkouts = listOf(payload.plannedWorkouts.single().copy(venueId = "missing"))))
        rejects(payload.copy(plannedExercises = listOf(payload.plannedExercises.single().copy(plannedWorkoutId = "missing"))))
        rejects(payload.copy(workoutSessions = listOf(payload.workoutSessions.single().copy(plannedWorkoutId = "missing"))))
        rejects(payload.copy(workoutSessions = listOf(payload.workoutSessions.single().copy(venueId = "missing"))))
        rejects(payload.copy(workoutSessions = listOf(payload.workoutSessions.single().copy(currentExerciseId = "missing"))))
        rejects(payload.copy(sessionExercises = listOf(payload.sessionExercises.single().copy(sessionId = "missing"))))
        rejects(payload.copy(setLogs = listOf(payload.setLogs.single().copy(sessionId = "missing", sessionExerciseId = null))))
        rejects(payload.copy(plannedWorkouts = listOf(payload.plannedWorkouts.single().copy(status = "unknown"))))
        rejects(payload.copy(plannedWorkouts = listOf(payload.plannedWorkouts.single().copy(scheduledDate = "not-a-date"))))
        rejects(payload.copy(plannedExercises = listOf(payload.plannedExercises.single().copy(targetSets = 0))))
    }

    private fun linkedPayload(): FitnessBackupPayload {
        val venue = TrainingVenueEntity("venue", "场地", true, 1L, 1L)
        val equipment = EquipmentEntity("equipment", "器械", "machine", 1L, 1L)
        val plan = PlannedWorkoutEntity("plan", "计划", "2026-07-17", venue.id, "in_progress", 1L, 1L)
        val plannedExercise = PlannedExerciseEntity("planned-exercise", plan.id, "exercise", 0, 1, "8", 10.0, "")
        val session = WorkoutSessionEntity("session", plan.id, venue.id, "exercise", "in_progress", 1L, null, 1L, "exercise")
        val sessionExercise = WorkoutSessionExerciseEntity("session-exercise", session.id, "exercise", 0, 1, "8", 10.0, "pending")
        val set = WorkoutSetLogEntity("set", session.id, "exercise", 1, 8, 10.0, "合适", true, 2L, sessionExercise.id)
        return emptyPayload().copy(
            venues = listOf(venue),
            equipment = listOf(equipment),
            venueEquipment = listOf(VenueEquipmentEntity(venue.id, equipment.id, true, 1L)),
            plannedWorkouts = listOf(plan),
            plannedExercises = listOf(plannedExercise),
            workoutSessions = listOf(session),
            sessionExercises = listOf(sessionExercise),
            setLogs = listOf(set),
        )
    }

    private fun emptyPayload() = FitnessBackupPayload(
        version = 4,
        exportedAt = 1L,
        userProfile = null,
        venues = emptyList(),
        equipment = emptyList(),
        plannedWorkouts = emptyList(),
        plannedExercises = emptyList(),
        workoutSessions = emptyList(),
        setLogs = emptyList(),
        foodLogs = emptyList(),
        aiDrafts = emptyList(),
        aiProviders = emptyList(),
    )
}
