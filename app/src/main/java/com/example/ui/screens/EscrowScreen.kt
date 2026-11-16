package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.Booking
import com.example.model.EscrowFeeBreakdown
import com.example.model.EscrowWorkflow
import com.example.model.UiState
import com.example.model.dataOrNull
import com.example.ui.components.CraftBadge
import com.example.ui.components.CraftCard
import com.example.ui.components.LedgerRow
import com.example.ui.components.formatCents
import com.example.ui.theme.MonoFigureStyle
import com.example.ui.theme.blockBenchColors

private enum class EscrowRoleFilter(val label: String) {
  ALL("All"),
  PROVIDING("Earning"),
  REQUESTING("Borrowing / Hiring"),
}

private enum class EscrowTab {
  ACTIVE,
  SETTLED,
}

/**
 * The user's escrow ledger. Each booking offers only the steps the signed-in neighbour may take
 * (see [EscrowWorkflow]): the listing owner approves a request and confirms completion, and the
 * other side sees what it is waiting for instead of buttons the security rules would refuse.
 */
@Composable
fun EscrowScreen(
  currentUserId: String,
  ledger: UiState<List<Booking>>,
  isBusy: Boolean,
  onAdvanceStatus: (booking: Booking, nextStatus: String, notes: String) -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val brand = blockBenchColors
  val bookings = ledger.dataOrNull().orEmpty()

  var tab by rememberSaveable { mutableStateOf(EscrowTab.ACTIVE) }
  var roleFilter by rememberSaveable { mutableStateOf(EscrowRoleFilter.ALL) }
  // Dialogs refer to bookings by id and read the live booking, never a stale copy.
  var cancelBookingId by rememberSaveable { mutableStateOf<String?>(null) }
  var noteDialog by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }

  val roleFiltered =
    remember(bookings, roleFilter, currentUserId) {
      when (roleFilter) {
        EscrowRoleFilter.ALL -> bookings
        EscrowRoleFilter.PROVIDING -> bookings.filter { it.providerUid == currentUserId }
        EscrowRoleFilter.REQUESTING -> bookings.filter { it.requesterUid == currentUserId }
      }
    }
  val activeBookings = remember(roleFiltered) { roleFiltered.filter { it.isActive } }
  val settledBookings = remember(roleFiltered) { roleFiltered.filterNot { it.isActive } }
  val displayed = if (tab == EscrowTab.ACTIVE) activeBookings else settledBookings

  val heldCents =
    remember(bookings) {
      bookings.filter { it.isActive }.sumOf { it.grossAmountCents + it.depositCents }
    }
  val earnedCents =
    remember(bookings, currentUserId) {
      bookings
        .filter { it.status == Booking.STATUS_COMPLETED && it.providerUid == currentUserId }
        .sumOf { it.providerNetCents }
    }

  Column(modifier = modifier.fillMaxSize()) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(text = "Micro-Escrow Ledger", style = MaterialTheme.typography.headlineSmall)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          CraftCard(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.weight(1f),
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "ACTIVE ESCROW HOLD",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = formatCents(heldCents),
                style = MonoFigureStyle,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
          CraftCard(
            containerColor = brand.escrowContainer,
            modifier = Modifier.weight(1f),
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "RELEASED PAYOUTS",
                style = MaterialTheme.typography.labelSmall,
                color = brand.onEscrowContainer,
              )
              Text(
                text = formatCents(earnedCents),
                style = MonoFigureStyle,
                color = brand.onEscrowContainer,
              )
            }
          }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          SegmentedButton(
            selected = tab == EscrowTab.ACTIVE,
            onClick = { tab = EscrowTab.ACTIVE },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.testTag("escrow_tab_active"),
          ) {
            Text("Active (${activeBookings.size})")
          }
          SegmentedButton(
            selected = tab == EscrowTab.SETTLED,
            onClick = { tab = EscrowTab.SETTLED },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.testTag("escrow_tab_settled"),
          ) {
            Text("Settled (${settledBookings.size})")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          EscrowRoleFilter.entries.forEach { option ->
            FilterChip(
              selected = roleFilter == option,
              onClick = { roleFilter = option },
              label = { Text(option.label) },
              modifier = Modifier.testTag("escrow_role_${option.name.lowercase()}"),
            )
          }
        }
      }
    }

    val contentModifier = Modifier.weight(1f).fillMaxWidth()
    when {
      ledger is UiState.Loading ->
        Box(modifier = contentModifier, contentAlignment = Alignment.Center) {
          CircularProgressIndicator(modifier = Modifier.testTag("escrow_loading"))
        }
      ledger is UiState.Error ->
        Box(modifier = contentModifier, contentAlignment = Alignment.Center) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.CloudOff,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(36.dp),
            )
            Text(
              text = ledger.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry, modifier = Modifier.testTag("escrow_retry_button")) {
              Text(stringResource(id = R.string.retry_sync))
            }
          }
        }
      displayed.isEmpty() ->
        Box(modifier = contentModifier, contentAlignment = Alignment.Center) {
          Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(40.dp),
            )
            Text(
              text =
                if (tab == EscrowTab.ACTIVE) "No active escrow holds" else "No settled handoffs yet",
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text =
                "Book a neighbor's tool or claim a micro-job from the Block Feed to track escrow " +
                  "holds and ${EscrowFeeBreakdown.PLATFORM_FEE_PERCENT}% platform fee splits here.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
          }
        }
      else ->
        LazyColumn(
          contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = contentModifier,
        ) {
          items(displayed, key = { it.id }) { booking ->
            BookingEscrowCard(
              booking = booking,
              currentUserId = currentUserId,
              isBusy = isBusy,
              onAdvance = { next ->
                if (next == Booking.STATUS_ESCROW_HELD || next == Booking.STATUS_IN_PROGRESS) {
                  noteDialog = booking.id to next
                } else {
                  onAdvanceStatus(booking, next, booking.notes)
                }
              },
              onRequestCancel = { cancelBookingId = booking.id },
            )
          }
        }
    }
  }

  bookings
    .firstOrNull { it.id == cancelBookingId }
    ?.let { booking ->
      val isOwner = currentUserId == booking.listingOwnerUid
      val title =
        when {
          booking.status == Booking.STATUS_ESCROW_HELD -> "Cancel and refund the escrow hold?"
          isOwner -> "Decline this request?"
          else -> "Cancel your request?"
        }
      AlertDialog(
        onDismissRequest = { cancelBookingId = null },
        title = { Text(title) },
        text = {
          Text(
            "\"${booking.listingTitle}\" goes back on the block feed and nothing is charged. " +
              "This cannot be undone."
          )
        },
        confirmButton = {
          Button(
            onClick = {
              onAdvanceStatus(booking, Booking.STATUS_CANCELLED, booking.notes)
              cancelBookingId = null
            },
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag("confirm_cancel_booking_button"),
          ) {
            Text(
              when {
                booking.status == Booking.STATUS_ESCROW_HELD -> "Cancel & Refund"
                isOwner -> "Decline"
                else -> "Cancel Request"
              }
            )
          }
        },
        dismissButton = { TextButton(onClick = { cancelBookingId = null }) { Text("Keep It") } },
      )
    }

  val noteTarget =
    noteDialog?.let { (bookingId, nextStatus) ->
      bookings.firstOrNull { it.id == bookingId }?.let { it to nextStatus }
    }
  if (noteTarget != null) {
    val (booking, nextStatus) = noteTarget
    var noteInput by rememberSaveable(booking.id, nextStatus) { mutableStateOf(booking.notes) }
    val isLocking = nextStatus == Booking.STATUS_ESCROW_HELD
    AlertDialog(
      onDismissRequest = { noteDialog = null },
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Key,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(if (isLocking) "Approve & Lock Escrow" else "Start Handoff Check-In")
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text =
              if (isLocking) {
                "Optionally add a porch locker PIN or pickup window before locking funds in micro-escrow."
              } else {
                "Confirm the tool/job handoff and update any check-in notes."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(
            value = noteInput,
            onValueChange = { noteInput = it.take(Booking.MAX_NOTES_LENGTH) },
            label = { Text("Porch PIN / Handoff Note") },
            placeholder = { Text("e.g. Side gate code 4812") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("handoff_note_input"),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onAdvanceStatus(booking, nextStatus, noteInput)
            noteDialog = null
          },
          enabled = !isBusy,
          modifier = Modifier.testTag("confirm_handoff_dialog_button"),
        ) {
          Text(if (isLocking) "Lock Escrow" else "Start Handoff")
        }
      },
      dismissButton = { TextButton(onClick = { noteDialog = null }) { Text("Back") } },
    )
  }
}

@Composable
private fun BookingEscrowCard(
  booking: Booking,
  currentUserId: String,
  isBusy: Boolean,
  onAdvance: (nextStatus: String) -> Unit,
  onRequestCancel: () -> Unit,
) {
  val brand = blockBenchColors
  val isProvider = booking.providerUid == currentUserId
  val allowed = EscrowWorkflow.allowedNextStatuses(booking, currentUserId)
  val ownerName = booking.listingOwnerName.ifBlank { "the listing owner" }

  val stepProgress =
    when (booking.status) {
      Booking.STATUS_REQUESTED -> 0.25f
      Booking.STATUS_ESCROW_HELD -> 0.50f
      Booking.STATUS_IN_PROGRESS -> 0.75f
      Booking.STATUS_COMPLETED -> 1.0f
      else -> 0.0f
    }
  val statusBadgeColor =
    when (booking.status) {
      Booking.STATUS_COMPLETED -> brand.escrowContainer
      Booking.STATUS_CANCELLED -> MaterialTheme.colorScheme.errorContainer
      Booking.STATUS_ESCROW_HELD,
      Booking.STATUS_IN_PROGRESS -> brand.proContainer
      else -> MaterialTheme.colorScheme.primaryContainer
    }
  val statusTextColor =
    when (booking.status) {
      Booking.STATUS_COMPLETED -> brand.onEscrowContainer
      Booking.STATUS_CANCELLED -> MaterialTheme.colorScheme.onErrorContainer
      Booking.STATUS_ESCROW_HELD,
      Booking.STATUS_IN_PROGRESS -> brand.onProContainer
      else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

  CraftCard(modifier = Modifier.fillMaxWidth().testTag("booking_card_${booking.id}")) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CraftBadge(
          text =
            when {
              isProvider -> "YOU EARN"
              booking.isMicroJob -> "YOU HIRE"
              else -> "YOU BORROW"
            },
          containerColor =
            if (isProvider) brand.escrowContainer else MaterialTheme.colorScheme.secondaryContainer,
          contentColor =
            if (isProvider) {
              brand.onEscrowContainer
            } else {
              MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
        CraftBadge(
          text = booking.status.replace("_", " ").uppercase(),
          containerColor = statusBadgeColor,
          contentColor = statusTextColor,
        )
      }

      Column {
        Text(text = booking.listingTitle, style = MaterialTheme.typography.titleMedium)
        Text(
          text =
            if (isProvider) {
              "With ${booking.requesterName} · Net payout ${formatCents(booking.providerNetCents)}"
            } else {
              "With ${booking.providerName} · ${if (booking.isMicroJob) "Micro-Job" else "Tool Rental"}"
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (booking.status != Booking.STATUS_CANCELLED) {
        LinearProgressIndicator(
          progress = { stepProgress },
          modifier = Modifier.fillMaxWidth().height(6.dp),
          color =
            if (booking.status == Booking.STATUS_COMPLETED) {
              brand.escrow
            } else {
              MaterialTheme.colorScheme.primary
            },
          trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
      }

      if (booking.notes.isNotBlank()) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Handoff Note: ${booking.notes}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      LedgerRow("Gross Rate", formatCents(booking.grossAmountCents))
      if (booking.depositCents > 0L) {
        LedgerRow("Refundable Deposit", formatCents(booking.depositCents))
      }
      LedgerRow(
        "${EscrowFeeBreakdown.PLATFORM_FEE_PERCENT}% Micro-Escrow Fee",
        formatCents(booking.platformFeeCents),
      )
      LedgerRow(
        label = "Net Provider Payout",
        value = formatCents(booking.providerNetCents),
        bold = true,
        valueColor = brand.escrow,
      )

      when (booking.status) {
        Booking.STATUS_REQUESTED ->
          if (Booking.STATUS_ESCROW_HELD in allowed) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedButton(
                onClick = onRequestCancel,
                enabled = !isBusy,
                modifier = Modifier.weight(1f).testTag("cancel_booking_${booking.id}"),
              ) {
                Text("Decline", maxLines = 1)
              }
              Button(
                onClick = { onAdvance(Booking.STATUS_ESCROW_HELD) },
                enabled = !isBusy,
                modifier = Modifier.weight(1.4f).testTag("lock_escrow_${booking.id}"),
              ) {
                Text("Approve & Lock", maxLines = 1)
              }
            }
          } else {
            WaitingNote(
              text = "Waiting for $ownerName to approve your request.",
              modifier = Modifier.testTag("booking_waiting_${booking.id}"),
            )
            OutlinedButton(
              onClick = onRequestCancel,
              enabled = !isBusy,
              modifier = Modifier.fillMaxWidth().testTag("cancel_booking_${booking.id}"),
            ) {
              Text("Cancel Request")
            }
          }
        Booking.STATUS_ESCROW_HELD ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = onRequestCancel,
              enabled = !isBusy,
              modifier = Modifier.weight(1f).testTag("refund_booking_${booking.id}"),
            ) {
              Text("Refund", maxLines = 1)
            }
            Button(
              onClick = { onAdvance(Booking.STATUS_IN_PROGRESS) },
              enabled = !isBusy,
              modifier = Modifier.weight(1.4f).testTag("start_handoff_${booking.id}"),
            ) {
              Text("Start Handoff", maxLines = 1)
            }
          }
        Booking.STATUS_IN_PROGRESS ->
          if (Booking.STATUS_COMPLETED in allowed) {
            Button(
              onClick = { onAdvance(Booking.STATUS_COMPLETED) },
              enabled = !isBusy,
              modifier = Modifier.fillMaxWidth().testTag("complete_escrow_${booking.id}"),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = brand.escrow,
                  contentColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
              Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                if (booking.isMicroJob) {
                  "Confirm Job Done & Release Payout"
                } else {
                  "Confirm Return & Release Payout"
                }
              )
            }
          } else {
            WaitingNote(
              text =
                if (booking.isMicroJob) {
                  "$ownerName confirms the finished job, which releases your payout."
                } else {
                  "Return the tool to $ownerName. They confirm the return, which releases your deposit."
                },
              modifier = Modifier.testTag("booking_waiting_${booking.id}"),
            )
          }
      }
    }
  }
}

@Composable
private fun WaitingNote(text: String, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      imageVector = Icons.Filled.HourglassTop,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(16.dp),
    )
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
