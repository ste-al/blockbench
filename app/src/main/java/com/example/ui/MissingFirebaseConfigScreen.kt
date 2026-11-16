package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.CraftCard

/**
 * Shown instead of the app when the build contains no Firebase configuration, i.e. it was built
 * without `app/google-services.json`. Without it there is no backend to talk to.
 */
@Composable
fun MissingFirebaseConfigScreen(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    CraftCard(
      modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth().testTag("missing_config_card")
    ) {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Build,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(36.dp),
        )
        Text(
          text = stringResource(id = R.string.missing_config_title),
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          text = stringResource(id = R.string.missing_config_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
