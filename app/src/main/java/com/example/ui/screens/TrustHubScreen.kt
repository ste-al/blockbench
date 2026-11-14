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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BlockCircle
import com.example.model.Booking
import com.example.model.CraftCategories
import com.example.model.Listing
import com.example.model.UserProfile
import com.example.ui.components.CraftCard
import com.example.ui.components.LedgerRow
import com.example.ui.components.NeighborTrustCardView
import com.example.ui.components.copyInviteCodeToClipboard
import com.example.ui.components.formatCents
import com.example.ui.components.shareNeighborhoodInvite
import com.example.ui.components.shareTrustCard
import com.example.ui.theme.EscrowSage
import com.example.ui.theme.MonoFigureStyle
import com.example.ui.theme.ProAmber
import com.example.ui.theme.ProAmberSoft
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrustHubScreen(
  currentUserId: String,
  profile: UserProfile,
  activeCircle: BlockCircle,
  circlesCount: Int,
  bookings: List<Booking>,
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
  var proEnabled by remember(profile.proAlertsEnabled) { mutableStateOf(profile.proAlertsEnabled) }
  var alertRadius by remember(profile.alertRadiusMiles) { mutableDoubleStateOf(profile.alertRadiusMiles) }
  var alertCats by remember(profile.alertCategories) { mutableStateOf(profile.alertCategories) }

  val completedBookings = remember(bookings) { bookings.filter { it.status == Booking.STATUS_COMPLETED } }
  val liveEarnedCents =
    remember(completedBookings, currentUserId, profile.totalEarnedCents) {
      maxOf(
        profile.totalEarnedCents,
        completedBookings.filter { it.providerUid == currentUserId }.sumOf { it.providerNetCents },
      )
    }
  val liveFeesCents =
    remember(completedBookings, profile.totalFeesSavedOrPaidCents) {
      maxOf(
        profile.totalFeesSavedOrPaidCents,
        completedBookings.sumOf { it.platformFeeCents },
      )
    }
  val liveHandoffs =
    remember(completedBookings, profile.completedHandoffs) {
      maxOf(profile.completedHandoffs, completedBookings.size.toLong())
    }
  val displayProfile =
    remember(profile, liveEarnedCents, liveFeesCents, liveHandoffs) {
      profile.copy(
        completedHandoffs = liveHandoffs,
        onTimeReturns = maxOf(profile.onTimeReturns, liveHandoffs),
        totalEarnedCents = liveEarnedCents,
        totalFeesSavedOrPaidCents = liveFeesCents,
      )
    }

  val hasUnsavedProChanges =
    proEnabled != profile.proAlertsEnabled ||
      abs(alertRadius - profile.alertRadiusMiles) > 0.05 ||
      alertCats.toSet() != profile.alertCategories.toSet()

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      NeighborTrustCardView(
        profile = displayProfile,
        circleName = activeCircle.name,
        inviteCode = activeCircle.inviteCode,
        onShareTrustCard = { shareTrustCard(context, displayProfile, activeCircle.name) },
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
            value = formatCents(liveEarnedCents),
            bold = true,
            valueColor = EscrowSage,
          )
          LedgerRow(
            label = "5% Escrow Platform Fees",
            value = formatCents(liveFeesCents),
          )
          LedgerRow(
            label = "Completed Block Handoffs",
            value = liveHandoffs.toString(),
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
          if (proEnabled) ProAmberSoft.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
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
              Surface(color = ProAmberSoft, shape = RoundedCornerShape(10.dp)) {
                Icon(
                  imageVector = Icons.Filled.NotificationsActive,
                  contentDescription = "Pro Alerts",
                  tint = ProAmber,
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
                  color = ProAmber,
                )
              }
            }
            Switch(
              checked = proEnabled,
              onCheckedChange = { checked ->
                proEnabled = checked
                onSaveProAlerts(checked, alertRadius, alertCats)
              },
              modifier = Modifier.testTag("pro_alerts_switch"),
            )
          }

          Text(
            text =
              "Auto-match instant micro-jobs and tool requests in ${activeCircle.name} before general members see them. Currently matching ${proAlertMatches.size} live listings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          HorizontalDivider(color = MaterialTheme.colorScheme.outline)

          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("Alert Watch Radius", style = MaterialTheme.typography.labelLarge)
              Text(
                text = String.format(Locale.US, "%.1f mi", alertRadius),
                style = MonoFigureStyle,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Slider(
              value = alertRadius.toFloat(),
              onValueChange = { alertRadius = it.toDouble() },
              valueRange = 0.5f..5.0f,
              steps = 8,
            )
          }

          Text("Watched Categories", style = MaterialTheme.typography.labelLarge)
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            CraftCategories.all.forEach { cat ->
              val selected = cat in alertCats
              FilterChip(
                selected = selected,
                onClick = {
                  alertCats = if (selected) alertCats - cat else (alertCats + cat).take(8)
                },
                label = { Text(cat) },
              )
            }
          }

          Button(
            onClick = { onSaveProAlerts(proEnabled, alertRadius, alertCats) },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth().testTag("save_pro_alerts_button"),
          ) {
            Text(
              when {
                hasUnsavedProChanges && proEnabled -> "Save Updated Watch Rules"
                proEnabled -> "Pro Alert Rules Saved"
                else -> "Activate Pro Alerts ($4.99/mo)"
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
          containerColor = ProAmberSoft.copy(alpha = 0.4f),
          modifier = Modifier.fillMaxWidth().testTag("pro_match_card_${listing.id}"),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.ElectricBolt,
                contentDescription = null,
                tint = ProAmber,
                modifier = Modifier.size(20.dp),
              )
              Column {
                Text(text = listing.title, style = MaterialTheme.typography.titleMedium)
                Text(
                  text =
                    "${listing.category} · ${String.format(Locale.US, "%.1f", listing.distanceMiles)} mi · ${formatCents(listing.rateCents)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = "View match in feed",
              tint = ProAmber,
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
              "Active Circle: ${activeCircle.name} (${activeCircle.memberUids.size} neighbors · $circlesCount joined)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = onOpenBlockSwitcher,
              modifier = Modifier.weight(1f).testTag("switch_block_button"),
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
