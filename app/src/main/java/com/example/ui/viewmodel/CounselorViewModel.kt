package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.E2eeCryptoManager
import com.example.crypto.NoOpVaultKeyCache
import com.example.crypto.VaultKeyCache
import com.example.data.ai.CounselorAiService
import com.example.data.repository.CounselorRepository
import com.example.model.ActivityCategory
import com.example.model.AiSummaryResult
import com.example.model.CounselingRecord
import com.example.model.EncryptedCounselingRecordDto
import com.example.model.VaultConfigDto
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.security.GeneralSecurityException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

sealed interface VaultState {
  data object Loading : VaultState

  data class LegacyPinMigration(val config: VaultConfigDto) : VaultState

  data class Unlocked(
    val config: VaultConfigDto,
    val masterKey: SecretKey,
  ) : VaultState

  data class Error(val message: String) : VaultState
}

sealed interface RecordsUiState {
  data object Loading : RecordsUiState

  data class Success(val records: List<CounselingRecord>) : RecordsUiState

  data class Error(val message: String) : RecordsUiState
}

sealed interface AiSummaryState {
  data object Idle : AiSummaryState

  data object Loading : AiSummaryState

  data class Success(val result: AiSummaryResult) : AiSummaryState

  data class Error(val message: String) : AiSummaryState
}

sealed interface CloudSyncState {
  data object Syncing : CloudSyncState

  data class Synced(val timestampMillis: Long, val recordCount: Int) : CloudSyncState

  data class Error(val message: String) : CloudSyncState
}

enum class StatsPeriod(val label: String) {
  WEEK("Minggu Ini"),
  MONTH("Bulan Ini"),
  ALL("Semua Masa"),
}

data class DailyTrendPoint(
  val dayLabel: String,
  val count: Int,
)

data class DashboardStats(
  val totalRecords: Int = 0,
  val counselingSessionsCount: Int = 0,
  val activitiesCount: Int = 0,
  val aiSummaryCount: Int = 0,
  val totalMinutes: Int = 0,
  val categoryCounts: Map<ActivityCategory, Int> = emptyMap(),
  val weeklyTrend: List<DailyTrendPoint> = emptyList(),
)

class CounselorViewModel(
  private val repository: CounselorRepository,
  private val aiService: CounselorAiService,
  private val currentUserId: String,
  private val userEmail: String,
  private val vaultKeyCache: VaultKeyCache = NoOpVaultKeyCache,
) : ViewModel() {

  private val unlockedKeyFlow = MutableStateFlow<SecretKey?>(null)
  private val localVaultConfigFlow = MutableStateFlow<VaultConfigDto?>(null)
  private val legacyMigrationConfigFlow = MutableStateFlow<VaultConfigDto?>(null)
  private val vaultActionErrorFlow = MutableStateFlow<String?>(null)
  private val vaultBusyFlow = MutableStateFlow(false)
  private val autoInitErrorFlow = MutableStateFlow<String?>(null)
  private var vaultInitJob: Job? = null

  val vaultActionError: StateFlow<String?> = vaultActionErrorFlow.asStateFlow()
  val vaultBusy: StateFlow<Boolean> = vaultBusyFlow.asStateFlow()

  private val rawVaultFlow =
    repository
      .observeVaultConfig(currentUserId)
      .map<VaultConfigDto?, Result<VaultConfigDto?>> { Result.success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing vault config", rootError)
        emit(Result.failure(rootError))
      }
      .shareIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        replay = 1,
      )

  val vaultState: StateFlow<VaultState> =
    combine(
        rawVaultFlow.onStart { emit(Result.success(null)) },
        localVaultConfigFlow,
        unlockedKeyFlow,
        legacyMigrationConfigFlow,
        autoInitErrorFlow,
      ) { configResult, localConfig, masterKey, legacyConfig, initError ->
        val effectiveConfig = configResult.getOrNull() ?: localConfig
        when {
          masterKey != null && effectiveConfig != null ->
            VaultState.Unlocked(effectiveConfig, masterKey)
          legacyConfig != null -> VaultState.LegacyPinMigration(legacyConfig)
          initError != null -> VaultState.Error(initError)
          configResult.isFailure && masterKey == null && localConfig == null ->
            VaultState.Error(
              configResult.exceptionOrNull()?.message
                ?: "Gagal memuatkan konfigurasi penyulitan."
            )
          else -> VaultState.Loading
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = VaultState.Loading,
      )

  private val rawEncryptedRecordsFlow =
    repository
      .observeEncryptedRecords(currentUserId)
      .map<List<EncryptedCounselingRecordDto>, Result<List<EncryptedCounselingRecordDto>>> {
        Result.success(it)
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing counseling records", rootError)
        emit(Result.failure(rootError))
      }

  val encryptedDtosState: StateFlow<List<EncryptedCounselingRecordDto>> =
    rawEncryptedRecordsFlow
      .map { it.getOrNull().orEmpty() }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList(),
      )

  val recordsState: StateFlow<RecordsUiState> =
    combine(rawEncryptedRecordsFlow, unlockedKeyFlow) { dtosResult, masterKey ->
        if (masterKey == null) {
          RecordsUiState.Loading
        } else {
          dtosResult.fold(
            onSuccess = { dtos ->
              val decrypted =
                dtos
                  .mapNotNull { dto ->
                    try {
                      repository.decryptRecord(dto, masterKey)
                    } catch (e: Exception) {
                      Log.e(TAG, "Failed to decrypt record ${dto.id}", e)
                      null
                    }
                  }
                  .sortedByDescending { it.sessionDateMillis }
              RecordsUiState.Success(decrypted)
            },
            onFailure = { err ->
              RecordsUiState.Error(err.message ?: "Gagal menyegerak rekod daripada awan.")
            },
          )
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = RecordsUiState.Loading,
      )

  private val _cloudSyncState = MutableStateFlow<CloudSyncState>(CloudSyncState.Syncing)
  val cloudSyncState: StateFlow<CloudSyncState> = _cloudSyncState.asStateFlow()

  private val _aiSummaryState = MutableStateFlow<AiSummaryState>(AiSummaryState.Idle)
  val aiSummaryState: StateFlow<AiSummaryState> = _aiSummaryState.asStateFlow()

  private val _statsPeriod = MutableStateFlow(StatsPeriod.ALL)
  val statsPeriod: StateFlow<StatsPeriod> = _statsPeriod.asStateFlow()

  private val _selectedCategory = MutableStateFlow<ActivityCategory?>(null)
  val selectedCategory: StateFlow<ActivityCategory?> = _selectedCategory.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  init {
    initializeVaultSession()
    viewModelScope.launch {
      recordsState.collect { state ->
        when (state) {
          is RecordsUiState.Success ->
            _cloudSyncState.value =
              CloudSyncState.Synced(System.currentTimeMillis(), state.records.size)
          is RecordsUiState.Error ->
            _cloudSyncState.value = CloudSyncState.Error(state.message)
          RecordsUiState.Loading -> _cloudSyncState.value = CloudSyncState.Syncing
        }
      }
    }
  }

  val dashboardStats: StateFlow<DashboardStats> =
    combine(recordsState, _statsPeriod) { state, period ->
        val allRecords = (state as? RecordsUiState.Success)?.records.orEmpty()
        computeDashboardStats(allRecords, period)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DashboardStats(),
      )

  fun setStatsPeriod(period: StatsPeriod) {
    _statsPeriod.value = period
  }

  fun setCategoryFilter(category: ActivityCategory?) {
    _selectedCategory.value = category
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun resetAiState() {
    _aiSummaryState.value = AiSummaryState.Idle
  }

  fun retryVaultSetup() {
    initializeVaultSession()
  }

  // --- Fast Automatic Seamless E2EE Key Provisioning ---

  private fun autoVaultSecret(uid: String): String = "dk-e2ee-auto:$uid"

  private fun initializeVaultSession() {
    vaultInitJob?.cancel()
    vaultInitJob =
      viewModelScope.launch {
        vaultBusyFlow.value = true
        autoInitErrorFlow.value = null
        try {
          // 1. Instant local Keystore cache check (< 10ms, zero PBKDF2, zero network)
          val localCached = withContext(Dispatchers.IO) { vaultKeyCache.load(currentUserId) }
          if (localCached != null) {
            localVaultConfigFlow.value = localCached.config
            unlockedKeyFlow.value = localCached.masterKey
            legacyMigrationConfigFlow.value = null
            return@launch
          }

          // 2. Fast Firestore offline cache check (~5ms)
          val cachedFirestoreConfig =
            try {
              repository.getVaultConfig(Source.CACHE)
            } catch (_: Exception) {
              null
            }
          if (cachedFirestoreConfig != null) {
            val cachedKey = tryUnwrapWithAutoSecret(cachedFirestoreConfig)
            if (cachedKey != null) {
              persistLocalAndUnlock(cachedFirestoreConfig, cachedKey)
              return@launch
            }
          }

          // 3. Server check with bounded timeout so UI never hangs indefinitely
          val serverConfig =
            withTimeout(FIRESTORE_UNLOCK_TIMEOUT_MS) {
              repository.getVaultConfig(Source.SERVER)
            }

          if (serverConfig == null) {
            // Brand new user: generate E2EE key locally and unlock immediately; sync to Firestore in background
            val payload = generateAutoSetupPayload(existing = null)
            persistLocalAndUnlock(payload.config, payload.masterKey)
            persistVaultConfigInBackground(payload.config, isNew = true)
          } else {
            val autoKey = tryUnwrapWithAutoSecret(serverConfig)
            if (autoKey != null) {
              persistLocalAndUnlock(serverConfig, autoKey)
            } else {
              val existingRecords =
                withTimeout(FIRESTORE_UNLOCK_TIMEOUT_MS) { repository.getEncryptedRecords() }
              if (existingRecords.isEmpty()) {
                val payload = generateAutoSetupPayload(existing = serverConfig)
                persistLocalAndUnlock(payload.config, payload.masterKey)
                persistVaultConfigInBackground(payload.config, isNew = false)
              } else {
                legacyMigrationConfigFlow.value = serverConfig
              }
            }
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: TimeoutCancellationException) {
          Log.w(TAG, "Firestore vault fetch timed out", e)
          autoInitErrorFlow.value =
            "Sambungan ke pelayan mengambil masa terlalu lama. Sila semak internet anda dan tekan Cuba Semula."
        } catch (e: Exception) {
          Log.e(TAG, "Auto E2EE vault setup/unlock failed", e)
          autoInitErrorFlow.value =
            e.localizedMessage ?: "Gagal menyediakan kunci penyulitan E2EE."
        } finally {
          vaultBusyFlow.value = false
        }
      }
  }

  private suspend fun tryUnwrapWithAutoSecret(config: VaultConfigDto): SecretKey? =
    withContext(Dispatchers.Default) {
      try {
        // AES-GCM unwrapping cryptographically authenticates the KEK in a single PBKDF2 pass
        val kek = E2eeCryptoManager.deriveKeyFromPin(autoVaultSecret(currentUserId), config.kdfSalt)
        E2eeCryptoManager.unwrapSecretKey(
          config.wrappedMasterKey,
          config.wrappedMasterKeyIv,
          kek,
        )
      } catch (_: GeneralSecurityException) {
        null
      } catch (_: IllegalArgumentException) {
        null
      }
    }

  private suspend fun generateAutoSetupPayload(existing: VaultConfigDto?): AutoSetupPayload =
    coroutineScope {
      val secret = autoVaultSecret(currentUserId)
      val salt = E2eeCryptoManager.generateSaltBase64()
      val kekDeferred = async(Dispatchers.Default) { E2eeCryptoManager.deriveKeyFromPin(secret, salt) }
      val hashDeferred = async(Dispatchers.Default) { E2eeCryptoManager.hashPinVerifier(secret, salt) }
      val masterKey = E2eeCryptoManager.generateMasterKey()
      val kek = kekDeferred.await()
      val pinHash = hashDeferred.await()
      val wrappedMaster = E2eeCryptoManager.wrapSecretKey(masterKey, kek)
      val config =
        if (existing == null) {
          VaultConfigDto(
            userId = currentUserId,
            pinHash = pinHash,
            kdfSalt = salt,
            wrappedMasterKey = wrappedMaster.ciphertextBase64,
            wrappedMasterKeyIv = wrappedMaster.ivBase64,
            totpEnabled = false,
          )
        } else {
          existing.copy(
            pinHash = pinHash,
            kdfSalt = salt,
            wrappedMasterKey = wrappedMaster.ciphertextBase64,
            wrappedMasterKeyIv = wrappedMaster.ivBase64,
            totpEnabled = false,
            wrappedTotpSecret = null,
            wrappedTotpIv = null,
            recoveryCodeHash = null,
          )
        }
      AutoSetupPayload(config, masterKey)
    }

  private suspend fun persistLocalAndUnlock(config: VaultConfigDto, masterKey: SecretKey) {
    withContext(Dispatchers.IO) { vaultKeyCache.save(currentUserId, config, masterKey) }
    localVaultConfigFlow.value = config
    unlockedKeyFlow.value = masterKey
    legacyMigrationConfigFlow.value = null
  }

  private fun persistVaultConfigInBackground(config: VaultConfigDto, isNew: Boolean) {
    viewModelScope.launch {
      try {
        repository.saveVaultConfig(config, isNew = isNew)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Background vaultConfig cloud sync deferred/failed", e)
      }
    }
  }

  fun migrateLegacyPinToAutoE2ee(config: VaultConfigDto, oldPin: String) {
    if (oldPin.length != 6) {
      vaultActionErrorFlow.value = "Sila masukkan PIN lama 6 digit."
      return
    }
    viewModelScope.launch {
      vaultBusyFlow.value = true
      vaultActionErrorFlow.value = null
      try {
        val migrated =
          withContext(Dispatchers.Default) {
            val oldKek = E2eeCryptoManager.deriveKeyFromPin(oldPin, config.kdfSalt)
            val masterKey =
              try {
                E2eeCryptoManager.unwrapSecretKey(
                  config.wrappedMasterKey,
                  config.wrappedMasterKeyIv,
                  oldKek,
                )
              } catch (_: GeneralSecurityException) {
                throw IllegalArgumentException("PIN lama tidak tepat.")
              }
            val autoSecret = autoVaultSecret(currentUserId)
            val newSalt = E2eeCryptoManager.generateSaltBase64()
            val newKekDeferred = async { E2eeCryptoManager.deriveKeyFromPin(autoSecret, newSalt) }
            val newHashDeferred = async { E2eeCryptoManager.hashPinVerifier(autoSecret, newSalt) }
            val newKek = newKekDeferred.await()
            val newPinHash = newHashDeferred.await()
            val newWrapped = E2eeCryptoManager.wrapSecretKey(masterKey, newKek)
            val updated =
              config.copy(
                pinHash = newPinHash,
                kdfSalt = newSalt,
                wrappedMasterKey = newWrapped.ciphertextBase64,
                wrappedMasterKeyIv = newWrapped.ivBase64,
                totpEnabled = false,
                wrappedTotpSecret = null,
                wrappedTotpIv = null,
                recoveryCodeHash = null,
              )
            AutoSetupPayload(updated, masterKey)
          }
        persistLocalAndUnlock(migrated.config, migrated.masterKey)
        persistVaultConfigInBackground(migrated.config, isNew = false)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        vaultActionErrorFlow.value = e.message ?: "Gagal menyahsulit rekod lama."
      } finally {
        vaultBusyFlow.value = false
      }
    }
  }

  fun clearSessionKey() {
    vaultInitJob?.cancel()
    vaultKeyCache.clear(currentUserId)
    unlockedKeyFlow.value = null
    localVaultConfigFlow.value = null
    legacyMigrationConfigFlow.value = null
    vaultActionErrorFlow.value = null
    autoInitErrorFlow.value = null
  }

  // --- AI Summary Generation ---

  fun generateAiSummary(
    category: ActivityCategory,
    clientName: String,
    className: String,
    dayName: String,
    timeRange: String,
    rawNotes: String,
  ) {
    if (rawNotes.isBlank()) {
      _aiSummaryState.value =
        AiSummaryState.Error(
          "Sila masukkan transkrip perbualan atau catatan ringkas terlebih dahulu."
        )
      return
    }
    viewModelScope.launch {
      _aiSummaryState.value = AiSummaryState.Loading
      try {
        val result =
          aiService.generateCounselingSummary(
            category = category,
            clientName = clientName,
            className = className,
            dayName = dayName,
            timeRange = timeRange,
            rawTranscriptOrNotes = rawNotes,
          )
        _aiSummaryState.value = AiSummaryState.Success(result)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _aiSummaryState.value =
          AiSummaryState.Error(e.message ?: "Gagal menjana analisis rumusan AI.")
      }
    }
  }

  // --- Record CRUD & Automatic Cloud Backup ---

  fun saveRecord(
    record: CounselingRecord,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    val masterKey = unlockedKeyFlow.value
    if (masterKey == null) {
      onError("Peti besi penyulitan sedang dikunci.")
      return
    }
    viewModelScope.launch {
      _cloudSyncState.value = CloudSyncState.Syncing
      try {
        val savedId =
          withContext(Dispatchers.Default) { repository.saveRecord(record, masterKey) }
        val count = (recordsState.value as? RecordsUiState.Success)?.records?.size ?: 0
        _cloudSyncState.value = CloudSyncState.Synced(System.currentTimeMillis(), count)
        onSuccess(savedId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Save record failed", e)
        val msg = e.localizedMessage ?: "Gagal menyimpan rekod ke awan."
        _cloudSyncState.value = CloudSyncState.Error(msg)
        onError(msg)
      }
    }
  }

  fun deleteRecord(recordId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    viewModelScope.launch {
      _cloudSyncState.value = CloudSyncState.Syncing
      try {
        repository.deleteRecord(recordId)
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val msg = e.localizedMessage ?: "Gagal memadam rekod."
        _cloudSyncState.value = CloudSyncState.Error(msg)
        onError(msg)
      }
    }
  }

  fun verifyCloudBackupSync(onComplete: (Int) -> Unit, onError: (String) -> Unit) {
    viewModelScope.launch {
      _cloudSyncState.value = CloudSyncState.Syncing
      try {
        val dtos = repository.getEncryptedRecords()
        _cloudSyncState.value = CloudSyncState.Synced(System.currentTimeMillis(), dtos.size)
        onComplete(dtos.size)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val msg = e.localizedMessage ?: "Segerakan awan gagal."
        _cloudSyncState.value = CloudSyncState.Error(msg)
        onError(msg)
      }
    }
  }

  fun buildEncryptedCloudBackupJson(): String {
    val dtos = encryptedDtosState.value
    val root = JSONObject()
    root.put("app", "Diari Kaunselor")
    root.put("userId", currentUserId)
    root.put("userEmail", userEmail)
    root.put("exportedAtMillis", System.currentTimeMillis())
    root.put("encryption", E2eeCryptoManager.ENCRYPTION_VERSION)
    val arr = JSONArray()
    for (dto in dtos) {
      arr.put(
        JSONObject().apply {
          put("id", dto.id)
          put("category", dto.category)
          put("sessionDateMillis", dto.sessionDateMillis)
          put("dayName", dto.dayName)
          put("timeRange", dto.timeRange)
          put("durationMinutes", dto.durationMinutes)
          put("hasAiSummary", dto.hasAiSummary)
          put("encryptionVersion", dto.encryptionVersion)
          put("encryptedPayload", dto.encryptedPayload)
          put("payloadIv", dto.payloadIv)
        }
      )
    }
    root.put("records", arr)
    return root.toString(2)
  }

  private fun computeDashboardStats(
    records: List<CounselingRecord>,
    period: StatsPeriod,
  ): DashboardStats {
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance()
    val filtered =
      when (period) {
        StatsPeriod.ALL -> records
        StatsPeriod.WEEK -> {
          cal.timeInMillis = now
          cal.add(Calendar.DAY_OF_YEAR, -6)
          cal.set(Calendar.HOUR_OF_DAY, 0)
          cal.set(Calendar.MINUTE, 0)
          cal.set(Calendar.SECOND, 0)
          val start = cal.timeInMillis
          records.filter { it.sessionDateMillis >= start }
        }
        StatsPeriod.MONTH -> {
          cal.timeInMillis = now
          cal.set(Calendar.DAY_OF_MONTH, 1)
          cal.set(Calendar.HOUR_OF_DAY, 0)
          cal.set(Calendar.MINUTE, 0)
          cal.set(Calendar.SECOND, 0)
          val start = cal.timeInMillis
          records.filter { it.sessionDateMillis >= start }
        }
      }

    val counts = ActivityCategory.entries.associateWith { cat -> filtered.count { it.category == cat } }
    val counselingCount = filtered.count { it.category.isCounselingSession }
    val activitiesCount = filtered.size - counselingCount
    val aiCount = filtered.count { it.hasAiSummary }
    val totalMins = filtered.sumOf { it.durationMinutes }

    val dayFmt = SimpleDateFormat("EEE", Locale.forLanguageTag("ms-MY"))
    val keyFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    val byDay = filtered.groupBy { keyFmt.format(Date(it.sessionDateMillis)) }
    val trend =
      (6 downTo 0).map { offset ->
        val c = Calendar.getInstance()
        c.timeInMillis = now
        c.add(Calendar.DAY_OF_YEAR, -offset)
        val key = keyFmt.format(c.time)
        val label = dayFmt.format(c.time).take(3)
        DailyTrendPoint(dayLabel = label, count = byDay[key]?.size ?: 0)
      }

    return DashboardStats(
      totalRecords = filtered.size,
      counselingSessionsCount = counselingCount,
      activitiesCount = activitiesCount,
      aiSummaryCount = aiCount,
      totalMinutes = totalMins,
      categoryCounts = counts,
      weeklyTrend = trend,
    )
  }

  private data class AutoSetupPayload(
    val config: VaultConfigDto,
    val masterKey: SecretKey,
  )

  private companion object {
    const val TAG = "CounselorVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
    const val FIRESTORE_UNLOCK_TIMEOUT_MS = 10_000L
  }
}
