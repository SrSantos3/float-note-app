package com.antigravity.floatingnotes.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryIndigo,
    onPrimary = TextPrimary,
    primaryContainer = PrimaryDark,
    secondary = SecondaryPink,
    tertiary = CyanAccent,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceElevated,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = DarkBorder
)

@Composable
fun FloatingNotesTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            try {
                var context = view.context
                while (context is android.content.ContextWrapper) {
                    if (context is Activity) break
                    context = context.baseContext
                }
                val window = (context as? Activity)?.window
                if (window != null) {
                    window.statusBarColor = DarkBackground.toArgb()
                    window.navigationBarColor = DarkBackground.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
