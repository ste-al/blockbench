package com.example

import com.example.model.CraftCategories
import com.example.model.EscrowFeeBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun escrowFeeBreakdown_calculatesFivePercentFeeAndNetPayout() {
    val breakdown = EscrowFeeBreakdown.calculate(rateCents = 2000L, depositCents = 4000L)
    assertEquals(2000L, breakdown.grossAmountCents)
    assertEquals(4000L, breakdown.depositCents)
    assertEquals(100L, breakdown.platformFeeCents)
    assertEquals(1900L, breakdown.providerNetCents)
    assertEquals(6000L, breakdown.totalEscrowHoldCents)
  }

  @Test
  fun escrowFeeBreakdown_enforcesMinimumOneCentFee() {
    val breakdown = EscrowFeeBreakdown.calculate(rateCents = 100L, depositCents = 0L)
    assertEquals(5L, breakdown.platformFeeCents)
    assertEquals(95L, breakdown.providerNetCents)
  }

  @Test
  fun parseDollarsToCents_parsesValidCurrencyAndRejectsMalformed() {
    assertEquals(1800L, CraftCategories.parseDollarsToCents("18"))
    assertEquals(1850L, CraftCategories.parseDollarsToCents("18.5"))
    assertEquals(1875L, CraftCategories.parseDollarsToCents("18.75"))
    assertNull(CraftCategories.parseDollarsToCents(""))
    assertNull(CraftCategories.parseDollarsToCents("18.7.5"))
    assertNull(CraftCategories.parseDollarsToCents("18.755"))
    assertNull(CraftCategories.parseDollarsToCents("-5"))
  }
}
