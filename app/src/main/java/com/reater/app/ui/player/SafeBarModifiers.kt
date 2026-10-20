package com.reater.app.ui.player

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 尋找 Compose Dialog 所在的真實 Window。
 */
fun findDialogWindow(view: View): Window? {
    if (view is DialogWindowProvider) return view.window
    var parent = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        parent = parent.parent
    }
    var ctx = view.context
    while (ctx is ContextWrapper) {
        if (ctx is DialogWindowProvider) return ctx.window
        ctx = ctx.baseContext
    }
    return (view.context.findPlayerActivity())?.window
}

/**
 * 將 Dialog Window 設置為全螢幕沉浸式（透明系統列、延伸至瀏海區域）。
 */
@Suppress("DEPRECATION")
fun applyDialogWindowImmersive(window: Window?) {
    window?.let { win ->
        WindowCompat.setDecorFitsSystemWindows(win, false)
        // Compose DialogLayout 只設 setLayout(MATCH_PARENT, WRAP_CONTENT)：
        // WRAP_CONTENT 子視窗的 frame 由 WMS 依「父視窗 visible frame」動態推導，
        // 在 One UI 上會下移一段系統列高度 → 頂部露出底層內容、高度不足。
        // 明確設為 MATCH_PARENT × MATCH_PARENT 對齊整個螢幕。
        win.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        win.statusBarColor = android.graphics.Color.TRANSPARENT
        win.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            win.isNavigationBarContrastEnforced = false
            win.isStatusBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            win.attributes = win.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    }
}

/** 隱藏控制列時讓系統列一同進入沉浸模式，邊緣滑動仍可暫時叫出。 */
fun setFullscreenSystemBarsVisible(window: Window?, visible: Boolean, activity: Activity? = null) {
    window?.let { win ->
        WindowCompat.getInsetsController(win, win.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (visible) show(WindowInsetsCompat.Type.systemBars())
            else hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    activity?.window?.let { actWin ->
        if (actWin != window) {
            WindowCompat.getInsetsController(actWin, actWin.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (visible) show(WindowInsetsCompat.Type.systemBars())
                else hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

/**
 * 取得影片真實寬高（考慮 EXIF/MP4 轉向角度）。
 * 若轉向為 90 或 270 度，寬高對調。
 */
fun getVideoDimensions(localPath: String): Pair<Int, Int>? {
    if (localPath.isBlank()) return null
    val file = File(localPath)
    if (!file.exists() || file.length() == 0L) return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        val w = wStr?.toIntOrNull() ?: 0
        val h = hStr?.toIntOrNull() ?: 0
        val rot = rotStr?.toIntOrNull() ?: 0
        if (w > 0 && h > 0) {
            if (rot == 90 || rot == 270) Pair(h, w) else Pair(w, h)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

@Suppress("DEPRECATION")
fun getRealDisplayMetricsDiff(context: Context, activity: Activity?): Rect {
    val act = activity ?: context.findPlayerActivity() ?: return Rect()
    val wm = act.windowManager ?: return Rect()
    return try {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            act.display
        } else {
            wm.defaultDisplay
        } ?: return Rect()

        val realSize = Point()
        val size = Point()
        display.getRealSize(realSize)
        display.getSize(size)

        val diffX = (realSize.x - size.x).coerceAtLeast(0)
        val diffY = (realSize.y - size.y).coerceAtLeast(0)
        val rotation = display.rotation

        if (diffX > 0) {
            if (rotation == Surface.ROTATION_270) {
                Rect(diffX, 0, 0, 0)
            } else {
                Rect(0, 0, diffX, 0)
            }
        } else if (diffY > 0) {
            Rect(0, 0, 0, diffY)
        } else {
            Rect()
        }
    } catch (_: Throwable) {
        Rect()
    }
}

fun getSystemNavigationBarHeightPx(context: Context): Int {
    val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
    return if (resId > 0) runCatching { context.resources.getDimensionPixelSize(resId) }.getOrDefault(0) else 0
}

fun getSystemNavigationBarLandscapeHeightPx(context: Context): Int {
    val resId = context.resources.getIdentifier("navigation_bar_height_landscape", "dimen", "android")
    return if (resId > 0) runCatching { context.resources.getDimensionPixelSize(resId) }.getOrDefault(0) else 0
}

fun getSystemNavigationBarWidthPx(context: Context): Int {
    val id1 = context.resources.getIdentifier("navigation_bar_width", "dimen", "android")
    val id2 = context.resources.getIdentifier("navigation_bar_height_landscape", "dimen", "android")
    val id3 = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
    val w1 = if (id1 > 0) runCatching { context.resources.getDimensionPixelSize(id1) }.getOrDefault(0) else 0
    val w2 = if (id2 > 0) runCatching { context.resources.getDimensionPixelSize(id2) }.getOrDefault(0) else 0
    val w3 = if (id3 > 0) runCatching { context.resources.getDimensionPixelSize(id3) }.getOrDefault(0) else 0
    return maxOf(w1, w2, w3)
}

fun getSystemStatusBarHeightPx(context: Context): Int {
    val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (resId > 0) runCatching { context.resources.getDimensionPixelSize(resId) }.getOrDefault(0) else 0
}

data class MediaViewerSafeInsets(
    val left: Dp = 0.dp,
    val top: Dp = 0.dp,
    val right: Dp = 0.dp,
    val bottom: Dp = 0.dp
)

/**
 * 取得全螢幕媒體檢視器的 Safe Area Insets（供頂部/底部控制列定位）。
 *
 * 核心原則：
 * 1. 嚴禁使用固定螢幕比例、固定 px / dp 來推算系統 UI 空間。
 * 2. 嚴格依實際 Window Insets 動態計算：
 *    statusBars, navigationBars, navigationBarsIgnoringVisibility, displayCutout, safeDrawing, systemBars。
 * 3. 解決 Navigation Bar 隱藏回報 0 問題：
 *    取 max(visibleNavigationBars, navigationBarsIgnoringVisibility)，並以 Activity decorView.rootWindowInsets、
 *    WindowManager currentWindowMetrics、實體螢幕尺寸差值以及系統資源尺寸多層保底，絕不被 0 誤導。
 * 4. 橫向模式全方位處理：
 *    同時計算 left, top, right, bottom，三鍵導覽列在側邊（常見於右側或左側）與挖孔螢幕時自動避讓，不假設左右永遠可用。
 * 5. 動態響應：
 *    旋轉、系統列顯隱、控制列顯隱時即時重新 Layout，操作元件永不超出安全操作範圍。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberMediaViewerSafeInsets(
    window: Window?,
    view: View,
    controlsVisible: Boolean = true
): MediaViewerSafeInsets {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val direction = LocalLayoutDirection.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = remember(view) { view.context.findPlayerActivity() }
    val effectiveWindow = window ?: remember(view) { findDialogWindow(view) }

    // 1. Compose 內建響應式 Insets（當系統列或導覽列變化時自動觸發 Recomposition）
    val composeNavBars = WindowInsets.navigationBars
    val composeNavBarsIgnoring = WindowInsets.navigationBarsIgnoringVisibility
    val composeStatusBars = WindowInsets.statusBars
    val composeStatusBarsIgnoring = WindowInsets.statusBarsIgnoringVisibility
    val composeCutout = WindowInsets.displayCutout
    val composeSafeDrawing = WindowInsets.safeDrawing
    val composeSystemBars = WindowInsets.systemBars
    val composeSystemBarsIgnoring = WindowInsets.systemBarsIgnoringVisibility

    // Compose 端即時像素值提取
    val cNavLeftPx = maxOf(composeNavBars.getLeft(density, direction), composeNavBarsIgnoring.getLeft(density, direction))
    val cNavRightPx = maxOf(composeNavBars.getRight(density, direction), composeNavBarsIgnoring.getRight(density, direction))
    val cNavBottomPx = maxOf(composeNavBars.getBottom(density), composeNavBarsIgnoring.getBottom(density))
    val cNavTopPx = maxOf(composeNavBars.getTop(density), composeNavBarsIgnoring.getTop(density))

    val cStatusTopPx = maxOf(composeStatusBars.getTop(density), composeStatusBarsIgnoring.getTop(density))

    val cCutoutLeftPx = composeCutout.getLeft(density, direction)
    val cCutoutRightPx = composeCutout.getRight(density, direction)
    val cCutoutTopPx = composeCutout.getTop(density)
    val cCutoutBottomPx = composeCutout.getBottom(density)

    val cSafeDrawingLeftPx = composeSafeDrawing.getLeft(density, direction)
    val cSafeDrawingRightPx = composeSafeDrawing.getRight(density, direction)
    val cSafeDrawingTopPx = composeSafeDrawing.getTop(density)
    val cSafeDrawingBottomPx = composeSafeDrawing.getBottom(density)

    val cSystemBarsLeftPx = maxOf(composeSystemBars.getLeft(density, direction), composeSystemBarsIgnoring.getLeft(density, direction))
    val cSystemBarsRightPx = maxOf(composeSystemBars.getRight(density, direction), composeSystemBarsIgnoring.getRight(density, direction))
    val cSystemBarsTopPx = maxOf(composeSystemBars.getTop(density), composeSystemBarsIgnoring.getTop(density))
    val cSystemBarsBottomPx = maxOf(composeSystemBars.getBottom(density), composeSystemBarsIgnoring.getBottom(density))

    // 2. 系統硬體資源尺寸保底（即使特定 OEM 深度定制系統在 WindowInsets 報告 0 也能保底）
    val resNavHeightPx = remember(view) { getSystemNavigationBarHeightPx(view.context) }
    val resNavWidthPx = remember(view) { getSystemNavigationBarWidthPx(view.context) }
    val resStatusHeightPx = remember(view) { getSystemStatusBarHeightPx(view.context) }

    fun collectCompatSources(): List<WindowInsetsCompat?> {
        val list = mutableListOf<WindowInsetsCompat?>()
        effectiveWindow?.decorView?.let { ViewCompat.getRootWindowInsets(it) }?.let { list.add(it) }
        ViewCompat.getRootWindowInsets(view)?.let { list.add(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = activity?.windowManager ?: (view.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            runCatching {
                wm?.currentWindowMetrics?.windowInsets?.let {
                    list.add(WindowInsetsCompat.toWindowInsetsCompat(it, effectiveWindow?.decorView ?: view))
                }
            }
        }
        activity?.window?.decorView?.let { ViewCompat.getRootWindowInsets(it) }?.let { list.add(it) }
        return list
    }

    fun computeCurrentInsets(
        compatSources: List<WindowInsetsCompat?>,
        systemBarsShown: Boolean
    ): MediaViewerSafeInsets {
        var rawNavLeftPx = cNavLeftPx
        var rawNavRightPx = cNavRightPx
        var rawNavBottomPx = cNavBottomPx
        var rawNavTopPx = cNavTopPx

        var rawCutoutLeftPx = cCutoutLeftPx
        var rawCutoutRightPx = cCutoutRightPx
        var rawCutoutTopPx = cCutoutTopPx
        var rawCutoutBottomPx = cCutoutBottomPx

        var rawStatusTopPx = cStatusTopPx

        for (compat in compatSources) {
            if (compat == null) continue
            val nav = compat.getInsets(WindowInsetsCompat.Type.navigationBars())
            val navIgnored = compat.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
            val status = compat.getInsets(WindowInsetsCompat.Type.statusBars())
            val statusIgnored = compat.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())
            val cutout = compat.getInsets(WindowInsetsCompat.Type.displayCutout())
            val cutoutIgnored = compat.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout())
            val sys = compat.getInsets(WindowInsetsCompat.Type.systemBars())
            val sysIgnored = compat.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())

            rawNavLeftPx = maxOf(rawNavLeftPx, nav.left, navIgnored.left, sys.left, sysIgnored.left)
            rawNavRightPx = maxOf(rawNavRightPx, nav.right, navIgnored.right, sys.right, sysIgnored.right)
            rawNavBottomPx = maxOf(rawNavBottomPx, nav.bottom, navIgnored.bottom, sys.bottom, sysIgnored.bottom)
            rawNavTopPx = maxOf(rawNavTopPx, nav.top, navIgnored.top, sys.top, sysIgnored.top)

            rawCutoutLeftPx = maxOf(rawCutoutLeftPx, cutout.left, cutoutIgnored.left)
            rawCutoutRightPx = maxOf(rawCutoutRightPx, cutout.right, cutoutIgnored.right)
            rawCutoutTopPx = maxOf(rawCutoutTopPx, cutout.top, cutoutIgnored.top)
            rawCutoutBottomPx = maxOf(rawCutoutBottomPx, cutout.bottom, cutoutIgnored.bottom)

            rawStatusTopPx = maxOf(rawStatusTopPx, status.top, statusIgnored.top)
        }

        // Display bounds diff fallback (API < 30 或特定 OEM 導覽列寬度/高度)
        val realDiff = getRealDisplayMetricsDiff(view.context, activity)
        if (realDiff.bottom > 0) rawNavBottomPx = maxOf(rawNavBottomPx, realDiff.bottom)
        if (realDiff.right > 0) rawNavRightPx = maxOf(rawNavRightPx, realDiff.right)
        if (realDiff.left > 0) rawNavLeftPx = maxOf(rawNavLeftPx, realDiff.left)

        // 合成 Left: 考慮導覽列（橫向時可能在左側）、Display Cutout、SafeDrawing、SystemBars
        val combinedLeftPx = maxOf(
            rawNavLeftPx,
            rawCutoutLeftPx,
            cSafeDrawingLeftPx,
            cSystemBarsLeftPx
        )
        var leftDp = with(density) { combinedLeftPx.toDp() }

        // 合成 Right: 考慮導覽列（橫向時常見在右側）、Display Cutout、SafeDrawing、SystemBars
        val combinedRightPx = maxOf(
            rawNavRightPx,
            rawCutoutRightPx,
            cSafeDrawingRightPx,
            cSystemBarsRightPx
        )
        var rightDp = with(density) { combinedRightPx.toDp() }

        // 橫向模式下，如果左右兩側測量值都為 0，但系統資源指出有導覽列寬度（三鍵導覽列）：
        if (isLandscape) {
            if (leftDp <= 0.dp && rightDp <= 0.dp && resNavWidthPx > 0) {
                rightDp = with(density) { resNavWidthPx.toDp() }
            }
        }

        // 合成 Top: 考慮 Status Bar、Display Cutout、SafeDrawing、SystemBars
        val combinedTopPx = maxOf(
            rawStatusTopPx,
            rawCutoutTopPx,
            cSafeDrawingTopPx,
            cSystemBarsTopPx,
            resStatusHeightPx
        )
        val topDp = with(density) { combinedTopPx.toDp() }

        // 合成 Bottom:
        // 直向時，導覽列位於底部，必須避讓 navigationBars, navigationBarsIgnoringVisibility, safeDrawing，並以 resNavHeightPx 保底；
        // 橫向時，導覽列位於側邊，底部僅在有手勢橫條（safeDrawing/navigationBars）時避讓，若測量為 0 則為 0。
        val combinedBottomPx = maxOf(
            rawNavBottomPx,
            rawCutoutBottomPx,
            cSafeDrawingBottomPx,
            cSystemBarsBottomPx
        )
        val bottomDp = if (!isLandscape) {
            val measured = with(density) { combinedBottomPx.toDp() }
            if (measured > 0.dp) measured else with(density) { resNavHeightPx.toDp() }
        } else {
            with(density) { combinedBottomPx.toDp() }
        }

        // 檢視器不把頂部導覽列納入布局；頂部控制列以 overlay 顯示。
        return MediaViewerSafeInsets(left = leftDp, top = 0.dp, right = rightDp, bottom = bottomDp)
    }

    var trigger by remember { mutableIntStateOf(0) }

    DisposableEffect(effectiveWindow, view, activity, configuration.orientation) {
        val targetDecor = effectiveWindow?.decorView ?: activity?.window?.decorView
        val listener = androidx.core.view.OnApplyWindowInsetsListener { _, insets ->
            trigger++
            insets
        }
        if (targetDecor != null) {
            ViewCompat.setOnApplyWindowInsetsListener(targetDecor, listener)
            targetDecor.requestApplyInsets()
        }
        onDispose {
            if (targetDecor != null) {
                ViewCompat.setOnApplyWindowInsetsListener(targetDecor, null)
            }
        }
    }

    LaunchedEffect(controlsVisible, configuration.orientation) {
        effectiveWindow?.decorView?.requestApplyInsets()
        activity?.window?.decorView?.requestApplyInsets()
        view.requestApplyInsets()
        trigger++
    }

    // 這裡直接計算，trigger 或 Compose reactive Insets 變動即時重算
    return remember(
        trigger,
        configuration.orientation,
        controlsVisible,
        cNavBottomPx,
        cNavRightPx,
        cNavLeftPx,
        cStatusTopPx,
        cCutoutLeftPx,
        cCutoutRightPx,
        cSafeDrawingBottomPx,
        cSafeDrawingRightPx,
        cSafeDrawingLeftPx
    ) {
        computeCurrentInsets(collectCompatSources(), controlsVisible)
    }
}

/**
 * 頂部控制列 Safe Area 修飾符：
 * 避讓頂部 status bar / cutout，以及左右兩側 navigation bar / cutout。
 */
@Composable
fun fullscreenTopBarModifier(safeInsets: MediaViewerSafeInsets): Modifier =
    Modifier.padding(
        start = safeInsets.left + 16.dp,
        end = safeInsets.right + 16.dp,
        // 頂部導覽列只顯示在 overlay，不參與媒體區域的高度計算。
        top = 8.dp,
        bottom = 8.dp
    )

/**
 * 底部控制列 Safe Area 修飾符：
 * 底部放在 navigationBarInset + 16dp 額外間距之上，確保三鍵與手勢導覽列皆不遮擋。
 * 橫向時同時避讓左側或右側導覽列。
 */
@Composable
fun fullscreenBottomBarModifier(safeInsets: MediaViewerSafeInsets): Modifier =
    Modifier.padding(
        start = safeInsets.left + 16.dp,
        end = safeInsets.right + 16.dp,
        top = 8.dp,
        bottom = safeInsets.bottom + 28.dp
    )

@Composable
fun fullscreenTopBarModifier(isLandscape: Boolean): Modifier {
    val view = LocalView.current
    val window = findDialogWindow(view)
    val safeInsets = rememberMediaViewerSafeInsets(window, view, controlsVisible = true)
    return fullscreenTopBarModifier(safeInsets)
}

@Composable
fun fullscreenBottomBarModifier(isLandscape: Boolean): Modifier {
    val view = LocalView.current
    val window = findDialogWindow(view)
    val safeInsets = rememberMediaViewerSafeInsets(window, view, controlsVisible = true)
    return fullscreenBottomBarModifier(safeInsets)
}

/**
 * 媒體層（圖片與影片本體）：
 * 依規格媒體本體採 Edge-to-Edge 呈現（黑色底色與內容延伸至邊界），不進行縮限，
 * 僅將可點擊操作 UI 限制在 Safe Area 之內。
 */
@Composable
fun fullscreenContentModifier(isLandscape: Boolean = false): Modifier = Modifier

fun Context.findPlayerActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

/**
 * 將圖片或影片儲存到系統相簿（Pictures/Reater 或 Movies/Reater）。
 * 若已有本機檔案直接轉存；若僅有遠端網址則於背景下載後轉存，並以 Toast 提示進度。
 */
suspend fun saveMediaToGalleryWithFeedback(
    context: Context,
    isVideo: Boolean,
    localPath: String,
    remoteUrl: String
) {
    val localFile = if (localPath.isNotBlank()) File(localPath).takeIf { it.exists() && it.length() > 0 } else null

    if (localFile == null && remoteUrl.isBlank()) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "無有效媒體檔案可儲存", Toast.LENGTH_SHORT).show()
        }
        return
    }

    if (localFile != null) {
        val ok = withContext(Dispatchers.IO) {
            copyFileToGallery(context, localFile, isVideo)
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, if (ok) "已儲存到相簿" else "儲存失敗", Toast.LENGTH_SHORT).show()
        }
        return
    }

    // 需由遠端網址下載
    withContext(Dispatchers.Main) {
        Toast.makeText(context, if (isVideo) "正在下載影片至相簿..." else "正在下載圖片至相簿...", Toast.LENGTH_SHORT).show()
    }

    val ok = withContext(Dispatchers.IO) {
        val downloaded = downloadToTemp(context, remoteUrl, isVideo) ?: return@withContext false
        try {
            copyFileToGallery(context, downloaded, isVideo)
        } finally {
            runCatching { downloaded.delete() }
        }
    }

    withContext(Dispatchers.Main) {
        Toast.makeText(context, if (ok) "已儲存到相簿" else "下載或儲存失敗，請檢查網路連線", Toast.LENGTH_SHORT).show()
    }
}

fun copyFileToGallery(context: Context, src: File, isVideo: Boolean): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val ext = src.extension.ifBlank { if (isVideo) "mp4" else "jpg" }
            val mime = if (isVideo) {
                when (ext.lowercase()) {
                    "webm" -> "video/webm"
                    "mkv" -> "video/x-matroska"
                    "3gp" -> "video/3gpp"
                    else -> "video/mp4"
                }
            } else {
                when (ext.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    else -> "image/jpeg"
                }
            }
            val relativeDir = if (isVideo) "${Environment.DIRECTORY_MOVIES}/Reater" else "${Environment.DIRECTORY_PICTURES}/Reater"
            val contentUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "reater_${System.currentTimeMillis()}.$ext")
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(contentUri, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val pubDir = Environment.getExternalStoragePublicDirectory(
                if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            )
            val dir = File(pubDir, "Reater").apply { if (!exists()) mkdirs() }
            val ext = src.extension.ifBlank { if (isVideo) "mp4" else "jpg" }
            val dst = File(dir, "reater_${System.currentTimeMillis()}.$ext")
            src.copyTo(dst, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(dst.absolutePath), null, null)
            true
        }
    } catch (_: Exception) {
        false
    }
}

private fun downloadToTemp(context: Context, url: String, isVideo: Boolean): File? {
    return try {
        val ext = when {
            url.contains(".mp4", ignoreCase = true) -> "mp4"
            url.contains(".mov", ignoreCase = true) -> "mov"
            url.contains(".webp", ignoreCase = true) -> "webp"
            url.contains(".png", ignoreCase = true) -> "png"
            url.contains(".gif", ignoreCase = true) -> "gif"
            isVideo -> "mp4"
            else -> "jpg"
        }
        val tmp = File(context.cacheDir, "reater_dl_${System.currentTimeMillis()}.$ext")
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body ?: return null
            tmp.outputStream().use { body.byteStream().copyTo(it) }
        }
        if (tmp.exists() && tmp.length() > 0) tmp else null
    } catch (_: Exception) {
        null
    }
}
