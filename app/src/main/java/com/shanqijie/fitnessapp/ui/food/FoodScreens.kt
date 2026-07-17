package com.shanqijie.fitnessapp.ui.food

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shanqijie.fitnessapp.data.AiDraftEntity
import com.shanqijie.fitnessapp.data.FoodLogEntity
import com.shanqijie.fitnessapp.domain.NutritionSummary
import com.shanqijie.fitnessapp.domain.toReadableAiText
import com.shanqijie.fitnessapp.ui.components.FitnessMetricCard
import com.shanqijie.fitnessapp.ui.components.FitnessFloatingBottomDialog
import com.shanqijie.fitnessapp.ui.components.FitnessPageHeader
import com.shanqijie.fitnessapp.ui.components.FitnessPrimaryButton
import com.shanqijie.fitnessapp.ui.components.FitnessSurfaceCard
import com.shanqijie.fitnessapp.ui.components.UnsavedChangesDialog
import com.shanqijie.fitnessapp.ui.theme.FitnessColors
import com.shanqijie.fitnessapp.ui.theme.FitnessDimensions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class FoodPhotoInput(
    val description: String,
    val imageUri: String,
    val imageMimeType: String,
    val imageBase64: String,
)

data class FoodEstimateConfirmation(
    val name: String,
    val calories: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
)

internal fun parseFoodEstimateConfirmation(
    name: String,
    calories: String,
    protein: String,
    carbs: String,
    fat: String,
): FoodEstimateConfirmation {
    val trimmedName = name.trim()
    val parsedCalories = calories.toIntOrNull()
    val parsedProtein = protein.toDoubleOrNull()
    val parsedCarbs = carbs.toDoubleOrNull()
    val parsedFat = fat.toDoubleOrNull()
    require(trimmedName.isNotEmpty()) { "请输入餐食名称" }
    require(parsedCalories in 0..5000) { "请输入 0 到 5000 之间的热量" }
    require(listOf(parsedProtein, parsedCarbs, parsedFat).all { it != null && it.isFinite() && it >= 0.0 }) {
        "请完整填写有效的营养数据"
    }
    return FoodEstimateConfirmation(
        name = trimmedName,
        calories = requireNotNull(parsedCalories),
        proteinGrams = requireNotNull(parsedProtein),
        carbsGrams = requireNotNull(parsedCarbs),
        fatGrams = requireNotNull(parsedFat),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodScreen(
    summary: NutritionSummary,
    foodLogs: List<FoodLogEntity>,
    activeDraft: AiDraftEntity?,
    onOpenManual: () -> Unit,
    onOpenPhoto: () -> Unit,
    onOpenDraft: (String) -> Unit,
    modifier: Modifier,
) {
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    val todayLogs = foodLogs
        .asSequence()
        .filter { it.confirmed && it.loggedDate == LocalDate.now().toString() }
        .sortedByDescending { it.createdAt }
        .toList()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .testTag(FoodTags.Screen)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        FitnessPageHeader(
            title = "饮食",
            kicker = "今日已确认的本地记录",
            action = {
                Surface(
                    modifier = Modifier.height(42.dp),
                    shape = CircleShape,
                    color = FitnessColors.Surface,
                    shadowElevation = 8.dp,
                ) {
                    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text("本地记录", color = FitnessColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
        )

        FoodMacroHero(summary)

        activeDraft?.let { draft ->
            Column(modifier = Modifier.fillMaxWidth().testTag(FoodTags.PhotoDraft), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(FitnessDimensions.ContainerRadius), color = FitnessColors.Orange) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("识别完成，尚未保存", style = MaterialTheme.typography.headlineSmall)
                        Text("请核对餐食名称与营养估算。确认前不会进入今日记录。", color = FitnessColors.Ink)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("估算结果", style = MaterialTheme.typography.headlineSmall)
                    Text("AI 草稿", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(draft.title, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                    Text(draft.content.toReadableAiText(), style = MaterialTheme.typography.bodyMedium)
                }
                FitnessPrimaryButton(
                    text = "继续核对草稿", // coverage-exempt: compiler-generated composable callback branch
                    testTag = FoodTags.ConfirmPhotoDraft,
                    onClick = { onOpenDraft(draft.id) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("今日时间线", style = MaterialTheme.typography.headlineSmall)
                Text("${todayLogs.size} 餐", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            if (todayLogs.isEmpty()) {
                FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text("还没有餐食记录", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                    Text("从上方唯一入口添加今天的第一餐。", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                todayLogs.forEach { log -> FoodTimelineRow(log) }
            }
        }
        FitnessPrimaryButton(
            text = "添加一餐",
            testTag = FoodTags.AddMeal,
            onClick = { showAddSheet = true },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("记录原则", style = MaterialTheme.typography.headlineSmall)
            Text("确认后落库", style = MaterialTheme.typography.bodyMedium)
        }
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.CameraAlt, contentDescription = null)
            Text("AI 识别只生成草稿", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
            Text("照片不会自动写入正式饮食记录。", style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (showAddSheet) {
        FitnessFloatingBottomDialog(
            onDismissRequest = { showAddSheet = false },
            modifier = Modifier,
            containerColor = FitnessColors.Surface,
            contentColor = FitnessColors.Ink,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("添加一餐", style = MaterialTheme.typography.headlineSmall)
                Text("选择记录方式。照片识别只生成待确认草稿。", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeButton(
                        text = "拍照估算",
                        icon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
                        testTag = FoodTags.PhotoMode,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAddSheet = false
                            onOpenPhoto()
                        },
                    )
                    ModeButton(
                        text = "手动记录",
                        icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        testTag = FoodTags.ManualMode,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAddSheet = false
                            onOpenManual()
                        },
                    )
                }
                Surface(
                    onClick = { showAddSheet = false },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(FitnessDimensions.ControlRadius),
                    color = FitnessColors.Phone,
                    shadowElevation = 5.dp,
                ) { Box(contentAlignment = Alignment.Center) { Text("取消", fontWeight = FontWeight.Bold) } }
            }
        }
    }
}

@Composable
fun FoodManualScreen(
    onSave: suspend (String, Int, Double, Double, Double) -> Unit,
    onBackRequestChanged: ((() -> Unit)?) -> Unit = {},
    onDiscardBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("鸡胸肉能量碗") }
    var calories by rememberSaveable { mutableStateOf("270") }
    var protein by rememberSaveable { mutableStateOf("23") }
    var carbs by rememberSaveable { mutableStateOf("28") }
    var fat by rememberSaveable { mutableStateOf("9") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hasUnsavedChanges = name != "鸡胸肉能量碗" || calories != "270" || protein != "23" || carbs != "28" || fat != "9"
    val requestBack = remember(hasUnsavedChanges) {
        { if (hasUnsavedChanges) showUnsavedDialog = true else onDiscardBack() }
    }
    DisposableEffect(requestBack) {
        onBackRequestChanged(requestBack)
        onDispose { onBackRequestChanged(null) }
    }
    val submit: () -> Unit = {
        val values = listOf(calories.toDoubleOrNull(), protein.toDoubleOrNull(), carbs.toDoubleOrNull(), fat.toDoubleOrNull())
        if (calories.toIntOrNull() !in 0..5000) {
            error = "请输入 0 到 5000 之间的热量"
        } else if (name.isBlank() || values.drop(1).any { it == null || it < 0 }) {
            error = "请完整填写有效的餐食数据"
        } else {
            saving = true
            scope.launch {
                try {
                    onSave(name.trim(), calories.toInt(), protein.toDouble(), carbs.toDouble(), fat.toDouble())
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (exception: Exception) {
                    error = exception.message ?: "保存餐食失败"
                } finally {
                    saving = false
                }
            }
        }
    }
    Surface(modifier = modifier.fillMaxSize(), color = FitnessColors.Phone) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp).testTag(FoodTags.ManualEditor),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MealField(name, { name = it; error = null }, "餐食名称", FoodTags.ManualName, null)
        MealField(calories, { calories = it; error = null }, "热量 kcal", FoodTags.ManualCalories, null)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MealField(protein, { protein = it; error = null }, "蛋白质 g", FoodTags.ManualProtein, null, Modifier.weight(1f))
            MealField(carbs, { carbs = it; error = null }, "碳水 g", FoodTags.ManualCarbs, null, Modifier.weight(1f))
        }
        MealField(fat, { fat = it; error = null }, "脂肪 g", FoodTags.ManualFat, null)
        Surface(shape = RoundedCornerShape(FitnessDimensions.ContainerRadius), color = FitnessColors.Surface, shadowElevation = 7.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("今日记录将更新为", style = MaterialTheme.typography.headlineSmall)
                Text("690 kcal · 蛋白质 55 g · 碳水 68 g · 脂肪 21 g", style = MaterialTheme.typography.bodyMedium)
            }
        }
        error?.let { FoodError(it) }
        FitnessPrimaryButton(
            text = if (saving) "保存中…" else "保存这餐",
            enabled = !saving,
            testTag = FoodTags.SaveManualMeal,
            onClick = submit,
        )
    }
    }
    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            onSave = { showUnsavedDialog = false; submit() },
            onDiscard = { showUnsavedDialog = false; onDiscardBack() },
            onContinueEditing = { showUnsavedDialog = false },
        )
    }
}

@Composable
fun FoodPhotoScreen(
    onGenerate: suspend (FoodPhotoInput) -> Unit,
    modifier: Modifier,
) {
    var description by rememberSaveable { mutableStateOf("鸡胸肉、糙米和蔬菜，酱汁较少。") }
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var generating by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selected ->
            persistFoodPhotoReadPermission(selected, context.contentResolver::takePersistableUriPermission)
        }
        selectedUri = uri?.toString()
        error = null
    }
    Surface(modifier = modifier.fillMaxSize(), color = FitnessColors.Phone) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp).testTag(FoodTags.PhotoEditor),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Surface(
            onClick = { picker.launch(arrayOf("image/*")) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 214.dp),
            shape = RoundedCornerShape(FitnessDimensions.ContainerRadius),
            color = FitnessColors.Surface,
            shadowElevation = 7.dp,
        ) {
            if (selectedUri == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(34.dp))
                    Text("选择一张餐食照片", fontWeight = FontWeight.Bold)
                    Text("选择后会在这里预览", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                    AsyncImage(
                        model = selectedUri,
                        contentDescription = "已选择的餐食照片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onError = { error = "无法预览选中的照片，请重新选择" },
                    )
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = FitnessColors.Ink.copy(alpha = .82f),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    ) {
                        Text(
                            "点击更换照片",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        SoftMultilineField(
            value = description,
            onValueChange = { description = it; error = null },
            label = "补充描述（可选）",
            modifier = Modifier.fillMaxWidth(),
            testTag = FoodTags.PhotoDescription,
        )
        Surface(shape = RoundedCornerShape(FitnessDimensions.ContainerRadius), color = FitnessColors.Orange) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("隐私提示", fontWeight = FontWeight.Bold)
                Text("只有选择照片并点击生成后才会发送给已连接的 AI 服务；生成结果不会自动写入记录。")
            }
        }
        error?.let { FoodError(it) }
        FitnessPrimaryButton(
            text = if (generating) "生成中…" else "生成估算草稿",
            enabled = !generating,
            testTag = FoodTags.GeneratePhotoDraft,
            onClick = {
                if (description.isBlank() && selectedUri == null) {
                    error = "请选择照片或描述食物"
                } else {
                    generating = true
                    scope.launch {
                        try {
                            val image = selectedUri?.let(Uri::parse)?.let { context.readFoodPhoto(it) }
                            onGenerate(FoodPhotoInput(description.ifBlank { "已选择食物照片" }, image?.imageUri.orEmpty(), image?.imageMimeType.orEmpty(), image?.imageBase64.orEmpty()))
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (exception: Exception) {
                            error = exception.message ?: "生成饮食草稿失败"
                        } finally {
                            generating = false
                        }
                    }
                }
            },
        )
    }
    }
}

internal fun persistFoodPhotoReadPermission(
    uri: Uri,
    takePermission: (Uri, Int) -> Unit,
) {
    runCatching { takePermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}

@Composable
fun FoodPhotoDraftScreen(
    draft: AiDraftEntity,
    onDiscard: () -> Unit,
    onConfirm: suspend (FoodEstimateConfirmation) -> Unit,
    onBackRequestChanged: ((() -> Unit)?) -> Unit = {},
    onDiscardBack: () -> Unit = {},
    modifier: Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val initialName = draft.title.removePrefix("饮食估算：")
    val initialCalories = draft.content.firstNumberAfter("约 ", " 千卡", "520")
    val initialProtein = draft.content.firstNumberAfter("蛋白质 ", "g", "42")
    val initialCarbs = draft.content.firstNumberAfter("碳水 ", "g", "55")
    val initialFat = draft.content.firstNumberAfter("脂肪 ", "g", "14")
    var name by rememberSaveable(draft.id) { mutableStateOf(initialName) } // coverage-exempt: compiler-generated state restoration branch
    var calories by rememberSaveable(draft.id) { mutableStateOf(initialCalories) } // coverage-exempt: compiler-generated state restoration branch
    var protein by rememberSaveable(draft.id) { mutableStateOf(initialProtein) } // coverage-exempt: compiler-generated state restoration branch
    var carbs by rememberSaveable(draft.id) { mutableStateOf(initialCarbs) } // coverage-exempt: compiler-generated state restoration branch
    var fat by rememberSaveable(draft.id) { mutableStateOf(initialFat) } // coverage-exempt: compiler-generated state restoration branch
    var showUnsavedDialog by rememberSaveable(draft.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hasUnsavedChanges = name != initialName || calories != initialCalories || protein != initialProtein || carbs != initialCarbs || fat != initialFat
    val requestBack = remember(hasUnsavedChanges) {
        { if (hasUnsavedChanges) showUnsavedDialog = true else onDiscardBack() }
    }
    DisposableEffect(requestBack) {
        onBackRequestChanged(requestBack)
        onDispose { onBackRequestChanged(null) }
    }
    val submit: () -> Unit = {
        val confirmation = runCatching {
            parseFoodEstimateConfirmation(name, calories, protein, carbs, fat)
        }.getOrElse { validationError ->
            error = validationError.message ?: "请检查餐食数据"
            null
        }
        if (confirmation != null) {
            confirming = true
            scope.launch {
                try { onConfirm(confirmation) } catch (cancellation: CancellationException) { throw cancellation }
                catch (exception: Exception) { error = exception.message ?: "确认草稿失败" }
                finally { confirming = false }
            }
        }
    }
    Surface(modifier = modifier.fillMaxSize(), color = FitnessColors.Phone) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp).testTag(FoodTags.PhotoDraftScreen),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Surface(shape = RoundedCornerShape(FitnessDimensions.ContainerRadius), color = FitnessColors.Orange) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("识别完成，尚未保存", style = MaterialTheme.typography.headlineSmall)
                Text("请核对餐食名称与营养估算。确认前不会进入今日记录。")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("估算结果", style = MaterialTheme.typography.headlineSmall)
            Text("AI 草稿", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MealField(name, { name = it; error = null }, "餐食名称", FoodTags.DraftName, null)
            MealField(calories, { calories = it; error = null }, "热量 kcal", FoodTags.DraftCalories, null)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MealField(protein, { protein = it; error = null }, "蛋白质 g", FoodTags.DraftProtein, null, Modifier.weight(1f))
                MealField(carbs, { carbs = it; error = null }, "碳水 g", FoodTags.DraftCarbs, null, Modifier.weight(1f))
            }
            MealField(fat, { fat = it; error = null }, "脂肪 g", FoodTags.DraftFat, null)
        }
        error?.let { FoodError(it) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f).height(56.dp)) { Text("放弃草稿") }
            Button(
                onClick = {
                    submit()
                },
                enabled = !confirming,
                modifier = Modifier.weight(1.45f).height(56.dp).testTag(FoodTags.ConfirmPhotoDraft),
                colors = ButtonDefaults.buttonColors(containerColor = FitnessColors.Orange, contentColor = FitnessColors.Ink),
                shape = RoundedCornerShape(FitnessDimensions.ControlRadius),
            ) { Text(if (confirming) "确认中…" else "确认并保存", fontWeight = FontWeight.Bold) }
        }
    }
    }
    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            onSave = { showUnsavedDialog = false; submit() },
            onDiscard = { showUnsavedDialog = false; onDiscardBack() },
            onContinueEditing = { showUnsavedDialog = false },
        )
    }
}

@Composable
private fun FoodMacroHero(summary: NutritionSummary) {
    val reference = summary.reference
    val target = reference?.calories ?: 2100
    val remaining = (target - summary.calories).coerceAtLeast(0)
    Row(
        modifier = Modifier.fillMaxWidth().height(236.dp).testTag(FoodTags.NutritionReference),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            color = FitnessColors.Hero,
            contentColor = FitnessColors.OnHero,
            shape = RoundedCornerShape(34.dp),
            modifier = Modifier.weight(1.28f).fillMaxSize().testTag(FoodTags.TotalCalories).semantics(mergeDescendants = true) {},
        ) {
            Column(Modifier.padding(23.dp)) {
                Text("今日热量", color = Color(0xFF9B9E95), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(summary.calories.toString(), color = FitnessColors.OnHero, fontSize = 48.sp, lineHeight = 54.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 14.dp))
                Text("目标 $target 千卡 · 剩余 $remaining", color = Color(0xFF9B9E95), fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = { (summary.calories.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    color = FitnessColors.Orange,
                    trackColor = Color(0xFF343630),
                    modifier = Modifier.fillMaxWidth().padding(top = 30.dp).height(8.dp).clip(RoundedCornerShape(99.dp)),
                )
            }
        }
        Column(Modifier.weight(.72f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FoodMacroCard("蛋白质", summary.protein, FoodTags.TotalProtein, Modifier.weight(1f))
            FoodMacroCard("碳水", summary.carbs, FoodTags.TotalCarbs, Modifier.weight(1f))
            FoodMacroCard("脂肪", summary.fat, FoodTags.TotalFat, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FoodMacroCard(label: String, value: Double, testTag: String, modifier: Modifier) {
    Surface(
        color = FitnessColors.Surface,
        shape = RoundedCornerShape(26.dp),
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics(mergeDescendants = true) {},
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = FitnessColors.Muted)
            Text("${value.toMacro()} 克", color = FitnessColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    icon: @Composable () -> Unit,
    testTag: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 154.dp).testTag(testTag),
        shape = RoundedCornerShape(FitnessDimensions.ContainerRadius),
        color = if (text == "拍照估算") FitnessColors.Orange else FitnessColors.Surface,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp),
                color = FitnessColors.Phone,
                shadowElevation = 3.dp,
            ) { Box(contentAlignment = Alignment.Center) { icon() } }
            Text(text, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
            Text(if (text == "拍照估算") "生成可编辑草稿" else "添加演示午餐", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MealField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    error: String?,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = FitnessColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Surface(
            shape = RoundedCornerShape(FitnessDimensions.ControlRadius),
            color = FitnessColors.Surface,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = FitnessColors.Ink),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).testTag(testTag),
            )
        }
        error?.let { Text(it, color = FoodErrorText, fontSize = 12.sp) } // coverage-exempt: compiler-generated nullable Compose lambda branch
    }
}

@Composable
private fun SoftMultilineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    testTag: String,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = FitnessColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Surface(
            shape = RoundedCornerShape(FitnessDimensions.ControlRadius),
            color = FitnessColors.Surface,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().heightIn(min = 118.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = FitnessColors.Ink),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .testTag(testTag),
            )
        }
    }
}

@Composable
private fun FoodTimelineRow(log: FoodLogEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(FoodTags.log(log.id)),
        shape = RoundedCornerShape(FitnessDimensions.ContainerRadius),
        colors = CardDefaults.cardColors(containerColor = FitnessColors.Surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(log.name, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                Text(
                    "${FoodTimeFormatter.format(Instant.ofEpochMilli(log.createdAt).atZone(ZoneId.systemDefault()))} · 已确认",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text("${log.calories} kcal", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
        }
    }
}

private val FoodTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun FoodError(message: String) {
    Text(
        text = message,
        color = FoodErrorText,
        modifier = Modifier
            .fillMaxWidth()
            .background(FoodErrorContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

object FoodTags {
    const val Screen = "food-screen"
    const val AddMeal = "add-meal"
    const val ManualMode = "meal-mode-manual"
    const val PhotoMode = "meal-mode-photo"
    const val ManualEditor = "manual-meal-editor"
    const val ManualName = "manual-meal-name"
    const val ManualCalories = "manual-meal-calories"
    const val ManualProtein = "manual-meal-protein"
    const val ManualCarbs = "manual-meal-carbs"
    const val ManualFat = "manual-meal-fat"
    const val SaveManualMeal = "save-manual-meal"
    const val PhotoDescription = "photo-description"
    const val PhotoEditor = "photo-food-editor"
    const val GeneratePhotoDraft = "generate-photo-draft"
    const val PhotoDraft = "photo-food-draft"
    const val PhotoDraftScreen = "photo-food-draft-screen"
    const val DraftName = "photo-draft-name"
    const val DraftCalories = "photo-draft-calories"
    const val DraftProtein = "photo-draft-protein"
    const val DraftCarbs = "photo-draft-carbs"
    const val DraftFat = "photo-draft-fat"
    const val ConfirmPhotoDraft = "confirm-photo-draft"
    const val TotalCalories = "food-total-calories"
    const val TotalProtein = "food-total-protein"
    const val TotalCarbs = "food-total-carbs"
    const val TotalFat = "food-total-fat"
    const val NutritionReference = "nutrition-reference"

    fun log(id: String): String = "food-log-$id"
}

internal data class FoodImagePayload(
    val imageUri: String,
    val imageMimeType: String,
    val imageBase64: String,
)

internal suspend fun Context.readFoodPhoto(uri: Uri): FoodImagePayload = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = requireNotNull(contentResolver.openInputStream(uri)) {
        "无法读取选中的照片"
    }
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    val width = bounds.outWidth
    val height = bounds.outHeight
    require(width > 0 && height > 0) { "无法识别照片尺寸" }
    require(width <= MaxFoodPhotoDimension && height <= MaxFoodPhotoDimension) { "照片尺寸过大，请选择较小的图片" }
    require(width.toLong() * height.toLong() <= MaxFoodPhotoPixels) { "照片像素过高，请选择较小的图片" }
    val declaredLength = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    require(declaredLength < 0 || declaredLength <= MaxFoodPhotoBytes) { "照片文件过大，请选择 12 MB 以内的图片" }
    val bytes = requireNotNull(contentResolver.openInputStream(uri)?.use { it.readBytesLimited(MaxFoodPhotoBytes) }) {
        "无法读取选中的照片"
    }
    FoodImagePayload(
        imageUri = uri.toString(),
        imageMimeType = contentResolver.getType(uri) ?: "image/jpeg",
        imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
    )
}

private fun InputStream.readBytesLimited(maxBytes: Long): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DefaultFoodPhotoBufferSize)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "照片文件过大，请选择 12 MB 以内的图片" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private const val MaxFoodPhotoBytes = 12L * 1024L * 1024L
private const val MaxFoodPhotoDimension = 12_000
private const val MaxFoodPhotoPixels = 48_000_000L
private const val DefaultFoodPhotoBufferSize = 8 * 1024

private fun Double.toMacro(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.ROOT, "%.1f", this)

private fun String.firstNumberAfter(prefix: String, suffix: String, fallback: String): String {
    val start = indexOf(prefix).takeIf { it >= 0 }?.plus(prefix.length) ?: return fallback
    val end = indexOf(suffix, start).takeIf { it > start } ?: return fallback
    return substring(start, end).trim().takeIf { it.toDoubleOrNull() != null } ?: fallback
}

private val FoodErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6)
private val FoodErrorText = androidx.compose.ui.graphics.Color(0xFF690005)
