package com.shanqijie.fitnessapp.domain

data class WorkoutSetInput(
    val reps: Int,
    val weightKg: Double,
    val feeling: String,
)

data class WorkoutSetRecord(
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val feeling: String,
)

class WorkoutRecorder(
    private val targetSets: Int,
    initialRecords: List<WorkoutSetRecord> = emptyList(),
) {
    private val records = initialRecords.sortedBy { it.setIndex }.toMutableList()

    val completedSets: Int get() = records.size
    val isComplete: Boolean get() = completedSets >= targetSets
    fun records(): List<WorkoutSetRecord> = records.toList()

    fun completeNextSet(input: WorkoutSetInput): WorkoutSetRecord {
        require(input.reps > 0) { "次数必须大于 0" }
        require(input.weightKg >= 0.0) { "重量不能为负数" }
        check(!isComplete) { "目标组数已全部完成" }

        val record = WorkoutSetRecord(
            setIndex = records.size + 1,
            reps = input.reps,
            weightKg = input.weightKg,
            feeling = input.feeling,
        )
        records += record
        return record
    }
}
