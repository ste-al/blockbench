package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.Booking
import com.example.model.Listing
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockBenchRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createCircleAndListing_andJoinByInviteCode_sharesFeedBetweenNeighbors() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = BlockBenchRepository(firestore)

    withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserProfile("Alice Miller", "Oak St") }
    val circle =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createBlockCircle("Oak & 14th Block", 1.5)
      }
    val listingId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createListing(
          circle = circle,
          ownerName = "Alice Miller",
          ownerTrustScore = 5.0,
          kind = Listing.KIND_TOOL,
          title = "Extension Ladder 20ft",
          description = "Fiberglass step/extension ladder ready on side porch.",
          category = "Ladders & Reach",
          rateCents = 1200L,
          depositCents = 3000L,
          distanceMiles = 0.2,
          instantPickup = true,
        )
      }

    // Bob joins via the invite code and syncs listing visibility
    val bobUid = signInTestUser(BOB_EMAIL)
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserProfile("Bob Carter", "Oak St") }
    val joinedCircle =
      withTimeout(DEFAULT_TIMEOUT_MS) { repo.joinCircleByInviteCode(circle.inviteCode) }
    assertEquals(circle.id, joinedCircle.id)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.syncListingsMembershipForCircle(circle.id, listOf(listingId))
    }

    val bobFeed =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeCircleListings(bobUid).first { list -> list.any { it.id == listingId } }
      }
    val sharedListing = bobFeed.first { it.id == listingId }
    assertEquals("Extension Ladder 20ft", sharedListing.title)

    // Bob books Alice's ladder and walks through escrow lifecycle
    val bookingId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createBookingRequest(sharedListing, "Bob Carter", "Pickup at 5pm")
      }
    val bobBookings =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeMyBookings(bobUid).first { list -> list.any { it.id == bookingId } }
      }
    val createdBooking = bobBookings.first { it.id == bookingId }
    assertEquals(Booking.STATUS_REQUESTED, createdBooking.status)
    assertEquals(60L, createdBooking.platformFeeCents)
    assertEquals(1140L, createdBooking.providerNetCents)

    // Advance booking through escrow -> in_progress -> completed
    signInTestUser(ALICE_EMAIL)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.advanceBookingStatus(createdBooking, Booking.STATUS_ESCROW_HELD)
    }
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.advanceBookingStatus(
        createdBooking.copy(status = Booking.STATUS_ESCROW_HELD),
        Booking.STATUS_IN_PROGRESS,
      )
    }
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.advanceBookingStatus(
        createdBooking.copy(status = Booking.STATUS_IN_PROGRESS),
        Booking.STATUS_COMPLETED,
      )
    }

    val aliceProfile =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeUserProfile(aliceUid).first { it != null && it.completedHandoffs >= 1L }
      }
    assertTrue((aliceProfile?.totalEarnedCents ?: 0L) >= 1140L)
  }

  @Test
  fun observeListings_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = BlockBenchRepository(firestore)

    var thrown: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeCircleListings("fake_uid").first() }
    } catch (t: Throwable) {
      thrown = t
    }
    val firestoreException =
      checkNotNull(thrown?.unwrapFirestoreException()) {
        "Expected FirebaseFirestoreException for unauthenticated list, got $thrown"
      }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  @Test
  fun observeBookings_unrelatedThirdUser_cannotSeeParticipantBooking() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = BlockBenchRepository(firestore)
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserProfile("Alice", "Oak St") }
    val circle = withTimeout(DEFAULT_TIMEOUT_MS) { repo.createBlockCircle("Cedar Block", 1.0) }
    val listingId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createListing(
          circle = circle,
          ownerName = "Alice",
          ownerTrustScore = 5.0,
          kind = Listing.KIND_MICRO_JOB,
          title = "Fix Backyard Gate Latch",
          description = "Need screws tightened and latch aligned.",
          category = "Quick Fix",
          rateCents = 2500L,
          depositCents = 0L,
          distanceMiles = 0.15,
          instantPickup = true,
        )
      }

    val bobUid = signInTestUser(BOB_EMAIL)
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserProfile("Bob", "Oak St") }
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.joinCircleByInviteCode(circle.inviteCode) }
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.syncListingsMembershipForCircle(circle.id, listOf(listingId))
    }
    val listing =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeCircleListings(bobUid).first { it.any { item -> item.id == listingId } }
      }.first { it.id == listingId }

    val bookingId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createBookingRequest(listing, "Bob", "Can drop by at noon")
      }

    val charlieUid = signInTestUser(CHARLIE_EMAIL)
    val charlieBookings =
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeMyBookings(charlieUid).first() }
    assertTrue(charlieBookings.none { it.id == bookingId })
  }

  @Test
  fun updateListingDetails_andSwitchOrLeaveCircle_persistsCleanly() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = BlockBenchRepository(firestore)
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserProfile("Alice", "Oak St") }
    val oakCircle = withTimeout(DEFAULT_TIMEOUT_MS) { repo.createBlockCircle("Oak Block", 1.5) }
    val listingId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createListing(
          circle = oakCircle,
          ownerName = "Alice",
          ownerTrustScore = 5.0,
          kind = Listing.KIND_TOOL,
          title = "Cordless Drill 18V",
          description = "Includes two batteries.",
          category = "Power Tools",
          rateCents = 1500L,
          depositCents = 2500L,
          distanceMiles = 0.25,
          instantPickup = true,
        )
      }

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateListingDetails(
        listingId = listingId,
        title = "Brushless Drill 20V",
        description = "Includes charger & 2 batteries.",
        category = "Power Tools",
        rateCents = 1900L,
        depositCents = 3000L,
        distanceMiles = 0.6,
        instantPickup = false,
        photoUri = "",
      )
    }

    val aliceUid = auth.currentUser!!.uid
    val updatedFeed =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeCircleListings(aliceUid).first { list ->
          list.any { it.id == listingId && it.title == "Brushless Drill 20V" }
        }
      }
    assertEquals(1900L, updatedFeed.first { it.id == listingId }.rateCents)

    // Bob joins Oak Block, switches to a second circle, then leaves Oak Block
    val bobUid = signInTestUser(BOB_EMAIL)
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserProfile("Bob", "Oak St") }
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.joinCircleByInviteCode(oakCircle.inviteCode) }
    val pineCircle = withTimeout(DEFAULT_TIMEOUT_MS) { repo.createBlockCircle("Pine Block", 2.0) }
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.switchActiveCircle(oakCircle) }
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.leaveBlockCircle(oakCircle, pineCircle) }

    val bobProfile =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeUserProfile(bobUid).first { it?.activeCircleId == pineCircle.id }
      }
    assertEquals(pineCircle.id, bobProfile?.activeCircleId)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_block@test.com"
    const val BOB_EMAIL = "bob_block@test.com"
    const val CHARLIE_EMAIL = "charlie_block@test.com"
    const val DEFAULT_TIMEOUT_MS = 6000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
