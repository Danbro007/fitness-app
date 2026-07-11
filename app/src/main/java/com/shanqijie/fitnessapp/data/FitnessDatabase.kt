package com.shanqijie.fitnessapp.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class FitnessDatabase(
    context: Context,
    name: String = DATABASE_NAME,
) : SQLiteOpenHelper(context.applicationContext, name, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        createTables(db)
    }

    private fun createTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS training_venue (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                is_default INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS equipment (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS exercise_media (
                exercise_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                body_part TEXT NOT NULL,
                equipment TEXT NOT NULL,
                target TEXT NOT NULL,
                media_id TEXT NOT NULL,
                local_path TEXT NOT NULL,
                asset_pack_id TEXT NOT NULL,
                bytes INTEGER NOT NULL,
                sha256 TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS planned_workout (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                scheduled_date TEXT NOT NULL,
                venue_id TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS planned_exercise (
                id TEXT PRIMARY KEY,
                planned_workout_id TEXT NOT NULL,
                exercise_id TEXT NOT NULL,
                order_index INTEGER NOT NULL,
                target_sets INTEGER NOT NULL,
                target_reps TEXT NOT NULL,
                target_weight_kg REAL NOT NULL,
                note TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS workout_session (
                id TEXT PRIMARY KEY,
                planned_workout_id TEXT,
                venue_id TEXT NOT NULL,
                exercise_id TEXT NOT NULL,
                status TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                updated_at INTEGER NOT NULL,
                current_exercise_id TEXT,
                rest_ends_at INTEGER,
                paused_at INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS workout_session_exercise (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                exercise_id TEXT NOT NULL,
                order_index INTEGER NOT NULL,
                target_sets INTEGER NOT NULL,
                target_reps TEXT NOT NULL,
                target_weight_kg REAL NOT NULL,
                status TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS workout_set_log (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                exercise_id TEXT NOT NULL,
                session_exercise_id TEXT,
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
            CREATE TABLE IF NOT EXISTS ai_provider (
                id TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                base_url TEXT NOT NULL,
                model TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                api_key_stored INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_profile (
                id TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                birth_year INTEGER NOT NULL,
                height_cm REAL NOT NULL,
                weight_kg REAL NOT NULL,
                goal TEXT NOT NULL,
                injuries TEXT NOT NULL,
                weekly_training_days INTEGER NOT NULL,
                preferred_minutes INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                measured_at TEXT NOT NULL DEFAULT '',
                body_type TEXT NOT NULL DEFAULT '',
                body_fat_percentage REAL,
                body_fat_mass_kg REAL,
                skeletal_muscle_kg REAL,
                body_water_kg REAL,
                basal_metabolism_kcal INTEGER,
                waist_hip_ratio REAL,
                body_age INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS food_log (
                id TEXT PRIMARY KEY,
                logged_date TEXT NOT NULL,
                name TEXT NOT NULL,
                calories INTEGER NOT NULL,
                protein_grams REAL NOT NULL,
                carbs_grams REAL NOT NULL,
                fat_grams REAL NOT NULL,
                source TEXT NOT NULL,
                image_note TEXT NOT NULL,
                image_uri TEXT NOT NULL DEFAULT '',
                provider_id TEXT NOT NULL DEFAULT '',
                model TEXT NOT NULL DEFAULT '',
                confirmed INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ai_draft (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                metadata_json TEXT NOT NULL DEFAULT '',
                confirmed_at INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS venue_equipment (
                venue_id TEXT NOT NULL,
                equipment_id TEXT NOT NULL,
                available INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(venue_id, equipment_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS app_preference (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS training_adjustment (
                id TEXT PRIMARY KEY,
                exercise_id TEXT NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                confirmed_at INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_exercise_media_equipment ON exercise_media(equipment)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_exercise_media_asset_pack ON exercise_media(asset_pack_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_planned_exercise_workout ON planned_exercise(planned_workout_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_session_exercise_order ON workout_session_exercise(session_id, order_index)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_set_session ON workout_set_log(session_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_set_session_exercise ON workout_set_log(session_id, exercise_id)")
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_set_runtime_order
            ON workout_set_log(session_id, session_exercise_id, set_index)
            WHERE session_exercise_id IS NOT NULL
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_food_log_date ON food_log(logged_date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ai_draft_type_status ON ai_draft(type, status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_venue_equipment_venue ON venue_equipment(venue_id, available)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_training_adjustment_exercise ON training_adjustment(exercise_id, status)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 8) {
            addColumnIfMissing(db, "user_profile", "measured_at", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "user_profile", "body_type", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "user_profile", "body_fat_percentage", "REAL")
            addColumnIfMissing(db, "user_profile", "body_fat_mass_kg", "REAL")
            addColumnIfMissing(db, "user_profile", "skeletal_muscle_kg", "REAL")
            addColumnIfMissing(db, "user_profile", "body_water_kg", "REAL")
            addColumnIfMissing(db, "user_profile", "basal_metabolism_kcal", "INTEGER")
            addColumnIfMissing(db, "user_profile", "waist_hip_ratio", "REAL")
            addColumnIfMissing(db, "user_profile", "body_age", "INTEGER")
        }
        if (oldVersion < 7) {
            addColumnIfMissing(db, "workout_session", "current_exercise_id", "TEXT")
            addColumnIfMissing(db, "workout_session", "rest_ends_at", "INTEGER")
            addColumnIfMissing(db, "workout_session", "paused_at", "INTEGER")
            addColumnIfMissing(db, "workout_set_log", "session_exercise_id", "TEXT")
        }
        if (oldVersion < 2) {
            createTables(db)
        }
        if (oldVersion < 3) {
            createTables(db)
        }
        if (oldVersion < 4) {
            createTables(db)
        }
        if (oldVersion < 5) {
            createTables(db)
            addColumnIfMissing(db, "food_log", "image_uri", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "food_log", "provider_id", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "food_log", "model", "TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 6) {
            createTables(db)
            addColumnIfMissing(db, "ai_draft", "metadata_json", "TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 7) {
            createTables(db)
            db.execSQL(
                """
                DELETE FROM workout_session
                WHERE id = 'session-local-smith-bench'
                  AND status = 'in_progress'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM workout_set_log
                      WHERE workout_set_log.session_id = workout_session.id
                  )
                """.trimIndent(),
            )
        }
    }

    private fun addColumnIfMissing(db: SQLiteDatabase, table: String, column: String, definition: String) {
        if (!tableExists(db, table)) return
        val cursor = db.rawQuery("PRAGMA table_info($table)", emptyArray())
        val exists = try {
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == column) {
                    found = true
                    break
                }
            }
            found
        } finally {
            cursor.close()
        }
        if (!exists) {
            db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
        }
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean {
        val cursor = db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table),
        )
        return try {
            cursor.moveToFirst()
        } finally {
            cursor.close()
        }
    }

    companion object {
        private const val DATABASE_NAME = "fitness.db"
        private const val DATABASE_VERSION = 8

        @Volatile
        private var instance: FitnessDatabase? = null

        fun get(context: Context): FitnessDatabase =
            instance ?: synchronized(this) {
                instance ?: FitnessDatabase(context).also { instance = it }
            }
    }
}
