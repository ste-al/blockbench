package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingTotalsTest {

  private val me = "me"

  private fun booking(status: String, iProvide: Boolean, net: Long = 1710L, fee: Long = 90L) =
    Booking(
      requesterUid = if (iProvide) "neighbor" else me,
      providerUid = if (iProvide) me else "neighbor",
      providerNetCents = net,
      platformFeeCents = fee,
      status = status,
    )

  @Test
  fun `only completed bookings count`() {
    val totals =
      BookingTotals.from(
        listOf(
          booking(Booking.STATUS_REQUESTED, iProvide = true),
          booking(Booking.STATUS_ESCROW_HELD, iProvide = true),
          booking(Booking.STATUS_IN_PROGRESS, iProvide = true),
          booking(Booking.STATUS_CANCELLED, iProvide = true),
        ),
        me,
      )
    assertEquals(BookingTotals(), totals)
  }

  @Test
  fun `handoffs and fees count on both sides, earnings only where I was the provider`() {
    val totals =
      BookingTotals.from(
        listOf(
          booking(Booking.STATUS_COMPLETED, iProvide = true, net = 1710L, fee = 90L),
          booking(Booking.STATUS_COMPLETED, iProvide = true, net = 2375L, fee = 125L),
          booking(Booking.STATUS_COMPLETED, iProvide = false, net = 1140L, fee = 60L),
        ),
        me,
      )
    assertEquals(3L, totals.completedHandoffs)
    assertEquals(4085L, totals.earnedCents)
    assertEquals(275L, totals.feesCents)
  }

  @Test
  fun `exceeds is true as soon as one figure is ahead of the profile`() {
    val profile =
      UserProfile(completedHandoffs = 2L, totalEarnedCents = 1000L, totalFeesSavedOrPaidCents = 100L)

    assertFalse(BookingTotals(2L, 1000L, 100L).exceeds(profile))
    assertFalse(BookingTotals(1L, 900L, 90L).exceeds(profile))
    assertTrue(BookingTotals(3L, 1000L, 100L).exceeds(profile))
    assertTrue(BookingTotals(2L, 1001L, 100L).exceeds(profile))
    assertTrue(BookingTotals(2L, 1000L, 101L).exceeds(profile))
  }

  @Test
  fun `atLeast never shows less than the profile already records`() {
    val profile =
      UserProfile(completedHandoffs = 5L, totalEarnedCents = 1000L, totalFeesSavedOrPaidCents = 100L)

    assertEquals(BookingTotals(5L, 2000L, 100L), BookingTotals(3L, 2000L, 40L).atLeast(profile))
    assertEquals(BookingTotals(3L, 2000L, 40L), BookingTotals(3L, 2000L, 40L).atLeast(null))
  }
}
