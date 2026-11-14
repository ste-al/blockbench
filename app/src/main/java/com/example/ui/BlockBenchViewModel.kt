package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.BlockBenchRepository
import com.example.model.BlockCircle
import com.example.model.Booking
import com.example.model.Listing
import com.example.model.UiState
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FeedFilter(val label: String) {
  ALL("All Nearby"),
  TOOLS("Tools"),
  MICRO_JOBS("Micro-Jobs"),
  INSTANT("Instant Pickup"),
}

data class FeedFiltersState(
  val kindFilter: FeedFilter = FeedFilter.ALL,
  val selectedCategory: String? = null,
  val searchQuery: String = "",
  val maxDistanceMiles: Double = 5.0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BlockBenchViewModel(
  private val repository: BlockBenchRepository,
  private val currentUserId: String,
  private val initialDisplayName: String,
) : ViewModel() {

  private val retryTrigger = MutableStateFlow(0)

  val userProfileState: StateFlow<UiState<UserProfile?>> =
    retryTrigger
      .flatMapLatest {
        repository
          .observeUserProfile(currentUserId)
          .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
          .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing user profile", root)
            emit(UiState.Error(root.message ?: "Failed to load neighbor profile"))
          }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val circlesState: StateFlow<UiState<List<BlockCircle>>> =
    retryTrigger
      .flatMapLatest {
        repository
          .observeMyCircles(currentUserId)
          .map<List<BlockCircle>, UiState<List<BlockCircle>>> { UiState.Success(it) }
          .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing circles", root)
            emit(UiState.Error(root.message ?: "Failed to load neighborhood circles"))
          }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val activeCircle: StateFlow<BlockCircle?> =
    combine(circlesState, userProfileState) { circlesUi, profileUi ->
        val circles = (circlesUi as? UiState.Success)?.data.orEmpty()
        val profile = (profileUi as? UiState.Success)?.data
        circles.firstOrNull { it.id == profile?.activeCircleId } ?: circles.firstOrNull()
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null,
      )

  val listingsState: StateFlow<UiState<List<Listing>>> =
    retryTrigger
      .flatMapLatest {
        repository
          .observeCircleListings(currentUserId)
          .map<List<Listing>, UiState<List<Listing>>> { UiState.Success(it) }
          .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing listings", root)
            emit(UiState.Error(root.message ?: "Failed to load block listings"))
          }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val bookingsState: StateFlow<UiState<List<Booking>>> =
    retryTrigger
      .flatMapLatest {
        repository
          .observeMyBookings(currentUserId)
          .map<List<Booking>, UiState<List<Booking>>> { UiState.Success(it) }
          .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing bookings", root)
            emit(UiState.Error(root.message ?: "Failed to load escrow ledger"))
          }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  private val _filters = MutableStateFlow(FeedFiltersState())
  val filters: StateFlow<FeedFiltersState> = _filters.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _feedbackMessage = MutableStateFlow<String?>(null)
  val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

  val filteredListings: StateFlow<List<Listing>> =
    combine(listingsState, _filters, activeCircle) { state, filter, circle ->
        val items = (state as? UiState.Success)?.data ?: emptyList()
        val activeCircleId = circle?.id
        items.filter { item ->
          val matchesCircle = activeCircleId == null || item.circleId == activeCircleId
          val matchesKind =
            when (filter.kindFilter) {
              FeedFilter.ALL -> true
              FeedFilter.TOOLS -> item.kind == Listing.KIND_TOOL
              FeedFilter.MICRO_JOBS -> item.kind == Listing.KIND_MICRO_JOB
              FeedFilter.INSTANT -> item.instantPickup
            }
          val matchesCategory =
            filter.selectedCategory == null || item.category == filter.selectedCategory
          val matchesDistance =
            filter.maxDistanceMiles >= 5.0 || item.distanceMiles <= filter.maxDistanceMiles
          val query = filter.searchQuery.trim()
          val matchesQuery =
            query.isEmpty() ||
              item.title.contains(query, ignoreCase = true) ||
              item.description.contains(query, ignoreCase = true) ||
              item.category.contains(query, ignoreCase = true) ||
              item.ownerName.contains(query, ignoreCase = true)
          matchesCircle && matchesKind && matchesCategory && matchesDistance && matchesQuery
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList(),
      )

  val proAlertMatches: StateFlow<List<Listing>> =
    combine(listingsState, userProfileState, activeCircle) { listingsUi, profileUi, circle ->
        val items = (listingsUi as? UiState.Success)?.data ?: emptyList()
        val profile = (profileUi as? UiState.Success)?.data
        val activeCircleId = circle?.id
        if (profile == null) {
          emptyList()
        } else {
          items.filter { item ->
            (activeCircleId == null || item.circleId == activeCircleId) &&
              item.ownerUid != currentUserId &&
              item.status == Listing.STATUS_AVAILABLE &&
              item.distanceMiles <= profile.alertRadiusMiles &&
              (profile.alertCategories.isEmpty() || item.category in profile.alertCategories)
          }
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList(),
      )

  init {
    viewModelScope.launch {
      runCatching { repository.ensureUserProfile(displayName = initialDisplayName) }
        .onFailure { e ->
          if (e is CancellationException) throw e
          Log.w(TAG, "Profile bootstrap failed", e)
          _feedbackMessage.value = e.localizedMessage ?: "Failed to initialize profile."
        }
    }

    // Reconcile settled booking stats with profile document so Trust Card & Escrow always agree
    viewModelScope.launch {
      combine(bookingsState, userProfileState) { bookingsUi, profileUi ->
          val bookings = (bookingsUi as? UiState.Success)?.data
          val profile = (profileUi as? UiState.Success)?.data
          if (bookings != null && profile != null) Pair(bookings, profile) else null
        }
        .collect { pair ->
          val (bookings, profile) = pair ?: return@collect
          val completed = bookings.filter { it.status == Booking.STATUS_COMPLETED }
          val expectedHandoffs = completed.size.toLong()
          val expectedEarned =
            completed.filter { it.providerUid == currentUserId }.sumOf { it.providerNetCents }
          val expectedFees = completed.sumOf { it.platformFeeCents }
          if (
            expectedHandoffs > profile.completedHandoffs ||
              expectedEarned > profile.totalEarnedCents ||
              expectedFees > profile.totalFeesSavedOrPaidCents
          ) {
            repository.syncCompletedEarningsToProfile(
              expectedHandoffs = maxOf(profile.completedHandoffs, expectedHandoffs),
              expectedEarnedCents = maxOf(profile.totalEarnedCents, expectedEarned),
              expectedFeesCents = maxOf(profile.totalFeesSavedOrPaidCents, expectedFees),
            )
          }
        }
    }
  }

  fun retryStreams() {
    retryTrigger.value += 1
    viewModelScope.launch {
      runCatching { repository.ensureUserProfile(displayName = initialDisplayName) }
    }
  }

  fun showFeedback(message: String) {
    _feedbackMessage.value = message
  }

  fun setKindFilter(filter: FeedFilter) {
    _filters.value = _filters.value.copy(kindFilter = filter)
  }

  fun toggleCategoryFilter(category: String?) {
    val next = if (_filters.value.selectedCategory == category) null else category
    _filters.value = _filters.value.copy(selectedCategory = next)
  }

  fun setSearchQuery(query: String) {
    _filters.value = _filters.value.copy(searchQuery = query)
  }

  fun setMaxDistanceMiles(miles: Double) {
    _filters.value = _filters.value.copy(maxDistanceMiles = miles)
  }

  fun clearFeedback() {
    _feedbackMessage.value = null
  }

  fun createBlockCircle(name: String, radiusMiles: Double, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      _isBusy.value = true
      try {
        val circle = repository.createBlockCircle(name, radiusMiles)
        _feedbackMessage.value =
          "Block \"${circle.name}\" created! Invite code: ${circle.inviteCode}"
        onDone()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedbackMessage.value = e.localizedMessage ?: "Could not create block circle."
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun joinBlockCircle(inviteCode: String, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      _isBusy.value = true
      try {
        val circle = repository.joinCircleByInviteCode(inviteCode)
        _feedbackMessage.value = "Joined ${circle.name}!"
        onDone()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedbackMessage.value = e.localizedMessage ?: "Invalid or expired invite code."
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun switchActiveCircle(circle: BlockCircle, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      _isBusy.value = true
      try {
        repository.switchActiveCircle(circle)
        _feedbackMessage.value = "Switched to ${circle.name}"
        onDone()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedbackMessage.value = e.localizedMessage ?: "Could not switch block circle."
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun leaveBlockCircle(
    circle: BlockCircle,
    allCircles: List<BlockCircle>,
    onDone: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      try {
        val fallback = allCircles.firstOrNull { it.id != circle.id }
        repository.leaveBlockCircle(circle, fallback)
        _feedbackMessage.value = "Left ${circle.name}."
        onDone()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedbackMessage.value = e.localizedMessage ?: "Could not leave block circle."
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun createListing(
    circle: BlockCircle,
    profile: UserProfile?,
    kind: String,
    title: String,
    description: String,
    category: String,
    rateCents: Long,
    depositCents: Long,
    distanceMiles: Double,
    instantPickup: Boolean,
    photoUri: String,
    onDone: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      try {
        repository.createListing(
          circle = circle,
          ownerName = profile?.displayName ?: initialDisplayName,
          ownerTrustScore = profile?.trustScore ?: 5.0,
          kind = kind,
          title = title,
          description = description,
          category = category,
          rateCents = rateCents,
          depositCents = depositCents,
          distanceMiles = distanceMiles,
          instantPickup = instantPickup,
          photoUri = photoUri,
        )
        _feedbackMessage.value = "Published to ${circle.name}!"
        onDone()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedbackMessage.value = e.localizedMessage ?: "Failed to publish listing."
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun updateListing(
    listingId: String,
    title: String,
    description: String,
    category: String,
    rateCents: Long,
    depositCents: Long,
    distanceMiles: Double,
    instantPickup: Boolean,
    photoUri: String,
    onDone: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      try {
        repository.updateListingDetails(
          listingId = listingId,
          title = title,
          description = description,
          category = category,
          rateCents = rateCents,
          depositCents = depositCents,
          distanceMiles = distanceMiles,
          instantPickup = instantPickup,
          photoUri = photoUri,
        )
        _feedbackMessage.value = "Listing updated!"
        onDone()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedbackMessage.value = e.localizedMessage ?: "Failed to update listing."
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun toggleListingPause(listing: Listing) {
    val nextStatus =
      if (listing.status == Listing.STATUS_PAUSED) {
        Listing.STATUS_AVAILABLE
      } else {
        Listing.STATUS_PAUSED
      }
    viewModelScope.launch {
      try {
        repository.updateListingStatus(listing.id, nextStatus)
        _feedbackMessage.value =
          if (nextStatus == Listing.STATUS_PAUSED) "Listing paused." else "Listing is live again."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedbackMessage.value = e.localizedMessage ?: "Could not update listing status."
      }
    }
  }

  fun deleteListing(listing: Listing) {
    viewModelScope.launch {
      try {
        repository.deleteListing(listing.id)
        _feedbackMessage.value = "Removed \"${listing.title}\"."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedbackMessage.value = e.localizedMessage ?: "Could not delete listing."
      }
    }
  }

  fun bookOrClaimListing(listing: Listing, profile: UserProfile?, notes: String, onDone: () -> Unit) {
    viewModelScope.launch {
      _isBusy.value = true
      try {
        repository.createBookingRequest(
          listing = listing,
          currentUserName = profile?.displayName ?: initialDisplayName,
          notes = notes,
        )
        _feedbackMessage.value = "Escrow request sent! Track progress in Escrow."
        onDone()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedbackMessage.value = e.localizedMessage ?: "Failed to place escrow hold."
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun advanceBooking(
    booking: Booking,
    nextStatus: String,
    updatedNotes: String = booking.notes,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      try {
        repository.advanceBookingStatus(booking, nextStatus, updatedNotes)
        _feedbackMessage.value =
          when (nextStatus) {
            Booking.STATUS_ESCROW_HELD -> "Escrow hold locked. Ready for handoff."
            Booking.STATUS_IN_PROGRESS -> "Handoff started!"
            Booking.STATUS_COMPLETED -> "Completed & escrow payout released!"
            Booking.STATUS_CANCELLED -> "Hold cancelled and listing released."
            else -> "Status updated."
          }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedbackMessage.value = e.localizedMessage ?: "Could not update escrow state."
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun updateProAlerts(enabled: Boolean, radiusMiles: Double, categories: List<String>) {
    viewModelScope.launch {
      _isBusy.value = true
      try {
        repository.updateProAlertsSettings(enabled, radiusMiles, categories)
        _feedbackMessage.value =
          if (enabled) "BlockBench Pro Alerts activated!" else "Pro Alerts paused."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedbackMessage.value = e.localizedMessage ?: "Could not update Pro Alerts."
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun syncMissingCircleMembersToOwnedListings(circle: BlockCircle, listings: List<Listing>) {
    val staleIds =
      listings
        .filter {
          it.circleId == circle.id &&
            it.ownerUid == currentUserId &&
            !it.memberUids.containsAll(circle.memberUids)
        }
        .map { it.id }
    if (staleIds.isNotEmpty()) {
      viewModelScope.launch {
        runCatching {
          repository.syncListingsMembershipForCircle(
            circleId = circle.id,
            listingIds = staleIds,
            memberUidsToSync = circle.memberUids,
          )
        }
      }
    }
  }

  private fun unwrapFirestoreError(error: Throwable): Throwable {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    return firestoreError ?: error
  }

  private companion object {
    const val TAG = "BlockBenchVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
