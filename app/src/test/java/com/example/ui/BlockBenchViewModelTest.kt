package com.example.ui

import androidx.lifecycle.viewModelScope
import com.example.data.UserFacingException
import com.example.data.repository.BlockBenchDataSource
import com.example.model.BlockCircle
import com.example.model.Booking
import com.example.model.BookingTotals
import com.example.model.Listing
import com.example.model.ListingDraft
import com.example.model.UiState
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric only for android.util.Log and FirebaseFirestoreException; no Firebase is involved. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BlockBenchViewModelTest {

  private val dispatcher = UnconfinedTestDispatcher()
  private val data = FakeDataSource()
  private lateinit var viewModel: BlockBenchViewModel

  private val oak = BlockCircle(id = "oak", creatorUid = "alice", name = "Oak Block", inviteCode = "OAK7K2MQ", memberUids = listOf("alice", ME))
  private val pine = BlockCircle(id = "pine", creatorUid = ME, name = "Pine Block", inviteCode = "PIN4X9QH", memberUids = listOf(ME))
  private val profile = UserProfile(id = ME, uid = ME, displayName = "Bob Carter", activeCircleId = "oak", trustScore = 4.5)

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
    Dispatchers.resetMain()
  }

  private fun createViewModel(): BlockBenchViewModel {
    viewModel = BlockBenchViewModel(data, currentUserId = ME, initialDisplayName = "Bob")
    return viewModel
  }

  /** Records every value [flow] emits, the way a screen collecting it would see them. */
  private fun <T> TestScope.record(flow: Flow<T>): List<T> {
    val values = mutableListOf<T>()
    backgroundScope.launch(dispatcher) { flow.collect { values += it } }
    return values
  }

  private fun offline() = FirebaseFirestoreException("backend detail", FirebaseFirestoreException.Code.UNAVAILABLE)

  // ----------------------------------------------------------- session state

  @Test
  fun `the session is loading until both profile and blocks arrived, then ready in one step`() = runTest(dispatcher) {
    val states = record(createViewModel().sessionState)

    data.profile.emit(Result.success(profile.copy(activeCircleId = "pine")))
    assertEquals(listOf<SessionState>(SessionState.Loading), states)

    data.circles.emit(Result.success(listOf(oak, pine)))

    // Never a "ready, but no active block" in between, which would flash the onboarding screen.
    assertEquals(
      listOf(SessionState.Loading, SessionState.Ready(profile.copy(activeCircleId = "pine"), listOf(oak, pine), pine)),
      states,
    )
  }

  @Test
  fun `a new user with no profile and no block is ready for onboarding`() = runTest(dispatcher) {
    val states = record(createViewModel().sessionState)

    data.profile.emit(Result.success(null))
    data.circles.emit(Result.success(emptyList()))

    assertEquals(SessionState.Ready(profile = null, circles = emptyList(), activeCircle = null), states.last())
    assertEquals(listOf("Bob"), data.ensuredProfiles)
  }

  @Test
  fun `a block the profile no longer points at falls back to the first block`() = runTest(dispatcher) {
    val states = record(createViewModel().sessionState)

    data.profile.emit(Result.success(profile.copy(activeCircleId = "left_long_ago")))
    data.circles.emit(Result.success(listOf(oak, pine)))

    assertEquals(oak, (states.last() as SessionState.Ready).activeCircle)
  }

  @Test
  fun `a failed listener becomes a readable failure, and retry starts over`() = runTest(dispatcher) {
    val states = record(createViewModel().sessionState)
    data.profile.emit(Result.success(profile))
    data.circles.emit(Result.failure(offline()))

    val failure = states.last() as SessionState.Failed
    assertTrue(failure.message.startsWith("Could not load your blocks."))
    assertTrue(failure.message.contains("offline"))
    assertFalse(failure.message.contains("backend detail"))

    // Retrying goes back to loading instead of leaving the old error on screen...
    data.circles.resetReplayCache()
    viewModel.retry()
    assertEquals(SessionState.Loading, states.last())
    assertEquals(2, data.ensuredProfiles.size)

    // ...and recovers as soon as the listener delivers again.
    data.circles.emit(Result.success(listOf(oak)))
    assertEquals(SessionState.Ready(profile, listOf(oak), oak), states.last())
  }

  // ------------------------------------------------------------------- feed

  @Test
  fun `the feed belongs to the active block and reloads when that changes`() = runTest(dispatcher) {
    val oakListing = Listing(id = "ladder", circleId = "oak", title = "Ladder")
    val pineListing = Listing(id = "drill", circleId = "pine", title = "Drill")
    createViewModel()
    record(viewModel.sessionState)
    val feed = record(viewModel.listingsState)
    val visible = record(viewModel.filteredListings)

    data.profile.emit(Result.success(profile))
    data.circles.emit(Result.success(listOf(oak, pine)))
    data.listingsOf("oak").emit(Result.success(listOf(oakListing)))
    assertEquals(UiState.Success(listOf(oakListing)), feed.last())
    assertEquals(listOf(oakListing), visible.last())

    val before = feed.size
    data.profile.emit(Result.success(profile.copy(activeCircleId = "pine")))
    // Oak's listings are not left on screen under Pine's name while Pine loads.
    assertEquals(UiState.Loading, feed.last())
    assertEquals(emptyList<Listing>(), visible.last())

    data.listingsOf("pine").emit(Result.success(listOf(pineListing)))
    assertEquals(UiState.Success(listOf(pineListing)), feed.last())
    assertTrue(feed.size > before)
  }

  @Test
  fun `a feed that fails to load is an error, not an empty block`() = runTest(dispatcher) {
    createViewModel()
    record(viewModel.sessionState)
    val feed = record(viewModel.listingsState)
    data.profile.emit(Result.success(profile))
    data.circles.emit(Result.success(listOf(oak)))

    data.listingsOf("oak").emit(Result.failure(offline()))

    val error = feed.last() as UiState.Error
    assertTrue(error.message.startsWith("Could not load this block's listings."))
  }

  @Test
  fun `filters narrow the feed and can be cleared in one go`() = runTest(dispatcher) {
    val ladder = Listing(id = "ladder", circleId = "oak", kind = Listing.KIND_TOOL, title = "Ladder", category = "Ladders & Reach")
    val latch = Listing(id = "latch", circleId = "oak", kind = Listing.KIND_MICRO_JOB, title = "Fix latch", category = "Quick Fix")
    createViewModel()
    record(viewModel.sessionState)
    val visible = record(viewModel.filteredListings)
    data.profile.emit(Result.success(profile))
    data.circles.emit(Result.success(listOf(oak)))
    data.listingsOf("oak").emit(Result.success(listOf(ladder, latch)))

    viewModel.setKindFilter(FeedFilter.MICRO_JOBS)
    assertEquals(listOf(latch), visible.last())

    viewModel.toggleCategoryFilter("Ladders & Reach")
    assertEquals(emptyList<Listing>(), visible.last())
    viewModel.toggleCategoryFilter("Ladders & Reach")
    assertEquals(listOf(latch), visible.last())

    viewModel.setSearchQuery("ladder")
    assertEquals(emptyList<Listing>(), visible.last())

    viewModel.clearFilters()
    assertEquals(listOf(ladder, latch), visible.last())
    assertEquals(FeedFiltersState(), viewModel.filters.value)
  }

  // ------------------------------------------------------------- operations

  @Test
  fun `a successful write reports back, calls onDone and clears the busy flag`() = runTest(dispatcher) {
    createViewModel()
    val inFlight = CompletableDeferred<Unit>()
    data.hold = inFlight
    var done = 0

    viewModel.createBlockCircle("Oak Block", 1.5) { done++ }
    assertTrue(viewModel.isBusy.value)
    assertEquals(0, done)

    inFlight.complete(Unit)
    assertFalse(viewModel.isBusy.value)
    assertEquals(1, done)
    assertEquals("Block \"Oak Block\" created! Invite code: OAK7K2MQ", viewModel.feedbackMessage.value)

    viewModel.clearFeedback()
    assertNull(viewModel.feedbackMessage.value)
  }

  @Test
  fun `a refused write shows its own message and does not call onDone`() = runTest(dispatcher) {
    createViewModel()
    data.failure = UserFacingException("No block uses the invite code ZZZ99999. Check it and try again.")
    var done = 0

    viewModel.joinBlockCircle("zzz99999") { done++ }

    assertEquals(0, done)
    assertFalse(viewModel.isBusy.value)
    assertEquals("No block uses the invite code ZZZ99999. Check it and try again.", viewModel.feedbackMessage.value)
  }

  @Test
  fun `a backend failure is explained without leaking the raw error`() = runTest(dispatcher) {
    createViewModel()
    data.failure = offline()

    viewModel.switchActiveCircle(pine)

    val message = viewModel.feedbackMessage.value!!
    assertTrue(message.startsWith("Could not switch blocks."))
    assertTrue(message.contains("offline"))
    assertFalse(message.contains("backend detail"))
  }

  @Test
  fun `the app stays busy until every overlapping write has finished`() = runTest(dispatcher) {
    createViewModel()
    val first = CompletableDeferred<Unit>()
    val second = CompletableDeferred<Unit>()

    data.hold = first
    viewModel.switchActiveCircle(oak)
    data.hold = second
    viewModel.switchActiveCircle(pine)

    first.complete(Unit)
    assertTrue(viewModel.isBusy.value)
    second.complete(Unit)
    assertFalse(viewModel.isBusy.value)
  }

  @Test
  fun `a failed profile bootstrap is reported once and does not block later writes`() = runTest(dispatcher) {
    data.failure = offline()
    createViewModel()

    assertTrue(viewModel.feedbackMessage.value!!.startsWith("Could not set up your profile yet."))

    viewModel.clearFeedback()
    viewModel.switchActiveCircle(oak)
    assertEquals("Switched to Oak Block", viewModel.feedbackMessage.value)
  }

  // ----------------------------------------------------------------- blocks

  @Test
  fun `leaving a block names the block to fall back to and reports removed listings`() = runTest(dispatcher) {
    createViewModel()
    record(viewModel.sessionState)
    data.profile.emit(Result.success(profile))
    data.circles.emit(Result.success(listOf(oak, pine)))
    data.removedOnLeave = 2

    viewModel.leaveBlockCircle(oak)

    assertEquals(listOf(oak to pine), data.left)
    assertEquals("You left Oak Block. Your 2 listings there were removed.", viewModel.feedbackMessage.value)
  }

  @Test
  fun `leaving the only block leaves nothing to fall back to`() = runTest(dispatcher) {
    createViewModel()
    record(viewModel.sessionState)
    data.profile.emit(Result.success(profile))
    data.circles.emit(Result.success(listOf(oak)))

    viewModel.leaveBlockCircle(oak)

    assertEquals(listOf<Pair<BlockCircle, BlockCircle?>>(oak to null), data.left)
    assertEquals("You left Oak Block.", viewModel.feedbackMessage.value)
  }

  // --------------------------------------------------------------- listings

  private val draft =
    ListingDraft(
      kind = Listing.KIND_TOOL,
      title = "Extension Ladder",
      description = "20 ft",
      category = "Ladders & Reach",
      rateCents = 1200L,
      depositCents = 3000L,
      distanceMiles = 0.2,
      instantPickup = true,
      photoUri = "",
    )

  @Test
  fun `a listing is published to the active block under the profile's name and score`() = runTest(dispatcher) {
    createViewModel()
    record(viewModel.sessionState)
    data.profile.emit(Result.success(profile))
    data.circles.emit(Result.success(listOf(oak, pine)))
    var done = 0

    viewModel.createListing(draft) { done++ }

    assertEquals(listOf(FakeDataSource.CreatedListing(oak, "Bob Carter", 4.5, draft.title, draft.rateCents)), data.createdListings)
    assertEquals(1, done)
    assertEquals("Published to Oak Block!", viewModel.feedbackMessage.value)
  }

  @Test
  fun `without an active block nothing is published`() = runTest(dispatcher) {
    createViewModel()
    var done = 0

    viewModel.createListing(draft) { done++ }

    assertTrue(data.createdListings.isEmpty())
    assertEquals(0, done)
    assertEquals("Could not publish the listing.", viewModel.feedbackMessage.value)
  }

  @Test
  fun `the pause button pauses what is available and resumes everything else`() = runTest(dispatcher) {
    createViewModel()

    viewModel.toggleListingPause(Listing(id = "a", status = Listing.STATUS_AVAILABLE))
    assertEquals("Listing paused.", viewModel.feedbackMessage.value)
    viewModel.toggleListingPause(Listing(id = "b", status = Listing.STATUS_PAUSED))
    assertEquals("Listing is live again.", viewModel.feedbackMessage.value)
    // A stale reservation from an older app version is cleared, not paused.
    viewModel.toggleListingPause(Listing(id = "c", status = Listing.STATUS_RESERVED))

    assertEquals(listOf("a" to true, "b" to false, "c" to false), data.pauses)
  }

  @Test
  fun `booking uses the profile's name and words the confirmation for the kind of listing`() = runTest(dispatcher) {
    createViewModel()
    record(viewModel.sessionState)
    data.profile.emit(Result.success(profile))
    data.circles.emit(Result.success(listOf(oak)))
    val tool = Listing(id = "ladder", ownerName = "Alice Miller", kind = Listing.KIND_TOOL)
    val job = Listing(id = "latch", ownerName = "Alice Miller", kind = Listing.KIND_MICRO_JOB)

    viewModel.bookOrClaimListing(tool, "5pm")
    assertEquals("Request sent! Alice Miller can now approve it in Escrow.", viewModel.feedbackMessage.value)
    viewModel.bookOrClaimListing(job, "")
    assertEquals("Job claimed! Alice Miller can now approve it in Escrow.", viewModel.feedbackMessage.value)

    assertEquals(listOf(Triple("ladder", "Bob Carter", "5pm"), Triple("latch", "Bob Carter", "")), data.bookings)
  }

  // ----------------------------------------------------------------- ledger

  private fun completed(id: String, iProvide: Boolean) =
    Booking(
      id = id,
      requesterUid = if (iProvide) "alice" else ME,
      providerUid = if (iProvide) ME else "alice",
      providerNetCents = 1710L,
      platformFeeCents = 90L,
      status = Booking.STATUS_COMPLETED,
    )

  @Test
  fun `totals come from completed bookings and the profile summary is brought up to them once`() = runTest(dispatcher) {
    createViewModel()
    val totals = record(viewModel.ledgerTotals)
    data.profile.emit(Result.success(profile))
    val bookings = listOf(completed("b1", iProvide = true), completed("b2", iProvide = false))

    data.myBookings.emit(Result.success(bookings))
    // Snapshots arrive in bursts; the same totals must not be written again and again.
    data.myBookings.emit(Result.success(bookings.toList()))
    data.profile.emit(Result.success(profile.copy(displayName = "Bob C.")))

    val expected = BookingTotals(completedHandoffs = 2L, earnedCents = 1710L, feesCents = 180L)
    assertEquals(expected, totals.last())
    assertEquals(listOf(expected), data.syncedTotals)
  }

  @Test
  fun `a profile that is already up to date is not written, and never shown as less`() = runTest(dispatcher) {
    createViewModel()
    val totals = record(viewModel.ledgerTotals)
    data.profile.emit(
      Result.success(profile.copy(completedHandoffs = 5L, totalEarnedCents = 9000L, totalFeesSavedOrPaidCents = 500L))
    )

    data.myBookings.emit(Result.success(listOf(completed("b1", iProvide = true))))

    assertEquals(BookingTotals(5L, 9000L, 500L), totals.last())
    assertTrue(data.syncedTotals.isEmpty())
  }

  @Test
  fun `advancing a booking confirms in words that match the step`() = runTest(dispatcher) {
    createViewModel()
    val booking = Booking(id = "b1", notes = "Porch")

    viewModel.advanceBooking(booking, Booking.STATUS_ESCROW_HELD, "Side gate 4812")
    assertEquals("Approved. Escrow is locked and ready for the handoff.", viewModel.feedbackMessage.value)
    viewModel.advanceBooking(booking, Booking.STATUS_CANCELLED)
    assertEquals("Cancelled. The listing is back on the feed.", viewModel.feedbackMessage.value)

    assertEquals(
      listOf(Triple("b1", Booking.STATUS_ESCROW_HELD, "Side gate 4812"), Triple("b1", Booking.STATUS_CANCELLED, "Porch")),
      data.advances,
    )
  }

  private companion object {
    const val ME = "bob"
  }
}

/** An in-memory [BlockBenchDataSource]: tests push values into the flows and inspect the calls. */
private class FakeDataSource : BlockBenchDataSource {
  data class CreatedListing(
    val circle: BlockCircle,
    val ownerName: String,
    val ownerTrustScore: Double,
    val title: String,
    val rateCents: Long,
  )

  val profile = MutableSharedFlow<Result<UserProfile?>>(replay = 1)
  val circles = MutableSharedFlow<Result<List<BlockCircle>>>(replay = 1)
  val myBookings = MutableSharedFlow<Result<List<Booking>>>(replay = 1)
  private val listings = mutableMapOf<String, MutableSharedFlow<Result<List<Listing>>>>()

  fun listingsOf(circleId: String) = listings.getOrPut(circleId) { MutableSharedFlow(replay = 1) }

  /** Thrown by the next write (then cleared). */
  var failure: Throwable? = null

  /** When set, the next write suspends until it is completed. */
  var hold: CompletableDeferred<Unit>? = null

  var removedOnLeave = 0

  val ensuredProfiles = mutableListOf<String>()
  val syncedTotals = mutableListOf<BookingTotals>()
  val left = mutableListOf<Pair<BlockCircle, BlockCircle?>>()
  val createdListings = mutableListOf<CreatedListing>()
  val pauses = mutableListOf<Pair<String, Boolean>>()
  val bookings = mutableListOf<Triple<String, String, String>>()
  val advances = mutableListOf<Triple<String, String, String>>()

  private suspend fun write() {
    val pending = hold
    hold = null
    pending?.await()
    failure?.let {
      failure = null
      throw it
    }
  }

  override fun observeUserProfile(uid: String) = profile.map { it.getOrThrow() }

  override fun observeMyCircles(uid: String) = circles.map { it.getOrThrow() }

  override fun observeCircleListings(circleId: String) = listingsOf(circleId).map { it.getOrThrow() }

  override fun observeMyBookings(uid: String) = myBookings.map { it.getOrThrow() }

  override suspend fun ensureUserProfile(displayName: String): UserProfile {
    ensuredProfiles += displayName
    write()
    return UserProfile(displayName = displayName)
  }

  override suspend fun updateProAlertsSettings(enabled: Boolean, radiusMiles: Double, categories: List<String>) = write()

  override suspend fun syncCompletedEarningsToProfile(completedHandoffs: Long, earnedCents: Long, feesCents: Long) {
    syncedTotals += BookingTotals(completedHandoffs, earnedCents, feesCents)
  }

  override suspend fun createBlockCircle(circleName: String, radiusMiles: Double): BlockCircle {
    write()
    return BlockCircle(id = "new", name = circleName, inviteCode = "OAK7K2MQ", radiusMiles = radiusMiles)
  }

  override suspend fun joinCircleByInviteCode(rawCode: String): BlockCircle {
    write()
    return BlockCircle(id = "joined", name = "Joined Block", inviteCode = rawCode)
  }

  override suspend fun switchActiveCircle(circle: BlockCircle) = write()

  override suspend fun leaveBlockCircle(circle: BlockCircle, nextActiveCircle: BlockCircle?): Int {
    write()
    left += circle to nextActiveCircle
    return removedOnLeave
  }

  override suspend fun createListing(
    circle: BlockCircle,
    ownerName: String,
    ownerTrustScore: Double,
    kind: String,
    title: String,
    description: String,
    category: String,
    rateCents: Long,
    depositCents: Long,
    distanceMiles: Double,
    instantPickup: Boolean,
    photoUri: String,
  ): String {
    write()
    createdListings += CreatedListing(circle, ownerName, ownerTrustScore, title, rateCents)
    return "listing_${createdListings.size}"
  }

  override suspend fun updateListingDetails(
    listingId: String,
    title: String,
    description: String,
    category: String,
    rateCents: Long,
    depositCents: Long,
    distanceMiles: Double,
    instantPickup: Boolean,
    photoUri: String,
  ) = write()

  override suspend fun setListingPaused(listingId: String, paused: Boolean) {
    write()
    pauses += listingId to paused
  }

  override suspend fun deleteListing(listingId: String) = write()

  override suspend fun createBookingRequest(listing: Listing, currentUserName: String, notes: String): String {
    write()
    bookings += Triple(listing.id, currentUserName, notes)
    return "booking_${bookings.size}"
  }

  override suspend fun advanceBookingStatus(booking: Booking, nextStatus: String, updatedNotes: String) {
    write()
    advances += Triple(booking.id, nextStatus, updatedNotes)
  }
}
