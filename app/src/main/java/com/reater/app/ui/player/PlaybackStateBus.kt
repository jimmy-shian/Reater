package com.reater.app.ui.player

import com.reater.app.data.remote.MediaDedup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 播放狀態匯流排：InlineVideoPlayer／FullscreenVideoViewer 播放期間定時回報
 * position/duration/isPlaying，詳情頁動態歌詞卡以 MediaDedup.normalizeKey(remoteUrl)
 * 反查同一媒體的播放進度（卡拉OK 同步用）。
 *
 * - key 與 AudioMetaStore 相同（normalizeKey），同一媒體在內嵌／全螢幕播放器間共用一個狀態
 * - 暫停時回報最後進度（歌詞卡凍結在當前行），播放器 dispose 時清除
 */
object PlaybackStateBus {

    data class Snapshot(
        val positionMs: Long,
        val durationMs: Long,
        val isPlaying: Boolean
    )

    private val _states = MutableStateFlow<Map<String, Snapshot>>(emptyMap())

    /** key = MediaDedup.normalizeKey(remoteUrl) → 最新播放快照 */
    val states: StateFlow<Map<String, Snapshot>> = _states.asStateFlow()

    fun update(remoteUrl: String, positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        val key = MediaDedup.normalizeKey(remoteUrl)
        if (key.isBlank()) return
        _states.update { old -> old + (key to Snapshot(positionMs.coerceAtLeast(0L), durationMs.coerceAtLeast(0L), isPlaying)) }
    }

    fun clear(remoteUrl: String) {
        val key = MediaDedup.normalizeKey(remoteUrl)
        if (key.isBlank()) return
        _states.update { old -> if (old.containsKey(key)) old - key else old }
    }
}
