package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.math.BigDecimal
import java.math.RoundingMode

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}

fun <T> UiState<T>.dataOrNull(): T? = (this as? UiState.Success)?.data

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val uid: String = "",
  val displayName: String = "",
  val neighborhoodLabel: String = "",
  val activeCircleId: String = "",
  val activeCircleName: String = "",
  val activeInviteCode: String = "",
  val trustScore: Double = 5.0,
  val completedHandoffs: Long = 0L,
  val onTimeReturns: Long = 0L,
  val totalEarnedCents: Long = 0L,
  val totalFeesSavedOrPaidCents: Long = 0L,
  val proAlertsEnabled: Boolean = false,
  val alertRadiusMiles: Double = 1.5,
  val alertCategories: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class BlockCircle(
  @DocumentId val id: String = "",
  val creatorUid: String = "",
  val name: String = "",
  val inviteCode: String = "",
  val radiusMiles: Double = 1.5,
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  companion object {
    /** Enforced by firestore.rules; a full block refuses new neighbours. */
    const val MAX_MEMBERS = 50
  }
}

@IgnoreExtraProperties
data class CircleInvite(
  @DocumentId val id: String = "",
  val inviteCode: String = "",
  val circleId: String = "",
  val circleName: String = "",
  val creatorUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

/**
 * A tool to lend or a micro-job to claim, visible to the members of [circleId].
 *
 * [status] is [STATUS_RESERVED] exactly while [activeBookingId] names a booking that is still in
 * flight. That pairing is maintained atomically with the booking and enforced by firestore.rules.
 */
@IgnoreExtraProperties
data class Listing(
  @DocumentId val id: String = "",
  val circleId: String = "",
  val ownerUid: String = "",
  val ownerName: String = "",
  val ownerTrustScore: Double = 5.0,
  val kind: String = KIND_TOOL,
  val title: String = "",
  val description: String = "",
  val category: String = "",
  val rateCents: Long = 0L,
  val depositCents: Long = 0L,
  val distanceMiles: Double = 0.25,
  val instantPickup: Boolean = true,
  /** Empty, or a small inline JPEG thumbnail as a `data:` URI (see ListingPhotoCodec). */
  val photoUri: String = "",
  val status: String = STATUS_AVAILABLE,
  val activeBookingId: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  @get:Exclude
  val isMicroJob: Boolean
    get() = kind == KIND_MICRO_JOB

  /**
   * Marked reserved by an app version that did not yet record which booking holds it. Nothing
   * will ever release such a reservation, so its owner is allowed to clear it by hand.
   */
  @get:Exclude
  val hasUntrackedReservation: Boolean
    get() = status == STATUS_RESERVED && activeBookingId.isBlank()

  companion object {
    const val KIND_TOOL = "tool"
    const val KIND_MICRO_JOB = "micro_job"
    const val STATUS_AVAILABLE = "available"
    const val STATUS_RESERVED = "reserved"
    const val STATUS_PAUSED = "paused"

    const val MIN_RATE_CENTS = 100L
    const val MAX_AMOUNT_CENTS = 500_000L
    const val MAX_TITLE_LENGTH = 80
    const val MAX_DESCRIPTION_LENGTH = 400
  }
}

/**
 * An escrow ledger entry between the neighbour who pays ([requesterUid]) and the neighbour who
 * earns ([providerUid]). The money terms are copied from the listing when the booking is created
 * and never change afterwards.
 */
@IgnoreExtraProperties
data class Booking(
  @DocumentId val id: String = "",
  val listingId: String = "",
  val listingTitle: String = "",
  val listingKind: String = Listing.KIND_TOOL,
  val circleId: String = "",
  val requesterUid: String = "",
  val requesterName: String = "",
  val providerUid: String = "",
  val providerName: String = "",
  val participantUids: List<String> = emptyList(),
  val grossAmountCents: Long = 0L,
  val depositCents: Long = 0L,
  val platformFeeCents: Long = 0L,
  val providerNetCents: Long = 0L,
  val status: String = STATUS_REQUESTED,
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  @get:Exclude
  val isMicroJob: Boolean
    get() = listingKind == Listing.KIND_MICRO_JOB

  /** Neither completed nor cancelled: the listing is still held by this booking. */
  @get:Exclude
  val isActive: Boolean
    get() = status != STATUS_COMPLETED && status != STATUS_CANCELLED

  /**
   * The neighbour who posted the listing: the lender of a tool, or the poster of a micro-job.
   * They approve the request and confirm completion; the other participant is the one who
   * created the booking.
   */
  @get:Exclude
  val listingOwnerUid: String
    get() = if (isMicroJob) requesterUid else providerUid

  @get:Exclude
  val listingOwnerName: String
    get() = if (isMicroJob) requesterName else providerName

  companion object {
    const val STATUS_REQUESTED = "requested"
    const val STATUS_ESCROW_HELD = "escrow_held"
    const val STATUS_IN_PROGRESS = "in_progress"
    const val STATUS_COMPLETED = "completed"
    const val STATUS_CANCELLED = "cancelled"

    const val MAX_NOTES_LENGTH = 250
  }
}

/**
 * The escrow state machine, mirrored one-to-one by the booking `update` rule in firestore.rules:
 *
 * ```
 * requested ──owner──▶ escrow_held ──either──▶ in_progress ──owner──▶ completed
 *     └──────either──────┴───────────▶ cancelled
 * ```
 *
 * "owner" is [Booking.listingOwnerUid]. The UI uses this to offer only the actions the signed-in
 * neighbour may actually take, and the repository uses it to fail fast with a readable message.
 */
object EscrowWorkflow {
  fun allowedNextStatuses(booking: Booking, uid: String): Set<String> {
    if (uid != booking.requesterUid && uid != booking.providerUid) return emptySet()
    val isListingOwner = uid == booking.listingOwnerUid
    return when (booking.status) {
      Booking.STATUS_REQUESTED ->
        if (isListingOwner) {
          setOf(Booking.STATUS_ESCROW_HELD, Booking.STATUS_CANCELLED)
        } else {
          setOf(Booking.STATUS_CANCELLED)
        }
      Booking.STATUS_ESCROW_HELD -> setOf(Booking.STATUS_IN_PROGRESS, Booking.STATUS_CANCELLED)
      Booking.STATUS_IN_PROGRESS ->
        if (isListingOwner) setOf(Booking.STATUS_COMPLETED) else emptySet()
      else -> emptySet()
    }
  }

  fun canTransition(booking: Booking, uid: String, nextStatus: String): Boolean =
    nextStatus in allowedNextStatuses(booking, uid)

  /** True when [uid] can move [booking] forward (approve, start the handoff, confirm completion). */
  fun awaitsActionFrom(booking: Booking, uid: String): Boolean =
    allowedNextStatuses(booking, uid).any { it != Booking.STATUS_CANCELLED }

  /** Moving to these statuses frees the listing for the next neighbour. */
  fun releasesListing(nextStatus: String): Boolean =
    nextStatus == Booking.STATUS_COMPLETED || nextStatus == Booking.STATUS_CANCELLED
}

/**
 * Lifetime figures derived from the bookings a user took part in. These are the source of truth
 * for the Trust Card; the counters stored on the profile are only a summary of them.
 */
data class BookingTotals(
  val completedHandoffs: Long = 0L,
  val earnedCents: Long = 0L,
  val feesCents: Long = 0L,
) {
  /** True when any figure is ahead of what [profile] has stored. */
  fun exceeds(profile: UserProfile): Boolean =
    completedHandoffs > profile.completedHandoffs ||
      earnedCents > profile.totalEarnedCents ||
      feesCents > profile.totalFeesSavedOrPaidCents

  /** Each figure, but never less than what [profile] already records. */
  fun atLeast(profile: UserProfile?): BookingTotals =
    if (profile == null) {
      this
    } else {
      BookingTotals(
        completedHandoffs = maxOf(completedHandoffs, profile.completedHandoffs),
        earnedCents = maxOf(earnedCents, profile.totalEarnedCents),
        feesCents = maxOf(feesCents, profile.totalFeesSavedOrPaidCents),
      )
    }

  companion object {
    fun from(bookings: List<Booking>, uid: String): BookingTotals {
      val completed = bookings.filter { it.status == Booking.STATUS_COMPLETED }
      return BookingTotals(
        completedHandoffs = completed.size.toLong(),
        earnedCents = completed.filter { it.providerUid == uid }.sumOf { it.providerNetCents },
        feesCents = completed.sumOf { it.platformFeeCents },
      )
    }
  }
}

/** What the listing form collects, for both new listings and edits. */
data class ListingDraft(
  val kind: String,
  val title: String,
  val description: String,
  val category: String,
  val rateCents: Long,
  val depositCents: Long,
  val distanceMiles: Double,
  val instantPickup: Boolean,
  val photoUri: String,
)

data class EscrowFeeBreakdown(
  val grossAmountCents: Long,
  val depositCents: Long,
  val platformFeeCents: Long,
  val providerNetCents: Long,
  val totalEscrowHoldCents: Long,
) {
  companion object {
    const val PLATFORM_FEE_PERCENT = 5L

    /**
     * The platform fee is [PLATFORM_FEE_PERCENT] of the rate, rounded half up to a whole cent and
     * never less than one cent; it comes out of the provider's payout. Integer arithmetic only:
     * `platformFeeFor()` in firestore.rules recomputes the same figure and rejects any booking
     * that disagrees by even a cent.
     */
    fun calculate(rateCents: Long, depositCents: Long): EscrowFeeBreakdown {
      val feeCents = ((rateCents * PLATFORM_FEE_PERCENT + 50L) / 100L).coerceAtLeast(1L)
      val netCents = (rateCents - feeCents).coerceAtLeast(0L)
      return EscrowFeeBreakdown(
        grossAmountCents = rateCents,
        depositCents = depositCents,
        platformFeeCents = feeCents,
        providerNetCents = netCents,
        totalEscrowHoldCents = rateCents + depositCents,
      )
    }
  }
}

object CraftCategories {
  val all =
    listOf(
      "Power Tools",
      "Yard & Outdoor",
      "Ladders & Reach",
      "Quick Fix",
      "Haul & Cleanup",
      "Auto & Garage",
    )

  private val DOLLARS_PATTERN = Regex("""^(\d{1,9})?(?:[.,](\d{0,2}))?$""")

  /**
   * Parses a dollar amount typed by the user ("18", "18.5", "18,75", ".5") into cents, or null
   * when it is not a plain non-negative amount with at most two decimals. A comma is accepted as
   * the decimal separator because that is what the numeric keyboard offers in many locales.
   */
  fun parseDollarsToCents(input: String): Long? {
    val trimmed = input.trim()
    val match = DOLLARS_PATTERN.matchEntire(trimmed) ?: return null
    val whole = match.groupValues[1]
    val fraction = match.groupValues[2]
    if (whole.isEmpty() && fraction.isEmpty()) return null
    return BigDecimal("${whole.ifEmpty { "0" }}.${fraction.ifEmpty { "0" }}")
      .movePointRight(2)
      .setScale(0, RoundingMode.UNNECESSARY)
      .toLong()
  }

  /** Formats cents for an editable amount field: "18" for 1800, "18.50" for 1850. */
  fun centsToDollarsInput(cents: Long): String {
    val dollars = cents / 100
    val remainder = cents % 100
    return if (remainder == 0L) "$dollars" else "$dollars.${remainder.toString().padStart(2, '0')}"
  }
}
