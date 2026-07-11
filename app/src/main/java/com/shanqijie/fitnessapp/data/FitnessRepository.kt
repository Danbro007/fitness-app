package com.shanqijie.fitnessapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.Base64
import com.shanqijie.fitnessapp.ai.AiChatClient
import com.shanqijie.fitnessapp.ai.AiGatewayFactory
import com.shanqijie.fitnessapp.ai.AiProviderConfig
import com.shanqijie.fitnessapp.ai.AiProviderCatalog
import com.shanqijie.fitnessapp.ai.AiTestResult
import com.shanqijie.fitnessapp.domain.ExerciseAsset
import com.shanqijie.fitnessapp.domain.ExerciseManifestParser
import com.shanqijie.fitnessapp.domain.HomePrimaryAction
import com.shanqijie.fitnessapp.domain.HomeSnapshot
import com.shanqijie.fitnessapp.domain.NutritionSummary
import com.shanqijie.fitnessapp.domain.NutritionReference
import com.shanqijie.fitnessapp.domain.WorkoutSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import java.io.File
import java.io.FileOutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun interface TimeProvider {
    fun currentTimeMillis(): Long
}

class FitnessRepository(
    private val context: Context,
    private val store: FitnessStore,
    private val credentialStore: AiCredentialStore = AiCredentialStore(context),
    private val timeProvider: TimeProvider = TimeProvider(System::currentTimeMillis),
    private val aiGatewayFactory: AiGatewayFactory = AiGatewayFactory(::AiChatClient),
) {
    private val refreshSignal = MutableStateFlow(0)

    suspend fun bootstrap(): BootstrapResult = withContext(Dispatchers.IO) {
        val manifestJson = context.assets
            .open("exercise-media/manifest.json")
            .bufferedReader()
            .use { it.readText() }
        val manifest = ExerciseManifestParser.parse(manifestJson)
        val assetPackId = manifest.packageStrategy.packs.firstOrNull()?.id ?: "exercise-gifs-pack-001"

        if (store.exerciseMediaCount() == 0) {
            store.upsertExerciseMedia(
                manifest.files.map { it.toEntity(assetPackId) },
            )
        }

        val now = timeProvider.currentTimeMillis()
        seedDefaultVenue(now)
        seedDefaultEquipment(now)
        seedDefaultAiProviders(now)

        val smithBench = store.exerciseById(SMITH_BENCH_PRESS_ID)
            ?: store.exerciseByName("smith bench press")
            ?: ExerciseManifestParser.findSmithBenchPress(manifest).toEntity(assetPackId)
        val smithCount = store.exercisesByEquipment("smith machine").size
        Log.i(
            "FitnessApp",
            "bootstrap gifs=${manifest.counts.localOrDownloaded}, failed=${manifest.counts.failed}, " +
                "smithMachine=$smithCount, smithBench=${smithBench.localPath}",
        )

        BootstrapResult(
            totalGifCount = manifest.counts.localOrDownloaded,
            failedGifCount = manifest.counts.failed,
            assetPackId = assetPackId,
            assetPackSizeMb = manifest.packageStrategy.packs.firstOrNull()?.sizeMb ?: 0.0,
            smithMachineExerciseCount = smithCount,
            smithBenchPress = smithBench,
            sessionId = DEFAULT_SESSION_ID,
        )
    }

    fun appState(): Flow<FitnessAppState> =
        refreshSignal.map {
            withContext(Dispatchers.IO) { currentAppState() }
        }

    fun completedSetCount(sessionId: String): Flow<Int> =
        refreshSignal.map {
            withContext(Dispatchers.IO) { store.completedSetCount(sessionId) }
        }

    fun setLogs(sessionId: String): Flow<List<WorkoutSetLogEntity>> =
        refreshSignal.map {
            withContext(Dispatchers.IO) { store.setLogs(sessionId) }
        }

    fun completedSetCount(sessionId: String, exerciseId: String): Flow<Int> =
        refreshSignal.map {
            withContext(Dispatchers.IO) { store.completedSetCount(sessionId, exerciseId) }
        }

    fun exerciseLogs(sessionId: String, exerciseId: String): Flow<List<WorkoutSetLogEntity>> =
        refreshSignal.map {
            withContext(Dispatchers.IO) { store.setLogs(sessionId, exerciseId) }
        }

    fun sessionIdFor(plannedWorkoutId: String, exerciseId: String): String =
        "$LEGACY_SESSION_PREFIX$plannedWorkoutId"

    suspend fun renameDefaultVenue(name: String) = withContext(Dispatchers.IO) {
        renameVenue(DEFAULT_VENUE_ID, name)
    }

    suspend fun renameVenue(id: String, name: String) = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "场地名称不能为空" }
        store.updateVenueName(id, trimmedName, updatedAt = System.currentTimeMillis())
        refreshSignal.update { it + 1 }
    }

    suspend fun addVenue(name: String): TrainingVenueEntity = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "场地名称不能为空" }
        val now = System.currentTimeMillis()
        val venue = TrainingVenueEntity(
            id = "venue-${UUID.randomUUID()}",
            name = trimmedName,
            isDefault = false,
            createdAt = now,
            updatedAt = now,
        )
        store.upsertVenue(venue)
        refreshSignal.update { it + 1 }
        venue
    }

    suspend fun setDefaultVenue(id: String) = withContext(Dispatchers.IO) {
        store.setDefaultVenue(id, updatedAt = System.currentTimeMillis())
        refreshSignal.update { it + 1 }
    }

    suspend fun addDefaultEquipment(name: String, category: String = "custom") = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "器械名称不能为空" }
        val now = System.currentTimeMillis()
        val equipment = EquipmentEntity(
            id = "equipment-${UUID.randomUUID()}",
            name = trimmedName,
            category = category,
            createdAt = now,
            updatedAt = now,
        )
        store.upsertEquipment(equipment)
        store.defaultVenue()?.let { venue ->
            store.upsertVenueEquipment(
                VenueEquipmentEntity(
                    venueId = venue.id,
                    equipmentId = equipment.id,
                    available = true,
                    updatedAt = now,
                ),
            )
        }
        refreshSignal.update { it + 1 }
    }

    suspend fun deleteEquipment(id: String) = withContext(Dispatchers.IO) {
        store.deleteEquipment(id)
        refreshSignal.update { it + 1 }
    }

    suspend fun bindEquipmentToVenue(venueId: String, equipmentId: String, available: Boolean) = withContext(Dispatchers.IO) {
        requireNotNull(store.venue(venueId)) { "场地不存在" }
        require(store.allEquipment().any { it.id == equipmentId }) { "器械不存在" }
        store.upsertVenueEquipment(
            VenueEquipmentEntity(
                venueId = venueId,
                equipmentId = equipmentId,
                available = available,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        refreshSignal.update { it + 1 }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) = withContext(Dispatchers.IO) {
        store.putPreference(ONBOARDING_COMPLETED_KEY, completed.toString())
        refreshSignal.update { it + 1 }
    }

    suspend fun setCalendarMode(mode: String) = withContext(Dispatchers.IO) {
        require(mode in setOf("周", "月", "年")) { "不支持的日历维度" }
        store.putPreference(CALENDAR_MODE_KEY, mode)
        refreshSignal.update { it + 1 }
    }

    suspend fun searchExercises(query: String, limit: Int = 100): List<ExerciseMediaEntity> =
        withContext(Dispatchers.IO) {
            store.searchExercises(query = query, limit = limit)
        }

    suspend fun saveUserProfile(
        displayName: String,
        birthYear: Int,
        heightCm: Double,
        weightKg: Double,
        goal: String,
        injuries: String,
        weeklyTrainingDays: Int,
        preferredMinutes: Int,
        bodyMeasurement: BodyMeasurement = BodyMeasurement(),
    ) = withContext(Dispatchers.IO) {
        val trimmedName = displayName.trim()
        require(trimmedName.length in 1..30) { "昵称需要在 1 到 30 个字符之间" }
        require(birthYear in 1940..LocalDate.now().year) { "出生年份不合理" }
        require(heightCm in 80.0..240.0) { "身高不合理" }
        require(weightKg in 25.0..250.0) { "体重不合理" }
        require(weeklyTrainingDays in 1..7) { "每周训练天数需要在 1 到 7 之间" }
        require(preferredMinutes in 15..180) { "单次训练时长需要在 15 到 180 分钟之间" }
        require(injuries.length <= 500) { "伤病与注意事项不能超过 500 个字符" }
        validateBodyMeasurement(bodyMeasurement)
        store.upsertUserProfile(
            UserProfileEntity(
                id = LOCAL_PROFILE_ID,
                displayName = trimmedName,
                birthYear = birthYear,
                heightCm = heightCm,
                weightKg = weightKg,
                goal = goal.trim().ifEmpty { "增肌减脂" },
                injuries = injuries.trim(),
                weeklyTrainingDays = weeklyTrainingDays,
                preferredMinutes = preferredMinutes,
                updatedAt = System.currentTimeMillis(),
                bodyMeasurement = bodyMeasurement.normalized(),
                avatarPath = store.userProfile()?.avatarPath.orEmpty(),
            ),
        )
        refreshSignal.update { it + 1 }
    }

    suspend fun saveProfileAvatar(source: Uri) = withContext(Dispatchers.IO) {
        val profile = requireNotNull(store.userProfile()) { "请先保存训练档案" }
        val mime = context.contentResolver.getType(source).orEmpty()
        require(mime in setOf("image/jpeg", "image/png", "image/webp")) { "仅支持 JPG、PNG 或 WebP" }
        context.contentResolver.openAssetFileDescriptor(source, "r")?.use { descriptor ->
            require(descriptor.length < 0 || descriptor.length <= 5L * 1024 * 1024) { "头像不能超过 5 MB" }
        }
        val decoded = context.contentResolver.openInputStream(source)?.use(BitmapFactory::decodeStream)
            ?: error("无法读取头像")
        val side = minOf(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val output = if (side > 512) Bitmap.createScaledBitmap(square, 512, 512, true) else square
        val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
        val relativePath = "avatars/profile-${System.currentTimeMillis()}.jpg"
        val target = File(context.filesDir, relativePath)
        FileOutputStream(target).use { stream ->
            require(output.compress(Bitmap.CompressFormat.JPEG, 85, stream)) { "头像压缩失败" }
        }
        val oldPath = profile.avatarPath
        store.upsertUserProfile(profile.copy(avatarPath = relativePath, updatedAt = System.currentTimeMillis()))
        if (oldPath.isNotBlank() && oldPath != relativePath) File(context.filesDir, oldPath).delete()
        if (output !== square) output.recycle()
        if (square !== decoded) square.recycle()
        decoded.recycle()
        refreshSignal.update { it + 1 }
    }

    suspend fun updatePlannedWorkoutDetails(id: String, name: String, scheduledDate: String) = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        val trimmedDate = scheduledDate.trim()
        require(trimmedName.isNotEmpty()) { "计划名称不能为空" }
        LocalDate.parse(trimmedDate)
        store.updatePlannedWorkoutDetails(
            id = id,
            name = trimmedName,
            scheduledDate = trimmedDate,
            updatedAt = System.currentTimeMillis(),
        )
        refreshSignal.update { it + 1 }
    }

    suspend fun createWorkoutFromTemplate(name: String, scheduledDate: String, venueId: String): PlannedWorkoutEntity =
        withContext(Dispatchers.IO) {
            val trimmedName = name.trim().ifEmpty { "自定义训练" }
            val trimmedDate = scheduledDate.trim()
            LocalDate.parse(trimmedDate)
            val now = System.currentTimeMillis()
            val workout = PlannedWorkoutEntity(
                id = "planned-${UUID.randomUUID()}",
                name = trimmedName,
                scheduledDate = trimmedDate,
                venueId = venueId.ifBlank { DEFAULT_VENUE_ID },
                status = "planned",
                createdAt = now,
                updatedAt = now,
            )
            store.upsertPlannedWorkout(workout)
            defaultTemplateExercises().forEachIndexed { index, exercise ->
                store.upsertPlannedExercise(
                    PlannedExerciseEntity(
                        id = "${workout.id}-${exercise.exerciseId}",
                        plannedWorkoutId = workout.id,
                        exerciseId = exercise.exerciseId,
                        orderIndex = index + 1,
                        targetSets = if (index == 0) 4 else 3,
                        targetReps = if (index == 0) "8-12" else "10-12",
                        targetWeightKg = 0.0,
                        note = if (index == 0) "主项" else "辅助",
                    ),
                )
            }
            refreshSignal.update { it + 1 }
            workout
        }

    suspend fun createMonthlyPlanFromTemplate(startDate: String, venueId: String): List<PlannedWorkoutEntity> =
        withContext(Dispatchers.IO) {
            val start = LocalDate.parse(startDate.trim())
            val targetVenueId = venueId.ifBlank { store.defaultVenue()?.id ?: DEFAULT_VENUE_ID }
            val template = defaultTemplateExercises()
            val now = System.currentTimeMillis()
            val workouts = (0 until 4).map { week ->
                PlannedWorkoutEntity(
                    id = "planned-month-${UUID.randomUUID()}",
                    name = "月计划 第 ${week + 1} 周",
                    scheduledDate = start.plusDays(week * 7L).toString(),
                    venueId = targetVenueId,
                    status = "planned",
                    createdAt = now,
                    updatedAt = now,
                )
            }
            workouts.forEach { workout ->
                store.upsertPlannedWorkout(workout)
                template.forEachIndexed { index, exercise ->
                    store.upsertPlannedExercise(
                        PlannedExerciseEntity(
                            id = "${workout.id}-${exercise.exerciseId}-${index + 1}",
                            plannedWorkoutId = workout.id,
                            exerciseId = exercise.exerciseId,
                            orderIndex = index + 1,
                            targetSets = if (index == 0) 4 else 3,
                            targetReps = if (index == 0) "8-12" else "10-12",
                        targetWeightKg = 0.0,
                            note = if (index == 0) "主项" else "辅助",
                        ),
                    )
                }
            }
            refreshSignal.update { it + 1 }
            workouts
        }

    suspend fun copyWorkout(id: String, newScheduledDate: String): PlannedWorkoutEntity = withContext(Dispatchers.IO) {
        val source = requireNotNull(store.plannedWorkouts().firstOrNull { it.id == id }) { "计划不存在" }
        val trimmedDate = newScheduledDate.trim()
        LocalDate.parse(trimmedDate)
        val now = System.currentTimeMillis()
        val copied = source.copy(
            id = "planned-${UUID.randomUUID()}",
            name = "${source.name} 复制",
            scheduledDate = trimmedDate,
            status = "planned",
            createdAt = now,
            updatedAt = now,
        )
        store.upsertPlannedWorkout(copied)
        store.plannedExercises(source.id).forEach { exercise ->
            store.upsertPlannedExercise(
                exercise.copy(
                    id = "${copied.id}-${exercise.exerciseId}-${exercise.orderIndex}",
                    plannedWorkoutId = copied.id,
                ),
            )
        }
        refreshSignal.update { it + 1 }
        copied
    }

    suspend fun deleteWorkout(id: String) = withContext(Dispatchers.IO) {
        store.deletePlannedWorkout(id)
        refreshSignal.update { it + 1 }
    }

    suspend fun rescheduleWorkout(id: String, scheduledDate: String) = withContext(Dispatchers.IO) {
        val workout = requireNotNull(store.plannedWorkouts().firstOrNull { it.id == id }) { "计划不存在" }
        updatePlannedWorkoutDetails(id = id, name = workout.name, scheduledDate = scheduledDate)
    }

    suspend fun skipWorkout(id: String) = withContext(Dispatchers.IO) {
        store.updatePlannedWorkoutStatus(id, status = "skipped", updatedAt = System.currentTimeMillis())
        refreshSignal.update { it + 1 }
    }

    suspend fun updatePlannedExerciseTarget(
        id: String,
        targetSets: Int,
        targetReps: String,
        targetWeightKg: Double,
        note: String,
    ) = withContext(Dispatchers.IO) {
        val trimmedReps = targetReps.trim()
        require(targetSets in 1..20) { "目标组数需要在 1 到 20 之间" }
        require(trimmedReps.isNotEmpty()) { "目标次数不能为空" }
        require(targetWeightKg >= 0.0) { "目标重量不能为负数" }
        store.updatePlannedExerciseTarget(
            id = id,
            targetSets = targetSets,
            targetReps = trimmedReps,
            targetWeightKg = targetWeightKg,
            note = note.trim(),
        )
        refreshSignal.update { it + 1 }
    }

    suspend fun replaceExercise(plannedExerciseId: String, replacementExerciseId: String, note: String) =
        withContext(Dispatchers.IO) {
            val trimmedReplacement = replacementExerciseId.trim()
            require(trimmedReplacement.isNotEmpty()) { "替换动作不能为空" }
            store.updatePlannedExerciseExercise(
                id = plannedExerciseId,
                exerciseId = trimmedReplacement,
                note = note.trim().ifEmpty { "替换动作" },
            )
            refreshSignal.update { it + 1 }
        }

    suspend fun startWorkout(planId: String): WorkoutSessionEntity = withContext(Dispatchers.IO) {
        val session = store.transaction { startWorkoutInTransaction(planId) }
        refreshSignal.update { it + 1 }
        session
    }

    suspend fun recordWorkoutSet(
        sessionId: String,
        exerciseId: String,
        reps: Int,
        weightKg: Double,
        feeling: String,
        restSeconds: Int = DEFAULT_REST_SECONDS,
    ): WorkoutSetLogEntity = withContext(Dispatchers.IO) {
        val log = store.transaction {
            recordWorkoutSetInTransaction(
                sessionId = sessionId,
                exerciseId = exerciseId,
                reps = reps,
                weightKg = weightKg,
                feeling = feeling,
                restSeconds = restSeconds,
            )
        }
        refreshSignal.update { it + 1 }
        log
    }

    suspend fun recordWorkoutSet(
        sessionId: String,
        reps: Int,
        weightKg: Double,
        feeling: String,
        restSeconds: Int = DEFAULT_REST_SECONDS,
    ): WorkoutSetLogEntity = withContext(Dispatchers.IO) {
        val log = store.transaction {
            val exerciseId = requireNotNull(store.workoutSession(sessionId)?.currentExerciseId) { "当前动作不存在" }
            recordWorkoutSetInTransaction(
                sessionId = sessionId,
                exerciseId = exerciseId,
                reps = reps,
                weightKg = weightKg,
                feeling = feeling,
                restSeconds = restSeconds,
            )
        }
        refreshSignal.update { it + 1 }
        log
    }

    suspend fun selectWorkoutExercise(sessionId: String, exerciseId: String): WorkoutSessionEntity =
        withContext(Dispatchers.IO) {
            val session = requireInProgressSession(sessionId)
            require(store.sessionExercises(sessionId).any { it.exerciseId == exerciseId }) { "动作不属于本次训练" }
            val now = timeProvider.currentTimeMillis()
            store.updateWorkoutRuntime(
                id = sessionId,
                currentExerciseId = exerciseId,
                restEndsAt = session.restEndsAt,
                pausedAt = session.pausedAt,
                updatedAt = now,
            )
            refreshSignal.update { it + 1 }
            requireNotNull(store.workoutSession(sessionId))
        }

    suspend fun startRest(sessionId: String, durationSeconds: Int = DEFAULT_REST_SECONDS): WorkoutSessionEntity =
        withContext(Dispatchers.IO) {
            val session = requireInProgressSession(sessionId)
            require(durationSeconds >= 0) { "休息时长不能为负数" }
            val now = timeProvider.currentTimeMillis()
            store.updateWorkoutRuntime(
                id = sessionId,
                currentExerciseId = session.currentExerciseId,
                restEndsAt = now + durationSeconds.toLong() * 1_000L,
                pausedAt = null,
                updatedAt = now,
            )
            refreshSignal.update { it + 1 }
            requireNotNull(store.workoutSession(sessionId))
        }

    suspend fun skipRest(sessionId: String): WorkoutSessionEntity = withContext(Dispatchers.IO) {
        val session = requireInProgressSession(sessionId)
        val now = timeProvider.currentTimeMillis()
        store.updateWorkoutRuntime(
            id = sessionId,
            currentExerciseId = session.currentExerciseId,
            restEndsAt = null,
            pausedAt = null,
            updatedAt = now,
        )
        refreshSignal.update { it + 1 }
        requireNotNull(store.workoutSession(sessionId))
    }

    suspend fun addExerciseToSession(
        sessionId: String,
        exerciseId: String,
        targetSets: Int = DEFAULT_TARGET_SETS,
        targetReps: String = DEFAULT_TARGET_REPS,
        targetWeightKg: Double = 0.0,
    ): WorkoutSessionExerciseEntity = withContext(Dispatchers.IO) {
        val session = requireInProgressSession(sessionId)
        requireNotNull(store.exerciseById(exerciseId)) { "动作不存在" }
        require(targetSets in 1..20) { "目标组数需要在 1 到 20 之间" }
        require(targetReps.isNotBlank()) { "目标次数不能为空" }
        require(targetWeightKg >= 0.0) { "目标重量不能为负数" }

        val existingExercises = store.sessionExercises(sessionId)
        val exercise = existingExercises.firstOrNull { it.exerciseId == exerciseId }
            ?: WorkoutSessionExerciseEntity(
                id = "$sessionId-library-${UUID.randomUUID()}",
                sessionId = sessionId,
                exerciseId = exerciseId,
                orderIndex = (existingExercises.maxOfOrNull { it.orderIndex } ?: 0) + 1,
                targetSets = targetSets,
                targetReps = targetReps.trim(),
                targetWeightKg = targetWeightKg,
                status = "pending",
            ).also(store::upsertSessionExercise)
        val now = timeProvider.currentTimeMillis()
        store.updateWorkoutRuntime(
            id = sessionId,
            currentExerciseId = exerciseId,
            restEndsAt = session.restEndsAt,
            pausedAt = session.pausedAt,
            updatedAt = now,
        )
        refreshSignal.update { it + 1 }
        exercise
    }

    suspend fun finishWorkout(sessionId: String): WorkoutSummary = withContext(Dispatchers.IO) {
        val summary = store.transaction { finishWorkoutInTransaction(sessionId) }
        refreshSignal.update { it + 1 }
        summary
    }

    suspend fun workoutSummary(sessionId: String): WorkoutSummary = withContext(Dispatchers.IO) {
        workoutSummaryFromStored(sessionId)
    }

    suspend fun addExerciseToPlan(planId: String, exerciseId: String): PlannedExerciseEntity =
        withContext(Dispatchers.IO) {
            require(store.plannedWorkouts().any { it.id == planId }) { "计划不存在" }
            requireNotNull(store.exerciseById(exerciseId)) { "动作不存在" }
            val existingExercises = store.plannedExercises(planId)
            existingExercises.firstOrNull { it.exerciseId == exerciseId }?.let { return@withContext it }
            val plannedExercise = PlannedExerciseEntity(
                id = "$planId-library-${UUID.randomUUID()}",
                plannedWorkoutId = planId,
                exerciseId = exerciseId,
                orderIndex = (existingExercises.maxOfOrNull { it.orderIndex } ?: 0) + 1,
                targetSets = DEFAULT_TARGET_SETS,
                targetReps = DEFAULT_TARGET_REPS,
                targetWeightKg = 0.0,
                note = "从动作库添加",
            )
            store.upsertPlannedExercise(plannedExercise)
            refreshSignal.update { it + 1 }
            plannedExercise
        }

    suspend fun resetLocalData() = withContext(Dispatchers.IO) {
        credentialStore.deleteApiKey(DEEPSEEK_PROVIDER_ID)
        AiProviderCatalog.entries.forEach { credentialStore.deleteApiKey(it.id) }
        store.clearPersonalData()
        val now = timeProvider.currentTimeMillis()
        seedDefaultVenue(now)
        seedDefaultEquipment(now)
        seedDefaultAiProviders(now)
        refreshSignal.update { it + 1 }
    }

    fun homeSnapshot(state: FitnessAppState, today: LocalDate = LocalDate.now()): HomeSnapshot {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)
        val completedThisWeek = state.workoutSessions.count { session ->
            session.status == "completed" && session.endedAt
                ?.let(::localDateAt)
                ?.let { date -> !date.isBefore(weekStart) && !date.isAfter(weekEnd) } == true
        }
        val workoutsThisWeek = state.plannedWorkouts.filter { workout ->
            parseLocalDate(workout.scheduledDate)
                ?.let { date -> !date.isBefore(weekStart) && !date.isAfter(weekEnd) } == true
        }
        val targetThisWeek = state.userProfile?.weeklyTrainingDays ?: workoutsThisWeek.size
        val nextWorkout = state.plannedWorkouts
            .filter { it.status == "planned" || it.status == "in_progress" }
            .sortedWith(compareBy({ parseLocalDate(it.scheduledDate) ?: LocalDate.MAX }, { it.createdAt }))
            .let { workouts ->
                workouts.firstOrNull { workout ->
                    parseLocalDate(workout.scheduledDate)?.isBefore(today) == false
                } ?: workouts.firstOrNull()
            }
        val activeSession = state.unfinishedSessions.maxByOrNull { it.startedAt }
        val completedToday = state.workoutSessions
            .filter { it.status == "completed" && it.endedAt?.let(::localDateAt) == today }
            .maxByOrNull { it.endedAt ?: Long.MIN_VALUE }
        val action = when {
            activeSession != null -> HomePrimaryAction.Resume(activeSession.id)
            completedToday != null -> HomePrimaryAction.Result(completedToday.id)
            nextWorkout != null -> HomePrimaryAction.Start(nextWorkout.id)
            else -> HomePrimaryAction.CreatePlan
        }
        return HomeSnapshot(
            action = action,
            completedThisWeek = completedThisWeek,
            targetThisWeek = targetThisWeek,
            nextWorkout = nextWorkout,
        )
    }

    fun nutritionSummary(state: FitnessAppState, date: LocalDate = LocalDate.now()): NutritionSummary {
        val logs = state.foodLogs.filter { it.confirmed && it.loggedDate == date.toString() }
        return NutritionSummary(
            calories = logs.sumOf { it.calories },
            protein = logs.sumOf { it.proteinGrams },
            carbs = logs.sumOf { it.carbsGrams },
            fat = logs.sumOf { it.fatGrams },
            reference = state.userProfile?.let(::nutritionReferenceFor),
        )
    }

    private fun nutritionReferenceFor(profile: UserProfileEntity): NutritionReference {
        val weight = profile.weightKg.coerceIn(35.0, 250.0)
        val (caloriesPerKg, proteinPerKg, carbsPerKg) = when (profile.goal) {
            "减脂" -> Triple(28.0, 1.6, 2.5)
            "增肌" -> Triple(33.0, 1.8, 4.0)
            else -> Triple(30.0, 1.6, 3.0)
        }
        return NutritionReference(
            calories = (weight * caloriesPerKg).toInt(),
            protein = weight * proteinPerKg,
            carbs = weight * carbsPerKg,
            fat = weight * 0.8,
        )
    }

    private fun validateBodyMeasurement(measurement: BodyMeasurement) {
        measurement.measuredAt.takeIf(String::isNotBlank)?.let(LocalDate::parse)
        require(measurement.bodyType.trim().length <= 20) { "体型描述不能超过 20 个字符" }
        measurement.bodyFatPercentage?.let { require(it in 0.0..75.0) { "体脂率需要在 0 到 75% 之间" } }
        measurement.bodyFatMassKg?.let { require(it in 0.0..150.0) { "体脂肪需要在 0 到 150 kg 之间" } }
        measurement.skeletalMuscleKg?.let { require(it in 0.0..100.0) { "骨骼肌需要在 0 到 100 kg 之间" } }
        measurement.bodyWaterKg?.let { require(it in 0.0..150.0) { "身体水分需要在 0 到 150 kg 之间" } }
        measurement.basalMetabolismKcal?.let { require(it in 500..5000) { "基础代谢需要在 500 到 5000 kcal 之间" } }
        measurement.waistHipRatio?.let { require(it in 0.3..2.0) { "腰臀比需要在 0.3 到 2.0 之间" } }
        measurement.bmi?.let { require(it in 10.0..80.0) { "BMI 需要在 10 到 80 之间" } }
    }

    private fun BodyMeasurement.normalized(): BodyMeasurement = copy(
        measuredAt = measuredAt.trim(),
        bodyType = bodyType.trim(),
    )

    private fun buildPlanPrompt(
        profile: UserProfileEntity?,
        days: Int,
        minutes: Int,
        venueName: String,
        equipment: String,
    ): String = buildString {
        appendLine("昵称：${profile?.displayName ?: "未填写"}")
        appendLine("出生年：${profile?.birthYear ?: "未填写"}")
        appendLine("身高：${profile?.heightCm?.toMetricText() ?: "未填写"} cm")
        appendLine("体重：${profile?.weightKg?.toMetricText() ?: "未填写"} kg")
        appendLine("训练目标：${profile?.goal ?: "未填写"}")
        appendLine("每周训练：$days 天")
        appendLine("单次时长：$minutes 分钟")
        appendLine(profile?.bodyMeasurement?.toPlanContext() ?: "体测数据：未填写")
        appendLine("伤病与注意事项：${profile?.injuries?.ifBlank { "未填写" } ?: "未填写"}")
        appendLine("场地：$venueName")
        append("可用器械：$equipment")
    }

    internal fun buildPlanPromptForTesting(profile: UserProfileEntity): String =
        buildPlanPrompt(
            profile = profile,
            days = profile.weeklyTrainingDays,
            minutes = profile.preferredMinutes,
            venueName = "测试场地",
            equipment = "哑铃",
        )

    private fun BodyMeasurement.toPlanContext(): String {
        return buildString {
            appendLine("体脂率：${bodyFatPercentage?.let { "${it.toMetricText()}%" } ?: "未填写"}")
            appendLine("体脂肪：${bodyFatMassKg?.let { "${it.toMetricText()} kg" } ?: "未填写"}")
            appendLine("BMI：${bmi?.toMetricText() ?: "未填写"}")
            appendLine("骨骼肌：${skeletalMuscleKg?.let { "${it.toMetricText()} kg" } ?: "未填写"}")
            appendLine("身体水分：${bodyWaterKg?.let { "${it.toMetricText()} kg" } ?: "未填写"}")
            appendLine("基础代谢：${basalMetabolismKcal?.let { "$it kcal" } ?: "未填写"}")
            append("腰臀比：${waistHipRatio?.toMetricText() ?: "未填写"}")
        }
    }

    private fun Double.toMetricText(): String =
        if (this % 1.0 == 0.0) toInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", this)

    suspend fun completeSet(
        sessionId: String,
        exerciseId: String,
        setIndex: Int,
        reps: Int,
        weightKg: Double,
        feeling: String,
    ) = withContext(Dispatchers.IO) {
        store.transaction {
            val session = compatibilitySessionInTransaction(
                requestedSessionId = sessionId,
                plannedWorkoutId = null,
                venueId = DEFAULT_VENUE_ID,
                exerciseId = exerciseId,
            )
            val selected = selectCompatibilityExerciseInTransaction(session, exerciseId)
            recordWorkoutSetInTransaction(
                sessionId = selected.id,
                exerciseId = exerciseId,
                reps = reps,
                weightKg = weightKg,
                feeling = feeling,
                restSeconds = DEFAULT_REST_SECONDS,
                expectedSetIndex = setIndex,
            )
        }
        refreshSignal.update { it + 1 }
    }

    suspend fun skipExercise(
        sessionId: String,
        plannedWorkoutId: String?,
        venueId: String,
        exerciseId: String,
        setIndex: Int,
        reason: String,
    ) = withContext(Dispatchers.IO) {
        val trimmedReason = reason.trim().ifEmpty { "跳过" }
        store.transaction {
            val session = compatibilitySessionInTransaction(
                requestedSessionId = sessionId,
                plannedWorkoutId = plannedWorkoutId,
                venueId = venueId,
                exerciseId = exerciseId,
            )
            val selected = selectCompatibilityExerciseInTransaction(session, exerciseId)
            val sessionExercise = requireNotNull(
                store.sessionExercises(selected.id).firstOrNull { it.exerciseId == exerciseId },
            )
            val nextSetIndex = (store.setLogs(selected.id, exerciseId).maxOfOrNull { it.setIndex } ?: 0) + 1
            require(setIndex == nextSetIndex) { "组序号必须连续" }
            require(nextSetIndex <= sessionExercise.targetSets) { "已达到目标组数" }
            val now = timeProvider.currentTimeMillis()
            store.insertSetLog(
                WorkoutSetLogEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = selected.id,
                    exerciseId = exerciseId,
                    setIndex = nextSetIndex,
                    actualReps = 0,
                    actualWeightKg = 0.0,
                    feeling = "跳过：$trimmedReason",
                    completed = false,
                    completedAt = now,
                    sessionExerciseId = sessionExercise.id,
                ),
            )
            store.updateSessionExerciseStatus(sessionExercise.id, status = "skipped")
        }
        refreshSignal.update { it + 1 }
    }

    suspend fun finishWorkoutSession(
        sessionId: String,
        plannedWorkoutId: String?,
        venueId: String,
        exerciseId: String,
    ) = withContext(Dispatchers.IO) {
        store.transaction {
            val session = compatibilitySessionInTransaction(
                requestedSessionId = sessionId,
                plannedWorkoutId = plannedWorkoutId,
                venueId = venueId,
                exerciseId = exerciseId,
            )
            finishWorkoutInTransaction(session.id)
        }
        refreshSignal.update { it + 1 }
    }

    suspend fun resumeSession(sessionId: String) = withContext(Dispatchers.IO) {
        store.updateWorkoutSessionStatus(
            id = sessionId,
            status = "in_progress",
            endedAt = null,
            updatedAt = System.currentTimeMillis(),
        )
        refreshSignal.update { it + 1 }
    }

    suspend fun generateTrainingAdjustment(exerciseId: String): TrainingAdjustmentEntity = withContext(Dispatchers.IO) {
        val exercise = store.exerciseById(exerciseId)
        val recentLogs = store.allSetLogs()
            .filter { it.exerciseId == exerciseId && it.completed }
            .sortedByDescending { it.completedAt }
        val latest = recentLogs.firstOrNull()
        val provider = activeAiProviderOrDefault()
        val localTitle: String
        val localContent: String
        when {
            latest == null -> {
                localTitle = "先记录训练"
                localContent = "这个动作还没有完成组记录。先完成 1-2 次训练后，再根据次数、重量和体感调整。"
            }

            latest.feeling.contains("轻松") -> {
                localTitle = "下次加重"
                localContent = "最近一组反馈轻松，下次可加 2.5kg，保持 ${latest.actualReps} 次附近，优先保证动作稳定。"
            }

            latest.feeling.contains("吃力") -> {
                localTitle = "维持或减量"
                localContent = "最近一组反馈吃力，下次先维持 ${formatWeight(latest.actualWeightKg)}kg，或减 2.5kg 换取更完整的动作质量。"
            }

            else -> {
                localTitle = "维持推进"
                localContent = "最近一组反馈合适，下次维持 ${formatWeight(latest.actualWeightKg)}kg，目标多完成 1-2 次。"
            }
        }
        val apiKey = credentialStore.loadApiKey(provider.id)
        val aiContent = if (apiKey != null && latest != null) {
            runCatching {
                aiGatewayFactory.create(provider.toConfig()).complete(
                    apiKey = apiKey,
                    systemPrompt = "你是力量训练教练。请基于训练记录给出下一次训练调整建议，回复不超过 80 字中文。",
                    userPrompt = "动作：${exercise?.name ?: exerciseId}\n最近记录：${latest.actualWeightKg}kg x ${latest.actualReps}，体感 ${latest.feeling}\n本地建议：$localContent",
                    temperature = 0.2,
                )
            }.getOrNull()
        } else {
            null
        }
        val now = System.currentTimeMillis()
        val adjustment = TrainingAdjustmentEntity(
            id = "adjust-${UUID.randomUUID()}",
            exerciseId = exerciseId,
            title = localTitle,
            content = aiContent?.takeIf { it.isNotBlank() } ?: localContent,
            status = "draft",
            createdAt = now,
            updatedAt = now,
            confirmedAt = null,
        )
        store.upsertTrainingAdjustment(adjustment)
        refreshSignal.update { it + 1 }
        adjustment
    }

    suspend fun confirmTrainingAdjustment(id: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        store.updateTrainingAdjustmentStatus(id, status = "confirmed", confirmedAt = now, updatedAt = now)
        refreshSignal.update { it + 1 }
    }

    suspend fun logFood(
        name: String,
        calories: Int,
        proteinGrams: Double,
        carbsGrams: Double,
        fatGrams: Double,
        source: String = "manual",
        imageNote: String = "",
        imageUri: String = "",
        providerId: String = "",
        model: String = "",
    ): FoodLogEntity = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "食物名称不能为空" }
        require(calories in 0..5000) { "热量不合理" }
        val now = System.currentTimeMillis()
        val foodLog = FoodLogEntity(
            id = "food-${UUID.randomUUID()}",
            loggedDate = LocalDate.now().toString(),
            name = trimmedName,
            calories = calories,
            proteinGrams = proteinGrams.coerceAtLeast(0.0),
            carbsGrams = carbsGrams.coerceAtLeast(0.0),
            fatGrams = fatGrams.coerceAtLeast(0.0),
            source = source,
            imageNote = imageNote.trim(),
            imageUri = imageUri,
            providerId = providerId,
            model = model,
            confirmed = true,
            createdAt = now,
        )
        store.insertFoodLog(foodLog)
        refreshSignal.update { it + 1 }
        foodLog
    }

    suspend fun generateFoodEstimateDraft(
        description: String,
        imageUri: String = "",
        imageMimeType: String = "",
        imageBase64: String = "",
    ): AiDraftEntity = withContext(Dispatchers.IO) {
        val trimmedDescription = description.trim()
        require(trimmedDescription.isNotEmpty()) { "请先描述食物或照片内容" }
        val estimate = estimateFood(trimmedDescription)
        val provider = activeAiProviderOrDefault()
        val apiKey = credentialStore.loadApiKey(provider.id)
        val aiNote = if (apiKey != null) {
            runCatching {
                val client = aiGatewayFactory.create(provider.toConfig())
                if (imageBase64.isNotBlank() && imageMimeType.isNotBlank()) {
                    client.completeVision(
                        apiKey = apiKey,
                        systemPrompt = "你是健身饮食助手。请识别食物并估算热量、蛋白质、碳水和脂肪，回复简洁中文。",
                        userPrompt = trimmedDescription,
                        imageMimeType = imageMimeType,
                        imageBase64 = imageBase64,
                        temperature = 0.1,
                    )
                } else {
                    client.complete(
                        apiKey = apiKey,
                        systemPrompt = "你是健身饮食助手。请根据用户描述估算热量、蛋白质、碳水和脂肪，回复简洁中文。",
                        userPrompt = trimmedDescription,
                        temperature = 0.1,
                    )
                }
            }.getOrNull()
        } else {
            null
        }
        val now = System.currentTimeMillis()
        val draft = AiDraftEntity(
            id = "draft-${UUID.randomUUID()}",
            type = "food_estimate",
            title = "饮食估算：${estimate.name}",
            content = buildString {
                append("约 ${estimate.calories} 千卡 · 蛋白质 ${formatMacro(estimate.protein)}g · 碳水 ${formatMacro(estimate.carbs)}g · 脂肪 ${formatMacro(estimate.fat)}g")
                append("\n依据：$trimmedDescription")
                if (!aiNote.isNullOrBlank()) {
                    append("\nAI 建议：${aiNote.take(240)}")
                }
            },
            status = "draft",
            createdAt = now,
            updatedAt = now,
            metadataJson = foodDraftMetadataJson(
                imageUri = imageUri,
                providerId = provider.id,
                model = provider.model,
            ),
            confirmedAt = null,
        )
        store.upsertAiDraft(draft)
        refreshSignal.update { it + 1 }
        draft
    }

    suspend fun confirmFoodEstimateDraft(draftId: String): FoodLogEntity = withContext(Dispatchers.IO) {
        val now = timeProvider.currentTimeMillis()
        val foodLog = store.transaction {
            val draft = requireNotNull(aiDraft(draftId)) { "草稿不存在" }
            require(draft.type == "food_estimate") { "不是饮食估算草稿" }
            require(draft.status == "draft") { "草稿已经确认或失效" }
            val estimate = parseFoodEstimateDraft(draft)
            val metadata = parseFoodDraftMetadata(draft.metadataJson)
            val log = FoodLogEntity(
                id = "food-${UUID.randomUUID()}",
                loggedDate = localDateAt(now).toString(),
                name = estimate.name,
                calories = estimate.calories,
                proteinGrams = estimate.protein,
                carbsGrams = estimate.carbs,
                fatGrams = estimate.fat,
                source = if (metadata.imageUri.isNotBlank()) "vision_ai" else "ai_estimate",
                imageNote = if (metadata.imageUri.isNotBlank()) "已选择食物照片" else draft.content.substringAfter("依据：", missingDelimiterValue = ""),
                imageUri = metadata.imageUri,
                providerId = metadata.providerId,
                model = metadata.model,
                confirmed = true,
                createdAt = now,
            )
            insertFoodLog(log)
            updateAiDraftStatus(draft.id, status = "confirmed", confirmedAt = now, updatedAt = now)
            log
        }
        refreshSignal.update { it + 1 }
        foodLog
    }

    suspend fun generateWeeklyPlanDraft(): AiDraftEntity = withContext(Dispatchers.IO) {
        val profile = requireNotNull(store.userProfile()) { "请先补全训练偏好与体测档案" }
        val venue = store.defaultVenue()
        val equipment = venue
            ?.let { store.equipmentNamesForVenue(it.id) }
            ?.joinToString("、")
            ?.ifEmpty { null }
            ?: store.equipmentNamesForVenue().joinToString("、").ifEmpty { "自重" }
        val now = System.currentTimeMillis()
        val days = profile?.weeklyTrainingDays ?: 3
        val minutes = profile?.preferredMinutes ?: 45
        val provider = activeAiProviderOrDefault()
        val apiKey = credentialStore.loadApiKey(provider.id)
        val aiContent = if (apiKey != null) {
            runCatching {
                aiGatewayFactory.create(provider.toConfig()).complete(
                    apiKey = apiKey,
                    systemPrompt = "你是健身计划助手。请基于用户目标、体测数据、场地和器械生成一周训练草稿。不要作医学诊断；如有伤病须优先保守安排。结果必须提醒用户确认后再保存。",
                    userPrompt = buildPlanPrompt(profile, days, minutes, venue?.name ?: "默认场地", equipment),
                    temperature = 0.2,
                )
            }.getOrNull()
        } else {
            null
        }
        val draft = AiDraftEntity(
            id = "draft-${UUID.randomUUID()}",
            type = "weekly_plan",
            title = "周计划草稿：$days 天",
            content = aiContent?.takeIf { it.isNotBlank() }
                ?: "按 ${profile?.goal ?: "增肌减脂"} 目标，建议每周 $days 天、每次 $minutes 分钟。\n${profile?.bodyMeasurement?.toPlanContext() ?: "未填写体测数据"}\n场地：${venue?.name ?: "默认场地"}\n可用器械：$equipment\n确认后会新建一节本地训练计划。",
            status = "draft",
            createdAt = now,
            updatedAt = now,
            confirmedAt = null,
        )
        store.upsertAiDraft(draft)
        refreshSignal.update { it + 1 }
        draft
    }

    suspend fun confirmWeeklyPlanDraft(draftId: String): PlannedWorkoutEntity = withContext(Dispatchers.IO) {
        val draft = requireNotNull(store.aiDraft(draftId)) { "草稿不存在" }
        require(draft.type == "weekly_plan") { "不是周计划草稿" }
        val workout = createWorkoutFromTemplate(
            name = "AI 生成训练",
            scheduledDate = LocalDate.now().plusDays(1).toString(),
            venueId = store.defaultVenue()?.id ?: DEFAULT_VENUE_ID,
        )
        val now = System.currentTimeMillis()
        store.updateAiDraftStatus(draftId, status = "confirmed", confirmedAt = now, updatedAt = now)
        refreshSignal.update { it + 1 }
        workout
    }

    suspend fun confirmFourWeekPlanDraft(draftId: String): List<PlannedWorkoutEntity> =
        withContext(Dispatchers.IO) {
            val now = timeProvider.currentTimeMillis()
            val workouts = store.transaction {
                val draft = requireNotNull(aiDraft(draftId)) { "草稿不存在" }
                require(draft.type == "weekly_plan") { "不是周计划草稿" }
                require(draft.status == "draft") { "草稿已经确认或失效" }
                val template = defaultTemplateExercises()
                require(template.isNotEmpty()) { "没有可用的动作模板" }
                val venueId = defaultVenue()?.id ?: DEFAULT_VENUE_ID
                val startDate = localDateAt(now).plusDays(1)
                val generated = (0 until 4).map { week ->
                    PlannedWorkoutEntity(
                        id = "planned-four-week-${UUID.randomUUID()}",
                        name = "AI 生成训练 第 ${week + 1} 周",
                        scheduledDate = startDate.plusDays(week * 7L).toString(),
                        venueId = venueId,
                        status = "planned",
                        createdAt = now,
                        updatedAt = now,
                    )
                }
                generated.forEach { workout ->
                    upsertPlannedWorkout(workout)
                    template.forEachIndexed { index, exercise ->
                        upsertPlannedExercise(
                            PlannedExerciseEntity(
                                id = "${workout.id}-${exercise.exerciseId}-${index + 1}",
                                plannedWorkoutId = workout.id,
                                exerciseId = exercise.exerciseId,
                                orderIndex = index + 1,
                                targetSets = if (index == 0) 4 else 3,
                                targetReps = if (index == 0) "8-12" else "10-12",
                            targetWeightKg = 0.0,
                                note = if (index == 0) "主项" else "辅助",
                            ),
                        )
                    }
                }
                updateAiDraftStatus(draftId, status = "confirmed", confirmedAt = now, updatedAt = now)
                generated
            }
            refreshSignal.update { it + 1 }
            workouts
        }

    suspend fun generateReplacementDraft(exerciseId: String): AiDraftEntity = withContext(Dispatchers.IO) {
        val current = store.exerciseById(exerciseId)
        val replacement = store.exercisesByEquipment(current?.equipment ?: "smith machine")
            .firstOrNull { it.exerciseId != exerciseId }
        val now = System.currentTimeMillis()
        val draft = AiDraftEntity(
            id = "draft-${UUID.randomUUID()}",
            type = "exercise_replacement",
            title = "动作替换建议",
            content = replacement?.let {
                "建议替换为 ${it.name}。目标肌群：${it.target}，器械：${it.equipment}。"
            } ?: "当前动作暂无同器械替代项，可在动作库按目标肌群筛选。",
            status = "draft",
            createdAt = now,
            updatedAt = now,
            confirmedAt = null,
        )
        store.upsertAiDraft(draft)
        refreshSignal.update { it + 1 }
        draft
    }

    suspend fun confirmAiDraft(draftId: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        store.updateAiDraftStatus(draftId, status = "confirmed", confirmedAt = now, updatedAt = now)
        refreshSignal.update { it + 1 }
    }

    suspend fun saveAiApiKey(providerId: String, apiKey: String) = withContext(Dispatchers.IO) {
        val provider = requireNotNull(store.aiProvider(providerId)) { "智能服务不存在" }
        credentialStore.saveApiKey(providerId, apiKey)
        store.upsertAiProvider(provider.copy(apiKeyStored = true, updatedAt = System.currentTimeMillis()))
        refreshSignal.update { it + 1 }
    }

    suspend fun selectAiProvider(providerId: String, endpoint: String, model: String) = withContext(Dispatchers.IO) {
        val catalog = requireNotNull(AiProviderCatalog.entry(providerId)) { "不支持的智能服务" }
        require(endpoint in catalog.endpoints) { "接口地址不属于当前服务商" }
        require(model in catalog.models || model == store.aiProvider(providerId)?.model) { "模型不属于当前服务商" }
        val now = System.currentTimeMillis()
        store.aiProviders().forEach { current ->
            store.upsertAiProvider(
                if (current.id == providerId) {
                    current.copy(baseUrl = endpoint, model = model, enabled = true, updatedAt = now)
                } else {
                    current.copy(enabled = false, updatedAt = now)
                },
            )
        }
        refreshSignal.update { it + 1 }
    }

    suspend fun testAiProvider(providerId: String): AiTestResult = withContext(Dispatchers.IO) {
        val provider = store.aiProvider(providerId)
            ?: return@withContext AiTestResult(success = false, message = "智能服务不存在")
        val apiKey = credentialStore.loadApiKey(providerId)
            ?: return@withContext AiTestResult(success = false, message = "请先保存接口密钥")

        runCatching {
            aiGatewayFactory.create(provider.toConfig()).testConnection(apiKey)
        }.getOrElse { error ->
            AiTestResult(
                success = false,
                message = when (error) {
                    is SocketTimeoutException -> "连接超时，请稍后重试"
                    is UnknownHostException -> "当前无网络，请检查连接"
                    else -> "连接失败：${error.message ?: error::class.java.simpleName}"
                },
            )
        }
    }

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        FitnessBackupCodec.encode(
            FitnessBackupPayload(
                version = 4,
                exportedAt = timeProvider.currentTimeMillis(),
                userProfile = store.userProfile(),
                avatarBase64 = store.userProfile()?.avatarPath?.takeIf(String::isNotBlank)
                    ?.let { File(context.filesDir, it) }
                    ?.takeIf(File::isFile)
                    ?.readBytes()
                    ?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
                    .orEmpty(),
                venues = store.trainingVenues(),
                equipment = store.allEquipment(),
                venueEquipment = store.venueEquipment(),
                preferences = store.preferences(),
                plannedWorkouts = store.plannedWorkouts(),
                plannedExercises = store.allPlannedExercises(),
                workoutSessions = store.workoutSessions(),
                sessionExercises = store.workoutSessions().flatMap { store.sessionExercises(it.id) },
                setLogs = store.allSetLogs(),
                foodLogs = store.foodLogs(),
                aiDrafts = store.aiDrafts(),
                trainingAdjustments = store.trainingAdjustments(),
                aiProviders = store.aiProviders(),
            ),
        )
    }

    suspend fun importBackupJson(rawJson: String) = withContext(Dispatchers.IO) {
        val payload = FitnessBackupCodec.decode(rawJson)
        validateBackupPayload(payload)
        store.transaction {
            store.clearPersonalData()
            val restoredProfile = payload.userProfile?.let { profile ->
                val restoredPath = runCatching {
                    if (payload.avatarBase64.isBlank()) return@runCatching ""
                    val bytes = Base64.decode(payload.avatarBase64, Base64.DEFAULT)
                    require(bytes.size <= 5 * 1024 * 1024) { "头像文件过大" }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("头像损坏")
                    bitmap.recycle()
                    File(context.filesDir, "avatars").mkdirs()
                    val relative = "avatars/restored-profile.jpg"
                    File(context.filesDir, relative).writeBytes(bytes)
                    relative
                }.getOrDefault("")
                profile.copy(avatarPath = restoredPath)
            }
            restoredProfile?.let(store::upsertUserProfile)
            payload.venues.forEach(store::upsertVenue)
            payload.equipment.forEach(store::upsertEquipment)
            payload.venueEquipment.forEach(store::upsertVenueEquipment)
            payload.preferences.forEach { (key, value) -> store.putPreference(key, value) }
            payload.plannedWorkouts.forEach(store::upsertPlannedWorkout)
            payload.plannedExercises.forEach(store::upsertPlannedExercise)
            payload.workoutSessions.forEach(store::upsertWorkoutSession)
            payload.sessionExercises.forEach(store::upsertSessionExercise)
            payload.setLogs.forEach(store::insertSetLog)
            payload.foodLogs.forEach(store::insertFoodLog)
            payload.aiDrafts.forEach(store::upsertAiDraft)
            payload.trainingAdjustments.forEach(store::upsertTrainingAdjustment)
            payload.aiProviders.filter { AiProviderCatalog.entry(it.id) != null }.forEach(store::upsertAiProvider)
        }
        refreshSignal.update { it + 1 }
    }

    private fun validateBackupPayload(payload: FitnessBackupPayload) {
        require(payload.version in 1..4) { "不支持的备份版本" }
        requireUnique("场地 ID", payload.venues.map { it.id })
        requireUnique("器械 ID", payload.equipment.map { it.id })
        requireUnique("计划 ID", payload.plannedWorkouts.map { it.id })
        requireUnique("计划动作 ID", payload.plannedExercises.map { it.id })
        requireUnique("训练记录 ID", payload.workoutSessions.map { it.id })
        requireUnique("训练动作 ID", payload.sessionExercises.map { it.id })
        requireUnique("组记录 ID", payload.setLogs.map { it.id })
        requireUnique("饮食记录 ID", payload.foodLogs.map { it.id })
        requireUnique("智能草稿 ID", payload.aiDrafts.map { it.id })
        requireUnique("训练调整 ID", payload.trainingAdjustments.map { it.id })
        requireUnique("智能服务 ID", payload.aiProviders.map { it.id })
        requireUnique(
            "训练动作",
            payload.sessionExercises.map { it.sessionId to it.exerciseId },
        )
        requireUnique(
            "训练组序号",
            payload.setLogs.map { Triple(it.sessionId, it.exerciseId, it.setIndex) },
        )
        payload.setLogs.forEach { log ->
            require(log.setIndex > 0) { "组序号必须大于 0" }
            require(log.actualReps >= 0) { "组次数不能为负数" }
            require(log.actualWeightKg >= 0.0) { "组重量不能为负数" }
            if (payload.version >= 2) require(log.feeling.isNotBlank()) { "组体感不能为空" }
        }
        if (payload.version >= 2) {
            val sessionIds = payload.workoutSessions.mapTo(mutableSetOf()) { it.id }
            val sessionExerciseById = payload.sessionExercises.associateBy { it.id }
            payload.sessionExercises.forEach { sessionExercise ->
                require(sessionExercise.sessionId in sessionIds) { "训练动作缺少对应训练记录" }
                require(sessionExercise.targetSets > 0) { "训练动作目标组数必须大于 0" }
            }
            payload.setLogs.forEach { log ->
                log.sessionExerciseId?.let { linkedId ->
                    val linked = requireNotNull(sessionExerciseById[linkedId]) { "组记录缺少对应训练动作" }
                    require(linked.sessionId == log.sessionId && linked.exerciseId == log.exerciseId) {
                        "组记录与训练动作不匹配"
                    }
                }
            }
        }
    }

    private fun <T> requireUnique(label: String, values: List<T>) {
        require(values.size == values.distinct().size) { "$label 重复" }
    }

    private fun startWorkoutInTransaction(
        planId: String,
        preferredSessionId: String? = null,
    ): WorkoutSessionEntity {
        val plan = requireNotNull(store.plannedWorkouts().firstOrNull { it.id == planId }) { "计划不存在" }
        store.workoutSessions()
            .firstOrNull { it.plannedWorkoutId == planId && it.status == "in_progress" }
            ?.let { return it }
        require(plan.status in STARTABLE_WORKOUT_STATUSES) { "训练计划当前不可开始" }

        val plannedExercises = store.plannedExercises(planId)
        require(plannedExercises.isNotEmpty()) { "训练计划没有动作" }
        val now = timeProvider.currentTimeMillis()
        val sessionId = preferredSessionId ?: UUID.randomUUID().toString()
        val firstExercise = plannedExercises.first()
        val session = WorkoutSessionEntity(
            id = sessionId,
            plannedWorkoutId = plan.id,
            venueId = plan.venueId,
            exerciseId = firstExercise.exerciseId,
            status = "in_progress",
            startedAt = now,
            endedAt = null,
            updatedAt = now,
            currentExerciseId = firstExercise.exerciseId,
        )
        store.upsertWorkoutSession(session)
        plannedExercises.forEach { plannedExercise ->
            store.upsertSessionExercise(
                WorkoutSessionExerciseEntity(
                    id = "$sessionId-${plannedExercise.id}",
                    sessionId = sessionId,
                    exerciseId = plannedExercise.exerciseId,
                    orderIndex = plannedExercise.orderIndex,
                    targetSets = plannedExercise.targetSets,
                    targetReps = plannedExercise.targetReps,
                    targetWeightKg = plannedExercise.targetWeightKg,
                    status = "pending",
                ),
            )
        }
        store.updatePlannedWorkoutStatus(plan.id, status = "in_progress", updatedAt = now)
        return session
    }

    private fun recordWorkoutSetInTransaction(
        sessionId: String,
        exerciseId: String,
        reps: Int,
        weightKg: Double,
        feeling: String,
        restSeconds: Int,
        expectedSetIndex: Int? = null,
    ): WorkoutSetLogEntity {
        require(reps in 1..50) { "次数需要在 1 到 50 之间" }
        require(weightKg >= 0.0) { "重量不能为负数" }
        require(feeling.isNotBlank()) { "体感不能为空" }
        require(restSeconds >= 0) { "休息时长不能为负数" }
        val session = requireInProgressSession(sessionId)
        val sessionExercise = requireNotNull(
            store.sessionExercises(sessionId).firstOrNull { it.exerciseId == exerciseId },
        ) { "动作不属于本次训练" }
        require(session.currentExerciseId == exerciseId) { "请先选择当前动作" }

        val existingLogs = store.setLogs(sessionId, exerciseId)
        val nextSetIndex = (existingLogs.maxOfOrNull { it.setIndex } ?: 0) + 1
        expectedSetIndex?.let { require(it == nextSetIndex) { "组序号必须连续" } }
        require(nextSetIndex <= sessionExercise.targetSets) { "已达到目标组数" }
        val now = timeProvider.currentTimeMillis()
        val log = WorkoutSetLogEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            exerciseId = exerciseId,
            setIndex = nextSetIndex,
            actualReps = reps,
            actualWeightKg = weightKg,
            feeling = feeling.trim(),
            completed = true,
            completedAt = now,
            sessionExerciseId = sessionExercise.id,
        )
        store.insertSetLog(log)
        if (nextSetIndex == sessionExercise.targetSets) {
            store.updateSessionExerciseStatus(sessionExercise.id, status = "completed")
        }
        store.updateWorkoutRuntime(
            id = sessionId,
            currentExerciseId = exerciseId,
            restEndsAt = now + restSeconds.toLong() * 1_000L,
            pausedAt = null,
            updatedAt = now,
        )
        return log
    }

    private fun compatibilitySessionInTransaction(
        requestedSessionId: String,
        plannedWorkoutId: String?,
        venueId: String,
        exerciseId: String,
    ): WorkoutSessionEntity {
        store.workoutSession(requestedSessionId)?.let { session ->
            ensureSessionExerciseSnapshotInTransaction(session, exerciseId)
            return session
        }

        val resolvedPlanId = plannedWorkoutId
            ?: requestedSessionId
                .takeIf { it.startsWith(LEGACY_SESSION_PREFIX) }
                ?.removePrefix(LEGACY_SESSION_PREFIX)
                ?.takeIf { candidate -> store.plannedWorkouts().any { it.id == candidate } }
        val plan = resolvedPlanId?.let { id -> store.plannedWorkouts().firstOrNull { it.id == id } }
        if (plan != null) {
            val session = store.workoutSessions()
                .firstOrNull { it.plannedWorkoutId == plan.id && it.status == "in_progress" }
                ?: if (store.plannedExercises(plan.id).isEmpty()) {
                    require(plan.status in STARTABLE_WORKOUT_STATUSES) { "训练计划当前不可开始" }
                    val now = timeProvider.currentTimeMillis()
                    WorkoutSessionEntity(
                        id = requestedSessionId,
                        plannedWorkoutId = plan.id,
                        venueId = plan.venueId,
                        exerciseId = exerciseId,
                        status = "in_progress",
                        startedAt = now,
                        endedAt = null,
                        updatedAt = now,
                        currentExerciseId = exerciseId,
                    ).also { created ->
                        store.upsertWorkoutSession(created)
                        store.updatePlannedWorkoutStatus(plan.id, status = "in_progress", updatedAt = now)
                    }
                } else {
                    startWorkoutInTransaction(
                        planId = plan.id,
                        preferredSessionId = requestedSessionId,
                    )
                }
            ensureSessionExerciseSnapshotInTransaction(session, exerciseId)
            return session
        }

        val now = timeProvider.currentTimeMillis()
        val session = WorkoutSessionEntity(
            id = requestedSessionId,
            plannedWorkoutId = plannedWorkoutId,
            venueId = venueId,
            exerciseId = exerciseId,
            status = "in_progress",
            startedAt = now,
            endedAt = null,
            updatedAt = now,
            currentExerciseId = exerciseId,
        )
        store.upsertWorkoutSession(session)
        ensureSessionExerciseSnapshotInTransaction(session, exerciseId)
        return session
    }

    private fun ensureSessionExerciseSnapshotInTransaction(
        session: WorkoutSessionEntity,
        exerciseId: String,
    ): WorkoutSessionExerciseEntity {
        val existing = store.sessionExercises(session.id)
        existing.firstOrNull { it.exerciseId == exerciseId }?.let { return it }
        val plannedExercise = session.plannedWorkoutId
            ?.let(store::plannedExercises)
            ?.firstOrNull { it.exerciseId == exerciseId }
        val snapshot = WorkoutSessionExerciseEntity(
            id = "${session.id}-compat-$exerciseId",
            sessionId = session.id,
            exerciseId = exerciseId,
            orderIndex = plannedExercise?.orderIndex ?: ((existing.maxOfOrNull { it.orderIndex } ?: 0) + 1),
            targetSets = plannedExercise?.targetSets ?: DEFAULT_TARGET_SETS,
            targetReps = plannedExercise?.targetReps ?: DEFAULT_TARGET_REPS,
            targetWeightKg = plannedExercise?.targetWeightKg ?: 0.0,
            status = "pending",
        )
        store.upsertSessionExercise(snapshot)
        return snapshot
    }

    private fun selectCompatibilityExerciseInTransaction(
        session: WorkoutSessionEntity,
        exerciseId: String,
    ): WorkoutSessionEntity {
        ensureSessionExerciseSnapshotInTransaction(session, exerciseId)
        if (session.currentExerciseId == exerciseId) return session
        val now = timeProvider.currentTimeMillis()
        store.updateWorkoutRuntime(
            id = session.id,
            currentExerciseId = exerciseId,
            restEndsAt = session.restEndsAt,
            pausedAt = session.pausedAt,
            updatedAt = now,
        )
        return requireNotNull(store.workoutSession(session.id))
    }

    private fun finishWorkoutInTransaction(sessionId: String): WorkoutSummary {
        val session = requireNotNull(store.workoutSession(sessionId)) { "训练记录不存在" }
        if (session.status !in setOf("completed", "partial")) {
            require(session.status == "in_progress") { "训练当前不可结束" }
            val now = timeProvider.currentTimeMillis()
            val summary = workoutSummaryFromStored(sessionId)
            val finalStatus = if (summary.isFullyCompleted) "completed" else "partial"
            store.updateWorkoutRuntime(
                id = sessionId,
                currentExerciseId = session.currentExerciseId,
                restEndsAt = null,
                pausedAt = null,
                updatedAt = now,
            )
            store.updateWorkoutSessionStatus(
                id = sessionId,
                status = finalStatus,
                endedAt = now,
                updatedAt = now,
            )
            session.plannedWorkoutId?.let { planId ->
                store.updatePlannedWorkoutStatus(
                    planId,
                    status = if (summary.isFullyCompleted) "completed" else "planned",
                    updatedAt = now,
                )
            }
        }
        return workoutSummaryFromStored(sessionId)
    }

    private fun requireInProgressSession(sessionId: String): WorkoutSessionEntity {
        val session = requireNotNull(store.workoutSession(sessionId)) { "训练记录不存在" }
        require(session.status == "in_progress") { "训练未在进行中" }
        return session
    }

    private fun workoutSummaryFromStored(sessionId: String): WorkoutSummary {
        val session = requireNotNull(store.workoutSession(sessionId)) { "训练记录不存在" }
        val logs = store.setLogs(sessionId).filter { it.completed }
        val endedAt = session.endedAt ?: timeProvider.currentTimeMillis()
        return WorkoutSummary(
            sessionId = session.id,
            completedSets = logs.size,
            targetSets = store.sessionExercises(sessionId).sumOf { it.targetSets },
            totalVolumeKg = logs.sumOf { it.actualWeightKg * it.actualReps },
            durationSeconds = ((endedAt - session.startedAt).coerceAtLeast(0L)) / 1_000L,
            feelingCounts = logs.groupingBy { it.feeling }.eachCount(),
        )
    }

    private fun localDateAt(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun parseLocalDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value) }.getOrNull()

    private fun currentAppState(): FitnessAppState =
        store.allPlannedExercises().let { plannedExercises ->
            val plannedExerciseViews = plannedExercises.mapNotNull { plannedExercise ->
                store.exerciseById(plannedExercise.exerciseId)?.let { media ->
                    PlannedExerciseView(plannedExercise, media)
                }
            }
            val selectedVenue = store.defaultVenue() ?: store.venue(DEFAULT_VENUE_ID)
            val venueEquipment = selectedVenue
                ?.let { store.equipmentForVenue(it.id) }
                ?.ifEmpty { store.allEquipment() }
                ?: store.allEquipment()
            val workoutSessions = store.workoutSessions()
            val workoutSessionExercises = workoutSessions.flatMap { store.sessionExercises(it.id) }
            val aiProviders = store.aiProviders().map { provider ->
                provider.copy(apiKeyStored = credentialStore.loadApiKey(provider.id) != null)
            }

            FitnessAppState(
                venue = selectedVenue,
                venues = store.trainingVenues(),
                equipment = store.allEquipment(),
                equipmentForSelectedVenue = venueEquipment,
                venueEquipment = store.venueEquipment(),
                plannedWorkouts = store.plannedWorkouts(),
                plannedExercises = plannedExercises,
                plannedExerciseViews = plannedExerciseViews,
                workoutSessions = workoutSessions,
                unfinishedSessions = workoutSessions.filter { it.status == "in_progress" },
                workoutSessionExercises = workoutSessionExercises,
                workoutSetLogs = store.allSetLogs(),
                userProfile = store.userProfile(),
                onboardingCompleted = store.preference(ONBOARDING_COMPLETED_KEY)?.toBooleanStrictOrNull() ?: false,
                foodLogs = store.foodLogs(),
                aiDrafts = store.aiDrafts(),
                trainingAdjustments = store.trainingAdjustments(),
                smithMachineExercises = store.exercisesByEquipment("smith machine"),
                exercises = store.allExercises(limit = 1_500),
                aiProviders = aiProviders,
                preferences = store.preferences(),
            )
        }

    private fun defaultTemplateExercises(): List<ExerciseMediaEntity> =
        listOfNotNull(
            store.exerciseById(SMITH_BENCH_PRESS_ID),
            store.exerciseById("0289"),
        ).distinctBy { it.exerciseId }
            .ifEmpty { store.exercisesByEquipment("smith machine").take(2) }

    private fun seedDefaultVenue(now: Long) {
        if (store.venue(DEFAULT_VENUE_ID) != null) return

        store.upsertVenue(
            TrainingVenueEntity(
                id = DEFAULT_VENUE_ID,
                name = "公司健身房",
                isDefault = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun seedDefaultEquipment(now: Long) {
        val existingNames = store.allEquipment().map { it.name }.toSet()
        val seeds = listOf(
            EquipmentSeed("equipment-smith-machine", "史密斯机", "machine"),
            EquipmentSeed("equipment-dumbbell", "哑铃", "free-weight"),
            EquipmentSeed("equipment-barbell", "杠铃", "free-weight"),
            EquipmentSeed("equipment-treadmill", "跑步机", "cardio"),
        )
        seeds.filterNot { it.name in existingNames }
            .forEach { seed ->
                store.upsertEquipment(
                    EquipmentEntity(
                        id = seed.id,
                        name = seed.name,
                        category = seed.category,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        seeds.forEach { seed ->
            store.upsertVenueEquipment(
                VenueEquipmentEntity(
                    venueId = DEFAULT_VENUE_ID,
                    equipmentId = seed.id,
                    available = true,
                    updatedAt = now,
                ),
            )
        }
    }

    private fun seedDefaultAiProviders(now: Long) {
        store.deleteAiProvider(DEEPSEEK_PROVIDER_ID)
        AiProviderCatalog.entries.forEachIndexed { index, catalog ->
            val existing = store.aiProvider(catalog.id)
            store.upsertAiProvider(
                AiProviderEntity(
                    id = catalog.id,
                    displayName = catalog.displayName,
                    baseUrl = existing?.baseUrl?.takeIf { it in catalog.endpoints } ?: catalog.endpoints.first(),
                    model = existing?.model ?: catalog.models.first(),
                    enabled = existing?.enabled ?: (index == 0),
                    apiKeyStored = credentialStore.loadApiKey(catalog.id) != null,
                    updatedAt = existing?.updatedAt ?: now,
                ),
            )
        }
    }

    private fun activeAiProviderOrDefault(): AiProviderEntity =
        store.aiProviders().firstOrNull { it.enabled }
            ?: store.aiProvider(OPENAI_PROVIDER_ID)
            ?: AiProviderEntity(
                id = OPENAI_PROVIDER_ID,
                displayName = "OpenAI",
                baseUrl = "https://api.openai.com/v1",
                model = "gpt-5-mini",
                enabled = true,
                apiKeyStored = credentialStore.loadApiKey(OPENAI_PROVIDER_ID) != null,
                updatedAt = System.currentTimeMillis(),
            )

    private fun ExerciseAsset.toEntity(assetPackId: String): ExerciseMediaEntity =
        ExerciseMediaEntity(
            exerciseId = exerciseId,
            name = name,
            bodyPart = bodyPart,
            equipment = equipment,
            target = target,
            mediaId = mediaId,
            localPath = "exercise-media/$localPath",
            assetPackId = assetPackId,
            bytes = bytes,
            sha256 = sha256,
        )

    companion object {
        const val DEFAULT_VENUE_ID = "venue-company-gym"
        const val DEFAULT_WORKOUT_ID = "planned-chest-strength-a"
        const val DEFAULT_SESSION_ID = "session-local-smith-bench"
        const val SMITH_BENCH_PRESS_ID = "0748"
        const val DEEPSEEK_PROVIDER_ID = "deepseek"
        const val OPENAI_PROVIDER_ID = "openai"
        const val LOCAL_PROFILE_ID = "profile-local"
        const val ONBOARDING_COMPLETED_KEY = "onboarding_completed"
        const val CALENDAR_MODE_KEY = "calendar_mode"
        const val DEFAULT_REST_SECONDS = 90
        const val DEFAULT_TARGET_SETS = 3
        const val DEFAULT_TARGET_REPS = "8-12"
        private const val LEGACY_SESSION_PREFIX = "session-"
        private val STARTABLE_WORKOUT_STATUSES = setOf("planned", "in_progress")
    }
}

data class BootstrapResult(
    val totalGifCount: Int,
    val failedGifCount: Int,
    val assetPackId: String,
    val assetPackSizeMb: Double,
    val smithMachineExerciseCount: Int,
    val smithBenchPress: ExerciseMediaEntity,
    val sessionId: String,
)

data class FitnessAppState(
    val venue: TrainingVenueEntity?,
    val venues: List<TrainingVenueEntity>,
    val equipment: List<EquipmentEntity>,
    val equipmentForSelectedVenue: List<EquipmentEntity>,
    val venueEquipment: List<VenueEquipmentEntity>,
    val plannedWorkouts: List<PlannedWorkoutEntity>,
    val plannedExercises: List<PlannedExerciseEntity>,
    val plannedExerciseViews: List<PlannedExerciseView>,
    val workoutSessions: List<WorkoutSessionEntity>,
    val unfinishedSessions: List<WorkoutSessionEntity>,
    val workoutSetLogs: List<WorkoutSetLogEntity>,
    val userProfile: UserProfileEntity?,
    val onboardingCompleted: Boolean,
    val foodLogs: List<FoodLogEntity>,
    val aiDrafts: List<AiDraftEntity>,
    val trainingAdjustments: List<TrainingAdjustmentEntity>,
    val smithMachineExercises: List<ExerciseMediaEntity>,
    val exercises: List<ExerciseMediaEntity>,
    val aiProviders: List<AiProviderEntity>,
    val workoutSessionExercises: List<WorkoutSessionExerciseEntity> = emptyList(),
    val preferences: Map<String, String> = emptyMap(),
)

data class PlannedExerciseView(
    val plannedExercise: PlannedExerciseEntity,
    val media: ExerciseMediaEntity,
)

private data class EquipmentSeed(
    val id: String,
    val name: String,
    val category: String,
)

private data class FoodEstimate(
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

private data class FoodDraftMetadata(
    val imageUri: String,
    val providerId: String,
    val model: String,
)

private val repositoryJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun AiProviderEntity.toConfig(): AiProviderConfig =
    AiProviderConfig(
        id = id,
        displayName = displayName,
        baseUrl = baseUrl,
        model = model,
    )


private fun estimateFood(description: String): FoodEstimate {
    val normalized = description.lowercase()
    return when {
        "鸡" in description && ("米饭" in description || "饭" in description) -> FoodEstimate(
            name = "鸡胸肉米饭",
            calories = 620,
            protein = 42.0,
            carbs = 68.0,
            fat = 16.0,
        )

        "牛" in description && ("饭" in description || "面" in description) -> FoodEstimate(
            name = "牛肉主食餐",
            calories = 760,
            protein = 46.0,
            carbs = 82.0,
            fat = 24.0,
        )

        "沙拉" in description || normalized.contains("salad") -> FoodEstimate(
            name = "轻食沙拉",
            calories = 380,
            protein = 24.0,
            carbs = 28.0,
            fat = 18.0,
        )

        else -> FoodEstimate(
            name = description.take(12).ifBlank { "餐食估算" },
            calories = 520,
            protein = 28.0,
            carbs = 56.0,
            fat = 18.0,
        )
    }
}

private fun parseFoodEstimateDraft(draft: AiDraftEntity): FoodEstimate {
    val name = draft.title.removePrefix("饮食估算：").ifBlank { "餐食估算" }
    val numbers = Regex("""(\d+(?:\.\d+)?)""").findAll(draft.content).map { it.value.toDouble() }.toList()
    return FoodEstimate(
        name = name,
        calories = numbers.getOrNull(0)?.toInt() ?: 0,
        protein = numbers.getOrNull(1) ?: 0.0,
        carbs = numbers.getOrNull(2) ?: 0.0,
        fat = numbers.getOrNull(3) ?: 0.0,
    )
}

private fun foodDraftMetadataJson(imageUri: String, providerId: String, model: String): String {
    val metadata = buildJsonObject {
        if (imageUri.isNotBlank()) put("imageUri", JsonPrimitive(imageUri))
        if (providerId.isNotBlank()) put("providerId", JsonPrimitive(providerId))
        if (model.isNotBlank()) put("model", JsonPrimitive(model))
    }
    return if (metadata.isEmpty()) {
        ""
    } else {
        repositoryJson.encodeToString(JsonObject.serializer(), metadata)
    }
}

private fun parseFoodDraftMetadata(rawJson: String): FoodDraftMetadata {
    if (rawJson.isBlank()) return FoodDraftMetadata(imageUri = "", providerId = "", model = "")
    val root = runCatching { repositoryJson.parseToJsonElement(rawJson).jsonObject }.getOrNull()
        ?: return FoodDraftMetadata(imageUri = "", providerId = "", model = "")
    return FoodDraftMetadata(
        imageUri = root["imageUri"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        providerId = root["providerId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        model = root["model"]?.jsonPrimitive?.contentOrNull.orEmpty(),
    )
}

private fun formatMacro(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

private fun formatWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
