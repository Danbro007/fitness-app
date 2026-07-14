package com.shanqijie.fitnessapp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.shanqijie.fitnessapp.data.AiProviderEntity
import com.shanqijie.fitnessapp.data.BodyMeasurement
import com.shanqijie.fitnessapp.data.AiDraftEntity
import com.shanqijie.fitnessapp.data.ExerciseMediaEntity
import com.shanqijie.fitnessapp.data.EquipmentEntity
import com.shanqijie.fitnessapp.data.FoodLogEntity
import com.shanqijie.fitnessapp.data.FitnessDatabase
import com.shanqijie.fitnessapp.data.FitnessStore
import com.shanqijie.fitnessapp.data.PlannedExerciseEntity
import com.shanqijie.fitnessapp.data.PlannedWorkoutEntity
import com.shanqijie.fitnessapp.data.TrainingAdjustmentEntity
import com.shanqijie.fitnessapp.data.TrainingVenueEntity
import com.shanqijie.fitnessapp.data.UserProfileEntity
import com.shanqijie.fitnessapp.data.VenueEquipmentEntity
import com.shanqijie.fitnessapp.data.WorkoutSessionEntity
import com.shanqijie.fitnessapp.data.WorkoutSetLogEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch

class FitnessStoreInstrumentedTest {
    private lateinit var context: Context

    @Test
    fun databaseSingletonHandlesFirstRepeatedAndContendedAccess() {
        val instanceField = FitnessDatabase::class.java.getDeclaredField("instance").apply {
            isAccessible = true
        }
        (instanceField.get(null) as? FitnessDatabase)?.close()
        instanceField.set(null, null)
        val started = CountDownLatch(2)
        val results = Collections.synchronizedList(mutableListOf<FitnessDatabase>())
        val workers = synchronized(FitnessDatabase.Companion) {
            List(2) {
                Thread {
                    started.countDown()
                    results += FitnessDatabase.get(context)
                }.apply { start() }
            }.also { started.await() }
        }
        workers.forEach(Thread::join)

        assertEquals(2, results.size)
        assertTrue(results[0] === results[1])
        assertTrue(results[0] === FitnessDatabase.get(context))
        results[0].close()
        instanceField.set(null, null)

        SQLiteDatabase.create(null).use { memoryDb ->
            FitnessDatabase(context, "no-upgrade-${System.nanoTime()}.db").use { helper ->
                helper.onUpgrade(memoryDb, 9, 9)
            }
        }
    }

    @Test
    fun versionOneEmptyDatabaseRunsEveryHistoricalAdditiveMigration() {
        val legacyName = "fitness-v1-${System.nanoTime()}.db"
        context.deleteDatabase(legacyName)
        context.openOrCreateDatabase(legacyName, Context.MODE_PRIVATE, null).use { raw ->
            raw.version = 1
        }

        val upgraded = FitnessDatabase(context, legacyName)
        try {
            val database = upgraded.readableDatabase
            assertEquals(9, database.version)
            val tables = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table'",
                emptyArray(),
            ).use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            assertTrue("food_log" in tables)
            assertTrue("ai_draft" in tables)
            assertTrue("workout_session_exercise" in tables)
            val foodColumns = database.rawQuery("PRAGMA table_info(food_log)", emptyArray()).use { cursor ->
                buildSet {
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
            }
            assertTrue(setOf("image_uri", "provider_id", "model").all { it in foodColumns })
        } finally {
            upgraded.close()
            context.deleteDatabase(legacyName)
        }
    }

    @Test
    fun versionEightProfileMigratesToBmiAndAvatarWithoutLosingLegacyData() {
        val legacyName = "fitness-v8-${System.nanoTime()}.db"
        context.deleteDatabase(legacyName)
        context.openOrCreateDatabase(legacyName, Context.MODE_PRIVATE, null).use { raw ->
            raw.execSQL("""
                CREATE TABLE user_profile (
                    id TEXT PRIMARY KEY, display_name TEXT NOT NULL, birth_year INTEGER NOT NULL,
                    height_cm REAL NOT NULL, weight_kg REAL NOT NULL, goal TEXT NOT NULL, injuries TEXT NOT NULL,
                    weekly_training_days INTEGER NOT NULL, preferred_minutes INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                    measured_at TEXT NOT NULL DEFAULT '', body_type TEXT NOT NULL DEFAULT '', body_fat_percentage REAL,
                    body_fat_mass_kg REAL, skeletal_muscle_kg REAL, body_water_kg REAL, basal_metabolism_kcal INTEGER,
                    waist_hip_ratio REAL, body_age INTEGER
                )
            """.trimIndent())
            raw.execSQL("INSERT INTO user_profile(id,display_name,birth_year,height_cm,weight_kg,goal,injuries,weekly_training_days,preferred_minutes,updated_at) VALUES('legacy','旧档案',1990,175,70,'保持体能','',3,45,1)")
            raw.version = 8
        }
        val upgraded = FitnessDatabase(context, legacyName)
        try {
            val profile = FitnessStore(upgraded).userProfile()
            assertEquals("旧档案", profile?.displayName)
            assertNull(profile?.bodyMeasurement?.bmi)
            assertEquals("", profile?.avatarPath)
            assertEquals(9, upgraded.readableDatabase.version)
        } finally {
            upgraded.close()
            context.deleteDatabase(legacyName)
        }
    }
    private lateinit var db: FitnessDatabase
    private lateinit var store: FitnessStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
        db = FitnessDatabase(context, DB_NAME)
        store = FitnessStore(db)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun persistsExerciseMediaAndCompletedWorkoutSet() {
        store.upsertExerciseMedia(
            listOf(
                ExerciseMediaEntity(
                    exerciseId = "0748",
                    name = "smith bench press",
                    bodyPart = "chest",
                    equipment = "smith machine",
                    target = "pectorals",
                    mediaId = "trqKQv2",
                    localPath = "exercise-media/gifs/0748-trqKQv2.gif",
                    assetPackId = "exercise-gifs-pack-001",
                    bytes = 1234,
                    sha256 = "abc",
                ),
            ),
        )

        store.insertSetLog(
            WorkoutSetLogEntity(
                id = "set-1",
                sessionId = "session-1",
                exerciseId = "0748",
                setIndex = 1,
                actualReps = 8,
                actualWeightKg = 70.0,
                feeling = "合适",
                completed = true,
                completedAt = 1000L,
            ),
        )

        assertEquals(1, store.exerciseMediaCount())
        assertEquals(1, store.exercisesByEquipment("smith machine").size)
        assertNotNull(store.exerciseById("0748"))
        assertEquals(1, store.completedSetCount("session-1"))
        assertEquals("合适", store.setLogs("session-1").single().feeling)
    }

    @Test
    fun emptyMediaBatchNestedTransactionAndMissingDraftAreSafe() {
        store.upsertExerciseMedia(emptyList())
        val result = store.transaction {
            transaction { "nested-result" }
        }

        assertEquals("nested-result", result)
        assertEquals(0, store.exerciseMediaCount())
        assertNull(store.aiDraft("missing-draft"))
    }

    @Test
    fun persistsVenueEquipmentAndPlannedWorkoutsForLocalPlanning() {
        store.upsertVenue(
            TrainingVenueEntity(
                id = "venue-1",
                name = "公司健身房",
                isDefault = true,
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )
        store.updateVenueName("venue-1", "小区健身房", updatedAt = 2000L)
        store.upsertEquipment(
            EquipmentEntity(
                id = "equipment-smith",
                name = "史密斯机",
                category = "machine",
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )
        store.upsertEquipment(
            EquipmentEntity(
                id = "equipment-dumbbell",
                name = "哑铃",
                category = "free-weight",
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )
        store.upsertPlannedWorkout(
            PlannedWorkoutEntity(
                id = "plan-1",
                name = "胸部力量 A",
                scheduledDate = "2026-07-08",
                venueId = "venue-1",
                status = "planned",
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )

        assertEquals("小区健身房", store.venue("venue-1")?.name)
        assertEquals(2000L, store.venue("venue-1")?.updatedAt)
        assertEquals(listOf("史密斯机", "哑铃"), store.allEquipment().map { it.name })
        assertEquals(listOf("哑铃", "史密斯机"), store.equipmentNamesForVenue())
        assertEquals(listOf("胸部力量 A"), store.plannedWorkouts().map { it.name })
    }

    @Test
    fun persistsPlannedExercisesAndFiltersSetLogsByExercise() {
        store.upsertPlannedWorkout(
            PlannedWorkoutEntity(
                id = "plan-1",
                name = "胸部力量 A",
                scheduledDate = "2026-07-08",
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
        store.upsertPlannedExercise(
            PlannedExerciseEntity(
                id = "plan-1-exercise-2",
                plannedWorkoutId = "plan-1",
                exerciseId = "0014",
                orderIndex = 2,
                targetSets = 3,
                targetReps = "10-12",
                targetWeightKg = 0.0,
                note = "辅助",
            ),
        )

        store.insertSetLog(
            WorkoutSetLogEntity(
                id = "set-smith",
                sessionId = "session-plan-1",
                exerciseId = "0748",
                setIndex = 1,
                actualReps = 8,
                actualWeightKg = 70.0,
                feeling = "合适",
                completed = true,
                completedAt = 1000L,
            ),
        )
        store.insertSetLog(
            WorkoutSetLogEntity(
                id = "set-other",
                sessionId = "session-plan-1",
                exerciseId = "0014",
                setIndex = 1,
                actualReps = 12,
                actualWeightKg = 0.0,
                feeling = "轻松",
                completed = true,
                completedAt = 1100L,
            ),
        )

        val plannedExercises = store.plannedExercises("plan-1")

        assertEquals(listOf("0748", "0014"), plannedExercises.map { it.exerciseId })
        assertEquals(4, plannedExercises.first().targetSets)
        assertEquals(listOf("plan-1-exercise-1", "plan-1-exercise-2"), store.allPlannedExercises().map { it.id })
        assertEquals(1, store.completedSetCount("session-plan-1", "0748"))
        assertEquals(listOf("合适"), store.setLogs("session-plan-1", "0748").map { it.feeling })
    }

    @Test
    fun updatesPlannedWorkoutDetailsAndExerciseTargets() {
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

        store.updatePlannedWorkoutDetails(
            id = "plan-1",
            name = "胸背力量 A",
            scheduledDate = "2026-07-10",
            updatedAt = 2000L,
        )
        store.updatePlannedExerciseTarget(
            id = "plan-1-exercise-1",
            targetSets = 5,
            targetReps = "6-8",
            targetWeightKg = 82.5,
            note = "加重",
        )

        val workout = store.plannedWorkouts().single()
        val exercise = store.plannedExercises("plan-1").single()

        assertEquals("胸背力量 A", workout.name)
        assertEquals("2026-07-10", workout.scheduledDate)
        assertEquals(2000L, workout.updatedAt)
        assertEquals(5, exercise.targetSets)
        assertEquals("6-8", exercise.targetReps)
        assertEquals(82.5, exercise.targetWeightKg, 0.01)
        assertEquals("加重", exercise.note)
    }

    @Test
    fun updatesWorkoutSessionAndPlannedWorkoutStatuses() {
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
        store.upsertWorkoutSession(
            WorkoutSessionEntity(
                id = "session-1",
                plannedWorkoutId = "plan-1",
                venueId = "venue-1",
                exerciseId = "0748",
                status = "in_progress",
                startedAt = 1000L,
                endedAt = null,
                updatedAt = 1000L,
            ),
        )

        store.updateWorkoutSessionStatus(
            id = "session-1",
            status = "completed",
            endedAt = 2500L,
            updatedAt = 2500L,
        )
        store.updatePlannedWorkoutStatus(
            id = "plan-1",
            status = "completed",
            updatedAt = 2500L,
        )

        val session = store.workoutSessions().single()
        val plan = store.plannedWorkouts().single()

        assertEquals("completed", session.status)
        assertEquals(2500L, session.endedAt)
        assertEquals(2500L, session.updatedAt)
        assertEquals("completed", plan.status)
        assertEquals(2500L, plan.updatedAt)
    }

    @Test
    fun persistsAiProviderMetadataWithoutPlaintextApiKey() {
        store.upsertAiProvider(
            AiProviderEntity(
                id = "deepseek",
                displayName = "DeepSeek",
                baseUrl = "https://api.deepseek.com",
                model = "deepseek-v4-flash",
                enabled = true,
                apiKeyStored = false,
                updatedAt = 1000L,
            ),
        )

        val provider = store.aiProvider("deepseek")

        assertEquals("DeepSeek", provider?.displayName)
        assertEquals("deepseek-v4-flash", provider?.model)
        assertEquals(false, provider?.apiKeyStored)
        assertEquals(listOf("deepseek"), store.aiProviders().map { it.id })
    }

    @Test
    fun persistsProfileFoodLogsAndAiDrafts() {
        store.upsertUserProfile(
            UserProfileEntity(
                id = "profile-local",
                displayName = "山崎",
                birthYear = 1994,
                heightCm = 176.0,
                weightKg = 76.5,
                goal = "增肌减脂",
                injuries = "肩峰撞击",
                weeklyTrainingDays = 4,
                preferredMinutes = 50,
                updatedAt = 1000L,
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
            ),
        )
        store.insertFoodLog(
            FoodLogEntity(
                id = "food-1",
                loggedDate = "2026-07-09",
                name = "鸡胸饭",
                calories = 620,
                proteinGrams = 42.0,
                carbsGrams = 68.0,
                fatGrams = 16.0,
                source = "ai_estimate",
                imageNote = "一碗米饭，一块鸡胸肉",
                confirmed = true,
                createdAt = 1100L,
            ),
        )
        store.upsertAiDraft(
            AiDraftEntity(
                id = "draft-1",
                type = "food_estimate",
                title = "饮食估算：鸡胸饭",
                content = "约 620 千卡，蛋白质 42g。",
                status = "draft",
                createdAt = 1200L,
                updatedAt = 1200L,
                confirmedAt = null,
            ),
        )
        store.updateAiDraftStatus("draft-1", status = "confirmed", confirmedAt = 1300L, updatedAt = 1300L)

        assertEquals("山崎", store.userProfile()?.displayName)
        assertEquals(76.5, store.userProfile()?.weightKg ?: 0.0, 0.01)
        requireNotNull(store.userProfile()?.bodyMeasurement).also { measurement ->
            assertEquals("偏胖型", measurement.bodyType)
            assertEquals(24.8, measurement.bodyFatPercentage ?: 0.0, 0.01)
            assertEquals(1613, measurement.basalMetabolismKcal)
            assertNull(measurement.bodyAge)
        }
        assertEquals(listOf("鸡胸饭"), store.foodLogs().map { it.name })
        assertEquals(620, store.foodLogs().single().calories)
        assertEquals("confirmed", store.aiDrafts().single().status)
        assertEquals(1300L, store.aiDrafts().single().confirmedAt)
    }

    @Test
    fun deletesEquipmentWorkoutAndReplacesPlannedExercise() {
        store.upsertEquipment(
            EquipmentEntity(
                id = "equipment-smith",
                name = "史密斯机",
                category = "machine",
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )
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

        store.deleteEquipment("equipment-smith")
        store.updatePlannedExerciseExercise(
            id = "plan-1-exercise-1",
            exerciseId = "0289",
            note = "替换：原动作器械被占用",
        )

        assertEquals(emptyList<String>(), store.allEquipment().map { it.name })
        assertEquals("0289", store.plannedExercises("plan-1").single().exerciseId)
        assertEquals("替换：原动作器械被占用", store.plannedExercises("plan-1").single().note)

        store.deletePlannedWorkout("plan-1")

        assertEquals(emptyList<PlannedWorkoutEntity>(), store.plannedWorkouts())
        assertEquals(emptyList<PlannedExerciseEntity>(), store.plannedExercises("plan-1"))
    }

    @Test
    fun persistsVenueEquipmentBindingPreferencesFoodImageMetadataAndTrainingAdjustment() {
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
        store.upsertVenueEquipment(
            VenueEquipmentEntity(
                venueId = "venue-1",
                equipmentId = "equipment-smith",
                available = true,
                updatedAt = 1200L,
            ),
        )
        store.upsertFoodLog(
            FoodLogEntity(
                id = "food-vision",
                loggedDate = "2026-07-09",
                name = "鸡胸饭",
                calories = 620,
                proteinGrams = 42.0,
                carbsGrams = 68.0,
                fatGrams = 16.0,
                source = "vision_ai",
                imageNote = "已选择食物照片",
                imageUri = "content://meal/1",
                providerId = "deepseek",
                model = "deepseek-v4-flash",
                confirmed = true,
                createdAt = 1300L,
            ),
        )
        store.upsertTrainingAdjustment(
            TrainingAdjustmentEntity(
                id = "adjust-1",
                exerciseId = "0748",
                title = "下次加重",
                content = "最近一组反馈轻松，下次可加 2.5kg。",
                status = "draft",
                createdAt = 1400L,
                updatedAt = 1400L,
                confirmedAt = null,
            ),
        )
        store.putPreference("onboarding_completed", "true")

        assertEquals(listOf("史密斯机"), store.equipmentForVenue("venue-1").map { it.name })
        assertEquals("true", store.preference("onboarding_completed"))
        assertEquals("content://meal/1", store.foodLogs().single().imageUri)
        assertEquals("deepseek-v4-flash", store.foodLogs().single().model)
        assertEquals("下次加重", store.trainingAdjustments().single().title)
    }

    private companion object {
        const val DB_NAME = "fitness-store-test.db"
    }
}
