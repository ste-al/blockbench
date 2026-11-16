package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.UserFacingException
import com.example.model.BlockCircle
import com.example.model.Booking
import com.example.model.InviteCodes
import com.example.model.Listing
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Drives [BlockBenchRepository] through the real Firebase SDK against the emulators, so every
 * write here has to get past `firestore.rules`. The rules themselves are tested exhaustively in
 * `firestore.test.js`; these tests make sure the app's own flows fit through them.
 */
class BlockBenchRepositoryTest : FirestoreEmulatorTestBase() {

  private lateinit var repo: BlockBenchRepository

  @Before
  fun createRepository() {
    repo = BlockBenchRepository(firestore, auth)
  }

  // ------------------------------------------------------------- circles

  @Test
  fun createBlockCircle_writesCircleInviteAndProfileTogether(): Unit = runBlocking {
    val aliceUid = signIn(ALICE)

    val circle = soon { repo.createBlockCircle("  Oak & 14th Block  ", 1.5) }

    assertEquals("Oak & 14th Block", circle.name)
    assertEquals(8, circle.inviteCode.length)
    assertTrue(circle.inviteCode.startsWith("OAK"))
    assertTrue(InviteCodes.isWellFormed(circle.inviteCode))

    val invite = soon { firestore.document("circleInvites/${circle.inviteCode}").get().await() }
    assertEquals(circle.id, invite.getString("circleId"))
    val profile = soon { repo.observeUserProfile(aliceUid).first { it?.activeCircleId == circle.id } }
    assertEquals(circle.inviteCode, profile?.activeInviteCode)
    val circles = soon { repo.observeMyCircles(aliceUid).first { it.isNotEmpty() } }
    assertEquals(listOf(circle.id), circles.map { it.id })
  }

  @Test
  fun createBlockCircle_createsTheProfileWhenTheBootstrapNeverRan(): Unit = runBlocking {
    // Sign in without ensureUserProfile(), as after a first launch that happened offline.
    val aliceUid = signInTestUser(ALICE.email)

    val circle = soon { repo.createBlockCircle("Cedar Block") }

    val profile = soon { repo.observeUserProfile(aliceUid).first { it != null } }
    assertEquals(circle.id, profile?.activeCircleId)
    assertEquals(aliceUid, profile?.uid)
  }

  @Test
  fun createBlockCircle_rejectsATooShortName(): Unit = runBlocking {
    signIn(ALICE)
    assertRefused("at least 2 characters") { repo.createBlockCircle(" a ") }
  }

  @Test
  fun joinByInviteCode_showsTheExistingFeedToTheNewMemberAtOnce(): Unit = runBlocking {
    signIn(ALICE)
    val circle = soon { repo.createBlockCircle("Oak Block") }
    val listingId = postTool(circle)

    val bobUid = signIn(BOB)
    // Typed the way people type codes: lower case, with a stray dash and spaces.
    val typedCode = " ${circle.inviteCode.take(3).lowercase()}-${circle.inviteCode.drop(3)} "
    val joined = soon { repo.joinCircleByInviteCode(typedCode) }

    assertEquals(circle.id, joined.id)
    assertTrue(bobUid in joined.memberUids)
    // No owner-side sync step in between: membership alone grants the feed.
    assertEquals(listOf(listingId), feed(circle.id).map { it.id })
    val profile = soon { repo.observeUserProfile(bobUid).first { it?.activeCircleId == circle.id } }
    assertEquals("Oak Block", profile?.activeCircleName)
  }

  @Test
  fun joinByInviteCode_isHarmlessForSomeoneWhoIsAlreadyAMember(): Unit = runBlocking {
    val aliceUid = signIn(ALICE)
    val oak = soon { repo.createBlockCircle("Oak Block") }
    val pine = soon { repo.createBlockCircle("Pine Block") }

    // The creator re-entering their own code simply switches back to that block.
    soon { repo.joinCircleByInviteCode(oak.inviteCode) }
    val aliceProfile = soon { repo.observeUserProfile(aliceUid).first { it?.activeCircleId == oak.id } }
    assertEquals(oak.id, aliceProfile?.activeCircleId)

    val bobUid = signIn(BOB)
    soon { repo.joinCircleByInviteCode(oak.inviteCode) }
    soon { repo.joinCircleByInviteCode(pine.inviteCode) }
    val again = soon { repo.joinCircleByInviteCode(oak.inviteCode) }
    assertEquals(1, again.memberUids.count { it == bobUid })
    val bobProfile = soon { repo.observeUserProfile(bobUid).first { it?.activeCircleId == oak.id } }
    assertEquals(oak.id, bobProfile?.activeCircleId)
  }

  @Test
  fun joinByInviteCode_explainsUnknownAndMalformedCodes(): Unit = runBlocking {
    signIn(ALICE)
    assertRefused("No block uses the invite code ZZZ99999") { repo.joinCircleByInviteCode("zzz99999") }
    assertRefused("letters and digits") { repo.joinCircleByInviteCode("ab") }
  }

  @Test
  fun leaveBlockCircle_removesMyListingsAndMyAccess_butKeepsMyOtherActiveBlock(): Unit = runBlocking {
    signIn(ALICE)
    val oak = soon { repo.createBlockCircle("Oak Block") }
    val aliceListingId = postTool(oak)

    val bobUid = signIn(BOB)
    soon { repo.joinCircleByInviteCode(oak.inviteCode) }
    val bobListingId = postTool(oak, title = "Bob's Hand Truck")
    val pine = soon { repo.createBlockCircle("Pine Block") }

    // Pine is Bob's active block; leaving Oak must not move him anywhere.
    val removed = soon { repo.leaveBlockCircle(oak, nextActiveCircle = null) }

    assertEquals(1, removed)
    val profile = soon { repo.observeUserProfile(bobUid).first { it != null } }
    assertEquals(pine.id, profile?.activeCircleId)
    assertEquals(listOf(pine.id), soon { repo.observeMyCircles(bobUid).first { it.size == 1 } }.map { it.id })
    assertPermissionDenied { repo.observeCircleListings(oak.id).first() }

    signIn(ALICE)
    val oakFeed = feed(oak.id).map { it.id }
    assertEquals(listOf(aliceListingId), oakFeed)
    assertFalse(bobListingId in oakFeed)
  }

  @Test
  fun leaveBlockCircle_fallsBackToAnotherBlockWhenLeavingTheActiveOne(): Unit = runBlocking {
    signIn(ALICE)
    val oak = soon { repo.createBlockCircle("Oak Block") }

    val bobUid = signIn(BOB)
    val pine = soon { repo.createBlockCircle("Pine Block") }
    soon { repo.joinCircleByInviteCode(oak.inviteCode) }

    soon { repo.leaveBlockCircle(oak, nextActiveCircle = pine) }

    val profile = soon { repo.observeUserProfile(bobUid).first { it?.activeCircleId == pine.id } }
    assertEquals(pine.inviteCode, profile?.activeInviteCode)
  }

  @Test
  fun leaveBlockCircle_isRefusedForTheCreatorAndWhileEscrowIsOpen(): Unit = runBlocking {
    signIn(ALICE)
    val oak = soon { repo.createBlockCircle("Oak Block") }
    val listingId = postTool(oak)
    assertRefused("can't leave") { repo.leaveBlockCircle(oak, nextActiveCircle = null) }

    signIn(BOB)
    soon { repo.joinCircleByInviteCode(oak.inviteCode) }
    val bookingId = soon { repo.createBookingRequest(listing(oak.id, listingId), "Bob", "") }
    assertRefused("open escrow bookings") { repo.leaveBlockCircle(oak, nextActiveCircle = null) }

    soon { repo.advanceBookingStatus(myBooking(bookingId), Booking.STATUS_CANCELLED) }
    assertEquals(0, soon { repo.leaveBlockCircle(oak, nextActiveCircle = null) })
  }

  // ------------------------------------------------------------ listings

  @Test
  fun updateListingDetails_andPauseAndDelete_roundTrip(): Unit = runBlocking {
    signIn(ALICE)
    val circle = soon { repo.createBlockCircle("Oak Block") }
    val listingId = postTool(circle, title = "Cordless Drill 18V", rateCents = 1500L)

    soon {
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
    val updated =
      soon {
          repo.observeCircleListings(circle.id).first { list ->
            list.any { it.id == listingId && it.title == "Brushless Drill 20V" }
          }
        }
        .first { it.id == listingId }
    assertEquals(1900L, updated.rateCents)
    assertFalse(updated.instantPickup)
    assertEquals(Listing.STATUS_AVAILABLE, updated.status)

    soon { repo.setListingPaused(listingId, paused = true) }
    assertEquals(Listing.STATUS_PAUSED, listingWithStatus(circle.id, listingId, Listing.STATUS_PAUSED).status)
    soon { repo.setListingPaused(listingId, paused = false) }
    listingWithStatus(circle.id, listingId, Listing.STATUS_AVAILABLE)

    soon { repo.deleteListing(listingId) }
    assertTrue(soon { repo.observeCircleListings(circle.id).first { it.isEmpty() } }.isEmpty())
    // Deleting something that is already gone is not an error.
    soon { repo.deleteListing(listingId) }
  }

  @Test
  fun createListing_validatesBeforeWriting_andDropsUnsupportedPhotos(): Unit = runBlocking {
    signIn(ALICE)
    val circle = soon { repo.createBlockCircle("Oak Block") }

    assertRefused("at least 3 characters") { postTool(circle, title = "ab") }
    assertRefused("between \$1 and \$5,000") { postTool(circle, rateCents = 50L) }
    assertRefused("between \$0 and \$5,000") { postTool(circle, depositCents = 900_000L) }

    // A picker URI is only readable on the phone that picked it; it must never be stored.
    val listingId =
      postTool(circle, photoUri = "content://media/picker/0/com.android.providers.media.photopicker/media/33")
    assertEquals("", listing(circle.id, listingId).photoUri)

    // Micro-jobs never carry a deposit, whatever the caller passes.
    val jobId = postJob(circle, depositCents = 2500L)
    assertEquals(0L, listing(circle.id, jobId).depositCents)
  }

  @Test
  fun observeCircleListings_isDeniedToOutsidersAndSignedOutCallers(): Unit = runBlocking {
    signIn(ALICE)
    val circle = soon { repo.createBlockCircle("Oak Block") }
    postTool(circle)

    signIn(CHARLIE)
    assertPermissionDenied { repo.observeCircleListings(circle.id).first() }

    auth.signOut()
    assertPermissionDenied { repo.observeCircleListings(circle.id).first() }
  }

  // ------------------------------------------------------------- bookings

  @Test
  fun toolRental_runsThroughTheWholeEscrowWorkflow(): Unit = runBlocking {
    val aliceUid = signIn(ALICE)
    val circle = soon { repo.createBlockCircle("Oak Block") }
    val listingId = postTool(circle, rateCents = 1200L, depositCents = 3000L)

    val bobUid = signIn(BOB)
    soon { repo.joinCircleByInviteCode(circle.inviteCode) }
    val bookingId =
      soon { repo.createBookingRequest(listing(circle.id, listingId), "Bob Carter", "Pickup at 5pm") }

    val requested = myBooking(bookingId)
    assertEquals(Booking.STATUS_REQUESTED, requested.status)
    assertEquals(bobUid, requested.requesterUid)
    assertEquals(aliceUid, requested.providerUid)
    assertEquals(aliceUid, requested.listingOwnerUid)
    assertEquals(1200L, requested.grossAmountCents)
    assertEquals(3000L, requested.depositCents)
    assertEquals(60L, requested.platformFeeCents)
    assertEquals(1140L, requested.providerNetCents)
    val reserved = listingWithStatus(circle.id, listingId, Listing.STATUS_RESERVED)
    assertEquals(bookingId, reserved.activeBookingId)

    // Only Alice, whose ladder it is, can approve; Bob gets told so instead of a raw denial.
    assertRefused("Only Alice Miller can approve") {
      repo.advanceBookingStatus(requested, Booking.STATUS_ESCROW_HELD)
    }

    signIn(ALICE)
    soon { repo.advanceBookingStatus(myBooking(bookingId), Booking.STATUS_ESCROW_HELD, "Side gate 4812") }
    assertEquals("Side gate 4812", bookingWithStatus(bookingId, Booking.STATUS_ESCROW_HELD).notes)

    signIn(BOB)
    soon { repo.advanceBookingStatus(myBooking(bookingId), Booking.STATUS_IN_PROGRESS) }
    val inProgress = bookingWithStatus(bookingId, Booking.STATUS_IN_PROGRESS)
    assertRefused("Only Alice Miller can confirm completion") {
      repo.advanceBookingStatus(inProgress, Booking.STATUS_COMPLETED)
    }
    assertRefused("can no longer be cancelled") {
      repo.advanceBookingStatus(inProgress, Booking.STATUS_CANCELLED)
    }

    signIn(ALICE)
    soon { repo.advanceBookingStatus(myBooking(bookingId), Booking.STATUS_COMPLETED) }
    val completed = bookingWithStatus(bookingId, Booking.STATUS_COMPLETED)
    assertRefused("already been completed") {
      repo.advanceBookingStatus(completed, Booking.STATUS_CANCELLED)
    }
    val freed = listingWithStatus(circle.id, listingId, Listing.STATUS_AVAILABLE)
    assertEquals("", freed.activeBookingId)

    // The profile summary follows the ledger, and syncing twice changes nothing.
    repeat(2) { soon { repo.syncCompletedEarningsToProfile(1L, 1140L, 60L) } }
    val profile = soon { repo.observeUserProfile(aliceUid).first { (it?.completedHandoffs ?: 0L) >= 1L } }
    assertEquals(1L, profile?.completedHandoffs)
    assertEquals(1L, profile?.onTimeReturns)
    assertEquals(1140L, profile?.totalEarnedCents)
    assertEquals(60L, profile?.totalFeesSavedOrPaidCents)
  }

  @Test
  fun microJob_isClaimedByTheWorkerAndSettledByThePoster(): Unit = runBlocking {
    val aliceUid = signIn(ALICE)
    val circle = soon { repo.createBlockCircle("Cedar Block") }
    val jobId = postJob(circle)

    val bobUid = signIn(BOB)
    soon { repo.joinCircleByInviteCode(circle.inviteCode) }
    val bookingId = soon { repo.createBookingRequest(listing(circle.id, jobId), "Bob Carter", "Noon works") }

    val claim = myBooking(bookingId)
    assertEquals(aliceUid, claim.requesterUid)
    assertEquals(bobUid, claim.providerUid)
    assertEquals(aliceUid, claim.listingOwnerUid)
    assertEquals(125L, claim.platformFeeCents)
    assertEquals(2375L, claim.providerNetCents)
    assertRefused("Only Alice Miller can approve") {
      repo.advanceBookingStatus(claim, Booking.STATUS_ESCROW_HELD)
    }

    signIn(ALICE)
    soon { repo.advanceBookingStatus(myBooking(bookingId), Booking.STATUS_ESCROW_HELD) }
    soon { repo.advanceBookingStatus(myBooking(bookingId), Booking.STATUS_IN_PROGRESS) }
    soon { repo.advanceBookingStatus(myBooking(bookingId), Booking.STATUS_COMPLETED) }
    bookingWithStatus(bookingId, Booking.STATUS_COMPLETED)
    listingWithStatus(circle.id, jobId, Listing.STATUS_AVAILABLE)
  }

  @Test
  fun booking_cannotBeDoubledUp_orMadeOnStaleTerms_orOnOnesOwnListing(): Unit = runBlocking {
    signIn(ALICE)
    val circle = soon { repo.createBlockCircle("Oak Block") }
    val listingId = postTool(circle, rateCents = 1200L)
    val asAliceSeesIt = listing(circle.id, listingId)
    assertRefused("your own listing") { repo.createBookingRequest(asAliceSeesIt, "Alice Miller", "") }

    signIn(BOB)
    soon { repo.joinCircleByInviteCode(circle.inviteCode) }
    val asBobSawIt = listing(circle.id, listingId)

    // Alice raises the price while Bob still has the old listing on screen.
    signIn(ALICE)
    soon {
      repo.updateListingDetails(
        listingId = listingId,
        title = asAliceSeesIt.title,
        description = asAliceSeesIt.description,
        category = asAliceSeesIt.category,
        rateCents = 2000L,
        depositCents = asAliceSeesIt.depositCents,
        distanceMiles = asAliceSeesIt.distanceMiles,
        instantPickup = asAliceSeesIt.instantPickup,
        photoUri = asAliceSeesIt.photoUri,
      )
    }

    signIn(BOB)
    assertRefused("changed the price") { repo.createBookingRequest(asBobSawIt, "Bob Carter", "") }
    val current = soon { repo.observeCircleListings(circle.id).first { l -> l.any { it.rateCents == 2000L } } }.first()
    val bookingId = soon { repo.createBookingRequest(current, "Bob Carter", "") }

    // Charlie still sees it as available, but the server knows better.
    signIn(CHARLIE)
    soon { repo.joinCircleByInviteCode(circle.inviteCode) }
    assertRefused("just reserved this") { repo.createBookingRequest(current, "Charlie", "") }
    assertTrue(soon { repo.observeMyBookings(auth.currentUser!!.uid).first() }.none { it.id == bookingId })
  }

  @Test
  fun reservedListing_cannotBePausedOrDeleted_untilItsBookingIsCancelled(): Unit = runBlocking {
    signIn(ALICE)
    val circle = soon { repo.createBlockCircle("Oak Block") }
    val listingId = postTool(circle)

    signIn(BOB)
    soon { repo.joinCircleByInviteCode(circle.inviteCode) }
    val bookingId = soon { repo.createBookingRequest(listing(circle.id, listingId), "Bob Carter", "") }

    signIn(ALICE)
    assertRefused("booking in progress") { repo.setListingPaused(listingId, paused = true) }
    assertRefused("booking in progress") { repo.deleteListing(listingId) }

    // Declining the request frees the listing in the same transaction.
    soon { repo.advanceBookingStatus(myBooking(bookingId), Booking.STATUS_CANCELLED) }
    bookingWithStatus(bookingId, Booking.STATUS_CANCELLED)
    listingWithStatus(circle.id, listingId, Listing.STATUS_AVAILABLE)
    soon { repo.deleteListing(listingId) }
  }

  @Test
  fun observeMyBookings_showsABookingToItsTwoParticipantsOnly(): Unit = runBlocking {
    val aliceUid = signIn(ALICE)
    val circle = soon { repo.createBlockCircle("Cedar Block") }
    val jobId = postJob(circle)

    signIn(BOB)
    soon { repo.joinCircleByInviteCode(circle.inviteCode) }
    val bookingId = soon { repo.createBookingRequest(listing(circle.id, jobId), "Bob Carter", "") }

    val charlieUid = signIn(CHARLIE)
    assertTrue(soon { repo.observeMyBookings(charlieUid).first() }.none { it.id == bookingId })
    // Asking for someone else's ledger is refused outright.
    assertPermissionDenied { repo.observeMyBookings(aliceUid).first() }

    signIn(ALICE)
    assertEquals(bookingId, myBooking(bookingId).id)
  }

  // ----------------------------------------------- data from older versions

  @Test
  fun bookingsAndReservationsFromBeforeReservationTracking_canStillBeSettled(): Unit = runBlocking {
    val aliceUid = signIn(ALICE)
    val circle = soon { repo.createBlockCircle("Oak Block") }
    val bobUid = signIn(BOB)
    soon { repo.joinCircleByInviteCode(circle.inviteCode) }

    // What the previous app version left behind: a listing marked reserved with no pointer to
    // the booking that holds it, a member list copied onto it, and a device-local photo URI.
    val longAgo = Timestamp(1_600_000_000, 0)
    adminSetDocument(
      "listings/legacy_listing",
      mapOf(
        "circleId" to circle.id,
        "ownerUid" to aliceUid,
        "ownerName" to "Alice Miller",
        "ownerTrustScore" to 5.0,
        "memberUids" to listOf(aliceUid, bobUid),
        "kind" to Listing.KIND_TOOL,
        "title" to "Old Pressure Washer",
        "description" to "",
        "category" to "Yard & Outdoor",
        "rateCents" to 1800L,
        "depositCents" to 4000L,
        "distanceMiles" to 0.3,
        "instantPickup" to true,
        "photoUri" to "content://media/picker/0/x/media/33",
        "status" to Listing.STATUS_RESERVED,
        "createdAt" to longAgo,
        "updatedAt" to longAgo,
      ),
    )
    adminSetDocument(
      "bookings/legacy_booking",
      mapOf(
        "listingId" to "legacy_listing",
        "listingTitle" to "Old Pressure Washer",
        "listingKind" to Listing.KIND_TOOL,
        "circleId" to circle.id,
        "requesterUid" to bobUid,
        "requesterName" to "Bob Carter",
        "providerUid" to aliceUid,
        "providerName" to "Alice Miller",
        "participantUids" to listOf(bobUid, aliceUid),
        "grossAmountCents" to 1800L,
        "depositCents" to 4000L,
        "platformFeeCents" to 90L,
        "providerNetCents" to 1710L,
        "status" to Booking.STATUS_REQUESTED,
        "notes" to "",
        "createdAt" to longAgo,
        "updatedAt" to longAgo,
      ),
    )

    // Bob cancels: the listing has nothing to release, so the booking is settled on its own.
    soon { repo.advanceBookingStatus(myBooking("legacy_booking"), Booking.STATUS_CANCELLED) }
    bookingWithStatus("legacy_booking", Booking.STATUS_CANCELLED)

    // The listing is left reserved with nothing pointing at it; its owner can clear that.
    signIn(ALICE)
    val stale = listing(circle.id, "legacy_listing")
    assertTrue(stale.hasUntrackedReservation)
    soon { repo.setListingPaused("legacy_listing", paused = false) }
    val cleared = listingWithStatus(circle.id, "legacy_listing", Listing.STATUS_AVAILABLE)

    // ...and from then on it behaves like any other listing.
    signIn(BOB)
    val bookingId = soon { repo.createBookingRequest(cleared, "Bob Carter", "") }
    assertNotEquals("legacy_booking", bookingId)
    assertEquals(bookingId, listingWithStatus(circle.id, "legacy_listing", Listing.STATUS_RESERVED).activeBookingId)
  }

  // ------------------------------------------------------------- helpers

  private data class TestUser(val email: String, val name: String)

  private suspend fun <T> soon(block: suspend () -> T): T = withTimeout(TIMEOUT_MS) { block() }

  /** Signs in as [user] and makes sure their profile exists, like the app does on launch. */
  private suspend fun signIn(user: TestUser): String {
    val uid = signInTestUser(user.email)
    soon { repo.ensureUserProfile(user.name) }
    return uid
  }

  private suspend fun postTool(
    circle: BlockCircle,
    title: String = "Extension Ladder 20ft",
    rateCents: Long = 1200L,
    depositCents: Long = 3000L,
    photoUri: String = "",
  ): String = soon {
    repo.createListing(
      circle = circle,
      ownerName = "Alice Miller",
      ownerTrustScore = 5.0,
      kind = Listing.KIND_TOOL,
      title = title,
      description = "Fiberglass step/extension ladder ready on side porch.",
      category = "Ladders & Reach",
      rateCents = rateCents,
      depositCents = depositCents,
      distanceMiles = 0.2,
      instantPickup = true,
      photoUri = photoUri,
    )
  }

  private suspend fun postJob(circle: BlockCircle, depositCents: Long = 0L): String = soon {
    repo.createListing(
      circle = circle,
      ownerName = "Alice Miller",
      ownerTrustScore = 5.0,
      kind = Listing.KIND_MICRO_JOB,
      title = "Fix Backyard Gate Latch",
      description = "Need screws tightened and latch aligned.",
      category = "Quick Fix",
      rateCents = 2500L,
      depositCents = depositCents,
      distanceMiles = 0.15,
      instantPickup = true,
    )
  }

  private suspend fun feed(circleId: String): List<Listing> = soon {
    repo.observeCircleListings(circleId).first { it.isNotEmpty() }
  }

  private suspend fun listing(circleId: String, listingId: String): Listing =
    soon { repo.observeCircleListings(circleId).first { list -> list.any { it.id == listingId } } }
      .first { it.id == listingId }

  private suspend fun listingWithStatus(circleId: String, listingId: String, status: String): Listing =
    soon {
        repo.observeCircleListings(circleId).first { list ->
          list.any { it.id == listingId && it.status == status }
        }
      }
      .first { it.id == listingId }

  /** The booking as the signed-in user currently sees it in their ledger. */
  private suspend fun myBooking(bookingId: String): Booking {
    val uid = checkNotNull(auth.currentUser).uid
    return soon { repo.observeMyBookings(uid).first { list -> list.any { it.id == bookingId } } }
      .first { it.id == bookingId }
  }

  private suspend fun bookingWithStatus(bookingId: String, status: String): Booking {
    val uid = checkNotNull(auth.currentUser).uid
    return soon {
        repo.observeMyBookings(uid).first { list ->
          list.any { it.id == bookingId && it.status == status }
        }
      }
      .first { it.id == bookingId }
  }

  /** Asserts that [block] fails with a message meant for the user that contains [fragment]. */
  private suspend fun assertRefused(fragment: String, block: suspend () -> Unit) {
    try {
      soon { block() }
    } catch (e: UserFacingException) {
      assertTrue("Unexpected message: ${e.message}", e.message.contains(fragment))
      return
    }
    fail("Expected to be refused with a message containing \"$fragment\"")
  }

  private suspend fun assertPermissionDenied(block: suspend () -> Unit) {
    val thrown =
      try {
        soon { block() }
        null
      } catch (e: Exception) {
        e
      }
    val firestoreException =
      checkNotNull(thrown?.unwrapFirestoreException()) {
        "Expected PERMISSION_DENIED from Firestore, got $thrown"
      }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  private companion object {
    val ALICE = TestUser("alice_block@test.com", "Alice Miller")
    val BOB = TestUser("bob_block@test.com", "Bob Carter")
    val CHARLIE = TestUser("charlie_block@test.com", "Charlie Diaz")
    const val TIMEOUT_MS = 8000L
  }
}
