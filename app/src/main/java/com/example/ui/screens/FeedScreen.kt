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
import androidx.compose.material.icons.filled.CloudOff
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BlockCircle
import com.example.model.Booking
import com.example.model.CraftCategories
import com.example.model.EscrowFeeBreakdown
import com.example.model.Listing
import com.example.model.ListingDraft
import com.example.model.ListingPhotos
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.dataOrNull
import com.example.ui.FeedFilter
import com.example.ui.FeedFiltersState
import com.example.ui.components.CraftCard
import com.example.ui.components.EscrowBreakdownCard
import com.example.ui.components.ListingCraftCard
import com.example.ui.components.ListingPhotoCodec
import com.example.ui.components.ListingThumbnail
import com.example.ui.components.circleSummary
import com.example.ui.components.copyInviteCodeToClipboard
import com.example.ui.components.formatCents
import com.example.ui.components.formatMiles
import com.example.ui.components.shareNeighborhoodInvite
import com.example.ui.components.spokenMiles
import com.example.ui.components.toTenthsOfAMile
import com.example.ui.theme.MonoFigureStyle
import com.example.ui.theme.blockBenchColors
import kotlinx.coroutines.launch

private val DISTANCE_OPTIONS = listOf(0.5, 1.5, 3.0, FeedFiltersState.ANY_DISTANCE_MILES)

/**
 * The active block's feed.
 *
 * [feed] is everything in the block and carries the loading / error state; [visibleListings] is
 * the same list after the user's [filters]. Open sheets refer to listings by id and are resolved
 * against [feed] on every recomposition, so they always show the listing as it is now (for
 * example "Reserved" the moment a neighbour books it) and close by themselves if it is deleted.
 */
@Composable
fun FeedScreen(
  currentUserId: String,
  profile: UserProfile?,
  activeCircle: BlockCircle,
  feed: UiState<List<Listing>>,
  visibleListings: List<Listing>,
  proAlertMatches: List<Listing>,
  filters: FeedFiltersState,
  isBusy: Boolean,
  showCreateSheet: Boolean,
  onDismissCreateSheet: () -> Unit,
  onOpenCreateSheet: () -> Unit,
  onOpenBlockSwitcher: () -> Unit,
  onKindFilterChange: (FeedFilter) -> Unit,
  onCategoryToggle: (String) -> Unit,
  onMaxDistanceChange: (Double) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onClearFilters: () -> Unit,
  onRetry: () -> Unit,
  onCreateListing: (ListingDraft) -> Unit,
  onUpdateListing: (listingId: String, draft: ListingDraft, onDone: () -> Unit) -> Unit,
  onTogglePause: (Listing) -> Unit,
  onDeleteListing: (Listing) -> Unit,
  onBookListing: (listing: Listing, notes: String, onDone: () -> Unit) -> Unit,
  onOpenProAlertsTab: () -> Unit,
  onShowFeedback: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val brand = blockBenchColors
  var selectedListingId by rememberSaveable { mutableStateOf<String?>(null) }
  var editingListingId by rememberSaveable { mutableStateOf<String?>(null) }
  var showDistanceMenu by remember { mutableStateOf(false) }

  val allListings = feed.dataOrNull().orEmpty()
  val selectedListing = allListings.firstOrNull { it.id == selectedListingId }
  val editingListing = allListings.firstOrNull { it.id == editingListingId }

  // Forget ids whose listing is gone, so a sheet does not pop back up if the id is ever reused.
  LaunchedEffect(feed, selectedListingId, editingListingId) {
    if (feed is UiState.Success) {
      if (selectedListingId != null && selectedListing == null) selectedListingId = null
      if (editingListingId != null && editingListing == null) editingListingId = null
    }
  }

  val proAlertsOn = profile?.proAlertsEnabled == true

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
                text = circleSummary(activeCircle),
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
            val pillContent =
              if (proAlertsOn) brand.onProContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(
              onClick = onOpenProAlertsTab,
              color =
                if (proAlertsOn) brand.proContainer else MaterialTheme.colorScheme.surfaceVariant,
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
                  tint = pillContent,
                )
                Text(
                  text = if (proAlertsOn) "PRO" else "Alerts",
                  style = MaterialTheme.typography.labelSmall,
                  color = pillContent,
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

        val searchLabel = stringResource(id = R.string.search_listings_label)
        OutlinedTextField(
          value = filters.searchQuery,
          onValueChange = onSearchQueryChange,
          placeholder = { Text(stringResource(id = R.string.search_listings_hint)) },
          leadingIcon = {
            Icon(imageVector = Icons.Filled.Search, contentDescription = null)
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          // The placeholder is all that names this field, and it goes away with the first letter.
          modifier =
            Modifier.fillMaxWidth().testTag("feed_search_input").semantics {
              contentDescription = searchLabel
            },
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
            val anyDistance = filters.maxDistanceMiles >= FeedFiltersState.ANY_DISTANCE_MILES
            FilterChip(
              selected = !anyDistance,
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
                  if (anyDistance) "Any distance" else "≤ ${formatMiles(filters.maxDistanceMiles)}"
                )
              },
              modifier = Modifier.testTag("filter_chip_distance"),
            )
            DropdownMenu(
              expanded = showDistanceMenu,
              onDismissRequest = { showDistanceMenu = false },
            ) {
              DISTANCE_OPTIONS.forEach { miles ->
                DropdownMenuItem(
                  text = {
                    Text(
                      if (miles >= FeedFiltersState.ANY_DISTANCE_MILES) {
                        "Any distance"
                      } else {
                        "Within ${formatMiles(miles)}"
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

    val firstMatch = proAlertMatches.firstOrNull()
    if (proAlertsOn && firstMatch != null) {
      Surface(
        color = brand.proContainer,
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("pro_alert_banner"),
        shape = RoundedCornerShape(12.dp),
        onClick = { selectedListingId = firstMatch.id },
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.ElectricBolt,
            contentDescription = null,
            tint = brand.onProContainer,
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text =
                if (proAlertMatches.size == 1) {
                  "1 Pro Alert match nearby"
                } else {
                  "${proAlertMatches.size} Pro Alert matches nearby"
                },
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "Tap to inspect ${firstMatch.title}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    val contentModifier = Modifier.weight(1f).fillMaxWidth()
    when {
      feed is UiState.Loading ->
        Box(modifier = contentModifier, contentAlignment = Alignment.Center) {
          CircularProgressIndicator(modifier = Modifier.testTag("feed_loading"))
        }
      feed is UiState.Error ->
        FeedLoadError(message = feed.message, onRetry = onRetry, modifier = contentModifier)
      visibleListings.isEmpty() ->
        EmptyBlockFeedState(
          circle = activeCircle,
          hasActiveFilters = filters.isNarrowing,
          onClearFilters = onClearFilters,
          onInviteNeighbors = {
            shareNeighborhoodInvite(context, activeCircle.name, activeCircle.inviteCode)
          },
          onPostFirstListing = onOpenCreateSheet,
          modifier = contentModifier,
        )
      else ->
        BoxWithConstraints(modifier = contentModifier) {
          // Bottom padding keeps the last card clear of the floating "post" button.
          val listPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp)
          if (maxWidth >= 600.dp) {
            LazyVerticalGrid(
              columns = GridCells.Fixed(2),
              contentPadding = listPadding,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.fillMaxSize(),
            ) {
              items(visibleListings, key = { it.id }) { listing ->
                ListingCraftCard(
                  listing = listing,
                  isOwnListing = listing.ownerUid == currentUserId,
                  onClick = { selectedListingId = listing.id },
                )
              }
            }
          } else {
            LazyColumn(
              contentPadding = listPadding,
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.fillMaxSize(),
            ) {
              items(visibleListings, key = { it.id }) { listing ->
                ListingCraftCard(
                  listing = listing,
                  isOwnListing = listing.ownerUid == currentUserId,
                  onClick = { selectedListingId = listing.id },
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
      onSave = onCreateListing,
    )
  }

  editingListing?.let { target ->
    ListingEditorSheet(
      existingListing = target,
      isBusy = isBusy,
      onDismiss = { editingListingId = null },
      onSave = { draft -> onUpdateListing(target.id, draft) { editingListingId = null } },
    )
  }

  selectedListing?.let { listing ->
    ListingDetailSheet(
      listing = listing,
      isOwnListing = listing.ownerUid == currentUserId,
      isBusy = isBusy,
      onDismiss = { selectedListingId = null },
      onBook = { notes -> onBookListing(listing, notes) { selectedListingId = null } },
      onEdit = {
        selectedListingId = null
        editingListingId = listing.id
      },
      onTogglePause = {
        onTogglePause(listing)
        selectedListingId = null
      },
      onDelete = {
        onDeleteListing(listing)
        selectedListingId = null
      },
    )
  }
}

@Composable
private fun FeedLoadError(
  message: String,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
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
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Button(onClick = onRetry, modifier = Modifier.testTag("feed_retry_button")) {
        Text(stringResource(id = R.string.retry_sync))
      }
    }
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
      // Scrolls rather than clips when the header leaves little room (landscape, large fonts).
      modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_workshop_hero),
        contentDescription = null,
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

/** Keeps what a numeric keyboard can produce for a money amount, in any locale. */
private fun String.asAmountInput(): String = filter { it.isDigit() || it == '.' || it == ',' }.take(10)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListingEditorSheet(
  existingListing: Listing?,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSave: (ListingDraft) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val isEditing = existingListing != null
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  // Keyed on the listing id only: the form must not reset under the user's fingers when the
  // listing document changes for another reason while it is being edited.
  val formKey = existingListing?.id
  var isJob by remember(formKey) { mutableStateOf(existingListing?.isMicroJob == true) }
  var title by remember(formKey) { mutableStateOf(existingListing?.title.orEmpty()) }
  var description by remember(formKey) { mutableStateOf(existingListing?.description.orEmpty()) }
  var category by
    remember(formKey) {
      mutableStateOf(
        existingListing?.category?.takeIf { it.isNotBlank() } ?: CraftCategories.all.first()
      )
    }
  var rateDollarsText by
    remember(formKey) {
      mutableStateOf(existingListing?.let { CraftCategories.centsToDollarsInput(it.rateCents) } ?: "18")
    }
  var depositDollarsText by
    remember(formKey) {
      mutableStateOf(
        existingListing?.let { CraftCategories.centsToDollarsInput(it.depositCents) } ?: "35"
      )
    }
  var distanceMiles by
    remember(formKey) { mutableDoubleStateOf(existingListing?.distanceMiles ?: 0.3) }
  var instantPickup by remember(formKey) { mutableStateOf(existingListing?.instantPickup ?: true) }
  // A photo saved by an older app version is a device-local URI nobody else can load; drop it.
  var photoUri by
    remember(formKey) { mutableStateOf(ListingPhotos.sanitize(existingListing?.photoUri.orEmpty())) }
  var isReadingPhoto by remember(formKey) { mutableStateOf(false) }
  var photoError by remember(formKey) { mutableStateOf<String?>(null) }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          isReadingPhoto = true
          photoError = null
          try {
            val thumbnail = ListingPhotoCodec.encodeThumbnail(context, uri)
            if (thumbnail != null) {
              photoUri = thumbnail
            } else {
              photoError = "That photo could not be read. Try a different one."
            }
          } finally {
            isReadingPhoto = false
          }
        }
      }
    }

  val parsedRateCents = CraftCategories.parseDollarsToCents(rateDollarsText)
  val parsedDepositCents =
    if (isJob) 0L else CraftCategories.parseDollarsToCents(depositDollarsText)
  val validationHint =
    when {
      title.trim().length < 3 -> "Enter at least 3 characters for the title."
      parsedRateCents == null ||
        parsedRateCents !in Listing.MIN_RATE_CENTS..Listing.MAX_AMOUNT_CENTS ->
        "Rate must be between $1.00 and $5,000.00."
      parsedDepositCents == null || parsedDepositCents !in 0L..Listing.MAX_AMOUNT_CENTS ->
        "Deposit must be between $0.00 and $5,000.00."
      else -> null
    }

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

      // A listing's kind is fixed once it exists (bookings depend on it).
      if (!isEditing) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          SegmentedButton(
            selected = !isJob,
            onClick = { isJob = false },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.testTag("create_kind_tool"),
          ) {
            Text("Lend a Tool")
          }
          SegmentedButton(
            selected = isJob,
            onClick = { isJob = true },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.testTag("create_kind_job"),
          ) {
            Text("Post Micro-Job")
          }
        }
      }

      OutlinedTextField(
        value = title,
        onValueChange = { title = it.take(Listing.MAX_TITLE_LENGTH) },
        label = { Text(if (isJob) "Micro-Job Title" else "Tool Name & Spec") },
        placeholder = {
          Text(if (isJob) "e.g. Fix Backyard Gate Latch" else "e.g. DeWalt Pressure Washer")
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("create_title_input"),
      )

      OutlinedTextField(
        value = description,
        onValueChange = { description = it.take(Listing.MAX_DESCRIPTION_LENGTH) },
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
          onValueChange = { rateDollarsText = it.asAmountInput() },
          label = { Text(if (isJob) "Payout ($)" else "Rate ($)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("create_rate_input"),
        )
        if (!isJob) {
          OutlinedTextField(
            value = depositDollarsText,
            onValueChange = { depositDollarsText = it.asAmountInput() },
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
            text = formatMiles(distanceMiles),
            style = MonoFigureStyle,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Slider(
          value = distanceMiles.toFloat(),
          onValueChange = { distanceMiles = it.toTenthsOfAMile() },
          valueRange = 0.1f..5.0f,
          modifier =
            Modifier.testTag("create_distance_slider").semantics {
              contentDescription = "Distance on Block"
              stateDescription = spokenMiles(distanceMiles)
            },
        )
      }

      if (
        parsedRateCents != null &&
          parsedRateCents >= Listing.MIN_RATE_CENTS &&
          parsedDepositCents != null
      ) {
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
          modifier =
            Modifier.testTag("create_instant_switch").semantics {
              contentDescription = "Instant Porch Pickup / Claim"
            },
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
          enabled = !isReadingPhoto,
          modifier = Modifier.weight(1f).testTag("pick_photo_button"),
        ) {
          if (isReadingPhoto) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          } else {
            Icon(
              imageVector = Icons.Filled.Image,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          }
          Spacer(modifier = Modifier.width(6.dp))
          Text(if (photoUri.isBlank()) "Add Photo (Optional)" else "Change Photo")
        }
        if (photoUri.isNotBlank()) {
          ListingThumbnail(photoUri = photoUri, title = title, isJob = isJob, size = 48.dp)
          TextButton(
            onClick = { photoUri = "" },
            modifier = Modifier.testTag("remove_photo_button"),
          ) {
            Text("Remove")
          }
        }
      }

      val hint = photoError ?: validationHint
      if (hint != null) {
        Text(
          text = hint,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      Button(
        onClick = {
          onSave(
            ListingDraft(
              kind = if (isJob) Listing.KIND_MICRO_JOB else Listing.KIND_TOOL,
              title = title,
              description = description,
              category = category,
              rateCents = parsedRateCents ?: 0L,
              depositCents = parsedDepositCents ?: 0L,
              distanceMiles = distanceMiles,
              instantPickup = instantPickup,
              photoUri = photoUri,
            )
          )
        },
        enabled = validationHint == null && !isBusy && !isReadingPhoto,
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
  // The listing turns "reserved" the instant our own booking commits, a moment before the sheet
  // is told to close. Keep the form up for that moment rather than flashing "a neighbor has this
  // reserved" at the person who just reserved it.
  var requestSent by remember { mutableStateOf(false) }
  val isJob = listing.isMicroJob
  val breakdown = EscrowFeeBreakdown.calculate(listing.rateCents, listing.depositCents)
  // A booking in flight holds the listing: it can be edited, but not paused or deleted.
  val isHeldByBooking =
    listing.status == Listing.STATUS_RESERVED && !listing.hasUntrackedReservation

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
          isJob = isJob,
          size = 72.dp,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(text = listing.title, style = MaterialTheme.typography.headlineSmall)
          Text(
            text =
              "${listing.category} · ${formatMiles(listing.distanceMiles)} · " +
                if (isOwnListing) "Posted by you" else "By ${listing.ownerName}",
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
        if (listing.status == Listing.STATUS_RESERVED) {
          DetailNotice(
            text =
              if (isHeldByBooking) {
                "A neighbor has a booking on this. Manage it in the Escrow tab; you can pause or " +
                  "delete the listing once that booking is settled."
              } else {
                "This is still marked reserved from an earlier booking. Clear the reservation " +
                  "to put it back on the feed."
              }
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onEdit,
            enabled = !isBusy,
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
            enabled = !isBusy && !isHeldByBooking,
            modifier = Modifier.weight(1f).testTag("toggle_pause_listing_button"),
          ) {
            Text(
              text =
                when (listing.status) {
                  Listing.STATUS_AVAILABLE -> "Pause"
                  Listing.STATUS_PAUSED -> "Resume"
                  else -> "Clear"
                },
              maxLines = 1,
            )
          }
          TextButton(
            onClick = { showDeleteConfirm = true },
            enabled = !isBusy && listing.status != Listing.STATUS_RESERVED,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag("delete_listing_button"),
          ) {
            Text("Delete")
          }
        }
      } else if (listing.status == Listing.STATUS_AVAILABLE || (requestSent && isBusy)) {
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it.take(Booking.MAX_NOTES_LENGTH) },
          label = { Text("Pickup / Arrival Note") },
          placeholder = { Text("e.g. Can swing by at 5:30 PM today") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("booking_notes_input"),
        )
        Button(
          onClick = {
            requestSent = true
            onBook(notes)
          },
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
        Text(
          text =
            "${listing.ownerName} approves the request before anything is locked in. " +
              "You can cancel any time before the handoff.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        DetailNotice(
          text =
            if (listing.status == Listing.STATUS_PAUSED) {
              "${listing.ownerName} has paused this listing for now."
            } else {
              "A neighbor has this reserved right now. It returns to the feed when they are done."
            },
          modifier = Modifier.testTag("listing_unavailable_notice"),
        )
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
          modifier = Modifier.testTag("confirm_delete_listing_button"),
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

@Composable
private fun DetailNotice(text: String, modifier: Modifier = Modifier) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(12.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    Text(
      text = text,
      modifier = Modifier.padding(14.dp),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
