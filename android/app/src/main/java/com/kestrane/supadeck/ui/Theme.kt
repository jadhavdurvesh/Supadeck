package com.kestrane.supadeck.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green = Color(0xFF3ECF8E)

private val Colors = darkColorScheme(
    primary = Green,
    onPrimary = Color(0xFF002114),
    secondaryContainer = Color(0xFF1B3A2C),
    onSecondaryContainer = Color(0xFFBDF5D8),
    background = Color(0xFF0E1512),
    onBackground = Color(0xFFE4ECE7),
    surface = Color(0xFF0E1512),
    onSurface = Color(0xFFE4ECE7),
    surfaceVariant = Color(0xFF1B2620),
    onSurfaceVariant = Color(0xFF9FB2A8),
    surfaceContainer = Color(0xFF151E19),
    surfaceContainerHigh = Color(0xFF1B2620),
    surfaceContainerHighest = Color(0xFF222E28),
    outline = Color(0xFF3A4A41),
    outlineVariant = Color(0xFF26332C),
    error = Color(0xFFFF7A7A),
)

@Composable
fun SupaDeckTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
