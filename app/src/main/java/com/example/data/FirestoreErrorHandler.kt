package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/**
 * An error whose [message] is written for the person using the app and can be shown as is
 * (a failed validation, "someone else just reserved this", ...).
 */
class UserFacingException(override val message: String, cause: Throwable? = null) :
  Exception(message, cause)

enum class OperationType(val value: String) {
  CREATE("create"),
  UPDATE("update"),
  DELETE("delete"),
  LIST("list"),
  GET("get"),
  WRITE("write"),
}

/**
 * Logs a failed Firestore operation as one structured line ("Firestore Error: {json}") and
 * returns that JSON.
 *
 * The signed-in user's uid, e-mail and providers help when debugging security rules, but they are
 * personal data, and Logcat ends up in bug reports. They are therefore only included in debug
 * builds; release builds log the operation, path and error alone.
 */
fun handleFirestoreError(
  exception: Throwable,
  operationType: OperationType,
  path: String?,
): String {
  val causes = generateSequence(exception) { it.cause }
  val rootError =
    causes.firstOrNull { it is FirebaseFirestoreException }
      ?: causes.firstOrNull { it !is CancellationException }
      ?: exception

  val errorInfoJson =
    JSONObject().apply {
      put("error", rootError.message ?: rootError.toString())
      put("operationType", operationType.value)
      put("path", path)
      if (BuildConfig.DEBUG) put("authInfo", currentAuthInfo())
    }

  val jsonString = errorInfoJson.toString()
  Log.e("FirestoreError", "Firestore Error: $jsonString")
  return jsonString
}

private fun currentAuthInfo(): JSONObject {
  val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
  val providerInfoList =
    currentUser?.providerData?.map { provider ->
      JSONObject().apply {
        put("providerId", provider.providerId)
        put("email", provider.email)
      }
    } ?: emptyList()

  return JSONObject().apply {
    put("userId", currentUser?.uid)
    put("email", currentUser?.email)
    put("emailVerified", currentUser?.isEmailVerified)
    put("tenantId", currentUser?.tenantId)
    put("providerInfo", JSONArray(providerInfoList))
  }
}

/**
 * Turns a failure into something worth showing in a snackbar. [fallback] says what was being
 * attempted ("Could not publish the listing."); raw SDK messages such as "PERMISSION_DENIED:
 * Missing or insufficient permissions." never reach the screen.
 */
fun Throwable.toUserMessage(fallback: String): String {
  val causes = generateSequence(this) { it.cause }.toList()
  causes.firstNotNullOfOrNull { it as? UserFacingException }?.let { return it.message }

  val firestoreError =
    causes.firstNotNullOfOrNull { it as? FirebaseFirestoreException } ?: return fallback
  val hint =
    when (firestoreError.code) {
      Code.UNAVAILABLE,
      Code.DEADLINE_EXCEEDED -> "You appear to be offline. Check your connection and try again."
      Code.PERMISSION_DENIED -> "You may no longer have access, or it changed in the meantime."
      Code.NOT_FOUND -> "It may have been removed."
      Code.ABORTED,
      Code.FAILED_PRECONDITION,
      Code.ALREADY_EXISTS -> "Someone else changed it at the same moment. Please try again."
      Code.RESOURCE_EXHAUSTED -> "Too many requests right now. Please try again shortly."
      Code.UNAUTHENTICATED -> "Your session expired. Please sign in again."
      else -> return fallback
    }
  return "$fallback $hint"
}
