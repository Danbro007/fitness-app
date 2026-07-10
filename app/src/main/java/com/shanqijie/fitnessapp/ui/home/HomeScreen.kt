package com.shanqijie.fitnessapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanqijie.fitnessapp.ui.components.FitnessGifImage
import com.shanqijie.fitnessapp.ui.components.FitnessPrimaryButton
import com.shanqijie.fitnessapp.ui.model.HomeUiState
import com.shanqijie.fitnessapp.ui.navigation.AppRoute
import com.shanqijie.fitnessapp.ui.navigation.FitnessTestTags
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab
import com.shanqijie.fitnessapp.ui.theme.FitnessColors
import com.shanqijie.fitnessapp.ui.theme.FitnessDimensions

data class HomeDayUi(
    val dayLabel: String,
    val workoutLabel: String,
    val completed: Boolean,
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    weekDays: List<HomeDayUi>,
    onNavigate: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
    heroAssetPath: String? = null,
    heroTitle: String = state.nextWorkout?.name ?: "安排下一次训练",
    venueName: String = "本地训练",
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            HomeGreeting(
                venueName = venueName,
                workoutName = heroTitle,
            )
        }
        item {
            HomeWorkoutHero(
                state = state,
                heroAssetPath = heroAssetPath,
                heroTitle = heroTitle,
                onNavigate = onNavigate,
            )
        }
        item {
            WeeklyProgress(
                completed = state.completedThisWeek,
                target = state.targetThisWeek,
                weekDays = weekDays.take(4),
            )
        }
        item {
            HomeQuickActions(onNavigate = onNavigate)
        }
    }
}

@Composable
private fun HomeGreeting(
    venueName: String,
    workoutName: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "$venueName · 本地计划",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = workoutName.toGreeting(),
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = FitnessColors.Surface,
            shadowElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(FitnessColors.Green),
                )
            }
        }
    }
}

@Composable
private fun HomeWorkoutHero(
    state: HomeUiState,
    heroAssetPath: String?,
    heroTitle: String,
    onNavigate: (AppRoute) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FitnessDimensions.LargeRadius),
        colors = CardDefaults.cardColors(containerColor = FitnessColors.Hero),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = if (state.nextWorkout == null) "TODAY" else "TODAY WORKOUT",
                color = Color(0xFFB8BDC6),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = heroTitle,
                        color = FitnessColors.OnHero,
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${state.completedThisWeek} / ${state.targetThisWeek.coerceAtLeast(0)} 次本周进度",
                        color = Color(0xFFB8BDC6),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF252C36)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!heroAssetPath.isNullOrBlank()) {
                        FitnessGifImage(
                            assetPath = heroAssetPath,
                            contentDescription = "${heroTitle}动作示范",
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.FitnessCenter,
                            contentDescription = null,
                            tint = FitnessColors.Green,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
            FitnessPrimaryButton(
                text = state.primaryAction.label,
                onClick = { onNavigate(state.primaryAction.route) },
                testTag = FitnessTestTags.HomePrimaryAction,
            )
        }
    }
}

@Composable
private fun WeeklyProgress(
    completed: Int,
    target: Int,
    weekDays: List<HomeDayUi>,
) {
    Column(
        modifier = Modifier.testTag(FitnessTestTags.WeeklyProgress),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("本周", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "$completed / ${target.coerceAtLeast(0)} 次",
                color = FitnessColors.Ink,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            weekDays.forEach { day ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 72.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (day.completed) FitnessColors.Green else FitnessColors.Surface,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = day.dayLabel,
                            color = if (day.completed) FitnessColors.Ink else FitnessColors.Muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = day.workoutLabel,
                            color = FitnessColors.Ink,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeQuickActions(onNavigate: (AppRoute) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("快速记录", style = MaterialTheme.typography.headlineSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickActionCard(
                title = "记饮食",
                subtitle = "更新今日营养",
                icon = {
                    Icon(
                        Icons.Rounded.Restaurant,
                        contentDescription = null,
                        tint = FitnessColors.Green,
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(FitnessTestTags.OpenFood),
                onClick = { onNavigate(AppRoute.Primary(PrimaryTab.Food)) },
            )
            QuickActionCard(
                title = "动作库",
                subtitle = "搜索本地动图",
                icon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = FitnessColors.Green,
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(FitnessTestTags.OpenLibrary),
                onClick = { onNavigate(AppRoute.Library(origin = PrimaryTab.Home)) },
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .heightIn(min = 104.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(FitnessDimensions.ContainerRadius),
        colors = CardDefaults.cardColors(containerColor = FitnessColors.Surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon()
            Spacer(Modifier.size(2.dp))
            Text(title, color = FitnessColors.Ink, fontWeight = FontWeight.Bold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun String?.toGreeting(): String = when {
    this == null -> "开始今天"
    contains("胸") -> "今天练胸"
    contains("腿") || contains("下肢") -> "今天练腿"
    contains("背") || contains("拉") -> "今天练背"
    else -> "今天训练"
}
