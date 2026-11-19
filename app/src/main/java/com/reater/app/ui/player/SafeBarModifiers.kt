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
import android.view.ViewGroup
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
import com.reater.app.R
import com.reater.app.notify.NotifyCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

private fun findDialogLayoutView(view: View?, decorView: View?): View? {
    var cur: Any? = view
    while (cur is View) {
        if (cur.javaClass.name == "androidx.compose.ui.window.DialogLayout") return cur
        cur = cur.parent
    }
    fun searchDown(v: View?): View? {
        if (v == null) return null
        if (v.javaClass.name == "androidx.compose.ui.window.DialogLayout") return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                searchDown(v.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
    return searchDown(decorView)
}

/**
 * 將 Dialog Window 設置為全螢幕沉浸式（透明系統列、延伸至瀏海區域）。
 */
@Suppress("DEPRECATION")
fun applyDialogWindowImmersive(window: Window?, view: View? = null) {
    window?.let { win ->
        WindowCompat.setDecorFitsSystemWindows(win, false)
        val decor = win.decorView
        if (decor.paddingLeft != 0 || decor.paddingTop != 0 || decor.paddingRight != 0 || decor.paddingBottom != 0) {
            decor.setPadding(0, 0, 0, 0)
        }
        // Compose DialogLayout 在 usePlatformDefaultWidth = false 時會用 screenHeightDp（扣除系統列）
        // 測量並覆寫 window.setLayout，造成系統列出現時視窗整體下移且底部被裁切。
        // 將其改為 true 使 DialogLayout 採用 WindowManager 給予的 MATCH_PARENT 全螢幕尺寸。
        findDialogLayoutView(view, decor)?.let { dialogLayout ->
            runCatching {
                val field = dialogLayout.javaClass.getDeclaredField("usePlatformDefaultWidth").apply {
                    isAccessible = true
                }
                if (!field.getBoolean(dialogLayout)) {
                    val setter = runCatching {
                        dialogLayout.javaClass.getMethod("setUsePlatformDefaultWidth", Boolean::class.javaPrimitiveType)
                    }.getOrNull()
                    if (setter != null) {
                        setter.invoke(dialogLayout, true)
                    } else {
                        field.setBoolean(dialogLayout, true)
                    }
                    dialogLayout.requestLayout()
                }
            }
        }
        win.addFlags(
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
        )
        win.statusBarColor = android.graphics.Color.TRANSPARENT
        win.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            win.isNavigationBarContrastEnforced = false
            win.isStatusBarContrastEnforced = false
        }
        val lp = win.attributes
        var attrsChanged = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            lp.layoutInDisplayCutoutMode != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        ) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            attrsChanged = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (lp.fitInsetsTypes != 0 || lp.fitInsetsSides != 0) {
                lp.fitInsetsTypes = 0
                lp.fitInsetsSides = 0
                attrsChanged = true
            }
        }
        if (attrsChanged) {
            win.attributes = lp
        }
        if (win.attributes.width != WindowManager.LayoutParams.MATCH_PARENT ||
            win.attributes.height != WindowManager.LayoutParams.MATCH_PARENT
        ) {
            win.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }
    }
}

/** 隱藏控制列時讓系統列一同進入沉浸模式，邊緣滑動仍可暫時叫出。僅操作 Dialog 視窗（無 Dialog 時才退回 Activity）。 */
fun setFullscreenSystemBarsVisible(window: Window?, visible: Boolean, activity: Activity? = null) {
    val targetWin = window ?: activity?.window ?: return
    WindowCompat.getInsetsController(targetWin, targetWin.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) show(WindowInsetsCompat.Type.systemBars())
        else hide(WindowInsetsCompat.Type.systemBars())
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
    val resNavHeightPx = remember(view, configuration.orientation) { getSystemNavigationBarHeightPx(view.context) }
    val resNavWidthPx = remember(view, configuration.orientation) { getSystemNavigationBarWidthPx(view.context) }
    val resStatusHeightPx = remember(view, configuration.orientation) { getSystemStatusBarHeightPx(view.context) }

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

        return MediaViewerSafeInsets(left = leftDp, top = topDp, right = rightDp, bottom = bottomDp)
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
        cCutoutTopPx,
        cSafeDrawingBottomPx,
        cSafeDrawingRightPx,
        cSafeDrawingLeftPx,
        cSafeDrawingTopPx,
        cSystemBarsTopPx,
        cSystemBarsBottomPx
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
        top = safeInsets.top + 8.dp,
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
        bottom = safeInsets.bottom + 16.dp
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
    remoteUrl: String,
    /** 純音訊（無視軌的 mp4）：存成 .m4a 進音樂資料庫，避免以影片身份在相簿顯示全黑 */
    isAudio: Boolean = false
) {
    val localFile = if (localPath.isNotBlank()) File(localPath).takeIf { it.exists() && it.length() > 0 } else null

    if (localFile == null && remoteUrl.isBlank()) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.save_error_no_media), Toast.LENGTH_SHORT).show()
        }
        return
    }

    if (localFile != null) {
        val ok = withContext(Dispatchers.IO) {
            copyFileToGallery(context, localFile, isVideo, isAudio)
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, if (ok) (if (isAudio) context.getString(R.string.save_done_music) else context.getString(R.string.save_done_gallery)) else context.getString(R.string.save_error), Toast.LENGTH_SHORT).show()
        }
        return
    }

    // 需由遠端網址下載
    withContext(Dispatchers.Main) {
        val what = when {
            isAudio -> context.getString(R.string.save_type_audio)
            isVideo -> context.getString(R.string.save_type_video)
            else -> context.getString(R.string.save_type_image)
        }
        Toast.makeText(context, context.getString(R.string.save_downloading_what, what), Toast.LENGTH_SHORT).show()
    }

    val ok = withContext(Dispatchers.IO) {
        val downloaded = downloadToTemp(context, remoteUrl, isVideo, isAudio) ?: return@withContext false
        try {
            copyFileToGallery(context, downloaded, isVideo, isAudio)
        } finally {
            runCatching { downloaded.delete() }
        }
    }

    withContext(Dispatchers.Main) {
        Toast.makeText(context, if (ok) (if (isAudio) context.getString(R.string.save_done_music) else context.getString(R.string.save_done_gallery)) else context.getString(R.string.save_error_network), Toast.LENGTH_SHORT).show()
    }
}

/**
 * 將音訊貼文匯出成「音樂版 .m4a」：純音訊 + ilst metadata（©nam 曲名/©ART 歌手/
 * ©lyr 歌詞/covr 封面），內部先寫暫存檔再進音樂資料庫。
 */
suspend fun exportAudioAsTaggedMusicWithFeedback(
    context: Context,
    localPath: String,
    remoteUrl: String,
    posterUrl: String,
    audioMetaJson: String,
    refreshSources: (suspend () -> com.reater.app.data.repository.ThreadPostRepository.RefreshedAudioSources?)? = null
): Boolean {
    var audioUrl = remoteUrl
    var coverUrl = posterUrl
    var metaJson = audioMetaJson
    var audioFile = resolveAudioSourceFile(context, localPath, audioUrl)
    if (audioFile == null && refreshSources != null) {
        // 網址過期/無本機檔：重抓貼文換新網址與新 metadata 後重試
        refreshSources()?.let { fresh ->
            audioUrl = fresh.audioUrl
            coverUrl = fresh.posterUrl
            metaJson = fresh.audioMetaJson
            audioFile = resolveAudioSourceFile(context, localPath = "", remoteUrl = audioUrl)
        }
    }
    if (audioFile == null) {
        withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.save_error_audio_unavailable), Toast.LENGTH_SHORT).show() }
        return false
    }
    val ok = withContext(Dispatchers.IO) {
        runCatching {
            val meta = com.reater.app.domain.AudioMetaStore.decode(metaJson)
            val coverBitmap = kotlinx.coroutines.withTimeout(30_000L) { loadCoverBitmap(context, coverUrl) }
            val coverJpeg = coverBitmap?.let { bm ->
                try {
                    java.io.ByteArrayOutputStream(256 * 1024).use { bos ->
                        bm.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, bos)
                        bos.toByteArray()
                    }
                } finally {
                    runCatching { bm.recycle() }
                }
            }
            val tagged = File(context.cacheDir, "reater_tag_${System.currentTimeMillis()}.m4a")
            val embedOk = Mp4TagWriter.embed(
                audioFile, tagged,
                Mp4TagWriter.Tags(
                    title = meta?.title.orEmpty(),
                    artist = meta?.artist.orEmpty(),
                    lyrics = meta?.let { com.reater.app.domain.AudioMetaStore.lyricsText(it) }.orEmpty(),
                    coverJpeg = coverJpeg
                )
            )
            if (!embedOk) {
                // 不支援的結構：回退乾淨 .m4a（無 metadata）
                copyFileToGallery(context, audioFile, isVideo = false, isAudio = true)
            } else {
                try {
                    copyFileToGallery(context, tagged, isVideo = false, isAudio = true)
                } finally {
                    runCatching { tagged.delete() }
                }
            }
        }.getOrDefault(false)
    }
    withContext(Dispatchers.Main) {
        Toast.makeText(
            context,
            if (ok) context.getString(R.string.save_done_music_with_meta) else context.getString(R.string.save_error_audio_check),
            Toast.LENGTH_SHORT
        ).show()
    }
    return ok
}

private val backgroundExportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val backgroundExportSeq = AtomicInteger(0)

/**
 * 背景執行音訊匯出（「音樂版 .m4a」或「影片版 .mp4」）：
 * 立即推送「執行中」系統通知與 Toast 提示，不阻斷使用者關閉檢視器或滑動檢閱其他內容。
 */
fun startBackgroundAudioExport(
    context: Context,
    isVideo: Boolean,
    localPath: String,
    remoteUrl: String,
    posterUrl: String,
    audioMetaJson: String = "",
    refreshSources: (suspend () -> com.reater.app.data.repository.ThreadPostRepository.RefreshedAudioSources?)? = null
) {
    val appContext = context.applicationContext
    val notifId = NotifyCenter.EXPORT_NOTIF_BASE + (backgroundExportSeq.getAndIncrement() % 1000)
    Toast.makeText(
        appContext,
        appContext.getString(R.string.video_export_background_started),
        Toast.LENGTH_SHORT
    ).show()
    NotifyCenter.postMediaExportRunning(appContext, notifId, isVideo)

    backgroundExportScope.launch {
        var success = false
        var message = ""
        try {
            if (isVideo) {
                AudioVideoComposer.clearFailure()
                success = exportAudioAsCoverVideoWithFeedback(
                    context = appContext,
                    localPath = localPath,
                    remoteUrl = remoteUrl,
                    posterUrl = posterUrl,
                    audioMetaJson = audioMetaJson,
                    refreshSources = refreshSources
                )
                message = when {
                    success -> appContext.getString(R.string.save_done_gallery_with_cover)
                    AudioVideoComposer.lastFailure.isNotBlank() -> appContext.getString(
                        R.string.save_error_compose_failed_reason,
                        AudioVideoComposer.lastFailure
                    )
                    else -> appContext.getString(R.string.save_error_compose_failed)
                }
            } else {
                success = exportAudioAsTaggedMusicWithFeedback(
                    context = appContext,
                    localPath = localPath,
                    remoteUrl = remoteUrl,
                    posterUrl = posterUrl,
                    audioMetaJson = audioMetaJson,
                    refreshSources = refreshSources
                )
                message = if (success) {
                    appContext.getString(R.string.save_done_music_with_meta)
                } else {
                    appContext.getString(R.string.save_error_audio_check)
                }
            }
        } catch (t: Throwable) {
            success = false
            message = t.message?.takeIf { it.isNotBlank() }
                ?: appContext.getString(
                    if (isVideo) R.string.save_error_compose_failed else R.string.save_error_audio_check
                )
        } finally {
            NotifyCenter.postMediaExportComplete(
                context = appContext,
                notifId = notifId,
                isVideo = isVideo,
                success = success,
                message = message.ifBlank {
                    appContext.getString(
                        if (isVideo) R.string.save_error_compose_failed else R.string.save_error_audio_check
                    )
                }
            )
        }
    }
}

/**
 * 將音訊貼文匯出成「影片版 .mp4」：封面圖合成為視訊軌 + 依歌詞時間軸疊上同步字幕
 * （文字自動換行，不超出畫面寬度），音訊重編碼為 AAC-LC（各裝置必解），
 * 進相簿可直接播放/分享。
 *
 * fbcdn 網址具時效性：本機無檔且下載失敗、或封面抓不到時，以 [refreshSources]
 * 重抓貼文換新網址/新歌詞後重試一次（詳情頁提供；null=不重抓）。
 */
suspend fun exportAudioAsCoverVideoWithFeedback(
    context: Context,
    localPath: String,
    remoteUrl: String,
    posterUrl: String,
    audioMetaJson: String = "",
    refreshSources: (suspend () -> com.reater.app.data.repository.ThreadPostRepository.RefreshedAudioSources?)? = null
): Boolean {
    AudioVideoComposer.clearFailure()
    var audioUrl = remoteUrl
    var coverUrl = posterUrl
    var metaJson = audioMetaJson
    var refreshed = false
    var audioFile = resolveAudioSourceFile(context, localPath, audioUrl)
    if (audioFile == null && refreshSources != null && !refreshed) {
        refreshed = true
        val fresh = refreshSources()
        if (fresh != null) {
            audioUrl = fresh.audioUrl
            coverUrl = fresh.posterUrl
            metaJson = fresh.audioMetaJson
            audioFile = resolveAudioSourceFile(context, localPath = "", remoteUrl = audioUrl)
        }
    }
    if (audioFile == null) {
        withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.save_error_audio_unavailable), Toast.LENGTH_SHORT).show() }
        return false
    }
    val ok = withContext(Dispatchers.IO) {
        runCatching {
            if (metaJson.isBlank() && refreshSources != null && !refreshed) {
                refreshed = true
                refreshSources()?.let { fresh ->
                    if (fresh.audioMetaJson.isNotBlank()) metaJson = fresh.audioMetaJson
                    if (coverUrl.isBlank() && fresh.posterUrl.isNotBlank()) coverUrl = fresh.posterUrl
                }
            }
            var cover = kotlinx.coroutines.withTimeout(30_000L) { loadCoverBitmap(context, coverUrl) }
            if (cover == null && refreshSources != null && !refreshed) {
                refreshed = true
                val fresh = refreshSources()
                if (fresh != null) {
                    coverUrl = fresh.posterUrl
                    if (fresh.audioMetaJson.isNotBlank()) metaJson = fresh.audioMetaJson
                    cover = kotlinx.coroutines.withTimeout(30_000L) { loadCoverBitmap(context, coverUrl) }
                }
            }
            if (cover == null) throw IllegalStateException("no cover")
            val lyrics = com.reater.app.domain.AudioMetaStore.decode(metaJson)
                ?.lyrics
                ?.map { AudioVideoComposer.Cue(it.text, it.startMs, it.endMs) }
                .orEmpty()
            try {
                val out = File(context.cacheDir, "reater_av_${System.currentTimeMillis()}.mp4")
                val composed = AudioVideoComposer.compose(audioFile, cover, out, lyrics)
                if (!composed) false else {
                    try {
                        copyFileToGallery(context, out, isVideo = true)
                    } finally {
                        runCatching { out.delete() }
                    }
                }
            } finally {
                runCatching { cover.recycle() }
            }
        }.getOrDefault(false)
    }
    withContext(Dispatchers.Main) {
        Toast.makeText(
            context,
            when {
                ok -> context.getString(R.string.save_done_gallery_with_cover)
                AudioVideoComposer.lastFailure.isNotBlank() -> context.getString(
                    R.string.save_error_compose_failed_reason,
                    AudioVideoComposer.lastFailure
                )
                else -> context.getString(R.string.save_error_compose_failed)
            },
            Toast.LENGTH_LONG
        ).show()
    }
    return ok
}

/** 音訊來源：本機檔優先；無本機檔時由遠端下載至暫存 */
private suspend fun resolveAudioSourceFile(context: Context, localPath: String, remoteUrl: String): File? {
    val local = localPath.takeIf { it.isNotBlank() }
        ?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
    if (local != null) return local
    if (remoteUrl.isBlank()) return null
    return withContext(Dispatchers.IO) { downloadToTemp(context, remoteUrl, isVideo = false, isAudio = true) }
}

/**
 * 載入封面位圖：http(s) 網址 → 下載至暫存再解碼；本機路徑直接解碼。
 * 失敗（含無網路）回 null。
 */
private suspend fun loadCoverBitmap(context: Context, posterUrl: String): android.graphics.Bitmap? {
    if (posterUrl.isBlank()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            if (posterUrl.startsWith("http")) {
                val f = downloadToTemp(context, posterUrl, isVideo = false, isAudio = false)
                    ?: return@runCatching null
                try {
                    android.graphics.BitmapFactory.decodeFile(f.absolutePath)
                } finally {
                    runCatching { f.delete() }
                }
            } else {
                val f = File(posterUrl)
                if (f.exists() && f.length() > 0) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
            }
        }.getOrNull()
    }
}

fun copyFileToGallery(context: Context, src: File, isVideo: Boolean, isAudio: Boolean = false): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val ext = when {
                src.extension.isBlank() -> if (isAudio) "m4a" else if (isVideo) "mp4" else "jpg"
                // 音訊來源實為 mp4 容器：以 .m4a 存放，音樂 App 才能正確辨識
                isAudio && src.extension.equals("mp4", ignoreCase = true) -> "m4a"
                else -> src.extension
            }
            val mime = if (isAudio) {
                when (ext.lowercase()) {
                    "mp3" -> "audio/mpeg"
                    "aac" -> "audio/aac"
                    "ogg" -> "audio/ogg"
                    else -> "audio/mp4"
                }
            } else if (isVideo) {
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
            val relativeDir = when {
                isAudio -> "${Environment.DIRECTORY_MUSIC}/Reater"
                isVideo -> "${Environment.DIRECTORY_MOVIES}/Reater"
                else -> "${Environment.DIRECTORY_PICTURES}/Reater"
            }
            val contentUri = when {
                isAudio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                isVideo -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

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
                when {
                    isAudio -> Environment.DIRECTORY_MUSIC
                    isVideo -> Environment.DIRECTORY_MOVIES
                    else -> Environment.DIRECTORY_PICTURES
                }
            )
            val dir = File(pubDir, "Reater").apply { if (!exists()) mkdirs() }
            val ext = when {
                src.extension.isBlank() -> if (isAudio) "m4a" else if (isVideo) "mp4" else "jpg"
                isAudio && src.extension.equals("mp4", ignoreCase = true) -> "m4a"
                else -> src.extension
            }
            val dst = File(dir, "reater_${System.currentTimeMillis()}.$ext")
            src.copyTo(dst, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(dst.absolutePath), null, null)
            true
        }
    } catch (_: Exception) {
        false
    }
}

private fun downloadToTemp(context: Context, url: String, isVideo: Boolean, isAudio: Boolean = false): File? {    return try {
        val ext = when {
            url.contains(".mp4", ignoreCase = true) -> if (isAudio) "m4a" else "mp4"
            url.contains(".mov", ignoreCase = true) -> "mov"
            url.contains(".webp", ignoreCase = true) -> "webp"
            url.contains(".png", ignoreCase = true) -> "png"
            url.contains(".gif", ignoreCase = true) -> "gif"
            isAudio -> "m4a"
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
