package com.reater.app.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class FetchedComment(
    val externalId: String,
    val author: String,
    val text: String,
    val likeCount: Int,
    val parentExternalId: String? = null,
    val depth: Int = 0
)

data class FetchedMedia(
    val kind: String,
    val remoteUrl: String,
    val width: Int = 0,
    val height: Int = 0
)

data class FetchedPostResult(
    val shortcode: String,
    val authorHandle: String,
    val authorDisplayName: String,
    val authorProfileUrl: String,
    val authorVerified: Boolean,
    val postedAt: Long,
    val bodyText: String,
    val likeCount: Int,
    val replyCount: Int,
    val repostCount: Int,
    val comments: List<FetchedComment>,
    val media: List<FetchedMedia>,
    val rawJsonMin: String,
    val status: String // COMPLETE, PARTIAL, FAILED
)

@Singleton
class ThreadsGraphQLClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Built-in default doc_ids
    private val docIdPost = "5587632691339264"
    private val igAppId = "238260118697367"
    private val defaultUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    /**
     * Attempts to fetch Threads post and top-level comments using the unofficial GraphQL endpoint.
     * Robust error handling ensures fallback to manual editing without crash.
     */
    suspend fun fetchPostByPostIdOrShortcode(postID: String, shortcode: String): Result<FetchedPostResult> {
        return try {
            val formBody = FormBody.Builder()
                .add("variables", """{"postID":"$postID"}""")
                .add("doc_id", docIdPost)
                .add("lsd", "AVrP8_ABCDE")
                .build()

            val request = Request.Builder()
                .url("https://www.threads.net/api/graphql")
                .post(formBody)
                .header("User-Agent", defaultUserAgent)
                .header("x-ig-app-id", igAppId)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(IOException("HTTP error code: ${response.code}"))
            }

            val rawBody = response.body?.string() ?: return Result.failure(IOException("Empty response body"))
            parseGraphQLResponse(rawBody, shortcode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseGraphQLResponse(jsonStr: String, fallbackShortcode: String): Result<FetchedPostResult> {
        return try {
            val root = jsonParser.parseToJsonElement(jsonStr).jsonObject
            val data = root["data"]?.jsonObject
            val dataObj = data?.get("data")?.jsonObject ?: data

            val containingPost = dataObj?.get("containing_thread")?.jsonObject
                ?.get("thread_items")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("post")?.jsonObject ?: dataObj?.get("mediaData")?.jsonObject

            if (containingPost == null) {
                return Result.failure(IllegalStateException("No post found in GraphQL response payload"))
            }

            val captionObj = containingPost["caption"]?.jsonObject
            val bodyText = captionObj?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()

            val userObj = containingPost["user"]?.jsonObject
            val authorHandle = userObj?.get("username")?.jsonPrimitive?.contentOrNull.orEmpty()
            val authorDisplayName = userObj?.get("full_name")?.jsonPrimitive?.contentOrNull ?: authorHandle
            val authorProfileUrl = userObj?.get("profile_pic_url")?.jsonPrimitive?.contentOrNull.orEmpty()
            val authorVerified = userObj?.get("is_verified")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false

            val likeCount = containingPost["like_count"]?.jsonPrimitive?.intOrNull ?: 0
            val replyCount = containingPost["reply_count"]?.jsonPrimitive?.intOrNull ?: 0
            val repostCount = containingPost["repost_count"]?.jsonPrimitive?.intOrNull ?: 0
            val postedAt = (containingPost["taken_at"]?.jsonPrimitive?.intOrNull?.toLong() ?: 0L) * 1000L

            val commentsList = mutableListOf<FetchedComment>()
            val threadItems = dataObj["reply_threads"]?.jsonArray
                ?: dataObj["containing_thread"]?.jsonObject?.get("reply_threads")?.jsonArray

            threadItems?.forEach { replyThread ->
                val replyItems = replyThread.jsonObject["thread_items"]?.jsonArray
                replyItems?.forEach { replyItem ->
                    val replyPost = replyItem.jsonObject["post"]?.jsonObject
                    if (replyPost != null) {
                        val cId = replyPost["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val cUser = replyPost["user"]?.jsonObject?.get("username")?.jsonPrimitive?.contentOrNull.orEmpty()
                        val cText = replyPost["caption"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
                        val cLikes = replyPost["like_count"]?.jsonPrimitive?.intOrNull ?: 0
                        if (cText.isNotBlank()) {
                            commentsList.add(
                                FetchedComment(
                                    externalId = cId,
                                    author = cUser,
                                    text = cText,
                                    likeCount = cLikes,
                                    depth = 0
                                )
                            )
                        }
                    }
                }
            }

            // Media extraction
            val mediaList = mutableListOf<FetchedMedia>()
            val imageVersions = containingPost["image_versions2"]?.jsonObject?.get("candidates")?.jsonArray
            val firstCandidate = imageVersions?.firstOrNull()?.jsonObject
            val firstImageUrl = firstCandidate?.get("url")?.jsonPrimitive?.contentOrNull
            if (!firstImageUrl.isNullOrBlank()) {
                mediaList.add(
                    FetchedMedia(
                        kind = "IMAGE",
                        remoteUrl = firstImageUrl,
                        width = firstCandidate["width"]?.jsonPrimitive?.intOrNull ?: 0,
                        height = firstCandidate["height"]?.jsonPrimitive?.intOrNull ?: 0
                    )
                )
            }

            // Minimal JSON for provenance without leaking entire huge response
            val rawJsonMin = """{"id":"${containingPost["id"]?.jsonPrimitive?.contentOrNull}","code":"$fallbackShortcode"}"""

            Result.success(
                FetchedPostResult(
                    shortcode = fallbackShortcode,
                    authorHandle = authorHandle,
                    authorDisplayName = authorDisplayName,
                    authorProfileUrl = authorProfileUrl,
                    authorVerified = authorVerified,
                    postedAt = postedAt,
                    bodyText = bodyText,
                    likeCount = likeCount,
                    replyCount = replyCount,
                    repostCount = repostCount,
                    comments = commentsList,
                    media = mediaList,
                    rawJsonMin = rawJsonMin,
                    status = if (commentsList.isNotEmpty()) "PARTIAL" else "COMPLETE"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
