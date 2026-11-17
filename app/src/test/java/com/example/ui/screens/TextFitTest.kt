package com.example.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.model.Booking
import com.example.model.BookingTotals
import com.example.model.Listing
import com.example.model.UiState
import com.example.ui.FeedFiltersState
import com.example.ui.components.BlockSwitcherSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The screens on a narrow phone, checked for text that is cut off instead of wrapped: at the
 * default text size in [TextFitAtDefaultSizeTest], at the largest one Android offers in
 * [TextFitAtLargestSizeTest]. Button labels used to be clipped in both ("1-Tap Invit" at the
 * default size, "Decli" at the largest), and badges and the invite code with large text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = NARROW_PHONE)
abstract class TextFitTest {

  @get:Rule val compose = createComposeRule()

  private val listings =
    listOf(
      sampleListing("tool", mine = false),
      sampleListing("job", mine = false, kind = Listing.KIND_MICRO_JOB),
      sampleListing("taken", mine = false, status = Listing.STATUS_RESERVED),
      sampleListing("mine", mine = true),
    )

  private fun showFeed() {
    compose.setThemedContent {
      FeedScreen(
        currentUserId = ME,
        profile = sampleProfile(proAlertsEnabled = true),
        activeCircle = sampleCircle,
        feed = UiState.Success(listings),
        visibleListings = listings,
        proAlertMatches = listOf(listings.first()),
        filters = FeedFiltersState(),
        isBusy = false,
        showCreateSheet = false,
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

  @Test
  fun `the feed`() {
    showFeed()
    compose.assertNoTextIsCutOff()
  }

  @Test
  fun `my own listing, open`() {
    showFeed()
    compose.onNodeWithTag("listing_card_mine").performClick()
    compose.onNodeWithTag("edit_listing_button").assertExists()

    compose.assertNoTextIsCutOff()
  }

  @Test
  fun `a neighbour's listing, open`() {
    showFeed()
    compose.onNodeWithTag("listing_card_tool").performClick()
    compose.onNodeWithTag("confirm_escrow_book_button").assertExists()

    compose.assertNoTextIsCutOff()
  }

  @Test
  fun `the escrow ledger`() {
    compose.setThemedContent {
      EscrowScreen(
        currentUserId = ME,
        ledger =
          UiState.Success(
            listOf(
              sampleBooking("a", Booking.STATUS_REQUESTED, iOwnTheListing = true),
              sampleBooking("b", Booking.STATUS_REQUESTED, iOwnTheListing = false),
              sampleBooking("c", Booking.STATUS_ESCROW_HELD, iOwnTheListing = true),
              sampleBooking("d", Booking.STATUS_IN_PROGRESS, iOwnTheListing = true, notes = "Side gate"),
              sampleBooking("e", Booking.STATUS_IN_PROGRESS, iOwnTheListing = false),
            )
          ),
        isBusy = false,
        onAdvanceStatus = { _, _, _ -> },
        onRetry = {},
      )
    }

    compose.assertNoTextIsCutOff()
  }

  @Test
  fun `the trust hub`() {
    compose.setThemedContent {
      TrustHubScreen(
        profile = sampleProfile(proAlertsEnabled = true),
        totals = BookingTotals(),
        activeCircle = sampleCircle,
        circlesCount = 2,
        proAlertMatches = listOf(listings.first()),
        isBusy = false,
        onSaveProAlerts = { _, _, _ -> },
        onOpenBlockSwitcher = {},
        onInspectProMatch = {},
        onShowFeedback = {},
        onSignOut = {},
      )
    }

    compose.assertNoTextIsCutOff()
  }

  @Test
  fun `onboarding`() {
    compose.setThemedContent {
      CircleOnboardingScreen(isBusy = false, onCreateCircle = { _, _ -> }, onJoinCircle = {}, onSignOut = {})
    }
    compose.assertNoTextIsCutOff()

    compose.onNodeWithTag("tab_join_block").performClick()
    compose.assertNoTextIsCutOff()
  }

  @Test
  fun `the block switcher`() {
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

    compose.assertNoTextIsCutOff()
  }
}

@Config(fontScale = 1.0f)
class TextFitAtDefaultSizeTest : TextFitTest()

@Config(fontScale = 2.0f)
class TextFitAtLargestSizeTest : TextFitTest()
