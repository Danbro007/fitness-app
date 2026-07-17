package com.shanqijie.fitnessapp

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.shanqijie.fitnessapp.data.FitnessBackupCodec
import com.shanqijie.fitnessapp.data.UserProfileEntity
import com.shanqijie.fitnessapp.ui.food.FoodPhotoInput
import com.shanqijie.fitnessapp.ui.food.FoodPhotoScreen
import com.shanqijie.fitnessapp.ui.food.persistFoodPhotoReadPermission
import com.shanqijie.fitnessapp.ui.profile.ProfileEditScreen
import com.shanqijie.fitnessapp.ui.settings.BackupSettingsScreen
import com.shanqijie.fitnessapp.ui.settings.SettingsTags
import com.shanqijie.fitnessapp.ui.theme.FitnessTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActivityResultCallbacksInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun foodPhotoPickerReturnsAReadablePreviewAndGenerationPayload() {
        val uri = createPng("food-photo-${System.nanoTime()}.png")
        var generated: FoodPhotoInput? = null
        try {
            setContentWithActivityResult(uri) {
                FoodPhotoScreen(
                    onGenerate = { generated = it },
                    modifier = androidx.compose.ui.Modifier,
                )
            }

            composeRule.onNodeWithText("选择一张餐食照片").performClick()
            composeRule.onNodeWithContentDescription("已选择的餐食照片").assertIsDisplayed()
            composeRule.onNodeWithText("生成估算草稿").performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) { generated != null }
            composeRule.runOnIdle {
                assertEquals(uri.toString(), generated?.imageUri)
                assertEquals("image/jpeg", generated?.imageMimeType)
                assertTrue(generated?.imageBase64.orEmpty().isNotBlank())
            }
        } finally {
            resolver.delete(uri, null, null)
        }
    }

    @Test
    fun cancellingFoodPhotoPickerLeavesTheDescriptionOnlyFlowUsable() {
        setContentWithActivityResult(null) {
            FoodPhotoScreen(
                onGenerate = {},
                modifier = androidx.compose.ui.Modifier,
            )
        }

        composeRule.onNodeWithText("选择一张餐食照片").performClick()
        composeRule.onNodeWithContentDescription("已选择的餐食照片").assertDoesNotExist()
        composeRule.onNodeWithText("生成估算草稿").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun profileAvatarPickerForwardsTheSelectedUriToPersistence() {
        val uri = createPng("profile-photo-${System.nanoTime()}.png")
        var savedUri: Uri? = null
        try {
            setContentWithActivityResult(uri) {
                ProfileEditScreen(
                    profile = UserProfileEntity(
                        id = "local-user",
                        displayName = "山崎",
                        birthYear = 1994,
                        heightCm = 176.0,
                        weightKg = 76.5,
                        goal = "保持体能",
                        injuries = "",
                        weeklyTrainingDays = 3,
                        preferredMinutes = 45,
                        updatedAt = 1L,
                    ),
                    onSave = { _, _, _, _, _, _, _, _, _ -> },
                    onSaveAvatar = { savedUri = it },
                )
            }

            composeRule.onNodeWithText("上传头像").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) { savedUri != null }
            composeRule.runOnIdle { assertEquals(uri, savedUri) }
        } finally {
            resolver.delete(uri, null, null)
        }
    }

    @Test
    fun cancellingProfileAvatarPickerDoesNotInvokePersistence() {
        var savedUri: Uri? = null
        setContentWithActivityResult(null) {
            ProfileEditScreen(
                profile = UserProfileEntity(
                    id = "local-user",
                    displayName = "山崎",
                    birthYear = 1994,
                    heightCm = 176.0,
                    weightKg = 76.5,
                    goal = "保持体能",
                    injuries = "",
                    weeklyTrainingDays = 3,
                    preferredMinutes = 45,
                    updatedAt = 1L,
                ),
                onSave = { _, _, _, _, _, _, _, _, _ -> },
                onSaveAvatar = { savedUri = it },
            )
        }

        composeRule.onNodeWithText("上传头像").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(null, savedUri) }
    }

    @Test
    fun backupFileContractsWriteAndReadTheSelectedDocument() {
        val uri = createDocument("fitness-backup-${System.nanoTime()}.json", "application/json")
        var imported = ""
        try {
            setContentWithActivityResult(uri) {
                BackupSettingsScreen(
                    onExportBackup = {
                        """{"version":4,"exportedAt":1,"userProfile":null,"venues":[],"equipment":[],"plannedWorkouts":[],"plannedExercises":[],"workoutSessions":[],"setLogs":[],"foodLogs":[],"aiDrafts":[],"aiProviders":[]}"""
                    },
                    onImportBackup = { imported = it },
                    onResetLocalData = {},
                    onResetComplete = {},
                )
            }

            composeRule.onNodeWithText("导出本机备份").performClick()
            composeRule.onNodeWithText("备份文件已保存").assertIsDisplayed()
            val written = requireNotNull(resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() })
            val writtenPayload = FitnessBackupCodec.decode(written)
            assertEquals(4, writtenPayload.version)
            assertEquals(1L, writtenPayload.exportedAt)
            assertEquals(null, writtenPayload.userProfile)
            assertTrue(writtenPayload.plannedWorkouts.isEmpty())
            assertTrue(writtenPayload.workoutSessions.isEmpty())

            composeRule.onNodeWithText("从备份恢复").performClick()
            composeRule.onNodeWithTag(SettingsTags.ImportDialog).assertIsDisplayed()
            composeRule.onNodeWithTag(SettingsTags.ConfirmImport).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) { imported.isNotBlank() }
            composeRule.onNodeWithText("本地数据已恢复，恢复前快照已保存").assertIsDisplayed()
            assertEquals(writtenPayload, FitnessBackupCodec.decode(imported))
        } finally {
            resolver.delete(uri, null, null)
        }
    }

    @Test
    fun cancellingBackupFileContractsLeavesTheScreenUsable() {
        setContentWithActivityResult(null) {
            BackupSettingsScreen(
                onExportBackup = { "{\"version\":4}" },
                onImportBackup = { error("取消选择时不应导入") },
                onResetLocalData = {},
                onResetComplete = {},
            )
        }

        composeRule.onNodeWithText("导出本机备份").performClick()
        composeRule.onNodeWithText("从备份恢复").performClick()
        composeRule.onNodeWithText("重置所有本机数据").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun blankBackupPayloadIsNotWrittenToTheSelectedDocument() {
        val uri = createDocument("blank-fitness-backup-${System.nanoTime()}.json", "application/json")
        try {
            setContentWithActivityResult(uri) {
                BackupSettingsScreen(
                    onExportBackup = { "" },
                    onImportBackup = {},
                    onResetLocalData = {},
                    onResetComplete = {},
                )
            }

            composeRule.onNodeWithText("导出本机备份").performClick()
            composeRule.onNodeWithText("重置所有本机数据").performScrollTo().assertIsDisplayed()
            val written = runCatching {
                resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            assertTrue(written.isNullOrEmpty())
        } finally {
            resolver.delete(uri, null, null)
        }
    }

    @Test
    fun foodPhotoPermissionHelperRequestsAReadGrantAndToleratesProviderRejection() {
        val uri = Uri.parse("content://documents/food-photo")
        var capturedUri: Uri? = null
        var capturedFlags = 0

        persistFoodPhotoReadPermission(uri) { selected, flags ->
            capturedUri = selected
            capturedFlags = flags
        }
        persistFoodPhotoReadPermission(uri) { _, _ -> error("provider rejected the grant") }

        assertEquals(uri, capturedUri)
        assertEquals(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION, capturedFlags)
    }

    private fun setContentWithActivityResult(result: Uri?, content: @Composable () -> Unit) {
        val registry = ImmediateActivityResultRegistry(result)
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                FitnessTheme { content() }
            }
        }
    }

    private fun createPng(name: String): Uri {
        val uri = createDocument(name, "image/png")
        resolver.openOutputStream(uri)?.use { output ->
            val bitmap = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888)
            try {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            } finally {
                bitmap.recycle()
            }
        }
        return uri
    }

    private fun createDocument(name: String, mimeType: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        }
        return requireNotNull(
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
        )
    }

    private val resolver
        get() = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver

    private class ImmediateActivityResultRegistry(
        private val result: Uri?,
    ) : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            @Suppress("UNCHECKED_CAST")
            dispatchResult(requestCode, result as O)
        }
    }
}
