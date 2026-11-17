package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination
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
import com.example.model.BlockCircle
import com.example.model.EscrowWorkflow
import com.example.model.UserProfile
import com.example.model.dataOrNull
import com.example.ui.components.ActionRow
import com.example.ui.components.BlockSwitcherSheet
import com.example.ui.components.CraftCard
import com.example.ui.components.copyInviteCodeToClipboard
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CircleOnboardingScreen
import com.example.ui.screens.EscrowScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.TrustHubScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

const val ROUTE_FEED = "feed"
const val ROUTE_ESCROW = "escrow"
const val ROUTE_TRUST = "trust"

private data class NavItem(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val badgeCount: Int = 0,
)

/**
 * Activity-scoped holder of "who is signed in" and of the ViewModels that belong to that session.
 *
 * Everything user-specific lives in [sessionStoreOwner] instead of the Activity's own
 * ViewModelStore, and that store is cleared the moment the signed-in user changes. Without this a
 * user's ViewModel (keyed by uid, but owned by the Activity) survived sign-out with its Firestore
 * listeners failing on PERMISSION_DENIED, and signing the same account back in showed that stale
 * error instead of the app.
 */
class SessionViewModel : ViewModel() {
  private val auth = Firebase.auth
  private val sessionStore = ViewModelStore()
  private var sessionUid: String? = auth.currentUser?.uid

  val sessionStoreOwner: ViewModelStoreOwner =
    object : ViewModelStoreOwner {
      override val viewModelStore: ViewModelStore
        get() = sessionStore
    }

  val user: StateFlow<FirebaseUser?> =
    auth
      .authStateFlow()
      .onEach { user ->
        if (user?.uid != sessionUid) {
          sessionStore.clear()
          sessionUid = user?.uid
        }
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, auth.currentUser)

  override fun onCleared() {
    sessionStore.clear()
  }
}

@Composable
fun BlockBenchAppRoot() {
  val session: SessionViewModel = viewModel()
  val user by session.user.collectAsStateWithLifecycle()
  val currentUser = user

  if (currentUser == null) {
    AuthScreen()
  } else {
    val displayName =
      currentUser.displayName?.takeIf { it.isNotBlank() }
        ?: currentUser.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
        ?: "Neighbor"
    key(currentUser.uid) {
      CompositionLocalProvider(LocalViewModelStoreOwner provides session.sessionStoreOwner) {
        AuthenticatedAppShell(
          currentUserId = currentUser.uid,
          initialDisplayName = displayName,
        )
      }
    }
  }
}

@Composable
private fun AuthenticatedAppShell(
  currentUserId: String,
  initialDisplayName: String,
) {
  val context = LocalContext.current
  val appContext = context.applicationContext
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: BlockBenchViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            BlockBenchViewModel(
              repository = BlockBenchRepository.create(appContext),
              currentUserId = currentUserId,
              initialDisplayName = initialDisplayName,
            )
          }
        },
    )

  val session by viewModel.sessionState.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(feedbackMessage) {
    val message = feedbackMessage
    if (message != null) {
      snackbarHostState.showSnackbar(message)
      viewModel.clearFeedback()
    }
  }

  val signOut: () -> Unit = { signOutUser(credentialManager = credentialManager, scope = scope) }

  when (val state = session) {
    SessionState.Loading ->
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("session_loading"))
      }
    is SessionState.Failed ->
      ErrorRecoveryScreen(
        message = state.message,
        onRetry = viewModel::retry,
        onSignOut = signOut,
      )
    is SessionState.Ready -> {
      val activeCircle = state.activeCircle
      if (activeCircle == null) {
        Scaffold(
          snackbarHost = { SnackbarHost(snackbarHostState) },
          contentWindowInsets = WindowInsets.safeDrawing,
        ) { innerPadding ->
          CircleOnboardingScreen(
            isBusy = isBusy,
            onCreateCircle = { name, radius -> viewModel.createBlockCircle(name, radius) },
            onJoinCircle = { code -> viewModel.joinBlockCircle(code) },
            onSignOut = signOut,
            // The Scaffold's padding already accounts for the system bars and the keyboard;
            // marking it consumed keeps anything inside from applying those insets again.
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
          )
        }
      } else {
        BlockShell(
          viewModel = viewModel,
          currentUserId = currentUserId,
          profile =
            state.profile
              ?: UserProfile(
                uid = currentUserId,
                displayName = initialDisplayName,
                activeCircleId = activeCircle.id,
                activeCircleName = activeCircle.name,
                activeInviteCode = activeCircle.inviteCode,
              ),
          circles = state.circles,
          activeCircle = activeCircle,
          isBusy = isBusy,
          snackbarHostState = snackbarHostState,
          onSignOut = signOut,
        )
      }
    }
  }
}

/** The three-tab app for a user who is in at least one block. */
@Composable
private fun BlockShell(
  viewModel: BlockBenchViewModel,
  currentUserId: String,
  profile: UserProfile,
  circles: List<BlockCircle>,
  activeCircle: BlockCircle,
  isBusy: Boolean,
  snackbarHostState: SnackbarHostState,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val feed by viewModel.listingsState.collectAsStateWithLifecycle()
  val visibleListings by viewModel.filteredListings.collectAsStateWithLifecycle()
  val proAlertMatches by viewModel.proAlertMatches.collectAsStateWithLifecycle()
  val ledger by viewModel.bookingsState.collectAsStateWithLifecycle()
  val ledgerTotals by viewModel.ledgerTotals.collectAsStateWithLifecycle()
  val filters by viewModel.filters.collectAsStateWithLifecycle()

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  var showCreateSheet by remember { mutableStateOf(false) }
  var showBlockSwitcher by remember { mutableStateOf(false) }

  // Requests waiting for this user's approval, handoffs to start or confirm: without a hint on
  // the tab, a listing owner has no way of noticing that a neighbour asked for their tool.
  val bookingsAwaitingMe =
    ledger.dataOrNull().orEmpty().count { EscrowWorkflow.awaitsActionFrom(it, currentUserId) }

  val navItems =
    listOf(
      NavItem(
        route = ROUTE_FEED,
        label = stringResource(id = R.string.nav_feed),
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build,
      ),
      NavItem(
        route = ROUTE_ESCROW,
        label = stringResource(id = R.string.nav_escrow),
        selectedIcon = Icons.Filled.Lock,
        unselectedIcon = Icons.Outlined.Lock,
        badgeCount = bookingsAwaitingMe,
      ),
      NavItem(
        route = ROUTE_TRUST,
        label = stringResource(id = R.string.nav_trust),
        selectedIcon = Icons.Filled.VerifiedUser,
        unselectedIcon = Icons.Outlined.VerifiedUser,
      ),
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
        if (currentDestination.isOn(ROUTE_FEED)) {
          val postLabel = stringResource(id = R.string.post_listing_fab)
          ExtendedFloatingActionButton(
            onClick = { showCreateSheet = true },
            // The button takes its name from the icon: Material drops the semantics of the text,
            // which is not there when an extended button is collapsed.
            icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = postLabel) },
            text = { Text(postLabel) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.testTag("post_listing_fab"),
          )
        }
      },
      bottomBar = {
        if (!useRail) {
          NavigationBar {
            navItems.forEach { item ->
              val selected = currentDestination.isOn(item.route)
              NavigationBarItem(
                selected = selected,
                onClick = { navigateToRoute(item.route) },
                icon = { NavItemIcon(item = item, selected = selected) },
                label = { Text(item.label) },
                modifier = Modifier.testTag("nav_item_${item.route}").announcingBadge(item),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)
      ) {
        if (useRail) {
          NavigationRail {
            navItems.forEach { item ->
              val selected = currentDestination.isOn(item.route)
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToRoute(item.route) },
                icon = { NavItemIcon(item = item, selected = selected) },
                label = { Text(item.label) },
                modifier = Modifier.testTag("rail_item_${item.route}").announcingBadge(item),
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
              activeCircle = activeCircle,
              feed = feed,
              visibleListings = visibleListings,
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
              onClearFilters = viewModel::clearFilters,
              onRetry = viewModel::retry,
              onCreateListing = { draft ->
                viewModel.createListing(draft) { showCreateSheet = false }
              },
              onUpdateListing = { listingId, draft, onDone ->
                viewModel.updateListing(listingId, draft, onDone)
              },
              onTogglePause = viewModel::toggleListingPause,
              onDeleteListing = viewModel::deleteListing,
              onBookListing = { listing, notes, onDone ->
                viewModel.bookOrClaimListing(listing, notes, onDone)
              },
              onOpenProAlertsTab = { navigateToRoute(ROUTE_TRUST) },
              onShowFeedback = viewModel::showFeedback,
            )
          }

          composable(ROUTE_ESCROW) {
            EscrowScreen(
              currentUserId = currentUserId,
              ledger = ledger,
              isBusy = isBusy,
              onAdvanceStatus = viewModel::advanceBooking,
              onRetry = viewModel::retry,
            )
          }

          composable(ROUTE_TRUST) {
            TrustHubScreen(
              profile = profile,
              totals = ledgerTotals,
              activeCircle = activeCircle,
              circlesCount = circles.size,
              proAlertMatches = proAlertMatches,
              isBusy = isBusy,
              onSaveProAlerts = viewModel::updateProAlerts,
              onOpenBlockSwitcher = { showBlockSwitcher = true },
              onInspectProMatch = { navigateToRoute(ROUTE_FEED) },
              onShowFeedback = viewModel::showFeedback,
              onSignOut = onSignOut,
            )
          }
        }
      }
    }
  }

  if (showBlockSwitcher) {
    BlockSwitcherSheet(
      currentUserId = currentUserId,
      activeCircle = activeCircle,
      circles = circles,
      isBusy = isBusy,
      onDismiss = { showBlockSwitcher = false },
      onSelectCircle = { circle ->
        viewModel.switchActiveCircle(circle) { showBlockSwitcher = false }
      },
      onCreateCircle = { name, radius ->
        viewModel.createBlockCircle(name, radius) { showBlockSwitcher = false }
      },
      onJoinCircle = { code -> viewModel.joinBlockCircle(code) { showBlockSwitcher = false } },
      onLeaveCircle = { circle ->
        viewModel.leaveBlockCircle(circle) { showBlockSwitcher = false }
      },
      onCopyCode = { code -> copyInviteCodeToClipboard(context, code, viewModel::showFeedback) },
    )
  }
}

private fun NavDestination?.isOn(route: String): Boolean =
  this?.hierarchy?.any { it.route == route } == true

/**
 * Says the badge count along with the tab's name. Material drops the semantics of a tab's icon,
 * badge included, whenever the tab has a label, so described on the icon the count would be seen
 * but never announced; it has to come from the tab itself.
 */
private fun Modifier.announcingBadge(item: NavItem): Modifier =
  if (item.badgeCount > 0) {
    semantics { contentDescription = "${item.label}, ${item.badgeCount} waiting for you" }
  } else {
    this
  }

@Composable
private fun NavItemIcon(item: NavItem, selected: Boolean) {
  val icon = if (selected) item.selectedIcon else item.unselectedIcon
  // The icon is decoration next to the label that names the tab.
  if (item.badgeCount > 0) {
    BadgedBox(badge = { Badge { Text(item.badgeCount.coerceAtMost(99).toString()) } }) {
      Icon(imageVector = icon, contentDescription = null)
    }
  } else {
    Icon(imageVector = icon, contentDescription = null)
  }
}

@Composable
private fun ErrorRecoveryScreen(
  message: String,
  onRetry: () -> Unit,
  onSignOut: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
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
          contentDescription = null,
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
        ActionRow(weights = listOf(1f, 1f)) {
          OutlinedButton(onClick = onSignOut, modifier = Modifier.testTag("error_sign_out_button")) {
            Text(stringResource(id = R.string.sign_out))
          }
          Button(onClick = onRetry, modifier = Modifier.testTag("error_retry_button")) {
            Text(stringResource(id = R.string.retry_sync))
          }
        }
      }
    }
  }
}
