package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.domain.WorkoutAdjustmentDirection
import com.shanqijie.fitnessapp.domain.WorkoutReviewSignals
import com.shanqijie.fitnessapp.domain.decideWorkoutAdjustment
import com.shanqijie.fitnessapp.domain.toJson
import com.shanqijie.fitnessapp.domain.workoutReviewMetadata
import com.shanqijie.fitnessapp.domain.WorkoutReviewMetadata
import com.shanqijie.fitnessapp.data.AiDraftEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutReviewPolicyTest {
    @Test
    fun increasesOnlyAfterCompleteEasyWorkoutAndGoodRecoveryFeedback() {
        assertEquals(
            WorkoutAdjustmentDirection.INCREASE,
            decideWorkoutAdjustment(
                WorkoutReviewSignals(8, 8, mapOf("轻松" to 5, "合适" to 3), "状态很好"),
            ),
        )
    }

    @Test
    fun reducesAfterLowCompletionOrExcessiveFatigue() {
        assertEquals(
            WorkoutAdjustmentDirection.REDUCE,
            decideWorkoutAdjustment(
                WorkoutReviewSignals(4, 8, mapOf("合适" to 4), "正常疲劳"),
            ),
        )
        assertEquals(
            WorkoutAdjustmentDirection.REDUCE,
            decideWorkoutAdjustment(
                WorkoutReviewSignals(8, 8, mapOf("合适" to 8), "疼痛不适"),
            ),
        )
        assertEquals(
            WorkoutAdjustmentDirection.REDUCE,
            decideWorkoutAdjustment(
                WorkoutReviewSignals(8, 8, mapOf("吃力" to 5, "合适" to 3), "正常疲劳"),
            ),
        )
        assertEquals(
            WorkoutAdjustmentDirection.REDUCE,
            decideWorkoutAdjustment(
                WorkoutReviewSignals(8, 8, mapOf("合适" to 8), "非常疲劳"),
            ),
        )
    }

    @Test
    fun maintainsWhenSignalsDoNotJustifyAChange() {
        assertEquals(
            WorkoutAdjustmentDirection.MAINTAIN,
            decideWorkoutAdjustment(
                WorkoutReviewSignals(8, 8, mapOf("合适" to 8), "正常疲劳"),
            ),
        )
        assertEquals(
            WorkoutAdjustmentDirection.MAINTAIN,
            decideWorkoutAdjustment(
                WorkoutReviewSignals(1, 0, mapOf("很轻松" to 0, "很吃力" to 0), "状态很好"),
            ),
        )
        assertEquals(
            WorkoutAdjustmentDirection.MAINTAIN,
            decideWorkoutAdjustment(
                WorkoutReviewSignals(8, 8, mapOf("轻松" to 3, "合适" to 5), "状态很好"),
            ),
        )
        assertEquals(
            WorkoutAdjustmentDirection.MAINTAIN,
            decideWorkoutAdjustment(
                WorkoutReviewSignals(9, 10, mapOf("轻松" to 9), "状态很好"),
            ),
        )
    }

    @Test
    fun reviewMetadataRoundTripsAndRejectsWrongOrDamagedDrafts() {
        val metadata = WorkoutReviewMetadata(
            sessionId = "session-1",
            direction = WorkoutAdjustmentDirection.MAINTAIN.name,
            postWorkoutFeeling = "正常疲劳",
            postWorkoutNote = "",
            exerciseIds = listOf("0748"),
        )
        fun draft(type: String, json: String) = AiDraftEntity(
            id = "draft-1",
            type = type,
            title = "复盘",
            content = "内容",
            status = "draft",
            createdAt = 1L,
            updatedAt = 1L,
            metadataJson = json,
            confirmedAt = null,
        )

        assertEquals(metadata, draft("workout_review", metadata.toJson()).workoutReviewMetadata())
        assertNull(draft("weekly_plan", metadata.toJson()).workoutReviewMetadata())
        assertNull(draft("workout_review", "").workoutReviewMetadata())
        assertNull(draft("workout_review", "{broken").workoutReviewMetadata())
    }
}
