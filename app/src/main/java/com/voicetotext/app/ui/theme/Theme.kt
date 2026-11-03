package com.voicetotext.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = AppSurfaceContainer,
    primaryContainer = PrimaryBlueContainer,
    onPrimaryContainer = OnPrimaryBlueContainer,
    secondary = StatusLive,
    onSecondary = AppSurfaceContainer,
    background = AppSurface,
    onBackground = TextPrimary,
    surface = AppSurface,
    onSurface = TextPrimary,
    surfaceVariant = AppSurfaceContainer,
    onSurfaceVariant = TextSecondary,
    outline = AppOutline
)

@Composable
fun VoiceToTextTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AppSurface.toArgb()
            window.navigationBarColor = AppSurface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
