package com.example.data.repository

import com.example.model.BlockCircle
import com.example.model.Booking
import com.example.model.Listing
import com.example.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Everything the UI layer needs from the data layer. [BlockBenchRepository] implements it on top
 * of Firestore; having it as an interface lets the ViewModel be tested against an in-memory fake.
 *
 * The `observe…` flows emit the current value and every later change, and fail with the
 * underlying error if access is lost. The suspending functions either complete or throw; a
 * [com.example.data.UserFacingException] carries a message that can be shown as it is.
 */
interface BlockBenchDataSource {

  fun observeUserProfile(uid: String): Flow<UserProfile?>

  fun observeMyCircles(uid: String): Flow<List<BlockCircle>>

  /** The feed of one block, newest first. */
  fun observeCircleListings(circleId: String): Flow<List<Listing>>

  fun observeMyBookings(uid: String): Flow<List<Booking>>

  /** Creates the signed-in user's profile on first launch; returns the existing one otherwise. */
  suspend fun ensureUserProfile(displayName: String): UserProfile

  suspend fun updateProAlertsSettings(
    enabled: Boolean,
    radiusMiles: Double,
    categories: List<String>,
  )

  /** Raises the profile's stored summary to these totals; never lowers it, never throws. */
  suspend fun syncCompletedEarningsToProfile(
    completedHandoffs: Long,
    earnedCents: Long,
    feesCents: Long,
  )

  suspend fun createBlockCircle(
    circleName: String,
    radiusMiles: Double = BlockBenchRepository.DEFAULT_RADIUS_MILES,
  ): BlockCircle

  suspend fun joinCircleByInviteCode(rawCode: String): BlockCircle

  suspend fun switchActiveCircle(circle: BlockCircle)

  /** Returns the number of the user's own listings that were removed from the block. */
  suspend fun leaveBlockCircle(circle: BlockCircle, nextActiveCircle: BlockCircle?): Int

  /** Returns the id of the new listing. */
  suspend fun createListing(
    circle: BlockCircle,
    ownerName: String,
    ownerTrustScore: Double,
    kind: String,
    title: String,
    description: String,
    category: String,
    rateCents: Long,
    depositCents: Long,
    distanceMiles: Double,
    instantPickup: Boolean,
    photoUri: String = "",
  ): String

  suspend fun updateListingDetails(
    listingId: String,
    title: String,
    description: String,
    category: String,
    rateCents: Long,
    depositCents: Long,
    distanceMiles: Double,
    instantPickup: Boolean,
    photoUri: String,
  )

  suspend fun setListingPaused(listingId: String, paused: Boolean)

  suspend fun deleteListing(listingId: String)

  /** Returns the id of the new booking. */
  suspend fun createBookingRequest(
    listing: Listing,
    currentUserName: String,
    notes: String,
  ): String

  suspend fun advanceBookingStatus(
    booking: Booking,
    nextStatus: String,
    updatedNotes: String = booking.notes,
  )
}
