package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.ai.AiChatClient
import com.shanqijie.fitnessapp.ai.AiHttpTransport
import com.shanqijie.fitnessapp.ai.AiProviderConfig
import com.shanqijie.fitnessapp.ai.AiProviderCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatClientTest {
    @Test
    fun providerCatalogMatchesTheThreeSupportedServices() {
        assertEquals(listOf("openai", "gemini", "qwen"), AiProviderCatalog.entries.map { it.id })
        assertEquals(listOf("gpt-5-mini", "gpt-5", "gpt-4.1-mini"), AiProviderCatalog.entry("openai")?.models)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai", AiProviderCatalog.entry("gemini")?.endpoints?.single())
        assertEquals(3, AiProviderCatalog.entry("qwen")?.endpoints?.size)
    }

    @Test
    fun buildsDeepSeekTestRequestWithCurrentModel() {
        val client = AiChatClient(deepSeekConfig)

        val json = client.buildTestRequestJson("只回复 OK")

        assertTrue(json.contains("\"model\":\"deepseek-v4-flash\""))
        assertTrue(json.contains("\"thinking\":{\"type\":\"disabled\"}"))
        assertTrue(json.contains("\"role\":\"user\""))
        assertTrue(json.contains("只回复 OK"))
    }

    @Test
    fun buildsOpenAiCompatibleChatRequestForDraftGeneration() {
        val client = AiChatClient(deepSeekConfig)

        val json = client.buildChatRequestJson(
            systemPrompt = "你是健身计划助手。",
            userPrompt = "生成一周训练草稿。",
            temperature = 0.3,
        )

        assertTrue(json.contains("\"model\":\"deepseek-v4-flash\""))
        assertTrue(json.contains("\"role\":\"system\""))
        assertTrue(json.contains("你是健身计划助手。"))
        assertTrue(json.contains("\"role\":\"user\""))
        assertTrue(json.contains("生成一周训练草稿。"))
        assertTrue(json.contains("\"temperature\":0.3"))
    }

    @Test
    fun parsesAssistantContentFromOpenAiCompatibleResponse() {
        val client = AiChatClient(deepSeekConfig)
        val response = """
            {
              "id": "chatcmpl-test",
              "model": "deepseek-v4-flash",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "连接成功"
                  },
                  "finish_reason": "stop"
                }
              ]
            }
        """.trimIndent()

        assertEquals("连接成功", client.parseAssistantContent(response))
    }

    @Test
    fun completesChatThroughTransport() {
        val transport = CapturingTransport(
            response = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "周计划草稿"
                      }
                    }
                  ]
                }
            """.trimIndent(),
        )
        val client = AiChatClient(deepSeekConfig, transport)

        val content = client.complete(
            apiKey = "sk-test",
            systemPrompt = "你是健身计划助手。",
            userPrompt = "生成一周训练草稿。",
        )

        assertEquals("周计划草稿", content)
        assertEquals("https://api.deepseek.com/chat/completions", transport.lastUrl)
        assertEquals("Bearer sk-test", transport.lastHeaders["Authorization"])
        assertTrue(transport.lastBody.contains("生成一周训练草稿。"))
    }

    @Test
    fun buildsVisionRequestWithImageDataAndTextPrompt() {
        val client = AiChatClient(deepSeekConfig)

        val json = client.buildVisionRequestJson(
            systemPrompt = "识别食物并估算热量。",
            userPrompt = "这是一份训练后晚餐。",
            imageMimeType = "image/jpeg",
            imageBase64 = "abc123",
            temperature = 0.1,
        )

        assertTrue(json.contains("\"model\":\"deepseek-v4-flash\""))
        assertTrue(json.contains("\"type\":\"text\""))
        assertTrue(json.contains("这是一份训练后晚餐。"))
        assertTrue(json.contains("\"type\":\"image_url\""))
        assertTrue(json.contains("data:image/jpeg;base64,abc123"))
        assertTrue(json.contains("\"temperature\":0.1"))
    }

    private val deepSeekConfig = AiProviderConfig(
        id = "deepseek",
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        model = "deepseek-v4-flash",
    )

    private class CapturingTransport(private val response: String) : AiHttpTransport {
        lateinit var lastUrl: String
        lateinit var lastHeaders: Map<String, String>
        lateinit var lastBody: String

        override fun postJson(url: String, headers: Map<String, String>, body: String): String {
            lastUrl = url
            lastHeaders = headers
            lastBody = body
            return response
        }
    }
}
