package com.shanqijie.fitnessapp

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.shanqijie.fitnessapp.data.AiCredentialStore
import com.shanqijie.fitnessapp.data.AiDraftEntity
import com.shanqijie.fitnessapp.data.ActionPreferenceEntity
import com.shanqijie.fitnessapp.ai.AiGateway
import com.shanqijie.fitnessapp.ai.AiGatewayFactory
import com.shanqijie.fitnessapp.ai.AiTestResult
import com.shanqijie.fitnessapp.data.BodyMeasurement
import com.shanqijie.fitnessapp.data.EquipmentEntity
import com.shanqijie.fitnessapp.data.FitnessBackupCodec
import com.shanqijie.fitnessapp.data.FitnessBackupPayload
import com.shanqijie.fitnessapp.data.FitnessDatabase
import com.shanqijie.fitnessapp.data.FitnessRepository
import com.shanqijie.fitnessapp.data.FitnessStore
import com.shanqijie.fitnessapp.data.ExerciseMediaEntity
import com.shanqijie.fitnessapp.data.FoodLogEntity
import com.shanqijie.fitnessapp.data.InjuryFilterOverrideEntity
import com.shanqijie.fitnessapp.data.PlanCycleEntity
import com.shanqijie.fitnessapp.data.PlanScheduleDayEntity
import com.shanqijie.fitnessapp.data.PlannedExerciseEntity
import com.shanqijie.fitnessapp.data.PlannedWorkoutEntity
import com.shanqijie.fitnessapp.data.TimeProvider
import com.shanqijie.fitnessapp.data.TrainingVenueEntity
import com.shanqijie.fitnessapp.data.UserProfileEntity
import com.shanqijie.fitnessapp.data.VenueEquipmentLoadEntity
import com.shanqijie.fitnessapp.data.WeeklyPlanDraftEntity
import com.shanqijie.fitnessapp.data.WorkoutSessionEntity
import com.shanqijie.fitnessapp.data.WorkoutSetLogEntity
import com.shanqijie.fitnessapp.domain.HomePrimaryAction
import com.shanqijie.fitnessapp.domain.WorkoutAdjustmentDirection
import com.shanqijie.fitnessapp.domain.WorkoutReviewMetadata
import com.shanqijie.fitnessapp.domain.workoutReviewMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
import java.lang.reflect.InvocationTargetException
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
        credentialStore.deleteApiKey(FitnessRepository.OPENAI_PROVIDER_ID)
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
        credentialStore.deleteApiKey(FitnessRepository.OPENAI_PROVIDER_ID)
        context.getSharedPreferences(CREDENTIAL_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        db.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun freshBootstrapDoesNotCreateAnUnfinishedSession() = runBlocking {
        val defaultTemplates = FitnessRepository::class.java.declaredMethods
            .single { it.name == "defaultTemplateExercises" }
            .apply { isAccessible = true }
        assertTrue((defaultTemplates.invoke(repository) as List<*>).isEmpty())
        store.upsertExerciseMedia(
            listOf(
                ExerciseMediaEntity("fallback-1", "动作一", "chest", "smith machine", "chest", "m1", "one.gif", "pack", 1, "a"),
                ExerciseMediaEntity("fallback-2", "动作二", "chest", "smith machine", "chest", "m2", "two.gif", "pack", 1, "b"),
                ExerciseMediaEntity("fallback-3", "动作三", "chest", "smith machine", "chest", "m3", "three.gif", "pack", 1, "c"),
            ),
        )
        assertEquals(2, (defaultTemplates.invoke(repository) as List<*>).size)
        repository.bootstrap()

        val state = repository.appState().first()

        assertTrue(state.workoutSessions.isEmpty())
        assertTrue(state.unfinishedSessions.isEmpty())
    }

    @Test
    fun bootstrapOffersCompleteEquipmentCatalogAndPreservesVenueChoices() = runBlocking {
        repository.bootstrap()
        val initial = repository.appState().first()

        assertTrue(initial.equipment.size >= 60)
        assertTrue(initial.equipment.any { it.name == "高位下拉机" && it.category == "machine" })
        assertTrue(initial.equipment.any { it.name == "训练轮胎" && it.category == "free-weight" })
        assertTrue(initial.equipment.any { it.name == "悬挂训练带" && it.category == "accessory" })
        assertTrue(initial.equipment.any { it.name == "上肢测功仪" && it.category == "cardio" })
        assertTrue(initial.equipment.any { it.name == "攀爬绳" && it.category == "body-weight" })
        assertEquals(
            setOf("史密斯机", "哑铃", "杠铃", "跑步机"),
            initial.equipmentForSelectedVenue.mapTo(mutableSetOf()) { it.name },
        )

        repository.bindEquipmentToVenue(
            venueId = initial.venue!!.id,
            equipmentId = "equipment-smith-machine",
            available = false,
        )
        repository.bindEquipmentToVenue(
            venueId = initial.venue.id,
            equipmentId = "equipment-cable",
            available = true,
        )
        repository.bootstrap()

        val restored = repository.appState().first()
        assertFalse(restored.equipmentForSelectedVenue.any { it.id == "equipment-smith-machine" })
        assertTrue(restored.equipmentForSelectedVenue.any { it.id == "equipment-cable" })
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

        assertTrue(draft.content.contains("体脂率：24.8%"))
        assertTrue(draft.content.contains("骨骼肌：32.5 kg"))
        assertTrue(draft.content.contains("基础代谢：1613 kcal"))
        assertFalse(draft.content.contains("身体年龄"))
        assertFalse(draft.content.contains("体型"))
    }

    @Test
    fun aiIntegrationCreatesPlanAndPhotoDraftsWithoutConfirmingProductData() = runBlocking {
        val gateway = RecordingAiGateway()
        val integratedRepository = FitnessRepository(
            context = context,
            store = store,
            credentialStore = credentialStore,
            timeProvider = timeProvider,
            aiGatewayFactory = AiGatewayFactory { gateway },
        )
        integratedRepository.bootstrap()
        integratedRepository.saveUserProfile(
            displayName = "山崎",
            birthYear = 1987,
            heightCm = 173.0,
            weightKg = 76.5,
            goal = "减脂",
            injuries = "无",
            weeklyTrainingDays = 3,
            preferredMinutes = 45,
            bodyMeasurement = BodyMeasurement(),
        )
        credentialStore.saveApiKey("qwen", "sk-integration-test")
        integratedRepository.selectAiProvider(
            providerId = "qwen",
            endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            model = "qwen3.7-plus",
        )

        assertTrue(integratedRepository.testAiProvider("qwen").success)
        val planDraft = integratedRepository.generateWeeklyPlanDraft()
        val photoDraft = integratedRepository.generateFoodEstimateDraft(
            description = "牛排米饭照片",
            imageUri = "content://test/food.jpg",
            imageMimeType = "image/jpeg",
            imageBase64 = "Zm9vZC1waG90bw==",
        )
        val beforeConfirmation = integratedRepository.appState().first()

        assertEquals("一周三练，力量优先", planDraft.content)
        assertTrue(photoDraft.content.contains("AI 建议：识别为牛排米饭"))
        assertTrue(gateway.lastVisionRequest?.contains("data:image/jpeg;base64,Zm9vZC1waG90bw==") == true)
        assertTrue(beforeConfirmation.foodLogs.isEmpty())
        assertEquals("draft", photoDraft.status)

        val confirmed = integratedRepository.confirmFoodEstimateDraft(photoDraft.id)
        val afterConfirmation = integratedRepository.appState().first()

        assertEquals("vision_ai", confirmed.source)
        assertEquals("qwen", confirmed.providerId)
        assertEquals(1, afterConfirmation.foodLogs.size)
        assertEquals("confirmed", afterConfirmation.aiDrafts.single { it.id == photoDraft.id }.status)
    }

    private class RecordingAiGateway : AiGateway {
        var lastVisionRequest: String? = null

        override fun testConnection(apiKey: String): AiTestResult =
            AiTestResult(success = true, message = "连接成功")

        override fun complete(
            apiKey: String,
            systemPrompt: String,
            userPrompt: String,
            temperature: Double,
        ): String = "一周三练，力量优先"

        override fun completeVision(
            apiKey: String,
            systemPrompt: String,
            userPrompt: String,
            imageMimeType: String,
            imageBase64: String,
            temperature: Double,
        ): String {
            lastVisionRequest = "data:$imageMimeType;base64,$imageBase64"
            return "识别为牛排米饭"
        }
    }

    @Test
    fun planPromptContainsAllFifteenProfileFieldsButNoAvatarOrBodyAge() {
        val profile = com.shanqijie.fitnessapp.data.UserProfileEntity(
            id = "profile-prompt",
            displayName = "山崎",
            birthYear = 1994,
            heightCm = 176.0,
            weightKg = 75.0,
            goal = "增肌",
            injuries = "右肩注意",
            weeklyTrainingDays = 4,
            preferredMinutes = 50,
            updatedAt = 1L,
            avatarPath = "avatars/private.jpg",
            bodyMeasurement = BodyMeasurement(
                bodyFatPercentage = 18.0,
                bodyFatMassKg = 13.5,
                bmi = 24.2,
                skeletalMuscleKg = 32.0,
                bodyWaterKg = 42.0,
                basalMetabolismKcal = 1680,
                waistHipRatio = 0.88,
                bodyAge = 39,
            ),
        )
        val prompt = repository.buildPlanPromptForTesting(profile)
        listOf("昵称：", "出生年：", "身高：", "体重：", "训练目标：", "每周训练：", "单次时长：", "伤病与注意事项：", "体脂率：", "体脂肪：", "BMI：", "骨骼肌：", "身体水分：", "基础代谢：", "腰臀比：").forEach {
            assertTrue("缺少 $it", prompt.contains(it))
        }
        assertFalse(prompt.contains("avatar"))
        assertFalse(prompt.contains("private.jpg"))
        assertFalse(prompt.contains("身体年龄"))
    }

    @Test
    fun backupRoundTripRestoresPrivateAvatarWithoutAffectingOtherProfileData() = runBlocking {
        seedProfile()
        val avatarDir = java.io.File(context.filesDir, "avatars").apply { mkdirs() }
        val avatar = java.io.File(avatarDir, "backup-source.jpg")
        val bitmap = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
        avatar.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        val profile = requireNotNull(store.userProfile())
        store.upsertUserProfile(profile.copy(avatarPath = "avatars/backup-source.jpg"))

        val exported = repository.exportBackupJson()
        assertFalse(exported.contains("bodyAge"))
        assertFalse(exported.contains("sk-"))
        repository.resetLocalData()
        assertFalse(avatar.exists())
        repository.importBackupJson(exported)

        val restored = requireNotNull(store.userProfile())
        assertEquals("测试用户", restored.displayName)
        assertTrue(restored.avatarPath.isNotBlank())
        assertTrue(java.io.File(context.filesDir, restored.avatarPath).isFile)
    }

    @Test
    fun profileAvatarIsSafelyCroppedScaledReplacedAndRequiresAProfile() = runBlocking {
        val withoutProfile = runCatching { repository.saveProfileAvatar(Uri.EMPTY) }.exceptionOrNull()
        assertTrue(withoutProfile is IllegalArgumentException)
        seedProfile()

        val firstUri = createMediaImage("avatar-first", width = 900, height = 600)
        val secondUri = createMediaImage("avatar-second", width = 640, height = 960)
        try {
            repository.saveProfileAvatar(firstUri)
            val firstProfile = requireNotNull(store.userProfile())
            val firstFile = context.filesDir.resolve(firstProfile.avatarPath)
            assertTrue(firstFile.isFile)
            BitmapFactory.decodeFile(firstFile.absolutePath).useBitmap { avatar ->
                assertEquals(512, avatar.width)
                assertEquals(512, avatar.height)
            }

            repository.saveProfileAvatar(secondUri)
            val secondProfile = requireNotNull(store.userProfile())
            val secondFile = context.filesDir.resolve(secondProfile.avatarPath)
            assertTrue(secondFile.isFile)
            assertFalse(firstFile.exists())
            assertTrue(firstProfile.avatarPath != secondProfile.avatarPath)
        } finally {
            context.contentResolver.delete(firstUri, null, null)
            context.contentResolver.delete(secondUri, null, null)
        }
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
        repository.extendRest(session.id)
        assertEquals(timeProvider.currentTimeMillis() + 60_000L, store.workoutSession(session.id)?.restEndsAt)
        repository.skipRest(session.id)
        val startedAtBeforePause = requireNotNull(store.workoutSession(session.id)).startedAt
        repository.toggleWorkoutPause(session.id)
        assertEquals(timeProvider.currentTimeMillis(), store.workoutSession(session.id)?.pausedAt)
        timeProvider.advanceSeconds(20)
        repository.toggleWorkoutPause(session.id)
        assertNull(store.workoutSession(session.id)?.pausedAt)
        assertEquals(startedAtBeforePause + 20_000L, store.workoutSession(session.id)?.startedAt)
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
        val repeatedSummary = repository.finishWorkout(session.id)
        val refreshedState = repository.appState().first()
        val home = repository.homeSnapshot(refreshedState, today = LocalDate.of(2026, 7, 10))

        assertEquals(1, store.workoutSessions().size)
        assertEquals(1, store.setLogs(session.id).size)
        assertEquals(session.id, store.setLogs(session.id).single().sessionId)
        assertNotNull(store.setLogs(session.id).single().sessionExerciseId)
        assertEquals("planned", store.plannedWorkouts().single { it.id == "plan-flow" }.status)
        assertEquals(1, summary.completedSets)
        assertEquals(summary, repeatedSummary)
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
        val repeatedSummary = repository.finishWorkout(session.id)
        val state = repository.appState().first()
        val home = repository.homeSnapshot(state, today = LocalDate.of(2026, 7, 10))

        assertTrue(summary.isFullyCompleted)
        assertEquals(summary, repeatedSummary)
        assertEquals("completed", store.workoutSession(session.id)?.status)
        assertEquals("completed", store.plannedWorkouts().single { it.id == "plan-complete" }.status)
        assertEquals(1, home.completedThisWeek)
        assertEquals(HomePrimaryAction.Result(session.id), home.action)
    }

    @Test
    fun workoutReviewUsesSetDetailsAndOnlyAdjustsFuturePlanAfterConfirmation() = runBlocking {
        seedExercises()
        seedPlan(
            planId = "plan-review-current",
            scheduledDate = "2026-07-10",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 1, targetWeightKg = 70.0)),
        )
        seedPlan(
            planId = "plan-review-future",
            scheduledDate = "2026-07-17",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 3, targetWeightKg = 70.0)),
        )
        val session = repository.startWorkout("plan-review-current")
        repository.recordWorkoutSet(
            sessionId = session.id,
            exerciseId = "0748",
            reps = 10,
            weightKg = 70.0,
            feeling = "轻松",
        )
        repository.finishWorkout(session.id)

        val draft = repository.generateWorkoutReviewDraft(
            sessionId = session.id,
            postWorkoutFeeling = "状态很好",
            postWorkoutNote = "动作稳定，恢复良好",
        )

        assertEquals("workout_review", draft.type)
        assertEquals(WorkoutAdjustmentDirection.INCREASE.name, draft.workoutReviewMetadata()?.direction)
        assertTrue(draft.content.contains("动作明细"))
        assertTrue(draft.content.contains("第1组 70kg × 10次（轻松）"))
        assertEquals(70.0, store.plannedExercises("plan-review-future").single().targetWeightKg, 0.01)

        repository.resolveWorkoutReviewDraft(draft.id, applyAdjustment = true)

        assertEquals(72.5, store.plannedExercises("plan-review-future").single().targetWeightKg, 0.01)
        assertEquals("confirmed", store.aiDraft(draft.id)?.status)
    }

    @Test
    fun fatiguedWorkoutCanReduceFutureLoadOrBeDismissedWithoutFurtherChanges() = runBlocking {
        seedExercises()
        seedPlan(
            planId = "plan-review-reduce-current",
            scheduledDate = "2026-07-10",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 2, targetWeightKg = 70.0)),
        )
        seedPlan(
            planId = "plan-review-reduce-future",
            scheduledDate = "2026-07-17",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 3, targetWeightKg = 70.0)),
        )
        val session = repository.startWorkout("plan-review-reduce-current")
        repository.recordWorkoutSet(
            sessionId = session.id,
            exerciseId = "0748",
            reps = 6,
            weightKg = 60.0,
            feeling = "吃力",
        )
        repository.finishWorkout(session.id)

        val appliedDraft = repository.generateWorkoutReviewDraft(
            sessionId = session.id,
            postWorkoutFeeling = "非常疲劳",
            postWorkoutNote = "最后一组动作变形",
        )
        assertEquals(WorkoutAdjustmentDirection.REDUCE.name, appliedDraft.workoutReviewMetadata()?.direction)
        assertEquals(70.0, store.plannedExercises("plan-review-reduce-future").single().targetWeightKg, 0.01)

        repository.resolveWorkoutReviewDraft(appliedDraft.id, applyAdjustment = true)
        assertEquals(67.5, store.plannedExercises("plan-review-reduce-future").single().targetWeightKg, 0.01)
        assertEquals("confirmed", store.aiDraft(appliedDraft.id)?.status)

        val dismissedDraft = repository.generateWorkoutReviewDraft(
            sessionId = session.id,
            postWorkoutFeeling = "疼痛不适",
            postWorkoutNote = "右肩不适",
        )
        repository.resolveWorkoutReviewDraft(dismissedDraft.id, applyAdjustment = false)
        assertEquals(67.5, store.plannedExercises("plan-review-reduce-future").single().targetWeightKg, 0.01)
        assertEquals("dismissed", store.aiDraft(dismissedDraft.id)?.status)
    }

    @Test
    fun workoutReviewCoversMaintainAndBodyweightAdjustmentRecommendations() = runBlocking {
        seedExercises()
        seedPlan(
            planId = "plan-bodyweight-increase-current",
            scheduledDate = "2026-07-10",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 1, targetWeightKg = 0.0)),
        )
        seedPlan(
            planId = "plan-bodyweight-increase-future",
            scheduledDate = "2026-07-17",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 3, targetWeightKg = 0.0)),
        )
        val increaseSession = repository.startWorkout("plan-bodyweight-increase-current")
        repository.recordWorkoutSet(increaseSession.id, "0748", 12, 0.0, "轻松")
        repository.finishWorkout(increaseSession.id)
        val increaseDraft = repository.generateWorkoutReviewDraft(increaseSession.id, "状态很好", "")
        repository.resolveWorkoutReviewDraft(increaseDraft.id, applyAdjustment = true)
        assertEquals(4, store.plannedExercises("plan-bodyweight-increase-future").single().targetSets)

        seedPlan(
            planId = "plan-bodyweight-reduce-current",
            scheduledDate = "2026-07-10",
            exercises = listOf(PlannedExerciseSeed("0289", targetSets = 2, targetWeightKg = 0.0)),
        )
        seedPlan(
            planId = "plan-bodyweight-reduce-future",
            scheduledDate = "2026-07-17",
            exercises = listOf(PlannedExerciseSeed("0289", targetSets = 3, targetWeightKg = 0.0)),
        )
        val reduceSession = repository.startWorkout("plan-bodyweight-reduce-current")
        repository.recordWorkoutSet(reduceSession.id, "0289", 6, 0.0, "吃力")
        repository.finishWorkout(reduceSession.id)
        val reduceDraft = repository.generateWorkoutReviewDraft(reduceSession.id, "非常疲劳", "")
        repository.resolveWorkoutReviewDraft(reduceDraft.id, applyAdjustment = true)
        assertEquals(2, store.plannedExercises("plan-bodyweight-reduce-future").single().targetSets)

        seedPlan(
            planId = "plan-maintain-current",
            scheduledDate = "2026-07-10",
            exercises = listOf(PlannedExerciseSeed("2330", targetSets = 1, targetWeightKg = 20.0)),
        )
        val maintainSession = repository.startWorkout("plan-maintain-current")
        repository.recordWorkoutSet(maintainSession.id, "2330", 10, 20.0, "合适")
        repository.finishWorkout(maintainSession.id)
        val maintainDraft = repository.generateWorkoutReviewDraft(maintainSession.id, "正常疲劳", "继续观察")

        assertEquals(WorkoutAdjustmentDirection.MAINTAIN.name, maintainDraft.workoutReviewMetadata()?.direction)
        assertTrue(maintainDraft.content.contains("暂时保持后续计划"))

        seedPlan(
            planId = "plan-maintain-future",
            scheduledDate = "2026-07-24",
            exercises = listOf(
                PlannedExerciseSeed("2330", targetSets = 3, targetWeightKg = 20.0),
                PlannedExerciseSeed("0289", targetSets = 2, targetWeightKg = 0.0),
            ),
        )
        FitnessRepository::class.java.declaredMethods
            .single { it.name == "applyWorkoutAdjustmentInTransaction" }
            .apply { isAccessible = true }
            .invoke(
                repository,
                WorkoutReviewMetadata(
                    sessionId = maintainSession.id,
                    direction = WorkoutAdjustmentDirection.MAINTAIN.name,
                    postWorkoutFeeling = "正常疲劳",
                    postWorkoutNote = "继续观察",
                    exerciseIds = listOf("2330", "0289"),
                ),
                WorkoutAdjustmentDirection.MAINTAIN,
            )
        val maintained = store.plannedExercises("plan-maintain-future").associateBy { it.exerciseId }
        assertEquals(3, maintained.getValue("2330").targetSets)
        assertEquals(20.0, maintained.getValue("2330").targetWeightKg, 0.01)
        assertEquals(2, maintained.getValue("0289").targetSets)
        assertEquals(0.0, maintained.getValue("0289").targetWeightKg, 0.01)
        assertTrue(maintained.values.all { it.note.contains("保持计划") })

        val recommendation = FitnessRepository::class.java.declaredMethods
            .single { it.name == "workoutAdjustmentRecommendation" }
            .apply { isAccessible = true }
        assertTrue(
            recommendation.invoke(
                repository,
                WorkoutAdjustmentDirection.REDUCE,
                listOf("2330"),
                emptyList<WorkoutSetLogEntity>(),
            ).toString().contains("0kg"),
        )
        val baseLog = store.setLogs(maintainSession.id).single()
        assertTrue(
            recommendation.invoke(
                repository,
                WorkoutAdjustmentDirection.REDUCE,
                listOf("2330"),
                listOf(
                    baseLog.copy(id = "weight-10", actualWeightKg = 10.0),
                    baseLog.copy(id = "weight-30", actualWeightKg = 30.0),
                    baseLog.copy(id = "weight-20", actualWeightKg = 20.0),
                ),
            ).toString().contains("30kg"),
        )
    }

    @Test
    fun workoutAdjustmentRejectsMissingSessionAndFiltersEveryInvalidFuturePlan() = runBlocking {
        seedExercises()
        val applyAdjustment = FitnessRepository::class.java.declaredMethods
            .single { it.name == "applyWorkoutAdjustmentInTransaction" }
            .apply { isAccessible = true }
        val missingError = runCatching {
            applyAdjustment.invoke(
                repository,
                WorkoutReviewMetadata(
                    sessionId = "missing-session",
                    direction = WorkoutAdjustmentDirection.REDUCE.name,
                    postWorkoutFeeling = "非常疲劳",
                    postWorkoutNote = "",
                    exerciseIds = listOf("0748"),
                ),
                WorkoutAdjustmentDirection.REDUCE,
            )
        }.exceptionOrNull()
        assertTrue(missingError is InvocationTargetException)
        assertTrue(missingError?.cause is IllegalArgumentException)

        seedPlan("current-plan", "2026-07-10", exercises = emptyList())
        seedPlan(
            "same-current-plan",
            "2026-07-17",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 4, targetWeightKg = 10.0)),
        )
        seedPlan(
            "completed-future-plan",
            "2026-07-17",
            status = "completed",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 4, targetWeightKg = 10.0)),
        )
        seedPlan(
            "invalid-date-plan",
            "not-a-date",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 4, targetWeightKg = 10.0)),
        )
        seedPlan(
            "past-plan",
            "2026-07-09",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 4, targetWeightKg = 10.0)),
        )
        seedPlan(
            "valid-future-plan",
            "2026-07-17",
            exercises = listOf(
                PlannedExerciseSeed("0748", targetSets = 4, targetWeightKg = 0.0),
                PlannedExerciseSeed("0289", targetSets = 3, targetWeightKg = 0.0),
            ),
        )
        val session = WorkoutSessionEntity(
            id = "active-review-session",
            plannedWorkoutId = "same-current-plan",
            venueId = "venue-1",
            exerciseId = "0748",
            status = "in_progress",
            startedAt = timeProvider.currentTimeMillis(),
            endedAt = null,
            updatedAt = timeProvider.currentTimeMillis(),
        )
        store.upsertWorkoutSession(session)
        store.insertSetLog(
            WorkoutSetLogEntity(
                id = "completed-low",
                sessionId = session.id,
                exerciseId = "0748",
                setIndex = 1,
                actualReps = 8,
                actualWeightKg = 0.5,
                feeling = "吃力",
                completed = true,
                completedAt = timeProvider.currentTimeMillis(),
            ),
        )
        store.insertSetLog(
            WorkoutSetLogEntity(
                id = "completed-high",
                sessionId = session.id,
                exerciseId = "0748",
                setIndex = 2,
                actualReps = 6,
                actualWeightKg = 1.0,
                feeling = "吃力",
                completed = true,
                completedAt = timeProvider.currentTimeMillis(),
            ),
        )
        store.insertSetLog(
            WorkoutSetLogEntity(
                id = "completed-middle",
                sessionId = session.id,
                exerciseId = "0748",
                setIndex = 3,
                actualReps = 7,
                actualWeightKg = 0.75,
                feeling = "吃力",
                completed = true,
                completedAt = timeProvider.currentTimeMillis(),
            ),
        )
        store.insertSetLog(
            WorkoutSetLogEntity(
                id = "incomplete-target",
                sessionId = session.id,
                exerciseId = "0289",
                setIndex = 1,
                actualReps = 6,
                actualWeightKg = 20.0,
                feeling = "吃力",
                completed = false,
                completedAt = timeProvider.currentTimeMillis(),
            ),
        )
        store.insertSetLog(
            WorkoutSetLogEntity(
                id = "completed-unrelated",
                sessionId = session.id,
                exerciseId = "2330",
                setIndex = 1,
                actualReps = 6,
                actualWeightKg = 40.0,
                feeling = "吃力",
                completed = true,
                completedAt = timeProvider.currentTimeMillis(),
            ),
        )

        applyAdjustment.invoke(
            repository,
            WorkoutReviewMetadata(
                sessionId = session.id,
                direction = WorkoutAdjustmentDirection.REDUCE.name,
                postWorkoutFeeling = "非常疲劳",
                postWorkoutNote = "",
                exerciseIds = listOf("0748", "0289"),
            ),
            WorkoutAdjustmentDirection.REDUCE,
        )

        val adjusted = store.plannedExercises("valid-future-plan").associateBy { it.exerciseId }
        assertEquals(4, adjusted.getValue("0748").targetSets)
        assertEquals(0.0, adjusted.getValue("0748").targetWeightKg, 0.01)
        assertEquals(2, adjusted.getValue("0289").targetSets)
        assertEquals(0.0, adjusted.getValue("0289").targetWeightKg, 0.01)
        listOf("same-current-plan", "completed-future-plan", "invalid-date-plan", "past-plan").forEach { planId ->
            assertTrue(store.plannedExercises(planId).all { it.note == "测试动作" })
        }
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
        seedProfile()
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
        seedProfile()
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
        store.upsertUserProfile(requireNotNull(state.userProfile).copy(goal = "保持体能"))
        val maintainNutrition = repository.nutritionSummary(
            repository.appState().first(),
            LocalDate.of(2026, 7, 10),
        )
        assertEquals(2_280, requireNotNull(maintainNutrition.reference).calories)
        store.upsertUserProfile(requireNotNull(state.userProfile).copy(goal = "减脂"))
        val cuttingNutrition = repository.nutritionSummary(
            repository.appState().first(),
            LocalDate.of(2026, 7, 10),
        )
        assertEquals(2_128, requireNotNull(cuttingNutrition.reference).calories)
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
        repository.saveAiApiKey(FitnessRepository.OPENAI_PROVIDER_ID, "sk-reset-test")
        val avatarDir = java.io.File(context.filesDir, "avatars").apply { mkdirs() }
        val avatar = java.io.File(avatarDir, "reset-profile.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val orphanAvatar = java.io.File(avatarDir, "reset-orphan.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        store.upsertUserProfile(requireNotNull(store.userProfile()).copy(avatarPath = "avatars/reset-profile.jpg"))
        seedExercises()
        seedPlan(
            planId = "plan-reset",
            scheduledDate = "2026-07-10",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 1, targetWeightKg = 70.0)),
        )
        repository.startWorkout("plan-reset")
        assertNotNull(credentialStore.loadApiKey(FitnessRepository.OPENAI_PROVIDER_ID))
        assertEquals(1, store.workoutSessions().size)

        repository.resetLocalData()
        val state = repository.appState().first()

        assertNull(credentialStore.loadApiKey(FitnessRepository.OPENAI_PROVIDER_ID))
        assertNull(state.userProfile)
        assertTrue(state.foodLogs.isEmpty())
        assertTrue(state.workoutSessions.isEmpty())
        assertTrue(state.workoutSessionExercises.isEmpty())
        assertTrue(state.workoutSetLogs.isEmpty())
        assertTrue(state.unfinishedSessions.isEmpty())
        assertTrue(state.venues.isNotEmpty())
        assertTrue(state.equipment.isNotEmpty())
        assertTrue(state.plannedWorkouts.isEmpty())
        assertEquals(listOf("openai", "gemini", "qwen"), state.aiProviders.map { it.id })
        assertTrue(state.aiProviders.none { it.apiKeyStored })
        assertFalse(avatar.exists())
        assertFalse(orphanAvatar.exists())
    }

    @Test
    fun importedProviderFlagCannotClaimAConnectionWithoutTheLocalKeystoreKey() = runBlocking {
        val rawBackup = providerFlagBackup(apiKeyStored = true)

        repository.importBackupJson(rawBackup)

        assertEquals(listOf("openai", "gemini", "qwen"), store.aiProviders().map { it.id })
        assertFalse(store.aiProvider(FitnessRepository.OPENAI_PROVIDER_ID)?.apiKeyStored == true)
        assertNull(credentialStore.loadApiKey(FitnessRepository.OPENAI_PROVIDER_ID))
        assertFalse(
            repository.appState().first().aiProviders
                .single { it.id == FitnessRepository.OPENAI_PROVIDER_ID }
                .apiKeyStored,
        )
    }

    @Test
    fun providerSeedingRepairsEndpointsAndCoversEveryDefaultSelectionPath() = runBlocking {
        store.upsertAiProvider(
            com.shanqijie.fitnessapp.data.AiProviderEntity(
                id = "qwen",
                displayName = "旧百炼",
                baseUrl = "https://invalid.example.test/v1",
                model = "custom-model",
                enabled = false,
                apiKeyStored = false,
                updatedAt = 77L,
            ),
        )
        credentialStore.saveApiKey("qwen", "sk-qwen-local")
        repository.bootstrap()

        val qwen = requireNotNull(store.aiProvider("qwen"))
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", qwen.baseUrl)
        assertEquals("custom-model", qwen.model)
        assertFalse(qwen.enabled)
        assertTrue(qwen.apiKeyStored)
        assertEquals(77L, qwen.updatedAt)

        val activeProvider = FitnessRepository::class.java.declaredMethods
            .single { it.name == "activeAiProviderOrDefault" }
            .apply { isAccessible = true }
        assertEquals("openai", (activeProvider.invoke(repository) as com.shanqijie.fitnessapp.data.AiProviderEntity).id)

        store.aiProviders().forEach { provider ->
            store.upsertAiProvider(provider.copy(enabled = false))
        }
        assertEquals("openai", (activeProvider.invoke(repository) as com.shanqijie.fitnessapp.data.AiProviderEntity).id)

        store.aiProviders().forEach { provider -> store.deleteAiProvider(provider.id) }
        val fallback = activeProvider.invoke(repository) as com.shanqijie.fitnessapp.data.AiProviderEntity
        assertEquals("openai", fallback.id)
        assertFalse(fallback.apiKeyStored)
        credentialStore.saveApiKey(FitnessRepository.OPENAI_PROVIDER_ID, "sk-fallback")
        val fallbackWithKey = activeProvider.invoke(repository) as com.shanqijie.fitnessapp.data.AiProviderEntity
        assertTrue(fallbackWithKey.apiKeyStored)
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
    fun foodDraftConfirmationPersistsUserEditedValues() = runBlocking {
        val draft = repository.generateFoodEstimateDraft("鸡胸肉米饭")

        val foodLog = repository.confirmFoodEstimateDraft(
            draftId = draft.id,
            name = "自定义晚餐",
            calories = 680,
            proteinGrams = 50.5,
            carbsGrams = 72.0,
            fatGrams = 18.0,
        )

        assertEquals("自定义晚餐", foodLog.name)
        assertEquals(680, foodLog.calories)
        assertEquals(50.5, foodLog.proteinGrams, 0.0)
        assertEquals(72.0, foodLog.carbsGrams, 0.0)
        assertEquals(18.0, foodLog.fatGrams, 0.0)
        assertEquals(foodLog, store.foodLogs().single())
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
        assertEquals(listOf("openai", "gemini", "qwen"), store.aiProviders().map { it.id })
        assertTrue(store.aiProviders().single { it.id == "openai" }.enabled)

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
        assertEquals(5, payload.version)
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
    fun backupV5RoundTripsAdaptivePlanState() = runBlocking {
        val now = timeProvider.currentTimeMillis()
        val venue = TrainingVenueEntity("venue-plan", "计划健身房", true, now, now)
        val equipment = EquipmentEntity("barbell", "杠铃", "free-weight", now, now)
        val cycle = PlanCycleEntity("cycle-1", 4, 1, "2026-07-13", 60, "active", now, now)
        val day = PlanScheduleDayEntity(cycle.id, 1, venue.id, 0)
        val draft = WeeklyPlanDraftEntity(
            id = "weekly-draft-1",
            cycleId = cycle.id,
            weekIndex = 1,
            weekStartDate = "2026-07-13",
            payloadJson = "{}",
            inputHash = "snapshot-1",
            status = "draft",
            explanationsJson = "[]",
            createdAt = now,
            updatedAt = now,
            confirmedAt = null,
        )
        val load = VenueEquipmentLoadEntity(venue.id, equipment.id, 20.0, 0, now)
        val preference = ActionPreferenceEntity("barbell-bench", "replace", "dumbbell-bench", now)
        val override = InjuryFilterOverrideEntity(
            "barbell-bench",
            "injuries-v1",
            "用户确认可安全完成",
            now,
            now,
        )
        store.upsertVenue(venue)
        store.upsertEquipment(equipment)
        store.upsertPlanCycle(cycle)
        store.replacePlanScheduleDays(cycle.id, listOf(day))
        store.upsertWeeklyPlanDraft(draft)
        store.replaceVenueEquipmentLoads(venue.id, equipment.id, listOf(load))
        store.upsertActionPreference(preference)
        store.upsertInjuryFilterOverride(override)

        val exported = repository.exportBackupJson()
        assertEquals(5, FitnessBackupCodec.decode(exported).version)
        store.clearPersonalData()
        repository.importBackupJson(exported)

        assertEquals(listOf(cycle), store.planCycles())
        assertEquals(listOf(day), store.allPlanScheduleDays())
        assertEquals(listOf(draft), store.weeklyPlanDrafts())
        assertEquals(listOf(load), store.allVenueEquipmentLoads())
        assertEquals(listOf(preference), store.actionPreferences())
        assertEquals(listOf(override), store.injuryFilterOverrides())
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
    fun compatibilitySessionsCoverFreeExistingAndRejectedPlanShapes() = runBlocking {
        seedExercises()

        repository.skipExercise(
            sessionId = "free-compatibility",
            plannedWorkoutId = null,
            venueId = "venue-free",
            exerciseId = "0748",
            setIndex = 1,
            reason = "   ",
        )
        repository.skipExercise(
            sessionId = "free-compatibility",
            plannedWorkoutId = null,
            venueId = "venue-free",
            exerciseId = "0289",
            setIndex = 1,
            reason = "器械占用",
        )
        val freeSnapshots = store.sessionExercises("free-compatibility")
        assertEquals(listOf(1, 2), freeSnapshots.map { it.orderIndex })
        assertTrue(freeSnapshots.all { it.targetSets == 3 && it.targetReps == "8-12" && it.targetWeightKg == 0.0 })
        assertEquals("跳过：跳过", store.setLogs("free-compatibility", "0748").single().feeling)
        assertTrue(
            runCatching {
                repository.skipExercise(
                    "free-compatibility",
                    null,
                    "venue-free",
                    "0748",
                    setIndex = 3,
                    reason = "再次跳过",
                )
            }.exceptionOrNull() is IllegalArgumentException,
        )

        seedPlan(
            planId = "plan-active-compatibility",
            scheduledDate = "2026-07-10",
            exercises = listOf(PlannedExerciseSeed("2330", targetSets = 1, targetWeightKg = 0.0)),
        )
        val active = repository.startWorkout("plan-active-compatibility")
        val latePlannedExercise = repository.addExerciseToPlan("plan-active-compatibility", "0748")
        repository.completeSet(
            sessionId = active.id,
            exerciseId = "0748",
            setIndex = 1,
            reps = 8,
            weightKg = 0.0,
            feeling = "合适",
        )
        val lateSnapshot = store.sessionExercises(active.id).single { it.exerciseId == "0748" }
        assertEquals(latePlannedExercise.orderIndex, lateSnapshot.orderIndex)
        assertEquals(latePlannedExercise.targetSets, lateSnapshot.targetSets)
        assertEquals(latePlannedExercise.targetReps, lateSnapshot.targetReps)
        assertEquals(latePlannedExercise.targetWeightKg, lateSnapshot.targetWeightKg, 0.0)
        store.upsertWorkoutSession(
            active.copy(
                id = "completed-same-plan",
                status = "completed",
                startedAt = active.startedAt + 1L,
                endedAt = active.startedAt + 2L,
                updatedAt = active.startedAt + 2L,
            ),
        )
        repository.skipExercise(
            sessionId = "different-requested-id",
            plannedWorkoutId = "plan-active-compatibility",
            venueId = "venue-ignored",
            exerciseId = "2330",
            setIndex = 1,
            reason = "跳过",
        )
        assertNotNull(store.workoutSession(active.id))
        assertNull(store.workoutSession("different-requested-id"))

        store.upsertPlannedWorkout(
            PlannedWorkoutEntity(
                id = "plan-empty-completed",
                name = "不可开始空计划",
                scheduledDate = "2026-07-10",
                venueId = "venue",
                status = "completed",
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        assertTrue(
            runCatching {
                repository.skipExercise(
                    "requested-empty-completed",
                    "plan-empty-completed",
                    "venue",
                    "0748",
                    setIndex = 1,
                    reason = "跳过",
                )
            }.exceptionOrNull() is IllegalArgumentException,
        )
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
        val mismatchedPayload = payload.copy(
            setLogs = listOf(
                originalLog.copy(
                    id = "mismatched-linked-set",
                    exerciseId = "different-exercise",
                ),
            ),
        )
        val mismatchFailure = runCatching {
            repository.importBackupJson(FitnessBackupCodec.encode(mismatchedPayload))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(mismatchFailure is IllegalArgumentException)
        assertEquals("导入前用户", store.userProfile()?.displayName)
        assertEquals(listOf(session.id), store.workoutSessions().map { it.id })
        assertEquals(listOf(originalLog.id), store.setLogs(session.id).map { it.id })
    }

    @Test
    fun backupValidationRejectsEveryDuplicateAndBrokenReferenceShape() = runBlocking {
        repository.bootstrap()
        seedExercises()
        seedPlan(
            planId = "plan-backup-validation",
            scheduledDate = "2026-07-10",
            exercises = listOf(PlannedExerciseSeed("0748", targetSets = 2, targetWeightKg = 20.0)),
        )
        val session = repository.startWorkout("plan-backup-validation")
        repository.recordWorkoutSet(session.id, "0748", 8, 20.0, "合适")
        repository.logFood("测试餐", 500, 40.0, 50.0, 15.0)
        repository.generateFoodEstimateDraft("测试餐")
        repository.generateTrainingAdjustment("0748")
        val payload = FitnessBackupCodec.decode(repository.exportBackupJson())
        val validation = FitnessRepository::class.java.declaredMethods
            .single { it.name == "validateBackupPayload" }
            .apply { isAccessible = true }

        fun assertValid(candidate: FitnessBackupPayload) {
            validation.invoke(repository, candidate)
        }
        fun assertInvalid(candidate: FitnessBackupPayload) {
            val failure = runCatching { validation.invoke(repository, candidate) }.exceptionOrNull()
            assertTrue(failure is InvocationTargetException && failure.cause is IllegalArgumentException)
        }

        assertValid(payload)
        assertValid(payload.copy(version = 1, setLogs = payload.setLogs.map { it.copy(feeling = "") }))
        assertValid(payload.copy(setLogs = payload.setLogs.map { it.copy(sessionExerciseId = null) }))
        assertInvalid(payload.copy(version = 0))
        assertInvalid(payload.copy(version = 5))
        assertInvalid(payload.copy(venues = payload.venues + payload.venues.first()))
        assertInvalid(payload.copy(equipment = payload.equipment + payload.equipment.first()))
        assertInvalid(payload.copy(plannedWorkouts = payload.plannedWorkouts + payload.plannedWorkouts.first()))
        assertInvalid(payload.copy(plannedExercises = payload.plannedExercises + payload.plannedExercises.first()))
        assertInvalid(payload.copy(workoutSessions = payload.workoutSessions + payload.workoutSessions.first()))
        assertInvalid(payload.copy(sessionExercises = payload.sessionExercises + payload.sessionExercises.first()))
        assertInvalid(payload.copy(setLogs = payload.setLogs + payload.setLogs.first()))
        assertInvalid(payload.copy(foodLogs = payload.foodLogs + payload.foodLogs.first()))
        assertInvalid(payload.copy(aiDrafts = payload.aiDrafts + payload.aiDrafts.first()))
        assertInvalid(payload.copy(trainingAdjustments = payload.trainingAdjustments + payload.trainingAdjustments.first()))
        assertInvalid(payload.copy(aiProviders = payload.aiProviders + payload.aiProviders.first()))
        assertInvalid(
            payload.copy(
                sessionExercises = payload.sessionExercises + payload.sessionExercises.first().copy(id = "duplicate-session-exercise-pair"),
            ),
        )
        assertInvalid(
            payload.copy(
                setLogs = payload.setLogs + payload.setLogs.first().copy(id = "duplicate-set-index"),
            ),
        )

        val log = payload.setLogs.first()
        assertInvalid(payload.copy(setLogs = listOf(log.copy(setIndex = 0))))
        assertInvalid(payload.copy(setLogs = listOf(log.copy(actualReps = -1))))
        assertInvalid(payload.copy(setLogs = listOf(log.copy(actualWeightKg = -0.1))))
        assertInvalid(payload.copy(setLogs = listOf(log.copy(feeling = ""))))
        val sessionExercise = payload.sessionExercises.first()
        assertInvalid(payload.copy(sessionExercises = listOf(sessionExercise.copy(sessionId = "missing-session"))))
        assertInvalid(payload.copy(sessionExercises = listOf(sessionExercise.copy(targetSets = 0))))
        assertInvalid(payload.copy(setLogs = listOf(log.copy(sessionExerciseId = "missing-session-exercise"))))
        assertInvalid(payload.copy(setLogs = listOf(log.copy(sessionId = "mismatched-session"))))
        assertInvalid(payload.copy(setLogs = listOf(log.copy(exerciseId = "mismatched-exercise"))))
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
    fun workoutStartAndSetRecordingRejectEveryInvalidBoundary() = runBlocking {
        seedExercises()
        store.upsertPlannedWorkout(
            PlannedWorkoutEntity(
                id = "plan-empty",
                name = "空计划",
                scheduledDate = "2026-07-10",
                venueId = "venue",
                status = "planned",
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        assertTrue(runCatching { repository.startWorkout("missing-plan") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repository.startWorkout("plan-empty") }.exceptionOrNull() is IllegalArgumentException)

        seedPlan(
            planId = "plan-validation",
            scheduledDate = "2026-07-10",
            exercises = listOf(
                PlannedExerciseSeed("0748", targetSets = 2, targetWeightKg = 20.0),
                PlannedExerciseSeed("0289", targetSets = 1, targetWeightKg = 10.0),
            ),
        )
        store.upsertWorkoutSession(
            WorkoutSessionEntity(
                id = "decoy-other-plan",
                plannedWorkoutId = "other-plan",
                venueId = "venue",
                exerciseId = "0748",
                status = "in_progress",
                startedAt = 3L,
                endedAt = null,
                updatedAt = 3L,
            ),
        )
        store.upsertWorkoutSession(
            WorkoutSessionEntity(
                id = "decoy-completed",
                plannedWorkoutId = "plan-validation",
                venueId = "venue",
                exerciseId = "0748",
                status = "completed",
                startedAt = 2L,
                endedAt = 2L,
                updatedAt = 2L,
            ),
        )
        store.upsertWorkoutSession(
            WorkoutSessionEntity(
                id = "decoy-cancelled",
                plannedWorkoutId = null,
                venueId = "venue",
                exerciseId = "0748",
                status = "cancelled",
                startedAt = 1L,
                endedAt = null,
                updatedAt = 1L,
            ),
        )
        val session = repository.startWorkout("plan-validation")

        val failures = listOf(
            runCatching { repository.recordWorkoutSet(session.id, "0748", 51, 20.0, "合适") }.exceptionOrNull(),
            runCatching { repository.recordWorkoutSet(session.id, "0748", 8, 20.0, "合适", restSeconds = -1) }.exceptionOrNull(),
            runCatching { repository.recordWorkoutSet("missing-session", 8, 20.0, "合适") }.exceptionOrNull(),
            runCatching { repository.recordWorkoutSet(session.id, "missing-action", 8, 20.0, "合适") }.exceptionOrNull(),
            runCatching { repository.recordWorkoutSet(session.id, "0289", 8, 10.0, "合适") }.exceptionOrNull(),
            runCatching { repository.completeSet(session.id, "0748", 2, 8, 20.0, "合适") }.exceptionOrNull(),
            runCatching { repository.finishWorkout("missing-session") }.exceptionOrNull(),
            runCatching { repository.workoutSummary("missing-session") }.exceptionOrNull(),
            runCatching { repository.finishWorkout("decoy-cancelled") }.exceptionOrNull(),
            runCatching { repository.startRest("decoy-completed") }.exceptionOrNull(),
        )
        assertTrue(failures.all { it is IllegalArgumentException })
        assertTrue(store.setLogs(session.id).isEmpty())
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
    fun profileValidationCoversEveryBodyMeasurementBoundary() = runBlocking {
        suspend fun save(measurement: BodyMeasurement) = repository.saveUserProfile(
            displayName = "测试用户",
            birthYear = 1994,
            heightCm = 176.0,
            weightKg = 75.0,
            goal = "保持体能",
            injuries = "",
            weeklyTrainingDays = 3,
            preferredMinutes = 45,
            bodyMeasurement = measurement,
        )

        save(
            BodyMeasurement(
                measuredAt = " 2026-07-14 ",
                bodyType = " 标准型 ",
                bodyFatPercentage = 0.0,
                bodyFatMassKg = 150.0,
                skeletalMuscleKg = 100.0,
                bodyWaterKg = 0.0,
                basalMetabolismKcal = 500,
                waistHipRatio = 2.0,
                bmi = 10.0,
            ),
        )
        assertEquals("2026-07-14", store.userProfile()?.bodyMeasurement?.measuredAt)
        assertEquals("标准型", store.userProfile()?.bodyMeasurement?.bodyType)

        val invalidMeasurements = listOf(
            BodyMeasurement(measuredAt = "not-a-date"),
            BodyMeasurement(bodyType = "x".repeat(21)),
            BodyMeasurement(bodyFatPercentage = -0.1),
            BodyMeasurement(bodyFatPercentage = 75.1),
            BodyMeasurement(bodyFatMassKg = 150.1),
            BodyMeasurement(bodyFatMassKg = -0.1),
            BodyMeasurement(skeletalMuscleKg = -0.1),
            BodyMeasurement(skeletalMuscleKg = 100.1),
            BodyMeasurement(bodyWaterKg = 150.1),
            BodyMeasurement(bodyWaterKg = -0.1),
            BodyMeasurement(basalMetabolismKcal = 499),
            BodyMeasurement(basalMetabolismKcal = 5001),
            BodyMeasurement(waistHipRatio = 2.1),
            BodyMeasurement(waistHipRatio = 0.2),
            BodyMeasurement(bmi = 80.1),
            BodyMeasurement(bmi = 9.9),
        )
        invalidMeasurements.forEach { measurement ->
            assertNotNull(runCatching { save(measurement) }.exceptionOrNull())
        }
    }

    @Test
    fun homeSnapshotAndPlanPromptCoverMissingInvalidAndPriorityStates() = runBlocking {
        repository.bootstrap()
        val seeded = repository.appState().first()
        val today = LocalDate.of(2026, 7, 10)
        val empty = seeded.copy(
            plannedWorkouts = emptyList(),
            plannedExercises = emptyList(),
            plannedExerciseViews = emptyList(),
            workoutSessions = emptyList(),
            unfinishedSessions = emptyList(),
            workoutSetLogs = emptyList(),
            userProfile = null,
        )
        assertEquals(HomePrimaryAction.CreatePlan, repository.homeSnapshot(empty, today).action)

        val past = PlannedWorkoutEntity("past", "过去计划", "2026-07-01", "venue", "planned", 1L, 1L)
        val invalid = past.copy(id = "invalid", name = "无效日期", scheduledDate = "not-a-date", createdAt = 2L)
        val future = past.copy(id = "future", name = "未来计划", scheduledDate = "2026-07-11", createdAt = 3L)
        val ignored = past.copy(id = "ignored", status = "completed", scheduledDate = "2026-07-12", createdAt = 4L)
        val startSnapshot = repository.homeSnapshot(
            empty.copy(plannedWorkouts = listOf(invalid, ignored, future, past)),
            today,
        )
        assertEquals(HomePrimaryAction.Start("future"), startSnapshot.action)
        assertEquals("future", startSnapshot.nextWorkout?.id)
        assertEquals(2, startSnapshot.targetThisWeek)
        val invalidDateFallback = repository.homeSnapshot(
            empty.copy(plannedWorkouts = listOf(invalid, past)),
            today,
        )
        assertEquals(HomePrimaryAction.Start("past"), invalidDateFallback.action)

        val completedToday = WorkoutSessionEntity(
            id = "completed-today",
            plannedWorkoutId = null,
            venueId = "venue",
            exerciseId = "0748",
            status = "completed",
            startedAt = timeProvider.currentTimeMillis() - 1_000L,
            endedAt = timeProvider.currentTimeMillis(),
            updatedAt = timeProvider.currentTimeMillis(),
        )
        val completedOutsideWeek = completedToday.copy(
            id = "completed-outside",
            endedAt = timeProvider.currentTimeMillis() - 20L * 24L * 60L * 60L * 1_000L,
        )
        val completedAfterWeek = completedToday.copy(
            id = "completed-after-week",
            endedAt = timeProvider.currentTimeMillis() + 20L * 24L * 60L * 60L * 1_000L,
        )
        val completedWithoutEnd = completedToday.copy(id = "completed-no-end", endedAt = null)
        val completedTodayOlder = completedToday.copy(
            id = "completed-today-older",
            endedAt = timeProvider.currentTimeMillis() - 500L,
        )
        val completedWeekStart = completedToday.copy(
            id = "completed-week-start",
            endedAt = timeProvider.currentTimeMillis() - 4L * 24L * 60L * 60L * 1_000L,
        )
        val completedWeekEnd = completedToday.copy(
            id = "completed-week-end",
            endedAt = timeProvider.currentTimeMillis() + 2L * 24L * 60L * 60L * 1_000L,
        )
        val inProgressHistory = completedToday.copy(
            id = "in-progress-history",
            status = "in_progress",
            endedAt = null,
        )
        val resultSnapshot = repository.homeSnapshot(
            empty.copy(
                workoutSessions = listOf(
                    completedOutsideWeek,
                    completedAfterWeek,
                    completedWithoutEnd,
                    inProgressHistory,
                    completedWeekStart,
                    completedWeekEnd,
                    completedTodayOlder,
                    completedToday,
                ),
            ),
            today,
        )
        assertEquals(HomePrimaryAction.Result("completed-today"), resultSnapshot.action)
        assertEquals(4, resultSnapshot.completedThisWeek)
        assertEquals(
            HomePrimaryAction.Result("completed-today"),
            repository.homeSnapshot(
                empty.copy(workoutSessions = listOf(completedToday, completedTodayOlder)),
                today,
            ).action,
        )

        val activeOld = completedToday.copy(id = "active-old", status = "in_progress", startedAt = 1L, endedAt = null)
        val activeNew = activeOld.copy(id = "active-new", startedAt = 2L)
        val resumeSnapshot = repository.homeSnapshot(
            empty.copy(
                unfinishedSessions = listOf(activeOld, activeNew),
                userProfile = UserProfileEntity(
                    id = "profile",
                    displayName = "测试",
                    birthYear = 1994,
                    heightCm = 176.0,
                    weightKg = 75.0,
                    goal = "保持体能",
                    injuries = "",
                    weeklyTrainingDays = 5,
                    preferredMinutes = 45,
                    updatedAt = 1L,
                ),
            ),
            today,
        )
        assertEquals(HomePrimaryAction.Resume("active-new"), resumeSnapshot.action)
        assertEquals(5, resumeSnapshot.targetThisWeek)

        val promptMethod = FitnessRepository::class.java.declaredMethods
            .single { it.name == "buildPlanPrompt" }
            .apply { isAccessible = true }
        val missingPrompt = promptMethod.invoke(repository, null, 3, 45, "测试场地", "无").toString()
        assertTrue(missingPrompt.contains("昵称：未填写"))
        assertTrue(missingPrompt.contains("体测数据：未填写"))
        val completeProfile = UserProfileEntity(
            id = "profile-complete",
            displayName = "测试",
            birthYear = 1994,
            heightCm = 176.5,
            weightKg = 75.0,
            goal = "保持体能",
            injuries = "",
            weeklyTrainingDays = 4,
            preferredMinutes = 60,
            updatedAt = 1L,
            bodyMeasurement = BodyMeasurement(
                bodyFatPercentage = 15.0,
                bodyFatMassKg = 11.5,
                bmi = 22.1,
                skeletalMuscleKg = 32.0,
                bodyWaterKg = 42.5,
                basalMetabolismKcal = 1_650,
                waistHipRatio = 0.85,
            ),
        )
        val completePrompt = promptMethod.invoke(repository, completeProfile, 4, 60, "公司", "哑铃").toString()
        assertTrue(completePrompt.contains("身高：176.5 cm"))
        assertTrue(completePrompt.contains("体重：75 kg"))
        assertTrue(completePrompt.contains("伤病与注意事项：未填写"))
        assertTrue(completePrompt.contains("体脂率：15%"))
    }

    @Test
    fun privateSessionGuardsRejectMissingAndDisappearingSessions() = runBlocking {
        val requireSession = FitnessRepository::class.java.declaredMethods
            .single { it.name == "requireInProgressSession" }
            .apply { isAccessible = true }
        val missing = runCatching { requireSession.invoke(repository, "missing-session") }.exceptionOrNull()
        assertTrue(missing is InvocationTargetException)
        assertTrue(missing?.cause is IllegalArgumentException)

        seedExercises()
        val session = WorkoutSessionEntity(
            id = "session-deleted-after-selection",
            plannedWorkoutId = null,
            venueId = "venue-1",
            exerciseId = "0748",
            status = "in_progress",
            startedAt = timeProvider.currentTimeMillis(),
            endedAt = null,
            updatedAt = timeProvider.currentTimeMillis(),
            currentExerciseId = "0748",
        )
        store.upsertWorkoutSession(session)
        db.writableDatabase.execSQL(
            """
            CREATE TRIGGER delete_selected_session
            AFTER UPDATE ON workout_session
            WHEN NEW.id = 'session-deleted-after-selection'
            BEGIN
                DELETE FROM workout_session WHERE id = NEW.id;
            END
            """.trimIndent(),
        )
        val selectExercise = FitnessRepository::class.java.declaredMethods
            .single { it.name == "selectCompatibilityExerciseInTransaction" }
            .apply { isAccessible = true }
        val disappearing = runCatching {
            selectExercise.invoke(repository, session, "0289")
        }.exceptionOrNull()
        assertTrue(disappearing is InvocationTargetException)
        assertTrue(disappearing?.cause is IllegalArgumentException)
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
        assertEquals(2, repository.searchExercises("bench").size)
        repository.completeSet(
            sessionId = "session-plan-0748",
            exerciseId = "0748",
            setIndex = 1,
            reps = 12,
            weightKg = 70.0,
            feeling = "轻松",
        )
        val adjustment = repository.generateTrainingAdjustment("0748")
        repository.confirmTrainingAdjustment(adjustment.id)
        assertEquals("confirmed", store.trainingAdjustments().single { it.id == adjustment.id }.status)
        assertEquals("0748", store.exerciseByName("SMITH BENCH PRESS")?.exerciseId)
        assertEquals(1, store.allExercises(limit = 0).size)
        assertEquals(4, store.allExercises().size)
        assertTrue(store.searchExercises("   ", limit = 1).isNotEmpty())
        assertEquals(2, store.searchExercises("bench").size)
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
        assertEquals("gpt-5-mini", foodLog.model)
        assertTrue(state.onboardingCompleted)
        assertEquals(listOf("史密斯机"), state.equipmentForSelectedVenue.map { it.name })
        assertEquals("下次加重", state.trainingAdjustments.single().title)
        assertEquals(4, state.plannedWorkouts.count { it.name.startsWith("月计划") })
    }

    @Test
    fun foodFallbackCoversSaladAndMalformedDraftMetadata() = runBlocking {
        repository.bootstrap()
        val saladDraft = repository.generateFoodEstimateDraft("一份 salad 沙拉")
        assertTrue(saladDraft.title.contains("轻食沙拉"))
        assertTrue(saladDraft.content.contains("380"))

        val now = timeProvider.currentTimeMillis()
        store.upsertAiDraft(
            AiDraftEntity(
                id = "malformed-food-metadata",
                type = "food_estimate",
                title = "饮食估算：测试餐",
                content = "约 100 千卡 · 蛋白质 10g · 碳水 12g · 脂肪 3g",
                status = "draft",
                createdAt = now,
                updatedAt = now,
                metadataJson = "not-json",
                confirmedAt = null,
            ),
        )

        val confirmed = repository.confirmFoodEstimateDraft("malformed-food-metadata")
        assertEquals("", confirmed.imageUri)
        assertEquals("", confirmed.providerId)
        assertEquals("", confirmed.model)
    }

    @Test
    fun venueEquipmentAndCalendarApisCoverValidAndRejectedChanges() = runBlocking {
        repository.bootstrap()
        val initial = repository.appState().first()
        val initialVenue = requireNotNull(initial.venue)

        repository.renameDefaultVenue("  公司训练区  ")
        val secondVenue = repository.addVenue("  家庭训练区  ")
        repository.setDefaultVenue(secondVenue.id)
        repository.addDefaultEquipment("  自定义沙袋  ", category = "accessory")
        val customEquipment = repository.appState().first().equipment.single { it.name == "自定义沙袋" }
        repository.replaceVenueEquipment(secondVenue.id, setOf(customEquipment.id))
        repository.setCalendarMode("月")

        val changed = repository.appState().first()
        assertEquals("家庭训练区", changed.venue?.name)
        assertEquals(listOf("自定义沙袋"), changed.equipmentForSelectedVenue.map { it.name })
        assertEquals("月", store.preference(FitnessRepository.CALENDAR_MODE_KEY))
        assertEquals("公司训练区", changed.venues.single { it.id == initialVenue.id }.name)

        repository.bindEquipmentToVenue(secondVenue.id, customEquipment.id, available = false)
        assertTrue(repository.appState().first().equipmentForSelectedVenue.isEmpty())
        repository.deleteEquipment(customEquipment.id)
        assertFalse(repository.appState().first().equipment.any { it.id == customEquipment.id })

        val rejected = listOf(
            runCatching { repository.renameVenue(secondVenue.id, "   ") }.exceptionOrNull(),
            runCatching { repository.addVenue("   ") }.exceptionOrNull(),
            runCatching { repository.addDefaultEquipment("   ") }.exceptionOrNull(),
            runCatching { repository.bindEquipmentToVenue("missing", "missing", true) }.exceptionOrNull(),
            runCatching { repository.bindEquipmentToVenue(secondVenue.id, "missing", true) }.exceptionOrNull(),
            runCatching { repository.replaceVenueEquipment("missing", emptySet()) }.exceptionOrNull(),
            runCatching { repository.replaceVenueEquipment(secondVenue.id, setOf("missing")) }.exceptionOrNull(),
            runCatching { repository.setCalendarMode("日") }.exceptionOrNull(),
        )
        assertTrue(rejected.all { it is IllegalArgumentException })
    }

    @Test
    fun workoutRuntimeControlsExposeFlowsAndRejectInvalidTransitions() = runBlocking {
        seedExercises()
        seedPlan(
            planId = "plan-runtime",
            scheduledDate = "2026-07-10",
            exercises = listOf(
                PlannedExerciseSeed("0748", targetSets = 2, targetReps = "8", targetWeightKg = 60.0),
                PlannedExerciseSeed("0289", targetSets = 1, targetReps = "10", targetWeightKg = 20.0),
            ),
        )
        val session = repository.startWorkout("plan-runtime")
        val selected = repository.selectWorkoutExercise(session.id, "0289")
        assertEquals("0289", selected.currentExerciseId)

        val resting = repository.startRest(session.id)
        assertNotNull(resting.restEndsAt)
        val extended = repository.extendRest(session.id, durationSeconds = 30)
        assertEquals(resting.restEndsAt!! + 30_000L, extended.restEndsAt)
        assertTrue(runCatching { repository.toggleWorkoutPause(session.id) }.exceptionOrNull() is IllegalArgumentException)

        repository.skipRest(session.id)
        val paused = repository.toggleWorkoutPause(session.id)
        assertNotNull(paused.pausedAt)
        timeProvider.advanceSeconds(5)
        val resumed = repository.toggleWorkoutPause(session.id)
        assertNull(resumed.pausedAt)
        assertEquals(session.startedAt + 5_000L, resumed.startedAt)

        val log = repository.recordWorkoutSet(
            sessionId = session.id,
            reps = 10,
            weightKg = 20.0,
            feeling = "合适",
        )
        assertEquals("0289", log.exerciseId)
        assertEquals(1, repository.completedSetCount(session.id).first())
        assertEquals(1, repository.completedSetCount(session.id, "0289").first())
        assertEquals(listOf(log.id), repository.setLogs(session.id).first().map { it.id })
        assertEquals(listOf(log.id), repository.exerciseLogs(session.id, "0289").first().map { it.id })
        assertEquals(1, repository.workoutSummary(session.id).completedSets)
        assertEquals("session-plan-runtime", repository.sessionIdFor("plan-runtime", "ignored"))

        assertTrue(runCatching { repository.selectWorkoutExercise(session.id, "2330") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(
            runCatching {
                repository.recordWorkoutSet(session.id, "2330", 8, 20.0, "合适")
            }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(runCatching { repository.startRest(session.id, -1) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repository.extendRest(session.id, 0) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repository.extendRest(session.id, 301) }.exceptionOrNull() is IllegalArgumentException)
        repository.skipRest(session.id)
        assertTrue(runCatching { repository.extendRest(session.id, 30) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun publicSuspendEntrypointsAlsoCompleteWhenAlreadyOnTheIoDispatcher() = runBlocking {
        withContext(Dispatchers.IO) {
            repository.bootstrap()
            val initial = repository.appState().first()
            val venue = requireNotNull(initial.venue)
            val firstEquipment = initial.equipment.first()

            repository.renameDefaultVenue("IO 训练区")
            repository.setDefaultVenue(venue.id)
            repository.addDefaultEquipment("IO 沙袋")
            val customEquipment = store.allEquipment().single { it.name == "IO 沙袋" }
            repository.bindEquipmentToVenue(venue.id, firstEquipment.id, available = true)
            repository.replaceVenueEquipment(venue.id, setOf(firstEquipment.id))
            repository.deleteEquipment(customEquipment.id)
            repository.setOnboardingCompleted(true)
            repository.setCalendarMode("年")
            repository.saveUserProfile(
                displayName = "IO 用户",
                birthYear = 1994,
                heightCm = 176.0,
                weightKg = 76.5,
                goal = "保持体能",
                injuries = "",
                weeklyTrainingDays = 3,
                preferredMinutes = 45,
            )
            val avatarUri = createMediaImage("io-avatar-${System.nanoTime()}", width = 12, height = 8)
            try {
                repository.saveProfileAvatar(avatarUri)
            } finally {
                context.contentResolver.delete(avatarUri, null, null)
            }

            repository.completeSet(
                sessionId = "io-compat-complete",
                exerciseId = "0748",
                setIndex = 1,
                reps = 10,
                weightKg = 20.0,
                feeling = "合适",
            )
            repository.finishWorkoutSession(
                sessionId = "io-compat-complete",
                plannedWorkoutId = null,
                venueId = venue.id,
                exerciseId = "0748",
            )
            val reviewDraft = repository.generateWorkoutReviewDraft(
                sessionId = "io-compat-complete",
                postWorkoutFeeling = "正常疲劳",
                postWorkoutNote = "IO 路径",
            )
            repository.resolveWorkoutReviewDraft(reviewDraft.id, applyAdjustment = false)
            repository.skipExercise(
                sessionId = "io-compat-skip",
                plannedWorkoutId = null,
                venueId = venue.id,
                exerciseId = "0289",
                setIndex = 1,
                reason = "IO 路径",
            )

            val plan = repository.createWorkoutFromTemplate(
                name = "IO 计划",
                scheduledDate = "2026-07-20",
                venueId = venue.id,
            )
            val plannedExercise = store.plannedExercises(plan.id).first()
            repository.updatePlannedExerciseTarget(plannedExercise.id, 4, "10", 20.0, "IO 更新")
            repository.replaceExercise(plannedExercise.id, plannedExercise.exerciseId, "IO 替换")
            repository.rescheduleWorkout(plan.id, "2026-07-21")
            repository.skipWorkout(plan.id)
            repository.deleteWorkout(plan.id)

            val adjustment = repository.generateTrainingAdjustment("0748")
            repository.confirmTrainingAdjustment(adjustment.id)
            repository.selectAiProvider(
                FitnessRepository.OPENAI_PROVIDER_ID,
                "https://api.openai.com/v1",
                "gpt-5-mini",
            )
            repository.saveAiApiKey(FitnessRepository.OPENAI_PROVIDER_ID, "io-test-key")
            val backup = repository.exportBackupJson()
            repository.importBackupJson(backup)
            repository.resetLocalData()

            assertEquals("公司健身房", repository.appState().first().venue?.name)
        }
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

    private fun createMediaImage(name: String, width: Int, height: Int): Uri {
        val uri = requireNotNull(
            context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                },
            ),
        )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(40, 160, 220))
        }
        context.contentResolver.openOutputStream(uri).use { output ->
            requireNotNull(output)
            require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
        return uri
    }

    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) {
        try {
            block(this)
        } finally {
            recycle()
        }
    }

    private suspend fun seedProfile() {
        repository.saveUserProfile(
            displayName = "测试用户",
            birthYear = 1994,
            heightCm = 176.0,
            weightKg = 75.0,
            goal = "保持体能",
            injuries = "",
            weeklyTrainingDays = 3,
            preferredMinutes = 35,
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
            "id": "${FitnessRepository.OPENAI_PROVIDER_ID}",
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
