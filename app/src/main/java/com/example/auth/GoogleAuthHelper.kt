package com.example.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val AUTH_TAG = "BlockBenchAuth"
private const val CLEAR_CREDENTIAL_STATE_TIMEOUT_MS = 10_000L

/**
 * Set when the user signs out. The sign-in screen tries a silent sign-in whenever it appears,
 * which right after an explicit sign-out would immediately put Google's account sheet back in
 * front of the person who just asked to leave. Cleared by the next deliberate sign-in.
 */
private val signedOutThisSession = AtomicBoolean(false)

sealed interface GoogleSignInResult {
  data object Success : GoogleSignInResult

  /** The user dismissed the account picker; nothing to report. */
  data object Cancelled : GoogleSignInResult

  data class Failure(val message: String) : GoogleSignInResult
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

/**
 * The OAuth web client id that the google-services plugin generates from `google-services.json`.
 *
 * It is resolved by name on purpose: the resource only exists when a `google-services.json` was
 * present at build time, and a direct `R.string.default_web_client_id` reference would make the
 * whole project fail to compile without that (untracked) file.
 */
@SuppressLint("DiscouragedApi")
internal fun Context.googleWebClientId(): String? {
  val resId = resources.getIdentifier("default_web_client_id", "string", packageName)
  return if (resId == 0) null else getString(resId).takeIf { it.isNotBlank() }
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

/**
 * Signs a returning user back in without any UI when they already authorised this app.
 * Returns true when a Firebase session was established.
 */
suspend fun trySilentGoogleSignIn(context: Context, credentialManager: CredentialManager): Boolean {
  if (Firebase.auth.currentUser != null) return true
  if (signedOutThisSession.get()) return false
  val clientId = context.googleWebClientId() ?: return false

  val googleIdOption =
    GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(true)
      .setServerClientId(clientId)
      .setAutoSelectEnabled(true)
      .build()
  val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

  return try {
    val result = credentialManager.getCredential(context.findActivity() ?: context, request)
    signInToFirebase(result.credential)
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Log.d(AUTH_TAG, "Silent sign-in unavailable: ${e.message}")
    false
  }
}

/** Runs the interactive "Sign in with Google" flow and exchanges the result for a Firebase session. */
suspend fun signInWithGoogle(
  context: Context,
  credentialManager: CredentialManager,
): GoogleSignInResult {
  val clientId =
    context.googleWebClientId()
      ?: return GoogleSignInResult.Failure(
        "Google Sign-In is not configured for this build (google-services.json is missing)."
      )
  val activity =
    context.findActivity()
      ?: return GoogleSignInResult.Failure("Google Sign-In needs to be started from a screen.")

  val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
  val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

  return try {
    val result = credentialManager.getCredential(activity, request)
    if (signInToFirebase(result.credential)) {
      signedOutThisSession.set(false)
      GoogleSignInResult.Success
    } else {
      GoogleSignInResult.Failure("Google returned an unexpected credential. Please try again.")
    }
  } catch (e: CancellationException) {
    throw e
  } catch (e: GetCredentialCancellationException) {
    Log.d(AUTH_TAG, "Google Sign-In dismissed: ${e.message}")
    GoogleSignInResult.Cancelled
  } catch (e: NoCredentialException) {
    Log.w(AUTH_TAG, "No Google account available", e)
    GoogleSignInResult.Failure(
      "No Google account is available on this device. Add one in system settings and try again."
    )
  } catch (e: Exception) {
    Log.e(AUTH_TAG, "Google Sign-In failed", e)
    GoogleSignInResult.Failure(e.localizedMessage ?: "Sign in failed. Please try again.")
  }
}

private suspend fun signInToFirebase(credential: Credential): Boolean {
  if (credential !is CustomCredential || credential.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
    return false
  }
  val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
  Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(googleIdToken, null)).await()
  return true
}

/**
 * Signs out of Firebase right away, then asks the credential providers to forget which account
 * this app was using, so that the next launch does not silently sign the same account back in.
 *
 * The second step is best effort. Signing out must not fail, or wait, because a credential
 * provider is missing or slow to answer, so it comes second, its failures are only logged and it
 * is given up on after a while. It is started before this function returns and is not cancelled
 * with [scope]: the sign-out itself takes the calling screen, and with it that scope, away.
 */
fun signOutUser(credentialManager: CredentialManager, scope: CoroutineScope) {
  signedOutThisSession.set(true)
  Firebase.auth.signOut()
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    withContext(NonCancellable) {
      try {
        val cleared =
          withTimeoutOrNull(CLEAR_CREDENTIAL_STATE_TIMEOUT_MS) {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
          }
        if (cleared == null) Log.w(AUTH_TAG, "Clearing the credential state timed out")
      } catch (e: Exception) {
        Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
      }
    }
  }
}
