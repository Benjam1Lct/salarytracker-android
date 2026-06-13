package com.benjamin.salarytracker.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun SalaryTrackerTheme(
    accentColor: Color = Purple,
    content: @Composable () -> Unit
) {
    val dynamicBg = when (accentColor) {
        Color(0xFF3B82F6) -> Color(0xFFF0F6FF) // blue
        Color(0xFF10B981) -> Color(0xFFF0DFDF).let { Color(0xFFF0FDF4) } // green
        Color(0xFFF59E0B) -> Color(0xFFFFFBEB) // orange
        Color(0xFFEF4444) -> Color(0xFFFEF2F2) // red
        Color(0xFFEC4899) -> Color(0xFFFDF2F8) // pink
        else -> Color(0xFFF2EEFB) // purple
    }

    val dynamicBgAlt = when (accentColor) {
        Color(0xFF3B82F6) -> Color(0xFFE0EDFF) // blue
        Color(0xFF10B981) -> Color(0xFFDCFCE7) // green
        Color(0xFFF59E0B) -> Color(0xFFFEF3C7) // orange
        Color(0xFFEF4444) -> Color(0xFFFEE2E2) // red
        Color(0xFFEC4899) -> Color(0xFFFCE7F3) // pink
        else -> Color(0xFFEAE3F8) // purple
    }

    val colors = lightColorScheme(
        primary = accentColor,
        onPrimary = Color.White,
        primaryContainer = accentColor.copy(alpha = 0.1f),
        onPrimaryContainer = accentColor,
        secondary = accentColor.copy(alpha = 0.8f),
        onSecondary = Color.White,
        secondaryContainer = accentColor.copy(alpha = 0.15f),
        onSecondaryContainer = accentColor,
        tertiary = accentColor.copy(alpha = 0.6f),
        onTertiary = Color.White,
        background = dynamicBg,
        onBackground = Ink,
        surface = CardWhite,
        onSurface = Ink,
        outline = OutlineLt,
        surfaceVariant = dynamicBgAlt,
        onSurfaceVariant = InkMuted
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
