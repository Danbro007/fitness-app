package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.domain.toReadableAiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiPresentationFormatterTest {
    @Test
    fun removesMarkdownSyntaxButPreservesReadableStructure() {
        val result = """
            ## 营养估算
            **热量**：520 kcal
            - 蛋白质：42g
            1. 控制酱汁
            [查看说明](https://example.com)
        """.trimIndent().toReadableAiText()

        assertEquals(
            "营养估算\n热量：520 kcal\n• 蛋白质：42g\n• 控制酱汁\n查看说明",
            result,
        )
        assertFalse(result.contains("**"))
        assertFalse(result.contains("##"))
    }
}
