package com.maisonchahyn.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF0A84FF)
val AccentPurple = Color(0xFFAF52DE)
val IosGreen = Color(0xFF34C759)
val IosOrange = Color(0xFFFF9F0A)
val IosPink = Color(0xFFFF375F)
val IosTeal = Color(0xFF64D2FF)
val IosYellow = Color(0xFFFFD60A)
val IosIndigo = Color(0xFF5E5CE6)

@Composable
fun MaisonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            secondary = AccentPurple,
            background = Color(0xFF0B1026),
            surface = Color(0xFF1C1C2E),
            surfaceContainer = Color(0xFF22223A),
            onSurface = Color.White,
            onBackground = Color.White,
            onPrimary = Color.White,
        ),
        content = content
    )
}
