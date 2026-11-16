package com.example.ui

import com.example.model.BlockCircle
import com.example.model.Listing
import com.example.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedFiltersTest {

  private val me = "me"

  private val washer =
    Listing(
      id = "washer",
      circleId = "oak",
      ownerUid = "bob",
      ownerName = "Bob Carter",
      kind = Listing.KIND_TOOL,
      title = "DeWalt Pressure Washer",
      description = "Includes 25ft hose.",
      category = "Yard & Outdoor",
      distanceMiles = 0.3,
      instantPickup = true,
    )
  private val latch =
    Listing(
      id = "latch",
      circleId = "oak",
      ownerUid = "cho",
      ownerName = "Cho Tanaka",
      kind = Listing.KIND_MICRO_JOB,
      title = "Fix Backyard Gate Latch",
      description = "Screws need tightening.",
      category = "Quick Fix",
      distanceMiles = 2.4,
      instantPickup = false,
    )
  private val myDrill =
    Listing(
      id = "drill",
      circleId = "oak",
      ownerUid = me,
      ownerName = "Me",
      kind = Listing.KIND_TOOL,
      title = "Cordless Drill",
      category = "Power Tools",
      distanceMiles = 0.1,
      instantPickup = true,
    )
  private val elsewhere = washer.copy(id = "elsewhere", circleId = "pine")
  private val all = listOf(washer, latch, myDrill, elsewhere)

  private fun ids(filters: FeedFiltersState, circleId: String? = "oak") =
    filterListings(all, filters, circleId).map { it.id }

  @Test
  fun `without filters the feed is everything in the active block`() {
    assertEquals(listOf("washer", "latch", "drill"), ids(FeedFiltersState()))
    assertEquals(listOf("elsewhere"), ids(FeedFiltersState(), circleId = "pine"))
    assertEquals(all.map { it.id }, ids(FeedFiltersState(), circleId = null))
  }

  @Test
  fun `kind filters`() {
    assertEquals(listOf("washer", "drill"), ids(FeedFiltersState(kindFilter = FeedFilter.TOOLS)))
    assertEquals(listOf("latch"), ids(FeedFiltersState(kindFilter = FeedFilter.MICRO_JOBS)))
    assertEquals(listOf("washer", "drill"), ids(FeedFiltersState(kindFilter = FeedFilter.INSTANT)))
  }

  @Test
  fun `category and distance filters`() {
    assertEquals(listOf("latch"), ids(FeedFiltersState(selectedCategory = "Quick Fix")))
    assertEquals(emptyList<String>(), ids(FeedFiltersState(selectedCategory = "Auto & Garage")))

    assertEquals(listOf("washer", "drill"), ids(FeedFiltersState(maxDistanceMiles = 0.5)))
    assertEquals(listOf("washer", "drill"), ids(FeedFiltersState(maxDistanceMiles = 1.5)))
    assertEquals(listOf("washer", "latch", "drill"), ids(FeedFiltersState(maxDistanceMiles = 3.0)))
    // The largest option means "any distance", even beyond five miles.
    val farAway = latch.copy(id = "far", distanceMiles = 9.0)
    assertEquals(
      listOf("far"),
      filterListings(listOf(farAway), FeedFiltersState(), "oak").map { it.id },
    )
  }

  @Test
  fun `search looks at title, description, category and owner, ignoring case and padding`() {
    assertEquals(listOf("washer"), ids(FeedFiltersState(searchQuery = "pressure")))
    assertEquals(listOf("washer"), ids(FeedFiltersState(searchQuery = "  HOSE ")))
    assertEquals(listOf("latch"), ids(FeedFiltersState(searchQuery = "quick")))
    assertEquals(listOf("latch"), ids(FeedFiltersState(searchQuery = "tanaka")))
    assertEquals(emptyList<String>(), ids(FeedFiltersState(searchQuery = "chainsaw")))
    assertEquals(listOf("washer", "latch", "drill"), ids(FeedFiltersState(searchQuery = "   ")))
  }

  @Test
  fun `filters combine`() {
    val filters =
      FeedFiltersState(kindFilter = FeedFilter.TOOLS, maxDistanceMiles = 0.5, searchQuery = "drill")
    assertEquals(listOf("drill"), ids(filters))
  }

  @Test
  fun `isNarrowing tells an empty block from a filtered-out one`() {
    assertFalse(FeedFiltersState().isNarrowing)
    assertFalse(FeedFiltersState(searchQuery = "  ").isNarrowing)
    assertTrue(FeedFiltersState(kindFilter = FeedFilter.TOOLS).isNarrowing)
    assertTrue(FeedFiltersState(selectedCategory = "Quick Fix").isNarrowing)
    assertTrue(FeedFiltersState(searchQuery = "drill").isNarrowing)
    assertTrue(FeedFiltersState(maxDistanceMiles = 3.0).isNarrowing)
  }

  @Test
  fun `pro alerts match other neighbours' available listings inside the watch rules`() {
    val profile = UserProfile(alertRadiusMiles = 1.5, alertCategories = listOf("Yard & Outdoor", "Power Tools"))
    fun matches(listings: List<Listing>, p: UserProfile? = profile, circleId: String? = "oak") =
      matchProAlerts(listings, p, me, circleId).map { it.id }

    // Not my own drill, not the job outside the radius and categories, not another block's.
    assertEquals(listOf("washer"), matches(all))
    assertEquals(emptyList<String>(), matches(listOf(washer.copy(status = Listing.STATUS_RESERVED))))
    assertEquals(emptyList<String>(), matches(listOf(washer.copy(status = Listing.STATUS_PAUSED))))
    assertEquals(emptyList<String>(), matches(listOf(washer.copy(distanceMiles = 1.6))))
    assertEquals(emptyList<String>(), matches(all, p = null))

    // No watched categories means "any category".
    val anyCategory = profile.copy(alertRadiusMiles = 5.0, alertCategories = emptyList())
    assertEquals(listOf("washer", "latch"), matches(all, p = anyCategory))
  }

  @Test
  fun `the active block is the one saved on the profile, else the first one`() {
    val oak = BlockCircle(id = "oak", name = "Oak")
    val pine = BlockCircle(id = "pine", name = "Pine")

    assertEquals(pine, resolveActiveCircle(listOf(oak, pine), UserProfile(activeCircleId = "pine")))
    assertEquals(oak, resolveActiveCircle(listOf(oak, pine), UserProfile(activeCircleId = "gone")))
    assertEquals(oak, resolveActiveCircle(listOf(oak, pine), UserProfile()))
    assertEquals(oak, resolveActiveCircle(listOf(oak, pine), null))
    assertNull(resolveActiveCircle(emptyList(), UserProfile(activeCircleId = "pine")))
  }
}
