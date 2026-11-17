package com.example.ui

import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import com.example.base.FirestoreEmulatorTestBase
import com.example.ui.screens.assertEveryControlHasAName
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
      check(System.currentTimeMillis() < deadline) { "Timed out waiting for the emulators" }
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

  private fun await(what: SemanticsMatcher) =
    waitOrDescribeScreen("Never saw ${what.description}") {
      compose.onAllNodes(what).fetchSemanticsNodes().isNotEmpty()
    }

  private fun awaitGone(what: SemanticsMatcher) =
    waitOrDescribeScreen("Still showing ${what.description}") {
      compose.onAllNodes(what).fetchSemanticsNodes().isEmpty()
    }

  /**
   * A step that never arrives fails with what was on screen instead: this test runs where nobody
   * can look, and a bare timeout says nothing about which of the layers underneath went wrong.
   */
  private fun waitOrDescribeScreen(failure: String, condition: () -> Boolean) {
    try {
      compose.waitUntil(TIMEOUT_MS, condition)
    } catch (e: ComposeTimeoutException) {
      val screen = compose.onAllNodes(isRoot()).printToString(maxDepth = Int.MAX_VALUE)
      throw AssertionError("$failure. On screen:\n$screen", e)
    }
  }

  private fun awaitTag(tag: String) = await(hasTestTag(tag))

  private fun awaitText(text: String) = await(hasText(text, substring = true))

  /** Onboarding, for a user who is not in a block yet: start one. Ends on its empty feed. */
  private fun startBlock(name: String) {
    awaitTag("block_name_input")
    compose.onNodeWithTag("block_name_input").performTextInput(name)
    compose.onNodeWithTag("create_block_submit_button").assertIsEnabled().performClick()
    awaitTag("post_listing_fab")
    awaitTag("empty_post_button")
  }

  /** Posts a tool through the editor sheet and waits for it to show up on the feed. */
  private fun postTool(title: String) {
    compose.onNodeWithTag("post_listing_fab").performClick()
    awaitTag("create_title_input")
    compose.onNodeWithTag("create_title_input").performTextInput(title)
    compose.onNodeWithTag("publish_listing_button").assertIsEnabled().performClick()
    awaitText("Posted by you")
    compose.onNodeWithText(title).assertExists()
  }

  private fun signOutFromTheTrustTab() {
    compose.onNodeWithTag("nav_item_trust").performClick()
    awaitTag("trust_sign_out_button")
    compose.onNodeWithTag("trust_sign_out_button").performClick()
    awaitTag("google_sign_in_button")
  }

  /** The invite code of the block the signed-in user is in, as a neighbour would be told it. */
  private fun inviteCodeOfMyBlock(): String {
    val uid = checkNotNull(auth.currentUser).uid
    val myBlocks = firestore.collection("circles").whereArrayContains("memberUids", uid).get()
    awaitTask(myBlocks)
    return checkNotNull(myBlocks.result.documents.single().getString("inviteCode"))
  }

  @Test
  fun `a new neighbour starts a block, posts a tool, looks around and signs out`() {
    signIn(ALICE)
    launchApp()

    // No block yet: onboarding. Once the block exists: the three-tab shell with an empty feed.
    startBlock("Smoke Test Block")
    compose.onNodeWithText("Smoke Test Block").assertExists()

    postTool("Extension Ladder 20ft")
    // The shell's own controls, which no screen test sees: the tabs and the post button.
    compose.assertEveryControlHasAName()

    // The other two tabs render with this user's data.
    compose.onNodeWithTag("nav_item_escrow").performClick()
    awaitText("No active escrow holds")
    compose.onNodeWithTag("nav_item_trust").performClick()
    awaitText("NEIGHBOR TRUST CARD")
    compose.onNodeWithText("1 neighbor", substring = true).assertExists()

    // Signing out tears the session down and lands on the sign-in screen.
    compose.onNodeWithTag("trust_sign_out_button").performClick()
    awaitTag("google_sign_in_button")
    compose.assertEveryControlHasAName()
  }

  @Test
  fun `a neighbour joins with the invite code and books the tool, and its owner is asked to approve`() {
    // Alice starts a block and lists her ladder.
    signIn(ALICE)
    launchApp()
    startBlock("Smoke Test Block")
    postTool("Extension Ladder 20ft")
    val inviteCode = inviteCodeOfMyBlock()

    // Bob takes over the running app. He is not in a block yet, and joins Alice's with her code.
    signOutFromTheTrustTab()
    signIn(BOB)
    awaitTag("tab_join_block")
    compose.onNodeWithTag("tab_join_block").performClick()
    compose.onNodeWithTag("invite_code_input").performTextInput(inviteCode)
    compose.onNodeWithTag("join_block_submit_button").assertIsEnabled().performClick()

    // Her ladder is on his feed. He books it, and is told whose move it is.
    awaitText("Extension Ladder 20ft")
    compose.onNodeWithText("Extension Ladder 20ft").performClick()
    awaitTag("confirm_escrow_book_button")
    compose.onNodeWithTag("confirm_escrow_book_button").assertIsEnabled().performClick()
    awaitGone(hasTestTag("confirm_escrow_book_button"))
    compose.onNodeWithTag("nav_item_escrow").performClick()
    awaitText("Waiting for $ALICE_NAME to approve your request.")

    // Back to Alice: the Escrow tab flags the request and offers her the next step.
    signOutFromTheTrustTab()
    signIn(ALICE)
    await(hasContentDescription("Escrow, 1 waiting for you"))
    compose.onNodeWithTag("nav_item_escrow").performClick()
    awaitText("With $BOB_NAME")
    compose.onNodeWithText("Approve & Lock").assertIsEnabled()
  }

  @Test
  fun `signed out, the app shows the sign-in screen`() {
    launchApp()

    awaitTag("google_sign_in_button")
  }

  private companion object {
    const val TIMEOUT_MS = 20_000L
    const val PASSWORD = "password123"

    // Without a display name, a user goes by the part of the email address before the "@".
    const val ALICE_NAME = "smoke_alice"
    const val BOB_NAME = "smoke_bob"
    const val ALICE = "$ALICE_NAME@test.com"
    const val BOB = "$BOB_NAME@test.com"
  }
}
