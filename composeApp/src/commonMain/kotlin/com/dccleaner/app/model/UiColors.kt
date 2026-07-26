package com.dccleaner.app.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class UiColors(
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val card: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val textSecondary: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val headerStart: Color,
    val headerEnd: Color
)

fun dccleanerUiColors(darkTheme: Boolean): UiColors =
    if (darkTheme) {
        UiColors(
            primary = Color(0xFF7CB7F5),
            secondary = Color(0xFF4DB6AC),
            background = Color(0xFF121316),
            card = Color(0xFF1E1F24),
            surfaceVariant = Color(0xFF292B31),
            outline = Color(0xFF333741),
            textSecondary = Color(0xFFAEB4BC),
            success = Color(0xFF66BB6A),
            warning = Color(0xFFFFB74D),
            danger = Color(0xFFFF7043),
            headerStart = Color(0xFF2563EB),
            headerEnd = Color(0xFF0F766E)
        )
    } else {
        UiColors(
            primary = Color(0xFF2196F3),
            secondary = Color(0xFF03DAC6),
            background = Color(0xFFF5F5F5),
            card = Color.White,
            surfaceVariant = Color(0xFFF5F5F5),
            outline = Color(0xFFDADCE0),
            textSecondary = Color(0xFF6F737A),
            success = Color(0xFF4CAF50),
            warning = Color(0xFFFF9800),
            danger = Color(0xFFFF6B6B),
            headerStart = Color(0xFF2196F3),
            headerEnd = Color(0xFF03DAC6)
        )
    }
