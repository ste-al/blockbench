package com.example.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.model.Booking
import com.example.model.BookingTotals
import com.example.model.Listing
import com.example.model.UiState
import com.example.ui.FeedFiltersState
import com.example.ui.MissingFirebaseConfigScreen
import com.example.ui.components.BlockSwitcherSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every screen, sheet and dialog in the states that show the most controls, checked for controls
 * that a screen reader would have no name for. See [assertEveryControlHasAName].
 *
 * Not here: the sign-in screen and the shell around the three tabs, which need a session and are
 * checked in `AppShellSmokeTest`, and the dialog with the handoff note, which cannot be opened
 * under Robolectric (see `EscrowScreenTest`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TALL_PHONE)
class AccessibleNamesTest {

  @get:Rule val compose = createComposeRule()

  private fun showOnboarding() {
    compose.setThemedContent {
      CircleOnboardingScreen(
        isBusy = false,
        onCreateCircle = { _, _ -> },
        onJoinCircle = {},
        onSignOut = {},
      )
    }
  }

  private fun showFeed(
    feed: UiState<List<Listing>>,
    filters: FeedFiltersState = FeedFiltersState(),
    proAlertMatches: List<Listing> = emptyList(),
    showCreateSheet: Boolean = false,
  ) {
    compose.setThemedContent {
      FeedScreen(
        currentUserId = ME,
        profile = sampleProfile(proAlertsEnabled = proAlertMatches.isNotEmpty()),
        activeCircle = sampleCircle,
        feed = feed,
        visibleListings = (feed as? UiState.Success)?.data.orEmpty(),
        proAlertMatches = proAlertMatches,
        filters = filters,
        isBusy = false,
        showCreateSheet = showCreateSheet,
        onDismissCreateSheet = {},
        onOpenCreateSheet = {},
        onOpenBlockSwitcher = {},
        onKindFilterChange = {},
        onCategoryToggle = {},
        onMaxDistanceChange = {},
        onSearchQueryChange = {},
        onClearFilters = {},
        onRetry = {},
        onCreateListing = {},
        onUpdateListing = { _, _, _ -> },
        onTogglePause = {},
        onDeleteListing = {},
        onBookListing = { _, _, _ -> },
        onOpenProAlertsTab = {},
        onShowFeedback = {},
      )
    }
  }

  private fun showEscrow(ledger: UiState<List<Booking>>) {
    compose.setThemedContent {
      EscrowScreen(
        currentUserId = ME,
        ledger = ledger,
        isBusy = false,
        onAdvanceStatus = { _, _, _ -> },
        onRetry = {},
      )
    }
  }

  @Test
  fun `onboarding, starting a block and joining one`() {
    showOnboarding()
    compose.assertEveryControlHasAName()

    compose.onNodeWithTag("tab_join_block").performClick()
    compose.assertEveryControlHasAName()
  }

  @Test
  fun `the feed with listings of every kind and a Pro Alert`() {
    val match = sampleListing("match", mine = false)
    showFeed(
      feed =
        UiState.Success(
          listOf(
            match,
            sampleListing("job", mine = false, kind = Listing.KIND_MICRO_JOB),
            sampleListing("taken", mine = false, status = Listing.STATUS_RESERVED),
            sampleListing("mine", mine = true),
            sampleListing("paused", mine = true, status = Listing.STATUS_PAUSED),
          )
        ),
      proAlertMatches = listOf(match),
    )

    compose.assertEveryControlHasAName()
  }

  @Test
  fun `the feed when it is empty`() {
    showFeed(UiState.Success(emptyList()))
    compose.assertEveryControlHasAName()
  }

  @Test
  fun `the feed when the filters leave nothing`() {
    showFeed(UiState.Success(emptyList()), filters = FeedFiltersState(searchQuery = "no such thing"))
    compose.assertEveryControlHasAName()
  }

  @Test
  fun `the feed when it failed to load`() {
    showFeed(UiState.Error("Could not load the feed."))
    compose.assertEveryControlHasAName()
  }

  @Test
  fun `the listing editor`() {
    showFeed(UiState.Success(emptyList()), showCreateSheet = true)
    compose.onNodeWithTag("create_title_input").assertExists()

    compose.assertEveryControlHasAName()
  }

  @Test
  fun `a neighbour's listing, open`() {
    showFeed(UiState.Success(listOf(sampleListing("theirs", mine = false))))
    compose.onNodeWithTag("listing_card_theirs").performClick()
    compose.onNodeWithTag("confirm_escrow_book_button").assertExists()

    compose.assertEveryControlHasAName()
  }

  @Test
  fun `my own listing, open, and the question before deleting it`() {
    showFeed(UiState.Success(listOf(sampleListing("mine", mine = true))))
    compose.onNodeWithTag("listing_card_mine").performClick()
    compose.onNodeWithTag("delete_listing_button").assertExists()
    compose.assertEveryControlHasAName()

    compose.onNodeWithTag("delete_listing_button").performClick()
    compose.onNodeWithTag("confirm_delete_listing_button").assertExists()
    compose.assertEveryControlHasAName()
  }

  @Test
  fun `the escrow ledger with a booking at every step, on both sides`() {
    showEscrow(
      UiState.Success(
        listOf(
          sampleBooking("a", Booking.STATUS_REQUESTED, iOwnTheListing = true),
          sampleBooking("b", Booking.STATUS_REQUESTED, iOwnTheListing = false),
          sampleBooking("c", Booking.STATUS_ESCROW_HELD, iOwnTheListing = true),
          sampleBooking("d", Booking.STATUS_IN_PROGRESS, iOwnTheListing = true),
          sampleBooking("e", Booking.STATUS_IN_PROGRESS, iOwnTheListing = false),
          sampleBooking("f", Booking.STATUS_COMPLETED, iOwnTheListing = true),
          sampleBooking("g", Booking.STATUS_CANCELLED, iOwnTheListing = false),
        )
      )
    )
    compose.assertEveryControlHasAName()

    compose.onNodeWithTag("cancel_booking_b").performClick()
    compose.onNodeWithTag("confirm_cancel_booking_button").assertExists()
    compose.assertEveryControlHasAName()
  }

  @Test
  fun `the settled side of the ledger`() {
    showEscrow(
      UiState.Success(listOf(sampleBooking("f", Booking.STATUS_COMPLETED, iOwnTheListing = true)))
    )
    compose.onNodeWithTag("escrow_tab_settled").performClick()

    compose.assertEveryControlHasAName()
  }

  @Test
  fun `the ledger when it failed to load`() {
    showEscrow(UiState.Error("Could not load the ledger."))
    compose.assertEveryControlHasAName()
  }

  @Test
  fun `the trust hub with Pro Alerts on and a match`() {
    compose.setThemedContent {
      TrustHubScreen(
        profile = sampleProfile(proAlertsEnabled = true),
        totals = BookingTotals(),
        activeCircle = sampleCircle,
        circlesCount = 2,
        proAlertMatches = listOf(sampleListing("match", mine = false)),
        isBusy = false,
        onSaveProAlerts = { _, _, _ -> },
        onOpenBlockSwitcher = {},
        onInspectProMatch = {},
        onShowFeedback = {},
        onSignOut = {},
      )
    }

    compose.assertEveryControlHasAName()
  }

  @Test
  fun `the block switcher on each of its tabs, and the question before leaving a block`() {
    val another = sampleCircle.copy(id = "circle_elm", name = "Elm Court", creatorUid = NEIGHBOR)
    compose.setThemedContent {
      BlockSwitcherSheet(
        currentUserId = ME,
        activeCircle = sampleCircle,
        circles = listOf(sampleCircle, another),
        isBusy = false,
        onDismiss = {},
        onSelectCircle = {},
        onCreateCircle = { _, _ -> },
        onJoinCircle = {},
        onLeaveCircle = {},
        onCopyCode = {},
      )
    }
    compose.onNodeWithTag("block_option_circle_elm").assertExists()
    compose.assertEveryControlHasAName()

    compose.onNodeWithTag("leave_block_circle_elm").performClick()
    compose.onNodeWithTag("confirm_leave_block_button").assertExists()
    compose.assertEveryControlHasAName()
  }

  @Test
  fun `the block switcher's forms for joining and starting a block`() {
    compose.setThemedContent {
      BlockSwitcherSheet(
        currentUserId = ME,
        activeCircle = sampleCircle,
        circles = listOf(sampleCircle),
        isBusy = false,
        onDismiss = {},
        onSelectCircle = {},
        onCreateCircle = { _, _ -> },
        onJoinCircle = {},
        onLeaveCircle = {},
        onCopyCode = {},
      )
    }
    compose.onNodeWithTag("switcher_tab_join").performClick()
    compose.onNodeWithTag("switcher_join_code_input").assertExists()
    compose.assertEveryControlHasAName()

    compose.onNodeWithTag("switcher_tab_create").performClick()
    compose.onNodeWithTag("switcher_create_name_input").assertExists()
    compose.assertEveryControlHasAName()
  }

  @Test
  fun `the screen shown when the Firebase configuration is missing`() {
    compose.setThemedContent { MissingFirebaseConfigScreen() }
    compose.assertEveryControlHasAName()
  }
}
