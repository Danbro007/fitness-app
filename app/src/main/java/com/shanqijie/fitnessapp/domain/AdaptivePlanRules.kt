package com.shanqijie.fitnessapp.domain

import kotlin.math.abs

enum class LoadAdjustmentDirection {
    INCREASE,
    MAINTAIN,
    REDUCE,
    FIRST_TRIAL,
}

data class ExercisePerformance(
    val exerciseId: String,
    val completedAt: Long,
    val sessionCompleted: Boolean,
    val targetSets: Int,
    val targetRepsPerSet: Int,
    val targetWeightKg: Double,
    val actualSets: Int,
    val actualTotalReps: Int,
    val actualWeightKg: Double,
    val feeling: String,
)

data class LoadRecommendation(
    val exerciseId: String,
    val direction: LoadAdjustmentDirection,
    val targetSets: Int,
    val targetRepsPerSet: Int,
    val suggestedWeightKg: Double,
    val evidence: List<ExercisePerformance>,
    val recentWeightedScore: Double,
    val explanation: String,
)

class AdaptivePlanPolicy {
    fun recommendLoad(
        exerciseId: String,
        baselineSets: Int,
        baselineRepsPerSet: Int,
        baselineWeightKg: Double,
        availableLoadsKg: List<Double>,
        history: List<ExercisePerformance>,
    ): LoadRecommendation {
        require(exerciseId.isNotBlank()) { "动作不能为空" }
        require(baselineSets > 0) { "目标组数必须大于 0" }
        require(baselineRepsPerSet > 0) { "目标次数必须大于 0" }
        val loads = normalizedLoads(availableLoadsKg)
        require(loads.isNotEmpty()) { "场地器械至少需要一个重量档位" }

        val exerciseHistory = history
            .asSequence()
            .filter { it.exerciseId == exerciseId && it.sessionCompleted }
            .sortedByDescending(ExercisePerformance::completedAt)
            .toList()
        if (exerciseHistory.isEmpty()) {
            return recommendation(
                exerciseId = exerciseId,
                direction = LoadAdjustmentDirection.FIRST_TRIAL,
                baselineSets = baselineSets,
                baselineRepsPerSet = baselineRepsPerSet,
                weightKg = loads.first(),
                evidence = emptyList(),
                score = 0.0,
                explanation = "没有该动作的已完成历史，使用当前场地最低重量档位作为首次试练建议",
            )
        }

        val baselineLoad = supportedBaseline(baselineWeightKg, loads)
        val recent = exerciseHistory.take(TREND_WINDOW_SIZE)
        if (recent.size < TREND_WINDOW_SIZE) {
            return recommendation(
                exerciseId,
                LoadAdjustmentDirection.MAINTAIN,
                baselineSets,
                baselineRepsPerSet,
                baselineLoad,
                recent,
                weightedScore(recent),
                "该动作已完成历史少于 3 次，保持组数、次数和基线重量",
            )
        }

        val allEasyAndComplete = recent.all { it.isEasy() && it.metTarget() }
        val allHardAndBelowTarget = recent.all { it.isHard() && it.belowTarget() }
        return when {
            allEasyAndComplete -> {
                val nextLoad = loads.firstOrNull { it > baselineLoad + LOAD_EPSILON }
                recommendation(
                    exerciseId,
                    if (nextLoad == null) LoadAdjustmentDirection.MAINTAIN else LoadAdjustmentDirection.INCREASE,
                    baselineSets,
                    baselineRepsPerSet,
                    nextLoad ?: baselineLoad,
                    recent,
                    weightedScore(recent),
                    if (nextLoad == null) "已达到当前场地最高重量档位，保持基线重量"
                    else "同一动作最近 3 次均完成目标且感觉轻松，建议增加一个可用重量档位",
                )
            }

            allHardAndBelowTarget -> {
                val previousLoad = loads.lastOrNull { it < baselineLoad - LOAD_EPSILON }
                recommendation(
                    exerciseId,
                    if (previousLoad == null) LoadAdjustmentDirection.MAINTAIN else LoadAdjustmentDirection.REDUCE,
                    baselineSets,
                    baselineRepsPerSet,
                    previousLoad ?: baselineLoad,
                    recent,
                    weightedScore(recent),
                    if (previousLoad == null) "已达到当前场地最低重量档位，保持基线重量"
                    else "同一动作最近 3 次均感觉吃力且实际表现低于目标，建议降低一个可用重量档位",
                )
            }

            else -> recommendation(
                exerciseId,
                LoadAdjustmentDirection.MAINTAIN,
                baselineSets,
                baselineRepsPerSet,
                baselineLoad,
                recent,
                weightedScore(recent),
                "最近 3 次未形成连续升降趋势，保持组数、次数和基线重量",
            )
        }
    }

    private fun recommendation(
        exerciseId: String,
        direction: LoadAdjustmentDirection,
        baselineSets: Int,
        baselineRepsPerSet: Int,
        weightKg: Double,
        evidence: List<ExercisePerformance>,
        score: Double,
        explanation: String,
    ) = LoadRecommendation(
        exerciseId = exerciseId,
        direction = direction,
        targetSets = baselineSets,
        targetRepsPerSet = baselineRepsPerSet,
        suggestedWeightKg = weightKg,
        evidence = evidence,
        recentWeightedScore = score,
        explanation = explanation,
    )

    private fun supportedBaseline(baselineWeightKg: Double, loads: List<Double>): Double =
        loads.lastOrNull { it <= baselineWeightKg + LOAD_EPSILON } ?: loads.first()

    private fun weightedScore(history: List<ExercisePerformance>): Double {
        val weights = listOf(3.0, 2.0, 1.0)
        val weighted = history.zip(weights).sumOf { (performance, weight) ->
            val signal = when {
                performance.isEasy() && performance.metTarget() -> 1.0
                performance.isHard() && performance.belowTarget() -> -1.0
                else -> 0.0
            }
            signal * weight
        }
        return weighted / weights.take(history.size).sum()
    }

    private fun ExercisePerformance.metTarget(): Boolean =
        actualSets >= targetSets &&
            actualTotalReps >= targetSets * targetRepsPerSet &&
            actualWeightKg + LOAD_EPSILON >= targetWeightKg

    private fun ExercisePerformance.belowTarget(): Boolean =
        actualSets < targetSets ||
            actualTotalReps < targetSets * targetRepsPerSet ||
            actualWeightKg + LOAD_EPSILON < targetWeightKg

    private fun ExercisePerformance.isEasy(): Boolean =
        feeling.contains("轻松", ignoreCase = true) || feeling.contains("easy", ignoreCase = true)

    private fun ExercisePerformance.isHard(): Boolean =
        listOf("吃力", "很难", "疲劳", "hard").any { feeling.contains(it, ignoreCase = true) }

    private fun normalizedLoads(loads: List<Double>): List<Double> =
        loads.onEach { require(it >= 0.0) { "重量档位不能为负数" } }.distinct().sorted()

    private companion object {
        const val TREND_WINDOW_SIZE = 3
        const val LOAD_EPSILON = 0.0001
    }
}

enum class PlanCandidateSource {
    LOCAL,
    AI,
}

data class CandidateExercise(
    val exerciseId: String,
    val name: String,
    val equipmentId: String,
    val primaryMuscle: String,
    val targetSets: Int,
    val targetRepsPerSet: Int,
    val targetWeightKg: Double,
)

data class CandidateTrainingDay(
    val dayOfWeek: Int,
    val venueId: String,
    val exercises: List<CandidateExercise>,
)

data class WeeklyPlanCandidate(
    val source: PlanCandidateSource,
    val days: List<CandidateTrainingDay>,
)

data class InjuryFilterException(
    val exerciseId: String,
    val injuriesHash: String,
)

data class PlanConstraintInput(
    val candidate: WeeklyPlanCandidate,
    val enabledEquipmentByVenue: Map<String, Set<String>>,
    val availableLoadsByVenueEquipment: Map<Pair<String, String>, List<Double>>,
    val injuriesText: String,
    val injuriesHash: String,
    val injuryExceptions: Set<InjuryFilterException> = emptySet(),
    val excludedExerciseIds: Set<String> = emptySet(),
)

enum class PlanConflictCode {
    INVALID_DAY,
    EMPTY_TRAINING_DAY,
    EQUIPMENT_UNAVAILABLE,
    UNSUPPORTED_WEIGHT,
    ACTION_EXCLUDED,
    INJURY_RISK,
    INJURY_UNCERTAIN,
    MUSCLE_RECOVERY_CONFLICT,
}

data class PlanConflict(
    val code: PlanConflictCode,
    val dayOfWeek: Int?,
    val exerciseId: String?,
    val message: String,
)

data class PlanValidationResult(
    val candidate: WeeklyPlanCandidate?,
    val conflicts: List<PlanConflict>,
) {
    val isValid: Boolean get() = candidate != null && conflicts.isEmpty()
}

class PlanConstraintValidator {
    fun validate(input: PlanConstraintInput): PlanValidationResult {
        val conflicts = buildList {
            input.candidate.days.forEach { day -> validateDay(input, day, this) }
            validateRecovery(input.candidate.days, this)
        }
        return PlanValidationResult(
            candidate = input.candidate.takeIf { conflicts.isEmpty() },
            conflicts = conflicts,
        )
    }

    private fun validateDay(
        input: PlanConstraintInput,
        day: CandidateTrainingDay,
        conflicts: MutableList<PlanConflict>,
    ) {
        if (day.dayOfWeek !in 1..7) {
            conflicts += conflict(PlanConflictCode.INVALID_DAY, day, null, "训练星期必须在 1 到 7 之间")
        }
        if (day.exercises.isEmpty()) {
            conflicts += conflict(PlanConflictCode.EMPTY_TRAINING_DAY, day, null, "训练日没有可执行动作")
        }
        day.exercises.forEach { exercise ->
            if (exercise.exerciseId in input.excludedExerciseIds) {
                conflicts += conflict(PlanConflictCode.ACTION_EXCLUDED, day, exercise, "动作已被用户长期排除")
            }
            val enabledEquipment = input.enabledEquipmentByVenue[day.venueId].orEmpty()
            if (exercise.equipmentId !in enabledEquipment) {
                conflicts += conflict(
                    PlanConflictCode.EQUIPMENT_UNAVAILABLE,
                    day,
                    exercise,
                    "当前场地没有启用该动作所需器械",
                )
            }
            val loads = input.availableLoadsByVenueEquipment[day.venueId to exercise.equipmentId].orEmpty()
            if (loads.none { abs(it - exercise.targetWeightKg) <= LOAD_EPSILON }) {
                conflicts += conflict(
                    PlanConflictCode.UNSUPPORTED_WEIGHT,
                    day,
                    exercise,
                    "目标重量不是当前场地器械支持的档位",
                )
            }
            validateInjury(input, day, exercise)?.let(conflicts::add)
        }
    }

    private fun validateInjury(
        input: PlanConstraintInput,
        day: CandidateTrainingDay,
        exercise: CandidateExercise,
    ): PlanConflict? {
        if (input.injuriesText.isBlank()) return null
        if (InjuryFilterException(exercise.exerciseId, input.injuriesHash) in input.injuryExceptions) return null
        val matchedRules = INJURY_RULES.filter { rule ->
            rule.injuryKeywords.any { input.injuriesText.contains(it, ignoreCase = true) }
        }
        if (matchedRules.isEmpty()) return conflict(
            PlanConflictCode.INJURY_UNCERTAIN,
            day,
            exercise,
            "系统无法确认该动作与当前伤病信息是否安全，已保守排除",
        )
        val exerciseDescription = "${exercise.name} ${exercise.primaryMuscle}".lowercase()
        val riskyRule = matchedRules.firstOrNull { rule -> rule.riskyTerms.any(exerciseDescription::contains) }
            ?: return null
        return conflict(
            PlanConflictCode.INJURY_RISK,
            day,
            exercise,
            "动作可能与“${riskyRule.label}”相关，已按保守策略排除",
        )
    }

    private fun validateRecovery(
        days: List<CandidateTrainingDay>,
        conflicts: MutableList<PlanConflict>,
    ) {
        days.sortedBy(CandidateTrainingDay::dayOfWeek).zipWithNext().forEach { (previous, current) ->
            if (current.dayOfWeek != previous.dayOfWeek + 1) return@forEach
            val previousMuscles = previous.exercises.mapNotNull { canonicalMuscle(it.primaryMuscle) }.toSet()
            val currentMuscles = current.exercises.mapNotNull { canonicalMuscle(it.primaryMuscle) }.toSet()
            val overlap = previousMuscles intersect currentMuscles
            if (overlap.isNotEmpty()) {
                conflicts += PlanConflict(
                    code = PlanConflictCode.MUSCLE_RECOVERY_CONFLICT,
                    dayOfWeek = current.dayOfWeek,
                    exerciseId = null,
                    message = "连续训练日重复主要肌群：${overlap.sorted().joinToString()}，请调整日程、器械或动作偏好",
                )
            }
        }
    }

    private fun canonicalMuscle(raw: String): String? {
        val muscle = raw.trim().lowercase()
        if (muscle.isBlank()) return null
        return when {
            listOf("chest", "pectoral", "胸").any(muscle::contains) -> "chest"
            listOf("back", "lat", "背").any(muscle::contains) -> "back"
            listOf("shoulder", "delt", "肩").any(muscle::contains) -> "shoulders"
            listOf("quadricep", "quad", "upper leg", "大腿前", "股四头").any(muscle::contains) -> "quadriceps"
            listOf("hamstring", "大腿后", "腘绳").any(muscle::contains) -> "hamstrings"
            listOf("glute", "臀").any(muscle::contains) -> "glutes"
            listOf("bicep", "肱二头").any(muscle::contains) -> "biceps"
            listOf("tricep", "肱三头").any(muscle::contains) -> "triceps"
            listOf("calf", "calves", "小腿").any(muscle::contains) -> "calves"
            listOf("abdominal", "abs", "core", "腹", "核心").any(muscle::contains) -> "core"
            else -> muscle
        }
    }

    private fun conflict(
        code: PlanConflictCode,
        day: CandidateTrainingDay,
        exercise: CandidateExercise?,
        message: String,
    ) = PlanConflict(code, day.dayOfWeek, exercise?.exerciseId, message)

    private data class InjuryRule(
        val label: String,
        val injuryKeywords: Set<String>,
        val riskyTerms: Set<String>,
    )

    private companion object {
        const val LOAD_EPSILON = 0.0001
        val INJURY_RULES = listOf(
            InjuryRule("膝部不适", setOf("膝", "knee"), setOf("深蹲", "squat", "弓步", "lunge", "腿举", "leg press", "step")),
            InjuryRule("肩部不适", setOf("肩", "shoulder"), setOf("卧推", "推举", "press", "侧平举", "raise", "飞鸟", "fly", "upright row")),
            InjuryRule("腰背不适", setOf("腰", "下背", "back"), setOf("硬拉", "deadlift", "深蹲", "squat", "划船", "row", "back extension")),
            InjuryRule("肘部不适", setOf("肘", "elbow"), setOf("推", "press", "屈伸", "extension", "弯举", "curl", "dip")),
            InjuryRule("手腕不适", setOf("腕", "wrist"), setOf("推", "press", "弯举", "curl", "抓举", "snatch", "挺举", "clean")),
        )
    }
}

object DefaultEquipmentLoads {
    fun forCategory(category: String): List<Double> = when (category.trim().lowercase()) {
        "bodyweight", "body-weight", "自重" -> listOf(0.0)
        "barbell", "杠铃", "plate-loaded" -> (0..32).map { 20.0 + it * 2.5 }
        "dumbbell", "哑铃", "free-weight" -> (1..20).map { it * 2.0 }
        "machine", "器械", "cable", "绳索" -> (1..20).map { it * 5.0 }
        else -> listOf(0.0)
    }
}
