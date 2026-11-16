package com.example.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.model.Listing
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.FeedFilter
import com.example.ui.FeedFiltersState
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
class FeedScreenTest {

  @get:Rule val compose = createComposeRule()

  private var feed: UiState<List<Listing>> by mutableStateOf(UiState.Success(emptyList()))
  private var filters by mutableStateOf(FeedFiltersState())
  private var profile: UserProfile? by mutableStateOf(sampleProfile())
  private var proAlertMatches: List<Listing> by mutableStateOf(emptyList())
  private var isBusy by mutableStateOf(false)

  private val booked = mutableListOf<Pair<String, String>>()
  private val paused = mutableListOf<String>()
  private val deleted = mutableListOf<String>()
  private var retries = 0
  private var filtersCleared = 0

  private fun show(vararg listings: Listing) {
    feed = UiState.Success(listings.toList())
    show()
  }

  private fun show() {
    compose.setThemedContent {
      val current = feed
      FeedScreen(
        currentUserId = ME,
        profile = profile,
        activeCircle = sampleCircle,
        feed = current,
        visibleListings = (current as? UiState.Success)?.data.orEmpty(),
        proAlertMatches = proAlertMatches,
        filters = filters,
        isBusy = isBusy,
        showCreateSheet = false,
        onDismissCreateSheet = {},
        onOpenCreateSheet = {},
        onOpenBlockSwitcher = {},
        onKindFilterChange = {},
        onCategoryToggle = {},
        onMaxDistanceChange = {},
        onSearchQueryChange = {},
        onClearFilters = { filtersCleared++ },
        onRetry = { retries++ },
        onCreateListing = {},
        onUpdateListing = { _, _, _ -> },
        onTogglePause = { paused += it.id },
        onDeleteListing = { deleted += it.id },
        onBookListing = { listing, notes, _ -> booked += listing.id to notes },
        onOpenProAlertsTab = {},
        onShowFeedback = {},
      )
    }
  }

  @Test
  fun `the header summarises the block with the invite code in full`() {
    show(sampleListing("l1", mine = false))

    compose.onNodeWithText("Oak & 14th Block").assertExists()
    compose.onNodeWithText("2 neighbors · Code OAK7K2MQ · 1.5 mi").assertExists()
  }

  @Test
  fun `loading is shown as loading, not as an empty block`() {
    feed = UiState.Loading
    show()

    compose.onNodeWithTag("feed_loading").assertExists()
    compose.onNodeWithTag("empty_post_button").assertDoesNotExist()
  }

  @Test
  fun `a failed feed says so and offers a retry`() {
    feed = UiState.Error("Could not load this block's listings. You appear to be offline.")
    show()

    compose.onNodeWithText("Could not load this block's listings. You appear to be offline.").assertExists()
    compose.onNodeWithTag("empty_post_button").assertDoesNotExist()
    compose.onNodeWithTag("feed_retry_button").performClick()

    assertEquals(1, retries)
  }

  @Test
  fun `an empty block invites posting, a filtered-out one offers to reset the filters`() {
    show()
    compose.onNodeWithTag("empty_invite_button").assertExists()
    compose.onNodeWithTag("clear_filters_button").assertDoesNotExist()

    filters = FeedFiltersState(kindFilter = FeedFilter.TOOLS)
    compose.onNodeWithTag("empty_invite_button").assertDoesNotExist()
    compose.onNodeWithTag("clear_filters_button").performClick()

    assertEquals(1, filtersCleared)
  }

  @Test
  fun `a neighbour's available listing can be booked with a note`() {
    show(sampleListing("l1", mine = false))

    compose.onNodeWithTag("listing_card_l1").performClick()
    compose.onNodeWithTag("booking_notes_input").performTextInput("Can swing by at 5:30")
    compose.onNodeWithTag("confirm_escrow_book_button").assertIsEnabled().performClick()

    assertEquals(listOf("l1" to "Can swing by at 5:30"), booked)
    // Owner-only controls are not offered on someone else's listing.
    compose.onNodeWithTag("delete_listing_button").assertDoesNotExist()
  }

  @Test
  fun `a listing someone else has reserved cannot be booked`() {
    show(sampleListing("l1", mine = false, status = Listing.STATUS_RESERVED))

    compose.onNodeWithTag("listing_card_l1").performClick()

    compose.onNodeWithTag("listing_unavailable_notice").assertExists()
    compose.onNodeWithTag("confirm_escrow_book_button").assertDoesNotExist()
  }

  @Test
  fun `an open sheet follows the listing when a neighbour books it meanwhile`() {
    show(sampleListing("l1", mine = false))
    compose.onNodeWithTag("listing_card_l1").performClick()
    compose.onNodeWithTag("confirm_escrow_book_button").assertExists()

    feed = UiState.Success(listOf(sampleListing("l1", mine = false, status = Listing.STATUS_RESERVED)))

    compose.onNodeWithTag("confirm_escrow_book_button").assertDoesNotExist()
    compose.onNodeWithTag("listing_unavailable_notice").assertExists()
  }

  @Test
  fun `an open sheet closes when its listing is deleted`() {
    show(sampleListing("l1", mine = false))
    compose.onNodeWithTag("listing_card_l1").performClick()
    compose.onNodeWithTag("confirm_escrow_book_button").assertExists()

    feed = UiState.Success(emptyList())

    compose.onNodeWithTag("confirm_escrow_book_button").assertDoesNotExist()
    compose.onNodeWithTag("listing_unavailable_notice").assertDoesNotExist()
  }

  @Test
  fun `my own available listing can be edited, paused and deleted after confirming`() {
    show(sampleListing("mine", mine = true))

    compose.onNodeWithTag("listing_card_mine").performClick()
    compose.onNodeWithTag("confirm_escrow_book_button").assertDoesNotExist()
    compose.onNodeWithTag("edit_listing_button").assertIsEnabled()
    compose.onNodeWithTag("toggle_pause_listing_button").assertIsEnabled()

    compose.onNodeWithTag("delete_listing_button").assertIsEnabled().performClick()
    assertEquals(emptyList<String>(), deleted)
    compose.onNodeWithTag("confirm_delete_listing_button").performClick()

    assertEquals(listOf("mine"), deleted)
  }

  @Test
  fun `my own listing with a booking in flight cannot be paused or deleted`() {
    show(sampleListing("mine", mine = true, status = Listing.STATUS_RESERVED))

    compose.onNodeWithTag("listing_card_mine").performClick()

    compose.onNodeWithTag("edit_listing_button").assertIsEnabled()
    compose.onNodeWithTag("toggle_pause_listing_button").assertIsNotEnabled()
    compose.onNodeWithTag("delete_listing_button").assertIsNotEnabled()
  }

  @Test
  fun `a reservation left over from an older app version can be cleared by the owner`() {
    show(sampleListing("mine", mine = true, status = Listing.STATUS_RESERVED, activeBookingId = ""))

    compose.onNodeWithTag("listing_card_mine").performClick()
    compose.onNodeWithText("Clear").assertExists()
    compose.onNodeWithTag("delete_listing_button").assertIsNotEnabled()
    compose.onNodeWithTag("toggle_pause_listing_button").assertIsEnabled().performClick()

    assertEquals(listOf("mine"), paused)
  }

  @Test
  fun `the Pro Alert banner appears only for subscribers and counts its matches`() {
    val one = sampleListing("l1", mine = false)
    val two = sampleListing("l2", mine = false)
    proAlertMatches = listOf(one)
    profile = sampleProfile(proAlertsEnabled = false)
    show(one, two)
    compose.onNodeWithTag("pro_alert_banner").assertDoesNotExist()

    profile = sampleProfile(proAlertsEnabled = true)
    compose.onNodeWithText("1 Pro Alert match nearby").assertExists()

    proAlertMatches = listOf(one, two)
    compose.onNodeWithText("2 Pro Alert matches nearby").assertExists()

    // Tapping the banner opens the first match.
    compose.onNodeWithTag("pro_alert_banner").performClick()
    compose.onNodeWithTag("confirm_escrow_book_button").assertExists()
  }
}
