package com.shanqijie.fitnessapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shanqijie.fitnessapp.data.AdaptivePlanWorkflow
import com.shanqijie.fitnessapp.data.EquipmentEntity
import com.shanqijie.fitnessapp.data.FitnessDatabase
import com.shanqijie.fitnessapp.data.FitnessStore
import com.shanqijie.fitnessapp.data.PlanCycleConfiguration
import com.shanqijie.fitnessapp.data.PlanDraftExplanation
import com.shanqijie.fitnessapp.data.PlanGenerationConflictException
import com.shanqijie.fitnessapp.data.PlanScheduleDayEntity
import com.shanqijie.fitnessapp.data.TimeProvider
import com.shanqijie.fitnessapp.data.TrainingVenueEntity
import com.shanqijie.fitnessapp.data.UserProfileEntity
import com.shanqijie.fitnessapp.data.VenueEquipmentEntity
import com.shanqijie.fitnessapp.data.VenueEquipmentLoadEntity
import com.shanqijie.fitnessapp.domain.CandidateExercise
import com.shanqijie.fitnessapp.domain.CandidateTrainingDay
import com.shanqijie.fitnessapp.domain.PlanCandidateSource
import com.shanqijie.fitnessapp.domain.PlanConflictCode
import com.shanqijie.fitnessapp.domain.WeeklyPlanCandidate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class AdaptivePlanWorkflowInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: FitnessDatabase
    private lateinit var store: FitnessStore
    private lateinit var workflow: AdaptivePlanWorkflow
    private var now = 1_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
        database = FitnessDatabase(context, DB_NAME)
        store = FitnessStore(database)
        workflow = AdaptivePlanWorkflow(store, TimeProvider { now })
        seedConfiguration()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun createsOnlyFirstWeekThenActivelyGeneratesAndConfirmsNextWeek() = runBlocking {
        val cycle = workflow.createCycle(configuration(totalWeeks = 2))
        val firstDraft = workflow.generateNextWeekDraft(cycle.id, candidate(), explanations())

        assertEquals(1, firstDraft.weekIndex)
        assertTrue(store.plannedWorkouts().isEmpty())

        val firstWeek = workflow.confirmWeek(firstDraft.id)
        assertEquals(2, firstWeek.size)
        assertEquals(listOf("2026-07-27", "2026-07-30"), firstWeek.map { it.scheduledDate })
        assertEquals(2, store.planCycle(cycle.id)?.currentWeek)

        val secondDraft = workflow.generateNextWeekDraft(cycle.id, candidate(), explanations())
        assertEquals(2, secondDraft.weekIndex)
        assertEquals("2026-08-03", secondDraft.weekStartDate)
        workflow.confirmWeek(secondDraft.id)

        assertEquals(4, store.plannedWorkouts().size)
        assertEquals("completed", store.planCycle(cycle.id)?.status)
        assertEquals("confirmed", store.weeklyPlanDraft(secondDraft.id)?.status)
    }

    @Test
    fun profileOrEquipmentInputChangesMakeDraftStaleAndBlockConfirmation() = runBlocking {
        val cycle = workflow.createCycle(configuration())
        val draft = workflow.generateNextWeekDraft(cycle.id, candidate(), explanations())
        val profile = requireNotNull(store.userProfile())
        store.upsertUserProfile(profile.copy(goal = "提升肌肉", updatedAt = 2_000L))

        val error = runCatching { workflow.confirmWeek(draft.id) }.exceptionOrNull()

        assertNotNull(error)
        assertEquals("stale", store.weeklyPlanDraft(draft.id)?.status)
        assertTrue(store.plannedWorkouts().isEmpty())
    }

    @Test
    fun aiCandidateCannotBypassInjuryAndVenueRules() = runBlocking {
        val cycle = workflow.createCycle(configuration())
        val profile = requireNotNull(store.userProfile())
        store.upsertUserProfile(profile.copy(injuries = "膝盖疼痛", updatedAt = 2_000L))
        val squat = bench.copy(exerciseId = "barbell-squat", name = "杠铃深蹲", primaryMuscle = "quadriceps")
        val unsafe = candidate(source = PlanCandidateSource.AI, firstExercise = squat)

        val error = runCatching { workflow.generateNextWeekDraft(cycle.id, unsafe, explanations()) }.exceptionOrNull()

        assertTrue(error is PlanGenerationConflictException)
        assertTrue(PlanConflictCode.INJURY_RISK in (error as PlanGenerationConflictException).conflicts.map { it.code })
        assertTrue(store.weeklyPlanDrafts().isEmpty())
    }

    @Test
    fun newCycleDefaultsPreservePriorScheduleDurationAndVenues() = runBlocking {
        val cycle = workflow.createCycle(configuration(totalWeeks = 3))

        val defaults = workflow.previousCycleDefaults(cycle.id)

        assertEquals(3, defaults.totalWeeks)
        assertEquals(60, defaults.preferredMinutes)
        assertEquals(listOf(1, 4), defaults.trainingDays.map { it.dayOfWeek })
        assertEquals(listOf("gym", "home"), defaults.trainingDays.map { it.venueId })
    }

    private fun seedConfiguration() {
        listOf(
            TrainingVenueEntity("gym", "公司健身房", true, now, now),
            TrainingVenueEntity("home", "家庭", false, now, now),
        ).forEach(store::upsertVenue)
        store.upsertEquipment(EquipmentEntity("barbell", "杠铃", "barbell", now, now))
        listOf("gym", "home").forEach { venueId ->
            store.upsertVenueEquipment(VenueEquipmentEntity(venueId, "barbell", true, now))
            store.replaceVenueEquipmentLoads(
                venueId,
                "barbell",
                listOf(VenueEquipmentLoadEntity(venueId, "barbell", 60.0, 0, now)),
            )
        }
        store.upsertUserProfile(
            UserProfileEntity(
                id = "user",
                displayName = "测试用户",
                birthYear = 1990,
                heightCm = 175.0,
                weightKg = 70.0,
                goal = "保持体能",
                injuries = "",
                weeklyTrainingDays = 2,
                preferredMinutes = 60,
                updatedAt = now,
            ),
        )
    }

    private fun configuration(totalWeeks: Int = 4) = PlanCycleConfiguration(
        id = "cycle-1",
        totalWeeks = totalWeeks,
        startDate = LocalDate.parse("2026-07-27"),
        preferredMinutes = 60,
        trainingDays = listOf(
            PlanScheduleDayEntity("cycle-1", 1, "gym", 0),
            PlanScheduleDayEntity("cycle-1", 4, "home", 1),
        ),
    )

    private fun candidate(
        source: PlanCandidateSource = PlanCandidateSource.LOCAL,
        firstExercise: CandidateExercise = bench,
    ) = WeeklyPlanCandidate(
        source,
        listOf(
            CandidateTrainingDay(1, "gym", listOf(firstExercise)),
            CandidateTrainingDay(4, "home", listOf(bench.copy(exerciseId = "barbell-row", name = "杠铃划船", primaryMuscle = "back"))),
        ),
    )

    private fun explanations() = listOf(
        PlanDraftExplanation("barbell-bench", "保持基线重量"),
        PlanDraftExplanation("barbell-row", "首次试练"),
    )

    private companion object {
        const val DB_NAME = "adaptive-plan-workflow-test.db"
        val bench = CandidateExercise("barbell-bench", "杠铃卧推", "barbell", "chest", 4, 8, 60.0)
    }
}
