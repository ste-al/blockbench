package com.example.data

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import java.io.IOException
import java.util.concurrent.ExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric only because FirebaseFirestoreException touches android.util classes. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class UserMessagesTest {

  private val fallback = "Could not publish the listing."

  private fun firestoreError(code: Code) = FirebaseFirestoreException("raw backend text", code)

  @Test
  fun `a message written for the user is shown as it is`() {
    val error = UserFacingException("A neighbor just reserved this.")
    assertEquals("A neighbor just reserved this.", error.toUserMessage(fallback))
  }

  @Test
  fun `a user-facing message is found even when the SDK wraps it`() {
    val wrapped = ExecutionException(RuntimeException(UserFacingException("This listing was just removed.")))
    assertEquals("This listing was just removed.", wrapped.toUserMessage(fallback))
  }

  @Test
  fun `backend errors say what was attempted and what to do, never the raw text`() {
    val offline = firestoreError(Code.UNAVAILABLE).toUserMessage(fallback)
    assertTrue(offline.startsWith(fallback))
    assertTrue(offline.contains("offline"))

    val denied = firestoreError(Code.PERMISSION_DENIED).toUserMessage(fallback)
    assertTrue(denied.startsWith(fallback))
    assertTrue(denied.contains("no longer have access"))

    assertTrue(firestoreError(Code.NOT_FOUND).toUserMessage(fallback).contains("removed"))
    assertTrue(firestoreError(Code.ABORTED).toUserMessage(fallback).contains("try again"))
    assertTrue(firestoreError(Code.UNAUTHENTICATED).toUserMessage(fallback).contains("sign in again"))

    // (The SDK refuses to build an exception for OK.)
    for (code in Code.values().filter { it != Code.OK }) {
      assertFalse(firestoreError(code).toUserMessage(fallback).contains("raw backend text"))
    }
  }

  @Test
  fun `a backend error nested in another exception is still recognised`() {
    val nested = IllegalStateException("outer", firestoreError(Code.DEADLINE_EXCEEDED))
    assertTrue(nested.toUserMessage(fallback).contains("offline"))
  }

  @Test
  fun `anything else falls back to what was being attempted`() {
    assertEquals(fallback, IOException("socket closed").toUserMessage(fallback))
    assertEquals(fallback, IllegalStateException("internal detail").toUserMessage(fallback))
    assertEquals(fallback, firestoreError(Code.INTERNAL).toUserMessage(fallback))
  }
}
