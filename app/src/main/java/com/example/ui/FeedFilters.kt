package com.example.ui

import com.example.model.BlockCircle
import com.example.model.Listing
import com.example.model.UserProfile

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
  val maxDistanceMiles: Double = ANY_DISTANCE_MILES,
) {
  /** True when anything narrows the feed, i.e. an empty feed may just be filtered out. */
  val isNarrowing: Boolean
    get() =
      kindFilter != FeedFilter.ALL ||
        selectedCategory != null ||
        searchQuery.isNotBlank() ||
        maxDistanceMiles < ANY_DISTANCE_MILES

  companion object {
    /** The largest distance option, which means "do not filter by distance". */
    const val ANY_DISTANCE_MILES = 5.0
  }
}

/** The block the user is working in: the one saved on their profile, else the first they belong to. */
internal fun resolveActiveCircle(circles: List<BlockCircle>, profile: UserProfile?): BlockCircle? =
  circles.firstOrNull { it.id == profile?.activeCircleId } ?: circles.firstOrNull()

internal fun filterListings(
  listings: List<Listing>,
  filters: FeedFiltersState,
  activeCircleId: String?,
): List<Listing> {
  val query = filters.searchQuery.trim()
  return listings.filter { item ->
    val matchesCircle = activeCircleId == null || item.circleId == activeCircleId
    val matchesKind =
      when (filters.kindFilter) {
        FeedFilter.ALL -> true
        FeedFilter.TOOLS -> item.kind == Listing.KIND_TOOL
        FeedFilter.MICRO_JOBS -> item.kind == Listing.KIND_MICRO_JOB
        FeedFilter.INSTANT -> item.instantPickup
      }
    val matchesCategory =
      filters.selectedCategory == null || item.category == filters.selectedCategory
    val matchesDistance =
      filters.maxDistanceMiles >= FeedFiltersState.ANY_DISTANCE_MILES ||
        item.distanceMiles <= filters.maxDistanceMiles
    val matchesQuery =
      query.isEmpty() ||
        item.title.contains(query, ignoreCase = true) ||
        item.description.contains(query, ignoreCase = true) ||
        item.category.contains(query, ignoreCase = true) ||
        item.ownerName.contains(query, ignoreCase = true)
    matchesCircle && matchesKind && matchesCategory && matchesDistance && matchesQuery
  }
}

/** Other neighbours' available listings that fall inside the user's Pro Alert watch rules. */
internal fun matchProAlerts(
  listings: List<Listing>,
  profile: UserProfile?,
  currentUserId: String,
  activeCircleId: String?,
): List<Listing> {
  if (profile == null) return emptyList()
  return listings.filter { item ->
    (activeCircleId == null || item.circleId == activeCircleId) &&
      item.ownerUid != currentUserId &&
      item.status == Listing.STATUS_AVAILABLE &&
      item.distanceMiles <= profile.alertRadiusMiles &&
      (profile.alertCategories.isEmpty() || item.category in profile.alertCategories)
  }
}
