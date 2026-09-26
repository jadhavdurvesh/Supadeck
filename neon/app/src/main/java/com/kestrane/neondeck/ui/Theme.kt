package com.kestrane.neondeck.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NeonTeal = Color(0xFF00E5BE)

private val Colors = darkColorScheme(
    primary = NeonTeal,
    onPrimary = Color(0xFF00201A),
    secondaryContainer = Color(0xFF15332C),
    onSecondaryContainer = Color(0xFFB6F5E8),
    background = Color(0xFF0A0F14),
    onBackground = Color(0xFFE4ECEA),
    surface = Color(0xFF0A0F14),
    onSurface = Color(0xFFE4ECEA),
    surfaceVariant = Color(0xFF16211F),
    onSurfaceVariant = Color(0xFF9FB2AE),
    surfaceContainer = Color(0xFF10181B),
    surfaceContainerHigh = Color(0xFF16211F),
    surfaceContainerHighest = Color(0xFF1C2A27),
    outline = Color(0xFF34433F),
    outlineVariant = Color(0xFF22312D),
    error = Color(0xFFFF7A7A),
)

@Composable
fun NeonDeckTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
