package com.reater.app.domain

import java.net.URI
import java.security.MessageDigest

object UrlParser {
    private val VALID_HOSTS = setOf(
        "threads.net",
        "www.threads.net",
        "threads.com",
        "www.threads.com"
    )

    // Match e.g. /@username/post/C_xyz123 or /t/C_xyz123
    private val POST_PATH_REGEX = Regex("""^/@?([A-Za-z0-9_.]+)/post/([A-Za-z0-9_-]+)""")

    data class ParsedThreadsUrl(
        val handle: String,
        val shortcode: String,
        val canonicalUrl: String,
        val urlHash: String
    )

    /**
     * Extracts first Threads URL from raw text (such as Intent.EXTRA_TEXT)
     */
    fun extractFirstThreadsUrl(text: String): String? {
        val urlRegex = Regex("""https?://[^\s]+""")
        val matches = urlRegex.findAll(text)
        for (match in matches) {
            val url = match.value.trimEnd('.', ',', ')', ']', '}')
            if (isThreadsHost(url)) {
                return url
            }
        }
        return null
    }

    private fun isThreadsHost(rawUrl: String): Boolean {
        return try {
            val uri = URI(rawUrl)
            val host = uri.host?.lowercase() ?: return false
            VALID_HOSTS.contains(host)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Canonicalizes Threads URL:
     * https://www.threads.com/@{lower(handle)}/post/{shortcode}
     */
    fun parseAndCanonicalize(rawUrl: String): ParsedThreadsUrl? {
        return try {
            val uri = URI(rawUrl)
            val host = uri.host?.lowercase() ?: return false
            if (!VALID_HOSTS.contains(host)) return false

            val path = uri.path ?: return false
            val match = POST_PATH_REGEX.find(path) ?: return false

            val handle = match.groupValues[1].lowercase()
            val shortcode = match.groupValues[2]

            val canonicalUrl = "https://www.threads.com/@$handle/post/$shortcode"
            val hash = sha256(canonicalUrl)

            ParsedThreadsUrl(
                handle = handle,
                shortcode = shortcode,
                canonicalUrl = canonicalUrl,
                urlHash = hash
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
