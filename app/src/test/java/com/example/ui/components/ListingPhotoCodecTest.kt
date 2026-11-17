package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.model.ListingPhotos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Uses Robolectric's native graphics so that JPEG encoding and decoding are the real thing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(manifest = Config.NONE)
class ListingPhotoCodecTest {

  /** A busy, hard-to-compress picture, like a real photo. */
  private fun photo(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    val random = java.util.Random(3)
    canvas.drawColor(Color.rgb(40, 90, 70))
    repeat(4000) {
      paint.color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
      val x = random.nextInt(width).toFloat()
      val y = random.nextInt(height).toFloat()
      canvas.drawRect(x, y, x + random.nextInt(60), y + random.nextInt(60), paint)
    }
    return bitmap
  }

  @Test
  fun `a large photo becomes a small inline thumbnail that the rules accept`() {
    val encoded = ListingPhotoCodec.encode(photo(1600, 1200))

    assertNotNull(encoded)
    assertTrue(encoded!!.startsWith(ListingPhotos.DATA_URI_PREFIX))
    assertTrue("${encoded.length} characters", encoded.length <= ListingPhotos.TARGET_LENGTH)
    assertTrue(ListingPhotos.isInlineThumbnail(encoded))
    // The same shape firestore.rules checks.
    assertTrue(Regex("^data:image/jpeg;base64,[A-Za-z0-9+/]+={0,2}$").matches(encoded))
  }

  @Test
  fun `the thumbnail keeps the picture's proportions within the size limit`() {
    val landscape = ListingPhotoCodec.decode(ListingPhotoCodec.encode(photo(1600, 1200))!!)!!
    assertTrue(maxOf(landscape.width, landscape.height) <= ListingPhotos.THUMBNAIL_EDGE_PX)
    assertEquals(4.0 / 3.0, landscape.width.toDouble() / landscape.height, 0.02)

    val portrait = ListingPhotoCodec.decode(ListingPhotoCodec.encode(photo(600, 1800))!!)!!
    assertTrue(maxOf(portrait.width, portrait.height) <= ListingPhotos.THUMBNAIL_EDGE_PX)
    assertEquals(1.0 / 3.0, portrait.width.toDouble() / portrait.height, 0.02)
  }

  @Test
  fun `a picture that is already small is not blown up`() {
    val small = ListingPhotoCodec.decode(ListingPhotoCodec.encode(photo(120, 80))!!)!!
    assertEquals(120, small.width)
    assertEquals(80, small.height)
  }

  @Test
  fun `transparent areas come out white rather than black`() {
    val transparent = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    val decoded = ListingPhotoCodec.decode(ListingPhotoCodec.encode(transparent)!!)!!

    val pixel = decoded.getPixel(32, 32)
    assertTrue("red=${Color.red(pixel)}", Color.red(pixel) > 240)
    assertTrue("green=${Color.green(pixel)}", Color.green(pixel) > 240)
    assertTrue("blue=${Color.blue(pixel)}", Color.blue(pixel) > 240)
  }

  @Test
  fun `anything that is not a stored thumbnail decodes to nothing`() {
    assertNull(ListingPhotoCodec.decode(""))
    assertNull(ListingPhotoCodec.decode("https://example.com/drill.jpg"))
    assertNull(ListingPhotoCodec.decode("content://media/picker/0/x/media/33"))
    assertNull(ListingPhotoCodec.decode(ListingPhotos.DATA_URI_PREFIX + "!!!not base64!!!"))
    assertNull(ListingPhotoCodec.decode(ListingPhotos.DATA_URI_PREFIX + "aGVsbG8gd29ybGQ="))
  }
}
