package com.shanqijie.fitnessapp.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shanqijie.fitnessapp.data.UserProfileEntity
import com.shanqijie.fitnessapp.data.BodyMeasurement
import com.shanqijie.fitnessapp.ui.components.FitnessMetricCard
import com.shanqijie.fitnessapp.ui.components.FitnessPageHeader
import com.shanqijie.fitnessapp.ui.components.FitnessPrimaryButton
import com.shanqijie.fitnessapp.ui.components.FitnessSelectionChip
import com.shanqijie.fitnessapp.ui.components.FitnessSurfaceCard
import com.shanqijie.fitnessapp.ui.theme.FitnessColors
import com.shanqijie.fitnessapp.ui.theme.FitnessDimensions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

@Composable
fun ProfileScreen(
    profile: UserProfileEntity?,
    completedWorkouts: Int,
    completedSets: Int,
    totalVolumeKg: Double,
    providerConnected: Boolean,
    onOpenPreferences: () -> Unit,
    onOpenVenue: () -> Unit,
    onOpenSmart: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(FitnessColors.Phone)
            .testTag(ProfileTags.Screen)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        FitnessPageHeader(title = "我的", kicker = "本地训练档案")

        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = profile?.displayName ?: "还没有训练档案",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = profile?.let {
                    "${it.goal} · ${it.heightCm.toCompact()} cm · ${it.weightKg.toCompact()} kg · BMI ${it.bmi().toCompact()}"
                } ?: "在训练偏好中设置目标、身体数据和每周节奏。",
                style = MaterialTheme.typography.bodyLarge,
            )
            profile?.let {
                Text(
                    "每周 ${it.weeklyTrainingDays} 天 · 单次 ${it.preferredMinutes} 分钟",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        profile?.bodyMeasurement?.takeIf { it.hasValues() }?.let { measurement ->
            FitnessSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProfileTags.BodyMeasurementSummary),
            ) {
                Text("最近体测", style = MaterialTheme.typography.headlineSmall)
                measurement.measuredAt.takeIf(String::isNotBlank)?.let { Text("体测日期：$it", style = MaterialTheme.typography.bodyMedium) }
                measurement.bodyType.takeIf(String::isNotBlank)?.let { Text("体型：$it", color = FitnessColors.Ink, fontWeight = FontWeight.Bold) }
                Text(measurement.summaryText(), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("训练数据", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FitnessMetricCard(
                    value = completedWorkouts.toString(),
                    label = "完成训练",
                    modifier = Modifier.weight(1f),
                )
                FitnessMetricCard(
                    value = completedSets.toString(),
                    label = "完成组数",
                    modifier = Modifier.weight(1f),
                )
            }
            FitnessMetricCard(
                value = "${totalVolumeKg.toCompact()} kg",
                label = "累计训练容量",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("设置", style = MaterialTheme.typography.headlineSmall)
            ProfileRow("训练偏好", profile?.goal ?: "完善训练档案", ProfileTags.PreferencesRow, onOpenPreferences)
            ProfileRow("场地与器械", "管理排课条件", ProfileTags.VenueRow, onOpenVenue)
            ProfileRow(
                "智能设置",
                if (providerConnected) "已连接" else "未连接",
                ProfileTags.SmartRow,
                onOpenSmart,
            )
            ProfileRow("数据备份", "导出、恢复与重置", ProfileTags.BackupRow, onOpenBackup)
            ProfileRow("关于", "i fitness 本地优先版", ProfileTags.AboutRow, onOpenAbout)
        }
    }
}

@Composable
private fun ProfileRow(
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag(testTag)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(FitnessDimensions.ContainerRadius),
        colors = CardDefaults.cardColors(containerColor = FitnessColors.Surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(title, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = FitnessColors.Muted)
        }
    }
}

@Composable
fun ProfileEditScreen(
    profile: UserProfileEntity?,
    onSave: suspend (
        displayName: String,
        birthYear: Int,
        heightCm: Double,
        weightKg: Double,
        goal: String,
        injuries: String,
        weeklyTrainingDays: Int,
        preferredMinutes: Int,
        bodyMeasurement: BodyMeasurement,
    ) -> Unit,
    isInitialSetup: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable(profile?.updatedAt) { mutableStateOf(profile?.displayName ?: "") }
    var birthYear by rememberSaveable(profile?.updatedAt) {
        mutableStateOf(profile?.birthYear?.toString() ?: if (isInitialSetup) "" else "1994")
    }
    var height by rememberSaveable(profile?.updatedAt) {
        mutableStateOf(profile?.heightCm?.toCompact() ?: if (isInitialSetup) "" else "176")
    }
    var weight by rememberSaveable(profile?.updatedAt) {
        mutableStateOf(profile?.weightKg?.toCompact() ?: if (isInitialSetup) "" else "75")
    }
    var goal by rememberSaveable(profile?.updatedAt) { mutableStateOf(profile?.goal ?: "保持体能") }
    var injuries by rememberSaveable(profile?.updatedAt) { mutableStateOf(profile?.injuries ?: "") }
    var weeklyDays by rememberSaveable(profile?.updatedAt) { mutableStateOf((profile?.weeklyTrainingDays ?: 3).toString()) }
    var minutes by rememberSaveable(profile?.updatedAt) { mutableStateOf((profile?.preferredMinutes ?: 45).toString()) }
    val savedMeasurement = profile?.bodyMeasurement ?: BodyMeasurement()
    var measuredAt by rememberSaveable(profile?.updatedAt) { mutableStateOf(savedMeasurement.measuredAt) }
    var bodyType by rememberSaveable(profile?.updatedAt) { mutableStateOf(savedMeasurement.bodyType) }
    var bodyFatPercentage by rememberSaveable(profile?.updatedAt) { mutableStateOf(savedMeasurement.bodyFatPercentage.toInput()) }
    var bodyFatMassKg by rememberSaveable(profile?.updatedAt) { mutableStateOf(savedMeasurement.bodyFatMassKg.toInput()) }
    var skeletalMuscleKg by rememberSaveable(profile?.updatedAt) { mutableStateOf(savedMeasurement.skeletalMuscleKg.toInput()) }
    var bodyWaterKg by rememberSaveable(profile?.updatedAt) { mutableStateOf(savedMeasurement.bodyWaterKg.toInput()) }
    var basalMetabolismKcal by rememberSaveable(profile?.updatedAt) { mutableStateOf(savedMeasurement.basalMetabolismKcal?.toString().orEmpty()) }
    var waistHipRatio by rememberSaveable(profile?.updatedAt) { mutableStateOf(savedMeasurement.waistHipRatio.toInput()) }
    var bodyAge by rememberSaveable(profile?.updatedAt) { mutableStateOf(savedMeasurement.bodyAge?.toString().orEmpty()) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(FitnessColors.Phone)
            .testTag(ProfileTags.Edit)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FitnessPageHeader(
            title = if (isInitialSetup) "先完成训练设置" else "训练偏好",
            kicker = if (isInitialSetup) "只保存在这台设备，用于生成可调整的首周计划" else "只在此页编辑",
        )
        ProfileField(name, { name = it }, "昵称", ProfileTags.Name)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileField(birthYear, { birthYear = it }, "出生年", ProfileTags.BirthYear, Modifier.weight(1f))
            ProfileField(height, { height = it }, "身高 cm", ProfileTags.Height, Modifier.weight(1f))
        }
        ProfileField(weight, { weight = it }, "体重 kg", ProfileTags.Weight)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ProfileTags.Goal),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("训练目标", style = MaterialTheme.typography.bodyMedium)
            Text("用于调整本地训练与饮食参考，可随时修改。", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileGoalOptions.forEach { option ->
                    FitnessSelectionChip(
                        label = option,
                        selected = goal == option,
                        onClick = { goal = option },
                    )
                }
            }
        }
        ProfileField(injuries, { injuries = it }, "伤病 / 注意事项", ProfileTags.Injuries)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("体测数据（可选）", style = MaterialTheme.typography.headlineSmall)
            Text("可按体测报告填写。BMI 会按身高和体重自动计算；其余数据只保存在本机，并作为 AI 训练建议的参考。", style = MaterialTheme.typography.bodyMedium)
            ProfileField(measuredAt, { measuredAt = it }, "体测日期（YYYY-MM-DD）", ProfileTags.MeasuredAt)
            ProfileField(bodyType, { bodyType = it }, "体型（如：偏胖型）", ProfileTags.BodyType)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileField(bodyFatPercentage, { bodyFatPercentage = it }, "体脂率 %", ProfileTags.BodyFatPercentage, Modifier.weight(1f))
                ProfileField(bodyFatMassKg, { bodyFatMassKg = it }, "体脂肪 kg", ProfileTags.BodyFatMass, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileField(skeletalMuscleKg, { skeletalMuscleKg = it }, "骨骼肌 kg", ProfileTags.SkeletalMuscle, Modifier.weight(1f))
                ProfileField(bodyWaterKg, { bodyWaterKg = it }, "身体水分 kg", ProfileTags.BodyWater, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileField(basalMetabolismKcal, { basalMetabolismKcal = it }, "基础代谢 kcal", ProfileTags.BasalMetabolism, Modifier.weight(1f))
                ProfileField(waistHipRatio, { waistHipRatio = it }, "腰臀比", ProfileTags.WaistHipRatio, Modifier.weight(1f))
            }
            ProfileField(bodyAge, { bodyAge = it }, "身体年龄", ProfileTags.BodyAge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileField(weeklyDays, { weeklyDays = it }, "每周天数", ProfileTags.WeeklyDays, Modifier.weight(1f))
            ProfileField(minutes, { minutes = it }, "单次分钟", ProfileTags.Minutes, Modifier.weight(1f))
        }
        errorMessage?.let { ProfileError(it) }
        FitnessPrimaryButton(
            text = if (saving) "保存中…" else if (isInitialSetup) "保存并开始制定计划" else "保存训练偏好",
            enabled = !saving,
            testTag = ProfileTags.Save,
            onClick = {
                val parsedBirthYear = birthYear.toIntOrNull()
                val parsedHeight = height.toDoubleOrNull()
                val parsedWeight = weight.toDoubleOrNull()
                val parsedWeeklyDays = weeklyDays.toIntOrNull()
                val parsedMinutes = minutes.toIntOrNull()
                val parsedBodyFatPercentage = bodyFatPercentage.toDoubleOrNull()
                val parsedBodyFatMassKg = bodyFatMassKg.toDoubleOrNull()
                val parsedSkeletalMuscleKg = skeletalMuscleKg.toDoubleOrNull()
                val parsedBodyWaterKg = bodyWaterKg.toDoubleOrNull()
                val parsedBasalMetabolismKcal = basalMetabolismKcal.toIntOrNull()
                val parsedWaistHipRatio = waistHipRatio.toDoubleOrNull()
                val parsedBodyAge = bodyAge.toIntOrNull()
                errorMessage = when {
                    parsedBirthYear == null || parsedBirthYear !in 1940..LocalDate.now().year -> "请输入合理的出生年份"
                    parsedHeight == null || parsedHeight !in 80.0..240.0 -> "请输入 80 到 240 cm 之间的身高"
                    parsedWeight == null || parsedWeight !in 25.0..250.0 -> "请输入 25 到 250 kg 之间的体重"
                    parsedWeeklyDays == null || parsedWeeklyDays !in 1..7 -> "每周训练天数需要在 1 到 7 之间"
                    parsedMinutes == null || parsedMinutes !in 15..180 -> "单次时长需要在 15 到 180 分钟之间"
                    measuredAt.isNotBlank() && runCatching { LocalDate.parse(measuredAt) }.isFailure -> "体测日期格式应为 YYYY-MM-DD"
                    bodyFatPercentage.isNotBlank() && parsedBodyFatPercentage == null -> "体脂率请输入数字"
                    bodyFatMassKg.isNotBlank() && parsedBodyFatMassKg == null -> "体脂肪请输入数字"
                    skeletalMuscleKg.isNotBlank() && parsedSkeletalMuscleKg == null -> "骨骼肌请输入数字"
                    bodyWaterKg.isNotBlank() && parsedBodyWaterKg == null -> "身体水分请输入数字"
                    basalMetabolismKcal.isNotBlank() && parsedBasalMetabolismKcal == null -> "基础代谢请输入整数"
                    waistHipRatio.isNotBlank() && parsedWaistHipRatio == null -> "腰臀比请输入数字"
                    bodyAge.isNotBlank() && parsedBodyAge == null -> "身体年龄请输入整数"
                    else -> null
                }
                if (errorMessage == null && !saving) {
                    saving = true
                    coroutineScope.launch {
                        try {
                            onSave(
                                name,
                                requireNotNull(parsedBirthYear),
                                requireNotNull(parsedHeight),
                                requireNotNull(parsedWeight),
                                goal,
                                injuries,
                                requireNotNull(parsedWeeklyDays),
                                requireNotNull(parsedMinutes),
                                BodyMeasurement(
                                    measuredAt = measuredAt,
                                    bodyType = bodyType,
                                    bodyFatPercentage = parsedBodyFatPercentage,
                                    bodyFatMassKg = parsedBodyFatMassKg,
                                    skeletalMuscleKg = parsedSkeletalMuscleKg,
                                    bodyWaterKg = parsedBodyWaterKg,
                                    basalMetabolismKcal = parsedBasalMetabolismKcal,
                                    waistHipRatio = parsedWaistHipRatio,
                                    bodyAge = parsedBodyAge,
                                ),
                            )
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Exception) {
                            errorMessage = error.message ?: "保存档案失败"
                        } finally {
                            saving = false
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.testTag(testTag),
    )
}

@Composable
private fun ProfileError(message: String) {
    Text(
        text = message,
        color = ProfileErrorText,
        modifier = Modifier
            .fillMaxWidth()
            .background(ProfileErrorContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

object ProfileTags {
    const val Screen = "profile-screen"
    const val PreferencesRow = "profile-row-preferences"
    const val VenueRow = "profile-row-venue"
    const val SmartRow = "profile-row-smart"
    const val BackupRow = "profile-row-backup"
    const val AboutRow = "profile-row-about"
    const val Edit = "profile-edit"
    const val Name = "profile-name"
    const val BirthYear = "profile-birth-year"
    const val Height = "profile-height"
    const val Weight = "profile-weight"
    const val Goal = "profile-goal"
    const val Injuries = "profile-injuries"
    const val WeeklyDays = "profile-weekly-days"
    const val Minutes = "profile-minutes"
    const val MeasuredAt = "profile-measured-at"
    const val BodyType = "profile-body-type"
    const val BodyFatPercentage = "profile-body-fat-percentage"
    const val BodyFatMass = "profile-body-fat-mass"
    const val SkeletalMuscle = "profile-skeletal-muscle"
    const val BodyWater = "profile-body-water"
    const val BasalMetabolism = "profile-basal-metabolism"
    const val WaistHipRatio = "profile-waist-hip-ratio"
    const val BodyAge = "profile-body-age"
    const val BodyMeasurementSummary = "profile-body-measurement-summary"
    const val Save = "save-profile"
}

private fun Double.toCompact(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.ROOT, "%.1f", this)

private fun Double?.toInput(): String = this?.toCompact().orEmpty()

private fun BodyMeasurement.hasValues(): Boolean =
    measuredAt.isNotBlank() || bodyType.isNotBlank() || bodyFatPercentage != null || bodyFatMassKg != null ||
        skeletalMuscleKg != null || bodyWaterKg != null || basalMetabolismKcal != null || waistHipRatio != null || bodyAge != null

private fun BodyMeasurement.summaryText(): String = buildList {
    bodyFatPercentage?.let { add("体脂率 ${it.toCompact()}%") }
    bodyFatMassKg?.let { add("体脂肪 ${it.toCompact()} kg") }
    skeletalMuscleKg?.let { add("骨骼肌 ${it.toCompact()} kg") }
    bodyWaterKg?.let { add("身体水分 ${it.toCompact()} kg") }
    basalMetabolismKcal?.let { add("基础代谢 $it kcal") }
    waistHipRatio?.let { add("腰臀比 ${it.toCompact()}") }
    bodyAge?.let { add("身体年龄 $it 岁") }
}.joinToString(" · ")

private fun UserProfileEntity.bmi(): Double = weightKg / ((heightCm / 100.0) * (heightCm / 100.0))

private val ProfileErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6)
private val ProfileErrorText = androidx.compose.ui.graphics.Color(0xFF690005)
private val ProfileGoalOptions = listOf("减脂", "保持体能", "增肌")
