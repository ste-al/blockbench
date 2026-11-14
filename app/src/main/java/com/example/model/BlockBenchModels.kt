package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.roundToLong

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}

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
)

@IgnoreExtraProperties
data class CircleInvite(
  @DocumentId val id: String = "",
  val inviteCode: String = "",
  val circleId: String = "",
  val circleName: String = "",
  val creatorUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Listing(
  @DocumentId val id: String = "",
  val circleId: String = "",
  val ownerUid: String = "",
  val ownerName: String = "",
  val ownerTrustScore: Double = 5.0,
  val memberUids: List<String> = emptyList(),
  val kind: String = KIND_TOOL,
  val title: String = "",
  val description: String = "",
  val category: String = "",
  val rateCents: Long = 0L,
  val depositCents: Long = 0L,
  val distanceMiles: Double = 0.25,
  val instantPickup: Boolean = true,
  val photoUri: String = "",
  val status: String = STATUS_AVAILABLE,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  companion object {
    const val KIND_TOOL = "tool"
    const val KIND_MICRO_JOB = "micro_job"
    const val STATUS_AVAILABLE = "available"
    const val STATUS_RESERVED = "reserved"
    const val STATUS_PAUSED = "paused"
  }
}

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
  companion object {
    const val STATUS_REQUESTED = "requested"
    const val STATUS_ESCROW_HELD = "escrow_held"
    const val STATUS_IN_PROGRESS = "in_progress"
    const val STATUS_COMPLETED = "completed"
    const val STATUS_CANCELLED = "cancelled"
  }
}

data class EscrowFeeBreakdown(
  val grossAmountCents: Long,
  val depositCents: Long,
  val platformFeeCents: Long,
  val providerNetCents: Long,
  val totalEscrowHoldCents: Long,
) {
  companion object {
    const val PLATFORM_FEE_RATE = 0.05

    fun calculate(rateCents: Long, depositCents: Long): EscrowFeeBreakdown {
      val feeCents = (rateCents * PLATFORM_FEE_RATE).roundToLong().coerceAtLeast(1L)
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

  fun parseDollarsToCents(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    if (!Regex("^\\d+(\\.\\d{0,2})?$").matches(trimmed)) return null
    val parsed = trimmed.toDoubleOrNull() ?: return null
    return (parsed * 100.0).roundToLong()
  }
}
