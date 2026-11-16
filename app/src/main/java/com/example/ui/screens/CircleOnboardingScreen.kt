package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.repository.BlockBenchRepository
import com.example.model.InviteCodes
import com.example.ui.components.BlockRadiusSlider
import com.example.ui.components.CraftCard
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * First-run screen for a signed-in user who is not in any block yet: start one or join one by
 * invite code. Window insets are the caller's job (it is hosted inside a Scaffold).
 */
@Composable
fun CircleOnboardingScreen(
  isBusy: Boolean,
  onCreateCircle: (name: String, radiusMiles: Double) -> Unit,
  onJoinCircle: (inviteCode: String) -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var joining by rememberSaveable { mutableStateOf(false) }
  var blockName by rememberSaveable { mutableStateOf("") }
  var radiusMiles by rememberSaveable {
    mutableDoubleStateOf(BlockBenchRepository.DEFAULT_RADIUS_MILES)
  }
  var inviteCode by rememberSaveable { mutableStateOf("") }
  var locationHint by remember { mutableStateOf<String?>(null) }
  var isLocating by remember { mutableStateOf(false) }

  fun suggestBlockNameFromLocation() {
    scope.launch {
      isLocating = true
      locationHint = null
      try {
        val area = lookUpNeighborhood(context)
        if (area != null) {
          blockName = "$area Block".take(BlockBenchRepository.MAX_NAME_LENGTH)
          locationHint = "Detected area: $area"
        } else {
          locationHint = "Could not work out your area. Enter your street or block name."
        }
      } finally {
        isLocating = false
      }
    }
  }

  val locationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        suggestBlockNameFromLocation()
      } else {
        locationHint = "No problem. Enter your street or block name instead."
      }
    }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
      modifier =
        Modifier.widthIn(max = 520.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "BlockBench Setup",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
        TextButton(
          onClick = onSignOut,
          modifier = Modifier.testTag("onboarding_sign_out_button"),
        ) {
          Text(stringResource(id = R.string.sign_out))
        }
      }

      Image(
        painter = painterResource(id = R.drawable.img_workshop_hero),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp)),
        contentScale = ContentScale.Crop,
      )

      Text(
        text = stringResource(id = R.string.join_or_create_title),
        style = MaterialTheme.typography.headlineMedium,
      )

      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
          selected = !joining,
          onClick = { joining = false },
          shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
          modifier = Modifier.testTag("tab_start_block"),
        ) {
          Text(stringResource(id = R.string.create_block_tab))
        }
        SegmentedButton(
          selected = joining,
          onClick = { joining = true },
          shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
          modifier = Modifier.testTag("tab_join_block"),
        ) {
          Text(stringResource(id = R.string.join_block_tab))
        }
      }

      CraftCard {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          if (!joining) {
            OutlinedTextField(
              value = blockName,
              onValueChange = { blockName = it.take(BlockBenchRepository.MAX_NAME_LENGTH) },
              label = { Text("Block or Street Name") },
              placeholder = { Text(stringResource(id = R.string.block_name_hint)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("block_name_input"),
            )

            OutlinedButton(
              onClick = {
                val hasPermission =
                  ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                  ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                  suggestBlockNameFromLocation()
                } else {
                  locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
              },
              enabled = !isLocating,
              modifier = Modifier.fillMaxWidth().testTag("detect_location_button"),
            ) {
              if (isLocating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
              } else {
                Icon(
                  imageVector = Icons.Filled.LocationOn,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(id = R.string.use_location_area))
            }

            locationHint?.let { hint ->
              Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
              )
            }

            BlockRadiusSlider(radiusMiles = radiusMiles, onRadiusChange = { radiusMiles = it })

            Button(
              onClick = { onCreateCircle(blockName, radiusMiles) },
              enabled = !isBusy && blockName.trim().length >= 2,
              modifier =
                Modifier.fillMaxWidth().height(50.dp).testTag("create_block_submit_button"),
            ) {
              if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
              } else {
                Text(stringResource(id = R.string.create_block_cta))
              }
            }
          } else {
            OutlinedTextField(
              value = inviteCode,
              onValueChange = {
                inviteCode = InviteCodes.normalize(it).take(InviteCodes.MAX_LENGTH)
              },
              label = { Text("Block Invite Code") },
              placeholder = { Text(stringResource(id = R.string.invite_code_hint)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("invite_code_input"),
            )
            Text(
              text = "Ask a neighbor to tap \"1-Tap Invite\" on their BlockBench Trust Card.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
              onClick = { onJoinCircle(inviteCode) },
              enabled = !isBusy && InviteCodes.isWellFormed(inviteCode),
              modifier =
                Modifier.fillMaxWidth().height(50.dp).testTag("join_block_submit_button"),
            ) {
              if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
              } else {
                Text(stringResource(id = R.string.join_block_cta))
              }
            }
          }
        }
      }
    }
  }
}

private const val LOCATION_TAG = "BlockBenchLocation"
private const val FRESH_FIX_TIMEOUT_MS = 8_000L

/**
 * Best-effort name of the neighbourhood the device is in (sub-locality, else street, else town),
 * or null. Uses the last known coarse location and asks for a fresh one only if there is none.
 * Reverse geocoding is a blocking network call, so it runs on the IO dispatcher: on the main
 * thread it would freeze the UI for as long as the lookup takes.
 */
@SuppressLint("MissingPermission") // Callers check ACCESS_COARSE_LOCATION first.
private suspend fun lookUpNeighborhood(context: Context): String? {
  val client = LocationServices.getFusedLocationProviderClient(context)
  val cancellation = CancellationTokenSource()
  val location =
    try {
      client.lastLocation.await()
        ?: withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) {
          client
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
            .await()
        }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(LOCATION_TAG, "Location lookup failed", e)
      null
    } finally {
      cancellation.cancel()
    } ?: return null

  if (!Geocoder.isPresent()) return null
  return withContext(Dispatchers.IO) {
    try {
      @Suppress("DEPRECATION")
      Geocoder(context, Locale.getDefault())
        .getFromLocation(location.latitude, location.longitude, 1)
        ?.firstOrNull()
        ?.let { address -> address.subLocality ?: address.thoroughfare ?: address.locality }
        ?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
      // IOException when the geocoding service is unreachable, IllegalArgumentException for
      // out-of-range coordinates.
      Log.w(LOCATION_TAG, "Reverse geocoding failed", e)
      null
    }
  }
}
