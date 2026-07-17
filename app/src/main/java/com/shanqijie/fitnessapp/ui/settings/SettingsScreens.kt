package com.shanqijie.fitnessapp.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shanqijie.fitnessapp.ai.AiTestResult
import com.shanqijie.fitnessapp.ai.AiProviderCatalog
import com.shanqijie.fitnessapp.data.AiProviderEntity
import com.shanqijie.fitnessapp.data.EquipmentEntity
import com.shanqijie.fitnessapp.data.FitnessBackupCodec
import com.shanqijie.fitnessapp.data.FitnessBackupPreview
import com.shanqijie.fitnessapp.data.TrainingVenueEntity
import com.shanqijie.fitnessapp.domain.ExerciseChineseNameTranslator
import com.shanqijie.fitnessapp.ui.components.FitnessPageHeader
import com.shanqijie.fitnessapp.ui.components.FitnessPrimaryButton
import com.shanqijie.fitnessapp.ui.components.FitnessSurfaceCard
import com.shanqijie.fitnessapp.ui.theme.FitnessColors
import com.shanqijie.fitnessapp.ui.theme.FitnessDimensions
import com.shanqijie.fitnessapp.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun VenueSettingsScreen(
    currentVenue: TrainingVenueEntity?,
    venues: List<TrainingVenueEntity>,
    equipment: List<EquipmentEntity>,
    enabledEquipmentIds: Set<String>,
    onRenameVenue: suspend (String) -> Unit,
    onOpenEquipmentFilter: () -> Unit,
    modifier: Modifier,
) {
    val initialVenueName = currentVenue?.name ?: ""
    var venueName by rememberSaveable(currentVenue?.updatedAt) { mutableStateOf(initialVenueName) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    SettingsColumn(modifier.testTag(SettingsTags.VenueScreen)) {
        SettingsSoftTextField(
            label = "当前训练场地",
            value = venueName,
            onValueChange = { venueName = it; message = null },
        )
        SettingsSectionHeader("可用器械", "已选 ${enabledEquipmentIds.size} 项")
        equipment.sortedWith(
            compareByDescending<EquipmentEntity> { it.id in enabledEquipmentIds }
                .thenBy { venueEquipmentOrder(it.id) }
                .thenBy { it.name },
        )
            .take(6)
            .chunked(3)
            .forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowItems.forEach { item ->
                        val selected = item.id in enabledEquipmentIds
                        Surface(
                            onClick = onOpenEquipmentFilter,
                            modifier = Modifier
                                .weight(1f)
                                .height(90.dp)
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = if (selected) FitnessColors.Orange else Color.Transparent,
                                    shape = RoundedCornerShape(22.dp),
                                ),
                            shape = RoundedCornerShape(22.dp),
                            color = FitnessColors.SurfaceStrong,
                            shadowElevation = if (selected) 3.dp else 6.dp,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(item.name, color = FitnessColors.Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                                if (selected) Text("已选择", color = FitnessColors.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    repeat(3 - rowItems.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                }
            }
        OutlinedButton(
            onClick = onOpenEquipmentFilter,
            modifier = Modifier.fillMaxWidth().heightIn(min = FitnessDimensions.MinimumTouchTarget),
            shape = RoundedCornerShape(22.dp),
        ) {
            Text("搜索并筛选全部器械")
        }
        SettingsSectionHeader("排课规则", "自动约束")
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            SettingToggleRow("优先使用已选器械", "避免生成现场无法执行的动作", true)
            SettingToggleRow("允许徒手替代", "器械占用时提供替代动作", true)
        }
        FitnessPrimaryButton(
            text = "保存场地设置",
            enabled = !busy && currentVenue != null,
            onClick = {
                runSettingAction(coroutineScope, { busy = it }, { message = it }) {
                    onRenameVenue(venueName)
                }
            },
        )
        message?.let { SettingsMessage(it) }
    }
}

private fun equipmentCategoryOrder(category: String): Int = when (category) {
    "machine" -> 0
    "free-weight" -> 1
    "accessory" -> 2
    "cardio" -> 3
    "body-weight" -> 4
    else -> 5
}

@Composable
fun EquipmentFilterScreen(
    equipment: List<EquipmentEntity>,
    enabledEquipmentIds: Set<String>,
    onSave: suspend (Set<String>) -> Unit,
    modifier: Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedIds by rememberSaveable(enabledEquipmentIds) {
        mutableStateOf(enabledEquipmentIds.sorted())
    }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val normalizedQuery = query.trim()
    val visibleEquipment = remember(equipment, normalizedQuery, selectedCategory) {
        equipment
            .asSequence()
            .filter { selectedCategory == null || it.category == selectedCategory }
            .filter { item ->
                normalizedQuery.isBlank() ||
                    equipmentSearchKeywords(item).any { keyword ->
                        keyword.contains(normalizedQuery, ignoreCase = true)
                    }
            }
            .sortedWith(compareBy<EquipmentEntity> { equipmentCategoryOrder(it.category) }.thenBy { it.name })
            .toList()
    }
    val categories = remember(equipment) {
        equipment.map { it.category }.distinct().sortedBy(::equipmentCategoryOrder)
    }
    val selectedIdSet = remember(selectedIds) { selectedIds.toSet() }
    val visibleEquipmentGroups = remember(visibleEquipment) {
        visibleEquipment.groupBy { it.category }
            .toList()
            .sortedBy { (category, _) -> equipmentCategoryOrder(category) }
    }

    SettingsColumn(modifier.testTag(SettingsTags.EquipmentFilterScreen)) {
        androidx.compose.foundation.layout.Box(Modifier.testTag(SettingsTags.EquipmentSearch)) {
            SettingsSoftTextField(
                label = "搜索器械",
                value = query,
                onValueChange = { query = it; message = null },
            )
        }
        Text("可输入器械名称或类型，例如“哑铃”“有氧”。", style = MaterialTheme.typography.bodyMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf<String?>(null) + categories).chunked(3).forEach { rowCategories ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowCategories.forEach { category ->
                        val selected = selectedCategory == category
                        Surface(
                            onClick = { selectedCategory = category },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) FitnessColors.Orange else FitnessColors.Surface,
                            shadowElevation = 4.dp,
                        ) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                Text(
                                    category?.let(::equipmentCategoryLabel) ?: "全部",
                                    color = FitnessColors.Ink,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    repeat(3 - rowCategories.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${visibleEquipment.size} 种器械 · 已选 ${selectedIds.size} 种", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Row {
                TextButton(onClick = {
                    selectedIds = (selectedIds + visibleEquipment.map { it.id }).distinct().sorted()
                }) { Text("全选当前") }
                TextButton(onClick = {
                    val visibleIds = visibleEquipment.mapTo(mutableSetOf()) { it.id }
                    selectedIds = selectedIds.filterNot { it in visibleIds }
                }) { Text("清空当前") }
            }
        }
        visibleEquipmentGroups.forEach { (category, items) ->
                SettingsSectionHeader(equipmentCategoryLabel(category), "${items.count { it.id in selectedIdSet }} / ${items.size}")
                items.forEach { item ->
                    val checked = item.id in selectedIdSet
                    Surface(
                        onClick = {
                            selectedIds = if (checked) {
                                selectedIds - item.id
                            } else {
                                (selectedIds + item.id).distinct().sorted()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .border(
                                width = if (checked) 2.dp else 0.dp,
                                color = if (checked) FitnessColors.Orange else Color.Transparent,
                                shape = RoundedCornerShape(22.dp),
                            ),
                        shape = RoundedCornerShape(22.dp),
                        color = FitnessColors.SurfaceStrong,
                        shadowElevation = if (checked) 2.dp else 5.dp,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                                Text(item.name, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                                Text(equipmentCategoryLabel(item.category), style = MaterialTheme.typography.bodyMedium)
                            }
                            Surface(
                                shape = CircleShape,
                                color = if (checked) FitnessColors.Orange else FitnessColors.SurfaceStrong,
                                modifier = Modifier
                                    .size(30.dp)
                                    .border(
                                        width = if (checked) 0.dp else 1.5.dp,
                                        color = if (checked) Color.Transparent else FitnessColors.Muted.copy(alpha = .55f),
                                        shape = CircleShape,
                                    ),
                            ) {
                                if (checked) {
                                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Check, contentDescription = "已选择", tint = FitnessColors.Ink, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        FitnessPrimaryButton(
            text = if (busy) "保存中…" else "保存器械筛选",
            enabled = !busy,
            onClick = {
                runSettingAction(coroutineScope, { busy = it }, { message = it }) {
                    onSave(selectedIdSet)
                }
            },
        )
        message?.let { SettingsMessage(it) }
    }
}

private fun venueEquipmentOrder(id: String): Int = listOf(
    "equipment-dumbbell",
    "equipment-smith-machine",
    "equipment-barbell",
    "equipment-cable",
    "equipment-treadmill",
    "equipment-stationary-bike",
).indexOf(id).takeIf { it >= 0 } ?: Int.MAX_VALUE

@Composable
private fun SettingsSoftTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = FitnessColors.Muted, fontWeight = FontWeight.Bold)
        val shape = RoundedCornerShape(22.dp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = FitnessColors.Ink),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .shadow(5.dp, shape)
                .background(FitnessColors.SurfaceStrong, shape)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        )
    }
}

private fun equipmentCategoryLabel(category: String): String = when (category) {
    "machine" -> "固定器械"
    "free-weight" -> "自由重量"
    "accessory" -> "辅助器材"
    "cardio" -> "有氧器械"
    "body-weight" -> "自重训练"
    else -> "其他"
}

private fun equipmentSearchKeywords(item: EquipmentEntity): List<String> {
    val aliases = buildList {
        when {
            "椭圆机" in item.name -> addAll(listOf("椭圆仪", "交叉训练机"))
            "龙门架" in item.name || "拉力器" in item.name -> addAll(listOf("龙门", "绳索", "飞鸟", "cable"))
            "夹胸" in item.name || "蝴蝶机" in item.name -> addAll(listOf("夹胸", "飞鸟", "蝴蝶机"))
            "腿举" in item.name || "蹬腿" in item.name -> addAll(listOf("腿举", "蹬腿", "腿推"))
            "高位下拉" in item.name -> addAll(listOf("高拉", "下拉", "背部"))
            "推胸" in item.name || "胸推" in item.name -> addAll(listOf("推胸", "胸推", "卧推"))
            "推肩" in item.name || "推举" in item.name -> addAll(listOf("推肩", "肩推", "推举"))
            "腿屈伸" in item.name -> addAll(listOf("腿伸展", "伸腿"))
            "腿弯举" in item.name -> addAll(listOf("腿弯曲", "屈腿"))
            "髋外展" in item.name -> addAll(listOf("坐姿外展", "臀外展", "外展"))
            "辅助引体" in item.name -> addAll(listOf("引体向上", "助力引体"))
            "固定单车" in item.name -> addAll(listOf("健身车", "自行车", "单车"))
        }
    }
    return listOf(item.name, equipmentCategoryLabel(item.category)) + aliases
}

@Composable
fun SmartSettingsScreen(
    providers: List<AiProviderEntity>,
    onSelectProvider: suspend (String, String, String) -> Unit,
    onSaveApiKey: suspend (String, String) -> Unit,
    onTestConnection: suspend (String) -> AiTestResult,
    modifier: Modifier = Modifier,
) {
    var selectedProviderId by rememberSaveable(providers) {
        mutableStateOf(providers.firstOrNull { it.enabled }?.id ?: AiProviderCatalog.entries.first().id)
    }
    val catalog = AiProviderCatalog.entry(selectedProviderId) ?: AiProviderCatalog.entries.first()
    val provider = providers.firstOrNull { it.id == selectedProviderId }
    var endpoint by rememberSaveable(selectedProviderId) {
        mutableStateOf(provider?.baseUrl?.takeIf { it in catalog.endpoints } ?: catalog.endpoints.first())
    }
    var model by rememberSaveable(selectedProviderId) {
        mutableStateOf(provider?.model ?: catalog.models.first())
    }
    var apiKey by remember { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var verifiedProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    SettingsColumn(modifier.testTag(SettingsTags.SmartScreen)) {
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                when {
                    verifiedProviderId == selectedProviderId -> "已连接"
                    provider?.apiKeyStored == true -> "已保存，尚未验证"
                    else -> "尚未填写"
                },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag(SettingsTags.SmartConnectionStatus),
            )
            Text(
                if (provider?.apiKeyStored == true) "连接凭据已保存在本机安全存储中，可点击下方按钮验证。"
                else "训练记录、计划执行、动作库和饮食手动记录不依赖 AI 服务。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("服务配置", style = MaterialTheme.typography.headlineSmall)
                Text(catalog.displayName, color = FitnessColors.Muted, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProviderCatalog.entries.forEach { item ->
                    Surface(
                        onClick = {
                            selectedProviderId = item.id
                            endpoint = providers.firstOrNull { it.id == item.id }?.baseUrl
                                ?.takeIf { it in item.endpoints } ?: item.endpoints.first()
                            model = providers.firstOrNull { it.id == item.id }?.model ?: item.models.first()
                            message = null
                        },
                        color = if (selectedProviderId == item.id) FitnessColors.Orange else FitnessColors.SurfaceStrong,
                        shape = RoundedCornerShape(22.dp),
                        shadowElevation = 6.dp,
                        modifier = Modifier.weight(1f).height(104.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Image(painterResource(providerLogo(item.id)), contentDescription = "${item.displayName} Logo", modifier = Modifier.size(38.dp).clip(CircleShape))
                            Text(item.displayName, color = FitnessColors.Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Text(providerOwner(item.id), style = MaterialTheme.typography.labelSmall, color = FitnessColors.Muted)
                        }
                    }
                }
            }
            CatalogDropdown("接口地址", endpoint, catalog.endpoints) {
                endpoint = it; message = null
            }
            Text("系统会使用所选服务商的官方地址，无需手动输入或记忆。", style = MaterialTheme.typography.labelSmall, color = FitnessColors.Muted)
            SettingsSecretField(
                value = apiKey,
                placeholder = if (provider?.apiKeyStored == true) "密钥已保存，留空即可测试" else "请输入接口密钥",
                onValueChange = { apiKey = it; message = null; verifiedProviderId = null },
            )
            Text("密钥只保存在本机安全存储中。", style = MaterialTheme.typography.labelSmall, color = FitnessColors.Muted)
            CatalogDropdown("模型", model, catalog.models.distinct().let { options ->
                if (model in options) options else listOf(model) + options
            }) {
                model = it; message = null
            }
            Text("模型选项会随服务商自动更新。", style = MaterialTheme.typography.labelSmall, color = FitnessColors.Muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("数据边界", style = MaterialTheme.typography.headlineSmall)
            Text("发送前可见", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("训练计划建议", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                    Text("发送训练偏好、全部体测数据与器械约束", style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = true, onCheckedChange = null)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("饮食照片估算", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                    Text("仅在你选择照片后发送", style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = false, onCheckedChange = null)
            }
        }
        FitnessPrimaryButton(
            text = if (provider?.apiKeyStored == true) "重新测试连接" else "保存并测试连接",
            enabled = !busy && (apiKey.isNotBlank() || provider?.apiKeyStored == true),
            testTag = SettingsTags.SaveSmartKey,
            onClick = {
                if (!busy) {
                    busy = true
                    message = null
                    coroutineScope.launch {
                        try {
                            onSelectProvider(selectedProviderId, endpoint, model)
                            if (apiKey.isNotBlank()) {
                                onSaveApiKey(selectedProviderId, apiKey)
                                apiKey = ""
                            }
                            val result = onTestConnection(selectedProviderId)
                            verifiedProviderId = selectedProviderId.takeIf { result.success }
                            message = result.message
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Exception) {
                            message = error.message ?: "测试连接失败"
                        } finally {
                            busy = false
                        }
                    }
                }
            },
        )
        message?.let { SettingsMessage(it) }
    }
}

@Composable
private fun SettingsSecretField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("接口密钥", style = MaterialTheme.typography.labelSmall, color = FitnessColors.Muted, fontWeight = FontWeight.Bold)
        val shape = RoundedCornerShape(22.dp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = FitnessColors.Ink),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, color = FitnessColors.Muted)
                inner()
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .shadow(5.dp, shape)
                .background(FitnessColors.SurfaceStrong, shape)
                .padding(horizontal = 18.dp, vertical = 17.dp)
                .testTag(SettingsTags.SmartApiKey),
        )
    }
}

@Composable
private fun CatalogDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = FitnessColors.Muted, fontWeight = FontWeight.Bold)
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(22.dp),
            color = FitnessColors.SurfaceStrong,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) { Text(catalogValueLabel(value), maxLines = 1) } }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

private fun catalogValueLabel(value: String): String = when (value) {
    "https://api.openai.com/v1" -> "OpenAI 官方接口"
    "gpt-5-mini" -> "GPT-5 mini（推荐）"
    else -> value
}

private fun providerLogo(id: String): Int = when (id) {
    "openai" -> R.drawable.provider_openai
    "gemini" -> R.drawable.provider_gemini
    else -> R.drawable.provider_qwen
}

private fun providerOwner(id: String): String = when (id) {
    "openai" -> "官方"
    "gemini" -> "Google"
    else -> "阿里云百炼"
}

@Composable
fun BackupSettingsScreen(
    onExportBackup: suspend () -> String,
    onImportBackup: suspend (String) -> Unit,
    onResetLocalData: suspend () -> Unit,
    onResetComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pendingBackup by remember { mutableStateOf("") }
    var pendingImportJson by remember { mutableStateOf("") }
    var pendingImportPreview by remember { mutableStateOf<FitnessBackupPreview?>(null) }
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null || pendingBackup.isBlank()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingBackup) }
                        ?: error("无法写入备份文件")
                }
            }.onSuccess {
                message = "备份文件已保存"
            }.onFailure { error ->
                message = error.message ?: "保存备份失败"
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        message = null
        coroutineScope.launch {
            try {
                val (rawJson, preview) = withContext(Dispatchers.IO) {
                    val declaredLength = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                    val json = context.contentResolver.openInputStream(uri)?.use {
                        FitnessBackupCodec.readBounded(it, declaredLength)
                    } ?: error("无法读取备份文件")
                    json to FitnessBackupCodec.preview(json)
                }
                pendingImportJson = rawJson
                pendingImportPreview = preview
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                message = error.message ?: "恢复备份失败"
            } finally {
                busy = false
            }
        }
    }

    SettingsColumn(modifier.testTag(SettingsTags.BackupScreen)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(FitnessDimensions.ContainerRadius),
            color = FitnessColors.Orange,
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("你的数据保存在本机", style = MaterialTheme.typography.headlineSmall)
                Text("备份包含训练档案、计划、训练记录与饮食记录，不包含 AI 接口密钥。", color = FitnessColors.Ink)
            }
        }
        SettingsSectionHeader("备份操作", "JSON 文件")
        BackupActionRow("↓", if (busy) "正在准备备份" else "导出本机备份", "最近导出：从未", !busy) {
            if (!busy) {
                busy = true
                message = null
                coroutineScope.launch {
                    try {
                        pendingBackup = onExportBackup()
                        exportLauncher.launch("fitness-backup-${LocalDate.now()}.json")
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        message = error.message ?: "生成备份失败"
                    } finally {
                        busy = false
                    }
                }
            }
        }
        BackupActionRow("↑", "从备份恢复", "恢复前会先校验文件", !busy) {
            importLauncher.launch(arrayOf("application/json", "text/*"))
        }
        SettingsSectionHeader("危险操作", "不可撤销")
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text("重置本机数据", style = MaterialTheme.typography.headlineSmall)
            Text("清除个人档案、计划、训练与饮食记录，并重新进入首次设置。动作素材不会删除。", style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { showResetConfirmation = true },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag(SettingsTags.RequestReset),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2B1D1B),
                    contentColor = Color(0xFFFF9E92),
                ),
            ) { Text("重置所有本机数据", color = Color(0xFFFF9E92)) }
        }
        message?.let { SettingsMessage(it) }
    }

    pendingImportPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    pendingImportJson = ""
                    pendingImportPreview = null
                }
            },
            title = { Text("确认恢复此备份？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("备份版本：${preview.version}")
                    Text("导出时间：${formatBackupTime(preview.exportedAt)}")
                    Text("档案 ${preview.profileCount} · 计划 ${preview.planCount} · 训练 ${preview.sessionCount}")
                    Text("组记录 ${preview.setCount} · 饮食 ${preview.foodCount}")
                    Text("恢复将替换当前本机数据。确认后会先自动保存一份恢复前快照。")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!busy && pendingImportJson.isNotBlank()) {
                            busy = true
                            coroutineScope.launch {
                                try {
                                    onImportBackup(pendingImportJson)
                                    pendingImportJson = ""
                                    pendingImportPreview = null
                                    message = "本地数据已恢复，恢复前快照已保存"
                                } catch (cancellation: CancellationException) {
                                    throw cancellation
                                } catch (error: Exception) {
                                    message = error.message ?: "恢复备份失败"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.testTag(SettingsTags.ConfirmImport),
                ) { Text("确认恢复") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingImportJson = ""
                        pendingImportPreview = null
                    },
                    enabled = !busy,
                    modifier = Modifier.testTag(SettingsTags.CancelImport),
                ) { Text("取消") }
            },
            modifier = Modifier.testTag(SettingsTags.ImportDialog),
        )
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!busy) showResetConfirmation = false }, // coverage-exempt: compiler-generated dialog callback branch; busy states are tested
            title = { Text("确认重置本地数据？") },
            text = { Text("此操作不可撤销。动作素材仍保留在本机。") },
            confirmButton = {
                Button(
                    onClick = {
                        if (!busy) {
                            busy = true
                            coroutineScope.launch {
                                try {
                                    onResetLocalData()
                                    showResetConfirmation = false
                                    onResetComplete()
                                } catch (cancellation: CancellationException) {
                                    throw cancellation
                                } catch (error: Exception) {
                                    message = error.message ?: "重置本地数据失败"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag(SettingsTags.ConfirmReset),
                ) { Text("确认重置") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }, enabled = !busy) { Text("取消") }
            },
            modifier = Modifier.testTag(SettingsTags.ResetDialog),
        )
    }
}

@Composable
fun AboutScreen(modifier: Modifier) {
    SettingsColumn(modifier.testTag(SettingsTags.AboutScreen)) {
        Spacer(Modifier.height(1.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(
                modifier = Modifier.size(128.dp),
                shape = RoundedCornerShape(34.dp),
                color = FitnessColors.Orange,
                shadowElevation = 7.dp,
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Text("iF", color = FitnessColors.Ink, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                }
            }
            Text("i fitness", style = MaterialTheme.typography.headlineLarge)
            Text(
                "一款本地优先的个人训练助手。没有账号、没有云同步；\n你的正式记录由你确认后保存在设备中。",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        StaticInfoRow("✓", "隐私原则", "AI 输出不自动落库")
        StaticInfoRow("▤", "本地数据", "SQLite · 本机备份")
        StaticInfoRow("S", "动作素材", "1324 个本地 GIF")
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text("版本 0.1 · 交互原型", style = MaterialTheme.typography.headlineSmall)
            Text("本页展示未来主义新拟态视觉方向，不代表原生 Android 已完成迁移。", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StaticInfoRow(symbol: String, title: String, subtitle: String) {
    Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(23.dp), color = FitnessColors.Surface, shadowElevation = 6.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = FitnessColors.Phone, modifier = Modifier.size(44.dp)) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text(symbol, color = FitnessColors.Ink, fontWeight = FontWeight.ExtraBold) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SettingsColumn(
    modifier: Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize(), color = FitnessColors.Phone) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String, meta: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(meta, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingToggleRow(title: String, subtitle: String, checked: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun BackupActionRow(
    symbol: String,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(23.dp),
        color = FitnessColors.Surface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = FitnessColors.Phone, modifier = Modifier.size(44.dp)) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Text(symbol, color = FitnessColors.Ink, fontWeight = FontWeight.ExtraBold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            Text("›", color = FitnessColors.Muted, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

private fun runSettingAction(
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    setBusy: (Boolean) -> Unit,
    setMessage: (String?) -> Unit,
    action: suspend () -> Unit,
) {
    setBusy(true)
    setMessage(null)
    coroutineScope.launch {
        try {
            action()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            setMessage(error.message ?: "保存设置失败")
        } finally {
            setBusy(false)
        }
    }
}

@Composable
private fun SettingsMessage(message: String) {
    Text(
        text = message,
        color = FitnessColors.Ink,
        modifier = Modifier
            .fillMaxWidth()
            .background(FitnessColors.Surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

object SettingsTags {
    const val VenueScreen = "venue-settings"
    const val EquipmentFilterScreen = "equipment-filter-screen"
    const val EquipmentSearch = "equipment-search"
    const val SmartScreen = "smart-settings"
    const val SmartConnectionStatus = "smart-connection-status"
    const val SmartApiKey = "smart-api-key"
    const val SaveSmartKey = "save-smart-key"
    const val BackupScreen = "backup-screen"
    const val ImportDialog = "import-dialog"
    const val ConfirmImport = "confirm-import"
    const val CancelImport = "cancel-import"
    const val RequestReset = "request-reset"
    const val ResetDialog = "reset-dialog"
    const val ConfirmReset = "confirm-reset"
    const val AboutScreen = "about-screen"
}

private fun formatBackupTime(epochMillis: Long): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
}.getOrDefault("未知")

private val ResetColor = Color(0xFFB3261E)
