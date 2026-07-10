package com.shanqijie.fitnessapp.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement

class FitnessStore(private val database: FitnessDatabase) {
    @Synchronized
    fun <T> transaction(block: FitnessStore.() -> T): T {
        val db = database.writableDatabase
        if (db.inTransaction()) return block()

        db.beginTransaction()
        return try {
            val result = block()
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
        }
    }

    fun upsertVenue(entity: TrainingVenueEntity) {
        database.writableDatabase.insertWithOnConflict(
            "training_venue",
            null,
            ContentValues().apply {
                put("id", entity.id)
                put("name", entity.name)
                put("is_default", if (entity.isDefault) 1 else 0)
                put("created_at", entity.createdAt)
                put("updated_at", entity.updatedAt)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun venue(id: String): TrainingVenueEntity? {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, name, is_default, created_at, updated_at
            FROM training_venue
            WHERE id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(id),
        )
        return try {
            if (cursor.moveToFirst()) cursor.toTrainingVenue() else null
        } finally {
            cursor.close()
        }
    }

    fun defaultVenue(): TrainingVenueEntity? {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, name, is_default, created_at, updated_at
            FROM training_venue
            WHERE is_default = 1
            ORDER BY created_at
            LIMIT 1
            """.trimIndent(),
            emptyArray(),
        )
        return try {
            if (cursor.moveToFirst()) cursor.toTrainingVenue() else null
        } finally {
            cursor.close()
        }
    }

    fun updateVenueName(id: String, name: String, updatedAt: Long) {
        database.writableDatabase.update(
            "training_venue",
            ContentValues().apply {
                put("name", name)
                put("updated_at", updatedAt)
            },
            "id = ?",
            arrayOf(id),
        )
    }

    fun trainingVenues(): List<TrainingVenueEntity> {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, name, is_default, created_at, updated_at
            FROM training_venue
            ORDER BY is_default DESC, created_at
            """.trimIndent(),
            emptyArray(),
        )
        return try {
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toTrainingVenue())
                }
            }
        } finally {
            cursor.close()
        }
    }

    fun setDefaultVenue(id: String, updatedAt: Long) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.update(
                "training_venue",
                ContentValues().apply {
                    put("is_default", 0)
                    put("updated_at", updatedAt)
                },
                null,
                null,
            )
            db.update(
                "training_venue",
                ContentValues().apply {
                    put("is_default", 1)
                    put("updated_at", updatedAt)
                },
                "id = ?",
                arrayOf(id),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun upsertEquipment(entity: EquipmentEntity) {
        database.writableDatabase.insertWithOnConflict(
            "equipment",
            null,
            ContentValues().apply {
                put("id", entity.id)
                put("name", entity.name)
                put("category", entity.category)
                put("created_at", entity.createdAt)
                put("updated_at", entity.updatedAt)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun allEquipment(): List<EquipmentEntity> {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, name, category, created_at, updated_at
            FROM equipment
            ORDER BY rowid
            """.trimIndent(),
            emptyArray(),
        )
        return try {
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toEquipment())
                }
            }
        } finally {
            cursor.close()
        }
    }

    fun upsertVenueEquipment(entity: VenueEquipmentEntity) {
        database.writableDatabase.insertWithOnConflict(
            "venue_equipment",
            null,
            ContentValues().apply {
                put("venue_id", entity.venueId)
                put("equipment_id", entity.equipmentId)
                put("available", if (entity.available) 1 else 0)
                put("updated_at", entity.updatedAt)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun venueEquipment(): List<VenueEquipmentEntity> {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT venue_id, equipment_id, available, updated_at
            FROM venue_equipment
            ORDER BY venue_id, equipment_id
            """.trimIndent(),
            emptyArray(),
        )
        return try {
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toVenueEquipment())
                }
            }
        } finally {
            cursor.close()
        }
    }

    fun equipmentForVenue(venueId: String): List<EquipmentEntity> {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT e.id, e.name, e.category, e.created_at, e.updated_at
            FROM equipment e
            INNER JOIN venue_equipment ve ON ve.equipment_id = e.id
            WHERE ve.venue_id = ? AND ve.available = 1
            ORDER BY e.rowid
            """.trimIndent(),
            arrayOf(venueId),
        )
        return try {
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toEquipment())
                }
            }
        } finally {
            cursor.close()
        }
    }

    fun equipmentNamesForVenue(venueId: String): List<String> =
        equipmentForVenue(venueId)
            .sortedWith(compareBy<EquipmentEntity> { it.category }.thenBy { it.name })
            .map { it.name }

    fun equipmentNamesForVenue(): List<String> =
        allEquipment()
            .sortedWith(compareBy<EquipmentEntity> { it.category }.thenBy { it.name })
            .map { it.name }

    fun deleteEquipment(id: String) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("venue_equipment", "equipment_id = ?", arrayOf(id))
            db.delete("equipment", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun upsertPlannedWorkout(entity: PlannedWorkoutEntity) {
        database.writableDatabase.insertWithOnConflict(
            "planned_workout",
            null,
            ContentValues().apply {
                put("id", entity.id)
                put("name", entity.name)
                put("scheduled_date", entity.scheduledDate)
                put("venue_id", entity.venueId)
                put("status", entity.status)
                put("created_at", entity.createdAt)
                put("updated_at", entity.updatedAt)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun plannedWorkouts(): List<PlannedWorkoutEntity> {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, name, scheduled_date, venue_id, status, created_at, updated_at
            FROM planned_workout
            ORDER BY scheduled_date, created_at
            """.trimIndent(),
            emptyArray(),
        )
        return try {
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toPlannedWorkout())
                }
            }
        } finally {
            cursor.close()
        }
    }

    fun updatePlannedWorkoutDetails(id: String, name: String, scheduledDate: String, updatedAt: Long) {
        database.writableDatabase.update(
            "planned_workout",
            ContentValues().apply {
                put("name", name)
                put("scheduled_date", scheduledDate)
                put("updated_at", updatedAt)
            },
            "id = ?",
            arrayOf(id),
        )
    }

    fun updatePlannedWorkoutStatus(id: String, status: String, updatedAt: Long) {
        database.writableDatabase.update(
            "planned_workout",
            ContentValues().apply {
                put("status", status)
                put("updated_at", updatedAt)
            },
            "id = ?",
            arrayOf(id),
        )
    }

    fun deletePlannedWorkout(id: String) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("planned_exercise", "planned_workout_id = ?", arrayOf(id))
            db.delete("planned_workout", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun upsertPlannedExercise(entity: PlannedExerciseEntity) {
        database.writableDatabase.insertWithOnConflict(
            "planned_exercise",
            null,
            ContentValues().apply {
                put("id", entity.id)
                put("planned_workout_id", entity.plannedWorkoutId)
                put("exercise_id", entity.exerciseId)
                put("order_index", entity.orderIndex)
                put("target_sets", entity.targetSets)
                put("target_reps", entity.targetReps)
                put("target_weight_kg", entity.targetWeightKg)
                put("note", entity.note)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun plannedExercises(plannedWorkoutId: String): List<PlannedExerciseEntity> {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, planned_workout_id, exercise_id, order_index, target_sets,
                   target_reps, target_weight_kg, note
            FROM planned_exercise
            WHERE planned_workout_id = ?
            ORDER BY order_index
            """.trimIndent(),
            arrayOf(plannedWorkoutId),
        )
        return try {
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toPlannedExercise())
                }
            }
        } finally {
            cursor.close()
        }
    }

    fun updatePlannedExerciseTarget(
        id: String,
        targetSets: Int,
        targetReps: String,
        targetWeightKg: Double,
        note: String,
    ) {
        database.writableDatabase.update(
            "planned_exercise",
            ContentValues().apply {
                put("target_sets", targetSets)
                put("target_reps", targetReps)
                put("target_weight_kg", targetWeightKg)
                put("note", note)
            },
            "id = ?",
            arrayOf(id),
        )
    }

    fun updatePlannedExerciseExercise(id: String, exerciseId: String, note: String) {
        database.writableDatabase.update(
            "planned_exercise",
            ContentValues().apply {
                put("exercise_id", exerciseId)
                put("note", note)
            },
            "id = ?",
            arrayOf(id),
        )
    }

    fun allPlannedExercises(): List<PlannedExerciseEntity> {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, planned_workout_id, exercise_id, order_index, target_sets,
                   target_reps, target_weight_kg, note
            FROM planned_exercise
            ORDER BY planned_workout_id, order_index
            """.trimIndent(),
            emptyArray(),
        )
        return try {
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toPlannedExercise())
                }
            }
        } finally {
            cursor.close()
        }
    }

    fun upsertWorkoutSession(entity: WorkoutSessionEntity) {
        database.writableDatabase.insertWithOnConflict(
            "workout_session",
            null,
            ContentValues().apply {
                put("id", entity.id)
                put("planned_workout_id", entity.plannedWorkoutId)
                put("venue_id", entity.venueId)
                put("exercise_id", entity.exerciseId)
                put("status", entity.status)
                put("started_at", entity.startedAt)
                put("ended_at", entity.endedAt)
                put("updated_at", entity.updatedAt)
                put("current_exercise_id", entity.currentExerciseId)
                put("rest_ends_at", entity.restEndsAt)
                put("paused_at", entity.pausedAt)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun updateWorkoutSessionStatus(id: String, status: String, endedAt: Long?, updatedAt: Long) {
        database.writableDatabase.update(
            "workout_session",
            ContentValues().apply {
                put("status", status)
                put("ended_at", endedAt)
                put("updated_at", updatedAt)
            },
            "id = ?",
            arrayOf(id),
        )
    }

    fun workoutSession(id: String): WorkoutSessionEntity? {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, planned_workout_id, venue_id, exercise_id, status, started_at, ended_at,
                   updated_at, current_exercise_id, rest_ends_at, paused_at
            FROM workout_session
            WHERE id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(id),
        )
        return try {
            if (cursor.moveToFirst()) cursor.toWorkoutSession() else null
        } finally {
            cursor.close()
        }
    }

    fun upsertSessionExercise(entity: WorkoutSessionExerciseEntity) {
        database.writableDatabase.insertWithOnConflict(
            "workout_session_exercise",
            null,
            ContentValues().apply {
                put("id", entity.id)
                put("session_id", entity.sessionId)
                put("exercise_id", entity.exerciseId)
                put("order_index", entity.orderIndex)
                put("target_sets", entity.targetSets)
                put("target_reps", entity.targetReps)
                put("target_weight_kg", entity.targetWeightKg)
                put("status", entity.status)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun sessionExercises(sessionId: String): List<WorkoutSessionExerciseEntity> {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, session_id, exercise_id, order_index, target_sets, target_reps,
                   target_weight_kg, status
            FROM workout_session_exercise
            WHERE session_id = ?
            ORDER BY order_index
            """.trimIndent(),
            arrayOf(sessionId),
        )
        return try {
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toWorkoutSessionExercise())
                }
            }
        } finally {
            cursor.close()
        }
    }

    fun updateWorkoutRuntime(
        id: String,
        currentExerciseId: String?,
        restEndsAt: Long?,
        pausedAt: Long?,
        updatedAt: Long,
    ) {
        database.writableDatabase.update(
            "workout_session",
            ContentValues().apply {
                put("current_exercise_id", currentExerciseId)
                put("rest_ends_at", restEndsAt)
                put("paused_at", pausedAt)
                put("updated_at", updatedAt)
            },
            "id = ?",
            arrayOf(id),
        )
    }

    fun updateSessionExerciseStatus(id: String, status: String) {
        database.writableDatabase.update(
            "workout_session_exercise",
            ContentValues().apply { put("status", status) },
            "id = ?",
            arrayOf(id),
        )
    }

    fun deleteSessionExercises(sessionId: String) {
        database.writableDatabase.delete(
            "workout_session_exercise",
            "session_id = ?",
            arrayOf(sessionId),
        )
    }

    fun workoutSessions(): List<WorkoutSessionEntity> {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, planned_workout_id, venue_id, exercise_id, status, started_at, ended_at,
                   updated_at, current_exercise_id, rest_ends_at, paused_at
            FROM workout_session
            ORDER BY started_at DESC
            """.trimIndent(),
            emptyArray(),
        )
        return try {
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toWorkoutSession())
                }
            }
        } finally {
            cursor.close()
        }
    }

    fun upsertExerciseMedia(items: List<ExerciseMediaEntity>) {
        if (items.isEmpty()) return

        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val statement = db.compileStatement(
                """
                INSERT OR REPLACE INTO exercise_media (
                    exercise_id, name, body_part, equipment, target, media_id,
                    local_path, asset_pack_id, bytes, sha256
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            )
            items.forEach { statement.bindExerciseMedia(it).executeInsert() }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun exerciseMediaCount(): Int =
        queryInt("SELECT COUNT(*) FROM exercise_media")

    fun exerciseById(id: String): ExerciseMediaEntity? =
        queryExerciseMedia(
            "SELECT * FROM exercise_media WHERE exercise_id = ? LIMIT 1",
            arrayOf(id),
        ).firstOrNull()

    fun exerciseByName(name: String): ExerciseMediaEntity? =
        queryExerciseMedia(
            "SELECT * FROM exercise_media WHERE lower(name) = lower(?) LIMIT 1",
            arrayOf(name),
        ).firstOrNull()

    fun exercisesByEquipment(equipment: String): List<ExerciseMediaEntity> =
        queryExerciseMedia(
            "SELECT * FROM exercise_media WHERE lower(equipment) = lower(?) ORDER BY name",
            arrayOf(equipment),
        )

    fun allExercises(limit: Int = 200): List<ExerciseMediaEntity> =
        queryExerciseMedia(
            "SELECT * FROM exercise_media ORDER BY name LIMIT ?",
            arrayOf(limit.coerceAtLeast(1).toString()),
        )

    fun searchExercises(query: String, limit: Int = 100): List<ExerciseMediaEntity> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return allExercises(limit)
        val like = "%${trimmedQuery.lowercase()}%"
        return queryExerciseMedia(
            """
            SELECT *
            FROM exercise_media
            WHERE lower(name) LIKE ?
               OR lower(body_part) LIKE ?
               OR lower(equipment) LIKE ?
               OR lower(target) LIKE ?
            ORDER BY name
            LIMIT ?
            """.trimIndent(),
            arrayOf(like, like, like, like, limit.coerceAtLeast(1).toString()),
        )
    }

    fun insertSetLog(entity: WorkoutSetLogEntity) {
        database.writableDatabase.insertWithOnConflict(
            "workout_set_log",
            null,
            ContentValues().apply {
                put("id", entity.id)
                put("session_id", entity.sessionId)
                put("exercise_id", entity.exerciseId)
                put("session_exercise_id", entity.sessionExerciseId)
                put("set_index", entity.setIndex)
                put("actual_reps", entity.actualReps)
                put("actual_weight_kg", entity.actualWeightKg)
                put("feeling", entity.feeling)
                put("completed", if (entity.completed) 1 else 0)
                put("completed_at", entity.completedAt)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun completedSetCount(sessionId: String): Int =
        queryInt(
            "SELECT COUNT(*) FROM workout_set_log WHERE session_id = ? AND completed = 1",
            arrayOf(sessionId),
        )

    fun completedSetCount(sessionId: String, exerciseId: String): Int =
        queryInt(
            "SELECT COUNT(*) FROM workout_set_log WHERE session_id = ? AND exercise_id = ? AND completed = 1",
            arrayOf(sessionId, exerciseId),
        )

    fun setLogs(sessionId: String): List<WorkoutSetLogEntity> {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, session_id, exercise_id, session_exercise_id, set_index, actual_reps, actual_weight_kg,
                   feeling, completed, completed_at
            FROM workout_set_log
            WHERE session_id = ?
            ORDER BY set_index
            """.trimIndent(),
            arrayOf(sessionId),
        )
        return try {
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toWorkoutSetLog())
                }
            }
        } finally {
            cursor.close()
        }
    }

    fun setLogs(sessionId: String, exerciseId: String): List<WorkoutSetLogEntity> {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, session_id, exercise_id, session_exercise_id, set_index, actual_reps, actual_weight_kg,
                   feeling, completed, completed_at
            FROM workout_set_log
            WHERE session_id = ? AND exercise_id = ?
            ORDER BY set_index
            """.trimIndent(),
            arrayOf(sessionId, exerciseId),
        )
        return try {
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toWorkoutSetLog())
                }
            }
        } finally {
            cursor.close()
        }
    }

    fun allSetLogs(): List<WorkoutSetLogEntity> {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, session_id, exercise_id, session_exercise_id, set_index, actual_reps, actual_weight_kg,
                   feeling, completed, completed_at
            FROM workout_set_log
            ORDER BY completed_at DESC
            """.trimIndent(),
            emptyArray(),
        )
        return try {
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toWorkoutSetLog())
                }
            }
        } finally {
            cursor.close()
        }
    }

    fun upsertAiProvider(entity: AiProviderEntity) {
        database.writableDatabase.insertWithOnConflict(
            "ai_provider",
            null,
            ContentValues().apply {
                put("id", entity.id)
                put("display_name", entity.displayName)
                put("base_url", entity.baseUrl)
                put("model", entity.model)
                put("enabled", if (entity.enabled) 1 else 0)
                put("api_key_stored", if (entity.apiKeyStored) 1 else 0)
                put("updated_at", entity.updatedAt)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun aiProvider(id: String): AiProviderEntity? {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, display_name, base_url, model, enabled, api_key_stored, updated_at
            FROM ai_provider
            WHERE id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(id),
        )
        return try {
            if (cursor.moveToFirst()) cursor.toAiProvider() else null
        } finally {
            cursor.close()
        }
    }

    fun aiProviders(): List<AiProviderEntity> {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, display_name, base_url, model, enabled, api_key_stored, updated_at
            FROM ai_provider
            ORDER BY rowid
            """.trimIndent(),
            emptyArray(),
        )
        return try {
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toAiProvider())
                }
            }
        } finally {
            cursor.close()
        }
    }

    fun upsertUserProfile(entity: UserProfileEntity) {
        database.writableDatabase.insertWithOnConflict(
            "user_profile",
            null,
            ContentValues().apply {
                put("id", entity.id)
                put("display_name", entity.displayName)
                put("birth_year", entity.birthYear)
                put("height_cm", entity.heightCm)
                put("weight_kg", entity.weightKg)
                put("goal", entity.goal)
                put("injuries", entity.injuries)
                put("weekly_training_days", entity.weeklyTrainingDays)
                put("preferred_minutes", entity.preferredMinutes)
                put("updated_at", entity.updatedAt)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun userProfile(): UserProfileEntity? {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, display_name, birth_year, height_cm, weight_kg, goal, injuries,
                   weekly_training_days, preferred_minutes, updated_at
            FROM user_profile
            ORDER BY updated_at DESC
            LIMIT 1
            """.trimIndent(),
            emptyArray(),
        )
        return try {
            if (cursor.moveToFirst()) cursor.toUserProfile() else null
        } finally {
            cursor.close()
        }
    }

    fun insertFoodLog(entity: FoodLogEntity) {
        database.writableDatabase.insertWithOnConflict(
            "food_log",
            null,
            ContentValues().apply {
                put("id", entity.id)
                put("logged_date", entity.loggedDate)
                put("name", entity.name)
                put("calories", entity.calories)
                put("protein_grams", entity.proteinGrams)
                put("carbs_grams", entity.carbsGrams)
                put("fat_grams", entity.fatGrams)
                put("source", entity.source)
                put("image_note", entity.imageNote)
                put("image_uri", entity.imageUri)
                put("provider_id", entity.providerId)
                put("model", entity.model)
                put("confirmed", if (entity.confirmed) 1 else 0)
                put("created_at", entity.createdAt)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun upsertFoodLog(entity: FoodLogEntity) {
        insertFoodLog(entity)
    }

    fun foodLogs(): List<FoodLogEntity> {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, logged_date, name, calories, protein_grams, carbs_grams,
                   fat_grams, source, image_note, image_uri, provider_id, model,
                   confirmed, created_at
            FROM food_log
            ORDER BY created_at DESC
            """.trimIndent(),
            emptyArray(),
        )
        return try {
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toFoodLog())
                }
            }
        } finally {
            cursor.close()
        }
    }

    fun upsertAiDraft(entity: AiDraftEntity) {
        database.writableDatabase.insertWithOnConflict(
            "ai_draft",
            null,
            ContentValues().apply {
                put("id", entity.id)
                put("type", entity.type)
                put("title", entity.title)
                put("content", entity.content)
                put("status", entity.status)
                put("created_at", entity.createdAt)
                put("updated_at", entity.updatedAt)
                put("metadata_json", entity.metadataJson)
                put("confirmed_at", entity.confirmedAt)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun aiDraft(id: String): AiDraftEntity? {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, type, title, content, status, created_at, updated_at, metadata_json, confirmed_at
            FROM ai_draft
            WHERE id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(id),
        )
        return try {
            if (cursor.moveToFirst()) cursor.toAiDraft() else null
        } finally {
            cursor.close()
        }
    }

    fun aiDrafts(): List<AiDraftEntity> {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, type, title, content, status, created_at, updated_at, metadata_json, confirmed_at
            FROM ai_draft
            ORDER BY created_at DESC
            """.trimIndent(),
            emptyArray(),
        )
        return try {
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toAiDraft())
                }
            }
        } finally {
            cursor.close()
        }
    }

    fun updateAiDraftStatus(id: String, status: String, confirmedAt: Long?, updatedAt: Long) {
        database.writableDatabase.update(
            "ai_draft",
            ContentValues().apply {
                put("status", status)
                put("confirmed_at", confirmedAt)
                put("updated_at", updatedAt)
            },
            "id = ?",
            arrayOf(id),
        )
    }

    fun putPreference(key: String, value: String, updatedAt: Long = System.currentTimeMillis()) {
        database.writableDatabase.insertWithOnConflict(
            "app_preference",
            null,
            ContentValues().apply {
                put("key", key)
                put("value", value)
                put("updated_at", updatedAt)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun preference(key: String): String? {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT value
            FROM app_preference
            WHERE key = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(key),
        )
        return try {
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } finally {
            cursor.close()
        }
    }

    fun preferences(): Map<String, String> {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT key, value
            FROM app_preference
            ORDER BY key
            """.trimIndent(),
            emptyArray(),
        )
        return try {
            buildMap {
                while (cursor.moveToNext()) {
                    put(
                        cursor.getString(cursor.getColumnIndexOrThrow("key")),
                        cursor.getString(cursor.getColumnIndexOrThrow("value")),
                    )
                }
            }
        } finally {
            cursor.close()
        }
    }

    fun upsertTrainingAdjustment(entity: TrainingAdjustmentEntity) {
        database.writableDatabase.insertWithOnConflict(
            "training_adjustment",
            null,
            ContentValues().apply {
                put("id", entity.id)
                put("exercise_id", entity.exerciseId)
                put("title", entity.title)
                put("content", entity.content)
                put("status", entity.status)
                put("created_at", entity.createdAt)
                put("updated_at", entity.updatedAt)
                put("confirmed_at", entity.confirmedAt)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun trainingAdjustments(): List<TrainingAdjustmentEntity> {
        val cursor = database.readableDatabase.rawQuery(
            """
            SELECT id, exercise_id, title, content, status, created_at, updated_at, confirmed_at
            FROM training_adjustment
            ORDER BY created_at DESC
            """.trimIndent(),
            emptyArray(),
        )
        return try {
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toTrainingAdjustment())
                }
            }
        } finally {
            cursor.close()
        }
    }

    fun updateTrainingAdjustmentStatus(id: String, status: String, confirmedAt: Long?, updatedAt: Long) {
        database.writableDatabase.update(
            "training_adjustment",
            ContentValues().apply {
                put("status", status)
                put("confirmed_at", confirmedAt)
                put("updated_at", updatedAt)
            },
            "id = ?",
            arrayOf(id),
        )
    }

    @Synchronized
    fun clearPersonalData() {
        val db = database.writableDatabase
        if (db.inTransaction()) {
            clearPersonalDataTables(db)
        } else {
            transaction { clearPersonalDataTables(db) }
        }
    }

    private fun clearPersonalDataTables(db: SQLiteDatabase) {
        listOf(
            "workout_set_log",
            "workout_session_exercise",
            "workout_session",
            "planned_exercise",
            "planned_workout",
            "food_log",
            "ai_draft",
            "training_adjustment",
            "app_preference",
            "venue_equipment",
            "user_profile",
            "equipment",
            "training_venue",
            "ai_provider",
        ).forEach { table ->
            db.delete(table, null, null)
        }
    }

    private fun queryInt(sql: String, args: Array<String> = emptyArray()): Int {
        val cursor = database.readableDatabase.rawQuery(sql, args)
        return try {
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        } finally {
            cursor.close()
        }
    }

    private fun queryExerciseMedia(sql: String, args: Array<String>): List<ExerciseMediaEntity> {
        val cursor = database.readableDatabase.rawQuery(sql, args)
        return try {
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toExerciseMedia())
                }
            }
        } finally {
            cursor.close()
        }
    }

    private fun SQLiteStatement.bindExerciseMedia(entity: ExerciseMediaEntity): SQLiteStatement {
        clearBindings()
        bindString(1, entity.exerciseId)
        bindString(2, entity.name)
        bindString(3, entity.bodyPart)
        bindString(4, entity.equipment)
        bindString(5, entity.target)
        bindString(6, entity.mediaId)
        bindString(7, entity.localPath)
        bindString(8, entity.assetPackId)
        bindLong(9, entity.bytes)
        bindString(10, entity.sha256)
        return this
    }

    private fun Cursor.toExerciseMedia(): ExerciseMediaEntity =
        ExerciseMediaEntity(
            exerciseId = getString(getColumnIndexOrThrow("exercise_id")),
            name = getString(getColumnIndexOrThrow("name")),
            bodyPart = getString(getColumnIndexOrThrow("body_part")),
            equipment = getString(getColumnIndexOrThrow("equipment")),
            target = getString(getColumnIndexOrThrow("target")),
            mediaId = getString(getColumnIndexOrThrow("media_id")),
            localPath = getString(getColumnIndexOrThrow("local_path")),
            assetPackId = getString(getColumnIndexOrThrow("asset_pack_id")),
            bytes = getLong(getColumnIndexOrThrow("bytes")),
            sha256 = getString(getColumnIndexOrThrow("sha256")),
        )

    private fun Cursor.toTrainingVenue(): TrainingVenueEntity =
        TrainingVenueEntity(
            id = getString(getColumnIndexOrThrow("id")),
            name = getString(getColumnIndexOrThrow("name")),
            isDefault = getInt(getColumnIndexOrThrow("is_default")) == 1,
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
        )

    private fun Cursor.toEquipment(): EquipmentEntity =
        EquipmentEntity(
            id = getString(getColumnIndexOrThrow("id")),
            name = getString(getColumnIndexOrThrow("name")),
            category = getString(getColumnIndexOrThrow("category")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
        )

    private fun Cursor.toVenueEquipment(): VenueEquipmentEntity =
        VenueEquipmentEntity(
            venueId = getString(getColumnIndexOrThrow("venue_id")),
            equipmentId = getString(getColumnIndexOrThrow("equipment_id")),
            available = getInt(getColumnIndexOrThrow("available")) == 1,
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
        )

    private fun Cursor.toPlannedWorkout(): PlannedWorkoutEntity =
        PlannedWorkoutEntity(
            id = getString(getColumnIndexOrThrow("id")),
            name = getString(getColumnIndexOrThrow("name")),
            scheduledDate = getString(getColumnIndexOrThrow("scheduled_date")),
            venueId = getString(getColumnIndexOrThrow("venue_id")),
            status = getString(getColumnIndexOrThrow("status")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
        )

    private fun Cursor.toPlannedExercise(): PlannedExerciseEntity =
        PlannedExerciseEntity(
            id = getString(getColumnIndexOrThrow("id")),
            plannedWorkoutId = getString(getColumnIndexOrThrow("planned_workout_id")),
            exerciseId = getString(getColumnIndexOrThrow("exercise_id")),
            orderIndex = getInt(getColumnIndexOrThrow("order_index")),
            targetSets = getInt(getColumnIndexOrThrow("target_sets")),
            targetReps = getString(getColumnIndexOrThrow("target_reps")),
            targetWeightKg = getDouble(getColumnIndexOrThrow("target_weight_kg")),
            note = getString(getColumnIndexOrThrow("note")),
        )

    private fun Cursor.toWorkoutSession(): WorkoutSessionEntity =
        WorkoutSessionEntity(
            id = getString(getColumnIndexOrThrow("id")),
            plannedWorkoutId = getStringOrNull(getColumnIndexOrThrow("planned_workout_id")),
            venueId = getString(getColumnIndexOrThrow("venue_id")),
            exerciseId = getString(getColumnIndexOrThrow("exercise_id")),
            status = getString(getColumnIndexOrThrow("status")),
            startedAt = getLong(getColumnIndexOrThrow("started_at")),
            endedAt = getLongOrNull(getColumnIndexOrThrow("ended_at")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
            currentExerciseId = getStringOrNull(getColumnIndexOrThrow("current_exercise_id")),
            restEndsAt = getLongOrNull(getColumnIndexOrThrow("rest_ends_at")),
            pausedAt = getLongOrNull(getColumnIndexOrThrow("paused_at")),
        )

    private fun Cursor.toWorkoutSessionExercise(): WorkoutSessionExerciseEntity =
        WorkoutSessionExerciseEntity(
            id = getString(getColumnIndexOrThrow("id")),
            sessionId = getString(getColumnIndexOrThrow("session_id")),
            exerciseId = getString(getColumnIndexOrThrow("exercise_id")),
            orderIndex = getInt(getColumnIndexOrThrow("order_index")),
            targetSets = getInt(getColumnIndexOrThrow("target_sets")),
            targetReps = getString(getColumnIndexOrThrow("target_reps")),
            targetWeightKg = getDouble(getColumnIndexOrThrow("target_weight_kg")),
            status = getString(getColumnIndexOrThrow("status")),
        )

    private fun Cursor.toWorkoutSetLog(): WorkoutSetLogEntity =
        WorkoutSetLogEntity(
            id = getString(getColumnIndexOrThrow("id")),
            sessionId = getString(getColumnIndexOrThrow("session_id")),
            exerciseId = getString(getColumnIndexOrThrow("exercise_id")),
            sessionExerciseId = getStringOrNull(getColumnIndexOrThrow("session_exercise_id")),
            setIndex = getInt(getColumnIndexOrThrow("set_index")),
            actualReps = getInt(getColumnIndexOrThrow("actual_reps")),
            actualWeightKg = getDouble(getColumnIndexOrThrow("actual_weight_kg")),
            feeling = getString(getColumnIndexOrThrow("feeling")),
            completed = getInt(getColumnIndexOrThrow("completed")) == 1,
            completedAt = getLong(getColumnIndexOrThrow("completed_at")),
        )

    private fun Cursor.toAiProvider(): AiProviderEntity =
        AiProviderEntity(
            id = getString(getColumnIndexOrThrow("id")),
            displayName = getString(getColumnIndexOrThrow("display_name")),
            baseUrl = getString(getColumnIndexOrThrow("base_url")),
            model = getString(getColumnIndexOrThrow("model")),
            enabled = getInt(getColumnIndexOrThrow("enabled")) == 1,
            apiKeyStored = getInt(getColumnIndexOrThrow("api_key_stored")) == 1,
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
        )

    private fun Cursor.toUserProfile(): UserProfileEntity =
        UserProfileEntity(
            id = getString(getColumnIndexOrThrow("id")),
            displayName = getString(getColumnIndexOrThrow("display_name")),
            birthYear = getInt(getColumnIndexOrThrow("birth_year")),
            heightCm = getDouble(getColumnIndexOrThrow("height_cm")),
            weightKg = getDouble(getColumnIndexOrThrow("weight_kg")),
            goal = getString(getColumnIndexOrThrow("goal")),
            injuries = getString(getColumnIndexOrThrow("injuries")),
            weeklyTrainingDays = getInt(getColumnIndexOrThrow("weekly_training_days")),
            preferredMinutes = getInt(getColumnIndexOrThrow("preferred_minutes")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
        )

    private fun Cursor.toFoodLog(): FoodLogEntity =
        FoodLogEntity(
            id = getString(getColumnIndexOrThrow("id")),
            loggedDate = getString(getColumnIndexOrThrow("logged_date")),
            name = getString(getColumnIndexOrThrow("name")),
            calories = getInt(getColumnIndexOrThrow("calories")),
            proteinGrams = getDouble(getColumnIndexOrThrow("protein_grams")),
            carbsGrams = getDouble(getColumnIndexOrThrow("carbs_grams")),
            fatGrams = getDouble(getColumnIndexOrThrow("fat_grams")),
            source = getString(getColumnIndexOrThrow("source")),
            imageNote = getString(getColumnIndexOrThrow("image_note")),
            imageUri = getString(getColumnIndexOrThrow("image_uri")),
            providerId = getString(getColumnIndexOrThrow("provider_id")),
            model = getString(getColumnIndexOrThrow("model")),
            confirmed = getInt(getColumnIndexOrThrow("confirmed")) == 1,
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
        )

    private fun Cursor.toAiDraft(): AiDraftEntity =
        AiDraftEntity(
            id = getString(getColumnIndexOrThrow("id")),
            type = getString(getColumnIndexOrThrow("type")),
            title = getString(getColumnIndexOrThrow("title")),
            content = getString(getColumnIndexOrThrow("content")),
            status = getString(getColumnIndexOrThrow("status")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
            metadataJson = getString(getColumnIndexOrThrow("metadata_json")),
            confirmedAt = getLongOrNull(getColumnIndexOrThrow("confirmed_at")),
        )

    private fun Cursor.toTrainingAdjustment(): TrainingAdjustmentEntity =
        TrainingAdjustmentEntity(
            id = getString(getColumnIndexOrThrow("id")),
            exerciseId = getString(getColumnIndexOrThrow("exercise_id")),
            title = getString(getColumnIndexOrThrow("title")),
            content = getString(getColumnIndexOrThrow("content")),
            status = getString(getColumnIndexOrThrow("status")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
            confirmedAt = getLongOrNull(getColumnIndexOrThrow("confirmed_at")),
        )

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun Cursor.getLongOrNull(index: Int): Long? =
        if (isNull(index)) null else getLong(index)
}
