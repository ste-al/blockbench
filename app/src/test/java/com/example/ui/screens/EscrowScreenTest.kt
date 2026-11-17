package com.example.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.model.Booking
import com.example.model.UiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TALL_PHONE)
class EscrowScreenTest {

  @get:Rule val compose = createComposeRule()

  private data class Advance(val bookingId: String, val nextStatus: String, val notes: String)

  private val advances = mutableListOf<Advance>()
  private var retries = 0

  private fun show(ledger: UiState<List<Booking>>, isBusy: Boolean = false) {
    compose.setThemedContent {
      EscrowScreen(
        currentUserId = ME,
        ledger = ledger,
        isBusy = isBusy,
        onAdvanceStatus = { booking, next, notes -> advances += Advance(booking.id, next, notes) },
        onRetry = { retries++ },
      )
    }
  }

  private fun show(vararg bookings: Booking) = show(UiState.Success(bookings.toList()))

  @Test
  fun `the borrower waits for approval and can only cancel the request`() {
    show(sampleBooking("b1", Booking.STATUS_REQUESTED, iOwnTheListing = false))

    compose.onNodeWithText("YOU BORROW").assertExists()
    compose.onNodeWithTag("booking_waiting_b1").assertExists()
    compose.onNodeWithText("Waiting for Bob Carter to approve your request.").assertExists()
    compose.onNodeWithTag("lock_escrow_b1").assertDoesNotExist()

    compose.onNodeWithTag("cancel_booking_b1").performClick()
    compose.onNodeWithText("Cancel your request?").assertExists()
    compose.onNodeWithTag("confirm_cancel_booking_button").performClick()

    assertEquals(listOf(Advance("b1", Booking.STATUS_CANCELLED, "")), advances)
  }

  // The approve and start-handoff buttons open a dialog with a note field. That dialog is not
  // driven from these tests: with the Compose version in use, a text field inside an AlertDialog
  // never becomes idle under Robolectric (it does not even in a bare two-line reproduction), so
  // the tests below stop at "the right neighbour is offered the step".

  @Test
  fun `the listing owner is offered approval instead of a waiting note`() {
    show(sampleBooking("b1", Booking.STATUS_REQUESTED, iOwnTheListing = true))

    compose.onNodeWithText("YOU EARN").assertExists()
    compose.onNodeWithTag("booking_waiting_b1").assertDoesNotExist()
    compose.onNodeWithTag("lock_escrow_b1").assertIsEnabled()
    compose.onNodeWithTag("cancel_booking_b1").assertIsEnabled()
  }

  @Test
  fun `the listing owner can decline a request`() {
    show(sampleBooking("b1", Booking.STATUS_REQUESTED, iOwnTheListing = true))

    compose.onNodeWithTag("cancel_booking_b1").performClick()
    compose.onNodeWithText("Decline this request?").assertExists()
    compose.onNodeWithTag("confirm_cancel_booking_button").performClick()

    assertEquals(listOf(Advance("b1", Booking.STATUS_CANCELLED, "")), advances)
  }

  @Test
  fun `either neighbour may start the handoff or refund once escrow is held`() {
    show(
      sampleBooking("mine", Booking.STATUS_ESCROW_HELD, iOwnTheListing = true),
      sampleBooking("theirs", Booking.STATUS_ESCROW_HELD, iOwnTheListing = false, notes = "Porch locker 2"),
    )

    compose.onNodeWithTag("start_handoff_mine").assertIsEnabled()
    compose.onNodeWithTag("start_handoff_theirs").assertIsEnabled()
    compose.onNodeWithTag("refund_booking_mine").assertIsEnabled()

    compose.onNodeWithTag("refund_booking_theirs").performClick()
    compose.onNodeWithText("Cancel and refund the escrow hold?").assertExists()
    compose.onNodeWithTag("confirm_cancel_booking_button").performClick()

    // The note already on the booking is kept.
    assertEquals(listOf(Advance("theirs", Booking.STATUS_CANCELLED, "Porch locker 2")), advances)
  }

  @Test
  fun `only the listing owner is offered completion`() {
    show(
      sampleBooking("mine", Booking.STATUS_IN_PROGRESS, iOwnTheListing = true),
      sampleBooking("theirs", Booking.STATUS_IN_PROGRESS, iOwnTheListing = false),
    )

    compose.onNodeWithTag("complete_escrow_theirs").assertDoesNotExist()
    compose.onNodeWithTag("booking_waiting_theirs").assertExists()
    // Nothing can be cancelled once the handoff has started.
    compose.onNodeWithTag("cancel_booking_mine").assertDoesNotExist()
    compose.onNodeWithTag("refund_booking_theirs").assertDoesNotExist()

    compose.onNodeWithTag("complete_escrow_mine").performClick()

    assertEquals(listOf(Advance("mine", Booking.STATUS_COMPLETED, "")), advances)
  }

  @Test
  fun `settled bookings move to their own tab and offer no actions`() {
    show(
      sampleBooking("open", Booking.STATUS_REQUESTED, iOwnTheListing = true),
      sampleBooking("done", Booking.STATUS_COMPLETED, iOwnTheListing = true),
      sampleBooking("dropped", Booking.STATUS_CANCELLED, iOwnTheListing = false),
    )

    compose.onNodeWithText("Active (1)").assertExists()
    compose.onNodeWithTag("booking_card_open").assertExists()
    compose.onNodeWithTag("booking_card_done").assertDoesNotExist()

    compose.onNodeWithTag("escrow_tab_settled").performClick()

    compose.onNodeWithText("Settled (2)").assertExists()
    compose.onNodeWithTag("booking_card_done").assertExists()
    compose.onNodeWithTag("booking_card_dropped").assertExists()
    compose.onNodeWithTag("booking_card_open").assertDoesNotExist()
    compose.onNodeWithTag("complete_escrow_done").assertDoesNotExist()
    compose.onNodeWithTag("cancel_booking_dropped").assertDoesNotExist()
  }

  @Test
  fun `the role filter narrows the ledger to one side`() {
    show(
      sampleBooking("lending", Booking.STATUS_REQUESTED, iOwnTheListing = true),
      sampleBooking("borrowing", Booking.STATUS_REQUESTED, iOwnTheListing = false),
    )

    compose.onNodeWithTag("escrow_role_providing").performClick()
    compose.onNodeWithTag("booking_card_lending").assertExists()
    compose.onNodeWithTag("booking_card_borrowing").assertDoesNotExist()

    compose.onNodeWithTag("escrow_role_requesting").performClick()
    compose.onNodeWithTag("booking_card_borrowing").assertExists()
    compose.onNodeWithTag("booking_card_lending").assertDoesNotExist()
  }

  @Test
  fun `actions are disabled while a write is in flight`() {
    show(
      UiState.Success(listOf(sampleBooking("b1", Booking.STATUS_REQUESTED, iOwnTheListing = true))),
      isBusy = true,
    )

    compose.onNodeWithTag("lock_escrow_b1").assertIsNotEnabled()
    compose.onNodeWithTag("cancel_booking_b1").assertIsNotEnabled()
  }

  @Test
  fun `loading and failure are shown as such, not as an empty ledger`() {
    show(UiState.Loading)
    compose.onNodeWithTag("escrow_loading").assertExists()
    compose.onNodeWithText("No active escrow holds").assertDoesNotExist()
  }

  @Test
  fun `a failed ledger offers a retry`() {
    show(UiState.Error("Could not load your escrow ledger. You appear to be offline."))

    compose.onNodeWithText("Could not load your escrow ledger. You appear to be offline.").assertExists()
    compose.onNodeWithText("No active escrow holds").assertDoesNotExist()
    compose.onNodeWithTag("escrow_retry_button").performClick()

    assertEquals(1, retries)
  }
}
