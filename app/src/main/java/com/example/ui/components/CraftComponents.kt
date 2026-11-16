package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material3.AlertDialog
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
import com.example.data.repository.BlockBenchRepository
import com.example.model.BlockCircle
import com.example.model.BookingTotals
import com.example.model.EscrowFeeBreakdown
import com.example.model.InviteCodes
import com.example.model.Listing
import com.example.model.UserProfile
import com.example.ui.theme.DeepPine
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.MonoFigureStyle
import com.example.ui.theme.ProAmberSoft
import com.example.ui.theme.blockBenchColors
import java.util.Locale
import kotlin.math.roundToInt

fun formatCents(cents: Long): String = String.format(Locale.US, "$%,.2f", cents / 100.0)

fun formatMiles(miles: Double): String = String.format(Locale.US, "%.1f mi", miles)

fun neighborsLabel(count: Int): String = if (count == 1) "1 neighbor" else "$count neighbors"

/**
 * One-line summary of a block. The invite code comes before the radius so that, where the line
 * has to be cut short, the code (which people read off the screen) is what survives.
 */
fun circleSummary(circle: BlockCircle): String =
  "${neighborsLabel(circle.memberUids.size)} · Code ${circle.inviteCode} · ${formatMiles(circle.radiusMiles)}"

/** Snaps a slider position to one decimal, so what is stored is what the label shows. */
fun Float.toTenthsOfAMile(): Double = (this * 10f).roundToInt() / 10.0

fun listingStatusLabel(listing: Listing): String =
  when (listing.status) {
    Listing.STATUS_RESERVED -> "Reserved"
    Listing.STATUS_PAUSED -> "Paused"
    else -> if (listing.isMicroJob) "Open Job" else "Available"
  }

fun copyInviteCodeToClipboard(
  context: Context,
  inviteCode: String,
  onCopied: (String) -> Unit,
) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
  if (clipboard == null) {
    onCopied("Could not reach the clipboard. Your invite code is $inviteCode")
    return
  }
  clipboard.setPrimaryClip(ClipData.newPlainText("BlockBench Invite Code", inviteCode))
  // Android 13+ shows its own confirmation when something is copied; a second one is noise.
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
    onCopied("Copied block invite code $inviteCode")
  }
}

fun shareNeighborhoodInvite(context: Context, circleName: String, inviteCode: String) {
  val message =
    "Join our \"$circleName\" tool-share & micro-services block on BlockBench! " +
      "Enter invite code $inviteCode to borrow nearby tools or claim quick neighborhood jobs."
  shareText(context, message, "Invite Neighbor to $circleName")
}

fun shareTrustCard(
  context: Context,
  profile: UserProfile,
  totals: BookingTotals,
  circleName: String,
  inviteCode: String,
) {
  val message =
    "BlockBench Neighbor Trust Card — ${profile.displayName}\n" +
      "Block: ${circleName.ifBlank { profile.neighborhoodLabel }}\n" +
      "Trust Score: ${String.format(Locale.US, "%.1f", profile.trustScore)} / 5.0 · " +
      "${totals.completedHandoffs} verified handoffs · " +
      "${onTimePercent(profile)}% on-time returns\n" +
      if (inviteCode.isNotBlank()) {
        "Join our block with code: $inviteCode"
      } else {
        "Verified neighbor on BlockBench"
      }
  shareText(context, message, "Share Neighbor Trust Card")
}

/**
 * Share of the profile's recorded handoffs that were returned on time. Returns are not timed yet,
 * so the repository counts every completed handoff as on time and this currently reads 100%.
 */
fun onTimePercent(profile: UserProfile): Int {
  val handoffs = profile.completedHandoffs
  if (handoffs <= 0L) return 100
  return (profile.onTimeReturns.coerceIn(0L, handoffs) * 100 / handoffs).toInt()
}

private fun shareText(context: Context, text: String, chooserTitle: String) {
  val sendIntent =
    Intent(Intent.ACTION_SEND).apply {
      putExtra(Intent.EXTRA_TEXT, text)
      type = "text/plain"
    }
  try {
    context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
  } catch (e: ActivityNotFoundException) {
    // No share targets at all (stripped-down devices, some emulators): nothing to open.
  }
}

@Composable
fun CraftCard(
  modifier: Modifier = Modifier,
  containerColor: Color = MaterialTheme.colorScheme.surface,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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

/** A small rounded label such as "TOOL", "INSTANT" or a status. */
@Composable
fun CraftBadge(
  text: String,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
  leadingIcon: (@Composable () -> Unit)? = null,
) {
  Surface(color = containerColor, shape = RoundedCornerShape(8.dp), modifier = modifier) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      leadingIcon?.invoke()
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor,
        maxLines = 1,
      )
    }
  }
}

@Composable
fun ListingCraftCard(
  listing: Listing,
  isOwnListing: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isJob = listing.isMicroJob
  val brand = blockBenchColors
  val kindContainer =
    if (isJob) {
      MaterialTheme.colorScheme.secondaryContainer
    } else {
      MaterialTheme.colorScheme.primaryContainer
    }
  val kindContent =
    if (isJob) {
      MaterialTheme.colorScheme.onSecondaryContainer
    } else {
      MaterialTheme.colorScheme.onPrimaryContainer
    }
  val isAvailable = listing.status == Listing.STATUS_AVAILABLE

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
          CraftBadge(
            text = if (isJob) "MICRO-JOB" else "TOOL",
            containerColor = kindContainer,
            contentColor = kindContent,
            leadingIcon = {
              Icon(
                imageVector = if (isJob) Icons.Filled.Handyman else Icons.Filled.Build,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = kindContent,
              )
            },
          )
          if (listing.instantPickup) {
            CraftBadge(
              text = "INSTANT",
              containerColor = brand.proContainer,
              contentColor = brand.onProContainer,
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.ElectricBolt,
                  contentDescription = null,
                  modifier = Modifier.size(13.dp),
                  tint = brand.onProContainer,
                )
              },
            )
          }
        }

        CraftBadge(
          text = listingStatusLabel(listing),
          containerColor =
            if (isAvailable) brand.escrowContainer else MaterialTheme.colorScheme.surfaceVariant,
          contentColor =
            if (isAvailable) {
              brand.onEscrowContainer
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ListingThumbnail(
          photoUri = listing.photoUri,
          title = listing.title,
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          // Names can be 60 characters long; let the name give way, not the price beside it.
          Text(
            text = if (isOwnListing) "Posted by you" else listing.ownerName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = "Trust score",
            modifier = Modifier.size(14.dp),
            tint = brand.pro,
          )
          Text(
            text = String.format(Locale.US, "%.1f", listing.ownerTrustScore),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
          Text(
            text = "· ${formatMiles(listing.distanceMiles)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
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
            maxLines = 1,
          )
          if (!isJob && listing.depositCents > 0L) {
            Text(
              text = "${formatCents(listing.depositCents)} deposit",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
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
  val brand = blockBenchColors
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
          contentDescription = null,
          tint = brand.escrow,
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
        label = "BlockBench Escrow Fee (${EscrowFeeBreakdown.PLATFORM_FEE_PERCENT}%)",
        value = formatCents(breakdown.platformFeeCents),
        valueColor = MaterialTheme.colorScheme.primary,
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      LedgerRow(
        label = "Net Provider Payout",
        value = formatCents(breakdown.providerNetCents),
        bold = true,
        valueColor = brand.escrow,
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
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      modifier = Modifier.weight(1f),
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
      maxLines = 1,
    )
  }
}

/** The listing's photo, or an icon for its kind when it has none. */
@Composable
fun ListingThumbnail(
  photoUri: String,
  title: String,
  isJob: Boolean,
  modifier: Modifier = Modifier,
  size: Dp = 64.dp,
) {
  val photo = rememberListingPhoto(photoUri)

  Box(
    modifier =
      modifier
        .size(size)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    if (photo != null) {
      Image(
        bitmap = photo,
        contentDescription = "Photo of $title",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
    } else {
      Icon(
        imageVector = if (isJob) Icons.Filled.Handyman else Icons.Filled.Build,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.size(size * 0.44f),
      )
    }
  }
}

/**
 * The shareable card at the top of the Trust tab. It keeps its deep-pine look in both themes, so
 * everything on it uses fixed colours chosen for that background rather than theme roles.
 */
@Composable
fun NeighborTrustCardView(
  profile: UserProfile,
  totals: BookingTotals,
  circleName: String,
  inviteCode: String,
  onShareTrustCard: () -> Unit,
  onOneTapInvite: () -> Unit,
  modifier: Modifier = Modifier,
  onCopyInviteCode: () -> Unit = {},
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = DeepPine),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f),
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
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = circleName.ifBlank { profile.neighborhoodLabel },
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.78f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
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
              contentDescription = "Trust score",
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
          value = totals.completedHandoffs.toString(),
          modifier = Modifier.weight(1f),
        )
        TrustMetricBox(
          label = "ON-TIME",
          value = "${onTimePercent(profile)}%",
          modifier = Modifier.weight(1f),
        )
        // A little wider than the others so an eight-character code fits on small phones.
        TrustMetricBox(
          label = "BLOCK CODE",
          value = inviteCode.ifBlank { "NONE" },
          modifier = Modifier.weight(1.3f),
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
              contentColor = MaterialTheme.colorScheme.onPrimary,
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Block invite code (tap to copy)",
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.8f),
            )
            Text(
              text = inviteCode,
              style = MonoCodeStyle,
              color = ProAmberSoft,
              maxLines = 1,
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
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private const val SWITCHER_TAB_BLOCKS = 0
private const val SWITCHER_TAB_JOIN = 1
private const val SWITCHER_TAB_CREATE = 2

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
  var selectedTab by remember { mutableIntStateOf(SWITCHER_TAB_BLOCKS) }
  var joinCodeInput by remember { mutableStateOf("") }
  var newBlockName by remember { mutableStateOf("") }
  var newRadiusMiles by remember {
    mutableDoubleStateOf(BlockBenchRepository.DEFAULT_RADIUS_MILES)
  }
  var circleToLeave by remember { mutableStateOf<BlockCircle?>(null) }

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
          selected = selectedTab == SWITCHER_TAB_BLOCKS,
          onClick = { selectedTab = SWITCHER_TAB_BLOCKS },
          shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
          modifier = Modifier.testTag("switcher_tab_blocks"),
        ) {
          Text("My Blocks (${circles.size})", maxLines = 1)
        }
        SegmentedButton(
          selected = selectedTab == SWITCHER_TAB_JOIN,
          onClick = { selectedTab = SWITCHER_TAB_JOIN },
          shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
          modifier = Modifier.testTag("switcher_tab_join"),
        ) {
          Text("Join Code", maxLines = 1)
        }
        SegmentedButton(
          selected = selectedTab == SWITCHER_TAB_CREATE,
          onClick = { selectedTab = SWITCHER_TAB_CREATE },
          shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
          modifier = Modifier.testTag("switcher_tab_create"),
        ) {
          Text("New Block", maxLines = 1)
        }
      }

      when (selectedTab) {
        SWITCHER_TAB_BLOCKS -> {
          circles.forEach { circle ->
            val isActive = circle.id == activeCircle.id
            val isCreator = circle.creatorUid == currentUserId
            CraftCard(
              onClick = {
                if (!isActive && !isBusy) {
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
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(
                      text = circle.name,
                      style = MaterialTheme.typography.titleMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f, fill = false),
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
                    text = circleSummary(circle),
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
                      contentDescription = "Copy invite code for ${circle.name}",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp),
                    )
                  }
                  if (!isCreator) {
                    IconButton(
                      onClick = { circleToLeave = circle },
                      enabled = !isBusy,
                      modifier = Modifier.size(48.dp).testTag("leave_block_${circle.id}"),
                    ) {
                      Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Leave ${circle.name}",
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
        SWITCHER_TAB_JOIN -> {
          OutlinedTextField(
            value = joinCodeInput,
            onValueChange = { joinCodeInput = InviteCodes.normalize(it).take(InviteCodes.MAX_LENGTH) },
            label = { Text("Block Invite Code") },
            placeholder = { Text("e.g. OAK7K2MQ") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("switcher_join_code_input"),
          )
          Button(
            onClick = { onJoinCircle(joinCodeInput) },
            enabled = !isBusy && InviteCodes.isWellFormed(joinCodeInput),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("switcher_join_submit"),
          ) {
            Text("Join & Switch Block")
          }
        }
        else -> {
          OutlinedTextField(
            value = newBlockName,
            onValueChange = { newBlockName = it.take(BlockBenchRepository.MAX_NAME_LENGTH) },
            label = { Text("Block or Street Name") },
            placeholder = { Text("e.g. Maple Court Bench") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("switcher_create_name_input"),
          )
          BlockRadiusSlider(
            radiusMiles = newRadiusMiles,
            onRadiusChange = { newRadiusMiles = it },
          )
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

  circleToLeave?.let { circle ->
    AlertDialog(
      onDismissRequest = { circleToLeave = null },
      title = { Text("Leave ${circle.name}?") },
      text = {
        Text(
          "You will lose access to this block's feed, and any tools or jobs you posted there " +
            "will be removed. You can rejoin later with the invite code."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            circleToLeave = null
            onLeaveCircle(circle)
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("confirm_leave_block_button"),
        ) {
          Text("Leave Block")
        }
      },
      dismissButton = { TextButton(onClick = { circleToLeave = null }) { Text("Stay") } },
    )
  }
}

/** The 0.5 to 5 mile, half-mile-step radius control shared by onboarding and the block switcher. */
@Composable
fun BlockRadiusSlider(
  radiusMiles: Double,
  onRadiusChange: (Double) -> Unit,
  modifier: Modifier = Modifier,
  label: String = "Block Radius",
) {
  Column(modifier = modifier) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(label, style = MaterialTheme.typography.labelLarge)
      Text(
        text = formatMiles(radiusMiles),
        style = MonoFigureStyle,
        color = MaterialTheme.colorScheme.primary,
      )
    }
    Slider(
      value = radiusMiles.toFloat(),
      onValueChange = { onRadiusChange(it.toTenthsOfAMile()) },
      valueRange = 0.5f..5.0f,
      steps = 8,
    )
  }
}
