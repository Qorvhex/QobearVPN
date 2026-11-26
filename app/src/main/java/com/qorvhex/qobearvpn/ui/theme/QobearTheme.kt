package com.qorvhex.qobearvpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.qorvhex.qobearvpn.R
import com.qorvhex.qobearvpn.model.AppLanguage
import com.qorvhex.qobearvpn.model.AppThemeMode

@Immutable
data class NothingPalette(
    val isDark: Boolean,
    val bgCanvas: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val borderHairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val nothingRed: Color,
    val onAccent: Color,
    val statusConnected: Color,
    val statusWarning: Color,
    val disabledSurface: Color,
    val disabledText: Color,
    val dotInactive: Color,
    val dotActive: Color,
)

private val DarkPalette = NothingPalette(
    isDark = true,
    bgCanvas = Color(0xFF000000),
    surfaceCard = Color(0xFF0E0E10),
    surfaceElevated = Color(0xFF18181B),
    borderHairline = Color(0xFF27272B),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFFA1A1AA),
    nothingRed = Color(0xFFD71921),
    onAccent = Color(0xFFFFFFFF),
    statusConnected = Color(0xFF22C55E),
    statusWarning = Color(0xFFF59E0B),
    disabledSurface = Color(0xFF1F1F23),
    disabledText = Color(0xFF71717A),
    dotInactive = Color(0xFF27272C),
    dotActive = Color(0xFFF5F5F7),
)

private val LightPalette = NothingPalette(
    isDark = false,
    bgCanvas = Color(0xFFF4F4F6),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEBEBEF),
    borderHairline = Color(0xFFDCDCE2),
    textPrimary = Color(0xFF111113),
    textSecondary = Color(0xFF52525B),
    nothingRed = Color(0xFFC8141C),
    onAccent = Color(0xFFFFFFFF),
    statusConnected = Color(0xFF16A34A),
    statusWarning = Color(0xFFD97706),
    disabledSurface = Color(0xFFE4E4E7),
    disabledText = Color(0xFF71717A),
    dotInactive = Color(0xFFD4D4D8),
    dotActive = Color(0xFF111113),
)

val LocalNothingPalette = staticCompositionLocalOf { DarkPalette }

val VazirmatnFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

val DotMatrixFamily = FontFamily(
    Font(R.font.dotgothic16_regular, FontWeight.Normal),
)

val SpaceMonoFamily = FontFamily(
    Font(R.font.spacemono_regular, FontWeight.Normal),
    Font(R.font.spacemono_bold, FontWeight.Bold),
)

private fun qobearTypography() = Typography(
    displayLarge = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = VazirmatnFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    ),
)

@Composable
fun QobearTheme(
    themeMode: AppThemeMode,
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkPalette else LightPalette
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = palette.nothingRed,
            onPrimary = palette.onAccent,
            background = palette.bgCanvas,
            onBackground = palette.textPrimary,
            surface = palette.surfaceCard,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.borderHairline,
        )
    } else {
        lightColorScheme(
            primary = palette.nothingRed,
            onPrimary = palette.onAccent,
            background = palette.bgCanvas,
            onBackground = palette.textPrimary,
            surface = palette.surfaceCard,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.borderHairline,
        )
    }

    val direction = if (language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalNothingPalette provides palette,
        LocalLayoutDirection provides direction,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = qobearTypography(),
        ) {
            ProvideTextStyle(value = TextStyle(fontFamily = VazirmatnFamily), content = content)
        }
    }
}
