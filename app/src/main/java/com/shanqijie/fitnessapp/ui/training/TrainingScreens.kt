package com.shanqijie.fitnessapp.ui.training

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanqijie.fitnessapp.data.AiDraftEntity
import com.shanqijie.fitnessapp.domain.toReadableAiText
import com.shanqijie.fitnessapp.domain.WorkoutSummary
import com.shanqijie.fitnessapp.domain.WorkoutAdjustmentDirection
import com.shanqijie.fitnessapp.domain.EquipmentAvailabilityScope
import com.shanqijie.fitnessapp.domain.WorkoutEarlyFinishReason
import com.shanqijie.fitnessapp.domain.workoutReviewMetadata
import com.shanqijie.fitnessapp.ui.components.FitnessGifImage
import com.shanqijie.fitnessapp.ui.components.FitnessFloatingBottomDialog
import com.shanqijie.fitnessapp.ui.components.FitnessMetricCard
import com.shanqijie.fitnessapp.ui.components.FitnessPrimaryButton
import com.shanqijie.fitnessapp.ui.components.FitnessSurfaceCard
import com.shanqijie.fitnessapp.ui.navigation.FitnessTestTags
import com.shanqijie.fitnessapp.ui.theme.FitnessColors
import com.shanqijie.fitnessapp.ui.theme.FitnessDimensions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

data class TrainingExerciseScreenUi(
    val sessionExerciseId: String,
    val exerciseId: String,
    val name: String,
    val detail: String,
    val assetPath: String,
    val targetSets: Int,
    val targetReps: String,
    val targetWeightKg: Double,
    val completedSets: Int,
)

data class TrainingPreparationScreenUi(
    val planId: String,
    val planName: String,
    val estimatedMinutes: Int,
    val exercises: List<TrainingExerciseScreenUi>,
)

data class TrainingActiveScreenUi(
    val sessionId: String,
    val planName: String,
    val startedAt: Long = 0L,
    val pausedAt: Long? = null,
    val currentExerciseId: String,
    val restEndsAt: Long?,
    val exercises: List<TrainingExerciseScreenUi>,
) {
    val currentExercise: TrainingExerciseScreenUi
        get() = exercises.first { it.exerciseId == currentExerciseId }
}

internal data class ParsedTrainingInputs(
    val reps: Int,
    val weightKg: Double,
    val valid: Boolean,
)

internal fun initialRepsInput(targetReps: String): String =
    (targetReps.substringBefore('-').toIntOrNull()?.coerceIn(1, 50) ?: 8).toString()

internal fun parseTrainingInputs(repsInput: String, weightInput: String): ParsedTrainingInputs {
    val parsedReps = repsInput.toIntOrNull()
    val parsedWeight = weightInput.toDoubleOrNull()
    return ParsedTrainingInputs(
        reps = parsedReps?.coerceIn(1, 50) ?: 1,
        weightKg = parsedWeight?.coerceIn(0.0, 500.0) ?: 0.0,
        valid = parsedReps != null && parsedReps in 1..50 &&
            parsedWeight != null && parsedWeight in 0.0..500.0,
    )
}

internal fun normalizeStepperCandidate(candidate: String, decimal: Boolean, maximum: Double): String? {
    val normalized = candidate.replace(',', '.')
    val validFormat = if (decimal) {
        normalized.matches(Regex("\\d{0,3}(\\.\\d?)?"))
    } else {
        normalized.matches(Regex("\\d{0,2}"))
    }
    val parsed = normalized.toDoubleOrNull()
    return normalized.takeIf { validFormat && (parsed == null || parsed <= maximum) }
}

@Composable
fun TrainingPreparationScreen(
    state: TrainingPreparationScreenUi?,
    onStartWorkout: (String) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .testTag(FitnessTestTags.TrainingPrep)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("训练准备 · 今日", color = FitnessColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(state?.planName ?: "先安排一次训练", style = MaterialTheme.typography.headlineLarge)
            }
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = FitnessColors.Surface,
                shadowElevation = 8.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(FitnessColors.Ink))
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().height(196.dp),
            shape = RoundedCornerShape(FitnessDimensions.LargeRadius),
            colors = CardDefaults.cardColors(containerColor = FitnessColors.Hero),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("准备就绪", color = Color(0xFF9B9E95), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${state?.exercises?.size ?: 0} 个动作", color = FitnessColors.OnHero, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        state?.let { "目标 ${it.exercises.sumOf { exercise -> exercise.targetSets }} 组 · 预计 ${it.estimatedMinutes} 分钟" }
                            ?: "完成计划后显示训练清单",
                        color = Color(0xFF9B9E95),
                        fontSize = 13.sp,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PreparationNode("热身", active = true)
                    Box(Modifier.weight(1f).height(2.dp).background(Color(0xFF32342F)))
                    PreparationNode("训练", active = false)
                    Box(Modifier.weight(1f).height(2.dp).background(Color(0xFF32342F)))
                    PreparationNode("保存", active = false)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("动作顺序", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text("可在训练中切换", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }

        state?.exercises?.forEachIndexed { index, exercise ->
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = FitnessColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(62.dp).clip(RoundedCornerShape(20.dp)).background(FitnessColors.Phone),
                        contentAlignment = Alignment.Center,
                    ) {
                        FitnessGifImage(
                            assetPath = exercise.assetPath,
                            contentDescription = exercise.name,
                            modifier = Modifier.fillMaxSize(),
                            animated = false,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(exercise.name, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${exercise.targetSets} 组 × ${exercise.targetReps} 次 · ${exercise.targetWeightKg.asWeight()} kg",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text((index + 1).toString().padStart(2, '0'), color = FitnessColors.Muted, fontWeight = FontWeight.Bold)
                }
            }
        }

        val startWorkout = state?.let { current ->
            { onStartWorkout(current.planId) }
        } ?: {}
        FitnessPrimaryButton(
            text = if (state == null) "暂无可开始训练" else "开始训练",
            onClick = startWorkout,
            enabled = state != null,
            testTag = FitnessTestTags.StartWorkout,
        )
    }
}

@Composable
private fun PreparationNode(label: String, active: Boolean) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = if (active) FitnessColors.Orange else Color.Transparent,
        border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF474942)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (active) FitnessColors.Ink else Color(0xFF9B9E95), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingActiveScreen(
    state: TrainingActiveScreenUi,
    onSelectExercise: (String) -> Unit,
    onRecordSet: suspend (reps: Int, weightKg: Double, feeling: String) -> Unit,
    onRestFinished: () -> Unit,
    onSkipRest: () -> Unit,
    onExtendRest: () -> Unit,
    onTogglePause: () -> Unit,
    onFinishWorkout: () -> Unit,
    onFinishWorkoutWithFeedback: suspend (WorkoutEarlyFinishReason?, String, EquipmentAvailabilityScope?) -> Unit = { _, _, _ -> onFinishWorkout() },
    modifier: Modifier,
) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity().window
        val controller = WindowCompat.getInsetsController(window, view)
        val previousStatusBarColor = window.statusBarColor
        val previousNavigationBarColor = window.navigationBarColor
        val previousLightStatusBars = controller.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller.isAppearanceLightNavigationBars
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = FitnessColors.Hero.toArgb()
        window.navigationBarColor = FitnessColors.Hero.toArgb()
        window.decorView.setBackgroundColor(FitnessColors.Hero.toArgb())
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            window.decorView.setBackgroundColor(FitnessColors.Phone.toArgb())
            window.statusBarColor = previousStatusBarColor
            window.navigationBarColor = previousNavigationBarColor
            controller.isAppearanceLightStatusBars = previousLightStatusBars
            controller.isAppearanceLightNavigationBars = previousLightNavigationBars
        }
    }
    val current = state.currentExercise
    var repsInput by rememberSaveable(current.exerciseId) {
        mutableStateOf(initialRepsInput(current.targetReps))
    }
    var weightInput by rememberSaveable(current.exerciseId) { mutableStateOf(current.targetWeightKg.asWeight()) }
    val parsedInputs = parseTrainingInputs(repsInput, weightInput)
    val reps = parsedInputs.reps
    val weightKg = parsedInputs.weightKg
    val numericInputsValid = parsedInputs.valid
    var feeling by rememberSaveable(current.exerciseId) { mutableStateOf(WorkoutFeelings[1]) }
    var showFinishDialog by rememberSaveable { mutableStateOf(false) }
    var earlyFinishReason by rememberSaveable { mutableStateOf(WorkoutEarlyFinishReason.TIME_LIMIT.name) }
    var equipmentScope by rememberSaveable { mutableStateOf(EquipmentAvailabilityScope.TEMPORARY.name) }
    var finishNote by rememberSaveable { mutableStateOf("") }
    var finishError by rememberSaveable { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isFinishing by remember { mutableStateOf(false) }
    var submittedCompletedSets by remember { mutableStateOf<Int?>(null) }
    var recordError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val restEndsAt = state.restEndsAt
    val resting = restEndsAt != null
    val isPaused = state.pausedAt != null
    val totalTargetSets = state.exercises.sumOf { it.targetSets }.coerceAtLeast(1)
    val totalCompletedSets = state.exercises.sumOf { it.completedSets }
    val allSetsCompleted = totalCompletedSets >= totalTargetSets

    fun finishWithFeedback() {
        if (isFinishing) return
        isFinishing = true
        finishError = null
        val reason = if (allSetsCompleted) null else runCatching { WorkoutEarlyFinishReason.valueOf(earlyFinishReason) }.getOrDefault(WorkoutEarlyFinishReason.TIME_LIMIT)
        val scope = if (reason == WorkoutEarlyFinishReason.EQUIPMENT_UNAVAILABLE) {
            runCatching { EquipmentAvailabilityScope.valueOf(equipmentScope) }.getOrDefault(EquipmentAvailabilityScope.TEMPORARY)
        } else null
        coroutineScope.launch {
            runCatching { onFinishWorkoutWithFeedback(reason, finishNote, scope) }
                .onFailure { error ->
                    finishError = error.message ?: "保存训练反馈失败"
                    isFinishing = false
                    if (!allSetsCompleted) showFinishDialog = true
                }
        }
    }

    LaunchedEffect(current.exerciseId, current.completedSets, state.restEndsAt) {
        val submittedSets = submittedCompletedSets
        if (
            isRecording && submittedSets != null &&
            (current.completedSets > submittedSets || state.restEndsAt != null)
        ) {
            isRecording = false
            submittedCompletedSets = null
        }
    }

    BackHandler { showFinishDialog = true }

    if (resting) {
        Box(modifier = modifier.fillMaxSize().testTag(FitnessTestTags.TrainingActive)) {
            RestPanel(
                restEndsAt = restEndsAt,
                exerciseName = current.name,
                weightKg = weightKg,
                reps = reps,
                startedAt = state.startedAt,
                onExtendRest = onExtendRest,
                onRestFinished = onRestFinished,
                onSkipRest = onSkipRest,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(FitnessTestTags.TrainingActive),
        containerColor = FitnessColors.Hero,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FitnessColors.Hero)
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = { showFinishDialog = true },
                    modifier = Modifier.size(52.dp).testTag(FitnessTestTags.RequestFinish),
                    shape = CircleShape,
                    color = Color(0xFF1A1C18),
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("×", color = Color.White, fontSize = 28.sp) }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("训练用时", color = Color(0xFF8F9189), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    TrainingElapsedText(startedAt = state.startedAt, pausedAt = state.pausedAt)
                }
                Surface(
                    onClick = onTogglePause,
                    modifier = Modifier
                        .size(52.dp)
                        .semantics { contentDescription = if (isPaused) "继续训练" else "暂停训练" },
                    shape = CircleShape,
                    color = if (isPaused) FitnessColors.Orange else Color(0xFF1A1C18),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (isPaused) "▶" else "Ⅱ", color = if (isPaused) FitnessColors.Ink else Color.White, fontSize = 20.sp)
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FitnessColors.Hero)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                recordError?.let { message ->
                    Text(
                        text = message,
                        color = WorkoutOnErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(WorkoutErrorContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                FitnessPrimaryButton(
                    text = when {
                        allSetsCompleted && isFinishing -> "正在整理训练总结…"
                        allSetsCompleted -> "完成训练并查看 AI 总结"
                        resting -> "休息中"
                        isRecording -> "保存中…"
                        else -> "完成本组"
                    },
                    onClick = {
                        if (allSetsCompleted) {
                            if (!isFinishing) {
                                finishWithFeedback()
                            }
                        } else if (!isRecording) {
                            isRecording = true
                            submittedCompletedSets = current.completedSets
                            recordError = null
                            coroutineScope.launch {
                                try {
                                    onRecordSet(reps, weightKg, feeling)
                                } catch (cancellation: CancellationException) {
                                    throw cancellation
                                } catch (error: Exception) {
                                    recordError = error.message ?: "保存训练组失败，请重试"
                                    isRecording = false
                                    submittedCompletedSets = null
                                }
                            }
                        }
                    },
                    enabled = if (allSetsCompleted) {
                        !isPaused && !isFinishing
                    } else {
                        numericInputsValid && !resting && !isPaused && !isRecording && current.completedSets < current.targetSets
                    },
                    testTag = FitnessTestTags.CompleteSet,
                )
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(
                    progress = { (totalCompletedSets.toFloat() / totalTargetSets).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(99.dp)),
                    color = FitnessColors.Orange,
                    trackColor = Color(0xFF292A25),
                )
                Text("$totalCompletedSets/$totalTargetSets 组", color = Color(0xFF9A9C94), fontSize = 11.sp)
            }

            if (isPaused) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = FitnessColors.Orange,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "训练已暂停 · 点击右上角继续",
                        color = FitnessColors.Ink,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(current.name, color = FitnessColors.OnHero, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (current.completedSets >= current.targetSets) {
                        "动作 ${state.exercises.indexOfFirst { it.exerciseId == current.exerciseId } + 1}/${state.exercises.size} · 本动作已完成"
                    } else {
                        "动作 ${state.exercises.indexOfFirst { it.exerciseId == current.exerciseId } + 1}/${state.exercises.size} · 当前第 ${current.completedSets + 1} 组"
                    },
                    color = FitnessColors.OnHero.copy(alpha = 0.58f),
                    fontSize = 12.sp,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(FitnessDimensions.LargeRadius))
                    .background(FitnessColors.Phone),
            ) {
                FitnessGifImage(
                    assetPath = current.assetPath,
                    contentDescription = current.name,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    text = "本地动作动图",
                    color = FitnessColors.OnHero,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.Black.copy(alpha = 0.68f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 10.sp,
                )
            }

            if (allSetsCompleted) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FitnessDimensions.LargeRadius),
                    color = Color(0xFF2A2C28),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "全部训练组已完成",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "本次重量、次数和体感已保存，下一步查看 AI 训练总结。",
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 12.sp,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Stepper(
                        label = "重量 · 公斤",
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        decimal = true,
                        maximum = 500.0,
                        inputTag = "training-weight-input",
                        decreaseDescription = "减少重量",
                        increaseDescription = "增加重量",
                        onDecrease = { weightInput = max(0.0, weightKg - 2.5).asWeight() },
                        onIncrease = { weightInput = (weightKg + 2.5).coerceAtMost(500.0).asWeight() },
                        modifier = Modifier.weight(1f),
                    )
                    Stepper(
                        label = "次数",
                        value = repsInput,
                        onValueChange = { repsInput = it },
                        decimal = false,
                        maximum = 50.0,
                        inputTag = "training-reps-input",
                        decreaseDescription = "减少次数",
                        increaseDescription = "增加次数",
                        onDecrease = { repsInput = max(1, reps - 1).toString() },
                        onIncrease = { repsInput = (reps + 1).coerceAtMost(50).toString() },
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkoutFeelings.forEach { option ->
                        Surface(
                            onClick = { feeling = option },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = if (feeling == option) FitnessColors.Orange else Color(0xFF2A2C28),
                            contentColor = if (feeling == option) FitnessColors.Ink else Color.White,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    option,
                                    color = if (feeling == option) FitnessColors.Ink else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }

    if (showFinishDialog) {
        FitnessFloatingBottomDialog(
            onDismissRequest = { showFinishDialog = false },
            modifier = Modifier,
            containerColor = Color(0xFF1A1C18),
            contentColor = Color.White,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("现在结束训练？", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "已完成的 ${state.exercises.sumOf { it.completedSets }} 组会保存；未完成动作不会补记，也不会把部分训练计为整次完成。",
                    color = Color(0xFF9B9E95),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!allSetsCompleted) {
                    Text("提前结束原因", color = Color.White, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WorkoutEarlyFinishReason.entries.take(2).forEach { reason ->
                            Surface(
                                onClick = { earlyFinishReason = reason.name },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = if (earlyFinishReason == reason.name) FitnessColors.Orange else Color(0xFF2A2C28),
                                contentColor = if (earlyFinishReason == reason.name) FitnessColors.Ink else Color.White,
                            ) { Box(contentAlignment = Alignment.Center) { Text(reason.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) } }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WorkoutEarlyFinishReason.entries.drop(2).forEach { reason ->
                            Surface(
                                onClick = { earlyFinishReason = reason.name },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = if (earlyFinishReason == reason.name) FitnessColors.Orange else Color(0xFF2A2C28),
                                contentColor = if (earlyFinishReason == reason.name) FitnessColors.Ink else Color.White,
                            ) { Box(contentAlignment = Alignment.Center) { Text(reason.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) } }
                        }
                    }
                    if (earlyFinishReason == WorkoutEarlyFinishReason.EQUIPMENT_UNAVAILABLE.name) {
                        Text("器械范围", color = Color(0xFF9B9E95), fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EquipmentAvailabilityScope.entries.forEach { option ->
                                Surface(
                                    onClick = { equipmentScope = option.name },
                                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (equipmentScope == option.name) FitnessColors.Orange else Color(0xFF2A2C28),
                                    contentColor = if (equipmentScope == option.name) FitnessColors.Ink else Color.White,
                                ) { Box(contentAlignment = Alignment.Center) { Text(option.label, fontSize = 11.sp) } }
                            }
                        }
                    }
                    Text("补充说明（可选）", color = Color(0xFF9B9E95), fontSize = 12.sp)
                    Surface(color = Color(0xFF2A2C28), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                        BasicTextField(
                            value = finishNote,
                            onValueChange = { if (it.length <= 300) finishNote = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (finishNote.isBlank()) Text("例如：最后两组时间不够，或右肩出现不适", color = Color(0xFF9B9E95))
                                    inner()
                                }
                            },
                        )
                    }
                }
                finishError?.let { Text(it, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = { showFinishDialog = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(28.dp)),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) { Text("继续训练", color = Color.White, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = {
                        showFinishDialog = false
                        finishWithFeedback()
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .heightIn(min = FitnessDimensions.MinimumTouchTarget)
                        .testTag(FitnessTestTags.ConfirmFinish),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FitnessColors.Orange,
                        contentColor = FitnessColors.OnOrange,
                    ),
                ) {
                    Text("保存并结束")
                }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("训练进行页必须挂载在 Activity 上下文中")
}

@Composable
private fun TrainingElapsedText(startedAt: Long, pausedAt: Long?) {
    var now by remember(startedAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt, pausedAt) {
        if (pausedAt != null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val displayedNow = pausedAt ?: now
    Text(
        text = formatElapsedTime((displayedNow - startedAt).coerceAtLeast(0L)),
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun RestPanel(
    restEndsAt: Long,
    exerciseName: String,
    weightKg: Double,
    reps: Int,
    startedAt: Long,
    onExtendRest: () -> Unit,
    onRestFinished: () -> Unit,
    onSkipRest: () -> Unit,
    modifier: Modifier,
) {
    var now by remember(restEndsAt) { mutableLongStateOf(System.currentTimeMillis()) }
    var completionSent by remember(restEndsAt) { mutableStateOf(false) }

    LaunchedEffect(restEndsAt) {
        while (true) {
            val remainingMillis = restEndsAt - now
            if (remainingMillis <= 0L) break
            delay(minOf(remainingMillis, 1_000L))
            now = System.currentTimeMillis()
        }
        if (!completionSent) {
            completionSent = true
            onRestFinished()
        }
    }

    val remainingSeconds = ceil(max(0L, restEndsAt - now) / 1_000.0).toInt()
    Column(
        modifier = modifier
            .background(FitnessColors.Hero)
            .testTag(FitnessTestTags.RestPanel)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Color(0xFF1A1C18), modifier = Modifier.size(52.dp)) { Box(contentAlignment = Alignment.Center) { Text("×", color = Color.White, fontSize = 28.sp) } }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("训练用时", color = Color(0xFF9B9E95), fontSize = 10.sp)
                Text(formatElapsedTime((now - startedAt).coerceAtLeast(0L)), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Surface(
                onClick = onExtendRest,
                shape = CircleShape,
                color = Color(0xFF1A1C18),
                modifier = Modifier.semantics { contentDescription = "延长休息 30 秒" },
            ) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text("+30 秒", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            shape = RoundedCornerShape(34.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171915)),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(30.dp), color = Color(0xFF0E100D), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("组间休息", color = Color(0xFF9B9E95), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(remainingSeconds.toString(), color = Color.White, fontSize = 76.sp, lineHeight = 78.sp, fontWeight = FontWeight.ExtraBold)
                            Text("秒", color = Color(0xFF9B9E95), modifier = Modifier.padding(bottom = 14.dp))
                        }
                        LinearProgressIndicator(
                            progress = { (remainingSeconds / 90f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)),
                            color = FitnessColors.Orange,
                            trackColor = Color(0xFF272923),
                        )
                    }
                }
                Text("放松，下一组马上开始", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 30.dp))
                Text("下一组：$exerciseName · ${weightKg.asWeight()} kg × $reps", color = Color(0xFF9B9E95), fontSize = 13.sp)
                Surface(
                    onClick = onSkipRest,
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF242620),
                    modifier = Modifier.fillMaxWidth().height(58.dp).padding(top = 0.dp).testTag(FitnessTestTags.SkipRest),
                ) { Box(contentAlignment = Alignment.Center) { Text("跳过休息  →", color = Color.White, fontWeight = FontWeight.Bold) } }
            }
        }
    }
}

private fun formatElapsedTime(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    decimal: Boolean,
    maximum: Double,
    inputTag: String,
    decreaseDescription: String,
    increaseDescription: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier,
) {
    var inputFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val inputShape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(FitnessDimensions.ContainerRadius))
            .background(FitnessColors.OnHero.copy(alpha = 0.08f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = FitnessColors.OnHero.copy(alpha = 0.65f), fontSize = 12.sp)
        BasicTextField(
            value = value, // coverage-exempt: compiler-generated BasicTextField adapter branch
            onValueChange = { candidate ->
                normalizeStepperCandidate(candidate, decimal, maximum)?.let(onValueChange)
            },
            modifier = Modifier
                .widthIn(min = 84.dp)
                .height(42.dp)
                .onFocusChanged { inputFocused = it.isFocused }
                .clip(inputShape)
                .background(Color(0xFF171915))
                .border(
                    width = if (inputFocused) 1.5.dp else 1.dp,
                    color = if (inputFocused) FitnessColors.Orange else Color.White.copy(alpha = 0.2f),
                    shape = inputShape,
                )
                .testTag(inputTag)
                .semantics { contentDescription = "$label，点击输入数字" }, // coverage-exempt: compiler-generated semantics lambda branch
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            cursorBrush = SolidColor(FitnessColors.Orange),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (value.isBlank()) {
                        Text("输入", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                    }
                    innerTextField()
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onDecrease,
                modifier = Modifier
                    .size(FitnessDimensions.MinimumTouchTarget)
                    .semantics { contentDescription = decreaseDescription }, // coverage-exempt: compiler-generated semantics lambda branch
            ) {
                Text("−", color = FitnessColors.OnHero, fontSize = 24.sp)
            }
            IconButton(
                onClick = onIncrease,
                modifier = Modifier
                    .size(FitnessDimensions.MinimumTouchTarget)
                    .semantics { contentDescription = increaseDescription }, // coverage-exempt: compiler-generated semantics lambda branch
            ) {
                Text("+", color = FitnessColors.OnHero, fontSize = 24.sp)
            }
        }
    }
}

@Composable
fun WorkoutSummaryScreen(
    summary: WorkoutSummary,
    weeklyCompleted: Int,
    weeklyTarget: Int,
    onDone: () -> Unit,
    reviewDraft: AiDraftEntity?,
    onGenerateReview: suspend (postWorkoutFeeling: String, note: String) -> Unit,
    onResolveReview: suspend (draftId: String, applyAdjustment: Boolean) -> Unit,
    injuryReviewRequired: Boolean = false,
    onResolveInjuryReview: suspend (confirmedSafe: Boolean) -> Unit = {},
    modifier: Modifier,
) {
    var postWorkoutFeeling by rememberSaveable(summary.sessionId) { mutableStateOf("正常疲劳") }
    var postWorkoutNote by rememberSaveable(summary.sessionId) { mutableStateOf("") }
    var reviewBusy by rememberSaveable(summary.sessionId) { mutableStateOf(false) }
    var reviewError by rememberSaveable(summary.sessionId) { mutableStateOf<String?>(null) }
    var injuryReviewBusy by rememberSaveable(summary.sessionId) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val reviewMetadata = reviewDraft?.workoutReviewMetadata()
    val reviewDirection = reviewMetadata?.direction
        ?.let { runCatching { WorkoutAdjustmentDirection.valueOf(it) }.getOrNull() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .testTag(FitnessTestTags.WorkoutSummary)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("训练已保存 · 本机", color = FitnessColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(if (summary.isFullyCompleted) "训练完成" else "部分完成", style = MaterialTheme.typography.headlineLarge)
            }
            Surface(shape = CircleShape, color = FitnessColors.Orange, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("✓", color = FitnessColors.Ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text("完成组数", style = MaterialTheme.typography.labelSmall, color = FitnessColors.Muted)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(summary.completedSets.toString(), color = FitnessColors.Ink, fontSize = 68.sp, lineHeight = 70.sp, fontWeight = FontWeight.ExtraBold)
                        Text(" / ${summary.targetSets} 组", color = FitnessColors.Muted, modifier = Modifier.padding(bottom = 12.dp))
                    }
                }
                Surface(color = FitnessColors.Orange, shape = RoundedCornerShape(99.dp)) {
                    Text(if (summary.isFullyCompleted) "全部完成" else "已保存部分", modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            LinearProgressIndicator(
                progress = { (summary.completedSets.toFloat() / summary.targetSets.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)),
                color = FitnessColors.Orange,
                trackColor = Color(0xFFE9EAE5),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FitnessMetricCard("${summary.durationSeconds / 60} 分钟", "训练时长", Modifier.weight(1f))
            FitnessMetricCard("${summary.totalVolumeKg.asWeight()} kg", "训练容量", Modifier.weight(1f))
            FitnessMetricCard(dominantFeeling(summary.feelingCounts), "主观体感", Modifier.weight(1f))
        }
        if (injuryReviewRequired) {
            FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Text("需要先完成伤病复核", style = MaterialTheme.typography.headlineSmall)
                Text("本次记录了身体不适。系统会采取保守策略，在复核完成前不生成下一周加量计划。", style = MaterialTheme.typography.bodyMedium)
                FitnessPrimaryButton(
                    text = if (injuryReviewBusy) "处理中…" else "我已完成复核，解除门禁",
                    enabled = !injuryReviewBusy,
                    onClick = {
                        injuryReviewBusy = true
                        coroutineScope.launch {
                            runCatching { onResolveInjuryReview(true) }
                                .onFailure { reviewError = it.message ?: "解除伤病复核门禁失败" }
                            injuryReviewBusy = false
                        }
                    },
                )
            }
        }
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.NorthEast, contentDescription = null)
            Text(if (summary.isFullyCompleted) "今天的目标已达成" else "已保存已完成的部分", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
            Text(if (summary.isFullyCompleted) "下一步可以补充蛋白质并安排恢复。" else "未完成动作不会补记，本周完成次数也不会虚增。", style = MaterialTheme.typography.bodyMedium)
        }

        FitnessPrimaryButton(
            text = "稍后总结，返回首页",
            onClick = onDone,
            testTag = FitnessTestTags.SummaryDone,
        )

        if (reviewDraft == null) {
            FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Text("训练后感受", style = MaterialTheme.typography.headlineSmall)
                Text("AI 会结合每组次数、重量、组间体感与这里的恢复反馈进行总结。", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("状态很好", "正常疲劳").forEach { feeling ->
                        WorkoutFeedbackChip(feeling, postWorkoutFeeling == feeling, Modifier.weight(1f)) { postWorkoutFeeling = feeling }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("非常疲劳", "疼痛不适").forEach { feeling ->
                        WorkoutFeedbackChip(feeling, postWorkoutFeeling == feeling, Modifier.weight(1f)) { postWorkoutFeeling = feeling }
                    }
                }
                Text("补充感受（可选）", style = MaterialTheme.typography.labelSmall, color = FitnessColors.Muted)
                Surface(
                    color = FitnessColors.Phone,
                    shape = RoundedCornerShape(FitnessDimensions.ControlRadius),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                ) {
                    BasicTextField(
                        value = postWorkoutNote,
                        onValueChange = { if (it.length <= 300) postWorkoutNote = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = FitnessColors.Ink),
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        decorationBox = { inner ->
                            Box {
                                if (postWorkoutNote.isBlank()) Text("例如：右肩有些紧、最后两组动作变形", color = FitnessColors.Muted)
                                inner()
                            }
                        },
                    )
                }
                Text("AI 只生成复盘草稿，不会自动修改后续计划。", color = FitnessColors.Muted, fontSize = 11.sp)
            }
            FitnessPrimaryButton(
                text = if (reviewBusy) "正在分析…" else "生成 AI 训练总结",
                enabled = !reviewBusy,
                onClick = {
                    reviewBusy = true
                    reviewError = null
                    coroutineScope.launch {
                        try {
                            onGenerateReview(postWorkoutFeeling, postWorkoutNote)
                        } catch (error: Exception) {
                            reviewError = error.message ?: "生成训练总结失败"
                        } finally {
                            reviewBusy = false
                        }
                    }
                },
            )
        } else {
            FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("AI 训练复盘", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        when (reviewDraft.status) {
                            "confirmed" -> "已应用"
                            "dismissed" -> "保持原计划"
                            else -> "待确认"
                        },
                        color = FitnessColors.Muted,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(reviewDraft.title, color = FitnessColors.Ink, fontWeight = FontWeight.ExtraBold)
                Text(reviewDraft.content.toReadableAiText(), style = MaterialTheme.typography.bodyMedium)
                reviewMetadata?.let {
                    Text("训练后感受：${it.postWorkoutFeeling}", color = FitnessColors.Muted, fontSize = 12.sp)
                }
            }
            if (reviewDraft.status == "draft") {
                FitnessPrimaryButton(
                    text = when (reviewDirection) {
                        WorkoutAdjustmentDirection.INCREASE -> "确认小幅加量"
                        WorkoutAdjustmentDirection.REDUCE -> "确认降低后续负荷"
                        else -> "确认保持当前计划"
                    },
                    enabled = !reviewBusy,
                    onClick = {
                        reviewBusy = true
                        reviewError = null
                        coroutineScope.launch {
                            try {
                                onResolveReview(reviewDraft.id, true)
                            } catch (error: Exception) {
                                reviewError = error.message ?: "调整计划失败"
                            } finally {
                                reviewBusy = false
                            }
                        }
                    },
                )
                if (reviewDirection != WorkoutAdjustmentDirection.MAINTAIN) {
                    TextButton(
                        onClick = {
                            reviewBusy = true
                            reviewError = null
                            coroutineScope.launch {
                                try {
                                    onResolveReview(reviewDraft.id, false)
                                } catch (error: Exception) {
                                    reviewError = error.message ?: "保留原计划失败"
                                } finally {
                                    reviewBusy = false
                                }
                            }
                        },
                        enabled = !reviewBusy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = FitnessDimensions.MinimumTouchTarget),
                    ) {
                        Text("保持原计划", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        reviewError?.let { Text(it, color = WorkoutOnErrorContainer, fontWeight = FontWeight.Bold) }

    }
}

internal fun dominantFeeling(feelingCounts: Map<String, Int>): String {
    var dominant = "合适"
    var highestCount = Int.MIN_VALUE
    feelingCounts.forEach { (feeling, count) ->
        if (count > highestCount) {
            dominant = feeling
            highestCount = count
        }
    }
    return dominant
}

@Composable
private fun WorkoutFeedbackChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = FitnessDimensions.MinimumTouchTarget),
        shape = RoundedCornerShape(99.dp),
        color = if (selected) FitnessColors.Orange else FitnessColors.Phone,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, FitnessColors.Muted.copy(alpha = .5f)),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
            Text(label, color = FitnessColors.Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

private val WorkoutFeelings = listOf("轻松", "合适", "吃力")
private val WorkoutErrorContainer = Color(0xFFFFDAD6)
private val WorkoutOnErrorContainer = Color(0xFF690005)

private fun Double.asWeight(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.ROOT, "%.1f", this)
