package com.shanqijie.fitnessapp.data

import com.shanqijie.fitnessapp.domain.CandidateExercise
import com.shanqijie.fitnessapp.domain.CandidateTrainingDay
import com.shanqijie.fitnessapp.domain.InjuryFilterException
import com.shanqijie.fitnessapp.domain.PlanConflict
import com.shanqijie.fitnessapp.domain.PlanConstraintInput
import com.shanqijie.fitnessapp.domain.PlanConstraintValidator
import com.shanqijie.fitnessapp.domain.WeeklyPlanCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

data class PlanCycleConfiguration(
    val id: String = UUID.randomUUID().toString(),
    val totalWeeks: Int = 4,
    val startDate: LocalDate,
    val preferredMinutes: Int = 60,
    val trainingDays: List<PlanScheduleDayEntity>,
)

data class PreviousCycleDefaults(
    val totalWeeks: Int,
    val preferredMinutes: Int,
    val trainingDays: List<PlanScheduleDayEntity>,
)

data class PlanDraftExplanation(
    val exerciseId: String,
    val message: String,
)

data class AdaptiveDraftExerciseView(
    val exerciseId: String,
    val name: String,
    val targetSets: Int,
    val targetRepsPerSet: Int,
    val targetWeightKg: Double,
)

data class AdaptiveDraftDayView(
    val dayOfWeek: Int,
    val venueId: String,
    val exercises: List<AdaptiveDraftExerciseView>,
)

data class AdaptiveDraftContent(
    val source: String,
    val days: List<AdaptiveDraftDayView>,
    val explanations: List<PlanDraftExplanation>,
)

class PlanGenerationConflictException(val conflicts: List<PlanConflict>) :
    IllegalArgumentException(conflicts.joinToString("；") { it.message })

class AdaptivePlanWorkflow(
    private val store: FitnessStore,
    private val timeProvider: TimeProvider = TimeProvider { System.currentTimeMillis() },
    private val validator: PlanConstraintValidator = PlanConstraintValidator(),
) {
    suspend fun createCycle(configuration: PlanCycleConfiguration): PlanCycleEntity = withContext(Dispatchers.IO) {
        require(configuration.id.isNotBlank()) { "计划周期 ID 不能为空" }
        require(configuration.totalWeeks in 1..12) { "计划周期必须在 1 到 12 周之间" }
        require(configuration.preferredMinutes in 15..180) { "单次训练时长必须在 15 到 180 分钟之间" }
        require(configuration.trainingDays.isNotEmpty()) { "至少选择一个训练日" }
        require(configuration.trainingDays.map { it.dayOfWeek }.distinct().size == configuration.trainingDays.size) {
            "训练星期不能重复"
        }
        require(configuration.trainingDays.all { it.cycleId == configuration.id }) { "训练日必须属于当前周期" }
        require(configuration.trainingDays.all { it.dayOfWeek in 1..7 }) { "训练星期必须在 1 到 7 之间" }
        val venueIds = store.trainingVenues().mapTo(mutableSetOf()) { it.id }
        require(configuration.trainingDays.all { it.venueId in venueIds }) { "训练日包含不存在的场地" }
        require(store.planCycles().none { it.status == STATUS_ACTIVE }) { "已有进行中的计划周期" }
        val now = timeProvider.currentTimeMillis()
        PlanCycleEntity(
            id = configuration.id,
            totalWeeks = configuration.totalWeeks,
            currentWeek = 1,
            startDate = configuration.startDate.toString(),
            preferredMinutes = configuration.preferredMinutes,
            status = STATUS_ACTIVE,
            createdAt = now,
            updatedAt = now,
        ).also { cycle ->
            store.transaction {
                store.upsertPlanCycle(cycle)
                store.replacePlanScheduleDays(cycle.id, configuration.trainingDays.sortedBy { it.orderIndex })
            }
        }
    }

    suspend fun generateNextWeekDraft(
        cycleId: String,
        candidate: WeeklyPlanCandidate,
        explanations: List<PlanDraftExplanation>,
    ): WeeklyPlanDraftEntity = withContext(Dispatchers.IO) {
        val cycle = requireNotNull(store.planCycle(cycleId)) { "计划周期不存在" }
        require(cycle.status == STATUS_ACTIVE) { "计划周期已结束，请开始新的计划周期" }
        require(store.preferences()[FitnessRepository.INJURY_REVIEW_REQUIRED_KEY] != "true") {
            "存在身体不适记录，请先完成伤病复核"
        }
        val schedules = store.planScheduleDays(cycle.id)
        requireCandidateMatchesSchedule(candidate, schedules)
        val validation = validator.validate(constraintInput(candidate))
        if (!validation.isValid) throw PlanGenerationConflictException(validation.conflicts)
        val inputHash = currentInputHash(cycle)
        val now = timeProvider.currentTimeMillis()
        store.weeklyPlanDrafts()
            .filter { it.cycleId == cycle.id && it.weekIndex == cycle.currentWeek && it.status == STATUS_DRAFT }
            .forEach { store.upsertWeeklyPlanDraft(it.copy(status = STATUS_STALE, updatedAt = now)) }
        val payload = candidate.toPayload(explanations)
        WeeklyPlanDraftEntity(
            id = UUID.randomUUID().toString(),
            cycleId = cycle.id,
            weekIndex = cycle.currentWeek,
            weekStartDate = LocalDate.parse(cycle.startDate).plusWeeks((cycle.currentWeek - 1).toLong()).toString(),
            payloadJson = json.encodeToString(payload),
            inputHash = inputHash,
            status = STATUS_DRAFT,
            explanationsJson = json.encodeToString(payload.explanations),
            createdAt = now,
            updatedAt = now,
            confirmedAt = null,
        ).also(store::upsertWeeklyPlanDraft)
    }

    suspend fun refreshDraftStatus(draftId: String): WeeklyPlanDraftEntity = withContext(Dispatchers.IO) {
        val draft = requireNotNull(store.weeklyPlanDraft(draftId)) { "周计划草稿不存在" }
        if (draft.status != STATUS_DRAFT) return@withContext draft
        val cycle = requireNotNull(store.planCycle(draft.cycleId)) { "计划周期不存在" }
        if (draft.inputHash == currentInputHash(cycle)) return@withContext draft
        draft.copy(status = STATUS_STALE, updatedAt = timeProvider.currentTimeMillis())
            .also(store::upsertWeeklyPlanDraft)
    }

    suspend fun readDraftContent(draftId: String): AdaptiveDraftContent = withContext(Dispatchers.IO) {
        val draft = requireNotNull(store.weeklyPlanDraft(draftId)) { "周计划草稿不存在" }
        json.decodeFromString<WeeklyPlanDraftPayload>(draft.payloadJson).toView()
    }

    suspend fun adjustDraftWeight(
        draftId: String,
        exerciseId: String,
        targetWeightKg: Double,
    ): WeeklyPlanDraftEntity = withContext(Dispatchers.IO) {
        require(targetWeightKg >= 0.0) { "重量不能为负数" }
        val draft = requireNotNull(store.weeklyPlanDraft(draftId)) { "周计划草稿不存在" }
        require(draft.status == STATUS_DRAFT) { "只有未确认草稿可以调整" }
        val cycle = requireNotNull(store.planCycle(draft.cycleId)) { "计划周期不存在" }
        require(draft.weekIndex == cycle.currentWeek && cycle.status == STATUS_ACTIVE) { "只能调整当前周草稿" }
        val payload = json.decodeFromString<WeeklyPlanDraftPayload>(draft.payloadJson)
        val matchingDay = payload.days.firstOrNull { day -> day.exercises.any { it.exerciseId == exerciseId } }
            ?: error("草稿中不存在该动作")
        val supportedLoads = store.venueEquipmentLoads(matchingDay.venueId, matchingDay.exercises.first { it.exerciseId == exerciseId }.equipmentId)
            .map(VenueEquipmentLoadEntity::weightKg)
        require(supportedLoads.any { abs(it - targetWeightKg) < 0.001 }) { "重量必须来自当前场地的可用档位" }
        val updatedPayload = payload.copy(
            days = payload.days.map { day ->
                day.copy(
                    exercises = day.exercises.map { exercise ->
                        if (exercise.exerciseId == exerciseId) exercise.copy(targetWeightKg = targetWeightKg) else exercise
                    },
                )
            },
        )
        draft.copy(
            payloadJson = json.encodeToString(updatedPayload),
            explanationsJson = json.encodeToString(updatedPayload.explanations),
            updatedAt = timeProvider.currentTimeMillis(),
        ).also(store::upsertWeeklyPlanDraft)
    }

    suspend fun confirmWeek(draftId: String): List<PlannedWorkoutEntity> = withContext(Dispatchers.IO) {
        val preflightDraft = requireNotNull(store.weeklyPlanDraft(draftId)) { "周计划草稿不存在" }
        val preflightCycle = requireNotNull(store.planCycle(preflightDraft.cycleId)) { "计划周期不存在" }
        if (preflightDraft.status == STATUS_DRAFT && preflightDraft.inputHash != currentInputHash(preflightCycle)) {
            store.upsertWeeklyPlanDraft(
                preflightDraft.copy(status = STATUS_STALE, updatedAt = timeProvider.currentTimeMillis()),
            )
            error("计划输入已变化，请重新生成本周计划")
        }
        store.transaction {
            val draft = requireNotNull(store.weeklyPlanDraft(draftId)) { "周计划草稿不存在" }
            require(draft.status == STATUS_DRAFT) { "周计划草稿当前不可确认" }
            val cycle = requireNotNull(store.planCycle(draft.cycleId)) { "计划周期不存在" }
            require(cycle.status == STATUS_ACTIVE && draft.weekIndex == cycle.currentWeek) { "周计划草稿与当前周期不匹配" }
            require(draft.inputHash == currentInputHash(cycle)) { "计划输入已变化，请重新生成本周计划" }
            val payload = json.decodeFromString<WeeklyPlanDraftPayload>(draft.payloadJson)
            val candidate = payload.toCandidate()
            val validation = validator.validate(constraintInput(candidate))
            if (!validation.isValid) throw PlanGenerationConflictException(validation.conflicts)
            val existingIds = store.plannedWorkouts().mapTo(mutableSetOf()) { it.id }
            val now = timeProvider.currentTimeMillis()
            val weekStart = LocalDate.parse(draft.weekStartDate)
            val workouts = payload.days.sortedBy { it.dayOfWeek }.map { day ->
                val workoutId = "${cycle.id}-w${draft.weekIndex}-d${day.dayOfWeek}"
                require(workoutId !in existingIds) { "本周正式计划已存在" }
                PlannedWorkoutEntity(
                    id = workoutId,
                    name = "第 ${draft.weekIndex} 周 · 周${day.dayOfWeek}训练",
                    scheduledDate = weekStart.plusDays((day.dayOfWeek - 1).toLong()).toString(),
                    venueId = day.venueId,
                    status = "planned",
                    createdAt = now,
                    updatedAt = now,
                ).also(store::upsertPlannedWorkout).also { workout ->
                    day.exercises.forEachIndexed { index, exercise ->
                        store.upsertPlannedExercise(
                            PlannedExerciseEntity(
                                id = "$workoutId-${exercise.exerciseId}",
                                plannedWorkoutId = workoutId,
                                exerciseId = exercise.exerciseId,
                                orderIndex = index,
                                targetSets = exercise.targetSets,
                                targetReps = exercise.targetRepsPerSet.toString(),
                                targetWeightKg = exercise.targetWeightKg,
                                note = payload.explanations.firstOrNull { it.exerciseId == exercise.exerciseId }?.message.orEmpty(),
                            ),
                        )
                    }
                }
            }
            store.upsertWeeklyPlanDraft(draft.copy(status = STATUS_CONFIRMED, updatedAt = now, confirmedAt = now))
            val finished = cycle.currentWeek >= cycle.totalWeeks
            store.upsertPlanCycle(
                cycle.copy(
                    currentWeek = if (finished) cycle.currentWeek else cycle.currentWeek + 1,
                    status = if (finished) STATUS_COMPLETED else STATUS_ACTIVE,
                    updatedAt = now,
                ),
            )
            workouts
        }
    }

    suspend fun previousCycleDefaults(cycleId: String): PreviousCycleDefaults = withContext(Dispatchers.IO) {
        val cycle = requireNotNull(store.planCycle(cycleId)) { "计划周期不存在" }
        PreviousCycleDefaults(cycle.totalWeeks, cycle.preferredMinutes, store.planScheduleDays(cycle.id))
    }

    private fun requireCandidateMatchesSchedule(
        candidate: WeeklyPlanCandidate,
        schedules: List<PlanScheduleDayEntity>,
    ) {
        val expected = schedules.associate { it.dayOfWeek to it.venueId }
        val actual = candidate.days.associate { it.dayOfWeek to it.venueId }
        require(actual.size == candidate.days.size) { "候选训练日不能重复" }
        require(actual == expected) { "候选训练日或场地与周期配置不一致" }
    }

    private fun constraintInput(candidate: WeeklyPlanCandidate): PlanConstraintInput {
        val enabled = store.venueEquipment().filter(VenueEquipmentEntity::available)
            .groupBy(VenueEquipmentEntity::venueId)
            .mapValues { (_, values) -> values.mapTo(mutableSetOf(), VenueEquipmentEntity::equipmentId) }
        val loads = store.allVenueEquipmentLoads().groupBy { it.venueId to it.equipmentId }
            .mapValues { (_, values) -> values.sortedBy(VenueEquipmentLoadEntity::orderIndex).map(VenueEquipmentLoadEntity::weightKg) }
        val injuries = store.userProfile()?.injuries.orEmpty()
        val injuriesHash = sha256(injuries.trim())
        val exceptions = store.injuryFilterOverrides().mapTo(mutableSetOf()) {
            InjuryFilterException(it.exerciseId, it.injuriesHash)
        }
        val excluded = store.actionPreferences().filter { it.preference == "exclude" }.mapTo(mutableSetOf()) { it.exerciseId }
        return PlanConstraintInput(candidate, enabled, loads, injuries, injuriesHash, exceptions, excluded)
    }

    private fun currentInputHash(cycle: PlanCycleEntity): String {
        val completedSessionIds = store.workoutSessions().filter { it.status == "completed" }.mapTo(mutableSetOf()) { it.id }
        val snapshot = AdaptivePlanInputSnapshot(
            profile = store.userProfile(),
            cycle = cycle,
            schedules = store.planScheduleDays(cycle.id).sortedWith(compareBy(PlanScheduleDayEntity::dayOfWeek, PlanScheduleDayEntity::venueId)),
            venueEquipment = store.venueEquipment().sortedWith(compareBy(VenueEquipmentEntity::venueId, VenueEquipmentEntity::equipmentId)),
            loads = store.allVenueEquipmentLoads().sortedWith(compareBy(VenueEquipmentLoadEntity::venueId, VenueEquipmentLoadEntity::equipmentId, VenueEquipmentLoadEntity::orderIndex)),
            preferences = store.preferences().toSortedMap().map { SnapshotPreference(it.key, it.value) },
            actionPreferences = store.actionPreferences().sortedBy(ActionPreferenceEntity::exerciseId),
            injuryExceptions = store.injuryFilterOverrides().sortedBy(InjuryFilterOverrideEntity::exerciseId),
            plannedWorkouts = store.plannedWorkouts().sortedBy(PlannedWorkoutEntity::id),
            plannedExercises = store.allPlannedExercises()
                .sortedWith(compareBy(PlannedExerciseEntity::plannedWorkoutId, PlannedExerciseEntity::orderIndex)),
            completedSessions = store.workoutSessions().filter { it.id in completedSessionIds }.sortedBy(WorkoutSessionEntity::id),
            sessionExercises = store.allSessionExercises().filter { it.sessionId in completedSessionIds }
                .sortedWith(compareBy(WorkoutSessionExerciseEntity::sessionId, WorkoutSessionExerciseEntity::orderIndex)),
            setLogs = store.allSetLogs().filter { it.sessionId in completedSessionIds }
                .sortedWith(compareBy(WorkoutSetLogEntity::sessionId, WorkoutSetLogEntity::exerciseId, WorkoutSetLogEntity::setIndex)),
        )
        return sha256(json.encodeToString(snapshot))
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_DRAFT = "draft"
        const val STATUS_STALE = "stale"
        const val STATUS_CONFIRMED = "confirmed"
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    }
}

@Serializable
private data class WeeklyPlanDraftPayload(
    val source: String,
    val days: List<DraftTrainingDay>,
    val explanations: List<DraftExplanation>,
)

@Serializable
private data class DraftTrainingDay(val dayOfWeek: Int, val venueId: String, val exercises: List<DraftExercise>)

@Serializable
private data class DraftExercise(
    val exerciseId: String,
    val name: String,
    val equipmentId: String,
    val primaryMuscle: String,
    val targetSets: Int,
    val targetRepsPerSet: Int,
    val targetWeightKg: Double,
)

@Serializable
private data class DraftExplanation(val exerciseId: String, val message: String)

private fun WeeklyPlanCandidate.toPayload(explanations: List<PlanDraftExplanation>) = WeeklyPlanDraftPayload(
    source = source.name,
    days = days.map { day -> DraftTrainingDay(day.dayOfWeek, day.venueId, day.exercises.map(CandidateExercise::toDraft)) },
    explanations = explanations.map { DraftExplanation(it.exerciseId, it.message) },
)

private fun CandidateExercise.toDraft() = DraftExercise(
    exerciseId, name, equipmentId, primaryMuscle, targetSets, targetRepsPerSet, targetWeightKg,
)

private fun WeeklyPlanDraftPayload.toCandidate() = WeeklyPlanCandidate(
    source = com.shanqijie.fitnessapp.domain.PlanCandidateSource.valueOf(source),
    days = days.map { day ->
        CandidateTrainingDay(day.dayOfWeek, day.venueId, day.exercises.map(DraftExercise::toCandidate))
    },
)

private fun WeeklyPlanDraftPayload.toView() = AdaptiveDraftContent(
    source = source,
    days = days.map { day ->
        AdaptiveDraftDayView(
            dayOfWeek = day.dayOfWeek,
            venueId = day.venueId,
            exercises = day.exercises.map {
                AdaptiveDraftExerciseView(it.exerciseId, it.name, it.targetSets, it.targetRepsPerSet, it.targetWeightKg)
            },
        )
    },
    explanations = explanations.map { PlanDraftExplanation(it.exerciseId, it.message) },
)

private fun DraftExercise.toCandidate() = CandidateExercise(
    exerciseId, name, equipmentId, primaryMuscle, targetSets, targetRepsPerSet, targetWeightKg,
)

@Serializable
private data class SnapshotPreference(val key: String, val value: String)

@Serializable
private data class AdaptivePlanInputSnapshot(
    val profile: UserProfileEntity?,
    val cycle: PlanCycleEntity,
    val schedules: List<PlanScheduleDayEntity>,
    val venueEquipment: List<VenueEquipmentEntity>,
    val loads: List<VenueEquipmentLoadEntity>,
    val preferences: List<SnapshotPreference>,
    val actionPreferences: List<ActionPreferenceEntity>,
    val injuryExceptions: List<InjuryFilterOverrideEntity>,
    val plannedWorkouts: List<PlannedWorkoutEntity>,
    val plannedExercises: List<PlannedExerciseEntity>,
    val completedSessions: List<WorkoutSessionEntity>,
    val sessionExercises: List<WorkoutSessionExerciseEntity>,
    val setLogs: List<WorkoutSetLogEntity>,
)
