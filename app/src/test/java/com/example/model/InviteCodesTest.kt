package com.example.model

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodesTest {

  @Test
  fun `normalize upper-cases and drops everything that is not a letter or digit`() {
    assertEquals("OAK7K2MQ", InviteCodes.normalize("oak7k2mq"))
    assertEquals("OAK7K2MQ", InviteCodes.normalize("  oak-7k2 mq\n"))
    assertEquals("OAK482", InviteCodes.normalize("(OAK482)!"))
    assertEquals("", InviteCodes.normalize(" -_. "))
  }

  @Test
  fun `well-formed codes are 4 to 12 upper-case letters and digits`() {
    assertTrue(InviteCodes.isWellFormed("OAK482"))
    assertTrue(InviteCodes.isWellFormed("OAK7K2MQ"))
    assertTrue(InviteCodes.isWellFormed("ABCD"))
    assertTrue(InviteCodes.isWellFormed("ABCDEFGH1234"))

    assertFalse(InviteCodes.isWellFormed(""))
    assertFalse(InviteCodes.isWellFormed("ABC"))
    assertFalse(InviteCodes.isWellFormed("ABCDEFGH12345"))
    assertFalse(InviteCodes.isWellFormed("oak482"))
    assertFalse(InviteCodes.isWellFormed("OAK-482"))
    assertFalse(InviteCodes.isWellFormed("OAK 482"))
  }

  @Test
  fun `a generated code starts with three letters of the block name`() {
    assertTrue(InviteCodes.generate("Oak & 14th Block").startsWith("OAK"))
    assertTrue(InviteCodes.generate("maple court").startsWith("MAP"))
    // Too few usable letters: padded, never shorter.
    assertTrue(InviteCodes.generate("9th & 3rd").startsWith("THR"))
    assertTrue(InviteCodes.generate("A1").startsWith("ABB"))
    assertTrue(InviteCodes.generate("12345").startsWith("BBB"))
    assertTrue(InviteCodes.generate("Улица Ленина").startsWith("BBB"))
  }

  @Test
  fun `a generated code is eight characters and always well-formed`() {
    val random = Random(42)
    repeat(500) {
      val code = InviteCodes.generate("Oak Block", random)
      assertEquals(8, code.length)
      assertTrue("$code is not well-formed", InviteCodes.isWellFormed(code))
      assertEquals(code, InviteCodes.normalize(code))
    }
  }

  @Test
  fun `the random part avoids characters that are easily confused`() {
    val random = Random(7)
    val suffixCharacters = (1..2000).flatMap { InviteCodes.generate("Oak", random).drop(3).toList() }.toSet()

    assertTrue(suffixCharacters.none { it in "01OIL" })
    // All 31 remaining characters do come up, i.e. the suffix really spans the whole alphabet.
    assertEquals(31, suffixCharacters.size)
  }

  @Test
  fun `codes are driven by the random source`() {
    assertEquals(InviteCodes.generate("Oak", Random(1)), InviteCodes.generate("Oak", Random(1)))
    assertNotEquals(InviteCodes.generate("Oak", Random(1)), InviteCodes.generate("Oak", Random(2)))
    // The default source is not seeded: two blocks with the same name get different codes.
    assertTrue((1..20).map { InviteCodes.generate("Oak") }.toSet().size > 1)
  }
}
