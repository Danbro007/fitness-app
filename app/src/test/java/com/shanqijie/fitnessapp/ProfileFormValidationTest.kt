package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.ui.profile.ProfileFormInput
import com.shanqijie.fitnessapp.ui.profile.validateProfileForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileFormValidationTest {
    private val valid = ProfileFormInput(
        name = "山崎",
        birthYear = "1994",
        height = "176.5",
        weight = "76.5",
        weeklyDays = "3",
        minutes = "45",
        measuredAt = "",
        bodyFatPercentage = "",
        bodyFatMassKg = "",
        skeletalMuscleKg = "",
        bodyWaterKg = "",
        basalMetabolismKcal = "",
        waistHipRatio = "",
        bmi = "",
    )

    @Test
    fun acceptsBoundaryAndCompleteOptionalMeasurements() {
        listOf(
            valid.copy(birthYear = "1940", height = "80", weight = "25", weeklyDays = "1", minutes = "15"),
            valid.copy(birthYear = "2026", height = "240", weight = "250", weeklyDays = "7", minutes = "180"),
            valid.copy(
                measuredAt = "2026-07-14",
                bodyFatPercentage = "15.5",
                bodyFatMassKg = "11.5",
                skeletalMuscleKg = "32",
                bodyWaterKg = "42.5",
                basalMetabolismKcal = "1650",
                waistHipRatio = "0.85",
                bmi = "22.1",
            ),
        ).forEach { input ->
            val result = validateProfileForm(input, currentYear = 2026)
            assertNull(result.errorMessage)
            assertNotNull(result.parsed)
        }
    }

    @Test
    fun rejectsEveryRequiredAndOptionalInputShape() {
        val cases = listOf(
            valid.copy(name = "   ") to "请输入昵称",
            valid.copy(birthYear = "x") to "请输入合理的出生年份",
            valid.copy(birthYear = "1939") to "请输入合理的出生年份",
            valid.copy(birthYear = "2027") to "请输入合理的出生年份",
            valid.copy(height = "x") to "请输入 80 到 240 cm 之间的身高",
            valid.copy(height = "79.9") to "请输入 80 到 240 cm 之间的身高",
            valid.copy(height = "240.1") to "请输入 80 到 240 cm 之间的身高",
            valid.copy(weight = "x") to "请输入 25 到 250 kg 之间的体重",
            valid.copy(weight = "24.9") to "请输入 25 到 250 kg 之间的体重",
            valid.copy(weight = "250.1") to "请输入 25 到 250 kg 之间的体重",
            valid.copy(weeklyDays = "x") to "每周训练天数需要在 1 到 7 之间",
            valid.copy(weeklyDays = "0") to "每周训练天数需要在 1 到 7 之间",
            valid.copy(weeklyDays = "8") to "每周训练天数需要在 1 到 7 之间",
            valid.copy(minutes = "x") to "单次时长需要在 15 到 180 分钟之间",
            valid.copy(minutes = "14") to "单次时长需要在 15 到 180 分钟之间",
            valid.copy(minutes = "181") to "单次时长需要在 15 到 180 分钟之间",
            valid.copy(measuredAt = "not-a-date") to "体测日期格式应为 YYYY-MM-DD",
            valid.copy(bodyFatPercentage = "x") to "体脂率请输入数字",
            valid.copy(bodyFatMassKg = "x") to "体脂肪请输入数字",
            valid.copy(skeletalMuscleKg = "x") to "骨骼肌请输入数字",
            valid.copy(bodyWaterKg = "x") to "身体水分请输入数字",
            valid.copy(basalMetabolismKcal = "1.5") to "基础代谢请输入整数",
            valid.copy(waistHipRatio = "x") to "腰臀比请输入数字",
            valid.copy(bmi = "x") to "BMI 需要在 10 到 80 之间",
            valid.copy(bmi = "9.9") to "BMI 需要在 10 到 80 之间",
            valid.copy(bmi = "80.1") to "BMI 需要在 10 到 80 之间",
        )

        cases.forEach { (input, message) ->
            val result = validateProfileForm(input, currentYear = 2026)
            assertEquals(message, result.errorMessage)
            assertNull(result.parsed)
        }
    }
}
