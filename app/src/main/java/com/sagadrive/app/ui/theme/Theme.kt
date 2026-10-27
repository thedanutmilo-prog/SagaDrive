package com.sagadrive.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object SagaColors {
    val Background = Color(0xFF0B1020)
    val Surface = Color(0xFF171E30)
    val Accent = Color(0xFF3D8BFF)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFAAB4C8)
}

@Composable
fun SagaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SagaColors.Accent, onPrimary = SagaColors.TextPrimary,
            background = SagaColors.Background, onBackground = SagaColors.TextPrimary,
            surface = SagaColors.Surface, onSurface = SagaColors.TextPrimary,
            surfaceVariant = SagaColors.Surface, onSurfaceVariant = SagaColors.TextSecondary,
        ),
        content = content,
    )
}
