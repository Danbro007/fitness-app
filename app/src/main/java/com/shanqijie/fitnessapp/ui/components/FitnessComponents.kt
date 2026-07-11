package com.shanqijie.fitnessapp.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.shanqijie.fitnessapp.BuildConfig
import com.shanqijie.fitnessapp.ui.navigation.FitnessTestTags
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab
import com.shanqijie.fitnessapp.ui.theme.FitnessColors
import com.shanqijie.fitnessapp.ui.theme.FitnessDimensions

internal enum class GifDecoderKind {
    GifDecoder,
    ImageDecoder,
}

internal fun gifDecoderKindFor(apiLevel: Int): GifDecoderKind =
    if (apiLevel >= 28) GifDecoderKind.ImageDecoder else GifDecoderKind.GifDecoder

internal class SharedInstance<T : Any> {
    @Volatile
    private var instance: T? = null

    fun get(create: () -> T): T =
        instance ?: synchronized(this) {
            instance ?: create().also { instance = it }
        }
}

private val SharedGifImageLoader = SharedInstance<ImageLoader>()

private fun fitnessGifImageLoader(context: Context): ImageLoader =
    SharedGifImageLoader.get {
        ImageLoader.Builder(context.applicationContext)
            .components {
                when (gifDecoderKindFor(Build.VERSION.SDK_INT)) {
                    GifDecoderKind.ImageDecoder -> add(ImageDecoderDecoder.Factory())
                    GifDecoderKind.GifDecoder -> add(GifDecoder.Factory())
                }
            }
            .build()
    }

@Composable
fun FitnessPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FitnessDimensions.MinimumTouchTarget)
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier),
        shape = RoundedCornerShape(FitnessDimensions.ControlRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = FitnessColors.Orange,
            contentColor = FitnessColors.OnOrange,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun FitnessSelectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier
            .heightIn(min = FitnessDimensions.MinimumTouchTarget)
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier),
    )
}

@Composable
fun FitnessPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (kicker != null) {
                Text(kicker, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        action?.invoke()
    }
}

@Composable
fun FitnessMetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(FitnessDimensions.ContainerRadius),
        colors = CardDefaults.cardColors(containerColor = FitnessColors.Surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun FitnessSurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(FitnessDimensions.LargeRadius))
            .background(FitnessColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun FitnessGifImage(
    assetPath: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (!BuildConfig.EXERCISE_MEDIA_ENABLED && assetPath.startsWith("exercise-media/gifs/")) {
        FitnessSurfaceCard(modifier = modifier) {
            Text(
                text = "动作示范媒体需取得授权后启用",
                style = MaterialTheme.typography.bodyMedium,
                color = FitnessColors.Muted,
            )
        }
        return
    }

    val context = LocalContext.current
    val imageLoader = fitnessGifImageLoader(context)
    val model = remember(assetPath) {
        when {
            "://" in assetPath -> assetPath
            assetPath.startsWith("/") -> assetPath
            else -> "file:///android_asset/${assetPath.removePrefix("/")}"
        }
    }

    AsyncImage(
        model = model,
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}

@Composable
fun FitnessBottomNav(
    selectedTab: PrimaryTab,
    onTabSelected: (PrimaryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.testTag(FitnessTestTags.BottomNav),
        containerColor = FitnessColors.Surface,
        contentColor = FitnessColors.Ink,
    ) {
        PrimaryTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(tab.title) },
                modifier = Modifier
                    .heightIn(min = FitnessDimensions.MinimumTouchTarget)
                    .testTag(FitnessTestTags.primaryTab(tab)),
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = FitnessColors.Green,
                    selectedTextColor = FitnessColors.Ink,
                    indicatorColor = FitnessColors.Phone,
                    unselectedIconColor = FitnessColors.Muted,
                    unselectedTextColor = FitnessColors.Muted,
                ),
            )
        }
    }
}

private val PrimaryTab.icon: ImageVector
    get() = when (this) {
        PrimaryTab.Home -> Icons.Rounded.Home
        PrimaryTab.Plan -> Icons.Rounded.CalendarMonth
        PrimaryTab.Training -> Icons.Rounded.FitnessCenter
        PrimaryTab.Food -> Icons.Rounded.Restaurant
        PrimaryTab.Profile -> Icons.Rounded.Person
    }
