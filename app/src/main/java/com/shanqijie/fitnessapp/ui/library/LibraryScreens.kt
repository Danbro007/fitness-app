package com.shanqijie.fitnessapp.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shanqijie.fitnessapp.data.ExerciseMediaEntity
import com.shanqijie.fitnessapp.domain.ExerciseChineseNameTranslator
import com.shanqijie.fitnessapp.ui.components.FitnessGifImage
import com.shanqijie.fitnessapp.ui.components.FitnessPageHeader
import com.shanqijie.fitnessapp.ui.components.FitnessPrimaryButton
import com.shanqijie.fitnessapp.ui.components.FitnessSelectionChip
import com.shanqijie.fitnessapp.ui.components.FitnessSurfaceCard
import com.shanqijie.fitnessapp.ui.theme.FitnessColors
import com.shanqijie.fitnessapp.ui.theme.FitnessDimensions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun LibraryScreen(
    exercises: List<ExerciseMediaEntity>,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(ExerciseFilter.All) }
    val searchableExercises = remember(exercises) { exercises.map(::SearchableExercise) }
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val filteredExercises = remember(searchableExercises, normalizedQuery, selectedFilter) {
        searchableExercises.filter { exercise ->
            selectedFilter.matches(exercise) &&
                (normalizedQuery.isBlank() || exercise.searchText.contains(normalizedQuery))
        }.sortedBy { exercise ->
            FeaturedExerciseNames.indexOf(exercise.media.name.lowercase(Locale.ROOT))
                .takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .testTag(LibraryTags.Screen),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 0.dp, end = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索动作、肌群或器械") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = FitnessColors.Surface,
                    unfocusedContainerColor = FitnessColors.Surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(5.dp, RoundedCornerShape(22.dp))
                    .testTag(LibraryTags.Search),
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ExerciseFilter.entries) { filter ->
                FitnessSelectionChip(
                    label = filter.label,
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    testTag = if (filter == ExerciseFilter.Back) LibraryTags.FilterBack else null,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(filteredExercises, key = { it.media.exerciseId }) { exercise ->
                ExerciseGridCard(
                    exercise = exercise,
                    onClick = { onOpenExercise(exercise.media.exerciseId) },
                )
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("素材说明", style = MaterialTheme.typography.headlineSmall)
                        Text("离线可用", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Text("动作数据保留原始字段", color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
                        Text("界面名称通过中文翻译层展示，原始 API 与测试数据不会被改写。", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseGridCard(
    exercise: SearchableExercise,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .testTag(LibraryTags.result(exercise.media.exerciseId)),
        shape = RoundedCornerShape(FitnessDimensions.ContainerRadius),
        colors = CardDefaults.cardColors(containerColor = FitnessColors.Surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FitnessGifImage(
                assetPath = exercise.media.localPath,
                contentDescription = exercise.name,
                animated = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .background(FitnessColors.Phone, RoundedCornerShape(18.dp)),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = exercise.name,
                    color = FitnessColors.Ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(exercise.bodyPart, exercise.equipment)
                        .filter(String::isNotBlank)
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ExerciseDetailScreen(
    exercise: ExerciseMediaEntity,
    actionContextLabel: String,
    actionLabel: String,
    onAddExercise: suspend () -> Unit,
    modifier: Modifier,
) {
    val translatedName = ExerciseChineseNameTranslator.translate(exercise.name)
    val translatedBodyPart = ExerciseChineseNameTranslator.translate(exercise.bodyPart)
    val translatedEquipment = ExerciseChineseNameTranslator.translate(exercise.equipment)
    val translatedTarget = ExerciseChineseNameTranslator.translate(exercise.target)
    var adding by rememberSaveable(exercise.exerciseId) { mutableStateOf(false) }
    var errorMessage by rememberSaveable(exercise.exerciseId) { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone)
            .testTag(LibraryTags.Detail),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, top = 0.dp, end = 18.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), // coverage-exempt: compiler-generated LazyColumn content branch
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(FitnessColors.Surface, RoundedCornerShape(FitnessDimensions.LargeRadius)),
            ) {
                FitnessGifImage(
                    assetPath = exercise.localPath,
                    contentDescription = translatedName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        item { Text(translatedName, style = MaterialTheme.typography.headlineLarge) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseMetaPill("目标：${translatedTarget.ifBlank { translatedBodyPart }}")
                ExerciseMetaPill("器械：${translatedEquipment.ifBlank { "未标注" }}")
                ExerciseMetaPill("难度：中级")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("动作提示", style = MaterialTheme.typography.headlineSmall)
                Text("3 步", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
        itemsIndexed(
            listOf(
                "肩胛骨向后下方收紧，背部稳定贴住卧推凳。",
                "下放至胸部中下段附近，前臂尽量保持垂直。",
                "推起时不要锁死肘关节，保持胸部持续发力。",
            ),
        ) { index, tip ->
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                shape = RoundedCornerShape(22.dp),
                color = FitnessColors.Surface,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(shape = CircleShape, color = FitnessColors.Orange, modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(String.format(Locale.ROOT, "%02d", index + 1), color = FitnessColors.Ink, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Text(tip, color = FitnessColors.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
        errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    color = DetailErrorText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DetailErrorContainer, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        item {
            FitnessPrimaryButton(
                text = if (adding) "添加中…" else actionLabel,
                enabled = !adding,
                testTag = LibraryTags.AddExercise,
                onClick = {
                    if (!adding) {
                        adding = true
                        errorMessage = null
                        coroutineScope.launch {
                            try {
                                onAddExercise()
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (error: Exception) {
                                errorMessage = error.message ?: "添加动作失败"
                            } finally {
                                adding = false
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ExerciseMetaPill(text: String) {
    Surface(shape = RoundedCornerShape(99.dp), color = FitnessColors.Surface) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
    }
}

object LibraryTags {
    const val Screen = "library-screen"
    const val Search = "library-search"
    const val FilterBack = "library-filter-back"
    const val Detail = "exercise-detail"
    const val AddExercise = "add-exercise"

    fun result(exerciseId: String): String = "library-result-$exerciseId"
}

internal class SearchableExercise(
    val media: ExerciseMediaEntity,
) {
    val name: String by lazy(LazyThreadSafetyMode.NONE) {
        ExerciseChineseNameTranslator.translate(media.name)
    }
    val bodyPart: String by lazy(LazyThreadSafetyMode.NONE) {
        ExerciseChineseNameTranslator.translate(media.bodyPart)
    }
    val equipment: String by lazy(LazyThreadSafetyMode.NONE) {
        ExerciseChineseNameTranslator.translate(media.equipment)
    }
    val target: String by lazy(LazyThreadSafetyMode.NONE) {
        ExerciseChineseNameTranslator.translate(media.target)
    }
    val searchText: String by lazy(LazyThreadSafetyMode.NONE) {
        listOf(
            media.exerciseId,
            media.name,
            media.bodyPart,
            media.equipment,
            media.target,
            name,
            bodyPart,
            equipment,
            target,
        ).joinToString(" ").lowercase(Locale.ROOT)
    }
}

internal enum class ExerciseFilter(val label: String) {
    All("全部"),
    Chest("胸部"),
    Back("背部"),
    Legs("腿部"),
    Core("核心");

    fun matches(exercise: SearchableExercise): Boolean {
        val anatomy = listOf(
            exercise.media.bodyPart,
            exercise.media.target,
            exercise.bodyPart,
            exercise.target,
        ).joinToString(" ").lowercase(Locale.ROOT)
        return when (this) {
            All -> true
            Chest -> "胸" in anatomy || "chest" in anatomy || "pector" in anatomy
            Back -> "背" in anatomy || "back" in anatomy || "lat" in anatomy
            Legs -> listOf("腿", "臀", "股", "小腿", "leg", "glute", "quad", "hamstring", "calf")
                .any { it in anatomy }
            Core -> listOf("腹", "核心", "腰", "abs", "abdominal", "waist", "core")
                .any { it in anatomy }
        }
    }
}

private val FeaturedExerciseNames = listOf(
    "smith bench press",
    "dumbbell bench press",
    "dumbbell incline bench press",
    "lever seated fly",
)

private val DetailErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6)
private val DetailErrorText = androidx.compose.ui.graphics.Color(0xFF690005)
