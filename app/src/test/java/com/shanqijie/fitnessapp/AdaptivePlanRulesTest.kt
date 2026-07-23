package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.domain.AdaptivePlanPolicy
import com.shanqijie.fitnessapp.domain.CandidateExercise
import com.shanqijie.fitnessapp.domain.CandidateTrainingDay
import com.shanqijie.fitnessapp.domain.DefaultEquipmentLoads
import com.shanqijie.fitnessapp.domain.ExercisePerformance
import com.shanqijie.fitnessapp.domain.InjuryFilterException
import com.shanqijie.fitnessapp.domain.LoadAdjustmentDirection
import com.shanqijie.fitnessapp.domain.PlanCandidateSource
import com.shanqijie.fitnessapp.domain.PlanConflictCode
import com.shanqijie.fitnessapp.domain.PlanConstraintInput
import com.shanqijie.fitnessapp.domain.PlanConstraintValidator
import com.shanqijie.fitnessapp.domain.WeeklyPlanCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePlanRulesTest {
    private val policy = AdaptivePlanPolicy()
    private val validator = PlanConstraintValidator()

    @Test
    fun threeRecentEasyCompletedSessionsIncreaseExactlyOneSupportedLoadStep() {
        val recommendation = policy.recommendLoad(
            exerciseId = "barbell-bench",
            baselineSets = 4,
            baselineRepsPerSet = 8,
            baselineWeightKg = 60.0,
            availableLoadsKg = listOf(50.0, 60.0, 62.5, 70.0),
            history = listOf(
                performance("barbell-bench", 1, feeling = "轻松"),
                performance("barbell-bench", 3, feeling = "轻松"),
                performance("barbell-bench", 2, feeling = "轻松"),
                performance("barbell-row", 4, feeling = "轻松"),
            ),
        )

        assertEquals(LoadAdjustmentDirection.INCREASE, recommendation.direction)
        assertEquals(62.5, recommendation.suggestedWeightKg, 0.001)
        assertEquals(4, recommendation.targetSets)
        assertEquals(8, recommendation.targetRepsPerSet)
        assertEquals(listOf(3L, 2L, 1L), recommendation.evidence.map { it.completedAt })
        assertEquals(1.0, recommendation.recentWeightedScore, 0.001)
    }

    @Test
    fun threeHardUnderTargetSessionsReduceExactlyOneSupportedLoadStep() {
        val history = (1L..3L).map { time ->
            performance(
                exerciseId = "barbell-bench",
                completedAt = time,
                feeling = "很吃力",
                actualSets = 3,
                actualTotalReps = 20,
                actualWeightKg = 57.5,
            )
        }

        val recommendation = policy.recommendLoad(
            "barbell-bench",
            4,
            8,
            60.0,
            listOf(50.0, 57.5, 60.0, 62.5),
            history,
        )

        assertEquals(LoadAdjustmentDirection.REDUCE, recommendation.direction)
        assertEquals(57.5, recommendation.suggestedWeightKg, 0.001)
        assertEquals(-1.0, recommendation.recentWeightedScore, 0.001)
    }

    @Test
    fun incompleteAndDifferentExerciseHistoryNeverCreatesFalseTrend() {
        val recommendation = policy.recommendLoad(
            "barbell-bench",
            4,
            8,
            60.0,
            listOf(57.5, 60.0, 62.5),
            listOf(
                performance("barbell-bench", 3, "轻松"),
                performance("barbell-bench", 2, "轻松", sessionCompleted = false),
                performance("barbell-row", 4, "轻松"),
            ),
        )

        assertEquals(LoadAdjustmentDirection.MAINTAIN, recommendation.direction)
        assertEquals(60.0, recommendation.suggestedWeightKg, 0.001)
        assertEquals(1, recommendation.evidence.size)
    }

    @Test
    fun firstTrialUsesLowestVenueLoadAndMixedTrendMaintainsBaseline() {
        val firstTrial = policy.recommendLoad("new-action", 3, 10, 40.0, listOf(30.0, 20.0, 25.0), emptyList())
        assertEquals(LoadAdjustmentDirection.FIRST_TRIAL, firstTrial.direction)
        assertEquals(20.0, firstTrial.suggestedWeightKg, 0.001)

        val mixed = policy.recommendLoad(
            "barbell-bench",
            4,
            8,
            61.3,
            listOf(57.5, 60.0, 62.5),
            listOf(
                performance("barbell-bench", 3, "轻松"),
                performance("barbell-bench", 2, "合适"),
                performance("barbell-bench", 1, "轻松"),
            ),
        )
        assertEquals(LoadAdjustmentDirection.MAINTAIN, mixed.direction)
        assertEquals(60.0, mixed.suggestedWeightKg, 0.001)
    }

    @Test
    fun loadBoundariesMaintainInsteadOfClaimingUnavailableAdjustment() {
        val easyHistory = (1L..3L).map { performance("barbell-bench", it, "轻松") }
        val hardHistory = (1L..3L).map {
            performance("barbell-bench", it, "吃力", actualSets = 3, actualTotalReps = 20)
        }

        val atMaximum = policy.recommendLoad("barbell-bench", 4, 8, 60.0, listOf(50.0, 60.0), easyHistory)
        val atMinimum = policy.recommendLoad("barbell-bench", 4, 8, 60.0, listOf(60.0, 62.5), hardHistory)

        assertEquals(LoadAdjustmentDirection.MAINTAIN, atMaximum.direction)
        assertEquals(60.0, atMaximum.suggestedWeightKg, 0.001)
        assertEquals(LoadAdjustmentDirection.MAINTAIN, atMinimum.direction)
        assertEquals(60.0, atMinimum.suggestedWeightKg, 0.001)
    }

    @Test
    fun validatorRejectsUnavailableEquipmentUnsupportedLoadsAndExcludedActions() {
        val result = validator.validate(
            validInput(
                candidate = candidate(
                    source = PlanCandidateSource.AI,
                    exercises = listOf(bench(weight = 61.3)),
                ),
                enabledEquipment = emptySet(),
                loads = listOf(60.0, 62.5),
                excluded = setOf("barbell-bench"),
            ),
        )

        assertFalse(result.isValid)
        assertEquals(null, result.candidate)
        assertTrue(PlanConflictCode.EQUIPMENT_UNAVAILABLE in result.conflicts.map { it.code })
        assertTrue(PlanConflictCode.UNSUPPORTED_WEIGHT in result.conflicts.map { it.code })
        assertTrue(PlanConflictCode.ACTION_EXCLUDED in result.conflicts.map { it.code })
    }

    @Test
    fun kneeTextConservativelyRejectsSquatAndAiCannotBypassValidator() {
        val squat = CandidateExercise("barbell-squat", "杠铃深蹲", "barbell", "quadriceps", 4, 8, 60.0)
        val input = validInput(
            candidate = candidate(PlanCandidateSource.AI, listOf(squat)),
            loads = listOf(60.0),
            injuriesText = "最近膝盖疼痛",
            injuriesHash = "knee-v1",
        )

        val result = validator.validate(input)

        assertFalse(result.isValid)
        assertEquals(listOf(PlanConflictCode.INJURY_RISK), result.conflicts.map { it.code })
        assertTrue(result.conflicts.single().message.contains("保守"))
    }

    @Test
    fun injuryExceptionOnlyAppliesToMatchingInjuryTextVersion() {
        val squat = CandidateExercise("barbell-squat", "杠铃深蹲", "barbell", "quadriceps", 4, 8, 60.0)
        val exception = InjuryFilterException("barbell-squat", "knee-v1")
        val accepted = validator.validate(
            validInput(
                candidate = candidate(exercises = listOf(squat)),
                loads = listOf(60.0),
                injuriesText = "膝盖疼痛",
                injuriesHash = "knee-v1",
                exceptions = setOf(exception),
            ),
        )
        val stale = validator.validate(
            validInput(
                candidate = candidate(exercises = listOf(squat)),
                loads = listOf(60.0),
                injuriesText = "膝盖疼痛加重",
                injuriesHash = "knee-v2",
                exceptions = setOf(exception),
            ),
        )

        assertTrue(accepted.isValid)
        assertEquals(PlanConflictCode.INJURY_RISK, stale.conflicts.single().code)
    }

    @Test
    fun unknownInjuryTextUsesConservativeUncertainConflict() {
        val result = validator.validate(
            validInput(
                candidate = candidate(exercises = listOf(bench())),
                injuriesText = "医生提醒暂时避免可能诱发症状的动作",
                injuriesHash = "unknown-v1",
            ),
        )

        assertEquals(PlanConflictCode.INJURY_UNCERTAIN, result.conflicts.single().code)
    }

    @Test
    fun multipleInjuryTermsEvaluateEveryMatchingRiskRule() {
        val result = validator.validate(
            validInput(
                candidate = candidate(exercises = listOf(bench())),
                injuriesText = "膝盖和肩部都有不适",
                injuriesHash = "multi-v1",
            ),
        )

        assertEquals(PlanConflictCode.INJURY_RISK, result.conflicts.single().code)
        assertTrue(result.conflicts.single().message.contains("肩部"))
    }

    @Test
    fun consecutiveDaysWithSamePrimaryMuscleReturnExplainableConflict() {
        val plan = WeeklyPlanCandidate(
            source = PlanCandidateSource.LOCAL,
            days = listOf(
                CandidateTrainingDay(1, "gym", listOf(bench())),
                CandidateTrainingDay(
                    2,
                    "gym",
                    listOf(bench().copy(exerciseId = "dumbbell-bench", primaryMuscle = "pectorals")),
                ),
            ),
        )
        val input = PlanConstraintInput(
            candidate = plan,
            enabledEquipmentByVenue = mapOf("gym" to setOf("barbell")),
            availableLoadsByVenueEquipment = mapOf(("gym" to "barbell") to listOf(60.0)),
            injuriesText = "",
            injuriesHash = "",
        )

        val result = validator.validate(input)

        assertEquals(PlanConflictCode.MUSCLE_RECOVERY_CONFLICT, result.conflicts.single().code)
        assertTrue(result.conflicts.single().message.contains("chest"))
    }

    @Test
    fun validLocalCandidateAndDefaultEquipmentLoadsStayExecutable() {
        val result = validator.validate(validInput(candidate = candidate(exercises = listOf(bench()))))

        assertTrue(result.isValid)
        assertEquals(PlanCandidateSource.LOCAL, result.candidate?.source)
        assertEquals(listOf(0.0), DefaultEquipmentLoads.forCategory("bodyweight"))
        assertTrue(62.5 in DefaultEquipmentLoads.forCategory("barbell"))
        assertEquals(listOf(0.0), DefaultEquipmentLoads.forCategory("unknown"))
    }

    private fun performance(
        exerciseId: String,
        completedAt: Long,
        feeling: String,
        sessionCompleted: Boolean = true,
        actualSets: Int = 4,
        actualTotalReps: Int = 32,
        actualWeightKg: Double = 60.0,
    ) = ExercisePerformance(
        exerciseId,
        completedAt,
        sessionCompleted,
        targetSets = 4,
        targetRepsPerSet = 8,
        targetWeightKg = 60.0,
        actualSets,
        actualTotalReps,
        actualWeightKg,
        feeling,
    )

    private fun bench(weight: Double = 60.0) = CandidateExercise(
        exerciseId = "barbell-bench",
        name = "杠铃卧推",
        equipmentId = "barbell",
        primaryMuscle = "chest",
        targetSets = 4,
        targetRepsPerSet = 8,
        targetWeightKg = weight,
    )

    private fun candidate(
        source: PlanCandidateSource = PlanCandidateSource.LOCAL,
        exercises: List<CandidateExercise>,
    ) = WeeklyPlanCandidate(source, listOf(CandidateTrainingDay(1, "gym", exercises)))

    private fun validInput(
        candidate: WeeklyPlanCandidate,
        enabledEquipment: Set<String> = setOf("barbell"),
        loads: List<Double> = listOf(60.0),
        excluded: Set<String> = emptySet(),
        injuriesText: String = "",
        injuriesHash: String = "",
        exceptions: Set<InjuryFilterException> = emptySet(),
    ) = PlanConstraintInput(
        candidate = candidate,
        enabledEquipmentByVenue = mapOf("gym" to enabledEquipment),
        availableLoadsByVenueEquipment = mapOf(("gym" to "barbell") to loads),
        injuriesText = injuriesText,
        injuriesHash = injuriesHash,
        injuryExceptions = exceptions,
        excludedExerciseIds = excluded,
    )
}
