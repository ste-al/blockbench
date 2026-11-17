package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BlockCircle
import com.example.model.BookingTotals
import com.example.model.CraftCategories
import com.example.model.EscrowFeeBreakdown
import com.example.model.Listing
import com.example.model.UserProfile
import com.example.ui.components.ActionRow
import com.example.ui.components.BlockRadiusSlider
import com.example.ui.components.CraftCard
import com.example.ui.components.LedgerRow
import com.example.ui.components.NeighborTrustCardView
import com.example.ui.components.copyInviteCodeToClipboard
import com.example.ui.components.formatCents
import com.example.ui.components.formatMiles
import com.example.ui.components.neighborsLabel
import com.example.ui.components.shareNeighborhoodInvite
import com.example.ui.components.shareTrustCard
import com.example.ui.theme.blockBenchColors
import kotlin.math.abs

/**
 * Trust Card, earnings ledger, Pro Alert rules and account controls.
 *
 * [totals] are the figures derived from the user's completed bookings; they are what the card and
 * the ledger show, so both always agree with the Escrow tab.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrustHubScreen(
  profile: UserProfile,
  totals: BookingTotals,
  activeCircle: BlockCircle,
  circlesCount: Int,
  proAlertMatches: List<Listing>,
  isBusy: Boolean,
  onSaveProAlerts: (enabled: Boolean, radiusMiles: Double, categories: List<String>) -> Unit,
  onOpenBlockSwitcher: () -> Unit,
  onInspectProMatch: () -> Unit,
  onShowFeedback: (String) -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val brand = blockBenchColors

  // The switch always shows what is saved: if a save fails it must not be left looking "on".
  val proEnabled = profile.proAlertsEnabled
  // Unsaved edits to the rules; re-seeded whenever the saved value changes.
  var alertRadius by
    remember(profile.alertRadiusMiles) { mutableDoubleStateOf(profile.alertRadiusMiles) }
  var alertCats by remember(profile.alertCategories) { mutableStateOf(profile.alertCategories) }

  val hasUnsavedRuleChanges =
    abs(alertRadius - profile.alertRadiusMiles) > 0.05 ||
      alertCats.toSet() != profile.alertCategories.toSet()

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      NeighborTrustCardView(
        profile = profile,
        totals = totals,
        circleName = activeCircle.name,
        inviteCode = activeCircle.inviteCode,
        onShareTrustCard = {
          shareTrustCard(context, profile, totals, activeCircle.name, activeCircle.inviteCode)
        },
        onOneTapInvite = {
          shareNeighborhoodInvite(context, activeCircle.name, activeCircle.inviteCode)
        },
        onCopyInviteCode = {
          copyInviteCodeToClipboard(context, activeCircle.inviteCode, onShowFeedback)
        },
      )
    }

    // Passive Income & Micro-Escrow Ledger Card
    item {
      CraftCard {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Passive Income & Fee Ledger",
            style = MaterialTheme.typography.titleLarge,
          )
          LedgerRow(
            label = "Net Passive Earnings",
            value = formatCents(totals.earnedCents),
            bold = true,
            valueColor = brand.escrow,
          )
          LedgerRow(
            label = "${EscrowFeeBreakdown.PLATFORM_FEE_PERCENT}% Escrow Platform Fees",
            value = formatCents(totals.feesCents),
          )
          LedgerRow(
            label = "Completed Block Handoffs",
            value = totals.completedHandoffs.toString(),
          )
          Text(
            text = stringResource(id = R.string.escrow_fee_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // BlockBench Pro Alerts Subscription & Auto-Match Rules
    item {
      CraftCard(
        containerColor =
          if (proEnabled) {
            brand.proContainer.copy(alpha = 0.55f)
          } else {
            MaterialTheme.colorScheme.surface
          },
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Surface(color = brand.proContainer, shape = RoundedCornerShape(10.dp)) {
                Icon(
                  imageVector = Icons.Filled.NotificationsActive,
                  contentDescription = null,
                  tint = brand.onProContainer,
                  modifier = Modifier.padding(8.dp).size(22.dp),
                )
              }
              Column {
                Text(
                  text = stringResource(id = R.string.pro_alerts_title),
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = stringResource(id = R.string.pro_alerts_price),
                  style = MaterialTheme.typography.bodySmall,
                  color = brand.onProContainer,
                )
              }
            }
            val proAlertsTitle = stringResource(id = R.string.pro_alerts_title)
            Switch(
              checked = proEnabled,
              onCheckedChange = { checked -> onSaveProAlerts(checked, alertRadius, alertCats) },
              enabled = !isBusy,
              modifier =
                Modifier.testTag("pro_alerts_switch").semantics {
                  contentDescription = proAlertsTitle
                },
            )
          }

          Text(
            text =
              "Auto-match instant micro-jobs and tool requests in ${activeCircle.name} before " +
                "general members see them. Your saved rules currently match " +
                "${proAlertMatches.size} live " +
                if (proAlertMatches.size == 1) "listing." else "listings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          BlockRadiusSlider(
            radiusMiles = alertRadius,
            onRadiusChange = { alertRadius = it },
            label = "Alert Watch Radius",
          )

          Text("Watched Categories", style = MaterialTheme.typography.labelLarge)
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            CraftCategories.all.forEach { cat ->
              val selected = cat in alertCats
              FilterChip(
                selected = selected,
                onClick = { alertCats = if (selected) alertCats - cat else alertCats + cat },
                label = { Text(cat) },
              )
            }
          }

          // The one button activates the subscription when it is off and saves edited rules when
          // it is on. (It used to save "off" again while offering to "Activate".)
          Button(
            onClick = { onSaveProAlerts(true, alertRadius, alertCats) },
            enabled = !isBusy && (!proEnabled || hasUnsavedRuleChanges),
            modifier = Modifier.fillMaxWidth().testTag("save_pro_alerts_button"),
          ) {
            Text(
              when {
                !proEnabled -> stringResource(id = R.string.pro_alerts_activate)
                hasUnsavedRuleChanges -> "Save Updated Watch Rules"
                else -> "Pro Alert Rules Saved"
              }
            )
          }
        }
      }
    }

    if (proAlertMatches.isNotEmpty()) {
      item {
        Text(
          text = "Live Pro Alert Matches (${proAlertMatches.size})",
          style = MaterialTheme.typography.titleMedium,
        )
      }
      items(proAlertMatches.take(3), key = { it.id }) { listing ->
        CraftCard(
          onClick = onInspectProMatch,
          containerColor = brand.proContainer.copy(alpha = 0.4f),
          modifier = Modifier.fillMaxWidth().testTag("pro_match_card_${listing.id}"),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.ElectricBolt,
              contentDescription = null,
              tint = brand.onProContainer,
              modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = listing.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text =
                  "${listing.category} · ${formatMiles(listing.distanceMiles)} · ${formatCents(listing.rateCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = "View match in feed",
              tint = brand.onProContainer,
            )
          }
        }
      }
    }

    // Active Block & Account Controls
    item {
      CraftCard {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(text = "Neighborhood Block & Account", style = MaterialTheme.typography.titleMedium)
          Text(
            text =
              "Active block: ${activeCircle.name} (${neighborsLabel(activeCircle.memberUids.size)}). " +
                "You belong to $circlesCount " +
                if (circlesCount == 1) "block." else "blocks.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          ActionRow(weights = listOf(1f, 0f)) {
            OutlinedButton(
              onClick = onOpenBlockSwitcher,
              modifier = Modifier.testTag("switch_block_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Manage Blocks")
            }
            TextButton(
              onClick = onSignOut,
              colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
              modifier = Modifier.testTag("trust_sign_out_button"),
            ) {
              Text(stringResource(id = R.string.sign_out))
            }
          }
        }
      }
    }
  }
}
