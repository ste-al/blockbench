package com.example.ui

import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.base.FirestoreEmulatorTestBase
import com.example.ui.theme.BlockBenchTheme
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import org.junit.Rule
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/**
 * The whole app, from the root composable down to Firestore and back, against the emulators:
 * session handling, onboarding, the navigation shell, posting a listing and signing out.
 *
 * It exists to catch wiring mistakes that the focused tests cannot see, such as a ViewModel that
 * cannot be created, a navigation host without its owner, or a write the rules refuse.
 *
 * Unlike the other emulator tests this one runs with Robolectric's default (paused) main looper,
 * which is what the Compose test rule supports. The test thread is then the main thread, so it
 * must never block on a Firebase Task; [signIn] pumps the looper while it waits instead.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class AppShellSmokeTest : FirestoreEmulatorTestBase() {

  @get:Rule val compose = createComposeRule()

  private fun signIn(email: String) {
    var attempt: Task<AuthResult> = auth.signInWithEmailAndPassword(email, PASSWORD)
    awaitTask(attempt)
    if (!attempt.isSuccessful) {
      attempt = auth.createUserWithEmailAndPassword(email, PASSWORD)
      awaitTask(attempt)
    }
    check(attempt.isSuccessful) { "Could not sign in to the Auth emulator: ${attempt.exception}" }
  }

  private fun awaitTask(task: Task<*>) {
    val deadline = System.currentTimeMillis() + TIMEOUT_MS
    while (!task.isComplete) {
      check(System.currentTimeMillis() < deadline) { "Timed out waiting for the Auth emulator" }
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(20)
    }
  }

  private fun launchApp() {
    compose.setContent {
      BlockBenchTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) { BlockBenchAppRoot() }
      }
    }
  }

  private fun awaitTag(tag: String) {
    compose.waitUntil(TIMEOUT_MS) {
      compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun awaitText(text: String) {
    compose.waitUntil(TIMEOUT_MS) {
      compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun `a new neighbour starts a block, posts a tool, looks around and signs out`() {
    signIn("smoke_alice@test.com")
    launchApp()

    // No block yet: onboarding.
    awaitTag("block_name_input")
    compose.onNodeWithTag("block_name_input").performTextInput("Smoke Test Block")
    compose.onNodeWithTag("create_block_submit_button").assertIsEnabled().performClick()

    // The block exists: the three-tab shell with an empty feed.
    awaitTag("post_listing_fab")
    compose.onNodeWithText("Smoke Test Block").assertExists()
    awaitTag("empty_post_button")

    // Post a tool through the editor sheet; it shows up on the feed.
    compose.onNodeWithTag("post_listing_fab").performClick()
    awaitTag("create_title_input")
    compose.onNodeWithTag("create_title_input").performTextInput("Extension Ladder 20ft")
    compose.onNodeWithTag("publish_listing_button").assertIsEnabled().performClick()
    awaitText("Posted by you")
    compose.onNodeWithText("Extension Ladder 20ft").assertExists()

    // The other two tabs render with this user's data.
    compose.onNodeWithTag("nav_item_escrow").performClick()
    awaitText("No active escrow holds")
    compose.onNodeWithTag("nav_item_trust").performClick()
    awaitText("NEIGHBOR TRUST CARD")
    compose.onNodeWithText("1 neighbor", substring = true).assertExists()

    // Signing out tears the session down and lands on the sign-in screen.
    compose.onNodeWithTag("trust_sign_out_button").performClick()
    awaitTag("google_sign_in_button")
  }

  @Test
  fun `signed out, the app shows the sign-in screen`() {
    launchApp()

    awaitTag("google_sign_in_button")
  }

  private companion object {
    const val TIMEOUT_MS = 20_000L
    const val PASSWORD = "password123"
  }
}
