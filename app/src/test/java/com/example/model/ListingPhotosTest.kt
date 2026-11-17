package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingPhotosTest {

  private val thumbnail = ListingPhotos.DATA_URI_PREFIX + "/9j/4AAQSkZJRgABAQAAAQABAAD/2Q=="

  @Test
  fun `only inline JPEG thumbnails count as photos`() {
    assertTrue(ListingPhotos.isInlineThumbnail(thumbnail))

    assertFalse(ListingPhotos.isInlineThumbnail(""))
    assertFalse(ListingPhotos.isInlineThumbnail(ListingPhotos.DATA_URI_PREFIX))
    assertFalse(ListingPhotos.isInlineThumbnail("https://example.com/drill.jpg"))
    assertFalse(ListingPhotos.isInlineThumbnail("content://media/picker/0/x/media/33"))
    assertFalse(ListingPhotos.isInlineThumbnail("file:///sdcard/DCIM/drill.jpg"))
    assertFalse(ListingPhotos.isInlineThumbnail("data:image/png;base64,iVBORw0KGgo="))
    assertFalse(ListingPhotos.isInlineThumbnail(" $thumbnail"))
  }

  @Test
  fun `a thumbnail larger than the rules allow is not accepted`() {
    val atLimit = thumbnail.padEnd(ListingPhotos.MAX_STORED_LENGTH, 'A')
    assertTrue(ListingPhotos.isInlineThumbnail(atLimit))
    assertFalse(ListingPhotos.isInlineThumbnail(atLimit + "A"))
    assertTrue(ListingPhotos.TARGET_LENGTH < ListingPhotos.MAX_STORED_LENGTH)
  }

  @Test
  fun `sanitize keeps thumbnails and turns everything else into no photo`() {
    assertEquals(thumbnail, ListingPhotos.sanitize(thumbnail))
    assertEquals("", ListingPhotos.sanitize(""))
    assertEquals("", ListingPhotos.sanitize("https://attacker.example/pixel.jpg"))
    assertEquals("", ListingPhotos.sanitize("content://media/picker/0/x/media/33"))
  }
}
