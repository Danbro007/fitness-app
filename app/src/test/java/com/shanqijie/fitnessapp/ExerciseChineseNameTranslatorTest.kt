package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.domain.ExerciseChineseNameTranslator
import com.shanqijie.fitnessapp.domain.ExerciseManifestParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExerciseChineseNameTranslatorTest {
    @Test
    fun translatesKnownExerciseNamesToChinese() {
        assertChinese("史密斯机卧推", ExerciseChineseNameTranslator.translate("smith bench press"))
        assertChinese("哑铃卧推", ExerciseChineseNameTranslator.translate("dumbbell bench press"))
        assertChinese("45度腿举", ExerciseChineseNameTranslator.translate("sled 45° leg press"))
        assertChinese("45度腿举", ExerciseChineseNameTranslator.translate("sled 45в° leg press"))
        assertChinese("绳索高位下拉全程", ExerciseChineseNameTranslator.translate("cable lat pulldown full range of motion"))
    }

    @Test
    fun translatesUiCategoryLabelsToChinese() {
        assertChinese("器械", ExerciseChineseNameTranslator.translate("machine"))
        assertChinese("自由重量", ExerciseChineseNameTranslator.translate("free-weight"))
        assertChinese("有氧", ExerciseChineseNameTranslator.translate("cardio"))
        assertChinese("自定义", ExerciseChineseNameTranslator.translate("custom"))
    }

    @Test
    fun preservesChineseAndHandlesBlankIgnoredAndNumericTokens() {
        assertEquals("训练动作", ExerciseChineseNameTranslator.translate("   "))
        assertEquals("杠铃深蹲", ExerciseChineseNameTranslator.translate("  杠铃深蹲  "))
        assertTrue(ExerciseChineseNameTranslator.translate("curl 12").contains("12"))

        val token = ExerciseChineseNameTranslator::class.java.declaredMethods
            .single { it.name == "translateToken" }
            .apply { isAccessible = true }
        assertNull(token.invoke(ExerciseChineseNameTranslator, ""))
        assertNull(token.invoke(ExerciseChineseNameTranslator, "and"))
        assertEquals("12", token.invoke(ExerciseChineseNameTranslator, "12"))
    }

    @Test
    fun translatesEveryManifestExerciseNameWithoutLatinLetters() {
        val manifest = ExerciseManifestParser.parse(manifestFile().readText())
        val untranslated = manifest.files
            .map { it.name to ExerciseChineseNameTranslator.translate(it.name) }
            .filter { (_, translated) -> translated.isBlank() || translated == "训练动作" || latinRegex.containsMatchIn(translated) }

        assertTrue(
            "动作库仍有英文动作名: ${untranslated.take(12)}",
            untranslated.isEmpty(),
        )
    }

    @Test
    fun translatesManifestMetadataShownByLibraryWithoutLatinLetters() {
        val manifest = ExerciseManifestParser.parse(manifestFile().readText())
        val untranslated = manifest.files
            .flatMap { listOf(it.bodyPart, it.equipment, it.target) }
            .distinct()
            .map { it to ExerciseChineseNameTranslator.translate(it) }
            .filter { (_, translated) -> translated.isBlank() || translated == "训练动作" || latinRegex.containsMatchIn(translated) }

        assertTrue(
            "动作库副标题仍有英文: ${untranslated.take(12)}",
            untranslated.isEmpty(),
        )
    }

    private fun assertChinese(expected: String, actual: String) {
        assertTrue("期望包含中文，实际为 $actual", cjkRegex.containsMatchIn(actual))
        assertFalse("不应包含英文字母，实际为 $actual", latinRegex.containsMatchIn(actual))
        assertTrue("期望 $expected，实际为 $actual", actual.contains(expected))
    }

    private fun manifestFile(): File =
        listOf(
            File("src/main/assets/exercise-media/manifest.json"),
            File("app/src/main/assets/exercise-media/manifest.json"),
        ).first { it.exists() }

    private companion object {
        val latinRegex = Regex("[A-Za-z]")
        val cjkRegex = Regex("[\\u4e00-\\u9fff]")
    }
}
