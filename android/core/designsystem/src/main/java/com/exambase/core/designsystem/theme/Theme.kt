package com.exambase.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Brand500,
    onPrimary = Neutral0,
    primaryContainer = Brand100,
    onPrimaryContainer = Brand900,
    secondary = Accent500,
    onSecondary = Neutral0,
    background = Neutral50,
    onBackground = Neutral900,
    surface = Neutral0,
    onSurface = Neutral900,
    onSurfaceVariant = Neutral500,
    outline = Neutral200,
    error = Danger500,
)

private val DarkColors = darkColorScheme(
    primary = Brand100,
    onPrimary = Brand900,
    primaryContainer = Brand600,
    onPrimaryContainer = Brand50,
    secondary = Accent200,
    onSecondary = Neutral950,
    background = Neutral950,
    onBackground = Neutral50,
    surface = Neutral900,
    onSurface = Neutral50,
    onSurfaceVariant = Neutral200,
    outline = Neutral500,
    error = Danger200,
)

@Composable
fun ExamBaseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ExamBaseTypography,
        shapes = ExamBaseShapes,
        content = content,
    )
}
