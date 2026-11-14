package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.BlockCircle
import com.example.model.EscrowFeeBreakdown
import com.example.model.Listing
import com.example.model.UserProfile
import com.example.ui.theme.DeepPine
import com.example.ui.theme.EscrowSage
import com.example.ui.theme.EscrowSageSoft
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.MonoFigureStyle
import com.example.ui.theme.ProAmber
import com.example.ui.theme.ProAmberSoft
import java.util.Locale

fun formatCents(cents: Long): String {
  val dollars = cents / 100.0
  return String.format(Locale.US, "$%.2f", dollars)
}

fun copyInviteCodeToClipboard(
  context: Context,
  inviteCode: String,
  onCopied: (String) -> Unit,
) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
  clipboard?.setPrimaryClip(ClipData.newPlainText("BlockBench Invite Code", inviteCode))
  onCopied("Copied block invite code $inviteCode")
}

fun shareNeighborhoodInvite(context: Context, circleName: String, inviteCode: String) {
  val message =
    "Join our \"$circleName\" tool-share & micro-services block on BlockBench! " +
      "Enter invite code $inviteCode to borrow nearby tools or claim quick neighborhood jobs."
  val sendIntent =
    Intent(Intent.ACTION_SEND).apply {
      action = Intent.ACTION_SEND
      putExtra(Intent.EXTRA_TEXT, message)
      type = "text/plain"
    }
  context.startActivity(Intent.createChooser(sendIntent, "Invite Neighbor to $circleName"))
}

fun shareTrustCard(context: Context, profile: UserProfile, circleName: String) {
  val onTimePct =
    if (profile.completedHandoffs > 0L) {
      ((profile.onTimeReturns.toDouble() / profile.completedHandoffs.toDouble()) * 100).toInt()
    } else {
      100
    }
  val message =
    "BlockBench Neighbor Trust Card — ${profile.displayName}\n" +
      "Block: ${circleName.ifBlank { profile.neighborhoodLabel }}\n" +
      "Trust Score: ${String.format(Locale.US, "%.1f", profile.trustScore)} / 5.0 · " +
      "${profile.completedHandoffs} verified handoffs · $onTimePct% on-time returns\n" +
      if (profile.activeInviteCode.isNotBlank()) {
        "Join our block with code: ${profile.activeInviteCode}"
      } else {
        "Verified neighbor on BlockBench"
      }
  val sendIntent =
    Intent(Intent.ACTION_SEND).apply {
      action = Intent.ACTION_SEND
      putExtra(Intent.EXTRA_TEXT, message)
      type = "text/plain"
    }
  context.startActivity(Intent.createChooser(sendIntent, "Share Neighbor Trust Card"))
}

@Composable
fun CraftCard(
  modifier: Modifier = Modifier,
  containerColor: Color = MaterialTheme.colorScheme.surface,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
  if (onClick != null) {
    Card(
      onClick = onClick,
      modifier = modifier,
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = containerColor),
      border = border,
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
      content = content,
    )
  } else {
    Card(
      modifier = modifier,
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = containerColor),
      border = border,
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
      content = content,
    )
  }
}

@Composable
fun ListingCraftCard(
  listing: Listing,
  isOwnListing: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isJob = listing.kind == Listing.KIND_MICRO_JOB
  val statusLabel =
    when (listing.status) {
      Listing.STATUS_RESERVED -> "Reserved"
      Listing.STATUS_PAUSED -> "Paused"
      else -> if (isJob) "Open Job" else "Available"
    }

  CraftCard(
    onClick = onClick,
    modifier = modifier.fillMaxWidth().testTag("listing_card_${listing.id}"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            color =
              if (isJob) {
                MaterialTheme.colorScheme.secondaryContainer
              } else {
                MaterialTheme.colorScheme.primaryContainer
              },
            shape = RoundedCornerShape(8.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = if (isJob) Icons.Filled.Handyman else Icons.Filled.Build,
                contentDescription = if (isJob) "Micro-job" else "Tool",
                modifier = Modifier.size(14.dp),
                tint =
                  if (isJob) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                  } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                  },
              )
              Text(
                text = if (isJob) "MICRO-JOB" else "TOOL",
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (isJob) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                  } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                  },
              )
            }
          }
          if (listing.instantPickup) {
            Surface(color = ProAmberSoft, shape = RoundedCornerShape(8.dp)) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.ElectricBolt,
                  contentDescription = "Instant pickup",
                  modifier = Modifier.size(13.dp),
                  tint = ProAmber,
                )
                Text(
                  text = "INSTANT",
                  style = MaterialTheme.typography.labelSmall,
                  color = ProAmber,
                )
              }
            }
          }
        }

        Surface(
          color =
            if (listing.status == Listing.STATUS_AVAILABLE) {
              EscrowSageSoft
            } else {
              MaterialTheme.colorScheme.surfaceVariant
            },
          shape = RoundedCornerShape(8.dp),
        ) {
          Text(
            text = statusLabel,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color =
              if (listing.status == Listing.STATUS_AVAILABLE) {
                EscrowSage
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ListingThumbnail(
          photoUri = listing.photoUri,
          title = listing.title,
          category = listing.category,
          isJob = isJob,
          size = 64.dp,
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = listing.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = listing.category,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (listing.description.isNotBlank()) {
            Text(
              text = listing.description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = if (isOwnListing) "Posted by you" else listing.ownerName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = "Trust score",
            modifier = Modifier.size(14.dp),
            tint = ProAmber,
          )
          Text(
            text = String.format(Locale.US, "%.1f", listing.ownerTrustScore),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "· ${String.format(Locale.US, "%.1f", listing.distanceMiles)} mi",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Column(horizontalAlignment = Alignment.End) {
          Text(
            text =
              if (isJob) {
                "${formatCents(listing.rateCents)} payout"
              } else {
                "${formatCents(listing.rateCents)}/use"
              },
            style = MonoFigureStyle,
            color = MaterialTheme.colorScheme.primary,
          )
          if (!isJob && listing.depositCents > 0L) {
            Text(
              text = "${formatCents(listing.depositCents)} deposit",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
fun EscrowBreakdownCard(
  breakdown: EscrowFeeBreakdown,
  isMicroJob: Boolean,
  modifier: Modifier = Modifier,
) {
  CraftCard(
    modifier = modifier.fillMaxWidth(),
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.Lock,
          contentDescription = "Micro-escrow breakdown",
          tint = EscrowSage,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = "Micro-Escrow Ledger Breakdown",
          style = MaterialTheme.typography.titleMedium,
        )
      }

      LedgerRow(
        label = if (isMicroJob) "Job Payout Rate" else "Tool Usage Fee",
        value = formatCents(breakdown.grossAmountCents),
      )
      if (!isMicroJob && breakdown.depositCents > 0L) {
        LedgerRow(
          label = "Refundable Return Deposit",
          value = formatCents(breakdown.depositCents),
        )
      }
      LedgerRow(
        label = "BlockBench Escrow Fee (5%)",
        value = formatCents(breakdown.platformFeeCents),
        valueColor = MaterialTheme.colorScheme.primary,
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outline)
      LedgerRow(
        label = "Net Provider Payout",
        value = formatCents(breakdown.providerNetCents),
        bold = true,
        valueColor = EscrowSage,
      )
      if (!isMicroJob && breakdown.depositCents > 0L) {
        LedgerRow(
          label = "Total Escrow Hold",
          value = formatCents(breakdown.totalEscrowHoldCents),
          bold = true,
        )
      }
    }
  }
}

@Composable
fun LedgerRow(
  label: String,
  value: String,
  bold: Boolean = false,
  valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style =
        if (bold) {
          MaterialTheme.typography.labelLarge
        } else {
          MaterialTheme.typography.bodyMedium
        },
      color =
        if (bold) {
          MaterialTheme.colorScheme.onSurface
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
    Text(
      text = value,
      style = MonoFigureStyle.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium),
      color = valueColor,
    )
  }
}

@Composable
fun ListingThumbnail(
  photoUri: String,
  title: String,
  category: String,
  isJob: Boolean,
  size: Dp = 64.dp,
  modifier: Modifier = Modifier,
) {
  var loadFailed by remember(photoUri) { mutableStateOf(false) }

  Box(
    modifier =
      modifier
        .size(size)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    if (photoUri.isNotBlank() && !loadFailed) {
      AsyncImage(
        model = photoUri,
        contentDescription = title,
        onError = { loadFailed = true },
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
    } else {
      Icon(
        imageVector = if (isJob) Icons.Filled.Handyman else Icons.Filled.Build,
        contentDescription = category,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.size(size * 0.44f),
      )
    }
  }
}

@Composable
fun NeighborTrustCardView(
  profile: UserProfile,
  circleName: String,
  inviteCode: String,
  onShareTrustCard: () -> Unit,
  onOneTapInvite: () -> Unit,
  onCopyInviteCode: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val onTimeRate =
    if (profile.completedHandoffs > 0L) {
      ((profile.onTimeReturns.toDouble() / profile.completedHandoffs.toDouble()) * 100).toInt()
    } else {
      100
    }

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = DeepPine),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier =
              Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.VerifiedUser,
              contentDescription = "Verified neighbor",
              tint = ProAmberSoft,
              modifier = Modifier.size(26.dp),
            )
          }
          Column {
            Text(
              text = "NEIGHBOR TRUST CARD",
              style = MaterialTheme.typography.labelSmall,
              color = ProAmberSoft,
            )
            Text(
              text = profile.displayName,
              style = MaterialTheme.typography.titleLarge,
              color = Color.White,
            )
            Text(
              text = circleName.ifBlank { profile.neighborhoodLabel },
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.78f),
            )
          }
        }

        Surface(
          color = Color.White.copy(alpha = 0.12f),
          shape = RoundedCornerShape(10.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Star,
              contentDescription = "Rating",
              tint = ProAmberSoft,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = String.format(Locale.US, "%.1f", profile.trustScore),
              style = MonoFigureStyle,
              color = Color.White,
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        TrustMetricBox(
          label = "HANDOFFS",
          value = profile.completedHandoffs.toString(),
          modifier = Modifier.weight(1f),
        )
        TrustMetricBox(
          label = "ON-TIME",
          value = "$onTimeRate%",
          modifier = Modifier.weight(1f),
        )
        TrustMetricBox(
          label = "BLOCK CODE",
          value = inviteCode.ifBlank { "NONE" },
          modifier = Modifier.weight(1f),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = onOneTapInvite,
          enabled = inviteCode.isNotBlank(),
          modifier = Modifier.weight(1f).testTag("one_tap_invite_button"),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = Color.White,
            ),
        ) {
          Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("1-Tap Invite", maxLines = 1)
        }

        OutlinedButton(
          onClick = onShareTrustCard,
          modifier = Modifier.weight(1f).testTag("share_trust_card_button"),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
          Text("Share Card", maxLines = 1)
        }
      }

      if (inviteCode.isNotBlank()) {
        Surface(
          onClick = onCopyInviteCode,
          color = Color.White.copy(alpha = 0.08f),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().testTag("trust_copy_code_row"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Block Invite Code (tap to copy)",
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.8f),
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = inviteCode,
                style = MonoCodeStyle,
                color = ProAmberSoft,
              )
              Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Copy invite code",
                tint = ProAmberSoft,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TrustMetricBox(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    color = Color.White.copy(alpha = 0.09f),
    shape = RoundedCornerShape(12.dp),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.72f),
      )
      Text(
        text = value,
        style = MonoFigureStyle,
        color = Color.White,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockSwitcherSheet(
  currentUserId: String,
  activeCircle: BlockCircle,
  circles: List<BlockCircle>,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSelectCircle: (BlockCircle) -> Unit,
  onCreateCircle: (name: String, radiusMiles: Double) -> Unit,
  onJoinCircle: (inviteCode: String) -> Unit,
  onLeaveCircle: (BlockCircle) -> Unit,
  onCopyCode: (String) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var actionMode by remember { mutableIntStateOf(0) } // 0 = My Blocks, 1 = Join, 2 = Start
  var joinCodeInput by remember { mutableStateOf("") }
  var newBlockName by remember { mutableStateOf("") }
  var newRadiusMiles by remember { mutableDoubleStateOf(1.5) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .imePadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = "Your Neighborhood Blocks",
        style = MaterialTheme.typography.headlineSmall,
      )

      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
          selected = actionMode == 0,
          onClick = { actionMode = 0 },
          shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
        ) {
          Text("My Blocks (${circles.size})", maxLines = 1)
        }
        SegmentedButton(
          selected = actionMode == 1,
          onClick = { actionMode = 1 },
          shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
        ) {
          Text("Join Code", maxLines = 1)
        }
        SegmentedButton(
          selected = actionMode == 2,
          onClick = { actionMode = 2 },
          shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
        ) {
          Text("New Block", maxLines = 1)
        }
      }

      when (actionMode) {
        0 -> {
          circles.forEach { circle ->
            val isActive = circle.id == activeCircle.id
            val isCreator = circle.creatorUid == currentUserId
            CraftCard(
              onClick = {
                if (!isActive) {
                  onSelectCircle(circle)
                }
              },
              containerColor =
                if (isActive) {
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                  MaterialTheme.colorScheme.surface
                },
              modifier = Modifier.fillMaxWidth().testTag("block_option_${circle.id}"),
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(
                      text = circle.name,
                      style = MaterialTheme.typography.titleMedium,
                    )
                    if (isActive) {
                      Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Active block",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                      )
                    }
                  }
                  Text(
                    text =
                      "${circle.memberUids.size} neighbors · ${String.format(Locale.US, "%.1f", circle.radiusMiles)} mi · Code ${circle.inviteCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(
                    onClick = { onCopyCode(circle.inviteCode) },
                    modifier = Modifier.size(48.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.ContentCopy,
                      contentDescription = "Copy block invite code",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp),
                    )
                  }
                  if (!isCreator) {
                    IconButton(
                      onClick = { onLeaveCircle(circle) },
                      enabled = !isBusy,
                      modifier = Modifier.size(48.dp).testTag("leave_block_${circle.id}"),
                    ) {
                      Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Leave block",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                      )
                    }
                  }
                }
              }
            }
          }
        }
        1 -> {
          OutlinedTextField(
            value = joinCodeInput,
            onValueChange = { joinCodeInput = it.uppercase(Locale.US).take(12) },
            label = { Text("Block Invite Code") },
            placeholder = { Text("e.g. OAK482") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("switcher_join_code_input"),
          )
          Button(
            onClick = { onJoinCircle(joinCodeInput) },
            enabled = !isBusy && joinCodeInput.trim().length >= 4,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("switcher_join_submit"),
          ) {
            Text("Join & Switch Block")
          }
        }
        2 -> {
          OutlinedTextField(
            value = newBlockName,
            onValueChange = { newBlockName = it.take(60) },
            label = { Text("Block or Street Name") },
            placeholder = { Text("e.g. Maple Court Bench") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("switcher_create_name_input"),
          )
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("Block Radius", style = MaterialTheme.typography.labelLarge)
              Text(
                text = String.format(Locale.US, "%.1f mi", newRadiusMiles),
                style = MonoFigureStyle,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Slider(
              value = newRadiusMiles.toFloat(),
              onValueChange = { newRadiusMiles = it.toDouble() },
              valueRange = 0.5f..5.0f,
              steps = 8,
            )
          }
          Button(
            onClick = { onCreateCircle(newBlockName, newRadiusMiles) },
            enabled = !isBusy && newBlockName.trim().length >= 2,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("switcher_create_submit"),
          ) {
            Icon(
              imageVector = Icons.Filled.Add,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Create & Switch Block")
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}
