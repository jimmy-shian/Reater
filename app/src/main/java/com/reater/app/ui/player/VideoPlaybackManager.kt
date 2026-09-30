package com.reater.app.ui.player

import java.util.concurrent.ConcurrentHashMap

/**
 * 全域影片播放協調器 (VideoPlaybackManager)
 *
 * 核心職責：
 * 1. 確保全 App 任何時候「只有一個」影片處於播放發聲狀態，杜絕多個播放器聲音打架。
 * 2. 註冊/協調播放器生命週期：當任一播放器開始播放時，自動調用先前活躍播放器的暫停回調。
 * 3. 跨層級控制：進入「詳細檢視 (DetailDialog)」、「全螢幕媒體檢視 (MediaViewerDialog)」或關閉對話框時，
 *    提供即時 pauseAll() 暫停所有背景播放器。
 */
object VideoPlaybackManager {
    @Volatile
    private var activePlayerId: String? = null
    private val pauseCallbacks = ConcurrentHashMap<String, () -> Unit>()

    /**
     * 當某個播放器開始播放時註冊
     * 自動通知並暫停先前正在播放的任何其他播放器
     */
    fun onPlayStarted(playerId: String, onPause: () -> Unit) {
        // 暫停先前所有其他播放器
        pauseCallbacks.forEach { (id, callback) ->
            if (id != playerId) {
                runCatching { callback() }
            }
        }
        pauseCallbacks.clear()
        pauseCallbacks[playerId] = onPause
        activePlayerId = playerId
    }

    /**
     * 當某個播放器手動暫停或播放完畢時調用
     */
    fun onPlayStopped(playerId: String) {
        if (activePlayerId == playerId) {
            activePlayerId = null
        }
        pauseCallbacks.remove(playerId)
    }

    /**
     * 一鍵暫停所有正在播放的播放器（例如打開詳細檢視、全螢幕檢視、切換分頁等場景）
     */
    fun pauseAll() {
        pauseCallbacks.forEach { (_, callback) ->
            runCatching { callback() }
        }
        pauseCallbacks.clear()
        activePlayerId = null
    }

    /**
     * 播放器組件銷毀 (onDispose) 時註銷
     */
    fun unregister(playerId: String) {
        pauseCallbacks.remove(playerId)
        if (activePlayerId == playerId) {
            activePlayerId = null
        }
    }
}
