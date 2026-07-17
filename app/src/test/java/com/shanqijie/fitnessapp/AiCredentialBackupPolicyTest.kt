package com.shanqijie.fitnessapp

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCredentialBackupPolicyTest {
    @Test
    fun manifestAndBackupRulesExcludeEncryptedCredentials() {
        val manifest = sourceFile("AndroidManifest.xml").readText()
        val legacyRules = sourceFile("res/xml/backup_rules.xml").readText()
        val extractionRules = sourceFile("res/xml/data_extraction_rules.xml").readText()
        val settingsSource = sourceFile("java/com/shanqijie/fitnessapp/ui/settings/SettingsScreens.kt").readText()

        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(legacyRules.contains("path=\"ai_credentials.xml\""))
        assertTrue(extractionRules.count { it == '<' } >= 6)
        assertTrue(extractionRules.split("ai_credentials.xml").size - 1 == 2)
        assertTrue(settingsSource.contains("var apiKey by remember { mutableStateOf(\"\") }"))
        assertTrue(!settingsSource.contains("var apiKey by rememberSaveable"))
    }

    private fun sourceFile(relativePath: String): File =
        listOf(File("src/main/$relativePath"), File("app/src/main/$relativePath"))
            .firstOrNull(File::isFile)
            ?: error("找不到 $relativePath")
}
