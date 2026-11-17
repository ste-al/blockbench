package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How [ActionRow] decides between buttons side by side and one under the other. */
class ActionRowTest {

  @Test
  fun `buttons share the line by weight when every label fits`() {
    // 290 to share: 120.8 and 169.2, rounded down, with the pixel left over going to the last.
    assertEquals(listOf(120, 170), sideBySideWidths(300, 10, listOf(80, 120), listOf(1f, 1.4f)))
  }

  @Test
  fun `buttons are stacked when one share is too narrow, even if the total would fit`() {
    // 150 + 100 + 10 fits in 300, but an even split leaves the first button 145 for 150.
    assertNull(sideBySideWidths(300, 10, listOf(150, 100), listOf(1f, 1f)))
    assertEquals(listOf(145, 145), sideBySideWidths(300, 10, listOf(140, 100), listOf(1f, 1f)))
  }

  @Test
  fun `a button without a weight keeps its natural width and the others share the rest`() {
    assertEquals(listOf(230, 60), sideBySideWidths(300, 10, listOf(100, 60), listOf(1f, 0f)))
    assertEquals(
      listOf(110, 110, 60),
      sideBySideWidths(300, 10, listOf(60, 70, 60), listOf(1f, 1f, 0f)),
    )
  }

  @Test
  fun `buttons are stacked when their natural widths alone overflow the line`() {
    assertNull(sideBySideWidths(200, 10, listOf(150, 60), listOf(1f, 0f)))
    assertNull(sideBySideWidths(200, 10, listOf(120, 120), listOf(1f, 1f)))
  }

  @Test
  fun `the widths always add up to the line`() {
    val widths = checkNotNull(sideBySideWidths(331, 10, listOf(50, 50, 50), listOf(1f, 1.4f, 2f)))
    assertEquals(331 - 2 * 10, widths.sum())
  }

  @Test
  fun `a single button takes the whole line`() {
    assertEquals(listOf(300), sideBySideWidths(300, 10, listOf(100), listOf(1f)))
  }
}
