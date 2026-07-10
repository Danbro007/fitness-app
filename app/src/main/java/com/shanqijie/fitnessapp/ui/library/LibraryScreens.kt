package com.shanqijie.fitnessapp.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FitnessPageHeader(
                title = "动作库",
                kicker = "${exercises.size} 个本地动作",
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索动作、部位或器械") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(LibraryTags.Search),
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
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

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "找到 ${filteredExercises.size} 个动作",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FitnessColors.Muted,
                )
            }
            items(filteredExercises, key = { it.media.exerciseId }) { exercise ->
                ExerciseResultRow(
                    exercise = exercise,
                    onClick = { onOpenExercise(exercise.media.exerciseId) },
                )
            }
        }
    }
}

@Composable
private fun ExerciseResultRow(
    exercise: SearchableExercise,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FitnessDimensions.MinimumTouchTarget)
            .testTag(LibraryTags.result(exercise.media.exerciseId)),
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
                assetPath = exercise.media.localPath,
                contentDescription = exercise.name,
                modifier = Modifier
                    .size(68.dp)
                    .background(FitnessColors.Phone, RoundedCornerShape(12.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
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
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = FitnessColors.Muted,
            )
        }
    }
}

@Composable
fun ExerciseDetailScreen(
    exercise: ExerciseMediaEntity,
    actionContextLabel: String,
    actionLabel: String,
    onAddExercise: suspend () -> Unit,
    modifier: Modifier = Modifier,
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { FitnessPageHeader(title = translatedName, kicker = "动作详情") }
        item {
            FitnessGifImage(
                assetPath = exercise.localPath,
                contentDescription = translatedName,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(FitnessColors.Surface, RoundedCornerShape(FitnessDimensions.LargeRadius)),
            )
        }
        item {
            FitnessSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                ExerciseDetailLine("训练部位", translatedBodyPart)
                ExerciseDetailLine("使用器械", translatedEquipment)
                ExerciseDetailLine("目标肌群", translatedTarget)
                Text(actionContextLabel, color = FitnessColors.Muted, style = MaterialTheme.typography.bodyMedium)
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
private fun ExerciseDetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = FitnessColors.Muted)
        Text(value.ifBlank { "未标注" }, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
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

private data class SearchableExercise(
    val media: ExerciseMediaEntity,
    val name: String = ExerciseChineseNameTranslator.translate(media.name),
    val bodyPart: String = ExerciseChineseNameTranslator.translate(media.bodyPart),
    val equipment: String = ExerciseChineseNameTranslator.translate(media.equipment),
    val target: String = ExerciseChineseNameTranslator.translate(media.target),
) {
    val searchText: String = listOf(
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

private enum class ExerciseFilter(val label: String) {
    All("全部"),
    Chest("胸"),
    Back("背"),
    Legs("腿"),
    Shoulders("肩"),
    Arms("手臂"),
    Core("腰腹");

    fun matches(exercise: SearchableExercise): Boolean {
        if (this == All) return true
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
            Shoulders -> "肩" in anatomy || "delt" in anatomy || "shoulder" in anatomy
            Arms -> listOf("手臂", "上臂", "前臂", "肘", "bicep", "tricep", "forearm", "arm")
                .any { it in anatomy }
            Core -> listOf("腹", "核心", "腰", "abs", "abdominal", "waist", "core")
                .any { it in anatomy }
        }
    }
}

private val DetailErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6)
private val DetailErrorText = androidx.compose.ui.graphics.Color(0xFF690005)
