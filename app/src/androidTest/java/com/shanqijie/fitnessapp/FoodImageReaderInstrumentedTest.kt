package com.shanqijie.fitnessapp

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.shanqijie.fitnessapp.ui.food.readFoodPhoto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodImageReaderInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun readsValidImageMetadataAndRejectsInvalidOrOversizedDimensions() = runBlocking {
        val valid = createPng("food-valid", width = 320, height = 240)
        val invalid = createRaw("food-invalid", "not an image".encodeToByteArray())
        val tooWide = createPng("food-too-wide", width = 12_001, height = 1)
        try {
            val payload = context.readFoodPhoto(valid)
            assertEquals(valid.toString(), payload.imageUri)
            assertEquals("image/png", payload.imageMimeType)
            assertTrue(Base64.decode(payload.imageBase64, Base64.NO_WRAP).isNotEmpty())

            val invalidError = runCatching { context.readFoodPhoto(invalid) }.exceptionOrNull()
            assertTrue(invalidError is IllegalArgumentException)
            assertEquals("无法识别照片尺寸", invalidError?.message)

            val dimensionError = runCatching { context.readFoodPhoto(tooWide) }.exceptionOrNull()
            assertTrue(dimensionError is IllegalArgumentException)
            assertEquals("照片尺寸过大，请选择较小的图片", dimensionError?.message)
        } finally {
            listOf(valid, invalid, tooWide).forEach { context.contentResolver.delete(it, null, null) }
        }
    }

    private fun createPng(name: String, width: Int, height: Int): Uri {
        val uri = newImage(name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(228, 255, 0))
        }
        context.contentResolver.openOutputStream(uri).use { output ->
            requireNotNull(output)
            require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
        return uri
    }

    private fun createRaw(name: String, bytes: ByteArray): Uri {
        val uri = newImage(name)
        context.contentResolver.openOutputStream(uri).use { output ->
            requireNotNull(output).write(bytes)
        }
        return uri
    }

    private fun newImage(name: String): Uri = requireNotNull(
        context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            },
        ),
    )
}
