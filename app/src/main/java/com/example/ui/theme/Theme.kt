package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme =
  lightColorScheme(
    primary = WorkshopTerracotta,
    onPrimary = Color.White,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    inversePrimary = DarkTerracotta,
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
    inverseSurface = InkPrimary,
    inverseOnSurface = ParchmentBg,
    surfaceDim = LinenDeep,
    surfaceBright = CardCream,
    surfaceContainerLowest = CardCream,
    surfaceContainerLow = ParchmentBg,
    surfaceContainer = LinenSurface,
    surfaceContainerHigh = LinenDeep,
    surfaceContainerHighest = LinenDeepest,
    outline = OutlineTaupe,
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
    inversePrimary = WorkshopTerracotta,
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
    inverseSurface = DarkInkPrimary,
    inverseOnSurface = DarkWorkshopCard,
    surfaceDim = DarkWorkshopBg,
    surfaceBright = DarkHairline,
    surfaceContainerLowest = DarkWorkshopBg,
    surfaceContainerLow = DarkWorkshopCard,
    surfaceContainer = DarkWorkshopLinen,
    surfaceContainerHigh = DarkHairline,
    surfaceContainerHighest = DarkLinenHighest,
    outline = DarkOutline,
    outlineVariant = DarkHairline,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
  )

/**
 * Brand accents that Material's colour roles have no slot for. Like the Material scheme they come
 * in a light and a dark variant: used as fixed colours they left amber-on-cream chips and
 * near-white text on cream banners in the dark theme.
 */
@Immutable
data class BlockBenchExtendedColors(
  /** Money released, completed handoffs, "you earn". */
  val escrow: Color,
  val escrowContainer: Color,
  val onEscrowContainer: Color,
  /** Pro Alerts, instant pickup, ratings. */
  val pro: Color,
  val proContainer: Color,
  val onProContainer: Color,
)

private val LightExtendedColors =
  BlockBenchExtendedColors(
    escrow = EscrowSage,
    escrowContainer = EscrowSageSoft,
    onEscrowContainer = EscrowSage,
    pro = ProAmber,
    proContainer = ProAmberSoft,
    onProContainer = ProAmberInk,
  )

private val DarkExtendedColors =
  BlockBenchExtendedColors(
    escrow = DarkEscrowSage,
    escrowContainer = DarkEscrowSageContainer,
    onEscrowContainer = DarkEscrowSage,
    pro = DarkProAmber,
    proContainer = DarkProAmberContainer,
    onProContainer = DarkProAmber,
  )

private val LocalBlockBenchColors = staticCompositionLocalOf { LightExtendedColors }

/** The themed brand accents; use next to `MaterialTheme.colorScheme`. */
val blockBenchColors: BlockBenchExtendedColors
  @Composable @ReadOnlyComposable get() = LocalBlockBenchColors.current

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
  CompositionLocalProvider(
    LocalBlockBenchColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors
  ) {
    MaterialTheme(
      colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
      typography = BlockBenchTypography,
      shapes = BlockBenchShapes,
      content = content,
    )
  }
}
