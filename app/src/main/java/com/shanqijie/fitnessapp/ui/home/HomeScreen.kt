package com.shanqijie.fitnessapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.NorthEast
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    modifier: Modifier,
    heroAssetPath: String?,
    heroTitle: String,
    venueName: String,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FitnessColors.Phone),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp,
            top = 12.dp,
            end = 18.dp,
            bottom = 112.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp), // coverage-exempt: compiler-generated LazyColumn content branch
    ) {
        item {
            HomeGreeting(
                venueName = venueName,
                workoutName = heroTitle,
            )
        }
        item { Spacer(Modifier.height(14.dp)) }
        item {
            HomeWorkoutHero(
                state = state,
                heroAssetPath = heroAssetPath,
                heroTitle = heroTitle,
                onNavigate = onNavigate,
            )
        }
        item { Spacer(Modifier.height(26.dp)) }
        item {
            WeeklyProgress(
                completed = state.completedThisWeek,
                target = state.targetThisWeek,
                weekDays = weekDays.take(4),
            )
        }
        item { Spacer(Modifier.height(26.dp)) }
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
            verticalArrangement = Arrangement.spacedBy(5.dp),
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
            modifier = Modifier
                .size(52.dp)
                .semantics { contentDescription = "本地离线模式" },
            shape = CircleShape,
            color = FitnessColors.Surface,
            shadowElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("本地", color = FitnessColors.Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
        modifier = Modifier.fillMaxWidth().height(312.dp),
        shape = RoundedCornerShape(FitnessDimensions.LargeRadius),
        colors = CardDefaults.cardColors(containerColor = FitnessColors.Hero),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(22.dp)) {
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 96.dp, y = (-36).dp)
                    .size(250.dp).border(46.dp, FitnessColors.Orange.copy(alpha = .12f), CircleShape),
            )
            Row(modifier = Modifier.align(Alignment.TopStart).padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(FitnessColors.Orange))
                Text(
                text = when {
                state.completedToday -> "今日已完成"
                state.nextWorkout == null -> "下一步"
                else -> "今日唯一任务"
            },
                color = Color(0xFFB7BAAF),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                )
            }
            Surface(
                color = Color(0xFF20211E),
                shape = RoundedCornerShape(99.dp),
                modifier = Modifier.align(Alignment.TopEnd).height(38.dp).width(72.dp),
            ) { Box(contentAlignment = Alignment.Center) { Text("0 / 7 组", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) } }
            Column(modifier = Modifier.align(Alignment.TopStart).padding(top = 62.dp)) {
                Text(
                    text = heroTitle.toHeroTitle(),
                    color = FitnessColors.OnHero,
                    fontSize = 36.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("2 个动作 · 7 组 · 约 21 分钟", color = Color(0xFFA4A69F), fontSize = 14.sp)
            }
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 60.dp, end = 2.dp)
                    .size(width = 120.dp, height = 98.dp).clip(RoundedCornerShape(26.dp)).background(FitnessColors.Surface),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!heroAssetPath.isNullOrBlank()) {
                        FitnessGifImage(
                            assetPath = heroAssetPath,
                            contentDescription = "${heroTitle}动作示范",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.FitnessCenter,
                            contentDescription = null,
                            tint = FitnessColors.Ink,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
            Surface(
                onClick = { onNavigate(state.primaryAction.route) },
                color = FitnessColors.Orange,
                contentColor = FitnessColors.Ink,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(58.dp).testTag(FitnessTestTags.HomePrimaryAction),
            ) {
                Row(Modifier.fillMaxSize().padding(start = 22.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(state.primaryAction.label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Surface(shape = CircleShape, color = FitnessColors.Ink, modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.NorthEast, contentDescription = null, tint = Color.White) }
                    }
                }
            }
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
            Text("本周节奏", style = MaterialTheme.typography.headlineSmall)
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
            weekDays.forEachIndexed { index, day ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 92.dp)
                        .shadow(7.dp, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    color = if (index == 0) FitnessColors.Orange else FitnessColors.Surface,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = day.dayLabel,
                            color = if (index == 0) FitnessColors.Ink.copy(alpha = .62f) else FitnessColors.Muted,
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("快速入口", style = MaterialTheme.typography.headlineSmall)
            Text("本机数据", style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickActionCard(
                title = "记录饮食",
                subtitle = "更新今日营养进度", // coverage-exempt: compiler-generated composable icon lambda branch
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
                highlighted = true,
                onClick = { onNavigate(AppRoute.Primary(PrimaryTab.Food)) },
            )
            QuickActionCard(
                title = "动作库",
                subtitle = "搜索本地动图", // coverage-exempt: compiler-generated composable icon lambda branch
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
                highlighted = false,
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
    modifier: Modifier,
    highlighted: Boolean,
) {
    Card(
        modifier = modifier
            .heightIn(min = 136.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(FitnessDimensions.ContainerRadius),
        colors = CardDefaults.cardColors(containerColor = if (highlighted) FitnessColors.Orange else FitnessColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
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

private fun String.toHeroTitle(): String =
    if (contains("胸部力量")) replace("胸部力量", "胸部\n力量") else this
