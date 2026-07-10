package com.shanqijie.fitnessapp

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.shanqijie.fitnessapp.data.FitnessDatabase
import com.shanqijie.fitnessapp.data.FitnessStore
import com.shanqijie.fitnessapp.data.WorkoutSessionEntity
import com.shanqijie.fitnessapp.data.WorkoutSessionExerciseEntity
import com.shanqijie.fitnessapp.data.WorkoutSetLogEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class FitnessWorkoutRuntimeStoreInstrumentedTest {
    private lateinit var context: Context
    private var database: FitnessDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun createsWorkoutRuntimeTablesAndUniqueSetIndex() {
        val db = openFitnessDatabase()

        assertTrue(
            tableColumns(db, "workout_session").containsAll(
                setOf("current_exercise_id", "rest_ends_at", "paused_at"),
            ),
        )
        assertEquals(
            setOf(
                "id",
                "session_id",
                "exercise_id",
                "order_index",
                "target_sets",
                "target_reps",
                "target_weight_kg",
                "status",
            ),
            tableColumns(db, "workout_session_exercise"),
        )
        assertTrue(tableColumns(db, "workout_set_log").contains("session_exercise_id"))

        insertSetLog(db, id = "legacy-null-1", sessionExerciseId = null, setIndex = 1)
        insertSetLog(db, id = "legacy-null-2", sessionExerciseId = null, setIndex = 1)
        insertSetLog(db, id = "runtime-1", sessionExerciseId = "session-exercise-1", setIndex = 1)
        try {
            insertSetLog(db, id = "runtime-duplicate", sessionExerciseId = "session-exercise-1", setIndex = 1)
            fail("Expected duplicate runtime set index to be rejected")
        } catch (_: SQLiteConstraintException) {
            // Expected: legacy NULL links remain compatible while runtime links are unique.
        }
    }

    @Test
    fun migratesV6HistoryAndRemovesOnlyEmptySeedSession() {
        createV6Database()

        val db = openFitnessDatabase()
        val store = FitnessStore(requireNotNull(database))

        assertTrue(
            tableColumns(db, "workout_session").containsAll(
                setOf("current_exercise_id", "rest_ends_at", "paused_at"),
            ),
        )
        assertTrue(tableExists(db, "workout_session_exercise"))
        assertTrue(tableColumns(db, "workout_set_log").contains("session_exercise_id"))
        assertNull(store.workoutSession("session-local-smith-bench"))
        assertEquals(
            setOf("legacy-with-log", "other-empty-session"),
            store.workoutSessions().map { it.id }.toSet(),
        )
        assertEquals("legacy-with-log", store.allSetLogs().single().sessionId)
    }

    @Test
    fun persistsSessionExercisesRuntimeAndLinkedSetLogs() {
        val store = FitnessStore(openFitnessDatabaseHelper())
        store.upsertWorkoutSession(
            WorkoutSessionEntity(
                id = "session-runtime",
                plannedWorkoutId = "plan-1",
                venueId = "venue-1",
                exerciseId = "exercise-1",
                status = "in_progress",
                startedAt = 1000L,
                endedAt = null,
                updatedAt = 1000L,
                currentExerciseId = "session-exercise-1",
                restEndsAt = null,
                pausedAt = null,
            ),
        )
        store.upsertSessionExercise(
            WorkoutSessionExerciseEntity(
                id = "session-exercise-2",
                sessionId = "session-runtime",
                exerciseId = "exercise-2",
                orderIndex = 2,
                targetSets = 3,
                targetReps = "10-12",
                targetWeightKg = 20.0,
                status = "pending",
            ),
        )
        store.upsertSessionExercise(
            WorkoutSessionExerciseEntity(
                id = "session-exercise-1",
                sessionId = "session-runtime",
                exerciseId = "exercise-1",
                orderIndex = 1,
                targetSets = 4,
                targetReps = "8-10",
                targetWeightKg = 70.0,
                status = "active",
            ),
        )
        store.updateWorkoutRuntime(
            id = "session-runtime",
            currentExerciseId = "session-exercise-2",
            restEndsAt = 3000L,
            pausedAt = 2500L,
            updatedAt = 2000L,
        )
        store.updateSessionExerciseStatus("session-exercise-1", "completed")
        store.insertSetLog(
            WorkoutSetLogEntity(
                id = "runtime-set-1",
                sessionId = "session-runtime",
                exerciseId = "exercise-1",
                setIndex = 1,
                actualReps = 8,
                actualWeightKg = 70.0,
                feeling = "合适",
                completed = true,
                completedAt = 2100L,
                sessionExerciseId = "session-exercise-1",
            ),
        )

        val session = requireNotNull(store.workoutSession("session-runtime"))
        assertEquals("session-exercise-2", session.currentExerciseId)
        assertEquals(3000L, session.restEndsAt)
        assertEquals(2500L, session.pausedAt)
        assertEquals(2000L, session.updatedAt)
        assertEquals(
            listOf("session-exercise-1", "session-exercise-2"),
            store.sessionExercises("session-runtime").map { it.id },
        )
        assertEquals("completed", store.sessionExercises("session-runtime").first().status)
        assertEquals("session-exercise-1", store.setLogs("session-runtime").single().sessionExerciseId)

        store.deleteSessionExercises("session-runtime")
        assertEquals(emptyList<WorkoutSessionExerciseEntity>(), store.sessionExercises("session-runtime"))
    }

    private fun openFitnessDatabaseHelper(): FitnessDatabase =
        FitnessDatabase(context, DB_NAME).also {
            database = it
            it.writableDatabase
        }

    private fun openFitnessDatabase(): SQLiteDatabase = openFitnessDatabaseHelper().writableDatabase

    private fun createV6Database() {
        context.openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE workout_session (
                    id TEXT PRIMARY KEY,
                    planned_workout_id TEXT,
                    venue_id TEXT NOT NULL,
                    exercise_id TEXT NOT NULL,
                    status TEXT NOT NULL,
                    started_at INTEGER NOT NULL,
                    ended_at INTEGER,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE workout_set_log (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    exercise_id TEXT NOT NULL,
                    set_index INTEGER NOT NULL,
                    actual_reps INTEGER NOT NULL,
                    actual_weight_kg REAL NOT NULL,
                    feeling TEXT NOT NULL,
                    completed INTEGER NOT NULL,
                    completed_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO workout_session VALUES
                    ('session-local-smith-bench', NULL, 'venue-1', 'exercise-1', 'in_progress', 1000, NULL, 1000),
                    ('legacy-with-log', NULL, 'venue-1', 'exercise-2', 'completed', 1100, 2000, 2000),
                    ('other-empty-session', NULL, 'venue-1', 'exercise-3', 'in_progress', 1200, NULL, 1200)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO workout_set_log VALUES
                    ('legacy-set', 'legacy-with-log', 'exercise-2', 1, 10, 20.0, '合适', 1, 1900)
                """.trimIndent(),
            )
            db.version = 6
        }
    }

    private fun insertSetLog(
        db: SQLiteDatabase,
        id: String,
        sessionExerciseId: String?,
        setIndex: Int,
        sessionId: String = "session-runtime",
    ) {
        val sessionExerciseColumn = if (sessionExerciseId == null) "NULL" else "?"
        val bindArgs = buildList<Any> {
            add(id)
            add(sessionId)
            if (sessionExerciseId != null) add(sessionExerciseId)
            add(setIndex)
        }.toTypedArray()
        db.execSQL(
            """
            INSERT INTO workout_set_log (
                id, session_id, exercise_id, session_exercise_id, set_index,
                actual_reps, actual_weight_kg, feeling, completed, completed_at
            ) VALUES (?, ?, 'exercise-1', $sessionExerciseColumn, ?, 8, 70.0, '合适', 1, 2000)
            """.trimIndent(),
            bindArgs,
        )
    }

    private fun tableColumns(db: SQLiteDatabase, table: String): Set<String> =
        db.rawQuery("PRAGMA table_info($table)", emptyArray()).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
        }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table),
        ).use { it.moveToFirst() }

    private companion object {
        const val DB_NAME = "fitness-workout-runtime-test.db"
    }
}
