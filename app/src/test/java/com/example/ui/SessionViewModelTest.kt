package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.base.FirestoreEmulatorTestBase
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SessionViewModel] against the Auth emulator: whatever lives in the session's ViewModelStore
 * must not outlive the signed-in user it was created for. (A user's ViewModel used to survive
 * sign-out with its listeners failing, and greeted the same account's next sign-in with that
 * stale error.)
 */
class SessionViewModelTest : FirestoreEmulatorTestBase() {

  class Probe : ViewModel() {
    @Volatile
    var cleared = false
      private set

    override fun onCleared() {
      cleared = true
    }
  }

  private var session: SessionViewModel? = null

  @After
  fun cancelSession() {
    session?.viewModelScope?.cancel()
  }

  private fun newSession() = SessionViewModel().also { session = it }

  private fun SessionViewModel.probe() = ViewModelProvider(sessionStoreOwner)[Probe::class.java]

  private suspend fun SessionViewModel.awaitUser(uid: String?) {
    withTimeout(TIMEOUT_MS) { user.first { it?.uid == uid } }
  }

  @Test
  fun `view models live as long as the session and are cleared on sign-out`(): Unit = runBlocking {
    val aliceUid = signInTestUser(ALICE)
    val session = newSession()
    session.awaitUser(aliceUid)

    val first = session.probe()
    assertSame(first, session.probe())
    assertFalse(first.cleared)

    auth.signOut()
    session.awaitUser(null)
    assertTrue(first.cleared)

    // The same account signing back in gets a fresh ViewModel, not the old one.
    signInTestUser(ALICE)
    session.awaitUser(aliceUid)
    val second = session.probe()
    assertNotSame(first, second)
    assertFalse(second.cleared)
  }

  @Test
  fun `switching straight to another account clears the previous user's view models`(): Unit = runBlocking {
    val aliceUid = signInTestUser(ALICE)
    val session = newSession()
    session.awaitUser(aliceUid)
    val alices = session.probe()

    val bobUid = signInTestUser(BOB)
    session.awaitUser(bobUid)

    assertTrue(alices.cleared)
    assertNotSame(alices, session.probe())
  }

  @Test
  fun `a session that starts signed out has an empty store until someone signs in`(): Unit = runBlocking {
    val session = newSession()
    session.awaitUser(null)

    val aliceUid = signInTestUser(ALICE)
    session.awaitUser(aliceUid)

    assertFalse(session.probe().cleared)
  }

  private companion object {
    const val ALICE = "alice_session@test.com"
    const val BOB = "bob_session@test.com"
    const val TIMEOUT_MS = 8000L
  }
}
