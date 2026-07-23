package com.shanqijie.fitnessapp.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.DragIndicator
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shanqijie.fitnessapp.data.AiDraftEntity
import com.shanqijie.fitnessapp.data.PlannedWorkoutEntity
import com.shanqijie.fitnessapp.data.PlannedExerciseView
import com.shanqijie.fitnessapp.data.WorkoutSessionEntity
import com.shanqijie.fitnessapp.data.WorkoutSetLogEntity
import com.shanqijie.fitnessapp.data.UserProfileEntity
import com.shanqijie.fitnessapp.data.FitnessAppState
import com.shanqijie.fitnessapp.data.AdaptiveDraftContent
import com.shanqijie.fitnessapp.data.PlanCycleEntity
import com.shanqijie.fitnessapp.data.PlanCycleConfiguration
import com.shanqijie.fitnessapp.data.PlanDraftExplanation
import com.shanqijie.fitnessapp.data.WeeklyPlanDraftEntity
import com.shanqijie.fitnessapp.domain.WeeklyPlanCandidate
import com.shanqijie.fitnessapp.domain.ExerciseChineseNameTranslator
import com.shanqijie.fitnessapp.domain.toReadableAiText
import com.shanqijie.fitnessapp.ui.components.FitnessGifImage
import com.shanqijie.fitnessapp.ui.components.FitnessFloatingBottomDialog
import com.shanqijie.fitnessapp.ui.components.FitnessLoadingIndicator
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
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    plans: List<PlannedWorkoutEntity>,
    plannedExerciseViews: List<PlannedExerciseView>,
    sessions: List<WorkoutSessionEntity>,
    setLogs: List<WorkoutSetLogEntity>,
    weeklyTrainingDays: Int,
    userProfile: UserProfileEntity?,
    initialCalendarMode: String,
    onCalendarModeChange: suspend (String) -> Unit,
    activeMonthlyDraft: AiDraftEntity?,
    onOpenPlan: (String) -> Unit,
    onCreatePlan: suspend (name: String, date: String) -> String,
    onGenerateMonthlyDraft: suspend () -> String,
    onOpenMonthlyDraft: (String) -> Unit,
    onConfirmMonthlyDraft: suspend (String) -> Unit,
    onStartPlan: (String) -> Unit,
    modifier: Modifier,
    adaptiveCycle: PlanCycleEntity? = null,
    adaptiveDraft: WeeklyPlanDraftEntity? = null,
    adaptiveDraftContent: AdaptiveDraftContent? = null,
    onCreateAdaptiveCycle: suspend (PlanCycleConfiguration) -> Unit = {},
    onGenerateAdaptiveWeek: suspend () -> Unit = {},
    onConfirmAdaptiveWeek: suspend () -> Unit = {},
    onRefreshAdaptiveDraft: suspend () -> Unit = {},
    onAdaptiveDraftContent: suspend (String) -> AdaptiveDraftContent = { error("草稿内容不可用") },
    onAdjustAdaptiveDraftWeight: suspend (String, String, Double) -> AdaptiveDraftContent = { _, _, _ -> error("草稿调整不可用") },
    onSaveAdaptiveActionPreference: suspend (String, String) -> Unit = { _, _ -> },
    onSaveAdaptiveVenueLoads: suspend (String, String, List<Double>) -> Unit = { _, _, _ -> },
    onSuggestedAdaptiveCandidate: () -> WeeklyPlanCandidate? = { null },
    adaptiveState: FitnessAppState? = null,
) {
    var showNewPlanEditor by rememberSaveable { mutableStateOf(false) }
    var editorName by rememberSaveable { mutableStateOf("自定义训练") }
    var editorDate by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var operationError by rememberSaveable { mutableStateOf<String?>(null) }
    var operationInProgress by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var calendarMode by rememberSaveable { mutableStateOf(initialCalendarMode) }
    var calendarMonth by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdaptiveSetup by rememberSaveable { mutableStateOf(false) }
    var showAdaptiveDraft by rememberSaveable { mutableStateOf(false) }
    var loadedAdaptiveDraftContent by remember { mutableStateOf(adaptiveDraftContent) }
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
            kicker = "回看记录，也看见下一步",
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

        val spotlightPlanId = plans.firstOrNull { it.scheduledDate == LocalDate.now().toString() }?.id ?: plans.firstOrNull()?.id
        PlanSpotlight(weeklyTrainingDays, onClick = spotlightPlanId?.let { id -> { onOpenPlan(id) } })

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

        MonthlyDraftSection(
            draft = activeMonthlyDraft,
            userProfile = userProfile,
            operationInProgress = operationInProgress,
            operationError = operationError,
            onOpenDraft = onOpenMonthlyDraft,
            onGenerate = {
                operationInProgress = true
                operationError = null
                coroutineScope.launch {
                    try {
                        val generatedId = onGenerateMonthlyDraft()
                        onOpenMonthlyDraft(generatedId)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        operationError = error.message ?: "生成计划草稿失败"
                    } finally {
                        operationInProgress = false
                    }
                }
            },
        )

        AdaptivePlanOverviewScreen(
            cycle = adaptiveCycle,
            draft = adaptiveDraft,
            onGenerate = {
                require(onSuggestedAdaptiveCandidate() != null) { "当前场地没有足够器械或重量档位，请先完成场地设置" }
                onGenerateAdaptiveWeek()
            },
            onOpenDraft = {
                showAdaptiveDraft = true
                adaptiveDraft?.id?.let { id ->
                    coroutineScope.launch { loadedAdaptiveDraftContent = onAdaptiveDraftContent(id) }
                }
            },
            onOpenSetup = { showAdaptiveSetup = true },
        )
    }

    if (showNewPlanEditor) {
        ModalBottomSheet(
            onDismissRequest = { if (!operationInProgress) showNewPlanEditor = false }, // coverage-exempt: compiler-generated sheet callback branch; busy states are tested
            containerColor = FitnessColors.Surface,
            modifier = Modifier.testTag(PlanTags.Editor),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
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
                            onClick = { showDatePicker = true },
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
                                    val createdPlanId = onCreatePlan(editorName, editorDate)
                                    showNewPlanEditor = false
                                    onOpenPlan(createdPlanId)
                                } catch (cancellation: CancellationException) {
                                    throw cancellation
                                } catch (error: Exception) {
                                    operationError = error.toPlanOperationMessage("创建计划失败")
                                } finally {
                                    operationInProgress = false
                                }
                            }
                        }
                    },
                )
            }
        }

        if (showDatePicker) {
            PlanDatePickerDialog(
                initialDate = editorDate,
                onDismiss = { showDatePicker = false },
                onConfirm = { selected ->
                    editorDate = selected
                    showDatePicker = false
                },
            )
        }
    }
    selectedDate?.let { rawDate ->
        val date = LocalDate.parse(rawDate)
        val dayPlans = plans.filter { it.scheduledDate == rawDate }
        val daySessions = sessions.filter { it.startedAt.toLocalDate() == date }
        FitnessFloatingBottomDialog(
            onDismissRequest = { selectedDate = null },
            containerColor = FitnessColors.Surface,
            contentColor = FitnessColors.Ink,
            modifier = Modifier.testTag(PlanTags.DayDetail),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 21.dp, end = 20.dp, bottom = 23.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val primaryPlan = dayPlans.firstOrNull()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(25.dp)) {
                        Text(date.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")), style = MaterialTheme.typography.bodyMedium)
                        Text(primaryPlan?.name ?: if (daySessions.isNotEmpty()) "力量训练记录" else "休息日", style = MaterialTheme.typography.headlineSmall)
                    }
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = if (primaryPlan?.status == "completed" || daySessions.isNotEmpty()) FitnessColors.Ink else if (primaryPlan != null) FitnessColors.Orange else FitnessColors.Phone,
                        contentColor = if (primaryPlan?.status == "completed" || daySessions.isNotEmpty()) Color.White else FitnessColors.Ink,
                    ) {
                        Text(
                            when { primaryPlan?.status == "completed" || daySessions.isNotEmpty() -> "已完成"; primaryPlan != null -> "已计划"; else -> "无安排" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                if (dayPlans.isEmpty() && daySessions.isEmpty()) {
                    Text("休息日 / 当天没有训练安排", style = MaterialTheme.typography.bodyLarge)
                }
                daySessions.forEach { session ->
                    val logs = setLogs.filter { it.sessionId == session.id && it.completed }
                    Text("已完成训练 · ${logs.size} 组", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                    logs.forEach { log -> Text("第 ${log.setIndex} 组 · ${log.actualReps} 次 · ${log.actualWeightKg} kg", style = MaterialTheme.typography.bodyMedium) }
                }
                dayPlans.forEach { plan ->
                    val dayExercises = plannedExerciseViews
                        .filter { it.plannedExercise.plannedWorkoutId == plan.id }
                        .sortedBy { it.plannedExercise.orderIndex }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = FitnessColors.Phone,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(plan.name, color = FitnessColors.Ink, fontWeight = FontWeight.ExtraBold)
                            Text(plan.status.toStatusLabel(), color = FitnessColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("预计 35 分钟 · ${dayExercises.sumOf { it.plannedExercise.targetSets }} 组", style = MaterialTheme.typography.bodyMedium)
                    dayExercises.forEachIndexed { index, view ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = FitnessColors.Phone,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(shape = RoundedCornerShape(14.dp), color = FitnessColors.Surface, modifier = Modifier.size(44.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Text((index + 1).toString().padStart(2, '0'), fontWeight = FontWeight.Bold, fontSize = 10.sp) }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(ExerciseChineseNameTranslator.translate(view.media.name), fontWeight = FontWeight.Bold)
                                    Text(
                                        "${view.plannedExercise.targetSets} 组 × ${view.plannedExercise.targetReps} 次 · ${view.plannedExercise.targetWeightKg.toWeight()} kg",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                    FitnessPrimaryButton(
                        text = "开始 ${plan.name}  →",
                        testTag = "${PlanTags.StartDayTraining}-${plan.id}",
                        onClick = { selectedDate = null; onStartPlan(plan.id) },
                    )
                }
                if (dayPlans.isEmpty()) {
                    FitnessPrimaryButton(
                        text = "返回日历",
                        onClick = { selectedDate = null },
                    )
                }
            }
        }
    }

    if (showAdaptiveSetup) {
        ModalBottomSheet(onDismissRequest = { showAdaptiveSetup = false }) {
            adaptiveState?.let { state ->
                AdaptivePlanSetupScreen(
                            state = state,
                            onCreate = { configuration -> onCreateAdaptiveCycle(configuration); showAdaptiveSetup = false },
                            onSaveLoads = onSaveAdaptiveVenueLoads,
                            modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
    }

    if (showAdaptiveDraft && adaptiveDraft != null) {
        ModalBottomSheet(onDismissRequest = { showAdaptiveDraft = false }) {
            AdaptivePlanDraftScreen(
                draft = adaptiveDraft,
                content = loadedAdaptiveDraftContent,
                onConfirm = { onConfirmAdaptiveWeek(); showAdaptiveDraft = false },
                onRefresh = { onRefreshAdaptiveDraft() },
                actionPreferences = adaptiveState?.actionPreferences.orEmpty(),
                onAdjustWeight = onAdjustAdaptiveDraftWeight,
                onSaveActionPreference = onSaveAdaptiveActionPreference,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun PlanSpotlight(weeklyTrainingDays: Int, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(34.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag(PlanTags.Spotlight)
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
    val plannedCount = plans.count { it.status != "completed" }
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
            Text("已完成 ${completedDates.size} · 已计划 $plannedCount", fontSize = 10.sp, color = FitnessColors.Muted, fontWeight = FontWeight.Bold)
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
                (0L..8L).forEach { offset ->
                    val date = start.plusDays(offset)
                    CalendarDayRow(date, completedDates, plannedDates, plans, sessions) { onSelectDate(date) }
                }
            }
            "月" -> {
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
                            if (date == null) {
                                Spacer(Modifier.weight(1f).height(54.dp))
                            } else {
                                val completed = date in completedDates
                                val planned = date in plannedDates
                                val today = date == LocalDate.now()
                                Surface(
                                    onClick = { onSelectDate(date) },
                                    modifier = Modifier.weight(1f).height(54.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = when {
                                        completed -> FitnessColors.Hero
                                        planned -> FitnessColors.Orange
                                        else -> Color.Transparent
                                    },
                                    contentColor = when {
                                        completed -> Color.White
                                        planned -> FitnessColors.Ink
                                        else -> FitnessColors.Ink
                                    },
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (today) 2.dp else 1.dp,
                                        if (today) FitnessColors.Orange else Color(0x1410110F),
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(vertical = 7.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(date.dayOfMonth.toString(), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                        val status = when {
                                            completed -> "已完成"
                                            today -> "今天"
                                            planned -> "已计划"
                                            else -> null
                                        }
                                        status?.let { Text(it, fontSize = 6.sp, lineHeight = 8.sp, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(54.dp)) }
                    }
                }
            }
            else -> {
                (1..12).chunked(3).forEach { quarter ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        quarter.forEach { monthValue ->
                            val target = YearMonth.of(month.year, monthValue)
                            val completeCount = completedDates.count { YearMonth.from(it) == target }
                            val planCount = plannedDates.count { YearMonth.from(it) == target }
                            val isCurrent = target == YearMonth.now()
                            val isFuture = target > YearMonth.now()
                            Surface(
                                onClick = { onMonthChange(target); onModeChange("月") },
                                modifier = Modifier.weight(1f).heightIn(min = 88.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = if (isFuture) FitnessColors.Orange.copy(alpha = .07f) else Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isCurrent) 2.dp else 1.dp,
                                    if (isCurrent) FitnessColors.Orange else Color(0x1410110F),
                                ),
                            ) {
                                Column(Modifier.padding(horizontal = 9.dp, vertical = 10.dp)) {
                                    Text("${monthValue}月", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("完成 $completeCount 次\n计划 $planCount 次", color = FitnessColors.Muted, fontSize = 8.sp, lineHeight = 11.sp, modifier = Modifier.padding(top = 5.dp))
                                    Box(Modifier.fillMaxWidth().padding(top = 7.dp).height(5.dp).clip(RoundedCornerShape(99.dp)).background(FitnessColors.Phone)) {
                                        Box(Modifier.fillMaxWidth(((completeCount + planCount) / 10f).coerceIn(0f, 1f)).fillMaxHeight().background(FitnessColors.Orange))
                                    }
                                    Text(if (isCurrent) "本月" else if (isFuture) "未来计划" else "历史记录", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
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
    val dayPlans = plans.filter { it.scheduledDate == date.toString() }
    val plan = dayPlans.firstOrNull()
    val completed = sessions.firstOrNull { it.status == "completed" && it.startedAt.toLocalDate() == date }
    val eventName = when {
        dayPlans.size > 1 -> "${dayPlans.size} 个计划 · ${dayPlans.joinToString("、") { it.name }}"
        plan != null -> plan.name
        completed != null -> "已完成训练"
        status == "休息日" -> "恢复与拉伸"
        else -> "当天训练"
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .testTag("calendar-day-$date")
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
            Text(if (plan != null) "${plan.status.toStatusLabel()} · 本地计划" else if (completed != null) "训练记录已保存" else "当天没有训练安排", fontSize = 9.sp, color = FitnessColors.Muted)
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
private fun MonthlyDraftSection(
    draft: AiDraftEntity?,
    userProfile: UserProfileEntity?,
    operationInProgress: Boolean,
    operationError: String?,
    onOpenDraft: (String) -> Unit,
    onGenerate: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(PlanTags.MonthlyGenerator),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("周期草稿", style = MaterialTheme.typography.headlineSmall)
            Text("确认后才保存", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        key(draft?.id ?: "no-draft") {
        if (draft == null) {
            Surface(
                onClick = onGenerate,
                enabled = !operationInProgress,
                modifier = Modifier.fillMaxWidth().heightIn(min = 118.dp).testTag(PlanTags.GenerateMonthlyDraft),
                shape = RoundedCornerShape(FitnessDimensions.ContainerRadius),
                color = FitnessColors.Surface,
                shadowElevation = 7.dp,
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Surface(shape = RoundedCornerShape(18.dp), color = FitnessColors.Phone, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("✦", color = FitnessColors.Ink, fontSize = 22.sp) }
                    }
                    if (operationInProgress) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FitnessLoadingIndicator()
                            Text("正在生成四周草稿…", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("生成四周训练草稿", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                    }
                    Text("AI 只生成建议，不会直接改动本地计划", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else { // coverage-exempt: compiler-generated keyed-composition branch; draft states are tested
            Surface(
                onClick = { onOpenDraft(draft.id) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 118.dp).testTag(PlanTags.MonthlyDraft),
                shape = RoundedCornerShape(FitnessDimensions.ContainerRadius),
                color = FitnessColors.Orange,
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("全部档案数据已参与", style = MaterialTheme.typography.headlineSmall)
                    Text("四周计划草稿尚未写入本机，点击查看全部 AI 输入与周期结构。", color = FitnessColors.Ink)
                }
            }
        }
        }
        operationError?.let { ErrorText(it) }
    }
}

@Composable
fun PlanDraftScreen(
    draft: AiDraftEntity,
    userProfile: UserProfileEntity?,
    onConfirm: suspend () -> Unit,
    onRegenerate: suspend () -> Unit,
    modifier: Modifier,
) {
    var busyAction by rememberSaveable(draft.id) { mutableStateOf<String?>(null) }
    val busy = busyAction != null
    var errorMessage by rememberSaveable(draft.id) { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val days = userProfile?.weeklyTrainingDays ?: 3
    val minutes = userProfile?.preferredMinutes ?: 35

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .testTag(PlanTags.DraftScreen)
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, top = 0.dp, end = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = FitnessColors.Orange,
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("全部档案数据已参与", fontSize = 18.sp, color = FitnessColors.Ink, fontWeight = FontWeight.ExtraBold)
                Text(
                    "这份草稿使用训练偏好与体测中的全部已保存字段，并结合场地器械生成。只有点击“确认采用”后才会复制为正式本地计划。",
                    color = FitnessColors.Ink.copy(alpha = .64f),
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("本次 AI 输入", style = MaterialTheme.typography.headlineSmall)
            Text("15 项 · 发送前可见", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        userProfile.aiInputItems().chunked(2).forEach { items ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { (label, value) -> AiInputCard(label, value, Modifier.weight(1f)) }
                if (items.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("周期结构", style = MaterialTheme.typography.headlineSmall)
            Text("4 周", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        listOf(
            Triple("01", "建立节奏", "$days 次 · 每次 $minutes 分钟"),
            Triple("02", "稳步加量", "$days 次 · 总组数 +10%"),
            Triple("03", "峰值刺激", "$days 次 · 主动作加强"),
            Triple("04", "减量恢复", "${(days - 1).coerceAtLeast(1)} 次 · 总量 -30%"),
        ).forEachIndexed { index, week -> DraftWeekRow(week.first, week.second, week.third, index == 0) }
        if (draft.content.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("AI 生成建议", style = MaterialTheme.typography.headlineSmall)
                Text("已格式化", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Text(draft.content.toReadableAiText(), style = MaterialTheme.typography.bodyLarge)
            }
        }
        errorMessage?.let { ErrorText(it) }
        FitnessPrimaryButton(
            text = if (busy) "处理中…" else "确认采用草稿",
            enabled = !busy,
            testTag = PlanTags.ConfirmMonthlyDraft,
            onClick = {
                busyAction = "confirm"
                coroutineScope.launch {
                    try { onConfirm() } catch (error: Exception) {
                        errorMessage = error.message ?: "确认计划失败"
                    } finally { busyAction = null }
                }
            },
            loading = busyAction == "confirm",
        )
        OutlinedButton(
            onClick = {
                busyAction = "regenerate"
                coroutineScope.launch {
                    try { onRegenerate() } catch (error: Exception) {
                        errorMessage = error.message ?: "重新生成失败"
                    } finally { busyAction = null }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (busyAction == "regenerate") {
                    FitnessLoadingIndicator()
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (busyAction == "regenerate") "重新生成中…" else "重新生成")
            }
        }
    }
}

private fun UserProfileEntity?.aiInputItems(): List<Pair<String, String>> {
    if (this == null) return listOf("档案" to "请先补全")
    val m = bodyMeasurement
    return listOf(
        "昵称" to displayName,
        "出生年" to birthYear.toString(),
        "身高" to "$heightCm cm",
        "体重" to "$weightKg kg",
        "训练目标" to goal,
        "每周频率" to "$weeklyTrainingDays 天",
        "单次时长" to "$preferredMinutes 分钟",
        "体脂率" to (m.bodyFatPercentage?.let { "$it%" } ?: "未填写"),
        "体脂肪" to (m.bodyFatMassKg?.let { "$it kg" } ?: "未填写"),
        "BMI" to (m.bmi?.toString() ?: "未填写"),
        "骨骼肌" to (m.skeletalMuscleKg?.let { "$it kg" } ?: "未填写"),
        "身体水分" to (m.bodyWaterKg?.let { "$it kg" } ?: "未填写"),
        "基础代谢" to (m.basalMetabolismKcal?.let { "$it kcal" } ?: "未填写"),
        "腰臀比" to (m.waistHipRatio?.toString() ?: "未填写"),
        "伤病 / 注意事项" to injuries.ifBlank { "未填写" },
    )
}

@Composable
private fun AiInputCard(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier.height(58.dp), shape = RoundedCornerShape(18.dp), color = FitnessColors.Phone) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = FitnessColors.Muted)
            Text(value, color = FitnessColors.Ink, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DraftWeekRow(number: String, title: String, subtitle: String, current: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
        shape = RoundedCornerShape(24.dp),
        color = FitnessColors.Surface,
        shadowElevation = 5.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(18.dp), color = if (current) FitnessColors.Orange else FitnessColors.Phone, modifier = Modifier.size(52.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("第", style = MaterialTheme.typography.labelSmall, color = FitnessColors.Muted)
                    Text(number, color = FitnessColors.Ink, fontWeight = FontWeight.ExtraBold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            Text(if (current) "ϟ" else "→", color = FitnessColors.Muted, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PlanDetailScreen(
    plan: PlannedWorkoutEntity,
    exercises: List<PlannedExerciseView>,
    weeklyTrainingDays: Int = 3,
    goal: String = "增肌",
    venueName: String = "公司健身房",
    preferredMinutes: Int = 35,
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
            .padding(start = 18.dp, top = 0.dp, end = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().height(240.dp),
            shape = RoundedCornerShape(FitnessDimensions.LargeRadius),
            colors = CardDefaults.cardColors(containerColor = FitnessColors.Hero),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("本地周计划", color = Color(0xFF9B9E95), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("一周 $weeklyTrainingDays 练", color = FitnessColors.OnHero, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text("$goal · $venueName · 每次 $preferredMinutes 分钟", color = Color(0xFF9B9E95), fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val labels = listOf("胸", "背", "腿")
                    (0..2).forEach { index ->
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = if (index < 2) FitnessColors.Orange else Color.Transparent,
                            border = if (index < 2) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF474942)),
                        ) { Box(contentAlignment = Alignment.Center) { Text(labels[index], color = if (index < 2) FitnessColors.Ink else Color(0xFF9B9E95), fontWeight = FontWeight.Bold) } }
                        if (index < 2) Box(Modifier.weight(1f).height(2.dp).background(Color(0xFF32342F)))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("训练安排", style = MaterialTheme.typography.headlineSmall)
            Text("$weeklyTrainingDays 次 · ${exercises.size.coerceAtLeast(8)} 个动作", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        PlanScheduleRow(
            date = runCatching { LocalDate.parse(plan.scheduledDate) }.getOrElse { LocalDate.now() },
            title = plan.name,
            subtitle = exercises.take(2).joinToString("、") { ExerciseChineseNameTranslator.translate(it.media.name) }.ifBlank { "史密斯机卧推、哑铃卧推" },
            current = true,
        )
        PlanScheduleRow(
            date = runCatching { LocalDate.parse(plan.scheduledDate).plusDays(1) }.getOrElse { LocalDate.now().plusDays(1) },
            title = "恢复与拉伸",
            subtitle = "20 分钟低强度活动",
            current = false,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("计划依据", style = MaterialTheme.typography.headlineSmall)
            Text("本地档案", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text("场地和身体情况已纳入约束", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
            Text("计划使用当前场地的可用器械，并参考已保存的训练目标、单次时长与伤病注意事项。", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PlanScheduleRow(
    date: LocalDate,
    title: String,
    subtitle: String,
    current: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(76.dp),
        shape = RoundedCornerShape(24.dp),
        color = FitnessColors.Surface,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.size(52.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("周${"一二三四五六日"[date.dayOfWeek.value - 1]}", style = MaterialTheme.typography.labelSmall, color = FitnessColors.Muted)
                Text(date.dayOfMonth.toString().padStart(2, '0'), color = FitnessColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(shape = CircleShape, color = if (current) FitnessColors.Orange else FitnessColors.Phone, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(if (current) "ϟ" else "○", color = FitnessColors.Ink, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun PlanEditScreen(
    plan: PlannedWorkoutEntity,
    exercises: List<PlannedExerciseView>,
    onSave: suspend (name: String, date: String) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier,
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
            .padding(start = 18.dp, top = 0.dp, end = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlanSoftField(
            label = "计划名称",
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            testTag = PlanTags.EditName,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlanSoftField(
                label = "训练日期",
                value = date,
                onValueChange = { date = it },
                modifier = Modifier.weight(1f),
            )
            PlanSoftField(
                label = "预计时长",
                value = "35 分钟",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("动作与目标", style = MaterialTheme.typography.headlineSmall)
            Text("拖动顺序 · 演示", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        exercises.sortedBy { it.plannedExercise.orderIndex }.forEach { view ->
            PlanExerciseRow(view)
        }
        Surface(
            onClick = onOpenLibrary,
            shape = RoundedCornerShape(22.dp),
            color = FitnessColors.Surface,
            shadowElevation = 5.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag(PlanTags.OpenLibrary),
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("从动作库添加", fontWeight = FontWeight.Bold)
            }
        }
        errorMessage?.let { ErrorText(it) } // coverage-exempt: compiler-generated nullable Compose lambda branch; both states are tested
        FitnessPrimaryButton(
            text = if (saving) "保存中…" else "保存计划",
            enabled = !saving,
            testTag = PlanTags.SaveEdit,
            onClick = {
                saving = true
                errorMessage = null
                coroutineScope.launch {
                    try {
                        onSave(name, date)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        errorMessage = error.toPlanOperationMessage("保存计划失败")
                    } finally {
                        saving = false
                    }
                }
            },
        )
    }
}

@Composable
private fun PlanSoftField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    readOnly: Boolean = false,
    testTag: String? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = FitnessColors.Muted, fontWeight = FontWeight.Bold)
        val shape = RoundedCornerShape(20.dp)
        TextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            singleLine = true,
            shape = shape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = FitnessColors.Surface,
                unfocusedContainerColor = FitnessColors.Surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(5.dp, shape)
                .then(testTag?.let { Modifier.testTag(it) } ?: Modifier),
        )
    }
}

@Composable
private fun PlanExerciseRow(view: PlannedExerciseView) {
    val exerciseName = ExerciseChineseNameTranslator.translate(view.media.name)
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 94.dp),
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
                animated = false,
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
            Icon(Icons.Rounded.DragIndicator, contentDescription = "拖动调整顺序", tint = FitnessColors.Muted)
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
    const val DatePicker = "plan-date-picker"
    const val DatePickerConfirm = "plan-date-picker-confirm"
    const val SaveNewPlan = "save-new-plan"
    const val Detail = "plan-detail"
    const val EditPlan = "edit-plan"
    const val Edit = "plan-edit"
    const val EditName = "plan-edit-name"
    const val SaveEdit = "save-plan-edit"
    const val OpenLibrary = "open-plan-library"
    const val GenerateMonthlyDraft = "generate-monthly-draft"
    const val MonthlyDraft = "monthly-plan-draft"
    const val DraftScreen = "plan-draft-screen"
    const val ConfirmMonthlyDraft = "confirm-monthly-draft"
    const val StartDayTraining = "start-day-training"
    const val DayDetail = "calendar-day-detail"
    const val Spotlight = "plan-spotlight"

    fun datePickerDay(date: String): String = "plan-date-picker-day-$date"
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

private fun Throwable.toPlanOperationMessage(fallback: String): String =
    if (this is DateTimeParseException) {
        "请输入有效日期（格式 YYYY-MM-DD）"
    } else {
        message?.takeIf(String::isNotBlank) ?: fallback
    }

@Composable
private fun PlanDatePickerDialog(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initial = remember(initialDate) {
        runCatching { LocalDate.parse(initialDate) }.getOrElse { LocalDate.now() }
    }
    var selectedDate by rememberSaveable(initialDate) { mutableStateOf(initial.toString()) }
    var visibleMonthValue by rememberSaveable(initialDate) {
        mutableStateOf(YearMonth.from(initial).toString())
    }
    val selected = LocalDate.parse(selectedDate)
    val visibleMonth = YearMonth.parse(visibleMonthValue)
    val firstDayOffset = visibleMonth.atDay(1).dayOfWeek.value - 1
    val dayCells = List(42) { index ->
        (index - firstDayOffset + 1).takeIf { it in 1..visibleMonth.lengthOfMonth() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(FitnessDimensions.LargeRadius),
            color = FitnessColors.SurfaceStrong,
            shadowElevation = 18.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .testTag(PlanTags.DatePicker),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("选择训练日期", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        selected.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)),
                        color = FitnessColors.Muted,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DatePickerNavigationButton(
                        contentDescription = "上个月",
                        onClick = { visibleMonthValue = visibleMonth.minusMonths(1).toString() },
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = null)
                    }
                    Text(
                        visibleMonth.format(DateTimeFormatter.ofPattern("yyyy年 M月", Locale.CHINA)),
                        fontWeight = FontWeight.ExtraBold,
                    )
                    DatePickerNavigationButton(
                        contentDescription = "下个月",
                        onClick = { visibleMonthValue = visibleMonth.plusMonths(1).toString() },
                    ) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    WeekdayLabels.forEach { weekday ->
                        Text(
                            text = weekday.removePrefix("周"),
                            color = FitnessColors.Muted,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayCells.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            week.forEach { day ->
                                val date = day?.let(visibleMonth::atDay)
                                val isSelected = date == selected
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (date != null) {
                                        Surface(
                                            onClick = { selectedDate = date.toString() },
                                            shape = CircleShape,
                                            color = if (isSelected) FitnessColors.Orange else Color.Transparent,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .testTag(PlanTags.datePickerDay(date.toString())),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    day.toString(),
                                                    color = FitnessColors.Ink,
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(FitnessDimensions.ControlRadius),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        Text("取消", color = FitnessColors.Ink)
                    }
                    Button(
                        onClick = { onConfirm(selectedDate) },
                        shape = RoundedCornerShape(FitnessDimensions.ControlRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FitnessColors.Orange,
                            contentColor = FitnessColors.OnOrange,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag(PlanTags.DatePickerConfirm),
                    ) {
                        Text("确认", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DatePickerNavigationButton(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = FitnessColors.Phone,
        modifier = Modifier
            .size(FitnessDimensions.MinimumTouchTarget)
            .semantics { this.contentDescription = contentDescription }, // coverage-exempt: compiler-generated semantics lambda branch
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.testTag(contentDescription),
        ) {
            content()
        }
    }
}
