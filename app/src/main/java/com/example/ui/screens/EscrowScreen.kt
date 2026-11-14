package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.Booking
import com.example.model.Listing
import com.example.ui.components.CraftCard
import com.example.ui.components.LedgerRow
import com.example.ui.components.formatCents
import com.example.ui.theme.EscrowSage
import com.example.ui.theme.EscrowSageSoft
import com.example.ui.theme.MonoFigureStyle
import com.example.ui.theme.ProAmber
import com.example.ui.theme.ProAmberSoft

private enum class EscrowRoleFilter(val label: String) {
  ALL("All"),
  PROVIDING("Earning"),
  REQUESTING("Borrowing / Hiring"),
}

@Composable
fun EscrowScreen(
  currentUserId: String,
  bookings: List<Booking>,
  isBusy: Boolean,
  onAdvanceStatus: (Booking, String, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var tabIdx by remember { mutableIntStateOf(0) } // 0 = Active, 1 = Settled
  var roleFilter by remember { mutableStateOf(EscrowRoleFilter.ALL) }
  var pendingCancelBooking by remember { mutableStateOf<Booking?>(null) }
  var pendingNoteDialog by remember { mutableStateOf<Pair<Booking, String>?>(null) }

  val roleFiltered =
    remember(bookings, roleFilter, currentUserId) {
      when (roleFilter) {
        EscrowRoleFilter.ALL -> bookings
        EscrowRoleFilter.PROVIDING -> bookings.filter { it.providerUid == currentUserId }
        EscrowRoleFilter.REQUESTING -> bookings.filter { it.requesterUid == currentUserId }
      }
    }

  val activeBookings =
    remember(roleFiltered) {
      roleFiltered.filter {
        it.status != Booking.STATUS_COMPLETED && it.status != Booking.STATUS_CANCELLED
      }
    }
  val settledBookings =
    remember(roleFiltered) {
      roleFiltered.filter {
        it.status == Booking.STATUS_COMPLETED || it.status == Booking.STATUS_CANCELLED
      }
    }
  val displayed = if (tabIdx == 0) activeBookings else settledBookings

  val heldCents =
    bookings
      .filter { it.status != Booking.STATUS_COMPLETED && it.status != Booking.STATUS_CANCELLED }
      .sumOf { it.grossAmountCents + it.depositCents }
  val earnedCents =
    bookings
      .filter { it.status == Booking.STATUS_COMPLETED && it.providerUid == currentUserId }
      .sumOf { it.providerNetCents }

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
            containerColor = EscrowSageSoft,
            modifier = Modifier.weight(1f),
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "RELEASED PAYOUTS",
                style = MaterialTheme.typography.labelSmall,
                color = EscrowSage,
              )
              Text(
                text = formatCents(earnedCents),
                style = MonoFigureStyle,
                color = EscrowSage,
              )
            }
          }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          SegmentedButton(
            selected = tabIdx == 0,
            onClick = { tabIdx = 0 },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.testTag("escrow_tab_active"),
          ) {
            Text("Active (${activeBookings.size})")
          }
          SegmentedButton(
            selected = tabIdx == 1,
            onClick = { tabIdx = 1 },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.testTag("escrow_tab_settled"),
          ) {
            Text("Settled (${settledBookings.size})")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
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

    if (displayed.isEmpty()) {
      Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
          modifier = Modifier.padding(24.dp),
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
            text = if (tabIdx == 0) "No active escrow holds" else "No settled handoffs yet",
            style = MaterialTheme.typography.titleLarge,
          )
          Text(
            text =
              "Book a neighbor's tool or claim a micro-job from the Block Feed to track escrow holds and 5% platform fee splits here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.weight(1f).fillMaxWidth(),
      ) {
        items(displayed, key = { it.id }) { booking ->
          BookingEscrowCard(
            booking = booking,
            isProvider = booking.providerUid == currentUserId,
            isBusy = isBusy,
            onAdvanceWithNote = { next ->
              if (next == Booking.STATUS_ESCROW_HELD || next == Booking.STATUS_IN_PROGRESS) {
                pendingNoteDialog = booking to next
              } else {
                onAdvanceStatus(booking, next, booking.notes)
              }
            },
            onRequestCancel = { pendingCancelBooking = booking },
          )
        }
      }
    }
  }

  pendingCancelBooking?.let { booking ->
    val isRefund = booking.status == Booking.STATUS_ESCROW_HELD
    AlertDialog(
      onDismissRequest = { pendingCancelBooking = null },
      title = { Text(if (isRefund) "Refund Escrow Hold?" else "Cancel Request?") },
      text = {
        Text(
          "This will release \"${booking.listingTitle}\" back to your neighborhood block feed."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onAdvanceStatus(booking, Booking.STATUS_CANCELLED, booking.notes)
            pendingCancelBooking = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text(if (isRefund) "Confirm Refund" else "Cancel Hold")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingCancelBooking = null }) { Text("Keep Hold") }
      },
    )
  }

  pendingNoteDialog?.let { (booking, nextStatus) ->
    var noteInput by remember(booking.id, nextStatus) { mutableStateOf(booking.notes) }
    val isLocking = nextStatus == Booking.STATUS_ESCROW_HELD
    AlertDialog(
      onDismissRequest = { pendingNoteDialog = null },
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
          Text(if (isLocking) "Lock Escrow & Porch Note" else "Start Handoff Check-In")
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
            onValueChange = { noteInput = it.take(250) },
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
            pendingNoteDialog = null
          },
          enabled = !isBusy,
          modifier = Modifier.testTag("confirm_handoff_dialog_button"),
        ) {
          Text(if (isLocking) "Lock Escrow" else "Start Handoff")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingNoteDialog = null }) { Text("Back") }
      },
    )
  }
}

@Composable
private fun BookingEscrowCard(
  booking: Booking,
  isProvider: Boolean,
  isBusy: Boolean,
  onAdvanceWithNote: (String) -> Unit,
  onRequestCancel: () -> Unit,
) {
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
      Booking.STATUS_COMPLETED -> EscrowSageSoft
      Booking.STATUS_CANCELLED -> MaterialTheme.colorScheme.errorContainer
      Booking.STATUS_ESCROW_HELD, Booking.STATUS_IN_PROGRESS -> ProAmberSoft
      else -> MaterialTheme.colorScheme.primaryContainer
    }
  val statusTextColor =
    when (booking.status) {
      Booking.STATUS_COMPLETED -> EscrowSage
      Booking.STATUS_CANCELLED -> MaterialTheme.colorScheme.error
      Booking.STATUS_ESCROW_HELD, Booking.STATUS_IN_PROGRESS -> ProAmber
      else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

  CraftCard(modifier = Modifier.fillMaxWidth().testTag("booking_card_${booking.id}")) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          color = if (isProvider) EscrowSageSoft else MaterialTheme.colorScheme.secondaryContainer,
          shape = RoundedCornerShape(6.dp),
        ) {
          Text(
            text = if (isProvider) "YOU EARN" else "YOU REQUESTED",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (isProvider) EscrowSage else MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
        Surface(color = statusBadgeColor, shape = RoundedCornerShape(8.dp)) {
          Text(
            text = booking.status.replace("_", " ").uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = statusTextColor,
          )
        }
      }

      Column {
        Text(text = booking.listingTitle, style = MaterialTheme.typography.titleMedium)
        Text(
          text =
            if (isProvider) {
              "With ${booking.requesterName} · Net payout ${formatCents(booking.providerNetCents)}"
            } else {
              "With ${booking.providerName} · ${if (booking.listingKind == Listing.KIND_TOOL) "Tool Rental" else "Micro-Job"}"
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
              EscrowSage
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

      HorizontalDivider(color = MaterialTheme.colorScheme.outline)

      LedgerRow("Gross Rate", formatCents(booking.grossAmountCents))
      if (booking.depositCents > 0L) {
        LedgerRow("Refundable Deposit", formatCents(booking.depositCents))
      }
      LedgerRow("5% Micro-Escrow Fee", formatCents(booking.platformFeeCents))
      LedgerRow(
        label = "Net Provider Payout",
        value = formatCents(booking.providerNetCents),
        bold = true,
        valueColor = EscrowSage,
      )

      when (booking.status) {
        Booking.STATUS_REQUESTED -> {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = onRequestCancel,
              enabled = !isBusy,
              modifier = Modifier.weight(1f).testTag("cancel_booking_${booking.id}"),
            ) {
              Text("Cancel")
            }
            Button(
              onClick = { onAdvanceWithNote(Booking.STATUS_ESCROW_HELD) },
              enabled = !isBusy,
              modifier = Modifier.weight(1f).testTag("lock_escrow_${booking.id}"),
            ) {
              Text("Lock Escrow")
            }
          }
        }
        Booking.STATUS_ESCROW_HELD -> {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = onRequestCancel,
              enabled = !isBusy,
              modifier = Modifier.weight(1f),
            ) {
              Text("Refund")
            }
            Button(
              onClick = { onAdvanceWithNote(Booking.STATUS_IN_PROGRESS) },
              enabled = !isBusy,
              modifier = Modifier.weight(1f).testTag("start_handoff_${booking.id}"),
            ) {
              Text("Start Handoff")
            }
          }
        }
        Booking.STATUS_IN_PROGRESS -> {
          Button(
            onClick = { onAdvanceWithNote(Booking.STATUS_COMPLETED) },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth().testTag("complete_escrow_${booking.id}"),
            colors = ButtonDefaults.buttonColors(containerColor = EscrowSage),
          ) {
            Icon(
              imageVector = Icons.Filled.CheckCircle,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Confirm Return & Release Payout")
          }
        }
      }
    }
  }
}
