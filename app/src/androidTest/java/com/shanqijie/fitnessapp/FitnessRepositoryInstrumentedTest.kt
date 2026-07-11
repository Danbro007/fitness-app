package com.shanqijie.fitnessapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shanqijie.fitnessapp.data.AiCredentialStore
import com.shanqijie.fitnessapp.data.BodyMeasurement
import com.shanqijie.fitnessapp.data.EquipmentEntity
import com.shanqijie.fitnessapp.data.FitnessBackupCodec
import com.shanqijie.fitnessapp.data.FitnessDatabase
import com.shanqijie.fitnessapp.data.FitnessRepository
import com.shanqijie.fitnessapp.data.FitnessStore
import com.shanqijie.fitnessapp.data.ExerciseMediaEntity
import com.shanqijie.fitnessapp.data.FoodLogEntity
import com.shanqijie.fitnessapp.data.PlannedExerciseEntity
import com.shanqijie.fitnessapp.data.PlannedWorkoutEntity
import com.shanqijie.fitnessapp.data.TimeProvider
import com.shanqijie.fitnessapp.data.TrainingVenueEntity
import com.shanqijie.fitnessapp.data.UserProfileEntity
import com.shanqijie.fitnessapp.data.WorkoutSessionEntity
import com.shanqijie.fitnessapp.domain.HomePrimaryAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class FitnessRepositoryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var db: FitnessDatabase
    private lateinit var store: FitnessStore
    private lateinit var repository: FitnessRepository
    private lateinit var credentialStore: AiCredentialStore
    private lateinit var timeProvider: FakeTimeProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
        db = FitnessDatabase(context, DB_NAME)
        store = FitnessStore(db)
        credentialStore = AiCredentialStore(context, CREDENTIAL_PREFERENCES)
        credentialStore.deleteApiKey(FitnessRepository.DEEPSEEK_PROVIDER_ID)
        timeProvider = FakeTimeProvider(Instant.parse("2026-07-10T01:00:00Z").toEpochMilli())
        repository = FitnessRepository(
            context = context,
            store = store,
            credentialStore = credentialStore,
            timeProvider = timeProvider,
        )
    }

    @After
    fun tearDown() {
        credentialStore.deleteApiKey(FitnessRepository.DEEPSEEK_PROVIDER_ID)
        context.getSharedPreferences(CREDENTIAL_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        db.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun freshBootstrapDoesNotCreateAnUnfinishedSession() = runBlocking {
        repository.bootstrap()

        val state = repository.appState().first()

        assertTrue(state.workoutSessions.isEmpty())
        assertTrue(state.unfinishedSessions.isEmpty())
    }

    @Test
    fun weeklyPlanDraftUsesSavedBodyCompositionAsAiContext() = runBlocking {
        repository.bootstrap()
        repository.saveUserProfile(
            displayName = "山崎",
            birthYear = 1987,
            heightCm = 173.0,
            weightKg = 76.5,
            goal = "减脂",
            injuries = "无",
            weeklyTrainingDays = 3,
            preferredMinutes = 45,
            bodyMeasurement = BodyMeasurement(
                measuredAt = "2026-06-14",
                bodyType = "偏胖型",
                bodyFatPercentage = 24.8,
                bodyFatMassKg = 19.0,
                skeletalMuscleKg = 32.5,
                bodyWaterKg = 42.1,
                basalMetabolismKcal = 1613,
                waistHipRatio = 0.90,
                bodyAge = 39,
            ),
        )

        val draft = repository.generateWeeklyPlanDraft()

        assertTrue(draft.content.contains("体脂率 24.8%"))
        assertTrue(draft.content.contains("骨骼肌 32.5 kg"))
        assertTrue(draft.content.contains("基础代谢 1613 kcal"))
        assertTrue(draft.content.contains("体型 偏胖型"))
    }

    @Test
    fun partialFinishPersistsProgressWithoutCompletingPlanOrWeek() = runBlocking {
        seedExercises()
        seedPlan(
            planId = "plan-flow",
            scheduledDate = "2026-07-10",
            exercises = listOf(
                PlannedExerciseSeed("0748", targetSets = 1, targetWeightKg = 70.0),
                PlannedExerciseSeed("0289", targetSets = 2, targetWeightKg = 24.0),
            ),
        )

        val session = repository.startWorkout("plan-flow")
        assertEquals(session.id, repository.startWorkout("plan-flow").id)
        UUID.fromString(session.id)
        assertEquals(listOf("0748", "0289"), store.sessionExercises(session.id).map { it.exerciseId })

        val invalidReps = runCatching {
            repository.recordWorkoutSet(
                sessionId = session.id,
                exerciseId = "0748",
                reps = 0,
                weightKg = 70.0,
                feeling = "合适",
            )
        }.exceptionOrNull()
        val invalidWeight = runCatching {
            repository.recordWorkoutSet(
                sessionId = session.id,
                exerciseId = "0748",
                reps = 8,
                weightKg = -1.0,
                feeling = "合适",
            )
        }.exceptionOrNull()
        assertTrue(invalidReps is IllegalArgumentException)
        assertTrue(invalidWeight is IllegalArgumentException)

        val log = repository.recordWorkoutSet(
            sessionId = session.id,
            exerciseId = "0748",
            reps = 8,
            weightKg = 70.0,
            feeling = "合适",
            restSeconds = 90,
        )
        assertEquals(1, log.setIndex)
        assertEquals(store.sessionExercises(session.id).first().id, log.sessionExerciseId)
        assertEquals(timeProvider.currentTimeMillis() + 90_000L, store.workoutSession(session.id)?.restEndsAt)

        val overTarget = runCatching {
            repository.recordWorkoutSet(
                sessionId = session.id,
                exerciseId = "0748",
                reps = 8,
                weightKg = 70.0,
                feeling = "轻松",
            )
        }.exceptionOrNull()
        assertTrue(overTarget is IllegalArgumentException)

        repository = FitnessRepository(
            context = context,
            store = store,
            credentialStore = credentialStore,
            timeProvider = timeProvider,
        )
        assertEquals(session.id, repository.appState().first().unfinishedSessions.single().id)
        repository.skipRest(session.id)
        assertNull(store.workoutSession(session.id)?.restEndsAt)
        repository.selectWorkoutExercise(session.id, "0289")
        repository.startRest(session.id, durationSeconds = 30)
        assertEquals(timeProvider.currentTimeMillis() + 30_000L, store.workoutSession(session.id)?.restEndsAt)
        repository.skipRest(session.id)
        repository.addExerciseToSession(
            sessionId = session.id,
            exerciseId = "2330",
            targetSets = 2,
            targetReps = "10-12",
            targetWeightKg = 45.0,
        )
        assertEquals("2330", store.workoutSession(session.id)?.currentExerciseId)

        timeProvider.advanceSeconds(300)
        val summary = repository.finishWorkout(session.id)
        val refreshedState = repository.appState().first()
        val home = repository.homeSnapshot(refreshedState, today = LocalDate.of(2026, 7, 10))

        assertEquals(1, store.workoutSessions().size)
        assertEquals(1, store.setLogs(session.id).size)
        assertEquals(session.id, store.setLogs(session.id).single().sessionId)
        assertNotNull(store.setLogs(session.id).single().sessionExerciseId)
        assertEquals("planned", store.plannedWorkouts().single { it.id == "plan-flow" }.status)
        assertEquals(1, summary.completedSets)
        assertEquals(5, summary.targetSets)
        assertFalse(summary.isFullyCompleted)
        assertEquals(560.0, summary.totalVolumeKg, 0.01)
        assertEquals(300L, summary.durationSeconds)
        assertEquals(mapOf("合适" to 1), summary.feelingCounts)
        assertEquals(0, home.completedThisWeek)
        assertEquals(1, home.targetThisWeek)
        assertEquals(HomePrimaryAction.Start("plan-flow"), home.action)
        assertEquals("partial", store.workoutSession(session.id)?.status)
    }

    @Test
    fun fullFinishCompletesPlanAndCountsTowardWeek() = runBlocking {
        seedExercises()
        seedPlan(
            planId = "plan-complete",
            scheduledDate = "2026-07-10",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 1, targetWeightKg = 70.0)),
        )

        val session = repository.startWorkout("plan-complete")
        repository.recordWorkoutSet(
            sessionId = session.id,
            exerciseId = "0748",
            reps = 8,
            weightKg = 70.0,
            feeling = "合适",
        )

        val summary = repository.finishWorkout(session.id)
        val state = repository.appState().first()
        val home = repository.homeSnapshot(state, today = LocalDate.of(2026, 7, 10))

        assertTrue(summary.isFullyCompleted)
        assertEquals("completed", store.workoutSession(session.id)?.status)
        assertEquals("completed", store.plannedWorkouts().single { it.id == "plan-complete" }.status)
        assertEquals(1, home.completedThisWeek)
        assertEquals(HomePrimaryAction.Result(session.id), home.action)
    }

    @Test
    fun libraryExerciseJoinsThePersistedSessionAndSurvivesRefresh() = runBlocking {
        seedExercises()
        seedPlan(
            planId = "plan-library",
            scheduledDate = "2026-07-11",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 2, targetWeightKg = 70.0)),
        )

        val planned = repository.addExerciseToPlan("plan-library", "2330")
        val session = repository.startWorkout("plan-library")
        val added = repository.addExerciseToSession(
            sessionId = session.id,
            exerciseId = "0289",
            targetSets = 4,
            targetReps = "8-10",
            targetWeightKg = 22.0,
        )

        repository = FitnessRepository(
            context = context,
            store = store,
            credentialStore = credentialStore,
            timeProvider = timeProvider,
        )
        val refreshed = repository.appState().first()
        val persisted = refreshed.workoutSessionExercises.filter { it.sessionId == session.id }

        assertEquals(2, planned.orderIndex)
        assertEquals("2330", planned.exerciseId)
        assertEquals(listOf("0748", "2330", "0289"), persisted.map { it.exerciseId })
        assertEquals(3, added.orderIndex)
        assertEquals(4, added.targetSets)
        assertEquals("0289", refreshed.unfinishedSessions.single().currentExerciseId)
    }

    @Test
    fun fourWeekDraftConfirmationCreatesAllPlansOnceAndRejectsInvalidDrafts() = runBlocking {
        seedExercises()
        val draft = repository.generateWeeklyPlanDraft()
        store.upsertAiDraft(draft.copy(id = "wrong-type-draft", type = "food_estimate"))

        val wrongTypeError = runCatching {
            repository.confirmFourWeekPlanDraft("wrong-type-draft")
        }.exceptionOrNull()
        assertTrue(wrongTypeError is IllegalArgumentException)
        assertTrue(store.plannedWorkouts().isEmpty())

        val workouts = repository.confirmFourWeekPlanDraft(draft.id)

        assertEquals(
            listOf("2026-07-11", "2026-07-18", "2026-07-25", "2026-08-01"),
            workouts.map { it.scheduledDate },
        )
        assertEquals(4, workouts.map { it.id }.distinct().size)
        workouts.forEach { workout ->
            assertEquals(listOf("0748", "0289"), store.plannedExercises(workout.id).map { it.exerciseId })
        }
        assertEquals("confirmed", store.aiDraft(draft.id)?.status)

        val repeatedError = runCatching {
            repository.confirmFourWeekPlanDraft(draft.id)
        }.exceptionOrNull()
        assertTrue(repeatedError is IllegalArgumentException)
        assertEquals(4, store.plannedWorkouts().size)
    }

    @Test
    fun fourWeekDraftConfirmationRollsBackPlansAndDraftWhenAWriteFails() = runBlocking {
        seedExercises()
        val draft = repository.generateWeeklyPlanDraft()
        db.writableDatabase.execSQL(
            """
            CREATE TEMP TRIGGER fail_third_four_week_plan
            BEFORE INSERT ON planned_workout
            WHEN NEW.scheduled_date = '2026-07-25'
            BEGIN
                SELECT RAISE(ABORT, 'simulated interruption');
            END
            """.trimIndent(),
        )

        val error = runCatching {
            repository.confirmFourWeekPlanDraft(draft.id)
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(store.plannedWorkouts().isEmpty())
        assertEquals("draft", store.aiDraft(draft.id)?.status)
        assertNull(store.aiDraft(draft.id)?.confirmedAt)
    }

    @Test
    fun homeAndNutritionSummariesUsePersistedData() = runBlocking {
        store.upsertUserProfile(
            UserProfileEntity(
                id = "profile-local",
                displayName = "山崎",
                birthYear = 1994,
                heightCm = 176.0,
                weightKg = 76.0,
                goal = "增肌",
                injuries = "",
                weeklyTrainingDays = 4,
                preferredMinutes = 50,
                updatedAt = timeProvider.currentTimeMillis(),
            ),
        )
        seedPlan(
            planId = "plan-completed",
            scheduledDate = "2026-07-10",
            status = "completed",
            exercises = emptyList(),
        )
        seedPlan(
            planId = "plan-next",
            scheduledDate = "2026-07-11",
            exercises = emptyList(),
        )
        store.upsertWorkoutSession(
            WorkoutSessionEntity(
                id = "session-completed",
                plannedWorkoutId = "plan-completed",
                venueId = "venue-1",
                exerciseId = "0748",
                status = "completed",
                startedAt = timeProvider.currentTimeMillis() - 3_600_000L,
                endedAt = timeProvider.currentTimeMillis(),
                updatedAt = timeProvider.currentTimeMillis(),
            ),
        )
        store.insertFoodLog(foodLog("food-1", "2026-07-10", 620, 42.0, 68.0, 16.0, confirmed = true))
        store.insertFoodLog(foodLog("food-2", "2026-07-10", 380, 24.0, 28.0, 18.0, confirmed = true))
        store.insertFoodLog(foodLog("food-draft", "2026-07-10", 999, 99.0, 99.0, 99.0, confirmed = false))
        store.insertFoodLog(foodLog("food-other-day", "2026-07-09", 500, 20.0, 30.0, 10.0, confirmed = true))

        val state = repository.appState().first()
        val home = repository.homeSnapshot(state, today = LocalDate.of(2026, 7, 10))
        val nutrition = repository.nutritionSummary(state, date = LocalDate.of(2026, 7, 10))

        assertEquals(HomePrimaryAction.Result("session-completed"), home.action)
        assertEquals(1, home.completedThisWeek)
        assertEquals(4, home.targetThisWeek)
        assertEquals("plan-next", home.nextWorkout?.id)
        assertEquals(1_000, nutrition.calories)
        assertEquals(66.0, nutrition.protein, 0.01)
        assertEquals(96.0, nutrition.carbs, 0.01)
        assertEquals(34.0, nutrition.fat, 0.01)
        requireNotNull(nutrition.reference).also { reference ->
            assertEquals(2_508, reference.calories)
            assertEquals(136.8, reference.protein, 0.01)
            assertEquals(304.0, reference.carbs, 0.01)
            assertEquals(60.8, reference.fat, 0.01)
        }
        Unit
    }

    @Test
    fun resetClearsPersonalDataAndCredentialsWithoutCreatingFakeSession() = runBlocking {
        repository.bootstrap()
        repository.saveUserProfile(
            displayName = "山崎",
            birthYear = 1994,
            heightCm = 176.0,
            weightKg = 76.0,
            goal = "增肌",
            injuries = "",
            weeklyTrainingDays = 4,
            preferredMinutes = 50,
        )
        repository.saveAiApiKey(FitnessRepository.DEEPSEEK_PROVIDER_ID, "sk-reset-test")
        seedExercises()
        seedPlan(
            planId = "plan-reset",
            scheduledDate = "2026-07-10",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 1, targetWeightKg = 70.0)),
        )
        repository.startWorkout("plan-reset")
        assertNotNull(credentialStore.loadApiKey(FitnessRepository.DEEPSEEK_PROVIDER_ID))
        assertEquals(1, store.workoutSessions().size)

        repository.resetLocalData()
        val state = repository.appState().first()

        assertNull(credentialStore.loadApiKey(FitnessRepository.DEEPSEEK_PROVIDER_ID))
        assertNull(state.userProfile)
        assertTrue(state.foodLogs.isEmpty())
        assertTrue(state.workoutSessions.isEmpty())
        assertTrue(state.workoutSessionExercises.isEmpty())
        assertTrue(state.workoutSetLogs.isEmpty())
        assertTrue(state.unfinishedSessions.isEmpty())
        assertTrue(state.venues.isNotEmpty())
        assertTrue(state.equipment.isNotEmpty())
        assertTrue(state.plannedWorkouts.isEmpty())
        assertEquals(listOf(FitnessRepository.DEEPSEEK_PROVIDER_ID), state.aiProviders.map { it.id })
        assertFalse(state.aiProviders.single().apiKeyStored)
    }

    @Test
    fun importedProviderFlagCannotClaimAConnectionWithoutTheLocalKeystoreKey() = runBlocking {
        val rawBackup = providerFlagBackup(apiKeyStored = true)

        repository.importBackupJson(rawBackup)

        assertTrue(store.aiProvider(FitnessRepository.DEEPSEEK_PROVIDER_ID)?.apiKeyStored == true)
        assertNull(credentialStore.loadApiKey(FitnessRepository.DEEPSEEK_PROVIDER_ID))
        assertFalse(
            repository.appState().first().aiProviders
                .single { it.id == FitnessRepository.DEEPSEEK_PROVIDER_ID }
                .apiKeyStored,
        )
    }

    @Test
    fun foodDraftConfirmationCreatesOneLogAndRejectsRepeatConfirmation() = runBlocking {
        val draft = repository.generateFoodEstimateDraft("鸡胸肉米饭")

        val foodLog = repository.confirmFoodEstimateDraft(draft.id)
        val repeatedError = runCatching {
            repository.confirmFoodEstimateDraft(draft.id)
        }.exceptionOrNull()

        assertEquals(foodLog.id, store.foodLogs().single().id)
        assertTrue(repeatedError is IllegalArgumentException)
        assertEquals("confirmed", store.aiDraft(draft.id)?.status)
    }

    @Test
    fun foodDraftConfirmationRollsBackLogWhenDraftStatusWriteFails() = runBlocking {
        val draft = repository.generateFoodEstimateDraft("鸡胸肉米饭")
        db.writableDatabase.execSQL(
            """
            CREATE TEMP TRIGGER fail_food_draft_status_update
            BEFORE UPDATE OF status ON ai_draft
            WHEN NEW.id = '${draft.id}' AND NEW.status = 'confirmed'
            BEGIN
                SELECT RAISE(ABORT, 'simulated interruption');
            END
            """.trimIndent(),
        )

        val error = runCatching {
            repository.confirmFoodEstimateDraft(draft.id)
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(store.foodLogs().isEmpty())
        assertEquals("draft", store.aiDraft(draft.id)?.status)
        assertNull(store.aiDraft(draft.id)?.confirmedAt)
    }

    @Test
    fun backupV1StillImportsAndV3RoundTripsRuntime() = runBlocking {
        val v1 =
            """
            {
              "version": 1,
              "exportedAt": 1000,
              "userProfile": null,
              "venues": [],
              "equipment": [],
              "plannedWorkouts": [],
              "plannedExercises": [],
              "workoutSessions": [{
                "id": "legacy-session",
                "plannedWorkoutId": null,
                "venueId": "legacy-venue",
                "exerciseId": "0748",
                "status": "completed",
                "startedAt": 1000,
                "endedAt": 2000,
                "updatedAt": 2000
              }],
              "setLogs": [{
                "id": "legacy-set",
                "sessionId": "legacy-session",
                "exerciseId": "0748",
                "setIndex": 1,
                "actualReps": 8,
                "actualWeightKg": 70.0,
                "feeling": "合适",
                "completed": true,
                "completedAt": 1500
              }],
              "foodLogs": [],
              "aiDrafts": [],
              "aiProviders": []
            }
            """.trimIndent()

        repository.importBackupJson(v1)
        assertNull(store.workoutSession("legacy-session")?.currentExerciseId)
        assertNull(store.workoutSession("legacy-session")?.restEndsAt)
        assertNull(store.setLogs("legacy-session").single().sessionExerciseId)
        assertTrue(store.sessionExercises("legacy-session").isEmpty())

        store.clearPersonalData()
        seedExercises()
        seedPlan(
            planId = "plan-v2",
            scheduledDate = "2026-07-10",
            exercises = listOf(
                PlannedExerciseSeed("0748", targetSets = 2, targetWeightKg = 70.0),
                PlannedExerciseSeed("0289", targetSets = 1, targetWeightKg = 24.0),
            ),
        )
        val session = repository.startWorkout("plan-v2")
        repository.recordWorkoutSet(
            sessionId = session.id,
            exerciseId = "0748",
            reps = 8,
            weightKg = 70.0,
            feeling = "合适",
            restSeconds = 75,
        )
        repository.selectWorkoutExercise(session.id, "0289")

        val exported = repository.exportBackupJson()
        val payload = FitnessBackupCodec.decode(exported)
        assertEquals(3, payload.version)
        assertEquals(2, payload.sessionExercises.size)
        assertEquals("0289", payload.workoutSessions.single().currentExerciseId)
        assertEquals(timeProvider.currentTimeMillis() + 75_000L, payload.workoutSessions.single().restEndsAt)
        assertNotNull(payload.setLogs.single().sessionExerciseId)

        repository.importBackupJson(exported)

        val restoredSession = store.workoutSession(session.id)
        assertEquals("0289", restoredSession?.currentExerciseId)
        assertEquals(timeProvider.currentTimeMillis() + 75_000L, restoredSession?.restEndsAt)
        assertEquals(listOf("0748", "0289"), store.sessionExercises(session.id).map { it.exerciseId })
        assertEquals(store.sessionExercises(session.id).first().id, store.setLogs(session.id).single().sessionExerciseId)
    }

    @Test
    fun legacyCompatibilityApisShareOneLinkedSessionAcrossExercises() = runBlocking {
        seedExercises()
        seedPlan(
            planId = "plan-legacy",
            scheduledDate = "2026-07-10",
            exercises = listOf(
                PlannedExerciseSeed("0748", targetSets = 1, targetWeightKg = 70.0),
                PlannedExerciseSeed("0289", targetSets = 1, targetWeightKg = 24.0),
            ),
        )

        val firstSessionId = repository.sessionIdFor("plan-legacy", "0748")
        val secondSessionId = repository.sessionIdFor("plan-legacy", "0289")
        assertEquals(firstSessionId, secondSessionId)

        repository.completeSet(
            sessionId = firstSessionId,
            exerciseId = "0748",
            setIndex = 1,
            reps = 8,
            weightKg = 70.0,
            feeling = "合适",
        )
        repository.completeSet(
            sessionId = secondSessionId,
            exerciseId = "0289",
            setIndex = 1,
            reps = 10,
            weightKg = 24.0,
            feeling = "轻松",
        )
        repository.finishWorkoutSession(
            sessionId = secondSessionId,
            plannedWorkoutId = "plan-legacy",
            venueId = "venue-1",
            exerciseId = "0289",
        )

        val session = store.workoutSessions().single()
        val logs = store.setLogs(session.id)
        assertEquals("completed", session.status)
        assertEquals(listOf("0748", "0289"), store.sessionExercises(session.id).map { it.exerciseId })
        assertEquals(2, logs.size)
        assertTrue(logs.all { it.sessionId == session.id })
        assertTrue(logs.all { it.sessionExerciseId != null })
    }

    @Test
    fun legacySkipExerciseUsesPlanSessionAndLinkedSnapshot() = runBlocking {
        seedExercises()
        seedPlan(
            planId = "plan-legacy-skip",
            scheduledDate = "2026-07-10",
            exercises = listOf(
                PlannedExerciseSeed("0748", targetSets = 1, targetWeightKg = 70.0),
                PlannedExerciseSeed("0289", targetSets = 1, targetWeightKg = 24.0),
            ),
        )
        val sessionId = repository.sessionIdFor("plan-legacy-skip", "0748")

        repository.completeSet(
            sessionId = sessionId,
            exerciseId = "0748",
            setIndex = 1,
            reps = 8,
            weightKg = 70.0,
            feeling = "合适",
        )
        repository.skipExercise(
            sessionId = repository.sessionIdFor("plan-legacy-skip", "0289"),
            plannedWorkoutId = "plan-legacy-skip",
            venueId = "venue-1",
            exerciseId = "0289",
            setIndex = 1,
            reason = "器械被占用",
        )

        val session = store.workoutSessions().single()
        val sessionExercises = store.sessionExercises(session.id)
        val skippedExercise = sessionExercises.single { it.exerciseId == "0289" }
        val skippedLog = store.setLogs(session.id, "0289").single()
        assertEquals(sessionId, session.id)
        assertEquals("0289", session.currentExerciseId)
        assertEquals(listOf("0748", "0289"), sessionExercises.map { it.exerciseId })
        assertEquals("skipped", skippedExercise.status)
        assertFalse(skippedLog.completed)
        assertEquals(skippedExercise.id, skippedLog.sessionExerciseId)
        assertEquals(2, store.setLogs(session.id).size)
    }

    @Test
    fun concurrentWorkoutStartsReturnOneActiveSession() = runBlocking {
        seedExercises()
        seedPlan(
            planId = "plan-concurrent-start",
            scheduledDate = "2026-07-10",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 2, targetWeightKg = 70.0)),
        )

        val gate = CompletableDeferred<Unit>()
        val results = coroutineScope {
            List(16) {
                async(Dispatchers.IO) {
                    gate.await()
                    repository.startWorkout("plan-concurrent-start")
                }
            }.also { gate.complete(Unit) }.awaitAll()
        }

        assertEquals(1, results.map { it.id }.distinct().size)
        assertEquals(1, store.workoutSessions().count { it.plannedWorkoutId == "plan-concurrent-start" })
        assertEquals(1, store.sessionExercises(results.first().id).size)
    }

    @Test
    fun concurrentDuplicateSetTapsPersistExactlyOneSet() = runBlocking {
        seedExercises()
        seedPlan(
            planId = "plan-concurrent-record",
            scheduledDate = "2026-07-10",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 1, targetWeightKg = 70.0)),
        )
        val session = repository.startWorkout("plan-concurrent-record")
        val gate = CompletableDeferred<Unit>()

        val results = coroutineScope {
            List(12) {
                async(Dispatchers.IO) {
                    gate.await()
                    runCatching {
                        repository.recordWorkoutSet(
                            sessionId = session.id,
                            exerciseId = "0748",
                            reps = 8,
                            weightKg = 70.0,
                            feeling = "合适",
                        )
                    }
                }
            }.also { gate.complete(Unit) }.awaitAll()
        }

        assertEquals(1, results.count { it.isSuccess })
        assertEquals(11, results.count { it.isFailure })
        assertEquals(1, store.setLogs(session.id, "0748").size)
        assertEquals(1, store.setLogs(session.id, "0748").single().setIndex)
    }

    @Test
    fun failedV2ImportLeavesExistingLocalDataUntouched() = runBlocking {
        seedExercises()
        seedPlan(
            planId = "plan-import-atomic",
            scheduledDate = "2026-07-10",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 2, targetWeightKg = 70.0)),
        )
        repository.saveUserProfile(
            displayName = "导入前用户",
            birthYear = 1994,
            heightCm = 176.0,
            weightKg = 76.0,
            goal = "增肌",
            injuries = "",
            weeklyTrainingDays = 4,
            preferredMinutes = 50,
        )
        val session = repository.startWorkout("plan-import-atomic")
        repository.recordWorkoutSet(
            sessionId = session.id,
            exerciseId = "0748",
            reps = 8,
            weightKg = 70.0,
            feeling = "合适",
        )
        val payload = FitnessBackupCodec.decode(repository.exportBackupJson())
        val originalLog = payload.setLogs.single()
        val invalidPayload = payload.copy(
            userProfile = payload.userProfile?.copy(displayName = "不应写入"),
            setLogs = payload.setLogs + originalLog.copy(id = "duplicate-linked-set"),
        )

        val failure = runCatching {
            repository.importBackupJson(FitnessBackupCodec.encode(invalidPayload))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals("导入前用户", store.userProfile()?.displayName)
        assertEquals(listOf(session.id), store.workoutSessions().map { it.id })
        assertEquals(listOf(originalLog.id), store.setLogs(session.id).map { it.id })
    }

    @Test
    fun recordWorkoutSetRejectsBlankFeeling() = runBlocking {
        seedExercises()
        seedPlan(
            planId = "plan-blank-feeling",
            scheduledDate = "2026-07-10",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 1, targetWeightKg = 70.0)),
        )
        val session = repository.startWorkout("plan-blank-feeling")

        val failure = runCatching {
            repository.recordWorkoutSet(
                sessionId = session.id,
                exerciseId = "0748",
                reps = 8,
                weightKg = 70.0,
                feeling = "   ",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(store.setLogs(session.id).isEmpty())
    }

    @Test
    fun startWorkoutRejectsSkippedAndCompletedPlansWithoutActiveSessions() = runBlocking {
        seedExercises()
        seedPlan(
            planId = "plan-skipped",
            scheduledDate = "2026-07-10",
            status = "skipped",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 1, targetWeightKg = 70.0)),
        )
        seedPlan(
            planId = "plan-completed-state",
            scheduledDate = "2026-07-10",
            status = "completed",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 1, targetWeightKg = 70.0)),
        )

        val skippedFailure = runCatching { repository.startWorkout("plan-skipped") }.exceptionOrNull()
        val completedFailure = runCatching { repository.startWorkout("plan-completed-state") }.exceptionOrNull()

        assertTrue(skippedFailure is IllegalArgumentException)
        assertTrue(completedFailure is IllegalArgumentException)
        assertTrue(store.workoutSessions().isEmpty())
    }

    @Test
    fun repositorySavesPlanEditorChangesAndRefreshesState() = runBlocking {
        store.upsertPlannedWorkout(
            PlannedWorkoutEntity(
                id = "plan-1",
                name = "胸部力量 A",
                scheduledDate = "2026-07-09",
                venueId = "venue-1",
                status = "planned",
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )
        store.upsertPlannedExercise(
            PlannedExerciseEntity(
                id = "plan-1-exercise-1",
                plannedWorkoutId = "plan-1",
                exerciseId = "0748",
                orderIndex = 1,
                targetSets = 4,
                targetReps = "8-12",
                targetWeightKg = 70.0,
                note = "主项",
            ),
        )

        repository.updatePlannedWorkoutDetails(
            id = "plan-1",
            name = "胸背力量 A",
            scheduledDate = "2026-07-10",
        )
        repository.updatePlannedExerciseTarget(
            id = "plan-1-exercise-1",
            targetSets = 5,
            targetReps = "6-8",
            targetWeightKg = 82.5,
            note = "加重",
        )

        val state = repository.appState().first()
        val workout = state.plannedWorkouts.single()
        val exercise = state.plannedExercises.single()

        assertEquals("胸背力量 A", workout.name)
        assertEquals("2026-07-10", workout.scheduledDate)
        assertEquals(5, exercise.targetSets)
        assertEquals("6-8", exercise.targetReps)
        assertEquals(82.5, exercise.targetWeightKg, 0.01)
        assertEquals("加重", exercise.note)
    }

    @Test
    fun repositorySkipsExerciseAndFinishesWorkoutSession() = runBlocking {
        store.upsertPlannedWorkout(
            PlannedWorkoutEntity(
                id = "plan-1",
                name = "胸部力量 A",
                scheduledDate = "2026-07-09",
                venueId = "venue-1",
                status = "planned",
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )

        repository.skipExercise(
            sessionId = "session-plan-1-0748",
            plannedWorkoutId = "plan-1",
            venueId = "venue-1",
            exerciseId = "0748",
            setIndex = 1,
            reason = "器械被占用",
        )

        val skippedLog = store.setLogs("session-plan-1-0748", "0748").single()
        val createdSession = store.workoutSessions().single()

        assertFalse(skippedLog.completed)
        assertEquals(0, skippedLog.actualReps)
        assertEquals(0.0, skippedLog.actualWeightKg, 0.01)
        assertEquals("跳过：器械被占用", skippedLog.feeling)
        assertEquals("in_progress", createdSession.status)
        assertEquals("plan-1", createdSession.plannedWorkoutId)

        repository.finishWorkoutSession(
            sessionId = "session-plan-1-0748",
            plannedWorkoutId = "plan-1",
            venueId = "venue-1",
            exerciseId = "0748",
        )

        val finishedSession = store.workoutSessions().single()
        val plan = store.plannedWorkouts().single()

        assertEquals("partial", finishedSession.status)
        assertNotNull(finishedSession.endedAt)
        assertEquals("planned", plan.status)
    }

    @Test
    fun repositoryManagesProfileFoodDraftAndBackupRoundTrip() = runBlocking {
        repository.saveUserProfile(
            displayName = "山崎",
            birthYear = 1994,
            heightCm = 176.0,
            weightKg = 76.5,
            goal = "增肌减脂",
            injuries = "肩峰撞击",
            weeklyTrainingDays = 4,
            preferredMinutes = 50,
        )
        val draft = repository.generateFoodEstimateDraft("一碗米饭，一块鸡胸肉，一份青菜")
        repository.confirmFoodEstimateDraft(draft.id)

        val exported = repository.exportBackupJson()

        store.clearPersonalData()
        assertEquals(null, store.userProfile())
        assertEquals(emptyList<String>(), store.foodLogs().map { it.name })

        repository.importBackupJson(exported)

        val state = repository.appState().first()

        assertEquals("山崎", state.userProfile?.displayName)
        assertEquals(4, state.userProfile?.weeklyTrainingDays)
        assertEquals(listOf("鸡胸肉米饭"), state.foodLogs.map { it.name })
        assertEquals("confirmed", state.aiDrafts.single { it.id == draft.id }.status)
    }

    @Test
    fun repositoryCreatesCopiesSkipsDeletesAndReplacesWorkoutPlans() = runBlocking {
        store.upsertExerciseMedia(
            listOf(
                ExerciseMediaEntity(
                    exerciseId = "0748",
                    name = "smith bench press",
                    bodyPart = "chest",
                    equipment = "smith machine",
                    target = "pectorals",
                    mediaId = "a",
                    localPath = "exercise-media/gifs/0748-a.gif",
                    assetPackId = "pack",
                    bytes = 1L,
                    sha256 = "a",
                ),
                ExerciseMediaEntity(
                    exerciseId = "0289",
                    name = "dumbbell bench press",
                    bodyPart = "chest",
                    equipment = "dumbbell",
                    target = "pectorals",
                    mediaId = "b",
                    localPath = "exercise-media/gifs/0289-b.gif",
                    assetPackId = "pack",
                    bytes = 1L,
                    sha256 = "b",
                ),
            ),
        )

        val created = repository.createWorkoutFromTemplate(
            name = "胸部力量 B",
            scheduledDate = "2026-07-11",
            venueId = "venue-1",
        )
        val copied = repository.copyWorkout(created.id, newScheduledDate = "2026-07-18")

        repository.rescheduleWorkout(created.id, "2026-07-12")
        repository.skipWorkout(created.id)
        repository.replaceExercise(
            plannedExerciseId = store.plannedExercises(created.id).first().id,
            replacementExerciseId = "0289",
            note = "替换：史密斯机被占用",
        )

        assertEquals("skipped", store.plannedWorkouts().first { it.id == created.id }.status)
        assertEquals("2026-07-12", store.plannedWorkouts().first { it.id == created.id }.scheduledDate)
        assertEquals("0289", store.plannedExercises(created.id).first().exerciseId)
        assertEquals(2, store.plannedExercises(copied.id).size)

        repository.deleteWorkout(created.id)

        assertFalse(store.plannedWorkouts().any { it.id == created.id })
        assertEquals(emptyList<PlannedExerciseEntity>(), store.plannedExercises(created.id))
    }

    @Test
    fun repositoryCompletesRemainingLocalWorkflows() = runBlocking {
        store.upsertVenue(
            TrainingVenueEntity(
                id = "venue-1",
                name = "公司健身房",
                isDefault = true,
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )
        store.upsertEquipment(
            EquipmentEntity(
                id = "equipment-smith",
                name = "史密斯机",
                category = "machine",
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )
        store.upsertExerciseMedia(
            listOf(
                ExerciseMediaEntity(
                    exerciseId = "0748",
                    name = "smith bench press",
                    bodyPart = "chest",
                    equipment = "smith machine",
                    target = "pectorals",
                    mediaId = "a",
                    localPath = "exercise-media/gifs/0748-a.gif",
                    assetPackId = "pack",
                    bytes = 1L,
                    sha256 = "a",
                ),
                ExerciseMediaEntity(
                    exerciseId = "0289",
                    name = "dumbbell bench press",
                    bodyPart = "chest",
                    equipment = "dumbbell",
                    target = "pectorals",
                    mediaId = "b",
                    localPath = "exercise-media/gifs/0289-b.gif",
                    assetPackId = "pack",
                    bytes = 1L,
                    sha256 = "b",
                ),
                ExerciseMediaEntity(
                    exerciseId = "0770",
                    name = "smith chair squat",
                    bodyPart = "upper legs",
                    equipment = "smith machine",
                    target = "quads",
                    mediaId = "c",
                    localPath = "exercise-media/gifs/0770-c.gif",
                    assetPackId = "pack",
                    bytes = 1L,
                    sha256 = "c",
                ),
                ExerciseMediaEntity(
                    exerciseId = "2330",
                    name = "cable row",
                    bodyPart = "back",
                    equipment = "cable",
                    target = "lats",
                    mediaId = "d",
                    localPath = "exercise-media/gifs/2330-d.gif",
                    assetPackId = "pack",
                    bytes = 1L,
                    sha256 = "d",
                ),
            ),
        )

        repository.bindEquipmentToVenue("venue-1", "equipment-smith", available = true)
        repository.setOnboardingCompleted(true)
        val monthlyWorkouts = repository.createMonthlyPlanFromTemplate(
            startDate = "2026-07-13",
            venueId = "venue-1",
        )
        val searched = repository.searchExercises("bench", limit = 10)
        repository.completeSet(
            sessionId = "session-plan-0748",
            exerciseId = "0748",
            setIndex = 1,
            reps = 12,
            weightKg = 70.0,
            feeling = "轻松",
        )
        val adjustment = repository.generateTrainingAdjustment("0748")
        val foodDraft = repository.generateFoodEstimateDraft(
            description = "鸡胸饭，训练后晚餐",
            imageUri = "content://meal/1",
            imageMimeType = "image/jpeg",
            imageBase64 = "abc123",
        )
        val foodLog = repository.confirmFoodEstimateDraft(foodDraft.id)
        val exported = repository.exportBackupJson()

        store.clearPersonalData()
        repository.importBackupJson(exported)
        val state = repository.appState().first()

        assertEquals(4, monthlyWorkouts.size)
        assertEquals(listOf("2026-07-13", "2026-07-20", "2026-07-27", "2026-08-03"), monthlyWorkouts.map { it.scheduledDate })
        assertEquals(listOf("0289", "0748"), searched.map { it.exerciseId }.sorted())
        assertEquals("下次加重", adjustment.title)
        assertEquals("content://meal/1", foodLog.imageUri)
        assertEquals("deepseek-v4-flash", foodLog.model)
        assertTrue(state.onboardingCompleted)
        assertEquals(listOf("史密斯机"), state.equipmentForSelectedVenue.map { it.name })
        assertEquals("下次加重", state.trainingAdjustments.single().title)
        assertEquals(4, state.plannedWorkouts.count { it.name.startsWith("月计划") })
    }

    private fun seedExercises() {
        store.upsertExerciseMedia(
            listOf(
                exercise("0748", "smith bench press", "smith machine"),
                exercise("0289", "dumbbell bench press", "dumbbell"),
                exercise("2330", "cable row", "cable"),
            ),
        )
    }

    private fun seedPlan(
        planId: String,
        scheduledDate: String,
        status: String = "planned",
        exercises: List<PlannedExerciseSeed>,
    ) {
        store.upsertPlannedWorkout(
            PlannedWorkoutEntity(
                id = planId,
                name = "测试训练 $planId",
                scheduledDate = scheduledDate,
                venueId = "venue-1",
                status = status,
                createdAt = timeProvider.currentTimeMillis(),
                updatedAt = timeProvider.currentTimeMillis(),
            ),
        )
        exercises.forEachIndexed { index, seed ->
            store.upsertPlannedExercise(
                PlannedExerciseEntity(
                    id = "$planId-${seed.exerciseId}",
                    plannedWorkoutId = planId,
                    exerciseId = seed.exerciseId,
                    orderIndex = index + 1,
                    targetSets = seed.targetSets,
                    targetReps = seed.targetReps,
                    targetWeightKg = seed.targetWeightKg,
                    note = "测试动作",
                ),
            )
        }
    }

    private fun exercise(id: String, name: String, equipment: String): ExerciseMediaEntity =
        ExerciseMediaEntity(
            exerciseId = id,
            name = name,
            bodyPart = "chest",
            equipment = equipment,
            target = "target",
            mediaId = "media-$id",
            localPath = "exercise-media/gifs/$id.gif",
            assetPackId = "test-pack",
            bytes = 1L,
            sha256 = "sha-$id",
        )

    private fun providerFlagBackup(apiKeyStored: Boolean): String =
        """
        {
          "version": 2,
          "exportedAt": 1000,
          "userProfile": null,
          "venues": [],
          "equipment": [],
          "plannedWorkouts": [],
          "plannedExercises": [],
          "workoutSessions": [],
          "setLogs": [],
          "foodLogs": [],
          "aiDrafts": [],
          "aiProviders": [{
            "id": "${FitnessRepository.DEEPSEEK_PROVIDER_ID}",
            "displayName": "DeepSeek",
            "baseUrl": "https://api.deepseek.com",
            "model": "deepseek-v4-flash",
            "enabled": true,
            "apiKeyStored": $apiKeyStored,
            "updatedAt": 1000
          }]
        }
        """.trimIndent()

    private fun foodLog(
        id: String,
        date: String,
        calories: Int,
        protein: Double,
        carbs: Double,
        fat: Double,
        confirmed: Boolean,
    ): FoodLogEntity =
        FoodLogEntity(
            id = id,
            loggedDate = date,
            name = id,
            calories = calories,
            proteinGrams = protein,
            carbsGrams = carbs,
            fatGrams = fat,
            source = "manual",
            imageNote = "",
            confirmed = confirmed,
            createdAt = timeProvider.currentTimeMillis(),
        )

    private companion object {
        const val DB_NAME = "fitness-repository-test.db"
        const val CREDENTIAL_PREFERENCES = "fitness-repository-test-credentials"
    }
}

private data class PlannedExerciseSeed(
    val exerciseId: String,
    val targetSets: Int,
    val targetReps: String = "8-12",
    val targetWeightKg: Double,
)

private class FakeTimeProvider(initialMillis: Long) : TimeProvider {
    private var now = initialMillis

    override fun currentTimeMillis(): Long = now

    fun advanceSeconds(seconds: Long) {
        now += seconds * 1_000L
    }
}
