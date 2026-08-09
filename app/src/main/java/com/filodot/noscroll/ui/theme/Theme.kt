package com.filodot.noscroll.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * "Calm focus" design tokens. Colors are deliberately warm and low-contrast in large surfaces;
 * interactive controls and errors retain WCAG-friendly contrast.
 */
object NoScrollColors {
    val WarmCanvas = Color(0xFFF7F3EA)
    val WarmSurface = Color(0xFFFFFDF8)
    val WarmSurfaceMuted = Color(0xFFEDE8DD)
    val Ink = Color(0xFF25221D)
    val InkMuted = Color(0xFF686258)
    val Amber = Color(0xFF936B00)
    val AmberContainer = Color(0xFFFFE3A0)
    val Sage = Color(0xFF5D7457)
    val SageContainer = Color(0xFFDCE8D7)
    val Outline = Color(0xFFD6CFC1)
}

private val LightColors = lightColorScheme(
    primary = NoScrollColors.Amber,
    onPrimary = Color.White,
    primaryContainer = NoScrollColors.AmberContainer,
    onPrimaryContainer = Color(0xFF2E2100),
    secondary = Color(0xFF696354),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE7D7),
    onSecondaryContainer = Color(0xFF252117),
    tertiary = NoScrollColors.Sage,
    onTertiary = Color.White,
    tertiaryContainer = NoScrollColors.SageContainer,
    onTertiaryContainer = Color(0xFF172313),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = NoScrollColors.WarmCanvas,
    onBackground = NoScrollColors.Ink,
    surface = NoScrollColors.WarmSurface,
    onSurface = NoScrollColors.Ink,
    surfaceVariant = NoScrollColors.WarmSurfaceMuted,
    onSurfaceVariant = NoScrollColors.InkMuted,
    outline = NoScrollColors.Outline,
    outlineVariant = Color(0xFFE7E0D3),
    scrim = Color(0x99000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF0C45A),
    onPrimary = Color(0xFF3D2F00),
    primaryContainer = Color(0xFF594600),
    onPrimaryContainer = Color(0xFFFFE3A0),
    secondary = Color(0xFFD1C7B1),
    onSecondary = Color(0xFF363126),
    secondaryContainer = Color(0xFF4D473A),
    onSecondaryContainer = Color(0xFFEDE7D7),
    tertiary = Color(0xFFB9CDB1),
    onTertiary = Color(0xFF253422),
    tertiaryContainer = Color(0xFF3B4B37),
    onTertiaryContainer = Color(0xFFDCE8D7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF171510),
    onBackground = Color(0xFFEAE4D9),
    surface = Color(0xFF201E19),
    onSurface = Color(0xFFEAE4D9),
    surfaceVariant = Color(0xFF49453D),
    onSurfaceVariant = Color(0xFFCBC5BA),
    outline = Color(0xFF958F84),
    outlineVariant = Color(0xFF49453D),
)

private val CalmTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 58.sp,
        letterSpacing = (-1).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 50.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)

private val CalmShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NoScrollTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CalmTypography,
        shapes = CalmShapes,
        content = content,
    )
}
