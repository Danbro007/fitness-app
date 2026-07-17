package com.shanqijie.fitnessapp

import androidx.test.core.app.ApplicationProvider
import com.shanqijie.fitnessapp.data.AiCredentialStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCredentialStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = AiCredentialStore(context, preferencesName = "ai-credential-test")

    @After
    fun tearDown() {
        store.deleteApiKey("deepseek")
        context.getSharedPreferences("ai-credential-test", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun savesAndLoadsEncryptedApiKey() {
        store.saveApiKey("deepseek", "sk-test-secret")

        assertEquals("sk-test-secret", store.loadApiKey("deepseek"))
        assertNotEquals("sk-test-secret", store.encryptedPayloadForTest("deepseek"))
    }

    @Test
    fun deletesApiKey() {
        store.saveApiKey("deepseek", "sk-test-secret")

        store.deleteApiKey("deepseek")

        assertNull(store.loadApiKey("deepseek"))
    }

    @Test
    fun rejectsBlankKeysAndReturnsNullWhenTheIvIsMissing() {
        assertTrue(runCatching { store.saveApiKey("deepseek", "") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { store.saveApiKey("deepseek", "   ") }.exceptionOrNull() is IllegalArgumentException)

        store.saveApiKey("deepseek", "sk-test-secret")
        context.getSharedPreferences("ai-credential-test", android.content.Context.MODE_PRIVATE)
            .edit()
            .remove("deepseek.iv")
            .commit()

        assertNull(store.loadApiKey("deepseek"))
    }

    @Test
    fun corruptedOrCrossDeviceCiphertextIsClearedWithoutBlockingStartup() {
        val preferences = context.getSharedPreferences("ai-credential-test", android.content.Context.MODE_PRIVATE)
        preferences.edit()
            .putString("deepseek.payload", "not-valid-base64")
            .putString("deepseek.iv", "also-not-valid-base64")
            .commit()

        assertNull(store.loadApiKey("deepseek"))
        assertNull(preferences.getString("deepseek.payload", null))
        assertNull(preferences.getString("deepseek.iv", null))
    }
}
