package com.reater.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Reater 全域語意色彩 Token：
 * - 淺色模式：暖銅金 × 米白紙感
 * - 深色模式：霧鋼藍 × 冷調深灰
 */
object ReaterPalette {
    // 淺色模式（Light Theme）
    val LightAppBackground = Color(0xFFF6F3EE)
    val LightSurface = Color(0xFFFFFCF8)
    val LightSurfaceVariant = Color(0xFFEFE9E0)
    val LightBrandGold = Color(0xFFD2AD7B) // 裝飾性品牌金色
    val LightPrimary = Color(0xFF8B5D34)   // 主要操作色 / 按鈕
    val LightOnPrimary = Color(0xFFFFFFFF) // 主要按鈕文字
    val LightOnSurface = Color(0xFF29241F) // 主要文字 / 標題 / 內文
    val LightOnSurfaceVariant = Color(0xFF655F58) // 次要文字 / 日期 / 來源
    val LightTagSelectedBg = Color(0xFFEFE0CC) // 標籤選取背景
    val LightTagSelectedText = Color(0xFF76502E) // 標籤選取文字
    val LightOutline = Color(0xFFDDD4C8)   // 邊框 / 分隔線
    val LightError = Color(0xFFBA1A1A)
    val LightOnError = Color(0xFFFFFFFF)

    // 深色模式（Dark Theme）
    val DarkAppBackground = Color(0xFF151A20)
    val DarkSurface = Color(0xFF252D35)
    val DarkSurfaceVariant = Color(0xFF303A44)
    val DarkBrandSteelBlue = Color(0xFF9BAFB9) // 品牌主色 / 霧鋼藍 / 主要操作色
    val DarkOnPrimary = Color(0xFF151A20)      // 主要按鈕文字
    val DarkOnSurface = Color(0xFFE4EBEF)      // 主要文字 / 標題 / 內文
    val DarkOnSurfaceVariant = Color(0xFFB2C0CA) // 次要文字 / 日期 / 來源
    val DarkTagSelectedBg = Color(0xFF344650)  // 標籤選取背景
    val DarkTagSelectedText = Color(0xFFDAE8EE) // 標籤選取文字
    val DarkOutline = Color(0xFF414C56)        // 邊框 / 分隔線
    val DarkError = Color(0xFFFFB4AB)
    val DarkOnError = Color(0xFF690005)
}

val LightColorScheme: ColorScheme = lightColorScheme(
    primary = ReaterPalette.LightPrimary,
    onPrimary = ReaterPalette.LightOnPrimary,
    primaryContainer = ReaterPalette.LightTagSelectedBg,
    onPrimaryContainer = ReaterPalette.LightTagSelectedText,
    secondary = ReaterPalette.LightPrimary,
    onSecondary = ReaterPalette.LightOnPrimary,
    secondaryContainer = ReaterPalette.LightTagSelectedBg,
    onSecondaryContainer = ReaterPalette.LightTagSelectedText,
    tertiary = ReaterPalette.LightBrandGold,
    onTertiary = ReaterPalette.LightOnSurface,
    tertiaryContainer = Color(0xFFF3E7D3),
    onTertiaryContainer = Color(0xFF433013),
    background = ReaterPalette.LightAppBackground,
    onBackground = ReaterPalette.LightOnSurface,
    surface = ReaterPalette.LightSurface,
    onSurface = ReaterPalette.LightOnSurface,
    surfaceVariant = ReaterPalette.LightSurfaceVariant,
    onSurfaceVariant = ReaterPalette.LightOnSurfaceVariant,
    outline = ReaterPalette.LightOutline,
    outlineVariant = ReaterPalette.LightOutline,
    error = ReaterPalette.LightError,
    onError = ReaterPalette.LightOnError,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = ReaterPalette.LightOnSurface,
    inverseOnSurface = ReaterPalette.LightAppBackground,
    inversePrimary = ReaterPalette.LightBrandGold
)

val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = ReaterPalette.DarkBrandSteelBlue,
    onPrimary = ReaterPalette.DarkOnPrimary,
    primaryContainer = ReaterPalette.DarkTagSelectedBg,
    onPrimaryContainer = ReaterPalette.DarkTagSelectedText,
    secondary = ReaterPalette.DarkBrandSteelBlue,
    onSecondary = ReaterPalette.DarkOnPrimary,
    secondaryContainer = ReaterPalette.DarkTagSelectedBg,
    onSecondaryContainer = ReaterPalette.DarkTagSelectedText,
    tertiary = ReaterPalette.DarkBrandSteelBlue,
    onTertiary = ReaterPalette.DarkOnPrimary,
    tertiaryContainer = ReaterPalette.DarkSurfaceVariant,
    onTertiaryContainer = ReaterPalette.DarkTagSelectedText,
    background = ReaterPalette.DarkAppBackground,
    onBackground = ReaterPalette.DarkOnSurface,
    surface = ReaterPalette.DarkSurface,
    onSurface = ReaterPalette.DarkOnSurface,
    surfaceVariant = ReaterPalette.DarkSurfaceVariant,
    onSurfaceVariant = ReaterPalette.DarkOnSurfaceVariant,
    outline = ReaterPalette.DarkOutline,
    outlineVariant = ReaterPalette.DarkOutline,
    error = ReaterPalette.DarkError,
    onError = ReaterPalette.DarkOnError,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = ReaterPalette.DarkOnSurface,
    inverseOnSurface = ReaterPalette.DarkAppBackground,
    inversePrimary = ReaterPalette.LightPrimary
)

@Immutable
data class ReaterExtendedColors(
    val brandGold: Color,
    val isDark: Boolean
)

val LocalReaterExtendedColors = staticCompositionLocalOf {
    ReaterExtendedColors(
        brandGold = ReaterPalette.LightBrandGold,
        isDark = false
    )
}

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
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = ReaterExtendedColors(
        brandGold = if (darkTheme) Color(0xFFD2AD7B) else ReaterPalette.LightBrandGold,
        isDark = darkTheme
    )

    CompositionLocalProvider(LocalReaterExtendedColors provides extendedColors) {
        MaterialTheme(colorScheme = colorScheme) {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale.coerceIn(0.85f, 1.3f))
            ) {
                content()
            }
        }
    }
}
