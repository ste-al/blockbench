package com.example.ui.screens

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.example.model.BlockCircle
import com.example.model.Booking
import com.example.model.Listing
import com.example.model.UserProfile
import com.example.ui.theme.BlockBenchTheme
import org.junit.Assert.assertEquals

/**
 * Screen tests run the real composables under Robolectric. The tall, phone-wide configuration
 * keeps whole screens composed, so lazy lists do not need scrolling to reach a node.
 */
internal const val TALL_PHONE = "w411dp-h2400dp-xxhdpi"

/** A small phone (360dp is the most common narrow width), as tall as [TALL_PHONE] and more. */
internal const val NARROW_PHONE = "w360dp-h3200dp-xxhdpi"

internal const val ME = "uid_me"
internal const val NEIGHBOR = "uid_bob"

internal fun ComposeContentTestRule.setThemedContent(content: @Composable () -> Unit) {
  setContent { BlockBenchTheme(darkTheme = false) { Surface { content() } } }
}

/**
 * Fails when something on screen that a person can act on has nothing a screen reader could call
 * it by: no text and no content description, of its own or merged in from what it contains.
 *
 * Material drops the semantics of some slots (a tab's icon next to its label, the text of an
 * extended floating action button), so a control can look labelled and still be announced as a
 * bare "button". Nothing short of a check like this one notices.
 */
internal fun ComposeTestRule.assertEveryControlHasAName() {
  val unnamed =
    onAllNodes(isRoot())
      .fetchSemanticsNodes()
      .flatMap { it.selfAndDescendants() }
      .filter { it.isControl && it.accessibleName.isBlank() }
      .map { it.config.getOrNull(SemanticsProperties.TestTag) ?: "untagged, at ${it.boundsInRoot}" }
  assertEquals("Controls a screen reader has no name for", emptyList<String>(), unnamed)
}

/**
 * Fails when a piece of text does not fit and is cut off instead of wrapping or ending in an
 * ellipsis: a button label clipped mid-word, a badge squeezed to a few letters. Text that is
 * shortened with an ellipsis on purpose, such as a long name, is left alone.
 */
internal fun ComposeTestRule.assertNoTextIsCutOff() {
  val cutOff =
    onAllNodes(isRoot(), useUnmergedTree = true)
      .fetchSemanticsNodes()
      .flatMap { it.selfAndDescendants() }
      .mapNotNull { node -> node.textLayout()?.takeIf { it.isCutOff }?.layoutInput?.text?.text }
  assertEquals("Text cut off where it does not fit", emptyList<String>(), cutOff)
}

private fun SemanticsNode.textLayout(): TextLayoutResult? {
  val results = mutableListOf<TextLayoutResult>()
  config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
  return results.firstOrNull()
}

/**
 * The layout the semantics action hands out is laid out again at the full width the text was
 * offered, so its width says nothing about how wide the text is drawn and `hasVisualOverflow`
 * is set for almost everything. What does show text that is cut off is that it needs more lines
 * than it may have, or more height than it was given. (A label that may not wrap shows up as
 * needing more lines, since this layout wraps it.)
 */
private val TextLayoutResult.isCutOff: Boolean
  get() =
    layoutInput.overflow == TextOverflow.Clip &&
      (multiParagraph.didExceedMaxLines || size.height < multiParagraph.height)

private fun SemanticsNode.selfAndDescendants(): List<SemanticsNode> =
  listOf(this) + children.flatMap { it.selfAndDescendants() }

private val SemanticsNode.isControl: Boolean
  get() = SemanticsActions.OnClick in config || SemanticsActions.SetProgress in config

private val SemanticsNode.accessibleName: String
  get() =
    (config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text })
      .joinToString(" ")

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
