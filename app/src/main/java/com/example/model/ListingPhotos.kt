package com.example.model

/**
 * Listing photos are stored inline in the listing document as a small JPEG thumbnail encoded as a
 * `data:` URI. There is no file storage backend, and the URI the system photo picker returns is
 * only readable on the device that picked it, so storing that would show the photo to its owner
 * and to nobody else.
 *
 * Only this format is ever written or displayed. In particular a listing can not make another
 * neighbour's phone fetch an arbitrary URL.
 */
object ListingPhotos {
  const val DATA_URI_PREFIX = "data:image/jpeg;base64,"

  /** Hard limit enforced by `isValidPhoto()` in firestore.rules. */
  const val MAX_STORED_LENGTH = 60_000

  /** What the encoder aims for (about 30 KB of JPEG), leaving headroom below the hard limit. */
  const val TARGET_LENGTH = 40_000

  /** Longest edge of a stored thumbnail, in pixels. */
  const val THUMBNAIL_EDGE_PX = 320

  fun isInlineThumbnail(photoUri: String): Boolean =
    photoUri.length in (DATA_URI_PREFIX.length + 1)..MAX_STORED_LENGTH &&
      photoUri.startsWith(DATA_URI_PREFIX)

  /** [photoUri] if it is a thumbnail in the supported format, otherwise "no photo". */
  fun sanitize(photoUri: String): String = if (isInlineThumbnail(photoUri)) photoUri else ""
}
