package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EscrowFeeBreakdownTest {

  @Test
  fun `fee is five percent of the rate and comes out of the provider's payout`() {
    val breakdown = EscrowFeeBreakdown.calculate(rateCents = 2000L, depositCents = 4000L)

    assertEquals(2000L, breakdown.grossAmountCents)
    assertEquals(4000L, breakdown.depositCents)
    assertEquals(100L, breakdown.platformFeeCents)
    assertEquals(1900L, breakdown.providerNetCents)
    assertEquals(6000L, breakdown.totalEscrowHoldCents)
  }

  @Test
  fun `fee is rounded half up to a whole cent`() {
    // 5% of each rate: 50.45, 50.50, 50.55, 0.50 and 6.15 cents.
    assertEquals(50L, EscrowFeeBreakdown.calculate(1009L, 0L).platformFeeCents)
    assertEquals(51L, EscrowFeeBreakdown.calculate(1010L, 0L).platformFeeCents)
    assertEquals(51L, EscrowFeeBreakdown.calculate(1011L, 0L).platformFeeCents)
    assertEquals(1L, EscrowFeeBreakdown.calculate(10L, 0L).platformFeeCents)
    assertEquals(6L, EscrowFeeBreakdown.calculate(123L, 0L).platformFeeCents)
  }

  @Test
  fun `fee and payout at the limits a listing may have`() {
    val smallest = EscrowFeeBreakdown.calculate(Listing.MIN_RATE_CENTS, 0L)
    assertEquals(5L, smallest.platformFeeCents)
    assertEquals(95L, smallest.providerNetCents)

    val largest = EscrowFeeBreakdown.calculate(Listing.MAX_AMOUNT_CENTS, Listing.MAX_AMOUNT_CENTS)
    assertEquals(25_000L, largest.platformFeeCents)
    assertEquals(475_000L, largest.providerNetCents)
    assertEquals(1_000_000L, largest.totalEscrowHoldCents)
  }

  @Test
  fun `fee is never below one cent and the payout never negative`() {
    val nine = EscrowFeeBreakdown.calculate(rateCents = 9L, depositCents = 0L)
    assertEquals(1L, nine.platformFeeCents)
    assertEquals(8L, nine.providerNetCents)

    val nothing = EscrowFeeBreakdown.calculate(rateCents = 0L, depositCents = 0L)
    assertEquals(1L, nothing.platformFeeCents)
    assertEquals(0L, nothing.providerNetCents)
  }

  @Test
  fun `payout plus fee always equals the rate for bookable rates`() {
    for (rate in Listing.MIN_RATE_CENTS..5_000L) {
      val breakdown = EscrowFeeBreakdown.calculate(rate, 0L)
      assertEquals(rate, breakdown.providerNetCents + breakdown.platformFeeCents)
    }
  }
}
