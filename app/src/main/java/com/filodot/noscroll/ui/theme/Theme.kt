package com.filodot.noscroll.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF765B00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDF8E),
    background = Color(0xFFFFF9F0),
    surface = Color(0xFFFFF9F0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFEBC248),
    primaryContainer = Color(0xFF594400),
    background = Color(0xFF17130D),
    surface = Color(0xFF17130D),
)

@Composable
fun NoScrollTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
