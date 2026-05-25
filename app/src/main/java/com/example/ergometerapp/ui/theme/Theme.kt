package com.example.ergometerapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Cyan80,
    onPrimary = Cyan30,
    primaryContainer = Cyan30,
    onPrimaryContainer = Cyan90,
    secondary = Steel80,
    onSecondary = Slate20,
    secondaryContainer = Slate40,
    onSecondaryContainer = Slate90,
    background = Slate10,
    onBackground = Slate90,
    surface = Slate20,
    onSurface = Slate90,
    surfaceVariant = Slate40,
    onSurfaceVariant = Slate80,
    outline = Slate80
)

private val LightColorScheme = lightColorScheme(
    primary = Cyan40,
    onPrimary = Slate95,
    primaryContainer = Cyan90,
    onPrimaryContainer = Cyan30,
    secondary = Steel40,
    onSecondary = Slate95,
    secondaryContainer = Cyan90,
    onSecondaryContainer = Slate20,
    background = Slate95,
    onBackground = Slate20,
    surface = Slate95,
    onSurface = Slate20,
    surfaceVariant = Slate90,
    onSurfaceVariant = Slate40,
    outline = Steel40
)

@Composable
fun EwocTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep custom palette by default to preserve a stable brand look.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
