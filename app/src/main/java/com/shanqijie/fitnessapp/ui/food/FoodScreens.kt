package com.shanqijie.fitnessapp.ui.food

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shanqijie.fitnessapp.data.AiDraftEntity
import com.shanqijie.fitnessapp.data.FoodLogEntity
import com.shanqijie.fitnessapp.domain.NutritionSummary
import com.shanqijie.fitnessapp.ui.components.FitnessMetricCard
import com.shanqijie.fitnessapp.ui.components.FitnessPageHeader
import com.shanqijie.fitnessapp.ui.components.FitnessPrimaryButton
import com.shanqijie.fitnessapp.ui.components.FitnessSurfaceCard
import com.shanqijie.fitnessapp.ui.theme.FitnessColors
import com.shanqijie.fitnessapp.ui.theme.FitnessDimensions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

data class FoodPhotoInput(
    val description: String,
    val imageUri: String,
    val imageMimeType: String,
    val imageBase64: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodScreen(
    summary: NutritionSummary,
    foodLogs: List<FoodLogEntity>,
    activeDraft: AiDraftEntity?,
    onSaveManualMeal: suspend (
        name: String,
        calories: Int,
        protein: Double,
        carbs: Double,
        fat: Double,
    ) -> Unit,
    onGeneratePhotoDraft: suspend (FoodPhotoInput) -> Unit,
    onConfirmPhotoDraft: suspend (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var calories by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var caloriesError by rememberSaveable { mutableStateOf<String?>(null) }
    var proteinError by rememberSaveable { mutableStateOf<String?>(null) }
    var carbsError by rememberSaveable { mutableStateOf<String?>(null) }
    var fatError by rememberSaveable { mutableStateOf<String?>(null) }
    var photoDescription by rememberSaveable { mutableStateOf("") }
    var selectedPhotoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var operationError by rememberSaveable { mutableStateOf<String?>(null) }
    var operationInProgress by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedPhotoUri = uri?.toString()
        operationError = null
    }
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
            kicker = "今日已确认的本地记录与参考摄入",
            action = {
                Surface(
                    onClick = {
                        mode = null
                        operationError = null
                        showAddSheet = true
                    },
                    modifier = Modifier.size(52.dp).testTag(FoodTags.AddMeal),
                    shape = CircleShape,
                    color = FitnessColors.Surface,
                    shadowElevation = 8.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, contentDescription = "添加一餐") }
                }
            },
        )

        FoodMacroHero(summary)

        activeDraft?.let { draft ->
            FitnessSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(FoodTags.PhotoDraft),
            ) {
                Text("照片估算草稿", style = MaterialTheme.typography.headlineSmall)
                Text(draft.title, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                Text(draft.content, style = MaterialTheme.typography.bodyMedium)
                Text("确认前不会计入今日总计。", style = MaterialTheme.typography.bodyMedium)
                FitnessPrimaryButton(
                    text = if (operationInProgress) "确认中…" else "确认记入",
                    enabled = !operationInProgress,
                    testTag = FoodTags.ConfirmPhotoDraft,
                    onClick = {
                        if (!operationInProgress) {
                            operationInProgress = true
                            operationError = null
                            coroutineScope.launch {
                                try {
                                    onConfirmPhotoDraft(draft.id)
                                } catch (cancellation: CancellationException) {
                                    throw cancellation
                                } catch (error: Exception) {
                                    operationError = error.message ?: "确认饮食草稿失败"
                                } finally {
                                    operationInProgress = false
                                }
                            }
                        }
                    },
                )
            }
        }

        operationError?.let { FoodError(it) }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("今日时间线", style = MaterialTheme.typography.headlineSmall)
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
            onClick = { mode = null; operationError = null; showAddSheet = true },
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
        ModalBottomSheet(
            onDismissRequest = {
                if (!operationInProgress) {
                    showAddSheet = false
                    mode = null
                }
            },
            containerColor = FitnessColors.Surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = when (mode) {
                        "manual" -> "手动记录"
                        "photo" -> "拍照估算"
                        else -> "选择记录方式"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                when (mode) {
                    null -> {
                        ModeButton(
                            text = "拍照估算",
                            icon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
                            testTag = FoodTags.PhotoMode,
                            onClick = { mode = "photo" },
                        )
                        ModeButton(
                            text = "手动记录",
                            icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            testTag = FoodTags.ManualMode,
                            onClick = { mode = "manual" },
                        )
                    }
                    "manual" -> {
                        Column(
                            modifier = Modifier.testTag(FoodTags.ManualEditor),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MealField(name, { name = it; nameError = null }, "餐食名称", FoodTags.ManualName, nameError)
                            MealField(calories, { calories = it; caloriesError = null }, "热量 kcal", FoodTags.ManualCalories, caloriesError)
                            MealField(protein, { protein = it; proteinError = null }, "蛋白质 g", FoodTags.ManualProtein, proteinError)
                            MealField(carbs, { carbs = it; carbsError = null }, "碳水 g", FoodTags.ManualCarbs, carbsError)
                            MealField(fat, { fat = it; fatError = null }, "脂肪 g", FoodTags.ManualFat, fatError)
                            operationError?.let { FoodError(it) }
                            FitnessPrimaryButton(
                                text = if (operationInProgress) "保存中…" else "保存餐食",
                                enabled = !operationInProgress,
                                testTag = FoodTags.SaveManualMeal,
                                onClick = {
                                    val parsedCalories = calories.toIntOrNull()
                                    val parsedProtein = protein.toDoubleOrNull()
                                    val parsedCarbs = carbs.toDoubleOrNull()
                                    val parsedFat = fat.toDoubleOrNull()
                                    nameError = if (name.isBlank()) "请输入餐食名称" else null
                                    caloriesError = if (parsedCalories == null || parsedCalories !in 0..5000) {
                                        "请输入 0 到 5000 之间的热量"
                                    } else null
                                    proteinError = parsedProtein.toMacroError()
                                    carbsError = parsedCarbs.toMacroError()
                                    fatError = parsedFat.toMacroError()
                                    if (listOf(nameError, caloriesError, proteinError, carbsError, fatError).all { it == null }) {
                                        operationInProgress = true
                                        operationError = null
                                        coroutineScope.launch {
                                            try {
                                                onSaveManualMeal(
                                                    name.trim(),
                                                    requireNotNull(parsedCalories),
                                                    requireNotNull(parsedProtein),
                                                    requireNotNull(parsedCarbs),
                                                    requireNotNull(parsedFat),
                                                )
                                                name = ""
                                                calories = ""
                                                protein = ""
                                                carbs = ""
                                                fat = ""
                                                showAddSheet = false
                                                mode = null
                                            } catch (cancellation: CancellationException) {
                                                throw cancellation
                                            } catch (error: Exception) {
                                                operationError = error.message ?: "保存餐食失败"
                                            } finally {
                                                operationInProgress = false
                                            }
                                        }
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.height(96.dp))
                        }
                    }
                    "photo" -> {
                        OutlinedButton(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = FitnessDimensions.MinimumTouchTarget),
                        ) {
                            Text(if (selectedPhotoUri == null) "选择照片" else "已选择照片")
                        }
                        OutlinedTextField(
                            value = photoDescription,
                            onValueChange = { photoDescription = it; operationError = null },
                            label = { Text("照片内容 / 食物描述") },
                            minLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(FoodTags.PhotoDescription),
                        )
                        operationError?.let { FoodError(it) }
                        FitnessPrimaryButton(
                            text = if (operationInProgress) "生成中…" else "生成估算草稿",
                            enabled = !operationInProgress,
                            testTag = FoodTags.GeneratePhotoDraft,
                            onClick = {
                                val description = photoDescription.trim().ifBlank {
                                    if (selectedPhotoUri != null) "已选择食物照片" else ""
                                }
                                if (description.isBlank()) {
                                    operationError = "请选择照片或描述食物"
                                } else if (!operationInProgress) {
                                    operationInProgress = true
                                    operationError = null
                                    coroutineScope.launch {
                                        try {
                                            val image = selectedPhotoUri
                                                ?.let(Uri::parse)
                                                ?.let { uri -> context.readFoodPhoto(uri) }
                                            onGeneratePhotoDraft(
                                                FoodPhotoInput(
                                                    description = description,
                                                    imageUri = image?.imageUri.orEmpty(),
                                                    imageMimeType = image?.imageMimeType.orEmpty(),
                                                    imageBase64 = image?.imageBase64.orEmpty(),
                                                ),
                                            )
                                            photoDescription = ""
                                            selectedPhotoUri = null
                                            showAddSheet = false
                                            mode = null
                                        } catch (cancellation: CancellationException) {
                                            throw cancellation
                                        } catch (error: Exception) {
                                            operationError = error.message ?: "生成饮食草稿失败"
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
                Text(summary.calories.toString(), fontSize = 48.sp, lineHeight = 54.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 14.dp))
                Text("目标 $target 千卡 · 剩余 $remaining", color = Color(0xFF9B9E95), fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = { (summary.calories.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    color = FitnessColors.Orange,
                    trackColor = Color(0xFF343630),
                    modifier = Modifier.fillMaxWidth().padding(top = 30.dp).height(8.dp).clip(RoundedCornerShape(99.dp)),
                )
            }
        }
        Column(Modifier.weight(.72f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FitnessSurfaceCard(modifier = Modifier.weight(1f).fillMaxWidth().testTag(FoodTags.TotalProtein).semantics(mergeDescendants = true) {}) {
                Text("蛋白质", style = MaterialTheme.typography.labelSmall, color = FitnessColors.Muted)
                Text("${summary.protein.toMacro()} 克", style = MaterialTheme.typography.headlineSmall)
            }
            FitnessSurfaceCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Text("碳水 / 脂肪", style = MaterialTheme.typography.labelSmall, color = FitnessColors.Muted)
                Text("${summary.carbs.toMacro()} 克", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag(FoodTags.TotalCarbs).semantics(mergeDescendants = true) {})
                Text("${summary.fat.toMacro()} 克脂肪", style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag(FoodTags.TotalFat).semantics(mergeDescendants = true) {})
            }
        }
    }
}

@Composable
private fun NutritionReferenceCard(summary: NutritionSummary) {
    val reference = summary.reference
    FitnessSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(FoodTags.NutritionReference),
    ) {
        Text("今日参考摄入", style = MaterialTheme.typography.headlineSmall)
        if (reference == null) {
            Text("完成训练档案后，会在这里显示基于你的本地档案计算的参考值。", style = MaterialTheme.typography.bodyMedium)
            return@FitnessSurfaceCard
        }
        Text("按档案估算，仅作日常记录参考；如有医疗或特殊饮食需求，请以专业人士建议为准。", style = MaterialTheme.typography.bodyMedium)
        NutritionReferenceRow("热量", summary.calories.toDouble(), reference.calories.toDouble(), "kcal")
        NutritionReferenceRow("蛋白质", summary.protein, reference.protein, "g")
        NutritionReferenceRow("碳水", summary.carbs, reference.carbs, "g")
        NutritionReferenceRow("脂肪", summary.fat, reference.fat, "g")
    }
}

@Composable
private fun NutritionReferenceRow(
    label: String,
    consumed: Double,
    reference: Double,
    unit: String,
) {
    val remaining = (reference - consumed).coerceAtLeast(0.0)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label ${consumed.toMacro()} / ${reference.toMacro()} $unit", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
        Text("还可参考 ${remaining.toMacro()} $unit", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ModeButton(
    text: String,
    icon: @Composable () -> Unit,
    testTag: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .testTag(testTag),
    ) {
        icon()
        Text(text, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun MealField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    error: String?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    )
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
                    "蛋白质 ${log.proteinGrams.toMacro()}g · 碳水 ${log.carbsGrams.toMacro()}g · 脂肪 ${log.fatGrams.toMacro()}g",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text("${log.calories} kcal", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
        }
    }
}

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
    const val GeneratePhotoDraft = "generate-photo-draft"
    const val PhotoDraft = "photo-food-draft"
    const val ConfirmPhotoDraft = "confirm-photo-draft"
    const val TotalCalories = "food-total-calories"
    const val TotalProtein = "food-total-protein"
    const val TotalCarbs = "food-total-carbs"
    const val TotalFat = "food-total-fat"
    const val NutritionReference = "nutrition-reference"

    fun log(id: String): String = "food-log-$id"
}

private data class FoodImagePayload(
    val imageUri: String,
    val imageMimeType: String,
    val imageBase64: String,
)

private suspend fun Context.readFoodPhoto(uri: Uri): FoodImagePayload = withContext(Dispatchers.IO) {
    val bytes = requireNotNull(contentResolver.openInputStream(uri)?.use { it.readBytes() }) {
        "无法读取选中的照片"
    }
    FoodImagePayload(
        imageUri = uri.toString(),
        imageMimeType = contentResolver.getType(uri) ?: "image/jpeg",
        imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
    )
}

private fun Double?.toMacroError(): String? =
    if (this == null || this < 0.0 || this > 1000.0) "请输入 0 到 1000 之间的数值" else null

private fun Double.toMacro(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.ROOT, "%.1f", this)

private val FoodErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6)
private val FoodErrorText = androidx.compose.ui.graphics.Color(0xFF690005)
