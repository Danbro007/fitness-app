package com.shanqijie.fitnessapp.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.Decoder
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.memory.MemoryCache
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

@SuppressLint("NewApi")
internal fun gifDecoderFactoryFor(apiLevel: Int): Decoder.Factory = when (gifDecoderKindFor(apiLevel)) {
    GifDecoderKind.GifDecoder -> GifDecoder.Factory()
    GifDecoderKind.ImageDecoder -> ImageDecoderDecoder.Factory()
}

internal class SharedInstance<T : Any> {
    @Volatile
    private var instance: T? = null

    fun get(create: () -> T): T =
        instance ?: synchronized(this) {
            instance ?: create().also { instance = it }
        }
}

private val SharedGifImageLoader = SharedInstance<ImageLoader>()
private val SharedStaticImageLoader = SharedInstance<ImageLoader>()

@Composable
fun FitnessFloatingBottomDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    containerColor: Color,
    contentColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 18.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = modifier.fillMaxWidth().offset(y = 6.dp),
                shape = RoundedCornerShape(30.dp),
                color = containerColor,
                contentColor = contentColor,
                shadowElevation = 14.dp,
            ) {
                Column {
                    Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(99.dp)).background(contentColor.copy(alpha = .28f)))
                    }
                    content()
                }
            }
        }
    }
}

private fun fitnessGifImageLoader(context: Context): ImageLoader =
    SharedGifImageLoader.get {
        ImageLoader.Builder(context.applicationContext)
            .memoryCache {
                MemoryCache.Builder(context.applicationContext)
                    .maxSizePercent(0.05)
                    .build()
            }
            .components {
                add(gifDecoderFactoryFor(Build.VERSION.SDK_INT))
            }
            .build()
    }

private fun fitnessStaticImageLoader(context: Context): ImageLoader =
    SharedStaticImageLoader.get {
        ImageLoader.Builder(context.applicationContext)
            .memoryCache {
                MemoryCache.Builder(context.applicationContext)
                    .maxSizePercent(0.15)
                    .build()
            }
            .build()
    }

@Composable
fun FitnessLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = FitnessColors.Ink,
) {
    CircularProgressIndicator(
        modifier = modifier
            .size(18.dp)
            .testTag("fitness-loading-indicator"),
        color = color,
        strokeWidth = 2.dp,
    )
}

@Composable
fun FitnessPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier),
        shape = RoundedCornerShape(FitnessDimensions.ControlRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = FitnessColors.Orange,
            contentColor = FitnessColors.OnOrange,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                FitnessLoadingIndicator(color = FitnessColors.OnOrange)
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
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
    val shape = RoundedCornerShape(18.dp)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = shape,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = FitnessColors.Surface,
            selectedContainerColor = FitnessColors.Orange,
            selectedLabelColor = FitnessColors.Ink,
        ),
        modifier = modifier
            .shadow(5.dp, shape)
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
    val shape = RoundedCornerShape(FitnessDimensions.LargeRadius)
    Column(
        modifier = modifier
            .shadow(7.dp, shape)
            .clip(shape)
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
    animated: Boolean = true,
) { // coverage-exempt: compiler-generated default-argument bridge
    val context = LocalContext.current
    val imageLoader = if (animated) {
        fitnessGifImageLoader(context)
    } else {
        fitnessStaticImageLoader(context)
    }
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
    Surface(
        color = FitnessColors.SurfaceStrong,
        shape = RoundedCornerShape(30.dp),
        shadowElevation = 18.dp,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp).height(82.dp).testTag(FitnessTestTags.BottomNav),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            PrimaryTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Column(
                    modifier = Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(23.dp))
                        .background(if (selected) FitnessColors.Orange else androidx.compose.ui.graphics.Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab) { onTabSelected(tab) }
                        .testTag(FitnessTestTags.primaryTab(tab)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = if (selected) FitnessColors.Ink else FitnessColors.Muted)
                    Text(tab.title, color = if (selected) FitnessColors.Ink else FitnessColors.Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
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
