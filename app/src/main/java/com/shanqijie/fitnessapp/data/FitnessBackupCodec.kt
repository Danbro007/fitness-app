package com.shanqijie.fitnessapp.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate

@Serializable
data class FitnessBackupPayload(
    val version: Int,
    val exportedAt: Long,
    val userProfile: UserProfileEntity?,
    val avatarBase64: String = "",
    val venues: List<TrainingVenueEntity>,
    val equipment: List<EquipmentEntity>,
    val venueEquipment: List<VenueEquipmentEntity> = emptyList(),
    val preferences: Map<String, String> = emptyMap(),
    val plannedWorkouts: List<PlannedWorkoutEntity>,
    val plannedExercises: List<PlannedExerciseEntity>,
    val workoutSessions: List<WorkoutSessionEntity>,
    val sessionExercises: List<WorkoutSessionExerciseEntity> = emptyList(),
    val setLogs: List<WorkoutSetLogEntity>,
    val foodLogs: List<FoodLogEntity>,
    val aiDrafts: List<AiDraftEntity>,
    val trainingAdjustments: List<TrainingAdjustmentEntity> = emptyList(),
    val aiProviders: List<AiProviderEntity>,
)

object FitnessBackupCodec {
    const val MAX_BACKUP_BYTES = 25 * 1024 * 1024
    private const val MAX_AVATAR_BYTES = 5 * 1024 * 1024
    const val MAX_AVATAR_BASE64_CHARS = ((MAX_AVATAR_BYTES + 2) / 3) * 4
    private const val MAX_COLLECTION_SIZE = 100_000
    private const val MAX_STRING_LENGTH = 256 * 1024

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: FitnessBackupPayload): String =
        json.encodeToString(payload)

    fun decode(rawJson: String): FitnessBackupPayload =
        json.decodeFromString(rawJson.also(::requireJsonSize))

    fun decodeAndValidate(rawJson: String): FitnessBackupPayload =
        decode(rawJson).also(::validate)

    fun preview(rawJson: String): FitnessBackupPreview {
        val payload = decodeAndValidate(rawJson)
        return FitnessBackupPreview(
            version = payload.version,
            exportedAt = payload.exportedAt,
            profileCount = if (payload.userProfile == null) 0 else 1,
            planCount = payload.plannedWorkouts.size,
            sessionCount = payload.workoutSessions.size,
            setCount = payload.setLogs.size,
            foodCount = payload.foodLogs.size,
        )
    }

    fun readBounded(input: InputStream, declaredLength: Long = -1L): String {
        require(declaredLength < 0L || declaredLength <= MAX_BACKUP_BYTES) { "备份文件超过 25 MB 上限" }
        val output = ByteArrayOutputStream(minOf(MAX_BACKUP_BYTES, 64 * 1024))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= MAX_BACKUP_BYTES) { "备份文件超过 25 MB 上限" }
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    fun validate(payload: FitnessBackupPayload) {
        require(payload.version in 1..4) { "不支持的备份版本" }
        require(payload.exportedAt >= 0L) { "备份导出时间无效" }
        require(payload.userProfile != null || payload.avatarBase64.isBlank()) { "头像缺少对应用户档案" }
        requireCollectionsWithinLimit(payload)
        requireStringsWithinLimit(payload)
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
        requireUnique("场地器械", payload.venueEquipment.map { it.venueId to it.equipmentId })
        requireUnique("计划动作顺序", payload.plannedExercises.map { it.plannedWorkoutId to it.orderIndex })
        requireUnique("训练动作", payload.sessionExercises.map { it.sessionId to it.exerciseId })
        requireUnique("训练组序号", payload.setLogs.map { Triple(it.sessionId, it.exerciseId, it.setIndex) })

        payload.setLogs.forEach { log ->
            require(log.setIndex > 0) { "组序号必须大于 0" }
            require(log.actualReps >= 0) { "组次数不能为负数" }
            require(log.actualWeightKg.isFinite() && log.actualWeightKg >= 0.0) { "组重量无效" }
            require(log.completedAt >= 0L) { "组记录时间无效" }
            if (payload.version >= 2) require(log.feeling.isNotBlank()) { "组体感不能为空" }
        }
        if (payload.version >= 2) {
            val sessionIds = payload.workoutSessions.mapTo(mutableSetOf()) { it.id }
            val sessionExerciseById = payload.sessionExercises.associateBy { it.id }
            payload.sessionExercises.forEach { sessionExercise ->
                require(sessionExercise.sessionId in sessionIds) { "训练动作缺少对应训练记录" }
                require(sessionExercise.targetSets > 0) { "训练动作目标组数必须大于 0" }
                require(sessionExercise.targetWeightKg.isFinite() && sessionExercise.targetWeightKg >= 0.0) {
                    "训练动作目标重量无效"
                }
            }
            payload.setLogs.forEach { log ->
                require(log.sessionId in sessionIds) { "组记录缺少对应训练记录" }
                log.sessionExerciseId?.let { linkedId ->
                    val linked = requireNotNull(sessionExerciseById[linkedId]) { "组记录缺少对应训练动作" }
                    require(linked.sessionId == log.sessionId && linked.exerciseId == log.exerciseId) {
                        "组记录与训练动作不匹配"
                    }
                }
            }
        }
        if (payload.version >= 4) validateCurrentReferencesAndRanges(payload)
    }

    private fun validateCurrentReferencesAndRanges(payload: FitnessBackupPayload) {
        val venueIds = payload.venues.mapTo(mutableSetOf()) { it.id }
        val equipmentIds = payload.equipment.mapTo(mutableSetOf()) { it.id }
        val planIds = payload.plannedWorkouts.mapTo(mutableSetOf()) { it.id }
        val sessionIds = payload.workoutSessions.mapTo(mutableSetOf()) { it.id }
        val sessionExercisesBySession = payload.sessionExercises.groupBy { it.sessionId }

        payload.venueEquipment.forEach {
            require(it.venueId in venueIds) { "场地器械缺少对应场地" }
            require(it.equipmentId in equipmentIds) { "场地器械缺少对应器械" }
        }
        payload.plannedWorkouts.forEach { plan ->
            require(plan.venueId in venueIds) { "计划缺少对应场地" }
            require(plan.status in setOf("planned", "in_progress", "completed", "skipped")) { "计划状态无效" }
            runCatching { LocalDate.parse(plan.scheduledDate) }.getOrElse { throw IllegalArgumentException("计划日期无效") }
            require(payload.plannedExercises.any { it.plannedWorkoutId == plan.id }) { "计划缺少动作" }
        }
        payload.plannedExercises.forEach { exercise ->
            require(exercise.plannedWorkoutId in planIds) { "计划动作缺少对应计划" }
            require(exercise.orderIndex >= 0) { "计划动作顺序无效" }
            require(exercise.targetSets > 0) { "计划动作目标组数必须大于 0" }
            require(exercise.targetWeightKg.isFinite() && exercise.targetWeightKg >= 0.0) { "计划动作目标重量无效" }
        }
        payload.workoutSessions.forEach { session ->
            require(session.plannedWorkoutId == null || session.plannedWorkoutId in planIds) { "训练记录缺少对应计划" }
            require(session.venueId in venueIds) { "训练记录缺少对应场地" }
            require(session.status in setOf("in_progress", "completed", "partial")) { "训练记录状态无效" }
            require(session.startedAt >= 0L && session.updatedAt >= 0L) { "训练记录时间无效" }
            require(session.endedAt == null || session.endedAt >= session.startedAt) { "训练结束时间无效" }
            val exercises = sessionExercisesBySession[session.id].orEmpty()
            require(exercises.isNotEmpty()) { "训练记录缺少训练动作" }
            require(exercises.any { it.exerciseId == session.exerciseId }) { "训练记录首个动作无效" }
            require(session.currentExerciseId == null || exercises.any { it.exerciseId == session.currentExerciseId }) {
                "训练记录当前动作无效"
            }
        }
        payload.sessionExercises.forEach { exercise ->
            require(exercise.sessionId in sessionIds) { "训练动作缺少对应训练记录" }
            require(exercise.orderIndex >= 0) { "训练动作顺序无效" }
            require(exercise.status in setOf("pending", "completed", "skipped")) { "训练动作状态无效" }
        }
        payload.setLogs.forEach { log -> require(log.sessionId in sessionIds) { "组记录缺少对应训练记录" } }
        payload.foodLogs.forEach { food ->
            runCatching { LocalDate.parse(food.loggedDate) }.getOrElse { throw IllegalArgumentException("饮食日期无效") }
            require(food.calories >= 0) { "饮食热量不能为负数" }
            require(listOf(food.proteinGrams, food.carbsGrams, food.fatGrams).all { it.isFinite() && it >= 0.0 }) {
                "饮食营养值无效"
            }
        }
        payload.userProfile?.let { profile ->
            require(profile.birthYear in 1900..LocalDate.now().year) { "出生年份无效" }
            require(profile.heightCm.isFinite() && profile.heightCm in 50.0..300.0) { "身高无效" }
            require(profile.weightKg.isFinite() && profile.weightKg in 10.0..500.0) { "体重无效" }
            require(profile.weeklyTrainingDays in 1..7) { "每周训练天数无效" }
            require(profile.preferredMinutes in 1..600) { "训练时长无效" }
        }
        payload.aiDrafts.forEach { require(it.status in setOf("draft", "confirmed", "dismissed")) { "智能草稿状态无效" } }
        payload.trainingAdjustments.forEach { require(it.status in setOf("draft", "confirmed", "dismissed")) { "训练调整状态无效" } }
    }

    private fun requireCollectionsWithinLimit(payload: FitnessBackupPayload) {
        val sizes = listOf(
            payload.venues.size, payload.equipment.size, payload.venueEquipment.size,
            payload.preferences.size, payload.plannedWorkouts.size, payload.plannedExercises.size,
            payload.workoutSessions.size, payload.sessionExercises.size, payload.setLogs.size,
            payload.foodLogs.size, payload.aiDrafts.size, payload.trainingAdjustments.size, payload.aiProviders.size,
        )
        require(sizes.all { it <= MAX_COLLECTION_SIZE }) { "备份记录数量超过上限" }
        require(payload.avatarBase64.length <= MAX_AVATAR_BASE64_CHARS) { "头像数据超过上限" }
    }

    private fun requireStringsWithinLimit(payload: FitnessBackupPayload) {
        val values = buildList {
            payload.userProfile?.let { addAll(listOf(it.id, it.displayName, it.goal, it.injuries, it.avatarPath)) }
            payload.venues.forEach { addAll(listOf(it.id, it.name)) }
            payload.equipment.forEach { addAll(listOf(it.id, it.name, it.category)) }
            payload.venueEquipment.forEach { addAll(listOf(it.venueId, it.equipmentId)) }
            payload.preferences.forEach { (key, value) -> add(key); add(value) }
            payload.plannedWorkouts.forEach { addAll(listOf(it.id, it.name, it.scheduledDate, it.venueId, it.status)) }
            payload.plannedExercises.forEach { addAll(listOf(it.id, it.plannedWorkoutId, it.exerciseId, it.targetReps, it.note)) }
            payload.workoutSessions.forEach { addAll(listOfNotNull(it.id, it.plannedWorkoutId, it.venueId, it.exerciseId, it.status, it.currentExerciseId)) }
            payload.sessionExercises.forEach { addAll(listOf(it.id, it.sessionId, it.exerciseId, it.targetReps, it.status)) }
            payload.setLogs.forEach { addAll(listOfNotNull(it.id, it.sessionId, it.exerciseId, it.feeling, it.sessionExerciseId)) }
            payload.foodLogs.forEach { addAll(listOf(it.id, it.loggedDate, it.name, it.source, it.imageNote, it.imageUri, it.providerId, it.model)) }
            payload.aiDrafts.forEach { addAll(listOf(it.id, it.type, it.title, it.content, it.status, it.metadataJson)) }
            payload.trainingAdjustments.forEach { addAll(listOf(it.id, it.exerciseId, it.title, it.content, it.status)) }
            payload.aiProviders.forEach { addAll(listOf(it.id, it.displayName, it.baseUrl, it.model)) }
        }
        require(values.all { it.length <= MAX_STRING_LENGTH }) { "备份文本字段超过上限" }
    }

    private fun requireJsonSize(rawJson: String) {
        require(rawJson.length <= MAX_BACKUP_BYTES) { "备份文件超过 25 MB 上限" }
    }

    private fun <T> requireUnique(label: String, values: List<T>) {
        require(values.size == values.distinct().size) { "$label 重复" }
    }
}

data class FitnessBackupPreview(
    val version: Int,
    val exportedAt: Long,
    val profileCount: Int,
    val planCount: Int,
    val sessionCount: Int,
    val setCount: Int,
    val foodCount: Int,
)
