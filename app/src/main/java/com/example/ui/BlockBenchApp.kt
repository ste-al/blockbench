package com.example.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.repository.BlockBenchRepository
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.components.BlockSwitcherSheet
import com.example.ui.components.CraftCard
import com.example.ui.components.copyInviteCodeToClipboard
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CircleOnboardingScreen
import com.example.ui.screens.EscrowScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.TrustHubScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_FEED = "feed"
const val ROUTE_ESCROW = "escrow"
const val ROUTE_TRUST = "trust"

@Composable
fun BlockBenchAppRoot(auth: FirebaseAuth = Firebase.auth) {
  val currentUser by
    auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    val uid = user.uid
    val displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Neighbor"
    key(uid) {
      AuthenticatedAppShell(
        currentUserId = uid,
        initialDisplayName = displayName,
      )
    }
  }
}

@Composable
private fun AuthenticatedAppShell(
  currentUserId: String,
  initialDisplayName: String,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: BlockBenchViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY]) as Application
            val dbId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)
            BlockBenchViewModel(
              repository = BlockBenchRepository(db),
              currentUserId = currentUserId,
              initialDisplayName = initialDisplayName,
            )
          }
        },
    )

  val profileUi by viewModel.userProfileState.collectAsStateWithLifecycle()
  val circlesUi by viewModel.circlesState.collectAsStateWithLifecycle()
  val activeCircle by viewModel.activeCircle.collectAsStateWithLifecycle()
  val listingsUi by viewModel.listingsState.collectAsStateWithLifecycle()
  val filteredListings by viewModel.filteredListings.collectAsStateWithLifecycle()
  val proAlertMatches by viewModel.proAlertMatches.collectAsStateWithLifecycle()
  val bookingsUi by viewModel.bookingsState.collectAsStateWithLifecycle()
  val filters by viewModel.filters.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(feedbackMessage) {
    val msg = feedbackMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearFeedback()
    }
  }

  val circles = (circlesUi as? UiState.Success)?.data.orEmpty()
  val profile = (profileUi as? UiState.Success)?.data
  val allListings = (listingsUi as? UiState.Success)?.data.orEmpty()
  val bookings = (bookingsUi as? UiState.Success)?.data.orEmpty()

  LaunchedEffect(activeCircle?.id, activeCircle?.memberUids, allListings) {
    val circle = activeCircle
    if (circle != null && allListings.isNotEmpty()) {
      viewModel.syncMissingCircleMembersToOwnedListings(circle, allListings)
    }
  }

  val handleSignOut: () -> Unit = {
    signOutUser(credentialManager = credentialManager, scope = scope)
  }

  if (circlesUi is UiState.Loading || profileUi is UiState.Loading) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  val streamErrorMessage =
    (circlesUi as? UiState.Error)?.message ?: (profileUi as? UiState.Error)?.message
  if (streamErrorMessage != null) {
    ErrorRecoveryScreen(
      message = streamErrorMessage,
      onRetry = viewModel::retryStreams,
      onSignOut = handleSignOut,
    )
    return
  }

  val currentCircle = activeCircle
  if (currentCircle == null) {
    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
      CircleOnboardingScreen(
        isBusy = isBusy,
        onCreateCircle = { name, radius -> viewModel.createBlockCircle(name, radius) },
        onJoinCircle = { code -> viewModel.joinBlockCircle(code) },
        onSignOut = handleSignOut,
        modifier = Modifier.padding(innerPadding),
      )
    }
    return
  }

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  var showCreateSheet by remember { mutableStateOf(false) }
  var showBlockSwitcher by remember { mutableStateOf(false) }

  val navItems =
    listOf(
      Triple(ROUTE_FEED, stringResource(id = R.string.nav_feed), Pair(Icons.Filled.Build, Icons.Outlined.Build)),
      Triple(ROUTE_ESCROW, stringResource(id = R.string.nav_escrow), Pair(Icons.Filled.Lock, Icons.Outlined.Lock)),
      Triple(ROUTE_TRUST, stringResource(id = R.string.nav_trust), Pair(Icons.Filled.VerifiedUser, Icons.Outlined.VerifiedUser)),
    )

  val navigateToRoute: (String) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      contentWindowInsets = WindowInsets.safeDrawing,
      floatingActionButton = {
        val isOnFeed = currentDestination?.hierarchy?.any { it.route == ROUTE_FEED } == true
        if (isOnFeed) {
          ExtendedFloatingActionButton(
            onClick = { showCreateSheet = true },
            icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(id = R.string.post_listing_fab)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.testTag("post_listing_fab"),
          )
        }
      },
      bottomBar = {
        if (!useRail) {
          NavigationBar {
            navItems.forEach { (route, label, icons) ->
              val selected = currentDestination?.hierarchy?.any { it.route == route } == true
              NavigationBarItem(
                selected = selected,
                onClick = { navigateToRoute(route) },
                icon = {
                  Icon(
                    imageVector = if (selected) icons.first else icons.second,
                    contentDescription = label,
                  )
                },
                label = { Text(label) },
                modifier = Modifier.testTag("nav_item_$route"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useRail) {
          NavigationRail {
            navItems.forEach { (route, label, icons) ->
              val selected = currentDestination?.hierarchy?.any { it.route == route } == true
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToRoute(route) },
                icon = {
                  Icon(
                    imageVector = if (selected) icons.first else icons.second,
                    contentDescription = label,
                  )
                },
                label = { Text(label) },
                modifier = Modifier.testTag("rail_item_$route"),
              )
            }
          }
        }

        NavHost(
          navController = navController,
          startDestination = ROUTE_FEED,
          modifier = Modifier.weight(1f).fillMaxSize(),
        ) {
          composable(ROUTE_FEED) {
            FeedScreen(
              currentUserId = currentUserId,
              profile = profile,
              activeCircle = currentCircle,
              listings = filteredListings,
              proAlertMatches = proAlertMatches,
              filters = filters,
              isBusy = isBusy,
              showCreateSheet = showCreateSheet,
              onDismissCreateSheet = { showCreateSheet = false },
              onOpenCreateSheet = { showCreateSheet = true },
              onOpenBlockSwitcher = { showBlockSwitcher = true },
              onKindFilterChange = viewModel::setKindFilter,
              onCategoryToggle = viewModel::toggleCategoryFilter,
              onMaxDistanceChange = viewModel::setMaxDistanceMiles,
              onSearchQueryChange = viewModel::setSearchQuery,
              onCreateListing = { kind, title, desc, cat, rate, deposit, dist, instant, photo ->
                viewModel.createListing(
                  circle = currentCircle,
                  profile = profile,
                  kind = kind,
                  title = title,
                  description = desc,
                  category = cat,
                  rateCents = rate,
                  depositCents = deposit,
                  distanceMiles = dist,
                  instantPickup = instant,
                  photoUri = photo,
                ) {
                  showCreateSheet = false
                }
              },
              onUpdateListing = { id, title, desc, cat, rate, deposit, dist, instant, photo, onDone ->
                viewModel.updateListing(
                  listingId = id,
                  title = title,
                  description = desc,
                  category = cat,
                  rateCents = rate,
                  depositCents = deposit,
                  distanceMiles = dist,
                  instantPickup = instant,
                  photoUri = photo,
                  onDone = onDone,
                )
              },
              onTogglePause = viewModel::toggleListingPause,
              onDeleteListing = viewModel::deleteListing,
              onBookListing = { listing, notes, onDone ->
                viewModel.bookOrClaimListing(listing, profile, notes, onDone)
              },
              onOpenProAlertsTab = { navigateToRoute(ROUTE_TRUST) },
              onShowFeedback = viewModel::showFeedback,
            )
          }

          composable(ROUTE_ESCROW) {
            EscrowScreen(
              currentUserId = currentUserId,
              bookings = bookings,
              isBusy = isBusy,
              onAdvanceStatus = viewModel::advanceBooking,
            )
          }

          composable(ROUTE_TRUST) {
            TrustHubScreen(
              currentUserId = currentUserId,
              profile =
                profile
                  ?: UserProfile(
                    uid = currentUserId,
                    displayName = initialDisplayName,
                    activeCircleId = currentCircle.id,
                    activeCircleName = currentCircle.name,
                    activeInviteCode = currentCircle.inviteCode,
                  ),
              activeCircle = currentCircle,
              circlesCount = circles.size,
              bookings = bookings,
              proAlertMatches = proAlertMatches,
              isBusy = isBusy,
              onSaveProAlerts = viewModel::updateProAlerts,
              onOpenBlockSwitcher = { showBlockSwitcher = true },
              onInspectProMatch = { navigateToRoute(ROUTE_FEED) },
              onShowFeedback = viewModel::showFeedback,
              onSignOut = handleSignOut,
            )
          }
        }
      }
    }
  }

  if (showBlockSwitcher) {
    BlockSwitcherSheet(
      currentUserId = currentUserId,
      activeCircle = currentCircle,
      circles = circles,
      isBusy = isBusy,
      onDismiss = { showBlockSwitcher = false },
      onSelectCircle = { circle ->
        viewModel.switchActiveCircle(circle) { showBlockSwitcher = false }
      },
      onCreateCircle = { name, radius ->
        viewModel.createBlockCircle(name, radius) { showBlockSwitcher = false }
      },
      onJoinCircle = { code ->
        viewModel.joinBlockCircle(code) { showBlockSwitcher = false }
      },
      onLeaveCircle = { circle ->
        viewModel.leaveBlockCircle(circle, circles) { showBlockSwitcher = false }
      },
      onCopyCode = { code ->
        copyInviteCodeToClipboard(context, code, viewModel::showFeedback)
      },
    )
  }
}

@Composable
private fun ErrorRecoveryScreen(
  message: String,
  onRetry: () -> Unit,
  onSignOut: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    CraftCard(modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.CloudOff,
          contentDescription = "Connection error",
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(40.dp),
        )
        Text(
          text = "Could not sync your block ledger",
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.weight(1f).testTag("error_sign_out_button"),
          ) {
            Text(stringResource(id = R.string.sign_out))
          }
          Button(
            onClick = onRetry,
            modifier = Modifier.weight(1f).testTag("error_retry_button"),
          ) {
            Text(stringResource(id = R.string.retry_sync))
          }
        }
      }
    }
  }
}
