package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The palette, held to the WCAG contrast minimums for every pairing the app puts on screen: 4.5:1
 * for text, 3:1 for the parts of a control that have to be seen (borders, icons, a switch's
 * thumb).
 *
 * The accents are also the colours of text buttons and figures, and Material draws dialogs,
 * sheets and the navigation bar on tinted surfaces of its own choosing. A colour that reads fine
 * on a white card can drop below the minimum on those, which is how the terracotta of a dialog's
 * buttons did before this test existed.
 *
 * Roles that nothing draws with (the tertiary pair) are deliberately left out.
 */
class ThemeContrastTest {

  private class Pairing(val what: String, val foreground: Color, val background: Color)

  private infix fun Pair<String, Color>.on(background: Pair<String, Color>) =
    Pairing("$first on ${background.first}", second, background.second)

  private fun contrast(a: Color, b: Color): Double {
    val lighter = maxOf(a.luminance(), b.luminance())
    val darker = minOf(a.luminance(), b.luminance())
    return (lighter + 0.05) / (darker + 0.05)
  }

  private fun List<Pairing>.below(minimum: Double): List<String> =
    filter { contrast(it.foreground, it.background) < minimum }
      .map { String.format(Locale.US, "%s (%.2f)", it.what, contrast(it.foreground, it.background)) }

  /** Everything text is drawn on: the page, cards, linen cards, sheets, the nav bar, dialogs. */
  private fun ColorScheme.textSurfaces() =
    listOf(
      "background" to background,
      "surface" to surface,
      "surfaceVariant" to surfaceVariant,
      "surfaceContainerLow" to surfaceContainerLow,
      "surfaceContainer" to surfaceContainer,
      "surfaceContainerHigh" to surfaceContainerHigh,
    )

  private fun textPairings(scheme: ColorScheme, brand: BlockBenchExtendedColors): List<Pairing> =
    scheme.textSurfaces().flatMap { surface ->
      listOf(
        ("onSurface" to scheme.onSurface) on surface,
        ("onSurfaceVariant" to scheme.onSurfaceVariant) on surface,
        // Text buttons, links and highlighted figures.
        ("primary" to scheme.primary) on surface,
        // Destructive text buttons and error messages.
        ("error" to scheme.error) on surface,
        // Payout figures.
        ("escrow" to brand.escrow) on surface,
      )
    } +
      listOf(
        ("onBackground" to scheme.onBackground) on ("background" to scheme.background),
        ("onPrimary" to scheme.onPrimary) on ("primary" to scheme.primary),
        ("onPrimaryContainer" to scheme.onPrimaryContainer) on
          ("primaryContainer" to scheme.primaryContainer),
        ("onSecondaryContainer" to scheme.onSecondaryContainer) on
          ("secondaryContainer" to scheme.secondaryContainer),
        ("onError" to scheme.onError) on ("error" to scheme.error),
        ("onErrorContainer" to scheme.onErrorContainer) on
          ("errorContainer" to scheme.errorContainer),
        // Snackbars.
        ("inverseOnSurface" to scheme.inverseOnSurface) on
          ("inverseSurface" to scheme.inverseSurface),
        ("inversePrimary" to scheme.inversePrimary) on ("inverseSurface" to scheme.inverseSurface),
        // Brand chips, and the "release payout" button.
        ("onEscrowContainer" to brand.onEscrowContainer) on
          ("escrowContainer" to brand.escrowContainer),
        ("surface" to scheme.surface) on ("escrow" to brand.escrow),
        ("onProContainer" to brand.onProContainer) on ("proContainer" to brand.proContainer),
        // The Pro Alerts banner and card hold ordinary text and a slider's figure. Where the app
        // thins a tint out over the page, the result lies between the two, so both ends passing
        // is enough.
        ("onSurface" to scheme.onSurface) on ("proContainer" to brand.proContainer),
        ("onSurfaceVariant" to scheme.onSurfaceVariant) on ("proContainer" to brand.proContainer),
        ("primary" to scheme.primary) on ("proContainer" to brand.proContainer),
        // The selected row of the block switcher.
        ("onSurface" to scheme.onSurface) on ("primaryContainer" to scheme.primaryContainer),
        ("onSurfaceVariant" to scheme.onSurfaceVariant) on
          ("primaryContainer" to scheme.primaryContainer),
      )

  private fun controlPairings(scheme: ColorScheme, brand: BlockBenchExtendedColors): List<Pairing> =
    // Borders of text fields and outlined buttons, wherever those can appear.
    scheme.textSurfaces().map { surface -> ("outline" to scheme.outline) on surface } +
      listOf(
        // The thumb and border of a switch that is off, on its own track.
        ("outline" to scheme.outline) on
          ("surfaceContainerHighest" to scheme.surfaceContainerHighest),
        // The rating star on a listing card.
        ("pro" to brand.pro) on ("surface" to scheme.surface),
        // The placeholder icon of a listing without a photo.
        ("secondary" to scheme.secondary) on ("surfaceVariant" to scheme.surfaceVariant),
        // Chip borders on the Pro Alerts card, icon buttons on the selected block's row.
        ("outline" to scheme.outline) on ("proContainer" to brand.proContainer),
        ("primary" to scheme.primary) on ("primaryContainer" to scheme.primaryContainer),
      )

  @Test
  fun `light theme text keeps at least 4_5 to 1 on everything it is drawn on`() {
    assertEquals(emptyList<String>(), textPairings(LightColorScheme, LightExtendedColors).below(4.5))
  }

  @Test
  fun `dark theme text keeps at least 4_5 to 1 on everything it is drawn on`() {
    assertEquals(emptyList<String>(), textPairings(DarkColorScheme, DarkExtendedColors).below(4.5))
  }

  @Test
  fun `light theme control outlines and icons keep at least 3 to 1`() {
    assertEquals(
      emptyList<String>(),
      controlPairings(LightColorScheme, LightExtendedColors).below(3.0),
    )
  }

  @Test
  fun `dark theme control outlines and icons keep at least 3 to 1`() {
    assertEquals(
      emptyList<String>(),
      controlPairings(DarkColorScheme, DarkExtendedColors).below(3.0),
    )
  }
}
