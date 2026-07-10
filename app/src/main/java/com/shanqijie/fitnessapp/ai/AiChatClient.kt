package com.shanqijie.fitnessapp.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AiProviderConfig(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val model: String,
)

data class AiTestResult(
    val success: Boolean,
    val message: String,
)

interface AiHttpTransport {
    fun postJson(url: String, headers: Map<String, String>, body: String): String
}

class HttpUrlConnectionAiTransport : AiHttpTransport {
    override fun postJson(url: String, headers: Map<String, String>, body: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body)
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (status !in 200..299) {
            throw IllegalStateException("AI provider returned HTTP $status: ${response.take(200)}")
        }
        return response
    }
}

class AiChatClient(
    private val provider: AiProviderConfig,
    private val transport: AiHttpTransport = HttpUrlConnectionAiTransport(),
) {
    fun buildTestRequestJson(prompt: String): String =
        buildChatRequestJson(
            systemPrompt = "You are a connection test endpoint. Reply briefly.",
            userPrompt = prompt,
            temperature = 0.0,
        )

    fun buildChatRequestJson(systemPrompt: String, userPrompt: String, temperature: Double = 0.2): String =
        Json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("model", JsonPrimitive(provider.model))
                put("messages", buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", JsonPrimitive("system"))
                            put("content", JsonPrimitive(systemPrompt))
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put("content", JsonPrimitive(userPrompt))
                        },
                    )
                })
                put("temperature", JsonPrimitive(temperature))
                put(
                    "thinking",
                    buildJsonObject {
                        put("type", JsonPrimitive("disabled"))
                    },
                )
            },
        )

    fun buildVisionRequestJson(
        systemPrompt: String,
        userPrompt: String,
        imageMimeType: String,
        imageBase64: String,
        temperature: Double = 0.2,
    ): String =
        Json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("model", JsonPrimitive(provider.model))
                put("messages", buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", JsonPrimitive("system"))
                            put("content", JsonPrimitive(systemPrompt))
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put("content", buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("type", JsonPrimitive("text"))
                                        put("text", JsonPrimitive(userPrompt))
                                    },
                                )
                                add(
                                    buildJsonObject {
                                        put("type", JsonPrimitive("image_url"))
                                        put(
                                            "image_url",
                                            buildJsonObject {
                                                put("url", JsonPrimitive("data:$imageMimeType;base64,$imageBase64"))
                                            },
                                        )
                                    },
                                )
                            })
                        },
                    )
                })
                put("temperature", JsonPrimitive(temperature))
                put(
                    "thinking",
                    buildJsonObject {
                        put("type", JsonPrimitive("disabled"))
                    },
                )
            },
        )

    fun complete(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double = 0.2,
    ): String {
        val response = transport.postJson(
            url = "${provider.baseUrl.trimEnd('/')}/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json",
            ),
            body = buildChatRequestJson(systemPrompt, userPrompt, temperature),
        )
        return parseAssistantContent(response)
    }

    fun completeVision(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        imageMimeType: String,
        imageBase64: String,
        temperature: Double = 0.2,
    ): String {
        val response = transport.postJson(
            url = "${provider.baseUrl.trimEnd('/')}/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json",
            ),
            body = buildVisionRequestJson(
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageMimeType = imageMimeType,
                imageBase64 = imageBase64,
                temperature = temperature,
            ),
        )
        return parseAssistantContent(response)
    }

    fun testConnection(apiKey: String, prompt: String = "只回复：连接成功"): AiTestResult {
        val response = transport.postJson(
            url = "${provider.baseUrl.trimEnd('/')}/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json",
            ),
            body = buildTestRequestJson(prompt),
        )
        val content = parseAssistantContent(response)
        return AiTestResult(success = content.isNotBlank(), message = content.ifBlank { "连接成功" })
    }

    fun parseAssistantContent(responseJson: String): String {
        val root = Json.parseToJsonElement(responseJson).jsonObject
        val choices = root["choices"] as? JsonArray ?: return ""
        val first = choices.firstOrNull()?.jsonObject ?: return ""
        val message = first["message"]?.jsonObject ?: return ""
        return message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }
}
