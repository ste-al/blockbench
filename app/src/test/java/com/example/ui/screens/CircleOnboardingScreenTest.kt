package com.example.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TALL_PHONE)
class CircleOnboardingScreenTest {

  @get:Rule val compose = createComposeRule()

  private val created = mutableListOf<Pair<String, Double>>()
  private val joined = mutableListOf<String>()
  private var signOuts = 0

  private fun show(isBusy: Boolean = false) {
    compose.setThemedContent {
      CircleOnboardingScreen(
        isBusy = isBusy,
        onCreateCircle = { name, radius -> created += name to radius },
        onJoinCircle = { joined += it },
        onSignOut = { signOuts++ },
      )
    }
  }

  @Test
  fun `a block needs a name of at least two characters`() {
    show()

    compose.onNodeWithTag("create_block_submit_button").assertIsNotEnabled()
    compose.onNodeWithTag("block_name_input").performTextInput("O")
    compose.onNodeWithTag("create_block_submit_button").assertIsNotEnabled()
    compose.onNodeWithTag("block_name_input").performTextInput("ak Block")
    compose.onNodeWithTag("create_block_submit_button").assertIsEnabled().performClick()

    assertEquals(listOf("Oak Block" to 1.5), created)
  }

  @Test
  fun `an invite code is tidied as it is typed and must be complete to submit`() {
    show()
    compose.onNodeWithTag("tab_join_block").performClick()

    compose.onNodeWithTag("join_block_submit_button").assertIsNotEnabled()
    compose.onNodeWithTag("invite_code_input").performTextInput("oak")
    compose.onNodeWithTag("join_block_submit_button").assertIsNotEnabled()
    compose.onNodeWithTag("invite_code_input").performTextInput("-7k2 mq")

    compose.onNodeWithTag("invite_code_input").assertTextContains("OAK7K2MQ")
    compose.onNodeWithTag("join_block_submit_button").assertIsEnabled().performClick()
    assertEquals(listOf("OAK7K2MQ"), joined)
  }

  @Test
  fun `nothing can be submitted twice while a request is in flight`() {
    show(isBusy = true)
    compose.onNodeWithTag("block_name_input").performTextInput("Oak Block")

    compose.onNodeWithTag("create_block_submit_button").assertIsNotEnabled()
  }

  @Test
  fun `signing out is always available`() {
    show()
    compose.onNodeWithTag("onboarding_sign_out_button").performClick()

    assertEquals(1, signOuts)
  }
}
