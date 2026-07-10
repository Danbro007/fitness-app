package com.shanqijie.fitnessapp.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shanqijie.fitnessapp.data.AiDraftEntity
import com.shanqijie.fitnessapp.data.PlannedWorkoutEntity
import com.shanqijie.fitnessapp.data.PlannedExerciseView
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
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    plans: List<PlannedWorkoutEntity>,
    plannedExerciseViews: List<PlannedExerciseView>,
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
    val coroutineScope = rememberCoroutineScope()
    val exerciseCountByPlan = plannedExerciseViews.groupingBy { it.plannedExercise.plannedWorkoutId }.eachCount()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .testTag(PlanTags.Screen)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        FitnessPageHeader(
            title = "计划",
            kicker = "先看本周，再安排下一步",
            action = {
                Button(
                    onClick = {
                        operationError = null
                        showNewPlanEditor = true
                    },
                    modifier = Modifier
                        .heightIn(min = FitnessDimensions.MinimumTouchTarget)
                        .testTag(PlanTags.NewPlan),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FitnessColors.Orange,
                        contentColor = FitnessColors.OnOrange,
                    ),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text("新计划")
                }
            },
        )

        WeeklySchedule(
            plans = plans,
            exerciseCountByPlan = exerciseCountByPlan,
            onOpenPlan = onOpenPlan,
        )

        MonthlyDraftSection(
            draft = activeMonthlyDraft,
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
                Text("创建训练计划", style = MaterialTheme.typography.headlineSmall)
                Text("确认后才会保存到这台设备。", style = MaterialTheme.typography.bodyMedium)
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
                    label = { Text("训练日期（YYYY-MM-DD）") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PlanTags.DateInput),
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
}

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
        Text("本周日程", style = MaterialTheme.typography.headlineSmall)
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
        Text("四周计划草稿", style = MaterialTheme.typography.headlineSmall)
        Text(
            "先生成一份周计划草稿。确认后复制为 4 周，确认前不会新增正式计划。",
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
                Text(if (operationInProgress) "生成中…" else "生成四周草稿")
            }
        } else {
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
                Text("确认后复制为 4 周", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
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
        FitnessPageHeader(plan.name, kicker = plan.scheduledDate)
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .testTag(PlanTags.Edit)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FitnessPageHeader("编辑计划", kicker = "修改后保存到本地")
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
            label = { Text("训练日期") },
            modifier = Modifier.fillMaxWidth(),
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
