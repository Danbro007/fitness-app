package com.shanqijie.fitnessapp.ui.plan

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanqijie.fitnessapp.data.AiDraftEntity
import com.shanqijie.fitnessapp.data.PlannedWorkoutEntity
import com.shanqijie.fitnessapp.data.PlannedExerciseView
import com.shanqijie.fitnessapp.data.WorkoutSessionEntity
import com.shanqijie.fitnessapp.data.WorkoutSetLogEntity
import com.shanqijie.fitnessapp.data.UserProfileEntity
import com.shanqijie.fitnessapp.domain.ExerciseChineseNameTranslator
import com.shanqijie.fitnessapp.ui.components.FitnessGifImage
import com.shanqijie.fitnessapp.ui.components.FitnessPageHeader
import com.shanqijie.fitnessapp.ui.components.FitnessPrimaryButton
import com.shanqijie.fitnessapp.ui.components.FitnessSurfaceCard
import com.shanqijie.fitnessapp.ui.theme.FitnessColors
import com.shanqijie.fitnessapp.ui.theme.FitnessDimensions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    plans: List<PlannedWorkoutEntity>,
    plannedExerciseViews: List<PlannedExerciseView>,
    sessions: List<WorkoutSessionEntity> = emptyList(),
    setLogs: List<WorkoutSetLogEntity> = emptyList(),
    weeklyTrainingDays: Int = 3,
    userProfile: UserProfileEntity? = null,
    initialCalendarMode: String = "周",
    onCalendarModeChange: suspend (String) -> Unit = {},
    activeMonthlyDraft: AiDraftEntity?,
    onOpenPlan: (String) -> Unit,
    onCreatePlan: suspend (name: String, date: String) -> Unit,
    onGenerateMonthlyDraft: suspend () -> Unit,
    onConfirmMonthlyDraft: suspend (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNewPlanEditor by rememberSaveable { mutableStateOf(false) }
    var editorName by rememberSaveable { mutableStateOf("自定义训练") }
    var editorDate by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var operationError by rememberSaveable { mutableStateOf<String?>(null) }
    var operationInProgress by rememberSaveable { mutableStateOf(false) }
    var calendarMode by rememberSaveable { mutableStateOf(initialCalendarMode) }
    var calendarMonth by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val exerciseCountByPlan = plannedExerciseViews.groupingBy { it.plannedExercise.plannedWorkoutId }.eachCount()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .testTag(PlanTags.Screen)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        FitnessPageHeader(
            title = "训练日历",
            kicker = "历史训练与未来计划",
            action = {
                Surface(
                    onClick = {
                        operationError = null
                        showNewPlanEditor = true
                    },
                    modifier = Modifier.size(52.dp).testTag(PlanTags.NewPlan),
                    shape = CircleShape,
                    color = FitnessColors.Surface,
                    shadowElevation = 8.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, contentDescription = "添加训练日") }
                }
            },
        )

        PlanSpotlight(weeklyTrainingDays)

        TrainingCalendar(
            mode = calendarMode,
            month = YearMonth.parse(calendarMonth),
            plans = plans,
            sessions = sessions,
            onModeChange = {
                calendarMode = it
                coroutineScope.launch { onCalendarModeChange(it) }
            },
            onMonthChange = { calendarMonth = it.toString() },
            onSelectDate = { selectedDate = it.toString() },
        )

        WeeklySchedule(
            plans = plans,
            exerciseCountByPlan = exerciseCountByPlan,
            onOpenPlan = onOpenPlan,
        )

        MonthlyDraftSection(
            draft = activeMonthlyDraft,
            userProfile = userProfile,
            operationInProgress = operationInProgress,
            operationError = operationError,
            onGenerate = {
                if (!operationInProgress) {
                    operationInProgress = true
                    operationError = null
                    coroutineScope.launch {
                        try {
                            onGenerateMonthlyDraft()
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Exception) {
                            operationError = error.message ?: "生成计划草稿失败"
                        } finally {
                            operationInProgress = false
                        }
                    }
                }
            },
            onConfirm = { draftId ->
                if (!operationInProgress) {
                    operationInProgress = true
                    operationError = null
                    coroutineScope.launch {
                        try {
                            onConfirmMonthlyDraft(draftId)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Exception) {
                            operationError = error.message ?: "确认计划失败"
                        } finally {
                            operationInProgress = false
                        }
                    }
                }
            },
        )
    }

    if (showNewPlanEditor) {
        ModalBottomSheet(
            onDismissRequest = { if (!operationInProgress) showNewPlanEditor = false },
            containerColor = FitnessColors.Surface,
            modifier = Modifier.testTag(PlanTags.Editor),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("添加一场训练", style = MaterialTheme.typography.headlineSmall)
                Text("只添加到指定日期；确认后才会保存到这台设备。", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = editorName,
                    onValueChange = { editorName = it },
                    label = { Text("计划名称") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PlanTags.NameInput),
                )
                OutlinedTextField(
                    value = editorDate,
                    onValueChange = { editorDate = it },
                    label = { Text("训练日期（可直接输入 YYYY-MM-DD）") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PlanTags.DateInput),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                context.showPlanDatePicker(editorDate) { selected -> editorDate = selected }
                            },
                        ) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = "选择训练日期")
                        }
                    },
                )
                operationError?.let { ErrorText(it) }
                FitnessPrimaryButton(
                    text = if (operationInProgress) "保存中…" else "确认创建",
                    enabled = !operationInProgress,
                    testTag = PlanTags.SaveNewPlan,
                    onClick = {
                        if (!operationInProgress) {
                            operationInProgress = true
                            operationError = null
                            coroutineScope.launch {
                                try {
                                    onCreatePlan(editorName, editorDate)
                                    showNewPlanEditor = false
                                } catch (cancellation: CancellationException) {
                                    throw cancellation
                                } catch (error: Exception) {
                                    operationError = error.message ?: "创建计划失败"
                                } finally {
                                    operationInProgress = false
                                }
                            }
                        }
                    },
                )
            }
        }
    }
    selectedDate?.let { rawDate ->
        val date = LocalDate.parse(rawDate)
        val dayPlans = plans.filter { it.scheduledDate == rawDate }
        val daySessions = sessions.filter { it.startedAt.toLocalDate() == date }
        ModalBottomSheet(onDismissRequest = { selectedDate = null }, containerColor = FitnessColors.Surface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")), style = MaterialTheme.typography.headlineSmall)
                if (dayPlans.isEmpty() && daySessions.isEmpty()) {
                    Text("休息日 / 当天没有训练安排", style = MaterialTheme.typography.bodyLarge)
                }
                daySessions.forEach { session ->
                    val logs = setLogs.filter { it.sessionId == session.id && it.completed }
                    Text("已完成训练 · ${logs.size} 组", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                    logs.forEach { log -> Text("第 ${log.setIndex} 组 · ${log.actualReps} 次 · ${log.actualWeightKg} kg", style = MaterialTheme.typography.bodyMedium) }
                }
                dayPlans.forEach { plan ->
                    Text(plan.name, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                    Text(if (plan.status == "completed") "已完成" else "已计划", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { selectedDate = null; onOpenPlan(plan.id) }) { Text("查看计划详情") }
                }
            }
        }
    }
}

@Composable
private fun PlanSpotlight(weeklyTrainingDays: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(34.dp))
            .background(FitnessColors.Hero).padding(24.dp),
    ) {
        Text("第 ${java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear().getFrom(LocalDate.now())} 周 · 本地计划", color = Color(0xFF9B9E95), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("一周 ${weeklyTrainingDays} 练", color = FitnessColors.OnHero, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 10.dp))
        Text("力量优先 · 单次约 35 分钟", color = Color(0xFF9B9E95), fontSize = 13.sp)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 34.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("一", "三", "六").forEachIndexed { index, label ->
                Surface(shape = CircleShape, color = if (index < 2) FitnessColors.Orange else Color.Transparent, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF43453E)), modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(label, color = if (index < 2) FitnessColors.Ink else Color(0xFF9B9E95), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
                if (index < 2) Spacer(Modifier.weight(1f).height(2.dp).background(Color(0xFF343630)))
            }
        }
    }
}

@Composable
private fun TrainingCalendar(
    mode: String,
    month: YearMonth,
    plans: List<PlannedWorkoutEntity>,
    sessions: List<WorkoutSessionEntity>,
    onModeChange: (String) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val completedDates = sessions.filter { it.status == "completed" }.mapTo(mutableSetOf()) { it.startedAt.toLocalDate() }
    val plannedDates = plans.filter { it.status != "completed" }.mapNotNullTo(mutableSetOf()) { runCatching { LocalDate.parse(it.scheduledDate) }.getOrNull() }
    FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(FitnessColors.Phone).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("周", "月", "年").forEach { item ->
                Button(
                    onClick = { onModeChange(item) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mode == item) FitnessColors.Orange else Color.Transparent,
                        contentColor = FitnessColors.Ink,
                    ),
                    modifier = Modifier.weight(1f).height(42.dp),
                ) { Text(item) }
            }
        }
        val overviewTitle = when (mode) {
            "周" -> {
                val start = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                "${start.monthValue} 月 ${start.dayOfMonth}—${start.plusDays(8).dayOfMonth} 日"
            }
            "月" -> "${month.year} 年 ${month.monthValue} 月"
            else -> "${month.year} 年"
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text(if (mode == "周") "本周与下周" else if (mode == "月") "月度分布" else "全年概览", fontSize = 10.sp, color = FitnessColors.Muted, fontWeight = FontWeight.Bold)
                Text(overviewTitle, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text("已完成 ${completedDates.size} · 已计划 ${plannedDates.size}", fontSize = 10.sp, color = FitnessColors.Muted, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("✓ 已完成", "◯ 今天", "· 已计划").forEach { label ->
                Surface(color = FitnessColors.Phone, shape = RoundedCornerShape(99.dp)) {
                    Text(label, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), fontSize = 9.sp, color = FitnessColors.Muted, fontWeight = FontWeight.Bold)
                }
            }
        }
        when (mode) {
            "周" -> {
                val start = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                (0L..6L).forEach { offset ->
                    val date = start.plusDays(offset)
                    CalendarDayRow(date, completedDates, plannedDates, plans, sessions) { onSelectDate(date) }
                }
            }
            "月" -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onMonthChange(month.minusMonths(1)) }) { Text("上月") }
                    Text("${month.year}年${month.monthValue}月", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onMonthChange(month.plusMonths(1)) }) { Text("下月") }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                        Text(label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 9.sp, color = FitnessColors.Muted, fontWeight = FontWeight.Bold)
                    }
                }
                val offset = month.atDay(1).dayOfWeek.value - 1
                val cells = List(offset) { null } + (1..month.lengthOfMonth()).map(month::atDay)
                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { date ->
                            val label = when {
                                date == null -> ""
                                date in completedDates -> "${date.dayOfMonth} ✓"
                                date in plannedDates -> "${date.dayOfMonth} ·"
                                else -> date.dayOfMonth.toString()
                            }
                            TextButton(
                                onClick = { date?.let(onSelectDate) },
                                enabled = date != null,
                                modifier = Modifier.weight(1f).heightIn(min = FitnessDimensions.MinimumTouchTarget),
                            ) { Text(label, maxLines = 1) }
                        }
                        repeat(7 - week.size) { Text("", modifier = Modifier.weight(1f)) }
                    }
                }
            }
            else -> {
                Text("${month.year} 年", style = MaterialTheme.typography.headlineSmall)
                (1..12).chunked(3).forEach { quarter ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        quarter.forEach { monthValue ->
                            val target = YearMonth.of(month.year, monthValue)
                            val completeCount = completedDates.count { YearMonth.from(it) == target }
                            val planCount = plannedDates.count { YearMonth.from(it) == target }
                            OutlinedButton(
                                onClick = { onMonthChange(target); onModeChange("月") },
                                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                            ) { Text("${monthValue}月\n$completeCount/$planCount") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayRow(
    date: LocalDate,
    completedDates: Set<LocalDate>,
    plannedDates: Set<LocalDate>,
    plans: List<PlannedWorkoutEntity>,
    sessions: List<WorkoutSessionEntity>,
    onClick: () -> Unit,
) {
    val status = when {
        date in completedDates -> "已完成"
        date == LocalDate.now() -> "今天"
        date in plannedDates -> "已计划"
        else -> "休息日"
    }
    val plan = plans.firstOrNull { it.scheduledDate == date.toString() }
    val completed = sessions.firstOrNull { it.status == "completed" && it.startedAt.toLocalDate() == date }
    val eventName = plan?.name ?: if (completed != null) "已完成训练" else if (status == "休息日") "恢复与拉伸" else "当天训练"
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .border(1.dp, if (date == LocalDate.now()) FitnessColors.Orange else Color(0x1410110F), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.size(width = 52.dp, height = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (date == LocalDate.now()) "今天" else WeekdayLabels[date.dayOfWeek.value - 1], fontSize = 9.sp, color = FitnessColors.Muted)
            Text(date.dayOfMonth.toString().padStart(2, '0'), fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(eventName, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(if (plan != null) "${plan.status} · 本地计划" else if (completed != null) "训练记录已保存" else "当天没有训练安排", fontSize = 9.sp, color = FitnessColors.Muted)
        }
        Surface(
            color = when (status) { "已完成" -> FitnessColors.Ink; "已计划" -> FitnessColors.Orange; else -> FitnessColors.Phone },
            contentColor = if (status == "已完成") Color.White else FitnessColors.Ink,
            shape = RoundedCornerShape(99.dp),
        ) { Text(status, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) }
    }
}

private fun Long.toLocalDate(): LocalDate = java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun WeeklySchedule(
    plans: List<PlannedWorkoutEntity>,
    exerciseCountByPlan: Map<String, Int>,
    onOpenPlan: (String) -> Unit,
) {
    val today = LocalDate.now()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PlanTags.WeeklySchedule),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("这周的安排", style = MaterialTheme.typography.headlineSmall)
        (0L..6L).forEach { offset ->
            val date = weekStart.plusDays(offset)
            val plan = plans.firstOrNull { it.scheduledDate == date.toString() }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .then(
                        if (plan != null) {
                            Modifier
                                .testTag("plan-row-${plan.id}")
                                .clickable { onOpenPlan(plan.id) }
                        } else {
                            Modifier
                        },
                    ),
                shape = RoundedCornerShape(FitnessDimensions.ContainerRadius),
                colors = CardDefaults.cardColors(containerColor = FitnessColors.Surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.size(width = 54.dp, height = 44.dp)) {
                        Text(WeekdayLabels[offset.toInt()], color = FitnessColors.Muted)
                        Text(date.format(dateFormatter), color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = plan?.name ?: "休息日",
                            color = FitnessColors.Ink,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (plan != null) {
                            Text(
                                "${exerciseCountByPlan[plan.id] ?: 0} 个动作 · ${plan.status.toStatusLabel()}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Text("留给恢复和日常活动", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (plan != null) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = FitnessColors.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyDraftSection(
    draft: AiDraftEntity?,
    userProfile: UserProfileEntity?,
    operationInProgress: Boolean,
    operationError: String?,
    onGenerate: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    FitnessSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PlanTags.MonthlyGenerator),
    ) {
        Text("周期建议（可选）", style = MaterialTheme.typography.headlineSmall)
        Text(
            "需要连续安排时再生成。它只是建议草稿；确认后才会创建未来 4 周的训练日。",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (draft == null) {
            OutlinedButton(
                onClick = onGenerate,
                enabled = !operationInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FitnessDimensions.MinimumTouchTarget)
                    .testTag(PlanTags.GenerateMonthlyDraft),
            ) {
                Text(if (operationInProgress) "生成中…" else "生成四周建议")
            }
        } else {
            Text("本次 AI 输入", style = MaterialTheme.typography.headlineSmall)
            Text("全部档案数据已参与", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
            Text(userProfile.aiInputSnapshot(), style = MaterialTheme.typography.bodyMedium)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FitnessColors.Phone, RoundedCornerShape(FitnessDimensions.ContainerRadius))
                    .testTag(PlanTags.MonthlyDraft)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(draft.title, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                Text(draft.content, style = MaterialTheme.typography.bodyMedium)
                Text("确认后才创建未来 4 周", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                FitnessPrimaryButton(
                    text = if (operationInProgress) "确认中…" else "确认并创建 4 周",
                    onClick = { onConfirm(draft.id) },
                    enabled = !operationInProgress,
                    testTag = PlanTags.ConfirmMonthlyDraft,
                )
            }
        }
        operationError?.let { ErrorText(it) }
    }
}

private fun UserProfileEntity?.aiInputSnapshot(): String {
    if (this == null) return "请先补全训练偏好与体测档案"
    val m = bodyMeasurement
    return listOf(
        "昵称：$displayName", "出生年：$birthYear", "身高：$heightCm cm", "体重：$weightKg kg",
        "训练目标：$goal", "每周训练天数：$weeklyTrainingDays", "单次训练分钟：$preferredMinutes",
        "伤病/注意事项：${injuries.ifBlank { "未填写" }}",
        "体脂率：${m.bodyFatPercentage?.let { "$it%" } ?: "未填写"}",
        "体脂肪：${m.bodyFatMassKg?.let { "$it kg" } ?: "未填写"}", "BMI：${m.bmi ?: "未填写"}",
        "骨骼肌：${m.skeletalMuscleKg?.let { "$it kg" } ?: "未填写"}",
        "身体水分：${m.bodyWaterKg?.let { "$it kg" } ?: "未填写"}",
        "基础代谢：${m.basalMetabolismKcal?.let { "$it kcal" } ?: "未填写"}",
        "腰臀比：${m.waistHipRatio ?: "未填写"}",
    ).joinToString("\n")
}

@Composable
fun PlanDetailScreen(
    plan: PlannedWorkoutEntity,
    exercises: List<PlannedExerciseView>,
    onEdit: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .testTag(PlanTags.Detail)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(plan.name, style = MaterialTheme.typography.headlineLarge)
        Text("${exercises.size} 个动作", style = MaterialTheme.typography.bodyLarge)
        exercises.sortedBy { it.plannedExercise.orderIndex }.forEach { view ->
            PlanExerciseRow(view)
        }
        FitnessPrimaryButton(
            text = "编辑计划",
            onClick = onEdit,
            testTag = PlanTags.EditPlan,
        )
        OutlinedButton(
            onClick = onOpenLibrary,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FitnessDimensions.MinimumTouchTarget)
                .testTag(PlanTags.OpenLibrary),
        ) {
            Text("从动作库添加")
        }
    }
}

@Composable
fun PlanEditScreen(
    plan: PlannedWorkoutEntity,
    exercises: List<PlannedExerciseView>,
    onSave: suspend (name: String, date: String) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable(plan.id) { mutableStateOf(plan.name) }
    var date by rememberSaveable(plan.id) { mutableStateOf(plan.scheduledDate) }
    var saving by rememberSaveable(plan.id) { mutableStateOf(false) }
    var errorMessage by rememberSaveable(plan.id) { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .testTag(PlanTags.Edit)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("计划名称") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PlanTags.EditName),
        )
        OutlinedTextField(
            value = date,
            onValueChange = { date = it },
            label = { Text("训练日期（可直接输入 YYYY-MM-DD）") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(
                    onClick = { context.showPlanDatePicker(date) { selected -> date = selected } },
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = "选择训练日期")
                }
            },
        )
        Text("动作清单", style = MaterialTheme.typography.headlineSmall)
        exercises.sortedBy { it.plannedExercise.orderIndex }.forEach { view ->
            PlanExerciseRow(view)
        }
        OutlinedButton(
            onClick = onOpenLibrary,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FitnessDimensions.MinimumTouchTarget)
                .testTag(PlanTags.OpenLibrary),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text("从动作库添加")
        }
        errorMessage?.let { ErrorText(it) }
        FitnessPrimaryButton(
            text = if (saving) "保存中…" else "保存修改",
            enabled = !saving,
            testTag = PlanTags.SaveEdit,
            onClick = {
                if (!saving) {
                    saving = true
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            onSave(name, date)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Exception) {
                            errorMessage = error.message ?: "保存计划失败"
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
private fun PlanExerciseRow(view: PlannedExerciseView) {
    val exerciseName = ExerciseChineseNameTranslator.translate(view.media.name)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FitnessDimensions.ContainerRadius),
        colors = CardDefaults.cardColors(containerColor = FitnessColors.Surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FitnessGifImage(
                assetPath = view.media.localPath,
                contentDescription = exerciseName,
                modifier = Modifier
                    .size(64.dp)
                    .background(FitnessColors.Phone, RoundedCornerShape(12.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(exerciseName, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                Text(
                    "${view.plannedExercise.targetSets} 组 × ${view.plannedExercise.targetReps} 次 · ${view.plannedExercise.targetWeightKg.toWeight()} kg",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        color = ColorErrorText,
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorErrorContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

object PlanTags {
    const val Screen = "plan-screen"
    const val WeeklySchedule = "weekly-schedule"
    const val MonthlyGenerator = "monthly-plan-generator"
    const val NewPlan = "new-plan"
    const val Editor = "plan-editor"
    const val NameInput = "plan-name-input"
    const val DateInput = "plan-date-input"
    const val SaveNewPlan = "save-new-plan"
    const val Detail = "plan-detail"
    const val EditPlan = "edit-plan"
    const val Edit = "plan-edit"
    const val EditName = "plan-edit-name"
    const val SaveEdit = "save-plan-edit"
    const val OpenLibrary = "open-plan-library"
    const val GenerateMonthlyDraft = "generate-monthly-draft"
    const val MonthlyDraft = "monthly-plan-draft"
    const val ConfirmMonthlyDraft = "confirm-monthly-draft"
}

private val WeekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val ColorErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6)
private val ColorErrorText = androidx.compose.ui.graphics.Color(0xFF690005)

private fun String.toStatusLabel(): String = when (this) {
    "planned" -> "待训练"
    "in_progress" -> "进行中"
    "completed" -> "已完成"
    "skipped" -> "已跳过"
    else -> "本地计划"
}

private fun Double.toWeight(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.ROOT, "%.1f", this)

private fun android.content.Context.showPlanDatePicker(
    current: String,
    onSelected: (String) -> Unit,
) {
    val date = runCatching { LocalDate.parse(current) }.getOrElse { LocalDate.now() }
    DatePickerDialog(
        this,
        { _, year, month, dayOfMonth -> onSelected(LocalDate.of(year, month + 1, dayOfMonth).toString()) },
        date.year,
        date.monthValue - 1,
        date.dayOfMonth,
    ).show()
}
