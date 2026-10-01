package com.reater.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
                return@withContext Result.failure(IOException("API HTTP error ${response.code}: $rawResponse"))
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
}
