package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.OperationType
import com.example.data.UserFacingException
import com.example.data.handleFirestoreError
import com.example.model.BlockCircle
import com.example.model.Booking
import com.example.model.CircleInvite
import com.example.model.EscrowFeeBreakdown
import com.example.model.EscrowWorkflow
import com.example.model.InviteCodes
import com.example.model.Listing
import com.example.model.ListingPhotos
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.TransactionOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * All Firestore access of the app.
 *
 * Reads are snapshot flows that keep working offline from the local cache. Every change, however,
 * runs as a transaction:
 * - it validates against the server's current state, so a stale screen cannot double-book a tool
 *   or act on a booking that has moved on, and the user gets a specific message instead of a
 *   bare PERMISSION_DENIED from the security rules;
 * - documents that must stay consistent (circle + invite, booking + listing reservation) are
 *   written atomically, which firestore.rules requires;
 * - it fails within a few seconds when offline. A plain write would be queued and its Task would
 *   not complete until the device is back online, leaving the UI "busy" indefinitely.
 *
 * Failures that the user can act on are thrown as [UserFacingException].
 */
class BlockBenchRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) : BlockBenchDataSource {

  private val users
    get() = db.collection("users")

  private val circles
    get() = db.collection("circles")

  private val invites
    get() = db.collection("circleInvites")

  private val listings
    get() = db.collection("listings")

  private val bookings
    get() = db.collection("bookings")

  private fun requireUserId(): String =
    auth.currentUser?.uid ?: throw UserFacingException("Please sign in again to continue.")

  // ------------------------------------------------------------ user profile

  override fun observeUserProfile(uid: String): Flow<UserProfile?> =
    users
      .document(uid)
      .snapshots()
      .map { snapshot -> snapshot.toModelOrNull<UserProfile>() }
      .logFailures(OperationType.GET, "users/$uid", uid)

  override suspend fun ensureUserProfile(displayName: String): UserProfile {
    val uid = requireUserId()
    val userRef = users.document(uid)
    val existing =
      transact(OperationType.WRITE, userRef.path) { tx ->
        val snapshot = tx.get(userRef)
        if (!snapshot.exists()) {
          tx.set(userRef, newProfileFields(uid, displayName))
        }
        snapshot.toModelOrNull<UserProfile>()
      }
    return existing
      ?: UserProfile(
        id = uid,
        uid = uid,
        displayName = cleanDisplayName(displayName),
        neighborhoodLabel = DEFAULT_NEIGHBORHOOD_LABEL,
        alertCategories = DEFAULT_ALERT_CATEGORIES,
        createdAt = Timestamp.now(),
        updatedAt = Timestamp.now(),
      )
  }

  override suspend fun updateProAlertsSettings(
    enabled: Boolean,
    radiusMiles: Double,
    categories: List<String>,
  ) {
    val uid = requireUserId()
    val userRef = users.document(uid)
    val fields =
      mapOf(
        "proAlertsEnabled" to enabled,
        "alertRadiusMiles" to radiusMiles.coerceIn(MIN_RADIUS_MILES, MAX_RADIUS_MILES),
        "alertCategories" to categories.distinct().take(MAX_ALERT_CATEGORIES),
      )
    transact(OperationType.UPDATE, userRef.path) { tx ->
      tx.upsertProfile(userRef, tx.get(userRef), fields)
    }
  }

  /**
   * Brings the profile's summary counters up to the totals derived from the user's completed
   * bookings. The counters only ever grow, and a failure here is logged but not thrown: the
   * figures on screen are computed from the bookings themselves, so nothing is lost.
   */
  override suspend fun syncCompletedEarningsToProfile(
    completedHandoffs: Long,
    earnedCents: Long,
    feesCents: Long,
  ) {
    val uid = auth.currentUser?.uid ?: return
    val userRef = users.document(uid)
    try {
      transact(OperationType.UPDATE, userRef.path) { tx ->
        // No profile yet: the bootstrap creates it, and its first snapshot triggers this again.
        val current = tx.get(userRef).toModelOrNull<UserProfile>() ?: return@transact
        val handoffs = maxOf(current.completedHandoffs, completedHandoffs.coerceIn(0L, MAX_COUNTER))
        val earned = maxOf(current.totalEarnedCents, earnedCents.coerceIn(0L, MAX_TOTAL_CENTS))
        val fees = maxOf(current.totalFeesSavedOrPaidCents, feesCents.coerceIn(0L, MAX_TOTAL_CENTS))
        if (
          handoffs == current.completedHandoffs &&
            earned == current.totalEarnedCents &&
            fees == current.totalFeesSavedOrPaidCents
        ) {
          return@transact
        }
        tx.update(
          userRef,
          mapOf(
            "completedHandoffs" to handoffs,
            // Returns are not timed yet, so every completed handoff counts as on time.
            "onTimeReturns" to maxOf(current.onTimeReturns, handoffs),
            "totalEarnedCents" to earned,
            "totalFeesSavedOrPaidCents" to fees,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Could not sync booking totals to the profile", e)
    }
  }

  // ------------------------------------------------- circles and invite codes

  override fun observeMyCircles(uid: String): Flow<List<BlockCircle>> =
    circles
      .whereArrayContains("memberUids", uid)
      .snapshots()
      .map { snapshot ->
        snapshot.toModels<BlockCircle>().sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
      }
      .logFailures(OperationType.LIST, "circles", uid)

  /**
   * Creates a block, its invite document and the pointer to it on the user's profile in one
   * transaction. A freshly generated invite code that happens to be taken is simply replaced by
   * another one; nothing is written until a free code is found.
   */
  override suspend fun createBlockCircle(circleName: String, radiusMiles: Double): BlockCircle {
    val uid = requireUserId()
    val cleanName = circleName.trim().take(MAX_NAME_LENGTH)
    if (cleanName.length < 2) throw UserFacingException("Block name must be at least 2 characters.")
    val boundedRadius = radiusMiles.coerceIn(MIN_RADIUS_MILES, MAX_RADIUS_MILES)

    val circleRef = circles.document()
    val userRef = users.document(uid)

    repeat(INVITE_CODE_ATTEMPTS) {
      val inviteCode = InviteCodes.generate(cleanName)
      val inviteRef = invites.document(inviteCode)
      val circle =
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
      val created =
        transact(OperationType.CREATE, circleRef.path) { tx ->
          val profile = tx.get(userRef)
          if (tx.get(inviteRef).exists()) return@transact false
          tx.set(
            circleRef,
            mapOf(
              "creatorUid" to uid,
              "name" to cleanName,
              "inviteCode" to inviteCode,
              "radiusMiles" to boundedRadius,
              "memberUids" to listOf(uid),
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          tx.set(
            inviteRef,
            mapOf(
              "inviteCode" to inviteCode,
              "circleId" to circleRef.id,
              "circleName" to cleanName,
              "creatorUid" to uid,
              "createdAt" to FieldValue.serverTimestamp(),
            ),
          )
          tx.upsertProfile(userRef, profile, activeCircleFields(circle))
          true
        }
      if (created) return circle
    }
    throw UserFacingException("Could not reserve an invite code for this block. Please try again.")
  }

  /**
   * Joins the block behind [rawCode] and makes it the active one. Entering the code of a block
   * the user already belongs to just switches to it.
   */
  override suspend fun joinCircleByInviteCode(rawCode: String): BlockCircle {
    val uid = requireUserId()
    val code = InviteCodes.normalize(rawCode)
    if (!InviteCodes.isWellFormed(code)) {
      throw UserFacingException(
        "Invite codes are ${InviteCodes.MIN_LENGTH}-${InviteCodes.MAX_LENGTH} letters and digits."
      )
    }

    val inviteRef = invites.document(code)
    val invite =
      firestoreCall(OperationType.GET, inviteRef.path) { inviteRef.get().await() }
        .toModelOrNull<CircleInvite>()
        ?.takeIf { it.circleId.isNotBlank() }
        ?: throw UserFacingException("No block uses the invite code $code. Check it and try again.")

    val circleRef = circles.document(invite.circleId)
    val userRef = users.document(uid)
    val target =
      BlockCircle(
        id = invite.circleId,
        creatorUid = invite.creatorUid,
        name = invite.circleName,
        inviteCode = invite.inviteCode,
      )

    // The rules accept "add myself" exactly once, and a non-member cannot read the circle, so
    // membership is determined from the circles the user can already see.
    val alreadyMember =
      firestoreCall(OperationType.LIST, "circles") {
          circles.whereArrayContains("memberUids", uid).get().await()
        }
        .documents
        .any { it.id == invite.circleId }

    try {
      transact(OperationType.UPDATE, circleRef.path) { tx ->
        val profile = tx.get(userRef)
        if (!alreadyMember) {
          tx.update(
            circleRef,
            mapOf(
              "memberUids" to FieldValue.arrayUnion(uid),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
        tx.upsertProfile(userRef, profile, activeCircleFields(target))
      }
    } catch (e: FirebaseFirestoreException) {
      if (!alreadyMember && e.code in REFUSED_CODES) {
        throw UserFacingException(
          "Could not join ${invite.circleName}. The block may be full " +
            "(${BlockCircle.MAX_MEMBERS} neighbors) or no longer exist.",
          e,
        )
      }
      throw e
    }

    return firestoreCall(OperationType.GET, circleRef.path) { circleRef.get().await() }
      .toModelOrNull<BlockCircle>()
      ?: target.copy(memberUids = listOf(invite.creatorUid, uid).distinct())
  }

  override suspend fun switchActiveCircle(circle: BlockCircle) {
    val uid = requireUserId()
    val userRef = users.document(uid)
    transact(OperationType.UPDATE, userRef.path) { tx ->
      tx.upsertProfile(userRef, tx.get(userRef), activeCircleFields(circle))
    }
  }

  /**
   * Leaves [circle] and takes the user's own listings there off its feed. If it was the active
   * block, [nextActiveCircle] (or none) becomes active instead. Refused while the user still has
   * escrow in flight in that block, because the neighbour on the other side would be left with a
   * booking nobody can see through. Returns the number of listings that were removed.
   */
  override suspend fun leaveBlockCircle(circle: BlockCircle, nextActiveCircle: BlockCircle?): Int {
    val uid = requireUserId()
    if (circle.creatorUid == uid) {
      throw UserFacingException("You started ${circle.name}, so you can't leave it.")
    }
    val circleRef = circles.document(circle.id)
    val userRef = users.document(uid)

    // Deliberately the same two queries the feed and the ledger use (filtered further in memory):
    // they are known to be allowed by the rules and need no composite index.
    val myListings =
      firestoreCall(OperationType.LIST, "listings") {
          listings.whereEqualTo("circleId", circle.id).get().await()
        }
        .toModels<Listing>()
        .filter { it.ownerUid == uid }
    val hasOpenBookings =
      firestoreCall(OperationType.LIST, "bookings") {
          bookings.whereArrayContains("participantUids", uid).get().await()
        }
        .toModels<Booking>()
        .any { it.circleId == circle.id && it.isActive }

    if (hasOpenBookings || myListings.any { it.status == Listing.STATUS_RESERVED }) {
      throw UserFacingException(
        "Settle or cancel your open escrow bookings in ${circle.name} before leaving it."
      )
    }

    transact(OperationType.UPDATE, circleRef.path) { tx ->
      val profile = tx.get(userRef)
      tx.update(
        circleRef,
        mapOf(
          "memberUids" to FieldValue.arrayRemove(uid),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      myListings.forEach { tx.delete(listings.document(it.id)) }
      val wasActive = !profile.exists() || profile.getString("activeCircleId") == circle.id
      if (wasActive) tx.upsertProfile(userRef, profile, activeCircleFields(nextActiveCircle))
    }
    return myListings.size
  }

  // ------------------------------------------------------------------ listings

  /** Only members of the block may read its feed; the query filter is what the rules check. */
  override fun observeCircleListings(circleId: String): Flow<List<Listing>> {
    val subscriberUid = auth.currentUser?.uid.orEmpty()
    return listings
      .whereEqualTo("circleId", circleId)
      .snapshots()
      .map { snapshot ->
        snapshot.toModels<Listing>().sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
      }
      .logFailures(OperationType.LIST, "listings?circleId=$circleId", subscriberUid)
  }

  override suspend fun createListing(
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
    photoUri: String,
  ): String {
    val uid = requireUserId()
    val details =
      validatedListingDetails(
        kind = kind,
        title = title,
        description = description,
        category = category,
        rateCents = rateCents,
        depositCents = depositCents,
        distanceMiles = distanceMiles,
        instantPickup = instantPickup,
        photoUri = photoUri,
      )
    val circleRef = circles.document(circle.id)
    val listingRef = listings.document()
    val payload =
      details +
        mapOf(
          "circleId" to circle.id,
          "ownerUid" to uid,
          "ownerName" to cleanDisplayName(ownerName),
          "ownerTrustScore" to ownerTrustScore.coerceIn(0.0, 5.0),
          "kind" to kind,
          "status" to Listing.STATUS_AVAILABLE,
          "activeBookingId" to "",
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
    return transact(OperationType.CREATE, listingRef.path) { tx ->
      if (!tx.get(circleRef).exists()) {
        throw UserFacingException("${circle.name} no longer exists.")
      }
      tx.set(listingRef, payload)
      listingRef.id
    }
  }

  override suspend fun updateListingDetails(
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
    val listingRef = listings.document(listingId)
    transact(OperationType.UPDATE, listingRef.path) { tx ->
      val current = tx.get(listingRef).toModelOrNull<Listing>() ?: throw listingGone()
      val details =
        validatedListingDetails(
          kind = current.kind,
          title = title,
          description = description,
          category = category,
          rateCents = rateCents,
          depositCents = depositCents,
          distanceMiles = distanceMiles,
          instantPickup = instantPickup,
          photoUri = photoUri,
        )
      tx.update(listingRef, details + ("updatedAt" to FieldValue.serverTimestamp()))
    }
  }

  /**
   * Pauses or resumes one of the user's listings. A listing with a booking in flight stays
   * reserved until that booking is settled. Resuming also clears a reservation left behind by an
   * older app version (see [Listing.hasUntrackedReservation]).
   */
  override suspend fun setListingPaused(listingId: String, paused: Boolean) {
    requireUserId()
    val listingRef = listings.document(listingId)
    transact(OperationType.UPDATE, listingRef.path) { tx ->
      val current = tx.get(listingRef).toModelOrNull<Listing>() ?: throw listingGone()
      val isReserved = current.status == Listing.STATUS_RESERVED
      if (isReserved && !(current.hasUntrackedReservation && !paused)) throw listingHasOpenBooking()
      tx.update(
        listingRef,
        mapOf(
          "status" to if (paused) Listing.STATUS_PAUSED else Listing.STATUS_AVAILABLE,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
    }
  }

  override suspend fun deleteListing(listingId: String) {
    requireUserId()
    val listingRef = listings.document(listingId)
    transact(OperationType.DELETE, listingRef.path) { tx ->
      val snapshot = tx.get(listingRef)
      if (!snapshot.exists()) return@transact
      if (snapshot.getString("status") == Listing.STATUS_RESERVED) throw listingHasOpenBooking()
      tx.delete(listingRef)
    }
  }

  // --------------------------------------------------------- escrow bookings

  override fun observeMyBookings(uid: String): Flow<List<Booking>> =
    bookings
      .whereArrayContains("participantUids", uid)
      .snapshots()
      .map { snapshot ->
        snapshot.toModels<Booking>().sortedByDescending {
          it.updatedAt ?: it.createdAt ?: Timestamp(0, 0)
        }
      }
      .logFailures(OperationType.LIST, "bookings", uid)

  /**
   * Books a tool or claims a micro-job: creates the booking and reserves the listing in one
   * transaction, against the listing as it is on the server right now. [listing] is what the user
   * was looking at; if the price they agreed to has changed since, nothing is booked.
   */
  override suspend fun createBookingRequest(
    listing: Listing,
    currentUserName: String,
    notes: String,
  ): String {
    val uid = requireUserId()
    if (listing.ownerUid == uid) throw UserFacingException("You can't book your own listing.")
    val myName = cleanDisplayName(currentUserName)
    val listingRef = listings.document(listing.id)
    val bookingRef = bookings.document()

    return transact(OperationType.CREATE, bookingRef.path) { tx ->
      val current = tx.get(listingRef).toModelOrNull<Listing>() ?: throw listingGone()
      when (current.status) {
        Listing.STATUS_AVAILABLE -> Unit
        Listing.STATUS_PAUSED ->
          throw UserFacingException("The owner just paused this listing.")
        else ->
          throw UserFacingException(
            "A neighbor just reserved this. It will be back on the feed once they are done."
          )
      }
      if (
        current.kind != listing.kind ||
          current.rateCents != listing.rateCents ||
          current.depositCents != listing.depositCents
      ) {
        throw UserFacingException(
          "The owner just changed the price of this listing. Check the new terms and try again."
        )
      }

      val terms = EscrowFeeBreakdown.calculate(current.rateCents, current.depositCents)
      // Tool: I borrow and pay, the owner provides it.
      // Micro-job: the owner pays, I provide the work.
      val requesterUid = if (current.isMicroJob) current.ownerUid else uid
      val requesterName = if (current.isMicroJob) current.ownerName else myName
      val providerUid = if (current.isMicroJob) uid else current.ownerUid
      val providerName = if (current.isMicroJob) myName else current.ownerName

      tx.set(
        bookingRef,
        // The rules compare the listing fields below with the listing itself, verbatim.
        mapOf(
          "listingId" to current.id,
          "listingTitle" to current.title,
          "listingKind" to current.kind,
          "circleId" to current.circleId,
          "requesterUid" to requesterUid,
          "requesterName" to requesterName,
          "providerUid" to providerUid,
          "providerName" to providerName,
          "participantUids" to listOf(requesterUid, providerUid),
          "grossAmountCents" to terms.grossAmountCents,
          "depositCents" to terms.depositCents,
          "platformFeeCents" to terms.platformFeeCents,
          "providerNetCents" to terms.providerNetCents,
          "status" to Booking.STATUS_REQUESTED,
          "notes" to notes.trim().take(Booking.MAX_NOTES_LENGTH),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      tx.update(
        listingRef,
        mapOf(
          "status" to Listing.STATUS_RESERVED,
          "activeBookingId" to bookingRef.id,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      bookingRef.id
    }
  }

  /**
   * Moves [booking] along the escrow workflow (see [EscrowWorkflow]) as the signed-in user.
   * Completing or cancelling frees the listing in the same transaction.
   */
  override suspend fun advanceBookingStatus(
    booking: Booking,
    nextStatus: String,
    updatedNotes: String,
  ) {
    val uid = requireUserId()
    val bookingRef = bookings.document(booking.id)
    val listingRef = listings.document(booking.listingId)
    val notes = updatedNotes.trim().take(Booking.MAX_NOTES_LENGTH)

    suspend fun commit(releaseListing: Boolean) {
      transact(OperationType.UPDATE, bookingRef.path) { tx ->
        val current =
          tx.get(bookingRef).toModelOrNull<Booking>()
            ?: throw UserFacingException("This booking no longer exists.")
        if (!EscrowWorkflow.canTransition(current, uid, nextStatus)) {
          throw UserFacingException(transitionRefusal(current, uid, nextStatus))
        }
        tx.update(
          bookingRef,
          mapOf(
            "status" to nextStatus,
            "notes" to notes,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        if (releaseListing) {
          tx.update(
            listingRef,
            mapOf(
              "status" to Listing.STATUS_AVAILABLE,
              "activeBookingId" to "",
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }
    }

    if (!EscrowWorkflow.releasesListing(nextStatus)) {
      commit(releaseListing = false)
      return
    }
    try {
      commit(releaseListing = true)
    } catch (e: FirebaseFirestoreException) {
      if (e.code !in REFUSED_CODES) throw e
      // Bookings made by app versions that did not record the reservation on the listing (or
      // whose listing has since been deleted) have nothing to release, and the rules refuse the
      // listing write. Settle the booking on its own; the rules still refuse *that* if the
      // listing does point at this booking.
      commit(releaseListing = false)
    }
  }

  // ------------------------------------------------------------------- helpers

  private fun transitionRefusal(current: Booking, uid: String, nextStatus: String): String {
    val ownerName = current.listingOwnerName.ifBlank { "the listing owner" }
    return when {
      current.status == nextStatus -> "That step was already taken."
      !current.isActive ->
        if (current.status == Booking.STATUS_COMPLETED) {
          "This booking has already been completed."
        } else {
          "This booking has already been cancelled."
        }
      nextStatus == Booking.STATUS_CANCELLED ->
        "The handoff has already started, so this booking can no longer be cancelled."
      nextStatus == Booking.STATUS_ESCROW_HELD && uid != current.listingOwnerUid ->
        "Only $ownerName can approve this request."
      nextStatus == Booking.STATUS_COMPLETED && uid != current.listingOwnerUid ->
        "Only $ownerName can confirm completion and release the payout."
      else -> "This booking changed in the meantime. Check its current status and try again."
    }
  }

  private fun listingGone() = UserFacingException("This listing was just removed.")

  private fun listingHasOpenBooking() =
    UserFacingException("This listing has a booking in progress. Settle it in Escrow first.")

  /** Validates what the listing form collects and returns it as document fields. */
  private fun validatedListingDetails(
    kind: String,
    title: String,
    description: String,
    category: String,
    rateCents: Long,
    depositCents: Long,
    distanceMiles: Double,
    instantPickup: Boolean,
    photoUri: String,
  ): Map<String, Any> {
    if (kind != Listing.KIND_TOOL && kind != Listing.KIND_MICRO_JOB) {
      throw UserFacingException("Unknown listing type.")
    }
    val cleanTitle = title.trim().take(Listing.MAX_TITLE_LENGTH)
    if (cleanTitle.length < 3) throw UserFacingException("Title must be at least 3 characters.")
    if (rateCents !in Listing.MIN_RATE_CENTS..Listing.MAX_AMOUNT_CENTS) {
      throw UserFacingException("Rate must be between $1 and $5,000.")
    }
    // Micro-jobs carry no deposit; the rules refuse one.
    val cleanDeposit = if (kind == Listing.KIND_MICRO_JOB) 0L else depositCents
    if (cleanDeposit !in 0L..Listing.MAX_AMOUNT_CENTS) {
      throw UserFacingException("Deposit must be between $0 and $5,000.")
    }
    return mapOf(
      "title" to cleanTitle,
      "description" to description.trim().take(Listing.MAX_DESCRIPTION_LENGTH),
      "category" to (category.trim().take(40).takeIf { it.length >= 2 } ?: DEFAULT_CATEGORY),
      "rateCents" to rateCents,
      "depositCents" to cleanDeposit,
      "distanceMiles" to distanceMiles.coerceIn(0.05, 10.0),
      "instantPickup" to instantPickup,
      "photoUri" to ListingPhotos.sanitize(photoUri),
    )
  }

  private fun cleanDisplayName(name: String): String =
    name.trim().take(MAX_NAME_LENGTH).ifEmpty { DEFAULT_DISPLAY_NAME }

  private fun signedInDisplayName(): String {
    val user = auth.currentUser
    return user?.displayName?.takeIf { it.isNotBlank() }
      ?: user?.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
      ?: DEFAULT_DISPLAY_NAME
  }

  private fun newProfileFields(uid: String, displayName: String): Map<String, Any> =
    mapOf(
      "uid" to uid,
      "displayName" to cleanDisplayName(displayName),
      "neighborhoodLabel" to DEFAULT_NEIGHBORHOOD_LABEL,
      "activeCircleId" to "",
      "activeCircleName" to "",
      "activeInviteCode" to "",
      "trustScore" to 5.0,
      "completedHandoffs" to 0L,
      "onTimeReturns" to 0L,
      "totalEarnedCents" to 0L,
      "totalFeesSavedOrPaidCents" to 0L,
      "proAlertsEnabled" to false,
      "alertRadiusMiles" to DEFAULT_RADIUS_MILES,
      "alertCategories" to DEFAULT_ALERT_CATEGORIES,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )

  /** The profile fields that point at the active block; `null` means "no block". */
  private fun activeCircleFields(circle: BlockCircle?): Map<String, Any> =
    mapOf(
      "activeCircleId" to (circle?.id ?: ""),
      "activeCircleName" to (circle?.name ?: ""),
      "activeInviteCode" to (circle?.inviteCode ?: ""),
      "neighborhoodLabel" to (circle?.name ?: DEFAULT_NEIGHBORHOOD_LABEL),
    )

  /**
   * Applies [fields] to the user's profile, creating the profile first if the first-launch
   * bootstrap has not succeeded yet (for example because the app was started offline). Call it
   * after all of the transaction's reads; [existing] is the profile as read in this transaction.
   */
  private fun Transaction.upsertProfile(
    userRef: DocumentReference,
    existing: DocumentSnapshot,
    fields: Map<String, Any>,
  ) {
    if (existing.exists()) {
      update(userRef, fields + ("updatedAt" to FieldValue.serverTimestamp()))
    } else {
      set(userRef, newProfileFields(userRef.id, signedInDisplayName()) + fields)
    }
  }

  private suspend fun <T> transact(
    operation: OperationType,
    path: String,
    block: (Transaction) -> T,
  ): T =
    firestoreCall(operation, path) {
      db.runTransaction(TRANSACTION_OPTIONS, Transaction.Function { tx -> block(tx) }).await()
    }

  private inline fun <T> firestoreCall(
    operation: OperationType,
    path: String,
    call: () -> T,
  ): T =
    try {
      call()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // A UserFacingException thrown inside a transaction arrives as the Task's failure.
      generateSequence<Throwable>(e) { it.cause }
        .filterIsInstance<UserFacingException>()
        .firstOrNull()
        ?.let { throw it }
      handleFirestoreError(e, operation, path)
      throw e
    }

  /**
   * Maps a document to [T], or null when it does not exist or does not fit the model. The rules
   * type-check every field, so a misfit should not happen; if it ever does, one bad document must
   * not take the whole feed or ledger down with it.
   */
  private inline fun <reified T : Any> DocumentSnapshot.toModelOrNull(): T? {
    if (!exists()) return null
    return try {
      toObject(T::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: RuntimeException) {
      Log.w(TAG, "Skipping malformed document ${reference.path}", e)
      null
    }
  }

  private inline fun <reified T : Any> QuerySnapshot.toModels(): List<T> =
    documents.mapNotNull { it.toModelOrNull<T>() }

  private fun <T> Flow<T>.logFailures(
    operation: OperationType,
    path: String,
    subscriberUid: String,
  ): Flow<T> = catch { error ->
    logFlowError(error, operation, path, subscriberUid)
    throw error
  }

  private fun logFlowError(
    error: Throwable,
    operationType: OperationType,
    path: String,
    subscriberUid: String,
  ) {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) return
    // Listeners that die because their user signed out are expected; don't report those.
    if (auth.currentUser?.uid != subscriberUid) return
    handleFirestoreError(firestoreError ?: error, operationType, path)
  }

  companion object {
    private const val TAG = "BlockBenchRepository"

    const val DEFAULT_RADIUS_MILES = 1.5
    const val MIN_RADIUS_MILES = 0.25
    const val MAX_RADIUS_MILES = 10.0
    const val MAX_NAME_LENGTH = 60

    private const val DEFAULT_DISPLAY_NAME = "Neighbor"
    private const val DEFAULT_NEIGHBORHOOD_LABEL = "My Block"
    private const val DEFAULT_CATEGORY = "Power Tools"
    private val DEFAULT_ALERT_CATEGORIES = listOf("Power Tools", "Quick Fix")
    private const val MAX_ALERT_CATEGORIES = 8
    private const val MAX_COUNTER = 100_000L
    private const val MAX_TOTAL_CENTS = 1_000_000_000L
    private const val INVITE_CODE_ATTEMPTS = 5

    // Fewer attempts than the default five: contention is rare here, and when the device is
    // offline every attempt only adds to the time the user waits to be told so.
    private val TRANSACTION_OPTIONS = TransactionOptions.Builder().setMaxAttempts(3).build()

    /** How the backend reports a write that the security rules (or a missing document) refused. */
    private val REFUSED_CODES =
      setOf(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        FirebaseFirestoreException.Code.NOT_FOUND,
      )

    fun create(context: Context): BlockBenchRepository {
      val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
      return BlockBenchRepository(FirebaseFirestore.getInstance(databaseId))
    }
  }
}
