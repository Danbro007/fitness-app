package com.shanqijie.fitnessapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.shanqijie.fitnessapp.data.AiCredentialStore
import com.shanqijie.fitnessapp.data.BodyMeasurement
import com.shanqijie.fitnessapp.data.FitnessDatabase
import com.shanqijie.fitnessapp.data.FitnessRepository
import com.shanqijie.fitnessapp.data.FitnessStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Opt-in live contract test for Alibaba Cloud Model Studio.
 *
 * The API key is supplied as an instrumentation argument and is never checked into the repository.
 * The regular test suite skips this class when the argument is absent.
 */
class BailianLiveIntegrationTest {
    private lateinit var context: Context
    private lateinit var database: FitnessDatabase
    private lateinit var credentialStore: AiCredentialStore
    private lateinit var repository: FitnessRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
        database = FitnessDatabase(context, DB_NAME)
        credentialStore = AiCredentialStore(context, CREDENTIALS)
        repository = FitnessRepository(context, FitnessStore(database), credentialStore)
    }

    @After
    fun tearDown() {
        credentialStore.deleteApiKey("qwen")
        database.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun qwenConnectionPlanAndFoodPhotoFlowWorksEndToEnd() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val apiKey = arguments.getString("bailianApiKey").orEmpty()
        assumeTrue("bailianApiKey is required for the opt-in live test", apiKey.isNotBlank())

        repository.bootstrap()
        repository.saveUserProfile(
            displayName = "AI 集成测试",
            birthYear = 1990,
            heightCm = 175.0,
            weightKg = 72.0,
            goal = "增肌",
            injuries = "无",
            weeklyTrainingDays = 3,
            preferredMinutes = 45,
            bodyMeasurement = BodyMeasurement(),
        )
        repository.saveAiApiKey("qwen", apiKey)
        repository.selectAiProvider(
            providerId = "qwen",
            endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            model = "qwen3.7-plus",
        )

        val connection = repository.testAiProvider("qwen")
        assertTrue(connection.message, connection.success)

        val planDraft = repository.generateWeeklyPlanDraft()
        assertTrue(planDraft.content.length > 40)
        assertFalse(planDraft.content.contains("确认后会新建一节本地训练计划"))

        val bytes = syntheticFoodPhoto()
        val photoDraft = repository.generateFoodEstimateDraft(
            description = "识别餐盘中的米饭、牛排和绿色配菜并估算营养",
            imageUri = "integration://synthetic-food.jpg",
            imageMimeType = "image/jpeg",
            imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
        )
        val beforeConfirmation = repository.appState().first()

        assertTrue(photoDraft.content.contains("AI 建议："))
        assertTrue(beforeConfirmation.foodLogs.isEmpty())

        val confirmed = repository.confirmFoodEstimateDraft(photoDraft.id)
        val afterConfirmation = repository.appState().first()
        assertEquals("vision_ai", confirmed.source)
        assertEquals("qwen", confirmed.providerId)
        assertEquals(1, afterConfirmation.foodLogs.size)
    }

    private fun syntheticFoodPhoto(): ByteArray {
        val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.rgb(222, 205, 180))
        paint.color = Color.WHITE
        canvas.drawCircle(160f, 160f, 132f, paint)
        paint.color = Color.rgb(244, 235, 194)
        canvas.drawOval(55f, 75f, 185f, 245f, paint)
        paint.color = Color.rgb(105, 58, 38)
        canvas.drawRoundRect(165f, 82f, 270f, 225f, 18f, 18f, paint)
        paint.color = Color.rgb(65, 135, 72)
        canvas.drawCircle(225f, 238f, 32f, paint)
        canvas.drawCircle(265f, 250f, 25f, paint)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
            output.toByteArray()
        }.also { bitmap.recycle() }
    }

    private companion object {
        const val DB_NAME = "bailian-live-integration.db"
        const val CREDENTIALS = "bailian-live-integration-credentials"
    }
}
