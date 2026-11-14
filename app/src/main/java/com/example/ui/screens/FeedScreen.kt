package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.BlockCircle
import com.example.model.CraftCategories
import com.example.model.EscrowFeeBreakdown
import com.example.model.Listing
import com.example.model.UserProfile
import com.example.ui.FeedFilter
import com.example.ui.FeedFiltersState
import com.example.ui.components.CraftCard
import com.example.ui.components.EscrowBreakdownCard
import com.example.ui.components.ListingCraftCard
import com.example.ui.components.ListingThumbnail
import com.example.ui.components.copyInviteCodeToClipboard
import com.example.ui.components.formatCents
import com.example.ui.components.shareNeighborhoodInvite
import com.example.ui.theme.MonoFigureStyle
import com.example.ui.theme.ProAmber
import com.example.ui.theme.ProAmberSoft
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
  currentUserId: String,
  profile: UserProfile?,
  activeCircle: BlockCircle,
  listings: List<Listing>,
  proAlertMatches: List<Listing>,
  filters: FeedFiltersState,
  isBusy: Boolean,
  showCreateSheet: Boolean,
  onDismissCreateSheet: () -> Unit,
  onOpenCreateSheet: () -> Unit,
  onOpenBlockSwitcher: () -> Unit,
  onKindFilterChange: (FeedFilter) -> Unit,
  onCategoryToggle: (String?) -> Unit,
  onMaxDistanceChange: (Double) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onCreateListing: (
    kind: String,
    title: String,
    description: String,
    category: String,
    rateCents: Long,
    depositCents: Long,
    distanceMiles: Double,
    instantPickup: Boolean,
    photoUri: String,
  ) -> Unit,
  onUpdateListing: (
    listingId: String,
    title: String,
    description: String,
    category: String,
    rateCents: Long,
    depositCents: Long,
    distanceMiles: Double,
    instantPickup: Boolean,
    photoUri: String,
    onDone: () -> Unit,
  ) -> Unit,
  onTogglePause: (Listing) -> Unit,
  onDeleteListing: (Listing) -> Unit,
  onBookListing: (Listing, String, () -> Unit) -> Unit,
  onOpenProAlertsTab: () -> Unit,
  onShowFeedback: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var selectedListing by remember { mutableStateOf<Listing?>(null) }
  var editingListing by remember { mutableStateOf<Listing?>(null) }
  var showDistanceMenu by remember { mutableStateOf(false) }

  val distanceOptions = listOf(0.5, 1.5, 3.0, 5.0)

  Column(modifier = modifier.fillMaxSize()) {
    // Top Block Bar
    Surface(
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 1.dp,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            onClick = onOpenBlockSwitcher,
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f).testTag("feed_block_switcher_button"),
          ) {
            Column(modifier = Modifier.padding(end = 6.dp, top = 2.dp, bottom = 2.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  text = activeCircle.name,
                  style = MaterialTheme.typography.headlineSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                  imageVector = Icons.Filled.ArrowDropDown,
                  contentDescription = "Switch neighborhood block",
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
              Text(
                text =
                  "${activeCircle.memberUids.size} neighbors · ${String.format(Locale.US, "%.1f", activeCircle.radiusMiles)} mi · Code ${activeCircle.inviteCode}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              onClick = onOpenProAlertsTab,
              color =
                if (profile?.proAlertsEnabled == true) {
                  ProAmberSoft
                } else {
                  MaterialTheme.colorScheme.surfaceVariant
                },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.testTag("feed_pro_alerts_pill"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.NotificationsActive,
                  contentDescription = "Pro Alerts",
                  modifier = Modifier.size(16.dp),
                  tint =
                    if (profile?.proAlertsEnabled == true) {
                      ProAmber
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                  text = if (profile?.proAlertsEnabled == true) "PRO" else "Alerts",
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    if (profile?.proAlertsEnabled == true) {
                      ProAmber
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
              }
            }

            IconButton(
              onClick = {
                copyInviteCodeToClipboard(context, activeCircle.inviteCode, onShowFeedback)
              },
              modifier = Modifier.size(48.dp).testTag("feed_copy_code_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Copy invite code",
                tint = MaterialTheme.colorScheme.secondary,
              )
            }

            IconButton(
              onClick = {
                shareNeighborhoodInvite(context, activeCircle.name, activeCircle.inviteCode)
              },
              modifier = Modifier.size(48.dp).testTag("feed_invite_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "Invite neighbors",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }

        OutlinedTextField(
          value = filters.searchQuery,
          onValueChange = onSearchQueryChange,
          placeholder = { Text(stringResource(id = R.string.search_listings_hint)) },
          leadingIcon = {
            Icon(imageVector = Icons.Filled.Search, contentDescription = null)
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("feed_search_input"),
        )

        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FeedFilter.entries.forEach { filterOption ->
            FilterChip(
              selected = filters.kindFilter == filterOption,
              onClick = { onKindFilterChange(filterOption) },
              label = { Text(filterOption.label) },
              modifier = Modifier.testTag("filter_chip_${filterOption.name.lowercase()}"),
            )
          }

          Box {
            FilterChip(
              selected = filters.maxDistanceMiles < 5.0,
              onClick = { showDistanceMenu = true },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.LocationOn,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              label = {
                Text(
                  if (filters.maxDistanceMiles >= 5.0) {
                    "Any distance"
                  } else {
                    "≤ ${String.format(Locale.US, "%.1f", filters.maxDistanceMiles)} mi"
                  }
                )
              },
              modifier = Modifier.testTag("filter_chip_distance"),
            )
            DropdownMenu(
              expanded = showDistanceMenu,
              onDismissRequest = { showDistanceMenu = false },
            ) {
              distanceOptions.forEach { miles ->
                DropdownMenuItem(
                  text = {
                    Text(
                      if (miles >= 5.0) {
                        "All block distances (≤ 5.0 mi)"
                      } else {
                        "Within ${String.format(Locale.US, "%.1f", miles)} miles"
                      }
                    )
                  },
                  onClick = {
                    onMaxDistanceChange(miles)
                    showDistanceMenu = false
                  },
                )
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          CraftCategories.all.forEach { category ->
            FilterChip(
              selected = filters.selectedCategory == category,
              onClick = { onCategoryToggle(category) },
              label = { Text(category) },
            )
          }
        }
      }
    }

    if (profile?.proAlertsEnabled == true && proAlertMatches.isNotEmpty()) {
      Surface(
        color = ProAmberSoft,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        onClick = { selectedListing = proAlertMatches.first() },
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.ElectricBolt,
            contentDescription = "Pro Alert Match",
            tint = ProAmber,
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "${proAlertMatches.size} Pro Alert Match nearby",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "Tap to inspect ${proAlertMatches.first().title}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    if (listings.isEmpty()) {
      val hasActiveFilters =
        filters.kindFilter != FeedFilter.ALL ||
          filters.selectedCategory != null ||
          filters.searchQuery.isNotBlank() ||
          filters.maxDistanceMiles < 5.0
      EmptyBlockFeedState(
        circle = activeCircle,
        hasActiveFilters = hasActiveFilters,
        onClearFilters = {
          onKindFilterChange(FeedFilter.ALL)
          onCategoryToggle(filters.selectedCategory)
          onSearchQueryChange("")
          onMaxDistanceChange(5.0)
        },
        onInviteNeighbors = {
          shareNeighborhoodInvite(context, activeCircle.name, activeCircle.inviteCode)
        },
        onPostFirstListing = onOpenCreateSheet,
        modifier = Modifier.weight(1f).fillMaxWidth(),
      )
    } else {
      BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
        val isWide = maxWidth >= 600.dp
        if (isWide) {
          LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            items(listings, key = { it.id }) { listing ->
              ListingCraftCard(
                listing = listing,
                isOwnListing = listing.ownerUid == currentUserId,
                onClick = { selectedListing = listing },
              )
            }
          }
        } else {
          LazyColumn(
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            items(listings, key = { it.id }) { listing ->
              ListingCraftCard(
                listing = listing,
                isOwnListing = listing.ownerUid == currentUserId,
                onClick = { selectedListing = listing },
              )
            }
          }
        }
      }
    }
  }

  if (showCreateSheet) {
    ListingEditorSheet(
      existingListing = null,
      isBusy = isBusy,
      onDismiss = onDismissCreateSheet,
      onSave = { kind, title, desc, cat, rate, deposit, dist, instant, photo ->
        onCreateListing(kind, title, desc, cat, rate, deposit, dist, instant, photo)
      },
    )
  }

  editingListing?.let { target ->
    ListingEditorSheet(
      existingListing = target,
      isBusy = isBusy,
      onDismiss = { editingListing = null },
      onSave = { _, title, desc, cat, rate, deposit, dist, instant, photo ->
        onUpdateListing(
          target.id,
          title,
          desc,
          cat,
          rate,
          deposit,
          dist,
          instant,
          photo,
        ) {
          editingListing = null
        }
      },
    )
  }

  selectedListing?.let { listing ->
    ListingDetailSheet(
      listing = listing,
      isOwnListing = listing.ownerUid == currentUserId,
      isBusy = isBusy,
      onDismiss = { selectedListing = null },
      onBook = { notes ->
        onBookListing(listing, notes) { selectedListing = null }
      },
      onEdit = {
        val target = listing
        selectedListing = null
        editingListing = target
      },
      onTogglePause = {
        onTogglePause(listing)
        selectedListing = null
      },
      onDelete = {
        onDeleteListing(listing)
        selectedListing = null
      },
    )
  }
}

@Composable
private fun EmptyBlockFeedState(
  circle: BlockCircle,
  hasActiveFilters: Boolean,
  onClearFilters: () -> Unit,
  onInviteNeighbors: () -> Unit,
  onPostFirstListing: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Column(
      modifier = Modifier.padding(24.dp).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_workshop_hero),
        contentDescription = "Empty neighborhood workbench",
        modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(18.dp)),
        contentScale = ContentScale.Crop,
      )
      Text(
        text =
          if (hasActiveFilters) {
            "No matching listings in ${circle.name}"
          } else {
            "Stock ${circle.name}'s shared bench"
          },
        style = MaterialTheme.typography.titleLarge,
      )
      Text(
        text =
          if (hasActiveFilters) {
            "Try clearing your distance, category, or search filters to view all tools and micro-jobs on your block."
          } else {
            "List an idle tool to earn passive micro-rentals, post a 20-minute micro-job, or invite neighbors using code ${circle.inviteCode}."
          },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (hasActiveFilters) {
          OutlinedButton(
            onClick = onClearFilters,
            modifier = Modifier.weight(1f).testTag("clear_filters_button"),
          ) {
            Text("Reset Filters")
          }
        } else {
          OutlinedButton(
            onClick = onInviteNeighbors,
            modifier = Modifier.weight(1f).testTag("empty_invite_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Share,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Invite")
          }
        }
        Button(
          onClick = onPostFirstListing,
          modifier = Modifier.weight(1f).testTag("empty_post_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Post Listing")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListingEditorSheet(
  existingListing: Listing?,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSave: (
    kind: String,
    title: String,
    description: String,
    category: String,
    rateCents: Long,
    depositCents: Long,
    distanceMiles: Double,
    instantPickup: Boolean,
    photoUri: String,
  ) -> Unit,
) {
  val isEditing = existingListing != null
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var selectedKindIdx by remember(existingListing?.id) {
    mutableIntStateOf(if (existingListing?.kind == Listing.KIND_MICRO_JOB) 1 else 0)
  }
  var title by remember(existingListing?.id) { mutableStateOf(existingListing?.title ?: "") }
  var description by remember(existingListing?.id) {
    mutableStateOf(existingListing?.description ?: "")
  }
  var category by remember(existingListing?.id) {
    mutableStateOf(existingListing?.category?.ifBlank { CraftCategories.all.first() } ?: CraftCategories.all.first())
  }
  var rateDollarsText by remember(existingListing?.id) {
    mutableStateOf(
      if (existingListing != null) {
        String.format(Locale.US, "%.2f", existingListing.rateCents / 100.0).removeSuffix(".00")
      } else {
        "18"
      }
    )
  }
  var depositDollarsText by remember(existingListing?.id) {
    mutableStateOf(
      if (existingListing != null) {
        String.format(Locale.US, "%.2f", existingListing.depositCents / 100.0).removeSuffix(".00")
      } else {
        "35"
      }
    )
  }
  var distanceMiles by remember(existingListing?.id) {
    mutableDoubleStateOf(existingListing?.distanceMiles ?: 0.3)
  }
  var instantPickup by remember(existingListing?.id) {
    mutableStateOf(existingListing?.instantPickup ?: true)
  }
  var photoUri by remember(existingListing?.id) { mutableStateOf(existingListing?.photoUri ?: "") }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        photoUri = uri.toString()
      }
    }

  val isJob = selectedKindIdx == 1
  val parsedRateCents = CraftCategories.parseDollarsToCents(rateDollarsText)
  val parsedDepositCents =
    if (isJob) 0L else CraftCategories.parseDollarsToCents(depositDollarsText)
  val validationHint =
    when {
      title.trim().length < 3 -> "Enter at least 3 characters for the title."
      parsedRateCents == null || parsedRateCents !in 100L..500_000L ->
        "Rate must be between $1.00 and $5,000.00."
      parsedDepositCents == null || parsedDepositCents !in 0L..500_000L ->
        "Deposit must be between $0.00 and $5,000.00."
      else -> null
    }
  val isValid = validationHint == null

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
        text = if (isEditing) "Edit Block Listing" else "Post to Neighborhood Block",
        style = MaterialTheme.typography.headlineSmall,
      )

      if (!isEditing) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          SegmentedButton(
            selected = selectedKindIdx == 0,
            onClick = { selectedKindIdx = 0 },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.testTag("create_kind_tool"),
          ) {
            Text("Lend a Tool")
          }
          SegmentedButton(
            selected = selectedKindIdx == 1,
            onClick = { selectedKindIdx = 1 },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.testTag("create_kind_job"),
          ) {
            Text("Post Micro-Job")
          }
        }
      }

      OutlinedTextField(
        value = title,
        onValueChange = { title = it.take(80) },
        label = { Text(if (isJob) "Micro-Job Title" else "Tool Name & Spec") },
        placeholder = {
          Text(if (isJob) "e.g. Fix Backyard Gate Latch" else "e.g. DeWalt Pressure Washer")
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("create_title_input"),
      )

      OutlinedTextField(
        value = description,
        onValueChange = { description = it.take(400) },
        label = { Text("Notes & Handoff Details") },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth().testTag("create_description_input"),
      )

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        CraftCategories.all.forEach { cat ->
          FilterChip(
            selected = category == cat,
            onClick = { category = cat },
            label = { Text(cat) },
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = rateDollarsText,
          onValueChange = { rateDollarsText = it.filter { ch -> ch.isDigit() || ch == '.' } },
          label = { Text(if (isJob) "Payout ($)" else "Rate ($)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("create_rate_input"),
        )
        if (!isJob) {
          OutlinedTextField(
            value = depositDollarsText,
            onValueChange = {
              depositDollarsText = it.filter { ch -> ch.isDigit() || ch == '.' }
            },
            label = { Text("Deposit ($)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("create_deposit_input"),
          )
        }
      }

      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("Distance on Block", style = MaterialTheme.typography.labelLarge)
          Text(
            text = String.format(Locale.US, "%.1f mi", distanceMiles),
            style = MonoFigureStyle,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Slider(
          value = distanceMiles.toFloat(),
          onValueChange = { distanceMiles = it.toDouble() },
          valueRange = 0.1f..5.0f,
          modifier = Modifier.testTag("create_distance_slider"),
        )
      }

      if (parsedRateCents != null && parsedRateCents >= 100L && parsedDepositCents != null) {
        EscrowBreakdownCard(
          breakdown = EscrowFeeBreakdown.calculate(parsedRateCents, parsedDepositCents),
          isMicroJob = isJob,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Instant Porch Pickup / Claim", style = MaterialTheme.typography.titleMedium)
          Text(
            text = "Highlights listing for Pro Alert subscribers nearby.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = instantPickup,
          onCheckedChange = { instantPickup = it },
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          modifier = Modifier.weight(1f).testTag("pick_photo_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Image,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(if (photoUri.isBlank()) "Add Photo (Optional)" else "Change Photo")
        }
        if (photoUri.isNotBlank()) {
          AsyncImage(
            model = photoUri,
            contentDescription = "Selected listing photo",
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
          )
        }
      }

      if (validationHint != null) {
        Text(
          text = validationHint,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      Button(
        onClick = {
          onSave(
            if (isJob) Listing.KIND_MICRO_JOB else Listing.KIND_TOOL,
            title,
            description,
            category,
            parsedRateCents ?: 0L,
            parsedDepositCents ?: 0L,
            distanceMiles,
            instantPickup,
            photoUri,
          )
        },
        enabled = isValid && !isBusy,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("publish_listing_button"),
      ) {
        Text(
          when {
            isEditing -> "Save Listing Changes"
            isJob -> "Publish Micro-Job"
            else -> "Publish Tool to Block"
          }
        )
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListingDetailSheet(
  listing: Listing,
  isOwnListing: Boolean,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onBook: (String) -> Unit,
  onEdit: () -> Unit,
  onTogglePause: () -> Unit,
  onDelete: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var notes by remember { mutableStateOf("") }
  var showDeleteConfirm by remember { mutableStateOf(false) }
  val isJob = listing.kind == Listing.KIND_MICRO_JOB
  val breakdown = EscrowFeeBreakdown.calculate(listing.rateCents, listing.depositCents)

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
          size = 72.dp,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(text = listing.title, style = MaterialTheme.typography.headlineSmall)
          Text(
            text =
              "${listing.category} · ${String.format(Locale.US, "%.1f", listing.distanceMiles)} mi · By ${listing.ownerName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (listing.description.isNotBlank()) {
        CraftCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
          Text(
            text = listing.description,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }

      EscrowBreakdownCard(breakdown = breakdown, isMicroJob = isJob)

      if (isOwnListing) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onEdit,
            modifier = Modifier.weight(1f).testTag("edit_listing_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Edit,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Edit", maxLines = 1)
          }
          OutlinedButton(
            onClick = onTogglePause,
            modifier = Modifier.weight(1f).testTag("toggle_pause_listing_button"),
          ) {
            Text(
              text = if (listing.status == Listing.STATUS_PAUSED) "Resume" else "Pause",
              maxLines = 1,
            )
          }
          TextButton(
            onClick = { showDeleteConfirm = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag("delete_listing_button"),
          ) {
            Text("Delete")
          }
        }
      } else if (listing.status == Listing.STATUS_AVAILABLE) {
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it.take(250) },
          label = { Text("Pickup / Arrival Note") },
          placeholder = { Text("e.g. Can swing by at 5:30 PM today") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("booking_notes_input"),
        )
        Button(
          onClick = { onBook(notes) },
          enabled = !isBusy,
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("confirm_escrow_book_button"),
        ) {
          Text(
            text =
              if (isJob) {
                "Claim Job (${formatCents(breakdown.providerNetCents)} net)"
              } else {
                "Hold Escrow & Book (${formatCents(breakdown.totalEscrowHoldCents)})"
              }
          )
        }
      } else {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "This listing is currently ${listing.status}.",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }

  if (showDeleteConfirm) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text("Delete Listing?") },
      text = { Text("Remove \"${listing.title}\" from your block feed? This cannot be undone.") },
      confirmButton = {
        Button(
          onClick = {
            showDeleteConfirm = false
            onDelete()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
      },
    )
  }
}
