package com.example.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Base class for tests that run the real Firebase SDK against the local Firestore and Auth
 * emulators, with `firestore.rules` in force.
 *
 * Start the emulators with `npm run emulators`, or run everything in one go with
 * `npm run test:android`. When the emulators are not running these tests are skipped rather than
 * failed, so a plain `./gradlew test` stays usable on a machine without the Firebase tooling.
 * Inside `firebase emulators:exec` (which is how CI runs them) a missing emulator is an error.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
abstract class FirestoreEmulatorTestBase {

  protected lateinit var firestore: FirebaseFirestore
  protected lateinit var auth: FirebaseAuth
  protected lateinit var databaseId: String

  @Before
  open fun setUpFirebase() {
    val emulatorsRunning = isListening(FIRESTORE_PORT) && isListening(AUTH_PORT)
    if (System.getenv("FIRESTORE_EMULATOR_HOST") != null) {
      check(emulatorsRunning) {
        "Running under `firebase emulators:exec`, but the Firestore ($FIRESTORE_PORT) and Auth " +
          "($AUTH_PORT) emulators are not both reachable. Check the ports in firebase.json."
      }
    }
    assumeTrue(
      "Firebase emulators are not running on $EMULATOR_HOST ($FIRESTORE_PORT / $AUTH_PORT); " +
        "start them with `npm run emulators` to run this test.",
      emulatorsRunning,
    )

    val context = ApplicationProvider.getApplicationContext<Context>()
    val resId =
      context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
    databaseId = if (resId != 0) context.getString(resId) else "demo-database"

    val app =
      if (FirebaseApp.getApps(context).isEmpty()) {
        val newApp =
          FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
              .setApplicationId("com.aistudio.blockbench.mrvkqz")
              .setProjectId(PROJECT_ID)
              .setApiKey("fake-api-key-for-emulator")
              .build(),
          )
        val newFirestore = FirebaseFirestore.getInstance(newApp, databaseId)
        newFirestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
        newFirestore.firestoreSettings =
          FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()

        val newAuth = FirebaseAuth.getInstance(newApp)
        newAuth.useEmulator(EMULATOR_HOST, AUTH_PORT)
        newApp
      } else {
        FirebaseApp.getInstance()
      }

    firestore = FirebaseFirestore.getInstance(app, databaseId)
    auth = FirebaseAuth.getInstance(app)

    // Every test starts from an empty database, whatever earlier tests or runs left behind.
    auth.signOut()
    clearDatabase()
  }

  @After
  open fun tearDownFirebase() {
    if (::auth.isInitialized) auth.signOut()
  }

  protected suspend fun signInTestUser(email: String): String =
    withContext(Dispatchers.IO) {
      withTimeout(AUTH_TIMEOUT_MS) {
        val result =
          try {
            auth.signInWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
          } catch (unused: FirebaseAuthInvalidUserException) {
            auth.createUserWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
          }
        checkNotNull(result.user?.uid) { "User auth failed" }
      }
    }

  protected fun Throwable.unwrapFirestoreException(): FirebaseFirestoreException? {
    var current: Throwable? = this
    while (current != null) {
      if (current is FirebaseFirestoreException) return current
      current = current.cause
    }
    return null
  }

  /**
   * Writes [fields] to [documentPath] as the emulator's admin, bypassing the security rules. Used
   * to stage documents in shapes that only earlier versions of the app could have written.
   * Supported values: String, Boolean, Int/Long, Double, [Timestamp] and lists of those.
   */
  protected fun adminSetDocument(documentPath: String, fields: Map<String, Any>) {
    val databasePath = "projects/$PROJECT_ID/databases/$databaseId"
    val body =
      JSONObject()
        .put(
          "writes",
          JSONArray()
            .put(
              JSONObject()
                .put(
                  "update",
                  JSONObject()
                    .put("name", "$databasePath/documents/$documentPath")
                    .put("fields", fields.toFirestoreFields()),
                )
            ),
        )
        .toString()
    val status =
      emulatorRequest("POST", "v1/$databasePath/documents:commit") { connection ->
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray()) }
      }
    check(status in 200..299) { "Seeding $documentPath failed with HTTP $status" }
  }

  private fun clearDatabase() {
    val status =
      emulatorRequest(
        "DELETE",
        "emulator/v1/projects/$PROJECT_ID/databases/$databaseId/documents",
      )
    check(status in 200..299) { "Clearing the emulator database failed with HTTP $status" }
  }

  private fun emulatorRequest(
    method: String,
    path: String,
    configure: (HttpURLConnection) -> Unit = {},
  ): Int {
    val url = URL("http://$EMULATOR_HOST:$FIRESTORE_PORT/$path")
    val connection = url.openConnection() as HttpURLConnection
    return try {
      connection.requestMethod = method
      connection.connectTimeout = HTTP_TIMEOUT_MS
      connection.readTimeout = HTTP_TIMEOUT_MS
      // The emulator treats this token as an admin credential that is not subject to the rules.
      connection.setRequestProperty("Authorization", "Bearer owner")
      configure(connection)
      connection.responseCode
    } finally {
      connection.disconnect()
    }
  }

  private fun Map<String, Any>.toFirestoreFields(): JSONObject =
    JSONObject().also { json -> forEach { (key, value) -> json.put(key, value.toFirestoreValue()) } }

  private fun Any.toFirestoreValue(): JSONObject =
    when (this) {
      is String -> JSONObject().put("stringValue", this)
      is Boolean -> JSONObject().put("booleanValue", this)
      is Int -> JSONObject().put("integerValue", toString())
      is Long -> JSONObject().put("integerValue", toString())
      is Double -> JSONObject().put("doubleValue", this)
      is Timestamp -> JSONObject().put("timestampValue", toRfc3339())
      is List<*> ->
        JSONObject()
          .put(
            "arrayValue",
            JSONObject().put("values", JSONArray(map { checkNotNull(it).toFirestoreValue() })),
          )
      else -> error("Unsupported seed value: $this")
    }

  private fun Timestamp.toRfc3339(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
      .apply { timeZone = TimeZone.getTimeZone("UTC") }
      .format(Date(seconds * 1000))

  private fun isListening(port: Int): Boolean =
    try {
      Socket().use { it.connect(InetSocketAddress(EMULATOR_HOST, port), CONNECT_TIMEOUT_MS) }
      true
    } catch (unused: IOException) {
      false
    }

  private companion object {
    const val EMULATOR_HOST = "127.0.0.1"
    const val FIRESTORE_PORT = 8085
    const val AUTH_PORT = 9099
    const val PROJECT_ID = "demo-no-project"
    const val DEFAULT_PASSWORD = "password123"
    const val AUTH_TIMEOUT_MS = 5000L
    const val CONNECT_TIMEOUT_MS = 300
    const val HTTP_TIMEOUT_MS = 5000
  }
}
