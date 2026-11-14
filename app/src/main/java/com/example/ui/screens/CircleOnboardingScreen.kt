package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.components.CraftCard
import com.google.android.gms.location.LocationServices
import java.util.Locale

@SuppressLint("MissingPermission")
@Composable
fun CircleOnboardingScreen(
  isBusy: Boolean,
  onCreateCircle: (name: String, radiusMiles: Double) -> Unit,
  onJoinCircle: (inviteCode: String) -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var selectedMode by remember { mutableIntStateOf(0) } // 0 = Start Block, 1 = Join Code
  var blockName by remember { mutableStateOf("") }
  var radiusMiles by remember { mutableDoubleStateOf(1.5) }
  var inviteCode by remember { mutableStateOf("") }
  var locationHint by remember { mutableStateOf<String?>(null) }

  fun fetchCoarseNeighborhood() {
    val fused = LocationServices.getFusedLocationProviderClient(context)
    fused.lastLocation
      .addOnSuccessListener { loc ->
        if (loc != null) {
          runCatching {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
            val area = addresses?.firstOrNull()?.let { addr ->
              addr.subLocality ?: addr.thoroughfare ?: addr.locality
            }
            if (!area.isNullOrBlank()) {
              blockName = "$area Block"
              locationHint = "Detected area: $area"
            } else {
              locationHint = "Location active — enter your street or block name."
            }
          }.onFailure {
            locationHint = "Enter your preferred street or block name."
          }
        } else {
          locationHint = "No recent GPS fix — enter your street name manually."
        }
      }
      .addOnFailureListener {
        locationHint = "Enter your street or block name manually."
      }
  }

  val locationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        fetchCoarseNeighborhood()
      } else {
        locationHint = "Location permission skipped — enter your street name manually."
      }
    }

  Box(
    modifier = modifier.fillMaxSize().safeDrawingPadding(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 520.dp)
          .fillMaxWidth()
          .imePadding()
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
        contentDescription = "Neighborhood block circle",
        modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp)),
        contentScale = ContentScale.Crop,
      )

      Text(
        text = stringResource(id = R.string.join_or_create_title),
        style = MaterialTheme.typography.headlineMedium,
      )

      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
          selected = selectedMode == 0,
          onClick = { selectedMode = 0 },
          shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
          modifier = Modifier.testTag("tab_start_block"),
        ) {
          Text(stringResource(id = R.string.create_block_tab))
        }
        SegmentedButton(
          selected = selectedMode == 1,
          onClick = { selectedMode = 1 },
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
          if (selectedMode == 0) {
            OutlinedTextField(
              value = blockName,
              onValueChange = { blockName = it },
              label = { Text("Block or Street Name") },
              placeholder = { Text(stringResource(id = R.string.block_name_hint)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("block_name_input"),
            )

            OutlinedButton(
              onClick = {
                val hasPerm =
                  ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                  ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) {
                  fetchCoarseNeighborhood()
                } else {
                  locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
              },
              modifier = Modifier.fillMaxWidth().testTag("detect_location_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(id = R.string.use_location_area))
            }

            if (locationHint != null) {
              Text(
                text = locationHint.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
              )
            }

            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("Block Radius", style = MaterialTheme.typography.labelLarge)
                Text(
                  text = String.format(Locale.US, "%.1f mi", radiusMiles),
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              Slider(
                value = radiusMiles.toFloat(),
                onValueChange = { radiusMiles = it.toDouble() },
                valueRange = 0.5f..5.0f,
                steps = 8,
              )
            }

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
              onValueChange = { inviteCode = it.uppercase(Locale.US).take(12) },
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
              enabled = !isBusy && inviteCode.trim().length >= 4,
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
