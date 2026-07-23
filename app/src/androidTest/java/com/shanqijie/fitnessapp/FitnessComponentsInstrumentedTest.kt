package com.shanqijie.fitnessapp

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.shanqijie.fitnessapp.ui.components.FitnessGifImage
import com.shanqijie.fitnessapp.ui.components.FitnessFloatingBottomDialog
import com.shanqijie.fitnessapp.ui.components.FitnessBottomNav
import com.shanqijie.fitnessapp.ui.components.FitnessMetricCard
import com.shanqijie.fitnessapp.ui.components.FitnessPageHeader
import com.shanqijie.fitnessapp.ui.components.FitnessPrimaryButton
import com.shanqijie.fitnessapp.ui.components.FitnessSelectionChip
import com.shanqijie.fitnessapp.ui.components.FitnessSurfaceCard
import com.shanqijie.fitnessapp.ui.components.gifDecoderFactoryFor
import com.shanqijie.fitnessapp.ui.theme.FitnessTheme
import com.shanqijie.fitnessapp.ui.navigation.PrimaryTab
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class FitnessComponentsInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun exerciseImageSupportsAnimatedStaticAbsoluteAndAssetModels() {
        assertEquals(GifDecoder.Factory::class.java, gifDecoderFactoryFor(26)::class.java)
        assertEquals(ImageDecoderDecoder.Factory::class.java, gifDecoderFactoryFor(28)::class.java)
        composeRule.setContent {
            FitnessTheme {
                Column {
                    FitnessGifImage(
                        assetPath = "plain-missing.gif",
                        contentDescription = "asset animated model",
                        modifier = Modifier.size(32.dp),
                    )
                    FitnessGifImage(
                        assetPath = "/tmp/missing-static.png",
                        contentDescription = "absolute static model",
                        modifier = Modifier.size(32.dp),
                        contentScale = ContentScale.Fit,
                        animated = false,
                    )
                    FitnessGifImage(
                        assetPath = "content://missing/image",
                        contentDescription = "uri static model",
                        modifier = Modifier.size(32.dp),
                        animated = false,
                    )
                    FitnessGifImage(
                        assetPath = "exercise-media/gifs/0748-trqKQv2.gif",
                        contentDescription = "local media asset",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("asset animated model").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("absolute static model").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("uri static model").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("local media asset").assertIsDisplayed()
    }

    @Test
    fun reusableComponentsCoverOptionalAndSelectedStates() {
        composeRule.setContent {
            FitnessTheme {
                Column {
                    FitnessPageHeader(title = "无副标题")
                    FitnessPageHeader(
                        title = "完整标题",
                        modifier = Modifier,
                        kicker = "副标题",
                        action = { Text("操作") },
                    )
                    FitnessPrimaryButton(text = "不可用按钮", onClick = {}, enabled = false)
                    FitnessSelectionChip(label = "未选择", selected = false, onClick = {})
                    FitnessSelectionChip(label = "已选择", selected = true, onClick = {}, testTag = "selected-chip")
                    FitnessMetricCard(value = "12", label = "组数")
                    FitnessSurfaceCard { Text("卡片内容") }
                }
            }
        }

        listOf("无副标题", "完整标题", "副标题", "操作", "不可用按钮", "未选择", "已选择", "12", "组数", "卡片内容")
            .forEach { composeRule.onNodeWithText(it).assertExists() }
    }

    @Test
    fun primaryButtonShowsLocalSpinnerWhileLoading() {
        composeRule.setContent {
            FitnessTheme {
                FitnessPrimaryButton(
                    text = "生成中…",
                    enabled = false,
                    loading = true,
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("生成中…").assertIsDisplayed()
        composeRule.onNodeWithTag("fitness-loading-indicator").assertIsDisplayed()
    }

    @Test
    fun bottomDialogAndNavigationCoverDefaultAndExplicitArguments() {
        val explicit = mutableStateOf(false)
        composeRule.setContent {
            FitnessTheme {
                Column {
                    FitnessBottomNav(
                        selectedTab = PrimaryTab.Home,
                        onTabSelected = {},
                        modifier = Modifier,
                    )
                    if (explicit.value) {
                        FitnessFloatingBottomDialog(
                            onDismissRequest = {},
                            modifier = Modifier,
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ) { Text("显式弹层") }
                    } else {
                        FitnessFloatingBottomDialog(
                            onDismissRequest = {},
                            modifier = Modifier,
                            containerColor = com.shanqijie.fitnessapp.ui.theme.FitnessColors.Surface,
                            contentColor = com.shanqijie.fitnessapp.ui.theme.FitnessColors.Ink,
                        ) { Text("默认弹层") }
                    }
                }
            }
        }

        composeRule.onNodeWithText("默认弹层").assertIsDisplayed()
        composeRule.runOnIdle { explicit.value = true }
        composeRule.onNodeWithText("显式弹层").assertIsDisplayed()
    }

    @Test
    fun trainingHelpersResolveWrappedActivitiesAndFormatLongDurations() {
        val owner = Class.forName("com.shanqijie.fitnessapp.ui.training.TrainingScreensKt")
        val findActivity = owner.declaredMethods.single { it.name == "findActivity" }.apply {
            isAccessible = true
        }
        val formatElapsedTime = owner.declaredMethods.single { it.name == "formatElapsedTime" }.apply {
            isAccessible = true
        }

        assertEquals(composeRule.activity, findActivity.invoke(null, composeRule.activity))
        assertEquals(
            composeRule.activity,
            findActivity.invoke(null, ContextWrapper(ContextWrapper(composeRule.activity))),
        )
        val failure = assertThrows(InvocationTargetException::class.java) {
            findActivity.invoke(null, ApplicationProvider.getApplicationContext<android.content.Context>())
        }
        assertTrue(failure.cause is IllegalStateException)
        assertEquals("1:01:01", formatElapsedTime.invoke(null, 3_661_000L))
    }
}
