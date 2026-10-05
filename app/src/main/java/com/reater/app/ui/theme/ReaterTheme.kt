package com.reater.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * App 主題：亮暗模式（跟隨系統 / 淺色 / 深色）+ 字體縮放（0.85–1.3）。
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
            LocalDensity provides Density(base.density, fontScale.coerceIn(0.85f, 1.3f))
        ) {
            content()
        }
    }
}
