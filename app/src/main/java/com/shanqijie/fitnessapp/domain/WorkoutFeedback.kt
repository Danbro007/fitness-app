package com.shanqijie.fitnessapp.domain

import kotlinx.serialization.Serializable

@Serializable
enum class WorkoutEarlyFinishReason(val label: String) {
    TIME_LIMIT("时间不够"),
    TOO_HEAVY("重量过重"),
    DISCOMFORT("身体不适"),
    EQUIPMENT_UNAVAILABLE("器械不可用"),
}

@Serializable
enum class EquipmentAvailabilityScope(val label: String) {
    TEMPORARY("本次临时不可用"),
    PERSISTENT("以后长期不可用"),
}

@Serializable
data class WorkoutFeedback(
    val sessionId: String,
    val reason: WorkoutEarlyFinishReason?,
    val note: String,
    val equipmentScope: EquipmentAvailabilityScope? = null,
    val createdAt: Long,
)

data class WorkoutFeedbackDecision(
    val requiresInjuryReview: Boolean,
    val disableEquipmentForVenue: Boolean,
    val message: String,
)

fun validateWorkoutFeedback(feedback: WorkoutFeedback) {
    require(feedback.sessionId.isNotBlank()) { "训练记录不能为空" }
    require(feedback.note.length <= 300) { "训练备注不能超过 300 字" }
    if (feedback.reason == WorkoutEarlyFinishReason.EQUIPMENT_UNAVAILABLE) {
        require(feedback.equipmentScope != null) { "请选择器械不可用的范围" }
    } else {
        require(feedback.equipmentScope == null) { "只有器械不可用时需要选择范围" }
    }
}

fun decideWorkoutFeedback(feedback: WorkoutFeedback): WorkoutFeedbackDecision {
    validateWorkoutFeedback(feedback)
    return when (feedback.reason) {
        WorkoutEarlyFinishReason.DISCOMFORT -> WorkoutFeedbackDecision(
            requiresInjuryReview = true,
            disableEquipmentForVenue = false,
            message = "已保留未完成状态；请先完成伤病复核，期间不自动增加训练负荷。",
        )
        WorkoutEarlyFinishReason.EQUIPMENT_UNAVAILABLE -> WorkoutFeedbackDecision(
            requiresInjuryReview = false,
            disableEquipmentForVenue = feedback.equipmentScope == EquipmentAvailabilityScope.PERSISTENT,
            message = if (feedback.equipmentScope == EquipmentAvailabilityScope.PERSISTENT) {
                "已记录为长期不可用，并从该场地的可用器械中排除。"
            } else {
                "已记录为本次临时不可用，下次生成计划时仍会保留该器械。"
            },
        )
        WorkoutEarlyFinishReason.TOO_HEAVY -> WorkoutFeedbackDecision(
            requiresInjuryReview = false,
            disableEquipmentForVenue = false,
            message = "已保留未完成状态；后续建议先降低重量，不静默改写已经开始的目标。",
        )
        WorkoutEarlyFinishReason.TIME_LIMIT, null -> WorkoutFeedbackDecision(
            requiresInjuryReview = false,
            disableEquipmentForVenue = false,
            message = "已保留本次实际完成数据，后续计划会继续参考该记录。",
        )
    }
}
