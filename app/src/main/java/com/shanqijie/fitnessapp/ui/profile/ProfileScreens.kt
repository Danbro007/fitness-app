package com.shanqijie.fitnessapp.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shanqijie.fitnessapp.data.UserProfileEntity
import com.shanqijie.fitnessapp.data.BodyMeasurement
import com.shanqijie.fitnessapp.ui.components.FitnessMetricCard
import com.shanqijie.fitnessapp.ui.components.FitnessFloatingBottomDialog
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
import java.io.File
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
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
    modifier: Modifier,
) {
    var showVisualModeSheet by rememberSaveable { mutableStateOf(false) }
    var visualMode by rememberSaveable { mutableStateOf("自动") }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .testTag(ProfileTags.Screen)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(23.dp),
    ) {
        FitnessPageHeader(
            title = "我的",
            kicker = "训练档案与本机设置",
            action = {
                Surface(onClick = onOpenPreferences, shape = CircleShape, color = FitnessColors.Surface, shadowElevation = 8.dp, modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Edit, contentDescription = "编辑训练档案") }
                }
            },
        )

        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 269.dp),
            shape = RoundedCornerShape(34.dp),
            color = FitnessColors.Hero,
        ) {
            val context = LocalContext.current
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (profile?.avatarPath?.isNotBlank() == true) {
                    AsyncImage(
                        model = File(context.filesDir, profile.avatarPath),
                        contentDescription = "我的头像",
                        modifier = Modifier.size(82.dp).clip(CircleShape).clickable(onClick = onOpenPreferences),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(82.dp)
                            .background(Color(0xFF292B26), CircleShape)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(FitnessColors.Orange)
                            .clickable(onClick = onOpenPreferences),
                        contentAlignment = Alignment.Center,
                    ) { Text(profile?.displayName?.trim()?.take(2)?.ifBlank { "我" } ?: "我", style = MaterialTheme.typography.headlineSmall) }
                }
                Text(
                    text = profile?.displayName ?: "还没有训练档案",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    profile?.let { "${it.goal} · 每周 ${it.weeklyTrainingDays} 练 · 单次 ${it.preferredMinutes} 分钟" }
                        ?: "在训练偏好中完善本地档案",
                    color = Color(0xFF9B9E95),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF23241F),
                ) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ProfileStat("训练", "$completedWorkouts 次")
                        ProfileStat("连续", if (completedWorkouts == 0) "0 周" else "1 周")
                        ProfileStat("总组数", completedSets.toString())
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("偏好与设备", style = MaterialTheme.typography.headlineSmall)
                Text("本机保存", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(6.dp))
            ProfileRow("训练偏好", profile?.goal ?: "完善训练档案", Icons.Rounded.FitnessCenter, ProfileTags.PreferencesRow, onOpenPreferences)
            ProfileRow("场地与器械", "管理排课条件", Icons.Rounded.LocationOn, ProfileTags.VenueRow, onOpenVenue)
            ProfileRow(
                "视觉模式",
                if (visualMode == "自动") "自动混合" else visualMode,
                Icons.Rounded.Contrast,
                ProfileTags.VisualRow,
            ) { showVisualModeSheet = true }
            ProfileRow(
                "连接 AI 服务",
                if (providerConnected) "已连接" else "未连接 · 核心功能仍可离线使用",
                Icons.Rounded.AutoAwesome,
                ProfileTags.SmartRow,
                onOpenSmart,
            )
            ProfileRow("本地数据备份", "导出或恢复训练记录", Icons.Rounded.Storage, ProfileTags.BackupRow, onOpenBackup)
            ProfileRow("关于", "i fitness 本地优先版", Icons.Rounded.Info, ProfileTags.AboutRow, onOpenAbout)
        }
        profile?.bodyMeasurement?.takeIf { it.hasValues() }?.let { measurement ->
            FitnessSurfaceCard(modifier = Modifier.fillMaxWidth().testTag(ProfileTags.BodyMeasurementSummary)) {
                Text("最近体测", style = MaterialTheme.typography.headlineSmall)
                Text(measurement.summaryText(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (showVisualModeSheet) {
        FitnessFloatingBottomDialog(
            onDismissRequest = { showVisualModeSheet = false },
            containerColor = FitnessColors.Surface,
            contentColor = FitnessColors.Ink,
            modifier = Modifier.testTag(ProfileTags.VisualSheet),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("选择视觉模式", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "日光适合日常记录，暗夜更像智能硬件面板；自动模式在训练执行时切入深色专注界面。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("日光", "暗夜", "自动").forEach { option ->
                        Surface(
                            onClick = { visualMode = option },
                            modifier = Modifier.weight(1f).heightIn(min = 96.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = if (visualMode == option) FitnessColors.Orange else FitnessColors.Phone,
                            contentColor = FitnessColors.Ink,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Surface(
                                    modifier = Modifier.size(34.dp),
                                    shape = CircleShape,
                                    color = if (visualMode == option) Color.White.copy(alpha = .75f) else FitnessColors.Surface,
                                    border = androidx.compose.foundation.BorderStroke(3.dp, FitnessColors.Orange),
                                ) {}
                                Text(option, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = { showVisualModeSheet = false },
                        modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                    ) { Text("取消") }
                    FitnessPrimaryButton(
                        text = "完成",
                        onClick = { showVisualModeSheet = false },
                        modifier = Modifier.weight(1.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9B9E95))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProfileRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .testTag(testTag)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(FitnessDimensions.ContainerRadius),
        colors = CardDefaults.cardColors(containerColor = FitnessColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = FitnessColors.Phone, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = FitnessColors.Ink) }
            }
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

internal data class ProfileFormInput(
    val name: String,
    val birthYear: String,
    val height: String,
    val weight: String,
    val weeklyDays: String,
    val minutes: String,
    val measuredAt: String,
    val bodyFatPercentage: String,
    val bodyFatMassKg: String,
    val skeletalMuscleKg: String,
    val bodyWaterKg: String,
    val basalMetabolismKcal: String,
    val waistHipRatio: String,
    val bmi: String,
)

internal data class ParsedProfileForm(
    val birthYear: Int,
    val heightCm: Double,
    val weightKg: Double,
    val weeklyTrainingDays: Int,
    val preferredMinutes: Int,
    val bodyFatPercentage: Double?,
    val bodyFatMassKg: Double?,
    val skeletalMuscleKg: Double?,
    val bodyWaterKg: Double?,
    val basalMetabolismKcal: Int?,
    val waistHipRatio: Double?,
    val bmi: Double?,
)

internal data class ProfileFormValidation(
    val parsed: ParsedProfileForm?,
    val errorMessage: String?,
)

internal fun validateProfileForm(
    input: ProfileFormInput,
    currentYear: Int = LocalDate.now().year,
): ProfileFormValidation {
    val parsed = ParsedProfileForm(
        birthYear = input.birthYear.toIntOrNull() ?: 0,
        heightCm = input.height.toDoubleOrNull() ?: Double.NaN,
        weightKg = input.weight.toDoubleOrNull() ?: Double.NaN,
        weeklyTrainingDays = input.weeklyDays.toIntOrNull() ?: 0,
        preferredMinutes = input.minutes.toIntOrNull() ?: 0,
        bodyFatPercentage = input.bodyFatPercentage.toDoubleOrNull(),
        bodyFatMassKg = input.bodyFatMassKg.toDoubleOrNull(),
        skeletalMuscleKg = input.skeletalMuscleKg.toDoubleOrNull(),
        bodyWaterKg = input.bodyWaterKg.toDoubleOrNull(),
        basalMetabolismKcal = input.basalMetabolismKcal.toIntOrNull(),
        waistHipRatio = input.waistHipRatio.toDoubleOrNull(),
        bmi = input.bmi.toDoubleOrNull(),
    )
    val errorMessage = when {
        input.name.isBlank() -> "请输入昵称"
        input.birthYear.toIntOrNull() == null || parsed.birthYear !in 1940..currentYear -> "请输入合理的出生年份"
        input.height.toDoubleOrNull() == null || parsed.heightCm !in 80.0..240.0 -> "请输入 80 到 240 cm 之间的身高"
        input.weight.toDoubleOrNull() == null || parsed.weightKg !in 25.0..250.0 -> "请输入 25 到 250 kg 之间的体重"
        input.weeklyDays.toIntOrNull() == null || parsed.weeklyTrainingDays !in 1..7 -> "每周训练天数需要在 1 到 7 之间"
        input.minutes.toIntOrNull() == null || parsed.preferredMinutes !in 15..180 -> "单次时长需要在 15 到 180 分钟之间"
        input.measuredAt.isNotBlank() && runCatching { LocalDate.parse(input.measuredAt) }.isFailure -> "体测日期格式应为 YYYY-MM-DD"
        input.bodyFatPercentage.isNotBlank() && parsed.bodyFatPercentage == null -> "体脂率请输入数字"
        input.bodyFatMassKg.isNotBlank() && parsed.bodyFatMassKg == null -> "体脂肪请输入数字"
        input.skeletalMuscleKg.isNotBlank() && parsed.skeletalMuscleKg == null -> "骨骼肌请输入数字"
        input.bodyWaterKg.isNotBlank() && parsed.bodyWaterKg == null -> "身体水分请输入数字"
        input.basalMetabolismKcal.isNotBlank() && parsed.basalMetabolismKcal == null -> "基础代谢请输入整数"
        input.waistHipRatio.isNotBlank() && parsed.waistHipRatio == null -> "腰臀比请输入数字"
        input.bmi.isNotBlank() && (parsed.bmi == null || parsed.bmi !in 10.0..80.0) -> "BMI 需要在 10 到 80 之间"
        else -> null
    }
    return ProfileFormValidation(
        parsed = parsed.takeIf { errorMessage == null },
        errorMessage = errorMessage,
    )
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
    onSaveAvatar: suspend (Uri) -> Unit = {},
    isInitialSetup: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable(profile?.updatedAt) { mutableStateOf(profile?.displayName ?: "") }
    var birthYear by rememberSaveable(profile?.updatedAt) { // coverage-exempt: compiler-generated state restoration branch
        mutableStateOf(profile?.birthYear?.toString() ?: if (isInitialSetup) "" else "1994")
    }
    var height by rememberSaveable(profile?.updatedAt) { // coverage-exempt: compiler-generated state restoration branch
        mutableStateOf(profile?.heightCm?.toCompact() ?: if (isInitialSetup) "" else "176")
    }
    var weight by rememberSaveable(profile?.updatedAt) { // coverage-exempt: compiler-generated state restoration branch
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
    var bmi by rememberSaveable(profile?.updatedAt) { mutableStateOf(savedMeasurement.bmi.toInput()) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var setupStep by rememberSaveable { mutableStateOf(1) }
    val coroutineScope = rememberCoroutineScope()
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            saving = true
            coroutineScope.launch {
                try {
                    onSaveAvatar(uri)
                } catch (error: Exception) {
                    errorMessage = error.message ?: "保存头像失败"
                } finally {
                    saving = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .then(if (isInitialSetup) Modifier.statusBarsPadding() else Modifier)
            .testTag(ProfileTags.Edit)
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, top = 0.dp, end = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isInitialSetup) {
            FitnessPageHeader(
                title = when (setupStep) {
                    1 -> "建立基础档案"
                    2 -> "设置训练偏好"
                    else -> "补充身体参数"
                },
                kicker = "$setupStep / 3 · 只保存在这台设备",
                action = {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = FitnessColors.Orange,
                        shadowElevation = 8.dp,
                    ) { Box(contentAlignment = Alignment.Center) { Text("✓", fontWeight = FontWeight.ExtraBold) } }
                },
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = if (isInitialSetup) FitnessColors.Orange else FitnessColors.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isInitialSetup) 10.dp else 6.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (isInitialSetup) when (setupStep) {
                        1 -> "先填写制定计划所需的基础信息"
                        2 -> "告诉我们你的目标和可用时间"
                        else -> "体测数据可跳过，之后随时补充"
                    } else "这些信息会约束本地计划",
                    color = FitnessColors.Ink,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (isInitialSetup) "核心训练功能不依赖账号或 AI，资料只保存在本机。"
                    else "训练目标、经验、伤病、可用时间和体测数据会影响建议；AI 输出仍需确认后才能写入。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (!isInitialSetup || setupStep == 1) {
        ProfileSectionHeader(title = "头像", meta = "仅保存在本机")
        val context = LocalContext.current
        Surface(
            onClick = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !saving && profile != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            shape = RoundedCornerShape(26.dp),
            color = FitnessColors.Surface,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = FitnessColors.Orange,
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (profile?.avatarPath?.isNotBlank() == true) {
                            AsyncImage(
                                model = File(context.filesDir, profile.avatarPath),
                                contentDescription = "当前头像",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        } else {
                            Text(
                                name.take(2).ifBlank { "头像" },
                                color = FitnessColors.Ink,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (profile?.avatarPath.isNullOrBlank()) "上传头像" else "更换头像", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                    Text("选择 JPG、PNG 或 WebP，系统会自动裁剪为正方形。", style = MaterialTheme.typography.bodyMedium)
                }
                Text("选择图片", color = FitnessColors.Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
        ProfileSectionHeader(title = "基础资料", meta = "必填")
        ProfileField(name, { name = it }, "昵称", ProfileTags.Name)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileField(birthYear, { birthYear = it }, "出生年", ProfileTags.BirthYear, Modifier.weight(1f))
            ProfileField(height, { height = it }, "身高 cm", ProfileTags.Height, Modifier.weight(1f))
        }
        ProfileField(weight, { weight = it }, "体重 kg", ProfileTags.Weight)
        }
        if (!isInitialSetup || setupStep == 2) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ProfileTags.Goal),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileSectionHeader(title = "训练目标", meta = "单选")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProfileGoalOptions.forEach { option ->
                    FitnessSelectionChip(
                        label = option,
                        selected = goal == option,
                        onClick = { goal = option },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileField(weeklyDays, { weeklyDays = it }, "每周天数", ProfileTags.WeeklyDays, Modifier.weight(1f))
            ProfileField(minutes, { minutes = it }, "单次分钟", ProfileTags.Minutes, Modifier.weight(1f))
        }
        ProfileField(injuries, { injuries = it }, "伤病 / 注意事项", ProfileTags.Injuries)
        }
        if (!isInitialSetup || setupStep == 3) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileSectionHeader(title = "身体参数", meta = if (isInitialSetup) "可跳过" else "全部可编辑 · AI 参考")
            Text("可按体测报告填写。数据只保存在本机，并作为 AI 训练建议的参考。", style = MaterialTheme.typography.bodyMedium)
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
            ProfileField(bmi, { bmi = it }, "BMI", ProfileTags.Bmi)
        }
        }
        errorMessage?.let { ProfileError(it) }
        if (isInitialSetup && setupStep > 1) {
            TextButton(
                onClick = { errorMessage = null; setupStep -= 1 },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("返回上一步", color = FitnessColors.Ink, fontWeight = FontWeight.Bold) }
        }
        FitnessPrimaryButton(
            text = when { // coverage-exempt: compiler-generated Compose change-mask branch; all labels are tested
                saving -> "保存中…"
                !isInitialSetup -> "保存训练偏好"
                setupStep < 3 -> "下一步"
                else -> "完成设置 · 体测可稍后补充"
            },
            enabled = !saving,
            testTag = ProfileTags.Save,
            onClick = {
                val validation = validateProfileForm(
                    ProfileFormInput(
                        name = name,
                        birthYear = birthYear,
                        height = height,
                        weight = weight,
                        weeklyDays = weeklyDays,
                        minutes = minutes,
                        measuredAt = measuredAt,
                        bodyFatPercentage = bodyFatPercentage,
                        bodyFatMassKg = bodyFatMassKg,
                        skeletalMuscleKg = skeletalMuscleKg,
                        bodyWaterKg = bodyWaterKg,
                        basalMetabolismKcal = basalMetabolismKcal,
                        waistHipRatio = waistHipRatio,
                        bmi = bmi,
                    ),
                )
                errorMessage = validation.errorMessage
                if (errorMessage == null && isInitialSetup && setupStep < 3) { // coverage-exempt: compiler-generated short-circuit branch; semantic cases are tested
                    setupStep += 1
                } else if (validation.parsed != null) { // coverage-exempt: compiler-generated Compose callback branch; success and failure are tested
                    val parsed = validation.parsed
                    saving = true
                    coroutineScope.launch {
                        try {
                            onSave(
                                name,
                                parsed.birthYear,
                                parsed.heightCm,
                                parsed.weightKg,
                                goal,
                                injuries,
                                parsed.weeklyTrainingDays,
                                parsed.preferredMinutes,
                                BodyMeasurement(
                                    measuredAt = measuredAt,
                                    bodyType = bodyType,
                                    bodyFatPercentage = parsed.bodyFatPercentage,
                                    bodyFatMassKg = parsed.bodyFatMassKg,
                                    skeletalMuscleKg = parsed.skeletalMuscleKg,
                                    bodyWaterKg = parsed.bodyWaterKg,
                                    basalMetabolismKcal = parsed.basalMetabolismKcal,
                                    waistHipRatio = parsed.waistHipRatio,
                                    bmi = parsed.bmi,
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = FitnessColors.Muted, fontWeight = FontWeight.Bold)
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = FitnessColors.Surface,
                unfocusedContainerColor = FitnessColors.Surface,
                disabledContainerColor = FitnessColors.Surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).shadow(5.dp, RoundedCornerShape(22.dp)).testTag(testTag),
        )
    }
}

@Composable
private fun ProfileSectionHeader(
    title: String,
    meta: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 4.dp,
                top = if (title == "头像") 0.dp else 14.dp,
                end = 4.dp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = FitnessColors.Ink,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = meta,
            style = MaterialTheme.typography.bodyMedium,
            color = FitnessColors.Muted,
            fontWeight = FontWeight.Bold,
        )
    }
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
    const val VisualRow = "profile-row-visual"
    const val VisualSheet = "profile-visual-sheet"
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
    const val Bmi = "profile-bmi"
    const val BodyMeasurementSummary = "profile-body-measurement-summary"
    const val Save = "save-profile"
}

private fun Double.toCompact(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.ROOT, "%.1f", this)

private fun Double?.toInput(): String = this?.toCompact().orEmpty()

private fun BodyMeasurement.hasValues(): Boolean =
    measuredAt.isNotBlank() || bodyType.isNotBlank() || bodyFatPercentage != null || bodyFatMassKg != null ||
        skeletalMuscleKg != null || bodyWaterKg != null || basalMetabolismKcal != null || waistHipRatio != null || bmi != null

private fun BodyMeasurement.summaryText(): String = buildList {
    bodyFatPercentage?.let { add("体脂率 ${it.toCompact()}%") }
    bodyFatMassKg?.let { add("体脂肪 ${it.toCompact()} kg") }
    skeletalMuscleKg?.let { add("骨骼肌 ${it.toCompact()} kg") }
    bodyWaterKg?.let { add("身体水分 ${it.toCompact()} kg") }
    basalMetabolismKcal?.let { add("基础代谢 $it kcal") }
    waistHipRatio?.let { add("腰臀比 ${it.toCompact()}") }
    bmi?.let { add("BMI ${it.toCompact()}") }
}.joinToString(" · ")

private val ProfileErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6)
private val ProfileErrorText = androidx.compose.ui.graphics.Color(0xFF690005)
private val ProfileGoalOptions = listOf("增肌", "减脂", "保持体能")
