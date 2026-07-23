package com.shanqijie.fitnessapp.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shanqijie.fitnessapp.data.AdaptiveDraftContent
import com.shanqijie.fitnessapp.data.ActionPreferenceEntity
import com.shanqijie.fitnessapp.data.FitnessAppState
import com.shanqijie.fitnessapp.data.PlanCycleConfiguration
import com.shanqijie.fitnessapp.data.PlanScheduleDayEntity
import com.shanqijie.fitnessapp.data.WeeklyPlanDraftEntity
import com.shanqijie.fitnessapp.domain.WeeklyPlanCandidate
import com.shanqijie.fitnessapp.ui.components.FitnessPageHeader
import com.shanqijie.fitnessapp.ui.components.FitnessPrimaryButton
import com.shanqijie.fitnessapp.ui.components.FitnessSurfaceCard
import com.shanqijie.fitnessapp.ui.theme.FitnessColors
import com.shanqijie.fitnessapp.ui.theme.FitnessDimensions
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@Composable
fun AdaptivePlanSetupScreen(
    state: FitnessAppState,
    onCreate: suspend (PlanCycleConfiguration) -> Unit,
    onSaveLoads: suspend (String, String, List<Double>) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    var weeksText by remember { mutableStateOf("4") }
    var minutesText by remember { mutableStateOf("60") }
    var selectedDays by remember { mutableStateOf(setOf(1, 4)) }
    var venueByDay by remember { mutableStateOf(emptyMap<Int, String>()) }
    var loadTextByMapping by remember { mutableStateOf(emptyMap<String, String>()) }
    var savingMapping by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val defaultVenue = state.venue?.id ?: state.venues.firstOrNull()?.id.orEmpty()
    val venueName = { venueId: String -> state.venues.firstOrNull { it.id == venueId }?.name ?: "未设置场地" }
    val enabledMappings = state.venueEquipment.filter { it.available }
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp)
            .testTag(AdaptivePlanTags.SetupScreen),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FitnessPageHeader(title = "新训练周期", kicker = "先定节奏，再看每一周")
        FitnessSurfaceCard(Modifier.fillMaxWidth()) {
            Text("周期周数", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(weeksText, { weeksText = it }, label = { Text("1–12 周") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            Text("所有训练日单次时长", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(minutesText, { minutesText = it }, label = { Text("15–180 分钟") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            Text("训练日", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { day ->
                    FilterChip(
                        selected = day in selectedDays,
                        onClick = {
                            val wasSelected = day in selectedDays
                            selectedDays = if (wasSelected) selectedDays - day else selectedDays + day
                            if (wasSelected) venueByDay = venueByDay - day
                        },
                        label = { Text("周$day") },
                    )
                }
            }
            Text("每个训练日可单独选择场地；未选择时使用默认场地。", style = MaterialTheme.typography.bodyMedium)
            selectedDays.sorted().forEach { day ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("周$day", modifier = Modifier.width(42.dp), style = MaterialTheme.typography.labelLarge)
                    state.venues.forEach { venue ->
                        FilterChip(
                            selected = (venueByDay[day] ?: defaultVenue) == venue.id,
                            onClick = { venueByDay = venueByDay + (day to venue.id) },
                            label = { Text(venue.name) },
                        )
                    }
                }
            }
            Text("默认场地：${venueName(defaultVenue)}", style = MaterialTheme.typography.bodyMedium)
        }
        FitnessSurfaceCard(Modifier.fillMaxWidth()) {
            Text("器械重量档位", style = MaterialTheme.typography.labelLarge)
            Text("建议重量只会从这里选择；你可以按场地和器械调整档位。", style = MaterialTheme.typography.bodyMedium)
            if (enabledMappings.isEmpty()) {
                Text("还没有可用器械，请先在器械设置中启用。", color = MaterialTheme.colorScheme.error)
            } else {
                enabledMappings.forEach { mapping ->
                    val mappingKey = "${mapping.venueId}:${mapping.equipmentId}"
                    val equipmentName = state.equipment.firstOrNull { it.id == mapping.equipmentId }?.name ?: mapping.equipmentId
                    val initialText = state.venueEquipmentLoads
                        .filter { it.venueId == mapping.venueId && it.equipmentId == mapping.equipmentId }
                        .sortedBy { it.orderIndex }
                        .joinToString(", ") { it.weightKg.toString().trimEnd('0').trimEnd('.') }
                    var textValue = loadTextByMapping[mappingKey] ?: initialText
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { value ->
                            textValue = value
                            loadTextByMapping = loadTextByMapping + (mappingKey to value)
                        },
                        label = { Text("${venueName(mapping.venueId)} · $equipmentName（kg）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        enabled = savingMapping != mappingKey,
                        onClick = {
                            val weights = textValue.split(',', '，', ' ').mapNotNull { it.trim().toDoubleOrNull() }
                            if (weights.isEmpty()) {
                                error = "请为 $equipmentName 填写至少一个重量档位"
                            } else {
                                savingMapping = mappingKey
                                scope.launch {
                                    runCatching { onSaveLoads(mapping.venueId, mapping.equipmentId, weights) }
                                        .onFailure { error = it.message ?: "保存重量档位失败" }
                                    savingMapping = null
                                }
                            }
                        },
                    ) { Text(if (savingMapping == mappingKey) "保存中…" else "保存档位") }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        FitnessPrimaryButton(
            text = "创建训练周期",
            testTag = AdaptivePlanTags.CreateCycle,
            enabled = selectedDays.isNotEmpty(),
            onClick = {
                val weeks = weeksText.toIntOrNull() ?: 0
                val minutes = minutesText.toIntOrNull() ?: 0
                if (weeks !in 1..12 || minutes !in 15..180) {
                    error = "周数需为 1–12，时长需为 15–180 分钟"
                } else {
                    error = null
                    scope.launch {
                        runCatching {
                            val cycleId = "cycle-${UUID.randomUUID()}"
                            onCreate(
                                PlanCycleConfiguration(
                                        id = cycleId,
                                    totalWeeks = weeks,
                                    startDate = LocalDate.now().with(java.time.DayOfWeek.MONDAY),
                                    preferredMinutes = minutes,
                                    trainingDays = selectedDays.sorted().mapIndexed { index, day ->
                                        PlanScheduleDayEntity(cycleId, day, venueByDay[day] ?: defaultVenue, index)
                                    },
                                ),
                            )
                        }.onFailure { error = it.message ?: "创建周期失败" }
                    }
                }
            },
        )
    }
}

@Composable
fun AdaptivePlanOverviewScreen(
    cycle: com.shanqijie.fitnessapp.data.PlanCycleEntity?,
    draft: WeeklyPlanDraftEntity?,
    onGenerate: suspend () -> Unit,
    onOpenDraft: () -> Unit,
    onOpenSetup: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FitnessPageHeader(title = "滚动计划", kicker = "每次只确认一周")
        if (cycle == null) {
            FitnessSurfaceCard(Modifier.fillMaxWidth()) {
                Text("还没有活动周期", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenSetup) { Text("设置训练周期") }
            }
        } else {
            FitnessSurfaceCard(Modifier.fillMaxWidth().testTag(AdaptivePlanTags.WeekRail)) {
                Text("第 ${cycle.currentWeek} / ${cycle.totalWeeks} 周", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..cycle.totalWeeks).forEach { week ->
                        Surface(shape = CircleShape, color = if (week < cycle.currentWeek) FitnessColors.Orange else FitnessColors.Ink, modifier = Modifier.size(28.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(week.toString(), color = if (week < cycle.currentWeek) FitnessColors.Ink else FitnessColors.OnHero, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Text("生成后仍是草稿；确认整周后才写入正式计划", style = MaterialTheme.typography.bodyMedium)
            }
            draft?.let {
                FitnessSurfaceCard(Modifier.fillMaxWidth().testTag(AdaptivePlanTags.DraftCard)) {
                    Text(if (it.status == "stale") "草稿已过期" else "第 ${it.weekIndex} 周草稿", style = MaterialTheme.typography.headlineSmall)
                    Text(if (it.status == "stale") "训练档案或器械配置已变化，请重新生成" else "逐动作依据和重量建议已准备好", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onOpenDraft) { Text("查看依据") }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            FitnessPrimaryButton(
                text = if (busy) "生成中…" else "生成第 ${cycle.currentWeek} 周计划",
                testTag = AdaptivePlanTags.GenerateWeek,
                enabled = !busy && cycle.status == "active",
                onClick = {
                    busy = true
                    scope.launch { runCatching { onGenerate() }.onFailure { error = it.message ?: "生成失败" }; busy = false }
                },
            )
        }
    }
}

@Composable
fun AdaptivePlanDraftScreen(
    draft: WeeklyPlanDraftEntity,
    content: AdaptiveDraftContent?,
    onConfirm: suspend () -> Unit,
    onRefresh: suspend () -> Unit,
    actionPreferences: List<ActionPreferenceEntity> = emptyList(),
    onAdjustWeight: suspend (String, String, Double) -> AdaptiveDraftContent = { _, _, _ -> error("草稿调整不可用") },
    onSaveActionPreference: suspend (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editableContent by remember(draft.id, content) { mutableStateOf(content) }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FitnessPageHeader(title = "第 ${draft.weekIndex} 周草稿", kicker = if (draft.status == "stale") "需要重新生成" else "软性参考 · 用户确认")
        editableContent?.days?.forEach { day ->
            FitnessSurfaceCard(Modifier.fillMaxWidth()) {
                Text("周${day.dayOfWeek} · ${day.venueId}", style = MaterialTheme.typography.headlineSmall)
                day.exercises.forEach { exercise ->
                    var weightText by remember(draft.id, exercise.exerciseId, exercise.targetWeightKg) {
                        mutableStateOf(exercise.targetWeightKg.toString().trimEnd('0').trimEnd('.'))
                    }
                    val persistentPreference = actionPreferences.firstOrNull { it.exerciseId == exercise.exerciseId }?.preference
                    Text(exercise.name, style = MaterialTheme.typography.bodyLarge)
                    Text("${exercise.targetSets} 组 × ${exercise.targetRepsPerSet} 次", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = weightText,
                            onValueChange = { weightText = it },
                            label = { Text("重量（kg）") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            enabled = !busy && draft.status == "draft",
                            onClick = {
                                val weight = weightText.toDoubleOrNull()
                                if (weight == null) {
                                    error = "请输入有效重量"
                                } else {
                                    busy = true
                                    scope.launch {
                                        runCatching { editableContent = onAdjustWeight(draft.id, exercise.exerciseId, weight) }
                                            .onFailure { error = it.message ?: "调整本周重量失败" }
                                        busy = false
                                    }
                                }
                            },
                        ) { Text("应用本周") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("动作偏好", style = MaterialTheme.typography.labelMedium)
                        FilterChip(
                            selected = persistentPreference != "exclude",
                            onClick = {
                                scope.launch {
                                    runCatching { onSaveActionPreference(exercise.exerciseId, "auto") }
                                        .onFailure { error = it.message ?: "保存动作偏好失败" }
                                }
                            },
                            label = { Text("自动安排") },
                        )
                        FilterChip(
                            selected = persistentPreference == "exclude",
                            onClick = {
                                scope.launch {
                                    runCatching { onSaveActionPreference(exercise.exerciseId, "exclude") }
                                        .onFailure { error = it.message ?: "保存动作偏好失败" }
                                }
                            },
                            label = { Text("以后排除") },
                        )
                    }
                    editableContent?.explanations?.firstOrNull { it.exerciseId == exercise.exerciseId }
                        ?.let { Text("依据：${it.message}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        if (editableContent == null) Text("正在加载草稿内容…", style = MaterialTheme.typography.bodyLarge)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(enabled = !busy, onClick = { busy = true; scope.launch { runCatching { onRefresh() }.onFailure { error = it.message }; busy = false } }) { Text("检查是否过期") }
            Button(enabled = !busy && draft.status == "draft", onClick = { busy = true; scope.launch { runCatching { onConfirm() }.onFailure { error = it.message ?: "确认失败" }; busy = false } }, modifier = Modifier.testTag(AdaptivePlanTags.ConfirmWeek)) { Text("确认整周") }
        }
    }
}

object AdaptivePlanTags {
    const val SetupScreen = "adaptive-plan-setup"
    const val CreateCycle = "adaptive-plan-create-cycle"
    const val WeekRail = "adaptive-plan-week-rail"
    const val DraftCard = "adaptive-plan-draft-card"
    const val GenerateWeek = "adaptive-plan-generate-week"
    const val ConfirmWeek = "adaptive-plan-confirm-week"
}
