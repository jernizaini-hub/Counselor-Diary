package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.GoogleAuthHelper
import com.example.data.ai.CounselorAiService
import com.example.data.firebase.configureAppCheck
import com.example.data.repository.CounselorRepository
import com.example.model.ActivityCategory
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.RecordEditorScreen
import com.example.ui.screens.RecordsListScreen
import com.example.ui.screens.SecurityBackupScreen
import com.example.ui.screens.VaultSetupScreen
import com.example.ui.screens.VaultUnlockScreen
import com.example.ui.theme.DiariKaunselorTheme
import com.example.ui.viewmodel.CounselorViewModel
import com.example.ui.viewmodel.RecordsUiState
import com.example.ui.viewmodel.VaultState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_RECORDS = "records"
const val ROUTE_SECURITY = "security"
const val ROUTE_EDITOR = "editor"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Mandatory Step 1: Configure App Check with launch intent before any UI or ViewModels
    configureAppCheck(this, intent)

    enableEdgeToEdge()
    setContent {
      DiariKaunselorTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          AppNavigation()
        }
      }
    }
  }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      AuthenticatedCounselorApp(
        currentUserId = user.uid,
        counselorName = user.displayName.orEmpty(),
        counselorEmail = user.email.orEmpty(),
      )
    }
  }
}

@Composable
private fun AuthenticatedCounselorApp(
  currentUserId: String,
  counselorName: String,
  counselorEmail: String,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: CounselorViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app =
              checkNotNull(this[APPLICATION_KEY]) {
                "APPLICATION_KEY missing from CreationExtras"
              }
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            CounselorViewModel(
              repository = CounselorRepository(db, Firebase.auth),
              aiService = CounselorAiService(),
              currentUserId = currentUserId,
              userEmail = counselorEmail,
            )
          }
        },
    )

  val vaultState by viewModel.vaultState.collectAsStateWithLifecycle()
  val vaultBusy by viewModel.vaultBusy.collectAsStateWithLifecycle()
  val vaultActionError by viewModel.vaultActionError.collectAsStateWithLifecycle()

  fun performSignOut() {
    viewModel.lockVault()
    GoogleAuthHelper.signOut(credentialManager, scope)
  }

  when (val state = vaultState) {
    VaultState.Loading -> {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator()
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "Memeriksa status peti besi keselamatan...",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }

    VaultState.NeedsSetup -> {
      VaultSetupScreen(
        userEmail = counselorEmail,
        isBusy = vaultBusy,
        errorMessage = vaultActionError,
        onSetupVault = { pin, enableTotp -> viewModel.setupVault(pin, enableTotp) },
        onSignOut = { performSignOut() },
      )
    }

    is VaultState.Locked -> {
      VaultUnlockScreen(
        config = state.config,
        userEmail = counselorEmail,
        isBusy = vaultBusy,
        errorMessage = vaultActionError,
        onUnlock = { pin, totp -> viewModel.unlockVault(state.config, pin, totp) },
        onSignOut = { performSignOut() },
      )
    }

    is VaultState.Error -> {
      Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "Ralat Sambungan Peti Besi",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
          Spacer(modifier = Modifier.height(16.dp))
          Button(onClick = { performSignOut() }) { Text("Log Keluar & Cuba Semula") }
        }
      }
    }

    is VaultState.Unlocked -> {
      UnlockedCounselorShell(
        viewModel = viewModel,
        unlockedVault = state,
        counselorName = counselorName,
        counselorEmail = counselorEmail,
        onSignOut = { performSignOut() },
      )
    }
  }
}

@Composable
private fun UnlockedCounselorShell(
  viewModel: CounselorViewModel,
  unlockedVault: VaultState.Unlocked,
  counselorName: String,
  counselorEmail: String,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route

  val recordsState by viewModel.recordsState.collectAsStateWithLifecycle()
  val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
  val activePeriod by viewModel.statsPeriod.collectAsStateWithLifecycle()
  val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()
  val aiSummaryState by viewModel.aiSummaryState.collectAsStateWithLifecycle()
  val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

  val records = (recordsState as? RecordsUiState.Success)?.records.orEmpty()
  var pendingNewCategory by remember { mutableStateOf(ActivityCategory.KAUNSELING_INDIVIDU) }

  val isTopLevel =
    currentRoute == ROUTE_DASHBOARD ||
      currentRoute == ROUTE_RECORDS ||
      currentRoute == ROUTE_SECURITY
  val useNavRail = LocalConfiguration.current.screenWidthDp >= 600

  fun navigateEditor(recordId: String?, initialCat: ActivityCategory = ActivityCategory.KAUNSELING_INDIVIDU) {
    pendingNewCategory = initialCat
    viewModel.resetAiState()
    val arg = recordId ?: "new"
    navController.navigate("$ROUTE_EDITOR/$arg")
  }

  val navItems =
    listOf(
      Triple(ROUTE_DASHBOARD, "Papan Pemuka", Pair(Icons.Filled.BarChart, Icons.Outlined.BarChart)),
      Triple(ROUTE_RECORDS, "Rekod & Diari", Pair(Icons.Filled.Book, Icons.Outlined.Book)),
      Triple(ROUTE_SECURITY, "Keselamatan", Pair(Icons.Filled.Security, Icons.Outlined.Security)),
    )

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    floatingActionButton = {
      if (isTopLevel && currentRoute != ROUTE_SECURITY) {
        ExtendedFloatingActionButton(
          onClick = { navigateEditor(null) },
          icon = { Icon(Icons.Filled.Add, contentDescription = null) },
          text = { Text("Catatan Baharu") },
          modifier = Modifier.testTag("new_record_fab"),
        )
      }
    },
    bottomBar = {
      if (isTopLevel && !useNavRail) {
        NavigationBar(windowInsets = WindowInsets.navigationBars) {
          navItems.forEach { (route, label, icons) ->
            val selected = currentRoute == route
            NavigationBarItem(
              selected = selected,
              onClick = {
                navController.navigate(route) {
                  popUpTo(ROUTE_DASHBOARD) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = {
                Icon(
                  imageVector = if (selected) icons.first else icons.second,
                  contentDescription = label,
                )
              },
              label = { Text(label) },
              modifier = Modifier.testTag("nav_$route"),
            )
          }
        }
      }
    },
  ) { innerPadding ->
    Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      if (isTopLevel && useNavRail) {
        NavigationRail {
          Spacer(modifier = Modifier.height(12.dp))
          navItems.forEach { (route, label, icons) ->
            val selected = currentRoute == route
            NavigationRailItem(
              selected = selected,
              onClick = {
                navController.navigate(route) {
                  popUpTo(ROUTE_DASHBOARD) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = {
                Icon(
                  imageVector = if (selected) icons.first else icons.second,
                  contentDescription = label,
                )
              },
              label = { Text(label) },
              modifier = Modifier.testTag("nav_rail_$route"),
            )
          }
        }
      }

      NavHost(
        navController = navController,
        startDestination = ROUTE_DASHBOARD,
        modifier = Modifier.weight(1f).fillMaxSize(),
      ) {
        composable(ROUTE_DASHBOARD) {
          DashboardScreen(
            counselorName = counselorName,
            stats = stats,
            activePeriod = activePeriod,
            cloudSyncState = cloudSyncState,
            recentRecords = records,
            onPeriodSelected = viewModel::setStatsPeriod,
            onQuickCreateCategory = { cat -> navigateEditor(null, cat) },
            onOpenRecord = { rec -> navigateEditor(rec.id) },
            onViewAllRecords = {
              navController.navigate(ROUTE_RECORDS) {
                popUpTo(ROUTE_DASHBOARD) { saveState = true }
                launchSingleTop = true
                restoreState = true
              }
            },
            onOpenSecurity = {
              navController.navigate(ROUTE_SECURITY) {
                popUpTo(ROUTE_DASHBOARD) { saveState = true }
                launchSingleTop = true
                restoreState = true
              }
            },
          )
        }

        composable(ROUTE_RECORDS) {
          RecordsListScreen(
            records = records,
            selectedCategory = selectedCategory,
            searchQuery = searchQuery,
            onCategorySelected = viewModel::setCategoryFilter,
            onSearchQueryChanged = viewModel::setSearchQuery,
            onOpenRecord = { rec -> navigateEditor(rec.id) },
            onCreateNew = { navigateEditor(null) },
          )
        }

        composable(ROUTE_SECURITY) {
          SecurityBackupScreen(
            userEmail = counselorEmail,
            vaultConfig = unlockedVault.config,
            totpSecret = unlockedVault.totpSecret,
            cloudSyncState = cloudSyncState,
            onToggleTotp = { enable ->
              viewModel.toggleTotpTwoFactor(enable) {
                Toast.makeText(
                  context,
                  if (enable) "Pengesahan TOTP 2FA diaktifkan."
                  else "Pengesahan TOTP 2FA dimatikan.",
                  Toast.LENGTH_SHORT,
                )
                  .show()
              }
            },
            onChangePin = viewModel::changeSecurityPin,
            onVerifyCloudSync = {
              viewModel.verifyCloudBackupSync(
                onComplete = { count ->
                  Toast.makeText(
                    context,
                    "Sandaran awan disahkan: $count rekod selamat di awan.",
                    Toast.LENGTH_SHORT,
                  )
                    .show()
                },
                onError = { err ->
                  Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                },
              )
            },
            onBuildEncryptedBackupJson = viewModel::buildEncryptedCloudBackupJson,
            onLockVault = viewModel::lockVault,
            onSignOut = onSignOut,
          )
        }

        composable(
          route = "$ROUTE_EDITOR/{recordId}",
          arguments = listOf(navArgument("recordId") { type = NavType.StringType }),
        ) { entry ->
          val recordId = entry.arguments?.getString("recordId")
          val existing =
            if (recordId == null || recordId == "new") null
            else records.firstOrNull { it.id == recordId }

          RecordEditorScreen(
            existingRecord = existing,
            initialCategory = pendingNewCategory,
            counselorName = counselorName,
            counselorEmail = counselorEmail,
            aiSummaryState = aiSummaryState,
            onGenerateAiSummary = viewModel::generateAiSummary,
            onResetAiState = viewModel::resetAiState,
            onSaveRecord = { rec, onResult ->
              viewModel.saveRecord(
                record = rec,
                onSuccess = { onResult(true) },
                onError = { msg ->
                  Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                  onResult(false)
                },
              )
            },
            onDeleteRecord = { id, onDone ->
              viewModel.deleteRecord(
                recordId = id,
                onSuccess = {
                  Toast.makeText(context, "Rekod berjaya dipadam.", Toast.LENGTH_SHORT).show()
                  onDone()
                },
                onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() },
              )
            },
            onBack = { navController.popBackStack() },
          )
        }
      }
    }
  }

  // One-time Recovery Code disclosure right after first Vault Setup
  unlockedVault.freshRecoveryCode?.let { recoveryCode ->
    AlertDialog(
      onDismissRequest = { viewModel.clearFreshRecoveryCode() },
      title = { Text("Simpan Kod Pemulihan Anda") },
      text = {
        Column {
          Text(
            text =
              "Peti besi penyulitan E2EE & 2FA anda kini aktif. Simpan kod pemulihan kecemasan ini di tempat selamat:",
            style = MaterialTheme.typography.bodyMedium,
          )
          Spacer(modifier = Modifier.height(12.dp))
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = recoveryCode,
              style = MaterialTheme.typography.titleMedium,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(14.dp),
            )
          }
          if (unlockedVault.totpSecret != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text =
                "Anda boleh menyemak kod TOTP 6-digit dan kunci Base32 pada bila-bila masa di tab Keselamatan.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      },
      confirmButton = {
        Button(onClick = { viewModel.clearFreshRecoveryCode() }) {
          Text("Saya Telah Simpan")
        }
      },
    )
  }
}
