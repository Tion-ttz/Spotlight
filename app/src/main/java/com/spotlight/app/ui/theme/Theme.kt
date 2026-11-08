package com.spotlight.app.ui.theme

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.spotlight.app.core.ThemeMode

/** Spotlight 的语义色板：搜索框、分组卡片、遮罩都由它决定。 */
data class SpotlightPalette(
    val isDark: Boolean,
    val scrimTop: Color,
    val scrimBottom: Color,
    val fieldBackground: Color,
    val fieldBorder: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val divider: Color,
    val pressedOverlay: Color,
    val badgeBackground: Color,
)

val DarkPalette = SpotlightPalette(
    isDark = true,
    // 半透明遮罩：桌面/锁屏壁纸会透出来，配合 Android 12+ 的背景模糊形成 iOS 毛玻璃效果
    scrimTop = Color(0xCC101014),
    scrimBottom = Color(0xB3101014),
    fieldBackground = Color(0x38FFFFFF),
    fieldBorder = Color(0x26FFFFFF),
    cardBackground = Color(0x24FFFFFF),
    cardBorder = Color(0x1AFFFFFF),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFC2C2CC),
    textTertiary = Color(0xFF9A9AA4),
    accent = Color(0xFF0A84FF),
    divider = Color(0x26FFFFFF),
    pressedOverlay = Color(0x26FFFFFF),
    badgeBackground = Color(0x2EFFFFFF),
)

val LightPalette = SpotlightPalette(
    isDark = false,
    scrimTop = Color(0xCCF2F2F7),
    scrimBottom = Color(0xB3F2F2F7),
    fieldBackground = Color(0x80FFFFFF),
    fieldBorder = Color(0x1F000000),
    cardBackground = Color(0x9EFFFFFF),
    cardBorder = Color(0x14000000),
    textPrimary = Color(0xFF111114),
    textSecondary = Color(0xFF54545E),
    textTertiary = Color(0xFF86868F),
    accent = Color(0xFF007AFF),
    divider = Color(0x1A000000),
    pressedOverlay = Color(0x12000000),
    badgeBackground = Color(0x1F000000),
)

val DarkPaletteBlurred = DarkPalette.copy(
    scrimTop = Color(0xA6101014),
    scrimBottom = Color(0x8C101014),
)

val LightPaletteBlurred = LightPalette.copy(
    scrimTop = Color(0xA6F2F2F7),
    scrimBottom = Color(0x8CF2F2F7),
)

val LocalSpotlightPalette = staticCompositionLocalOf { DarkPalette }

private val SpotlightTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

@Composable
fun SpotlightTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    // Android 12+ 且系统开启跨窗口模糊时，遮罩可以更透一些，让毛玻璃效果更明显
    val context = LocalContext.current
    val blurEnabled = remember(context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            false
        } else {
            runCatching {
                val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                manager?.isCrossWindowBlurEnabled == true
            }.getOrDefault(false)
        }
    }
    val palette = when {
        dark && blurEnabled -> DarkPaletteBlurred
        dark -> DarkPalette
        blurEnabled -> LightPaletteBlurred
        else -> LightPalette
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = Color(0xFF101014),
            onBackground = palette.textPrimary,
            surface = Color(0xFF1A1A1F),
            onSurface = palette.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = Color(0xFFF2F2F7),
            onBackground = palette.textPrimary,
            surface = Color.White,
            onSurface = palette.textPrimary,
        )
    }
    CompositionLocalProvider(LocalSpotlightPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = SpotlightTypography,
            content = content,
        )
    }
}
