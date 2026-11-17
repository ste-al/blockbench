package com.example.auth

import android.app.PendingIntent
import android.content.Context
import android.os.CancellationSignal
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateCredentialRequest
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PrepareGetCredentialResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.ClearCredentialUnknownException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import com.example.base.FirestoreEmulatorTestBase
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private typealias ClearCallback = CredentialManagerCallback<Void?, ClearCredentialException>

/**
 * [signOutUser] against the Auth emulator, with the credential providers played by a fake.
 *
 * Signing out is the one thing that has to work when everything else is broken, so none of it may
 * hinge on the providers: on a device without one, or with one that is slow, the "forget this
 * account" request fails or never comes back. (Sign-out used to wait for that answer first, and
 * with no answer the button did nothing at all.)
 */
class SignOutTest : FirestoreEmulatorTestBase() {

  /** A [CredentialManager] that records the "forget this account" requests it is given. */
  private class FakeCredentialManager(private val answer: (ClearCallback) -> Unit = {}) :
    CredentialManager {
    val pending = CopyOnWriteArrayList<Pair<CancellationSignal?, ClearCallback>>()

    override fun clearCredentialStateAsync(
      request: ClearCredentialStateRequest,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: ClearCallback,
    ) {
      pending += cancellationSignal to callback
      answer(callback)
    }

    override fun getCredentialAsync(
      context: Context,
      request: GetCredentialRequest,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
    ) = notUsed()

    override fun getCredentialAsync(
      context: Context,
      pendingGetCredentialHandle: PrepareGetCredentialResponse.PendingGetCredentialHandle,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
    ) = notUsed()

    override fun prepareGetCredentialAsync(
      request: GetCredentialRequest,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<PrepareGetCredentialResponse, GetCredentialException>,
    ) = notUsed()

    override fun createCredentialAsync(
      context: Context,
      request: CreateCredentialRequest,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException>,
    ) = notUsed()

    override fun createSettingsPendingIntent(): PendingIntent = notUsed()

    private fun notUsed(): Nothing = error("Signing out has no business calling this")
  }

  /** Anything the sign-out lets escape instead of handling. */
  private val escaped = CopyOnWriteArrayList<Throwable>()

  /** Stands in for the scope of the screen whose button was pressed. */
  private val screenScope =
    CoroutineScope(
      Job() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, e -> escaped += e }
    )

  private var manager: FakeCredentialManager? = null

  @After
  fun finishPendingRequests() {
    // Answer whatever a test left hanging, so that nothing of it outlives the test.
    manager?.pending?.forEach { (_, callback) -> callback.onResult(null) }
    screenScope.cancel()
  }

  private fun credentialManager(answer: (ClearCallback) -> Unit = {}) =
    FakeCredentialManager(answer).also { manager = it }

  private suspend fun signIn() {
    signInTestUser(ALICE)
    assertNotNull(auth.currentUser)
  }

  @Test
  fun `signs out without waiting for the credential providers`(): Unit = runBlocking {
    signIn()
    val neverAnswers = credentialManager()

    signOutUser(neverAnswers, screenScope)

    assertNull(auth.currentUser)
    assertEquals(1, neverAnswers.pending.size)
  }

  @Test
  fun `a credential provider that fails does not get in the way`(): Unit = runBlocking {
    signIn()
    val fails = credentialManager { it.onError(ClearCredentialUnknownException("No provider")) }

    signOutUser(fails, screenScope)

    assertNull(auth.currentUser)
    assertEquals(emptyList<Throwable>(), escaped)
  }

  @Test
  fun `the request to forget the account outlives the screen that made it`(): Unit = runBlocking {
    signIn()
    val slow = credentialManager()

    signOutUser(slow, screenScope)
    // Signing out replaces the screen, which cancels its scope while the request is in flight.
    screenScope.cancel()

    val (cancellation, callback) = slow.pending.single()
    assertFalse("The request was abandoned", checkNotNull(cancellation).isCanceled)
    callback.onResult(null)
    assertEquals(emptyList<Throwable>(), escaped)
  }

  @Test
  fun `the providers are asked even when the screen is gone before the request starts`(): Unit =
    runBlocking {
      signIn()
      val answers = credentialManager { it.onResult(null) }
      screenScope.cancel()

      signOutUser(answers, screenScope)

      assertNull(auth.currentUser)
      assertEquals(1, answers.pending.size)
    }

  private companion object {
    const val ALICE = "alice_signout@test.com"
  }
}
