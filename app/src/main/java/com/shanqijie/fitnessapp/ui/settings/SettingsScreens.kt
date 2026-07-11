package com.shanqijie.fitnessapp.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.shanqijie.fitnessapp.ai.AiTestResult
import com.shanqijie.fitnessapp.ai.AiProviderCatalog
import com.shanqijie.fitnessapp.data.AiProviderEntity
import com.shanqijie.fitnessapp.data.EquipmentEntity
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

@Composable
fun VenueSettingsScreen(
    currentVenue: TrainingVenueEntity?,
    venues: List<TrainingVenueEntity>,
    equipment: List<EquipmentEntity>,
    enabledEquipmentIds: Set<String>,
    onRenameVenue: suspend (String) -> Unit,
    onAddVenue: suspend (String) -> Unit,
    onSetDefaultVenue: suspend (String) -> Unit,
    onToggleEquipment: suspend (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var venueName by rememberSaveable(currentVenue?.updatedAt) { mutableStateOf(currentVenue?.name.orEmpty()) }
    var newVenueName by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    SettingsColumn(modifier.testTag(SettingsTags.VenueScreen)) {
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text("当前场地", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = venueName,
                onValueChange = { venueName = it; message = null },
                label = { Text("场地名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FitnessPrimaryButton(
                text = "保存场地名称",
                enabled = !busy && currentVenue != null,
                onClick = {
                    runSettingAction(coroutineScope, { busy = it }, { message = it }) {
                        onRenameVenue(venueName)
                    }
                },
            )
        }
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text("场地列表", style = MaterialTheme.typography.headlineSmall)
            venues.forEach { venue ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = FitnessDimensions.MinimumTouchTarget),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(venue.name, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                        Text(if (venue.isDefault) "默认场地" else "可设为默认", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!venue.isDefault) {
                        TextButton(
                            onClick = {
                                runSettingAction(coroutineScope, { busy = it }, { message = it }) {
                                    onSetDefaultVenue(venue.id)
                                }
                            },
                        ) { Text("设为默认") }
                    }
                }
            }
            OutlinedTextField(
                value = newVenueName,
                onValueChange = { newVenueName = it; message = null },
                label = { Text("新场地") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    runSettingAction(coroutineScope, { busy = it }, { message = it }) {
                        onAddVenue(newVenueName)
                        newVenueName = ""
                    }
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FitnessDimensions.MinimumTouchTarget),
            ) { Text("添加场地") }
        }
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text("可用器械", style = MaterialTheme.typography.headlineSmall)
            equipment.groupBy { it.category }
                .toSortedMap(compareBy(::equipmentCategoryOrder))
                .forEach { (category, items) ->
                    Text(equipmentCategoryLabel(category), color = FitnessColors.Muted, fontWeight = FontWeight.Bold)
                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = FitnessDimensions.MinimumTouchTarget)
                                .clickable(enabled = currentVenue != null && !busy) {
                                    runSettingAction(coroutineScope, { busy = it }, { message = it }) {
                                        onToggleEquipment(item.id, item.id !in enabledEquipmentIds)
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = item.id in enabledEquipmentIds,
                                onCheckedChange = { enabled ->
                                    if (currentVenue != null && !busy) {
                                        runSettingAction(coroutineScope, { busy = it }, { message = it }) {
                                            onToggleEquipment(item.id, enabled)
                                        }
                                    }
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                                Text(ExerciseChineseNameTranslator.translate(item.category), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
        }
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

private fun equipmentCategoryLabel(category: String): String = when (category) {
    "machine" -> "固定器械"
    "free-weight" -> "自由重量"
    "accessory" -> "辅助器材"
    "cardio" -> "有氧器械"
    "body-weight" -> "自重训练"
    else -> "其他"
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
    var apiKey by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    SettingsColumn(modifier.testTag(SettingsTags.SmartScreen)) {
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text(if (provider?.apiKeyStored == true) "当前已连接" else "当前未连接", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag(SettingsTags.SmartConnectionStatus))
            Text("训练记录、计划执行、动作库和饮食手动记录不依赖 AI 服务。", style = MaterialTheme.typography.bodyMedium)
        }
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
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
                            runSettingAction(coroutineScope, { busy = it }, { message = it }) {
                                onSelectProvider(item.id, endpoint, model)
                            }
                        },
                        color = if (selectedProviderId == item.id) FitnessColors.Orange else FitnessColors.SurfaceStrong,
                        shape = RoundedCornerShape(22.dp),
                        shadowElevation = 6.dp,
                        modifier = Modifier.weight(1f).heightIn(min = 104.dp),
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
                runSettingAction(coroutineScope, { busy = it }, { message = it }) { onSelectProvider(selectedProviderId, endpoint, model) }
            }
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; message = null },
                label = { Text("接口密钥") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(
                        onClick = {
                            if (!busy) {
                                busy = true
                                coroutineScope.launch {
                                    try {
                                        onSaveApiKey(selectedProviderId, apiKey)
                                        apiKey = ""
                                        message = "密钥已安全保存在本机"
                                    } catch (error: Exception) {
                                        message = error.message ?: "保存密钥失败"
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag(SettingsTags.SaveSmartKey),
                    ) { Text(if (busy) "保存中" else "保存") }
                },
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().testTag(SettingsTags.SmartApiKey),
            )
            CatalogDropdown("模型", model, catalog.models.distinct().let { options ->
                if (model in options) options else listOf(model) + options
            }) {
                model = it; message = null
                runSettingAction(coroutineScope, { busy = it }, { message = it }) { onSelectProvider(selectedProviderId, endpoint, model) }
            }
            OutlinedButton(
                onClick = {
                    if (!busy) {
                        busy = true
                        message = null
                        coroutineScope.launch {
                            try {
                                val result = onTestConnection(selectedProviderId)
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
                enabled = !busy && provider?.apiKeyStored == true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FitnessDimensions.MinimumTouchTarget),
            ) { Text("检查连接") }
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
        message?.let { SettingsMessage(it) }
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
        ) { Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) { Text(value, maxLines = 1) } }
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
                val rawJson = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("无法读取备份文件")
                }
                onImportBackup(rawJson)
                message = "本地数据已恢复"
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
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text("本地备份", style = MaterialTheme.typography.headlineSmall)
            Text("备份包含档案、计划、训练和饮食记录，不包含明文 API Key。", style = MaterialTheme.typography.bodyMedium)
            FitnessPrimaryButton(
                text = if (busy) "准备中…" else "导出备份",
                enabled = !busy,
                onClick = {
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
                },
            )
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FitnessDimensions.MinimumTouchTarget),
            ) { Text("从文件恢复") }
        }
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text("重置本地数据", style = MaterialTheme.typography.headlineSmall)
            Text("会清除个人档案、训练、饮食、草稿和本机密钥，然后恢复基础计划。", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { showResetConfirmation = true },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FitnessDimensions.MinimumTouchTarget)
                    .testTag(SettingsTags.RequestReset),
            ) { Text("重置本地数据", color = ResetColor) }
        }
        message?.let { SettingsMessage(it) }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!busy) showResetConfirmation = false },
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
fun AboutScreen(modifier: Modifier = Modifier) {
    SettingsColumn(modifier.testTag(SettingsTags.AboutScreen)) {
        FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text("数据在你手里", style = MaterialTheme.typography.headlineSmall)
            Text("训练、计划、饮食和档案默认保存在本机，无账号、无云同步。", style = MaterialTheme.typography.bodyLarge)
            Text("AI 只用于生成建议和草稿，正式数据需要你确认。", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SettingsColumn(
    modifier: Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
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
    const val SmartScreen = "smart-settings"
    const val SmartConnectionStatus = "smart-connection-status"
    const val SmartApiKey = "smart-api-key"
    const val SaveSmartKey = "save-smart-key"
    const val BackupScreen = "backup-screen"
    const val RequestReset = "request-reset"
    const val ResetDialog = "reset-dialog"
    const val ConfirmReset = "confirm-reset"
    const val AboutScreen = "about-screen"
}

private val ResetColor = Color(0xFFB3261E)
