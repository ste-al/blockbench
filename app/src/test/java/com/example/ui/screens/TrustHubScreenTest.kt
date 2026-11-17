package com.example.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.model.BookingTotals
import com.example.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TALL_PHONE)
class TrustHubScreenTest {

  @get:Rule val compose = createComposeRule()

  private data class Saved(val enabled: Boolean, val radiusMiles: Double, val categories: List<String>)

  private val saves = mutableListOf<Saved>()

  private fun show(
    profile: UserProfile,
    totals: BookingTotals = BookingTotals(),
    isBusy: Boolean = false,
  ) {
    compose.setThemedContent {
      TrustHubScreen(
        profile = profile,
        totals = totals,
        activeCircle = sampleCircle,
        circlesCount = 1,
        proAlertMatches = emptyList(),
        isBusy = isBusy,
        onSaveProAlerts = { enabled, radius, categories -> saves += Saved(enabled, radius, categories) },
        onOpenBlockSwitcher = {},
        onInspectProMatch = {},
        onShowFeedback = {},
        onSignOut = {},
      )
    }
  }

  @Test
  fun `the activate button turns Pro Alerts on`() {
    show(sampleProfile(proAlertsEnabled = false))

    compose.onNodeWithText("Activate Pro Alerts (\$4.99/mo)").assertExists()
    compose.onNodeWithTag("save_pro_alerts_button").assertIsEnabled().performClick()

    assertEquals(listOf(Saved(true, 1.5, listOf("Power Tools", "Quick Fix"))), saves)
  }

  @Test
  fun `the switch saves the value it is flipped to`() {
    show(sampleProfile(proAlertsEnabled = true))

    compose.onNodeWithTag("pro_alerts_switch").assertIsOn().performClick()

    assertEquals(listOf(Saved(false, 1.5, listOf("Power Tools", "Quick Fix"))), saves)
  }

  @Test
  fun `the switch keeps showing what is saved until the save comes back`() {
    show(sampleProfile(proAlertsEnabled = false))

    // The save callback above does not change the profile, as when a save fails.
    compose.onNodeWithTag("pro_alerts_switch").assertIsOff().performClick()

    assertEquals(1, saves.size)
    compose.onNodeWithTag("pro_alerts_switch").assertIsOff()
  }

  @Test
  fun `with nothing to save the button is disabled, and editing the rules enables it`() {
    show(sampleProfile(proAlertsEnabled = true))

    compose.onNodeWithText("Pro Alert Rules Saved").assertExists()
    compose.onNodeWithTag("save_pro_alerts_button").assertIsNotEnabled()

    compose.onNodeWithText("Haul & Cleanup").performClick()

    compose.onNodeWithText("Save Updated Watch Rules").assertExists()
    compose.onNodeWithTag("save_pro_alerts_button").assertIsEnabled().performClick()
    assertEquals(1, saves.size)
    assertTrue(saves.single().enabled)
    assertEquals(listOf("Power Tools", "Quick Fix", "Haul & Cleanup"), saves.single().categories)
  }

  @Test
  fun `nothing can be saved while another write is in flight`() {
    show(sampleProfile(proAlertsEnabled = false), isBusy = true)

    compose.onNodeWithTag("save_pro_alerts_button").assertIsNotEnabled()
    compose.onNodeWithTag("pro_alerts_switch").assertIsNotEnabled()
  }

  @Test
  fun `the card and the ledger show the totals derived from bookings`() {
    show(
      sampleProfile().copy(completedHandoffs = 3L, onTimeReturns = 3L),
      totals = BookingTotals(completedHandoffs = 3L, earnedCents = 123_456L, feesCents = 270L),
    )

    compose.onNodeWithText("\$1,234.56").assertExists()
    compose.onNodeWithText("\$2.70").assertExists()
    compose.onNodeWithText("100%").assertExists()
    compose.onNodeWithText("Active block: Oak & 14th Block (2 neighbors). You belong to 1 block.").assertExists()
  }
}
