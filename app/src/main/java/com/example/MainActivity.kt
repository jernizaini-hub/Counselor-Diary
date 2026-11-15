package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Security
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.crypto.AndroidVaultKeyCache
import com.example.data.ai.CounselorAiService
import com.example.data.firebase.configureAppCheck
import com.example.data.repository.CounselorRepository
import com.example.model.ActivityCategory
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LegacyPinMigrationScreen
import com.example.ui.screens.RecordEditorScreen
import com.example.ui.screens.RecordsListScreen
import com.example.ui.screens.SecurityBackupScreen
import com.example.ui.theme.DiariKaunselorTheme
import com.example.ui.viewmodel.CounselorViewModel
import com.example.ui.viewmodel.RecordsUiState
import com.example.ui.viewmodel.VaultState
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
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
private fun rememberFirebaseAuthOrNull(): FirebaseAuth? {
  val context = LocalContext.current
  return remember(context) {
    try {
      if (FirebaseApp.getApps(context).isEmpty()) {
        FirebaseApp.initializeApp(context)
      }
      if (FirebaseApp.getApps(context).isNotEmpty()) Firebase.auth else null
    } catch (_: Exception) {
      null
    }
  }
}

@Composable
fun AppNavigation(auth: FirebaseAuth? = rememberFirebaseAuthOrNull()) {
  if (auth == null) {
    MissingFirebaseConfigScreen()
    return
  }

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
private fun MissingFirebaseConfigScreen() {
  Box(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.WarningAmber,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.height(48.dp),
      )
      Text(
        text = "Konfigurasi Firebase Diperlukan",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      Text(
        text =
          "Binaan APK ini tidak mengandungi fail konfigurasi Firebase (google-services.json), biasanya apabila dibina melalui GitHub tanpa secret GOOGLE_SERVICES_JSON.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "Cara Membaiki:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text =
              "1. Tambah secret bernama GOOGLE_SERVICES_JSON (isi fail app/google-services.json) dalam GitHub Repository Settings -> Secrets and variables -> Actions, kemudian bina semula APK.\n2. Atau jana/pasang binaan terus melalui menu Publish di AI Studio atau Android Studio.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
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
              vaultKeyCache = AndroidVaultKeyCache(app),
            )
          }
        },
    )

  val vaultState by viewModel.vaultState.collectAsStateWithLifecycle()
  val vaultBusy by viewModel.vaultBusy.collectAsStateWithLifecycle()
  val vaultActionError by viewModel.vaultActionError.collectAsStateWithLifecycle()

  fun performSignOut() {
    viewModel.clearSessionKey()
    GoogleAuthHelper.signOut(credentialManager, scope)
  }

  when (val state = vaultState) {
    VaultState.Loading -> {
      var slowConnectionHint by remember { mutableStateOf(false) }
      LaunchedEffect(Unit) {
        delay(4_000L)
        slowConnectionHint = true
      }
      Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          CircularProgressIndicator()
          Text(
            text = "Menyediakan ruang kerja tersulit E2EE...",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
          )
          if (slowConnectionHint) {
            Text(
              text = "Sedang menyegerak konfigurasi akaun daripada awan. Sila tunggu sebentar...",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = { performSignOut() }) {
              Text("Log Keluar / Tukar Akaun")
            }
          }
        }
      }
    }

    is VaultState.LegacyPinMigration -> {
      LegacyPinMigrationScreen(
        userEmail = counselorEmail,
        isBusy = vaultBusy,
        errorMessage = vaultActionError,
        onMigrate = { oldPin -> viewModel.migrateLegacyPinToAutoE2ee(state.config, oldPin) },
        onSignOut = { performSignOut() },
      )
    }

    is VaultState.Error -> {
      Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Ralat Sambungan Ruang Kerja",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
          )
          Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Button(
            onClick = { viewModel.retryVaultSetup() },
            modifier = Modifier.fillMaxWidth(0.75f).testTag("retry_vault_button"),
          ) {
            Text("Cuba Semula")
          }
          OutlinedButton(
            onClick = { performSignOut() },
            modifier = Modifier.fillMaxWidth(0.75f),
          ) {
            Text("Log Keluar & Tukar Akaun")
          }
        }
      }
    }

    is VaultState.Unlocked -> {
      UnlockedCounselorShell(
        viewModel = viewModel,
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
          text = { Text("Catatan Baharu", fontWeight = FontWeight.Bold) },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.testTag("new_record_fab"),
        )
      }
    },
    bottomBar = {
      if (isTopLevel && !useNavRail) {
        NavigationBar(
          windowInsets = WindowInsets.navigationBars,
          containerColor = MaterialTheme.colorScheme.surface,
          tonalElevation = 3.dp,
        ) {
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
              label = {
                Text(
                  text = label,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
              },
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
            cloudSyncState = cloudSyncState,
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
}
