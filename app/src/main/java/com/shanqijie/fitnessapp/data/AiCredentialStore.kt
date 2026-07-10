package com.shanqijie.fitnessapp.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AiCredentialStore(
    context: Context,
    private val preferencesName: String = "ai_credentials",
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun saveApiKey(providerId: String, apiKey: String) {
        val trimmed = apiKey.trim()
        require(trimmed.isNotEmpty()) { "接口密钥不能为空" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(payloadKey(providerId), Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(ivKey(providerId), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun loadApiKey(providerId: String): String? {
        val payload = preferences.getString(payloadKey(providerId), null) ?: return null
        val iv = preferences.getString(ivKey(providerId), null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)),
        )
        val decrypted = cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP))
        return decrypted.toString(Charsets.UTF_8)
    }

    fun deleteApiKey(providerId: String) {
        preferences.edit()
            .remove(payloadKey(providerId))
            .remove(ivKey(providerId))
            .apply()
    }

    fun encryptedPayloadForTest(providerId: String): String? =
        preferences.getString(payloadKey(providerId), null)

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun payloadKey(providerId: String): String = "$providerId.payload"

    private fun ivKey(providerId: String): String = "$providerId.iv"

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "fitness_app_ai_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
