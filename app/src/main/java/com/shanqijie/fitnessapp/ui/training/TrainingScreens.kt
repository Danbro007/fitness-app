package com.shanqijie.fitnessapp.ui.training

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanqijie.fitnessapp.domain.WorkoutSummary
import com.shanqijie.fitnessapp.ui.components.FitnessGifImage
import com.shanqijie.fitnessapp.ui.components.FitnessMetricCard
import com.shanqijie.fitnessapp.ui.components.FitnessPrimaryButton
import com.shanqijie.fitnessapp.ui.components.FitnessSelectionChip
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
    val currentExerciseId: String,
    val restEndsAt: Long?,
    val exercises: List<TrainingExerciseScreenUi>,
) {
    val currentExercise: TrainingExerciseScreenUi
        get() = exercises.first { it.exerciseId == currentExerciseId }
}

@Composable
fun TrainingPreparationScreen(
    state: TrainingPreparationScreenUi?,
    onStartWorkout: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .testTag(FitnessTestTags.TrainingPrep)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "TRAINING / READY",
            color = FitnessColors.Ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = state?.planName ?: "先安排一次训练",
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = state?.let { "${it.exercises.size} 个动作 · 约 ${it.estimatedMinutes} 分钟" }
                ?: "完成计划后，这里会显示训练清单。",
            style = MaterialTheme.typography.bodyLarge,
        )

        state?.exercises?.forEachIndexed { index, exercise ->
            FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (index + 1).toString().padStart(2, '0'),
                        color = FitnessColors.Ink,
                        fontWeight = FontWeight.Bold,
                    )
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
                }
            }
        }

        FitnessPrimaryButton(
            text = if (state == null) "暂无可开始训练" else "开始训练",
            onClick = { state?.let { onStartWorkout(it.planId) } },
            enabled = state != null,
            testTag = FitnessTestTags.StartWorkout,
        )
    }
}

@Composable
fun TrainingActiveScreen(
    state: TrainingActiveScreenUi,
    onSelectExercise: (String) -> Unit,
    onRecordSet: suspend (reps: Int, weightKg: Double, feeling: String) -> Unit,
    onRestFinished: () -> Unit,
    onSkipRest: () -> Unit,
    onFinishWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = state.currentExercise
    var reps by rememberSaveable(current.exerciseId) {
        mutableStateOf(current.targetReps.substringBefore('-').toIntOrNull()?.coerceIn(1, 50) ?: 8)
    }
    var weightKg by rememberSaveable(current.exerciseId) { mutableStateOf(current.targetWeightKg) }
    var feeling by rememberSaveable(current.exerciseId) { mutableStateOf(WorkoutFeelings[1]) }
    var showFinishDialog by rememberSaveable { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var submittedCompletedSets by remember { mutableStateOf<Int?>(null) }
    var recordError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val resting = state.restEndsAt != null

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
                restEndsAt = requireNotNull(state.restEndsAt),
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
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LIVE WORKOUT",
                        color = FitnessColors.Green,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.planName,
                        color = FitnessColors.OnHero,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(
                    onClick = { showFinishDialog = true },
                    modifier = Modifier
                        .heightIn(min = FitnessDimensions.MinimumTouchTarget)
                        .testTag(FitnessTestTags.RequestFinish),
                ) {
                    Text("结束", color = FitnessColors.OnHero)
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
                        resting -> "休息中"
                        isRecording -> "保存中…"
                        else -> "完成本组"
                    },
                    onClick = {
                        if (!isRecording) {
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
                    enabled = !resting && !isRecording && current.completedSets < current.targetSets,
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.exercises, key = { it.sessionExerciseId }) { exercise ->
                    FitnessSelectionChip(
                        label = "${exercise.name} ${exercise.completedSets}/${exercise.targetSets}",
                        selected = exercise.exerciseId == current.exerciseId,
                        onClick = { if (!resting) onSelectExercise(exercise.exerciseId) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(FitnessDimensions.LargeRadius))
                    .background(Color.Black),
            ) {
                FitnessGifImage(
                    assetPath = current.assetPath,
                    contentDescription = current.name,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    text = "${current.completedSets}/${current.targetSets} SETS",
                    color = FitnessColors.OnHero,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.68f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = current.name,
                    color = FitnessColors.OnHero,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = current.detail,
                    color = FitnessColors.OnHero.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                )
            }

            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Stepper(
                        label = "重量",
                        value = "${weightKg.asWeight()} kg",
                        decreaseDescription = "减少重量",
                        increaseDescription = "增加重量",
                        onDecrease = { weightKg = max(0.0, weightKg - 2.5) },
                        onIncrease = { weightKg += 2.5 },
                        modifier = Modifier.weight(1f),
                    )
                    Stepper(
                        label = "次数",
                        value = reps.toString(),
                        decreaseDescription = "减少次数",
                        increaseDescription = "增加次数",
                        onDecrease = { reps = max(1, reps - 1) },
                        onIncrease = { reps = (reps + 1).coerceAtMost(50) },
                        modifier = Modifier.weight(1f),
                    )
                }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WorkoutFeelings) { option ->
                    FitnessSelectionChip(
                        label = option,
                        selected = feeling == option,
                        onClick = { feeling = option },
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("结束本次训练？") },
            text = { Text("已完成的训练组会保存到本地，未完成动作不会补记。") },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) { Text("继续训练") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishDialog = false
                        onFinishWorkout()
                    },
                    modifier = Modifier
                        .heightIn(min = FitnessDimensions.MinimumTouchTarget)
                        .testTag(FitnessTestTags.ConfirmFinish),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FitnessColors.Orange,
                        contentColor = FitnessColors.OnOrange,
                    ),
                ) {
                    Text("确认结束")
                }
            },
        )
    }
}

@Composable
private fun RestPanel(
    restEndsAt: Long,
    onRestFinished: () -> Unit,
    onSkipRest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember(restEndsAt) { mutableLongStateOf(System.currentTimeMillis()) }
    var completionSent by remember(restEndsAt) { mutableStateOf(false) }

    LaunchedEffect(restEndsAt) {
        while (now < restEndsAt) {
            delay(250)
            now = System.currentTimeMillis()
        }
        if (!completionSent) {
            completionSent = true
            onRestFinished()
        }
    }

    val remainingSeconds = ceil(max(0L, restEndsAt - now) / 1_000.0).toInt()
    Column(
        modifier = modifier.background(FitnessColors.Hero).testTag(FitnessTestTags.RestPanel).padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Color(0xFF1A1C18), modifier = Modifier.size(52.dp)) { Box(contentAlignment = Alignment.Center) { Text("×", color = Color.White, fontSize = 28.sp) } }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("训练用时", color = Color(0xFF9B9E95), fontSize = 10.sp)
                Text("00:00", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Surface(shape = CircleShape, color = Color(0xFF1A1C18), modifier = Modifier.size(52.dp)) { Box(contentAlignment = Alignment.Center) { Text("＋", color = Color.White, fontSize = 22.sp) } }
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
                Text("下一组：当前动作 · 目标组数", color = Color(0xFF9B9E95), fontSize = 13.sp)
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

@Composable
private fun Stepper(
    label: String,
    value: String,
    decreaseDescription: String,
    increaseDescription: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(FitnessDimensions.ContainerRadius))
            .background(FitnessColors.OnHero.copy(alpha = 0.08f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = FitnessColors.OnHero.copy(alpha = 0.65f), fontSize = 12.sp)
        Text(value, color = FitnessColors.OnHero, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onDecrease,
                modifier = Modifier
                    .size(FitnessDimensions.MinimumTouchTarget)
                    .semantics { contentDescription = decreaseDescription },
            ) {
                Text("−", color = FitnessColors.OnHero, fontSize = 24.sp)
            }
            IconButton(
                onClick = onIncrease,
                modifier = Modifier
                    .size(FitnessDimensions.MinimumTouchTarget)
                    .semantics { contentDescription = increaseDescription },
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .testTag(FitnessTestTags.WorkoutSummary)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("训练已保存 · 本机", color = FitnessColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(if (summary.isFullyCompleted) "训练完成" else "部分完成", style = MaterialTheme.typography.headlineLarge)
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
                    Text(if (summary.isFullyCompleted) "已全部保存" else "已保存部分", modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            FitnessMetricCard(summary.feelingCounts.maxByOrNull { it.value }?.key ?: "合适", "主观体感", Modifier.weight(1f))
        }
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.NorthEast, contentDescription = null)
            Text("已保存已完成的部分", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
            Text(if (summary.isFullyCompleted) "本周已完成 $weeklyCompleted / $weeklyTarget 次。" else "未完成动作不会补记，本周完成次数也不会虚增。", style = MaterialTheme.typography.bodyMedium)
        }

        FitnessPrimaryButton(
            text = "返回首页",
            onClick = onDone,
            testTag = FitnessTestTags.SummaryDone,
        )
    }
}

private val WorkoutFeelings = listOf("轻松", "合适", "吃力")
private val WorkoutErrorContainer = Color(0xFFFFDAD6)
private val WorkoutOnErrorContainer = Color(0xFF690005)

private fun Double.asWeight(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.ROOT, "%.1f", this)
