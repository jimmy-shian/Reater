package com.reater.app.domain

import org.json.JSONObject

/** 保存 Threads 獨立文字附件的完整原文。 */
object TextAttachmentStore {
    private const val KEY = "text_attachment"
    const val INLINE_MARKER = "\n\n\u0000reater_text_attachment\u0000\n"

    fun withTextAttachment(rawJson: String, text: String): String {
        val value = text
        if (value.isBlank()) return rawJson
        val root = runCatching { JSONObject(rawJson) }.getOrElse { JSONObject() }
        root.put(KEY, value)
        return root.toString()
    }

    fun read(rawJson: String): String = runCatching {
        JSONObject(rawJson).optString(KEY)
    }.getOrDefault("")

    fun appendToText(text: String, attachment: String): String {
        val body = text.trim()
        val value = attachment
        if (value.isBlank()) return body
        return body + INLINE_MARKER + value
    }
}
