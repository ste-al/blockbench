package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme =
  lightColorScheme(
    primary = WorkshopTerracotta,
    onPrimary = Color.White,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    secondary = DeepPine,
    onSecondary = Color.White,
    secondaryContainer = PineContainer,
    onSecondaryContainer = OnPineContainer,
    tertiary = ProAmber,
    onTertiary = Color.White,
    tertiaryContainer = ProAmberSoft,
    onTertiaryContainer = InkPrimary,
    background = ParchmentBg,
    onBackground = InkPrimary,
    surface = CardCream,
    onSurface = InkPrimary,
    surfaceVariant = LinenSurface,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = CardCream,
    surfaceContainerLow = ParchmentBg,
    surfaceContainer = LinenSurface,
    surfaceContainerHigh = LinenDeep,
    outline = HairlineTaupe,
    outlineVariant = HairlineTaupe,
    error = ErrorCrimson,
    onError = Color.White,
    errorContainer = ErrorSoft,
    onErrorContainer = ErrorCrimson,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = DarkTerracotta,
    onPrimary = DarkWorkshopBg,
    primaryContainer = DarkTerracottaContainer,
    onPrimaryContainer = DarkInkPrimary,
    secondary = DarkPine,
    onSecondary = DarkWorkshopBg,
    secondaryContainer = DarkPineContainer,
    onSecondaryContainer = DarkInkPrimary,
    tertiary = DarkProAmber,
    onTertiary = DarkWorkshopBg,
    tertiaryContainer = DarkWorkshopLinen,
    onTertiaryContainer = DarkInkPrimary,
    background = DarkWorkshopBg,
    onBackground = DarkInkPrimary,
    surface = DarkWorkshopCard,
    onSurface = DarkInkPrimary,
    surfaceVariant = DarkWorkshopLinen,
    onSurfaceVariant = DarkInkMuted,
    surfaceContainerLowest = DarkWorkshopBg,
    surfaceContainerLow = DarkWorkshopCard,
    surfaceContainer = DarkWorkshopLinen,
    surfaceContainerHigh = DarkHairline,
    outline = DarkHairline,
    outlineVariant = DarkHairline,
  )

val BlockBenchShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun BlockBenchTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = BlockBenchTypography,
    shapes = BlockBenchShapes,
    content = content,
  )
}
