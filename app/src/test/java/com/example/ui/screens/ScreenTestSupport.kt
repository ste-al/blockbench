package com.example.ui.screens

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.example.model.BlockCircle
import com.example.model.Booking
import com.example.model.Listing
import com.example.model.UserProfile
import com.example.ui.theme.BlockBenchTheme

/**
 * Screen tests run the real composables under Robolectric. The tall, phone-wide configuration
 * keeps whole screens composed, so lazy lists do not need scrolling to reach a node.
 */
internal const val TALL_PHONE = "w411dp-h2400dp-xxhdpi"

internal const val ME = "uid_me"
internal const val NEIGHBOR = "uid_bob"

internal fun ComposeContentTestRule.setThemedContent(content: @Composable () -> Unit) {
  setContent { BlockBenchTheme(darkTheme = false) { Surface { content() } } }
}

internal val sampleCircle =
  BlockCircle(
    id = "circle_oak",
    creatorUid = ME,
    name = "Oak & 14th Block",
    inviteCode = "OAK7K2MQ",
    radiusMiles = 1.5,
    memberUids = listOf(ME, NEIGHBOR),
  )

internal fun sampleProfile(proAlertsEnabled: Boolean = false) =
  UserProfile(
    id = ME,
    uid = ME,
    displayName = "Alice Miller",
    activeCircleId = sampleCircle.id,
    proAlertsEnabled = proAlertsEnabled,
    alertRadiusMiles = 1.5,
    alertCategories = listOf("Power Tools", "Quick Fix"),
  )

internal fun sampleListing(
  id: String,
  mine: Boolean,
  status: String = Listing.STATUS_AVAILABLE,
  kind: String = Listing.KIND_TOOL,
  activeBookingId: String = if (status == Listing.STATUS_RESERVED) "booking_x" else "",
) =
  Listing(
    id = id,
    circleId = sampleCircle.id,
    ownerUid = if (mine) ME else NEIGHBOR,
    ownerName = if (mine) "Alice Miller" else "Bob Carter",
    kind = kind,
    title = if (kind == Listing.KIND_TOOL) "Pressure Washer $id" else "Gate Latch Job $id",
    description = "Ready on the side porch.",
    category = "Yard & Outdoor",
    rateCents = 1800L,
    depositCents = if (kind == Listing.KIND_TOOL) 4000L else 0L,
    distanceMiles = 0.3,
    instantPickup = true,
    status = status,
    activeBookingId = activeBookingId,
  )

/** A tool rental between me and one neighbour; [iOwnTheListing] decides who lends and who borrows. */
internal fun sampleBooking(
  id: String,
  status: String,
  iOwnTheListing: Boolean,
  notes: String = "",
) =
  Booking(
    id = id,
    listingId = "listing_$id",
    listingTitle = "Pressure Washer",
    listingKind = Listing.KIND_TOOL,
    circleId = sampleCircle.id,
    requesterUid = if (iOwnTheListing) NEIGHBOR else ME,
    requesterName = if (iOwnTheListing) "Bob Carter" else "Alice Miller",
    providerUid = if (iOwnTheListing) ME else NEIGHBOR,
    providerName = if (iOwnTheListing) "Alice Miller" else "Bob Carter",
    participantUids = listOf(if (iOwnTheListing) NEIGHBOR else ME, if (iOwnTheListing) ME else NEIGHBOR),
    grossAmountCents = 1800L,
    depositCents = 4000L,
    platformFeeCents = 90L,
    providerNetCents = 1710L,
    status = status,
    notes = notes,
  )
