package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.model.BlockCircle
import com.example.model.Booking
import com.example.model.CircleInvite
import com.example.model.EscrowFeeBreakdown
import com.example.model.Listing
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class BlockBenchRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  // --- User Profile ---

  fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
    val path = "users/$uid"
    emitAll(
      db.collection("users")
        .document(uid)
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(
              UserProfile::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
          } else {
            null
          }
        }
        .catch { error ->
          logFlowError(error, OperationType.GET, path, uid)
          throw error
        }
    )
  }

  suspend fun ensureUserProfile(
    displayName: String,
    neighborhoodLabel: String = "Neighborhood Block",
  ): UserProfile {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    return try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        checkNotNull(
          existing.toObject(
            UserProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        )
      } else {
        val cleanName = displayName.trim().take(60).ifEmpty { "Neighbor" }
        val cleanLabel = neighborhoodLabel.trim().take(80).ifEmpty { "My Block" }
        val payload =
          mapOf(
            "uid" to uid,
            "displayName" to cleanName,
            "neighborhoodLabel" to cleanLabel,
            "activeCircleId" to "",
            "activeCircleName" to "",
            "activeInviteCode" to "",
            "trustScore" to 5.0,
            "completedHandoffs" to 0L,
            "onTimeReturns" to 0L,
            "totalEarnedCents" to 0L,
            "totalFeesSavedOrPaidCents" to 0L,
            "proAlertsEnabled" to false,
            "alertRadiusMiles" to 1.5,
            "alertCategories" to listOf("Power Tools", "Quick Fix"),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.set(payload).await()
        UserProfile(
          id = uid,
          uid = uid,
          displayName = cleanName,
          neighborhoodLabel = cleanLabel,
          alertCategories = listOf("Power Tools", "Quick Fix"),
          createdAt = Timestamp.now(),
          updatedAt = Timestamp.now(),
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  suspend fun updateProAlertsSettings(
    enabled: Boolean,
    radiusMiles: Double,
    categories: List<String>,
  ) {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    val sanitizedCategories = categories.distinct().take(8)
    val boundedRadius = radiusMiles.coerceIn(0.25, 10.0)
    try {
      docRef
        .update(
          mapOf(
            "proAlertsEnabled" to enabled,
            "alertRadiusMiles" to boundedRadius,
            "alertCategories" to sanitizedCategories,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun updateNeighborhoodLabel(label: String) {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    val cleanLabel = label.trim().take(80)
    try {
      docRef
        .update(
          mapOf(
            "neighborhoodLabel" to cleanLabel,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun syncCompletedEarningsToProfile(
    expectedHandoffs: Long,
    expectedEarnedCents: Long,
    expectedFeesCents: Long,
  ) {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    try {
      docRef
        .update(
          mapOf(
            "completedHandoffs" to expectedHandoffs.coerceIn(0L, 100_000L),
            "onTimeReturns" to expectedHandoffs.coerceIn(0L, 100_000L),
            "totalEarnedCents" to expectedEarnedCents.coerceIn(0L, 1_000_000_000L),
            "totalFeesSavedOrPaidCents" to expectedFeesCents.coerceIn(0L, 1_000_000_000L),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
    }
  }

  // --- Neighborhood Block Circles & One-Tap Invite ---

  fun observeMyCircles(uid: String): Flow<List<BlockCircle>> = flow {
    val path = "circles"
    emitAll(
      db.collection(path)
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            BlockCircle::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, path, uid)
          throw error
        }
    )
  }

  suspend fun createBlockCircle(
    circleName: String,
    radiusMiles: Double = 1.5,
  ): BlockCircle {
    val uid = requireUserId()
    val cleanName = circleName.trim().take(60)
    require(cleanName.length >= 2) { "Block name must be at least 2 characters." }
    val boundedRadius = radiusMiles.coerceIn(0.25, 10.0)

    val circleRef = db.collection("circles").document()
    val inviteCode = generateInviteCode(cleanName)
    val inviteRef = db.collection("circleInvites").document(inviteCode)
    val userRef = db.collection("users").document(uid)

    return try {
      circleRef
        .set(
          mapOf(
            "creatorUid" to uid,
            "name" to cleanName,
            "inviteCode" to inviteCode,
            "radiusMiles" to boundedRadius,
            "memberUids" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      val batch = db.batch()
      batch.set(
        inviteRef,
        mapOf(
          "inviteCode" to inviteCode,
          "circleId" to circleRef.id,
          "circleName" to cleanName,
          "creatorUid" to uid,
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.update(
        userRef,
        mapOf(
          "activeCircleId" to circleRef.id,
          "activeCircleName" to cleanName,
          "activeInviteCode" to inviteCode,
          "neighborhoodLabel" to cleanName,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      BlockCircle(
        id = circleRef.id,
        creatorUid = uid,
        name = cleanName,
        inviteCode = inviteCode,
        radiusMiles = boundedRadius,
        memberUids = listOf(uid),
        createdAt = Timestamp.now(),
        updatedAt = Timestamp.now(),
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, circleRef.path)
      throw e
    }
  }

  suspend fun joinCircleByInviteCode(rawCode: String): BlockCircle {
    val uid = requireUserId()
    val code = rawCode.trim().uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "")
    require(code.length in 4..12) { "Invite code must be 4-12 letters/digits." }

    val inviteRef = db.collection("circleInvites").document(code)
    val inviteSnap =
      try {
        inviteRef.get().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, inviteRef.path)
        throw e
      }

    if (!inviteSnap.exists()) {
      throw IllegalArgumentException("No block circle found for invite code $code.")
    }

    val invite =
      checkNotNull(
        inviteSnap.toObject(
          CircleInvite::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      )
    val circleRef = db.collection("circles").document(invite.circleId)

    try {
      circleRef
        .update(
          mapOf(
            "memberUids" to FieldValue.arrayUnion(uid),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      db.collection("users")
        .document(uid)
        .update(
          mapOf(
            "activeCircleId" to invite.circleId,
            "activeCircleName" to invite.circleName,
            "activeInviteCode" to invite.inviteCode,
            "neighborhoodLabel" to invite.circleName,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      val joinedCircle =
        circleRef.get().await().toObject(
          BlockCircle::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        ) ?: BlockCircle(
          id = invite.circleId,
          creatorUid = invite.creatorUid,
          name = invite.circleName,
          inviteCode = invite.inviteCode,
          memberUids = listOf(invite.creatorUid, uid),
        )
      return joinedCircle
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, circleRef.path)
      throw e
    }
  }

  suspend fun switchActiveCircle(circle: BlockCircle) {
    val uid = requireUserId()
    val userRef = db.collection("users").document(uid)
    try {
      userRef
        .update(
          mapOf(
            "activeCircleId" to circle.id,
            "activeCircleName" to circle.name,
            "activeInviteCode" to circle.inviteCode,
            "neighborhoodLabel" to circle.name,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, userRef.path)
      throw e
    }
  }

  suspend fun leaveBlockCircle(
    circle: BlockCircle,
    fallbackCircle: BlockCircle?,
  ) {
    val uid = requireUserId()
    require(circle.creatorUid != uid) {
      "Block creators cannot leave their own circle."
    }
    val circleRef = db.collection("circles").document(circle.id)
    val userRef = db.collection("users").document(uid)
    try {
      circleRef
        .update(
          mapOf(
            "memberUids" to FieldValue.arrayRemove(uid),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      userRef
        .update(
          mapOf(
            "activeCircleId" to (fallbackCircle?.id ?: ""),
            "activeCircleName" to (fallbackCircle?.name ?: ""),
            "activeInviteCode" to (fallbackCircle?.inviteCode ?: ""),
            "neighborhoodLabel" to (fallbackCircle?.name ?: "My Block"),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, circleRef.path)
      throw e
    }
  }

  suspend fun syncListingsMembershipForCircle(
    circleId: String,
    listingIds: List<String>,
    memberUidsToSync: List<String> = emptyList(),
  ) {
    val uid = requireUserId()
    val toUnion = memberUidsToSync.ifEmpty { listOf(uid) }.distinct().take(50)
    for (listingId in listingIds) {
      val ref = db.collection("listings").document(listingId)
      try {
        ref
          .update(
            mapOf(
              "memberUids" to FieldValue.arrayUnion(*toUnion.toTypedArray()),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "listings/$listingId (circle=$circleId)")
      }
    }
  }

  // --- Block Feed Listings (Tools & Micro-Jobs) ---

  fun observeCircleListings(uid: String): Flow<List<Listing>> = flow {
    val path = "listings"
    emitAll(
      db.collection(path)
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(
              Listing::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, path, uid)
          throw error
        }
    )
  }

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
  ): String {
    val uid = requireUserId()
    val cleanTitle = title.trim().take(80)
    require(cleanTitle.length >= 3) { "Title must be at least 3 characters." }
    require(rateCents in 100L..500_000L) { "Rate must be between $1 and $5,000." }
    require(depositCents in 0L..500_000L) { "Deposit must be between $0 and $5,000." }

    val memberUids = (circle.memberUids + uid).distinct().take(50)
    val docRef = db.collection("listings").document()
    val payload =
      mapOf(
        "circleId" to circle.id,
        "ownerUid" to uid,
        "ownerName" to ownerName.trim().take(60).ifEmpty { "Neighbor" },
        "ownerTrustScore" to ownerTrustScore.coerceIn(0.0, 5.0),
        "memberUids" to memberUids,
        "kind" to kind,
        "title" to cleanTitle,
        "description" to description.trim().take(400),
        "category" to category.trim().take(40).ifEmpty { "Power Tools" },
        "rateCents" to rateCents,
        "depositCents" to depositCents,
        "distanceMiles" to distanceMiles.coerceIn(0.05, 10.0),
        "instantPickup" to instantPickup,
        "photoUri" to photoUri.trim().take(500),
        "status" to Listing.STATUS_AVAILABLE,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

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
  ) {
    requireUserId()
    val cleanTitle = title.trim().take(80)
    require(cleanTitle.length >= 3) { "Title must be at least 3 characters." }
    require(rateCents in 100L..500_000L) { "Rate must be between $1 and $5,000." }
    require(depositCents in 0L..500_000L) { "Deposit must be between $0 and $5,000." }

    val docRef = db.collection("listings").document(listingId)
    try {
      docRef
        .update(
          mapOf(
            "title" to cleanTitle,
            "description" to description.trim().take(400),
            "category" to category.trim().take(40).ifEmpty { "Power Tools" },
            "rateCents" to rateCents,
            "depositCents" to depositCents,
            "distanceMiles" to distanceMiles.coerceIn(0.05, 10.0),
            "instantPickup" to instantPickup,
            "photoUri" to photoUri.trim().take(500),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun updateListingStatus(listingId: String, newStatus: String) {
    requireUserId()
    val docRef = db.collection("listings").document(listingId)
    try {
      docRef
        .update(
          mapOf(
            "status" to newStatus,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteListing(listingId: String) {
    requireUserId()
    val docRef = db.collection("listings").document(listingId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  // --- Micro-Escrow Bookings ---

  fun observeMyBookings(uid: String): Flow<List<Booking>> = flow {
    val path = "bookings"
    emitAll(
      db.collection(path)
        .whereArrayContains("participantUids", uid)
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(
              Booking::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, path, uid)
          throw error
        }
    )
  }

  suspend fun createBookingRequest(
    listing: Listing,
    currentUserName: String,
    notes: String,
  ): String {
    val uid = requireUserId()
    require(listing.ownerUid != uid) { "You cannot book your own listing." }
    val isMicroJob = listing.kind == Listing.KIND_MICRO_JOB
    // For tools: borrower is requester, tool owner is provider.
    // For micro-jobs: listing owner is requester (hiring help), claiming neighbor is provider (worker).
    val requesterUid = if (isMicroJob) listing.ownerUid else uid
    val requesterName = if (isMicroJob) listing.ownerName else currentUserName
    val providerUid = if (isMicroJob) uid else listing.ownerUid
    val providerName = if (isMicroJob) currentUserName else listing.ownerName

    val breakdown = EscrowFeeBreakdown.calculate(listing.rateCents, listing.depositCents)
    val bookingRef = db.collection("bookings").document()
    val listingRef = db.collection("listings").document(listing.id)

    val payload =
      mapOf(
        "listingId" to listing.id,
        "listingTitle" to listing.title.take(80),
        "listingKind" to listing.kind,
        "circleId" to listing.circleId,
        "requesterUid" to requesterUid,
        "requesterName" to requesterName.trim().take(60).ifEmpty { "Neighbor" },
        "providerUid" to providerUid,
        "providerName" to providerName.trim().take(60).ifEmpty { "Neighbor" },
        "participantUids" to listOf(requesterUid, providerUid),
        "grossAmountCents" to breakdown.grossAmountCents,
        "depositCents" to breakdown.depositCents,
        "platformFeeCents" to breakdown.platformFeeCents,
        "providerNetCents" to breakdown.providerNetCents,
        "status" to Booking.STATUS_REQUESTED,
        "notes" to notes.trim().take(250),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )

    return try {
      val batch = db.batch()
      batch.set(bookingRef, payload)
      batch.update(
        listingRef,
        mapOf(
          "status" to Listing.STATUS_RESERVED,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      bookingRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, bookingRef.path)
      throw e
    }
  }

  suspend fun advanceBookingStatus(
    booking: Booking,
    nextStatus: String,
    updatedNotes: String = booking.notes,
  ) {
    val uid = requireUserId()
    val bookingRef = db.collection("bookings").document(booking.id)
    val listingRef = db.collection("listings").document(booking.listingId)
    val isReleasingListing =
      nextStatus == Booking.STATUS_COMPLETED || nextStatus == Booking.STATUS_CANCELLED

    try {
      val batch = db.batch()
      batch.update(
        bookingRef,
        mapOf(
          "status" to nextStatus,
          "notes" to updatedNotes.trim().take(250),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      if (isReleasingListing) {
        batch.update(
          listingRef,
          mapOf(
            "status" to Listing.STATUS_AVAILABLE,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      if (nextStatus == Booking.STATUS_COMPLETED) {
        val userRef = db.collection("users").document(uid)
        val earnedIncrement =
          if (uid == booking.providerUid) booking.providerNetCents else 0L
        batch.update(
          userRef,
          mapOf(
            "completedHandoffs" to FieldValue.increment(1L),
            "onTimeReturns" to FieldValue.increment(1L),
            "totalEarnedCents" to FieldValue.increment(earnedIncrement),
            "totalFeesSavedOrPaidCents" to FieldValue.increment(booking.platformFeeCents),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, bookingRef.path)
      throw e
    }
  }

  private fun logFlowError(
    error: Throwable,
    operationType: OperationType,
    path: String,
    uid: String,
  ) {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) return
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
      handleFirestoreError(exceptionToLog, operationType, path)
    }
  }

  private fun generateInviteCode(circleName: String): String {
    val prefix =
      circleName
        .uppercase(Locale.US)
        .replace(Regex("[^A-Z]"), "")
        .take(3)
        .padEnd(3, 'B')
    val suffix = (100..999).random().toString()
    return "$prefix$suffix"
  }
}
