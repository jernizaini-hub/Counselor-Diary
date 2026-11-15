package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme =
  lightColorScheme(
    primary = SagePrimary,
    onPrimary = SageOnPrimary,
    primaryContainer = SagePrimaryContainer,
    onPrimaryContainer = SageOnPrimaryContainer,
    secondary = TerracottaSecondary,
    onSecondary = TerracottaOnSecondary,
    secondaryContainer = TerracottaContainer,
    onSecondaryContainer = TerracottaOnContainer,
    tertiary = SlateTertiary,
    onTertiary = SlateOnTertiary,
    tertiaryContainer = SlateContainer,
    onTertiaryContainer = SlateOnContainer,
    background = CreamBackground,
    onBackground = InkOnBackground,
    surface = CreamSurface,
    onSurface = InkOnBackground,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = InkOnSurfaceVariant,
    surfaceContainerLow = CreamSurfaceContainerLow,
    surfaceContainer = CreamSurfaceContainer,
    surfaceContainerHigh = CreamSurfaceContainerHigh,
    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant,
    error = ErrorCrimson,
    errorContainer = ErrorContainerLight,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = DarkSagePrimary,
    onPrimary = DarkOnSagePrimary,
    primaryContainer = DarkSageContainer,
    onPrimaryContainer = SagePrimaryContainer,
    secondary = DarkTerracottaSecondary,
    onSecondary = DarkOnTerracotta,
    secondaryContainer = TerracottaOnContainer,
    onSecondaryContainer = TerracottaContainer,
    tertiary = SlateContainer,
    onTertiary = SlateOnContainer,
    background = DarkForestBackground,
    onBackground = DarkInk,
    surface = DarkForestSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkForestSurfaceContainer,
    onSurfaceVariant = DarkInkVariant,
    surfaceContainerLow = DarkForestSurface,
    surfaceContainer = DarkForestSurfaceContainer,
    surfaceContainerHigh = DarkForestSurfaceContainer,
    outline = WarmOutline,
  )

val AppShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
  )

@Composable
fun DiariKaunselorTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = AppTypography,
    shapes = AppShapes,
    content = content,
  )
}
