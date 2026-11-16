package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CraftCategoriesTest {

  @Test
  fun `parses whole and fractional dollar amounts`() {
    assertEquals(1800L, CraftCategories.parseDollarsToCents("18"))
    assertEquals(1850L, CraftCategories.parseDollarsToCents("18.5"))
    assertEquals(1875L, CraftCategories.parseDollarsToCents("18.75"))
    assertEquals(1800L, CraftCategories.parseDollarsToCents("18."))
    assertEquals(50L, CraftCategories.parseDollarsToCents(".5"))
    assertEquals(0L, CraftCategories.parseDollarsToCents("0"))
    assertEquals(1800L, CraftCategories.parseDollarsToCents("  18  "))
    assertEquals(500_000L, CraftCategories.parseDollarsToCents("5000.00"))
  }

  @Test
  fun `accepts a comma as the decimal separator`() {
    assertEquals(1875L, CraftCategories.parseDollarsToCents("18,75"))
    assertEquals(50L, CraftCategories.parseDollarsToCents(",5"))
  }

  @Test
  fun `is exact where binary floating point is not`() {
    // 0.29 * 100 and 1.15 * 100 are 28.999... and 114.999... as doubles.
    assertEquals(29L, CraftCategories.parseDollarsToCents("0.29"))
    assertEquals(115L, CraftCategories.parseDollarsToCents("1.15"))
    assertEquals(99_999_999_999L, CraftCategories.parseDollarsToCents("999999999.99"))
  }

  @Test
  fun `rejects anything that is not a plain amount`() {
    for (input in listOf("", " ", ".", ",", "18.7.5", "18.755", "-5", "+5", "abc", "1 000", "1,000.50", "1e3", "0x10", "1234567890")) {
      assertNull("\"$input\" should be rejected", CraftCategories.parseDollarsToCents(input))
    }
  }

  @Test
  fun `formats cents for an editable field and parses them back`() {
    assertEquals("18", CraftCategories.centsToDollarsInput(1800L))
    assertEquals("18.50", CraftCategories.centsToDollarsInput(1850L))
    assertEquals("0.05", CraftCategories.centsToDollarsInput(5L))
    assertEquals("0", CraftCategories.centsToDollarsInput(0L))

    for (cents in listOf(0L, 5L, 99L, 100L, 1850L, 123_456L, 500_000L)) {
      assertEquals(cents, CraftCategories.parseDollarsToCents(CraftCategories.centsToDollarsInput(cents)))
    }
  }
}
