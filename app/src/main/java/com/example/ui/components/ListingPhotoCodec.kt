package com.example.ui.components

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.example.model.ListingPhotos
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Converts between a picked image and the inline thumbnail format described in [ListingPhotos].
 */
object ListingPhotoCodec {
  private const val TAG = "ListingPhotoCodec"

  // Tried in order until the encoded thumbnail fits ListingPhotos.TARGET_LENGTH.
  private val EDGE_STEPS = listOf(ListingPhotos.THUMBNAIL_EDGE_PX, 240, 160)
  private val QUALITY_STEPS = listOf(82, 70, 58, 45)

  private val decoded = LruCache<String, ImageBitmap>(48)

  /**
   * Reads the image behind [uri] (as returned by the photo picker), scales it down and returns it
   * as a `data:` URI, or null when it cannot be read or decoded.
   */
  suspend fun encodeThumbnail(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
      try {
        val source = loadScaled(context.contentResolver, uri, ListingPhotos.THUMBNAIL_EDGE_PX)
        source?.let(::encode)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // IOException, SecurityException, ImageDecoder.DecodeException, ...
        Log.w(TAG, "Could not read the picked photo", e)
        null
      } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Picked photo is too large to decode", e)
        null
      }
    }

  /** Encodes [bitmap] as a data URI no longer than [ListingPhotos.TARGET_LENGTH], or null. */
  fun encode(bitmap: Bitmap): String? {
    for (edge in EDGE_STEPS) {
      val scaled = bitmap.fitWithin(edge).flattenOnWhite()
      for (quality in QUALITY_STEPS) {
        val bytes =
          ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
          }
        val dataUri =
          ListingPhotos.DATA_URI_PREFIX + Base64.encodeToString(bytes, Base64.NO_WRAP)
        if (dataUri.length <= ListingPhotos.TARGET_LENGTH) return dataUri
      }
    }
    return null
  }

  /** Decodes a stored thumbnail, or returns null for anything that is not one. */
  fun decode(photoUri: String): Bitmap? {
    if (!ListingPhotos.isInlineThumbnail(photoUri)) return null
    return try {
      val bytes = Base64.decode(photoUri.substring(ListingPhotos.DATA_URI_PREFIX.length), Base64.DEFAULT)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: IllegalArgumentException) {
      Log.w(TAG, "Listing photo is not valid base64", e)
      null
    }
  }

  internal fun cached(photoUri: String): ImageBitmap? = decoded.get(photoUri)

  internal suspend fun decodeCached(photoUri: String): ImageBitmap? {
    // Most listings have no photo; don't hop threads just to find that out.
    if (!ListingPhotos.isInlineThumbnail(photoUri)) return null
    cached(photoUri)?.let { return it }
    val image = withContext(Dispatchers.Default) { decode(photoUri)?.asImageBitmap() }
    if (image != null) decoded.put(photoUri, image)
    return image
  }

  private fun loadScaled(resolver: ContentResolver, uri: Uri, maxEdge: Int): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      // ImageDecoder applies the EXIF orientation itself.
      ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longestEdge = max(info.size.width, info.size.height)
        if (longestEdge > maxEdge) {
          val scale = maxEdge.toFloat() / longestEdge
          decoder.setTargetSize(
            (info.size.width * scale).roundToInt().coerceAtLeast(1),
            (info.size.height * scale).roundToInt().coerceAtLeast(1),
          )
        }
      }
    } else {
      loadScaledLegacy(resolver, uri, maxEdge)
    }

  private fun loadScaledLegacy(resolver: ContentResolver, uri: Uri, maxEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    // Decode at the smallest power-of-two subsampling that still covers the target size.
    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxEdge) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap =
      resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: return null

    // BitmapFactory ignores the EXIF orientation that cameras use for portrait shots.
    val rotation =
      resolver.openInputStream(uri)?.use { stream -> ExifInterface(stream).rotationDegrees } ?: 0
    if (rotation == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
  }

  private fun Bitmap.fitWithin(maxEdge: Int): Bitmap {
    val longestEdge = max(width, height)
    if (longestEdge <= maxEdge) return this
    val factor = maxEdge.toFloat() / longestEdge
    return scale(
      (width * factor).roundToInt().coerceAtLeast(1),
      (height * factor).roundToInt().coerceAtLeast(1),
    )
  }

  /** JPEG has no alpha channel; without this, transparent areas would come out black. */
  private fun Bitmap.flattenOnWhite(): Bitmap {
    if (!hasAlpha()) return this
    val flattened = createBitmap(width, height)
    Canvas(flattened).apply {
      drawColor(Color.WHITE)
      drawBitmap(this@flattenOnWhite, 0f, 0f, null)
    }
    return flattened
  }
}

/**
 * The decoded thumbnail for [photoUri], or null while it is being decoded and when the listing
 * has no (valid) photo. Decoding happens off the main thread and results are cached, so scrolling
 * a feed back and forth does not decode or flicker again.
 */
@Composable
fun rememberListingPhoto(photoUri: String): ImageBitmap? {
  val image by
    produceState(initialValue = ListingPhotoCodec.cached(photoUri), photoUri) {
      value = ListingPhotoCodec.decodeCached(photoUri)
    }
  return image
}
