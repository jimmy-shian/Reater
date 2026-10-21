package com.reater.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val response_format: ResponseFormat? = null,
    val temperature: Double = 0.3
)

@Serializable
data class ResponseFormat(
    val type: String
)

@Serializable
data class OpenAiMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAiChatResponse(
    val choices: List<OpenAiChoice> = emptyList(),
    val usage: OpenAiUsage? = null
)

@Serializable
data class OpenAiChoice(
    val message: OpenAiMessage
)

@Serializable
data class OpenAiUsage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0
)

@Serializable
data class AiAnalysisResult(
    val category: String,
    val tags: List<String> = emptyList(),
    val summary: String,
    val confidence: Double = 1.0
)

class OpenAiApiException(val code: Int, val rawBody: String) : IOException("HTTP $code: $rawBody")

@Singleton
class OpenAiClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchModels(apiKey: String, customBaseUrl: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            val baseUrl = customBaseUrl.trim().removeSuffix("/").ifBlank {
                return@withContext Result.success(emptyList())
            }
            runCatching {
                val request = Request.Builder()
                    .url("$baseUrl/models")
                    .apply {
                        if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey")
                    }
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException("Model API HTTP error ${response.code}: $body")
                    }
                    json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
                        ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
                        ?.filter(String::isNotBlank)
                        ?.distinct()
                        ?.sorted()
                        .orEmpty()
                }
            }
        }

    /**
     * Executes AI categorization and summarization in JSON mode.
     * Compatible with OpenAI and OpenAI-compatible endpoints (DeepSeek, Groq, Ollama, OpenRouter, etc.)
     */
    suspend fun analyzePost(
        apiKey: String,
        model: String,
        customBaseUrl: String?,
        postContent: String,
        commentsContent: String
    ): Result<Pair<AiAnalysisResult, OpenAiUsage>> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("API Key is empty"))
        }

        val baseUrl = customBaseUrl?.trim()?.removeSuffix("/")?.ifBlank { null }
            ?: "https://api.openai.com/v1"
        val endpoint = "$baseUrl/chat/completions"

        val systemPrompt = """
            You are a content analyzer for 'Reater'. Analyze the given Threads post and comments.
            Output ONLY valid JSON matching this schema:
            {
              "category": "string (a short 2-6 character topic label, e.g. 科技, 生活, 讀書)",
              "tags": ["tag1", "tag2", "tag3"], // at most 3 tags
              "summary": "string (under 120 words capturing the core message and consensus)",
              "confidence": 0.95
            }
        """.trimIndent()

        val userPrompt = """
            [POST BODY]:
            $postContent

            [NOTABLE COMMENTS]:
            $commentsContent
        """.trimIndent()

        val requestPayload = OpenAiChatRequest(
            model = model.ifBlank { "gpt-5-nano" },
            messages = listOf(
                OpenAiMessage(role = "system", content = systemPrompt),
                OpenAiMessage(role = "user", content = userPrompt)
            ),
            response_format = ResponseFormat(type = "json_object"),
            temperature = 0.3
        )

        val bodyString = json.encodeToString(OpenAiChatRequest.serializer(), requestPayload)

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(bodyString.toRequestBody(jsonMediaType))
            .build()

        return withContext(Dispatchers.IO) { try {
            val response = client.newCall(request).execute()
            val rawResponse = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(OpenAiApiException(response.code, rawResponse))
            }

            val parsedResponse = json.decodeFromString(OpenAiChatResponse.serializer(), rawResponse)
            val contentJson = parsedResponse.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(IllegalStateException("Empty AI response"))

            // Strip possible markdown fences if compatible model outputs ```json ... ```
            val cleanedJson = contentJson.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val analysis = json.decodeFromString(AiAnalysisResult.serializer(), cleanedJson)
            val usage = parsedResponse.usage ?: OpenAiUsage()

            Result.success(Pair(analysis, usage))
        } catch (e: Exception) {
            Result.failure(e)
        } }
    }

    fun computeInputHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun formatAiError(throwable: Throwable?): String {
            if (throwable == null) return "AI 分析失敗，請稍後再試"
            if (throwable is OpenAiApiException) {
                return when (throwable.code) {
                    401 -> "API 金鑰無效或尚未授權，請至設定檢查"
                    403 -> "存取遭拒絕，此金鑰沒有該模型的呼叫權限"
                    404 -> "找不到指定的模型或 API 網址不存在"
                    429 -> "請求次數過於頻繁或額度已用盡，請稍後再試"
                    500, 502, 503, 504 -> "AI 服務伺服器連線異常，請稍後重試"
                    else -> "連線失敗，請稍後再試"
                }
            }
            val msg = throwable.message.orEmpty()
            val codeMatch = Regex("""\b(401|403|404|429|500|502|503|504)\b""").find(msg)?.value?.toIntOrNull()
            if (codeMatch != null) {
                return when (codeMatch) {
                    401 -> "API 金鑰無效或尚未授權，請至設定檢查"
                    403 -> "存取遭拒絕，此金鑰沒有該模型的呼叫權限"
                    404 -> "找不到指定的模型或 API 網址不存在"
                    429 -> "請求次數過於頻繁或額度已用盡，請稍後再試"
                    500, 502, 503, 504 -> "AI 服務伺服器連線異常，請稍後重試"
                    else -> "連線失敗，請稍後再試"
                }
            }
            if (throwable is java.net.SocketTimeoutException) {
                return "AI 伺服器回應超時，請檢查網路連線後再試"
            }
            if (throwable is java.net.UnknownHostException) {
                return "無法連線至 AI 伺服器，請檢查網路或網址設定"
            }
            return if (msg.isNotBlank() && !msg.contains("Exception") && !msg.contains("HTTP") && !msg.contains("failed to connect")) {
                msg
            } else {
                "AI 分析失敗，請稍後再試"
            }
        }
    }
}
