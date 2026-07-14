package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.ai.AiChatClient
import com.shanqijie.fitnessapp.ai.AiHttpTransport
import com.shanqijie.fitnessapp.ai.AiProviderConfig
import com.shanqijie.fitnessapp.ai.AiProviderCatalog
import com.shanqijie.fitnessapp.ai.HttpUrlConnectionAiTransport
import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatClientTest {
    @Test
    fun providerCatalogMatchesTheThreeSupportedServices() {
        assertEquals(listOf("openai", "gemini", "qwen"), AiProviderCatalog.entries.map { it.id })
        assertEquals(listOf("gpt-5-mini", "gpt-5", "gpt-4.1-mini"), AiProviderCatalog.entry("openai")?.models)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai", AiProviderCatalog.entry("gemini")?.endpoints?.single())
        assertEquals(3, AiProviderCatalog.entry("qwen")?.endpoints?.size)
        assertEquals(null, AiProviderCatalog.entry("unsupported"))
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
        assertTrue(client.buildChatRequestJson("system", "user").contains("\"temperature\":0.2"))
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
        assertTrue(
            client.buildVisionRequestJson("system", "user", "image/png", "data")
                .contains("\"temperature\":0.2"),
        )
    }

    @Test
    fun completesVisionAndConnectionChecksThroughTheConfiguredTransport() {
        val visionTransport = CapturingTransport("""{"choices":[{"message":{"content":"约 520 千卡"}}]}""")
        val visionClient = AiChatClient(deepSeekConfig.copy(baseUrl = "https://api.deepseek.com/"), visionTransport)

        assertEquals(
            "约 520 千卡",
            visionClient.completeVision(
                apiKey = "sk-vision",
                systemPrompt = "识别食物",
                userPrompt = "估算图片",
                imageMimeType = "image/png",
                imageBase64 = "abc123",
            ),
        )
        assertEquals("https://api.deepseek.com/chat/completions", visionTransport.lastUrl)
        assertEquals("Bearer sk-vision", visionTransport.lastHeaders["Authorization"])
        assertTrue(visionTransport.lastBody.contains("data:image/png;base64,abc123"))

        val connection = AiChatClient(
            deepSeekConfig,
            CapturingTransport("""{"choices":[{"message":{"content":""}}]}"""),
        ).testConnection("sk-test")
        assertFalse(connection.success)
        assertEquals("连接成功", connection.message)

        val successfulConnection = AiChatClient(
            deepSeekConfig,
            CapturingTransport("""{"choices":[{"message":{"content":"模型在线"}}]}"""),
        ).testConnection("sk-test", prompt = "ping")
        assertTrue(successfulConnection.success)
        assertEquals("模型在线", successfulConnection.message)
    }

    @Test
    fun parserReturnsBlankForIncompleteCompatibleResponses() {
        val client = AiChatClient(deepSeekConfig)

        assertEquals("", client.parseAssistantContent("{}"))
        assertEquals("", client.parseAssistantContent("""{"choices":[]}"""))
        assertEquals("", client.parseAssistantContent("""{"choices":[{}]}"""))
        assertEquals("", client.parseAssistantContent("""{"choices":[{"message":{}}]}"""))
        assertEquals("", client.parseAssistantContent("""{"choices":[{"message":{"content":null}}]}"""))
    }

    @Test
    fun urlConnectionTransportReadsSuccessAndReportsProviderErrors() {
        val transport = HttpUrlConnectionAiTransport()
        OneShotHttpServer(200, "ok").use { server ->
            assertEquals(
                "ok",
                transport.postJson(server.url, mapOf("Content-Type" to "application/json"), "{\"ping\":true}"),
            )
            server.awaitRequest()
            assertTrue(server.request.contains("POST / HTTP/1.1"))
            assertTrue(server.request.endsWith("{\"ping\":true}"))
        }
        OneShotHttpServer(401, "invalid api key").use { server ->
            val failure = runCatching {
                transport.postJson(server.url, emptyMap(), "{}")
            }.exceptionOrNull()
            server.awaitRequest()
            assertTrue(failure is IllegalStateException)
            assertTrue(failure?.message.orEmpty().contains("HTTP 401: invalid api key"))
        }
        OneShotHttpServer(299, "upper success boundary").use { server ->
            assertEquals("upper success boundary", transport.postJson(server.url, emptyMap(), "{}"))
            server.awaitRequest()
        }
        OneShotHttpServer(300, "redirect boundary").use { server ->
            val failure = runCatching { transport.postJson(server.url, emptyMap(), "{}") }.exceptionOrNull()
            server.awaitRequest()
            assertTrue(failure is IllegalStateException)
        }
        OneShotHttpServer(199, "interim response").use { server ->
            val failure = runCatching {
                transport.postJson(server.url, emptyMap(), "{}")
            }.exceptionOrNull()
            server.awaitRequest()
            assertTrue(failure != null)
        }
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

    private class OneShotHttpServer(
        private val status: Int,
        private val response: String,
    ) : AutoCloseable {
        private val socket = ServerSocket(0)
        @Volatile var request: String = ""
            private set
        val url = "http://127.0.0.1:${socket.localPort}/"
        private val worker = Thread {
            socket.accept().use { client ->
                val input = client.getInputStream()
                val headerBytes = mutableListOf<Byte>()
                while (headerBytes.takeLast(4) != listOf<Byte>(13, 10, 13, 10)) {
                    val value = input.read()
                    if (value < 0) break
                    headerBytes += value.toByte()
                }
                val header = headerBytes.toByteArray().toString(Charsets.UTF_8)
                val contentLength = Regex("Content-Length: (\\d+)", RegexOption.IGNORE_CASE)
                    .find(header)?.groupValues?.get(1)?.toInt() ?: 0
                val body = ByteArray(contentLength)
                var offset = 0
                while (offset < body.size) {
                    val read = input.read(body, offset, body.size - offset)
                    if (read < 0) break
                    offset += read
                }
                request = header + body.copyOf(offset).toString(Charsets.UTF_8)
                val reason = if (status in 200..299) "OK" else "Unauthorized"
                val responseBytes = response.toByteArray()
                client.getOutputStream().use { output ->
                    output.write("HTTP/1.1 $status $reason\r\nContent-Length: ${responseBytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write(responseBytes)
                }
            }
        }.apply { start() }

        fun awaitRequest() = worker.join(5_000)

        override fun close() {
            socket.close()
            worker.join(5_000)
        }
    }
}
