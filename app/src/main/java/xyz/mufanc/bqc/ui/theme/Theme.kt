package xyz.mufanc.bqc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NightBlue,
    onPrimary = Night,
    primaryContainer = NightQuiet,
    onPrimaryContainer = NightInk,
    background = Night,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightLine,
    onSurfaceVariant = NightSlate,
    outline = NightSlate,
    outlineVariant = NightLine,
)

private val LightColorScheme = lightColorScheme(
    primary = AcousticBlue,
    onPrimary = Frost,
    primaryContainer = QuietBlue,
    onPrimaryContainer = Ink,
    background = Fog,
    onBackground = Ink,
    surface = Frost,
    onSurface = Ink,
    surfaceVariant = Line,
    onSurfaceVariant = Slate,
    outline = Slate,
    outlineVariant = Line,
)

@Composable
fun BoseQCTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
