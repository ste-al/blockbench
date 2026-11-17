package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.BlockBenchDataSource
import com.example.data.toUserMessage
import com.example.model.BlockCircle
import com.example.model.Booking
import com.example.model.BookingTotals
import com.example.model.Listing
import com.example.model.ListingDraft
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.dataOrNull
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the signed-in shell needs to know before it can decide which screen to show. */
sealed interface SessionState {
  data object Loading : SessionState

  data class Failed(val message: String) : SessionState

  /**
   * [profile] is null until the first-launch bootstrap has written it; [activeCircle] is null
   * when the user does not belong to any block yet (which leads to onboarding).
   */
  data class Ready(
    val profile: UserProfile?,
    val circles: List<BlockCircle>,
    val activeCircle: BlockCircle?,
  ) : SessionState
}

/**
 * State and actions for one signed-in session. An instance belongs to exactly one user: the app
 * root keeps it in a ViewModelStore that is cleared on sign-out, so its Firestore listeners never
 * outlive the session they were opened for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BlockBenchViewModel(
  private val repository: BlockBenchDataSource,
  private val currentUserId: String,
  private val initialDisplayName: String,
) : ViewModel() {

  /** Bumped by [retry] to restart every Firestore listener. */
  private val retryAttempt = MutableStateFlow(0)

  private val profileState: StateFlow<UiState<UserProfile?>> =
    retryable("your profile") { repository.observeUserProfile(currentUserId) }
      .stateWhileSubscribed(UiState.Loading)

  private val circlesState: StateFlow<UiState<List<BlockCircle>>> =
    retryable("your blocks") { repository.observeMyCircles(currentUserId) }
      .stateWhileSubscribed(UiState.Loading)

  /**
   * Profile, blocks and the active block as one value, so the shell can never observe them out
   * of step with each other (which used to flash the onboarding screen at members on launch).
   */
  val sessionState: StateFlow<SessionState> =
    combine(profileState, circlesState) { profileUi, circlesUi ->
        when {
          circlesUi is UiState.Error -> SessionState.Failed(circlesUi.message)
          profileUi is UiState.Error -> SessionState.Failed(profileUi.message)
          circlesUi is UiState.Success && profileUi is UiState.Success ->
            SessionState.Ready(
              profile = profileUi.data,
              circles = circlesUi.data,
              activeCircle = resolveActiveCircle(circlesUi.data, profileUi.data),
            )
          else -> SessionState.Loading
        }
      }
      .stateWhileSubscribed(SessionState.Loading)

  private val activeCircleId: Flow<String?> =
    sessionState.map { (it as? SessionState.Ready)?.activeCircle?.id }.distinctUntilChanged()

  /** The active block's feed. Loading again whenever the active block changes. */
  val listingsState: StateFlow<UiState<List<Listing>>> =
    run {
        var announced: Pair<Int, String>? = null
        combine(retryAttempt, activeCircleId, ::Pair).flatMapLatest { (attempt, circleId) ->
          if (circleId == null) {
            flowOf<UiState<List<Listing>>>(UiState.Success(emptyList()))
          } else {
            repository.observeCircleListings(circleId).asUiState("this block's listings").onStart {
              // Don't leave the previous block's listings on screen under the new block's name.
              val key = attempt to circleId
              if (key != announced) {
                announced = key
                emit(UiState.Loading)
              }
            }
          }
        }
      }
      .stateWhileSubscribed(UiState.Loading)

  val bookingsState: StateFlow<UiState<List<Booking>>> =
    retryable("your escrow ledger") { repository.observeMyBookings(currentUserId) }
      .stateWhileSubscribed(UiState.Loading)

  private val _filters = MutableStateFlow(FeedFiltersState())
  val filters: StateFlow<FeedFiltersState> = _filters.asStateFlow()

  private val busyOperations = MutableStateFlow(0)

  /** True while at least one write is in flight. A counter, so overlapping writes don't clear it early. */
  val isBusy: StateFlow<Boolean> =
    busyOperations.map { it > 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  private val _feedbackMessage = MutableStateFlow<String?>(null)
  val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

  val filteredListings: StateFlow<List<Listing>> =
    combine(listingsState, _filters, activeCircleId) { listingsUi, filters, circleId ->
        filterListings(listingsUi.dataOrNull().orEmpty(), filters, circleId)
      }
      .stateWhileSubscribed(emptyList())

  val proAlertMatches: StateFlow<List<Listing>> =
    combine(listingsState, profileState, activeCircleId) { listingsUi, profileUi, circleId ->
        matchProAlerts(
          listings = listingsUi.dataOrNull().orEmpty(),
          profile = profileUi.dataOrNull(),
          currentUserId = currentUserId,
          activeCircleId = circleId,
        )
      }
      .stateWhileSubscribed(emptyList())

  private var lastSyncedTotals: BookingTotals? = null

  /**
   * Handoffs, earnings and fees over the user's completed bookings (never below what the profile
   * already records). Whenever the bookings are ahead of the profile's stored summary, the
   * summary is brought up to date, for both sides of every booking alike.
   */
  val ledgerTotals: StateFlow<BookingTotals> =
    combine(bookingsState, profileState) { bookingsUi, profileUi ->
        val bookings = bookingsUi.dataOrNull()
        Triple(
          BookingTotals.from(bookings.orEmpty(), currentUserId),
          profileUi.dataOrNull(),
          bookings != null,
        )
      }
      .onEach { (computed, profile, bookingsLoaded) ->
        if (bookingsLoaded && profile != null && computed.exceeds(profile)) {
          syncProfileSummary(computed)
        }
      }
      .map { (computed, profile, _) -> computed.atLeast(profile) }
      .stateWhileSubscribed(BookingTotals())

  init {
    bootstrapProfile(reportFailure = true)
  }

  // ------------------------------------------------------------------ actions

  /** Restarts every listener after a failure, and retries the profile bootstrap. */
  fun retry() {
    retryAttempt.update { it + 1 }
    bootstrapProfile(reportFailure = false)
  }

  fun showFeedback(message: String) {
    _feedbackMessage.value = message
  }

  fun clearFeedback() {
    _feedbackMessage.value = null
  }

  fun setKindFilter(filter: FeedFilter) {
    _filters.update { it.copy(kindFilter = filter) }
  }

  fun toggleCategoryFilter(category: String) {
    _filters.update {
      it.copy(selectedCategory = if (it.selectedCategory == category) null else category)
    }
  }

  fun setSearchQuery(query: String) {
    _filters.update { it.copy(searchQuery = query) }
  }

  fun setMaxDistanceMiles(miles: Double) {
    _filters.update { it.copy(maxDistanceMiles = miles) }
  }

  fun clearFilters() {
    _filters.value = FeedFiltersState()
  }

  fun createBlockCircle(name: String, radiusMiles: Double, onDone: () -> Unit = {}) =
    launchOperation("Could not create the block.") {
      val circle = repository.createBlockCircle(name, radiusMiles)
      onDone()
      "Block \"${circle.name}\" created! Invite code: ${circle.inviteCode}"
    }

  fun joinBlockCircle(inviteCode: String, onDone: () -> Unit = {}) =
    launchOperation("Could not join the block.") {
      val circle = repository.joinCircleByInviteCode(inviteCode)
      onDone()
      "You're in ${circle.name}!"
    }

  fun switchActiveCircle(circle: BlockCircle, onDone: () -> Unit = {}) =
    launchOperation("Could not switch blocks.") {
      repository.switchActiveCircle(circle)
      onDone()
      "Switched to ${circle.name}"
    }

  fun leaveBlockCircle(circle: BlockCircle, onDone: () -> Unit = {}) =
    launchOperation("Could not leave the block.") {
      val session = sessionState.value as? SessionState.Ready
      // Only matters when the block being left is the active one; otherwise the active block stays.
      val nextActive = session?.circles?.firstOrNull { it.id != circle.id }
      val removedListings = repository.leaveBlockCircle(circle, nextActive)
      onDone()
      when (removedListings) {
        0 -> "You left ${circle.name}."
        1 -> "You left ${circle.name}. Your listing there was removed."
        else -> "You left ${circle.name}. Your $removedListings listings there were removed."
      }
    }

  fun createListing(draft: ListingDraft, onDone: () -> Unit = {}) =
    launchOperation("Could not publish the listing.") {
      val session = sessionState.value as? SessionState.Ready
      val circle = session?.activeCircle ?: error("No active block to publish to")
      repository.createListing(
        circle = circle,
        ownerName = session.profile?.displayName ?: initialDisplayName,
        ownerTrustScore = session.profile?.trustScore ?: DEFAULT_TRUST_SCORE,
        kind = draft.kind,
        title = draft.title,
        description = draft.description,
        category = draft.category,
        rateCents = draft.rateCents,
        depositCents = draft.depositCents,
        distanceMiles = draft.distanceMiles,
        instantPickup = draft.instantPickup,
        photoUri = draft.photoUri,
      )
      onDone()
      "Published to ${circle.name}!"
    }

  fun updateListing(listingId: String, draft: ListingDraft, onDone: () -> Unit = {}) =
    launchOperation("Could not save the listing.") {
      repository.updateListingDetails(
        listingId = listingId,
        title = draft.title,
        description = draft.description,
        category = draft.category,
        rateCents = draft.rateCents,
        depositCents = draft.depositCents,
        distanceMiles = draft.distanceMiles,
        instantPickup = draft.instantPickup,
        photoUri = draft.photoUri,
      )
      onDone()
      "Listing updated!"
    }

  /** Pauses an available listing, resumes a paused one, and clears a stale reservation. */
  fun toggleListingPause(listing: Listing) =
    launchOperation("Could not update the listing.") {
      val pause = listing.status == Listing.STATUS_AVAILABLE
      repository.setListingPaused(listing.id, paused = pause)
      if (pause) "Listing paused." else "Listing is live again."
    }

  fun deleteListing(listing: Listing) =
    launchOperation("Could not delete the listing.") {
      repository.deleteListing(listing.id)
      "Removed \"${listing.title}\"."
    }

  fun bookOrClaimListing(listing: Listing, notes: String, onDone: () -> Unit = {}) =
    launchOperation("Could not send the request.") {
      val profile = (sessionState.value as? SessionState.Ready)?.profile
      repository.createBookingRequest(
        listing = listing,
        currentUserName = profile?.displayName ?: initialDisplayName,
        notes = notes,
      )
      onDone()
      if (listing.isMicroJob) {
        "Job claimed! ${listing.ownerName} can now approve it in Escrow."
      } else {
        "Request sent! ${listing.ownerName} can now approve it in Escrow."
      }
    }

  fun advanceBooking(booking: Booking, nextStatus: String, updatedNotes: String = booking.notes) =
    launchOperation("Could not update the booking.") {
      repository.advanceBookingStatus(booking, nextStatus, updatedNotes)
      when (nextStatus) {
        Booking.STATUS_ESCROW_HELD -> "Approved. Escrow is locked and ready for the handoff."
        Booking.STATUS_IN_PROGRESS -> "Handoff started!"
        Booking.STATUS_COMPLETED -> "Completed. The payout has been released."
        Booking.STATUS_CANCELLED -> "Cancelled. The listing is back on the feed."
        else -> "Booking updated."
      }
    }

  fun updateProAlerts(enabled: Boolean, radiusMiles: Double, categories: List<String>) =
    launchOperation("Could not update Pro Alerts.") {
      repository.updateProAlertsSettings(enabled, radiusMiles, categories)
      if (enabled) "Pro Alert rules saved." else "Pro Alerts paused."
    }

  // ------------------------------------------------------------------ plumbing

  /**
   * Runs one write. [block] returns the confirmation to show; any failure is turned into a
   * readable message, with [failureMessage] saying what was being attempted.
   */
  private fun launchOperation(failureMessage: String, block: suspend () -> String?) {
    viewModelScope.launch {
      busyOperations.update { it + 1 }
      try {
        block()?.let { _feedbackMessage.value = it }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, failureMessage, e)
        _feedbackMessage.value = e.toUserMessage(failureMessage)
      } finally {
        busyOperations.update { it - 1 }
      }
    }
  }

  private fun bootstrapProfile(reportFailure: Boolean) {
    viewModelScope.launch {
      try {
        repository.ensureUserProfile(displayName = initialDisplayName)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Not fatal: every write that needs the profile creates it on demand.
        Log.w(TAG, "Profile bootstrap failed", e)
        if (reportFailure) {
          _feedbackMessage.value = e.toUserMessage("Could not set up your profile yet.")
        }
      }
    }
  }

  private fun syncProfileSummary(totals: BookingTotals) {
    // Snapshots arrive in bursts (local echo, then server); ask once per distinct set of totals.
    if (totals == lastSyncedTotals) return
    lastSyncedTotals = totals
    viewModelScope.launch {
      repository.syncCompletedEarningsToProfile(
        completedHandoffs = totals.completedHandoffs,
        earnedCents = totals.earnedCents,
        feesCents = totals.feesCents,
      )
    }
  }

  /**
   * A listener that is restarted by [retry]. After an explicit retry it reports
   * [UiState.Loading] again; when it merely resumes (the app returned from the background) the
   * previous value stays on screen until fresh data arrives.
   */
  private fun <T> retryable(what: String, source: () -> Flow<T>): Flow<UiState<T>> {
    var announcedAttempt = 0
    return retryAttempt.flatMapLatest { attempt ->
      source().asUiState(what).onStart {
        if (attempt != announcedAttempt) {
          announcedAttempt = attempt
          emit(UiState.Loading)
        }
      }
    }
  }

  private fun <T> Flow<T>.asUiState(what: String): Flow<UiState<T>> =
    map<T, UiState<T>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Error observing $what", root)
        emit(UiState.Error(root.toUserMessage("Could not load $what.")))
      }

  private fun <T> Flow<T>.stateWhileSubscribed(initialValue: T): StateFlow<T> =
    stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = initialValue,
    )

  private companion object {
    const val TAG = "BlockBenchVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
    const val DEFAULT_TRUST_SCORE = 5.0
  }
}
