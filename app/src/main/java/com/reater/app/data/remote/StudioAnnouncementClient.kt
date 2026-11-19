package com.reater.app.data.remote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

const val STUDIO_ANNOUNCEMENTS_URL = "https://home.yesolve-studio.workers.dev/announcements.json"
private const val STUDIO_APP_ID = "reater"
private const val PREF_NAME = "yesolve_announcements"
private const val KEY_SEEN_IDS = "seen_ids"
private const val KEY_DAILY_LAST_SHOWN = "daily_last_shown_json"

@Serializable
data class StudioAnnouncementsPayload(
    val version: Int = 1,
    val updated_at: String = "",
    val announcements: List<StudioAnnouncement> = emptyList()
)

@Serializable
data class StudioAnnouncement(
    val id: String = "",
    val apps: List<String> = emptyList(),
    val enabled: Boolean = true,
    val start_at: String = "",
    val end_at: String = "",
    val frequency: String = "once",
    val dismissible: Boolean = true,
    val tag: String = "",
    val title: String = "",
    val message: String = "",
    val cta_text: String = "",
    val cta_url: String = ""
)

object StudioAnnouncementClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun fetchActiveAnnouncement(context: Context): StudioAnnouncement? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(STUDIO_ANNOUNCEMENTS_URL)
                    .header("User-Agent", "Yesolve-Reater-Android")
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()

                val body = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("StudioAnnouncement", "HTTP ${response.code}")
                        return@withContext null
                    }
                    response.body?.string().orEmpty()
                }
                if (body.isBlank()) return@withContext null

                val payload = json.decodeFromString<StudioAnnouncementsPayload>(body)
                val nowMs = System.currentTimeMillis()
                payload.announcements.firstOrNull { item ->
                    isAnnouncementEligible(context, item, nowMs)
                }
            }.onFailure { err ->
                Log.w("StudioAnnouncement", "Fetch failed: ${err.message}")
            }.getOrNull()
        }

    fun markDismissed(context: Context, item: StudioAnnouncement) {
        if (item.id.isBlank()) return
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val freq = item.frequency.trim().lowercase()
        if (freq == "daily") {
            val map = parseDailyMap(prefs.getString(KEY_DAILY_LAST_SHOWN, null)).toMutableMap()
            map[item.id] = localDateKey()
            val serialized = map.entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) ->
                "\"${escapeJson(k)}\":\"${escapeJson(v)}\""
            }
            prefs.edit().putString(KEY_DAILY_LAST_SHOWN, serialized).apply()
        } else if (freq != "always") {
            val seen = prefs.getStringSet(KEY_SEEN_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
            seen.add(item.id)
            prefs.edit().putStringSet(KEY_SEEN_IDS, seen).apply()
        }
    }

    private fun isAnnouncementEligible(context: Context, item: StudioAnnouncement, nowMs: Long): Boolean {
        if (!item.enabled) return false
        if (item.id.isBlank()) return false
        if (item.title.isBlank() && item.message.isBlank()) return false

        val targets = item.apps.map { it.trim().lowercase() }
        if (targets.isNotEmpty() && "all" !in targets && STUDIO_APP_ID !in targets) {
            return false
        }

        val startMs = parseIsoMillis(item.start_at)
        val endMs = parseIsoMillis(item.end_at)
        if (item.start_at.isNotBlank() && (startMs == null || nowMs < startMs)) return false
        if (item.end_at.isNotBlank() && (endMs == null || nowMs > endMs)) return false

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val freq = item.frequency.trim().lowercase()
        return when (freq) {
            "always" -> true
            "daily" -> {
                val map = parseDailyMap(prefs.getString(KEY_DAILY_LAST_SHOWN, null))
                map[item.id] != localDateKey()
            }
            else -> {
                val seen = prefs.getStringSet(KEY_SEEN_IDS, emptySet()) ?: emptySet()
                item.id !in seen
            }
        }
    }

    private fun parseIsoMillis(raw: String): Long? {
        val text = raw.trim()
        if (text.isBlank()) return null
        return runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
    }

    private fun localDateKey(): String {
        val cal = java.util.Calendar.getInstance()
        val y = cal.get(java.util.Calendar.YEAR)
        val m = cal.get(java.util.Calendar.MONTH) + 1
        val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(y, m, d)
    }

    private fun parseDailyMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, String>>(raw)
        }.getOrDefault(emptyMap())
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
