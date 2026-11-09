package com.reater.app.data.remote

/**
 * 貼文擷取與離線下載階段狀態。
 */
enum class FetchStage {
    IDLE,
    READING_POST,        // 1. 正在讀取貼文資料
    BODY_PARSED,         // 2. 已取得貼文內文
    FETCHING_COMMENTS,   // 3. 正在擷取留言
    DOWNLOADING_MEDIA,   // 4. 正在下載媒體
    COMPLETED,           // 5. 已完成
    PARTIAL_SUCCESS,     // 5. 部分內容取得失敗 / 部分完成
    FAILED               // 擷取失敗
}

data class FetchProgressInfo(
    val stage: FetchStage = FetchStage.IDLE,
    val message: String = "",
    val mediaIndex: Int = 0,
    val mediaTotal: Int = 0,
    val bytesRead: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBps: Long = 0L,
    val etaSeconds: Long = -1L
)
