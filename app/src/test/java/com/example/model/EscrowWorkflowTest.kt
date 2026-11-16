package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The transition table here is the one enforced by the booking `update` rule in firestore.rules. */
class EscrowWorkflowTest {

  private val owner = "owner_uid"
  private val neighbor = "neighbor_uid"
  private val allStatuses =
    listOf(
      Booking.STATUS_REQUESTED,
      Booking.STATUS_ESCROW_HELD,
      Booking.STATUS_IN_PROGRESS,
      Booking.STATUS_COMPLETED,
      Booking.STATUS_CANCELLED,
    )

  /** A tool rental: the owner lends (provider), the neighbour borrows and pays (requester). */
  private fun toolBooking(status: String) =
    Booking(
      id = "b1",
      listingKind = Listing.KIND_TOOL,
      requesterUid = neighbor,
      requesterName = "Bob",
      providerUid = owner,
      providerName = "Alice",
      status = status,
    )

  /** A micro-job: the owner posted it and pays (requester), the neighbour does the work. */
  private fun jobBooking(status: String) =
    Booking(
      id = "b2",
      listingKind = Listing.KIND_MICRO_JOB,
      requesterUid = owner,
      requesterName = "Alice",
      providerUid = neighbor,
      providerName = "Bob",
      status = status,
    )

  @Test
  fun `the listing owner is the lender of a tool and the poster of a job`() {
    assertEquals(owner, toolBooking(Booking.STATUS_REQUESTED).listingOwnerUid)
    assertEquals("Alice", toolBooking(Booking.STATUS_REQUESTED).listingOwnerName)
    assertEquals(owner, jobBooking(Booking.STATUS_REQUESTED).listingOwnerUid)
    assertEquals("Alice", jobBooking(Booking.STATUS_REQUESTED).listingOwnerName)
  }

  @Test
  fun `only the owner approves a request, either side may cancel it`() {
    for (booking in listOf(toolBooking(Booking.STATUS_REQUESTED), jobBooking(Booking.STATUS_REQUESTED))) {
      assertEquals(
        setOf(Booking.STATUS_ESCROW_HELD, Booking.STATUS_CANCELLED),
        EscrowWorkflow.allowedNextStatuses(booking, owner),
      )
      assertEquals(
        setOf(Booking.STATUS_CANCELLED),
        EscrowWorkflow.allowedNextStatuses(booking, neighbor),
      )
    }
  }

  @Test
  fun `either side starts the handoff or cancels once escrow is held`() {
    for (booking in listOf(toolBooking(Booking.STATUS_ESCROW_HELD), jobBooking(Booking.STATUS_ESCROW_HELD))) {
      for (uid in listOf(owner, neighbor)) {
        assertEquals(
          setOf(Booking.STATUS_IN_PROGRESS, Booking.STATUS_CANCELLED),
          EscrowWorkflow.allowedNextStatuses(booking, uid),
        )
      }
    }
  }

  @Test
  fun `only the owner completes, and nobody cancels after the handoff`() {
    for (booking in listOf(toolBooking(Booking.STATUS_IN_PROGRESS), jobBooking(Booking.STATUS_IN_PROGRESS))) {
      assertEquals(setOf(Booking.STATUS_COMPLETED), EscrowWorkflow.allowedNextStatuses(booking, owner))
      assertEquals(emptySet<String>(), EscrowWorkflow.allowedNextStatuses(booking, neighbor))
    }
  }

  @Test
  fun `settled bookings are final`() {
    for (status in listOf(Booking.STATUS_COMPLETED, Booking.STATUS_CANCELLED)) {
      for (uid in listOf(owner, neighbor)) {
        assertEquals(emptySet<String>(), EscrowWorkflow.allowedNextStatuses(toolBooking(status), uid))
        assertEquals(emptySet<String>(), EscrowWorkflow.allowedNextStatuses(jobBooking(status), uid))
      }
      assertFalse(toolBooking(status).isActive)
    }
    assertTrue(toolBooking(Booking.STATUS_REQUESTED).isActive)
    assertTrue(toolBooking(Booking.STATUS_ESCROW_HELD).isActive)
    assertTrue(toolBooking(Booking.STATUS_IN_PROGRESS).isActive)
  }

  @Test
  fun `someone who is not part of the booking may do nothing`() {
    for (status in allStatuses) {
      assertEquals(emptySet<String>(), EscrowWorkflow.allowedNextStatuses(toolBooking(status), "stranger"))
      assertFalse(EscrowWorkflow.awaitsActionFrom(toolBooking(status), "stranger"))
    }
  }

  @Test
  fun `steps cannot be skipped or repeated`() {
    val requested = toolBooking(Booking.STATUS_REQUESTED)
    assertTrue(EscrowWorkflow.canTransition(requested, owner, Booking.STATUS_ESCROW_HELD))
    assertFalse(EscrowWorkflow.canTransition(requested, owner, Booking.STATUS_REQUESTED))
    assertFalse(EscrowWorkflow.canTransition(requested, owner, Booking.STATUS_IN_PROGRESS))
    assertFalse(EscrowWorkflow.canTransition(requested, owner, Booking.STATUS_COMPLETED))
    assertFalse(EscrowWorkflow.canTransition(requested, owner, "refunded"))

    val held = toolBooking(Booking.STATUS_ESCROW_HELD)
    assertFalse(EscrowWorkflow.canTransition(held, owner, Booking.STATUS_REQUESTED))
    assertFalse(EscrowWorkflow.canTransition(held, owner, Booking.STATUS_COMPLETED))
  }

  @Test
  fun `a booking awaits whoever can move it forward, not whoever can only cancel`() {
    val requested = toolBooking(Booking.STATUS_REQUESTED)
    assertTrue(EscrowWorkflow.awaitsActionFrom(requested, owner))
    assertFalse(EscrowWorkflow.awaitsActionFrom(requested, neighbor))

    val held = toolBooking(Booking.STATUS_ESCROW_HELD)
    assertTrue(EscrowWorkflow.awaitsActionFrom(held, owner))
    assertTrue(EscrowWorkflow.awaitsActionFrom(held, neighbor))

    val inProgress = jobBooking(Booking.STATUS_IN_PROGRESS)
    assertTrue(EscrowWorkflow.awaitsActionFrom(inProgress, owner))
    assertFalse(EscrowWorkflow.awaitsActionFrom(inProgress, neighbor))

    assertFalse(EscrowWorkflow.awaitsActionFrom(toolBooking(Booking.STATUS_COMPLETED), owner))
  }

  @Test
  fun `completing and cancelling free the listing, the other steps keep it reserved`() {
    assertTrue(EscrowWorkflow.releasesListing(Booking.STATUS_COMPLETED))
    assertTrue(EscrowWorkflow.releasesListing(Booking.STATUS_CANCELLED))
    assertFalse(EscrowWorkflow.releasesListing(Booking.STATUS_ESCROW_HELD))
    assertFalse(EscrowWorkflow.releasesListing(Booking.STATUS_IN_PROGRESS))
  }
}
