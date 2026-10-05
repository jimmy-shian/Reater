package com.reater.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * App 主題：亮暗模式（跟隨系統 / 淺色 / 深色）+ 字體縮放（0.85–1.8）。
 * 包在 setContent 最外層即可全 App 生效。
 */
@Composable
fun ReaterTheme(
    themeMode: String = "system",
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(base.density, fontScale.coerceIn(0.85f, 1.8f))
        ) {
            content()
        }
    }
}

/**
 * 封裝 Compose 原生 Dialog：
 * Compose 的 Dialog 底層為獨立 DialogWindow，不會繼承外層 CompositionLocalProvider 中的 LocalDensity。
 * 此處捕獲外部 LocalDensity (含已套用之 fontScale) 並重新注入到 Dialog 內容中，
 * 確保彈窗內所有文字均能正確跟隨「字體大小 (比例)」設定進行等比縮放。
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    val currentDensity = LocalDensity.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        CompositionLocalProvider(
            LocalDensity provides currentDensity
        ) {
            content()
        }
    }
}

/**
 * 封裝 Compose 原生 AlertDialog：
 * 同樣確保確認對話框內之標題、內文與按鈕均能繼承 LocalDensity 之縮放比例。
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = androidx.compose.material3.AlertDialogDefaults.shape,
    containerColor: Color = androidx.compose.material3.AlertDialogDefaults.containerColor,
    iconContentColor: Color = androidx.compose.material3.AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = androidx.compose.material3.AlertDialogDefaults.titleContentColor,
    textContentColor: Color = androidx.compose.material3.AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = androidx.compose.material3.AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    val currentDensity = LocalDensity.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            CompositionLocalProvider(LocalDensity provides currentDensity) {
                confirmButton()
            }
        },
        modifier = modifier,
        dismissButton = dismissButton?.let {
            {
                CompositionLocalProvider(LocalDensity provides currentDensity) {
                    it()
                }
            }
        },
        icon = icon?.let {
            {
                CompositionLocalProvider(LocalDensity provides currentDensity) {
                    it()
                }
            }
        },
        title = title?.let {
            {
                CompositionLocalProvider(LocalDensity provides currentDensity) {
                    it()
                }
            }
        },
        text = text?.let {
            {
                CompositionLocalProvider(LocalDensity provides currentDensity) {
                    it()
                }
            }
        },
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties
    )
}
