package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.E2eeCryptoManager
import com.example.data.ai.CounselorAiService
import com.example.data.repository.CounselorRepository
import com.example.model.ActivityCategory
import com.example.model.AiSummaryResult
import com.example.model.CounselingRecord
import com.example.model.EncryptedCounselingRecordDto
import com.example.model.VaultConfigDto
import com.google.firebase.firestore.FirebaseFirestoreException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface VaultState {
  data object Loading : VaultState
  data object NeedsSetup : VaultState
  data class Locked(val config: VaultConfigDto) : VaultState
  data class Unlocked(
    val config: VaultConfigDto,
    val masterKey: SecretKey,
    val totpSecret: String? = null,
    val freshRecoveryCode: String? = null,
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
) : ViewModel() {

  private val unlockedKeyFlow = MutableStateFlow<SecretKey?>(null)
  private val unlockedTotpSecretFlow = MutableStateFlow<String?>(null)
  private val freshRecoveryCodeFlow = MutableStateFlow<String?>(null)
  private val vaultActionErrorFlow = MutableStateFlow<String?>(null)
  private val vaultBusyFlow = MutableStateFlow(false)

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

  val vaultState: StateFlow<VaultState> =
    combine(rawVaultFlow, unlockedKeyFlow, unlockedTotpSecretFlow, freshRecoveryCodeFlow) {
        configResult,
        masterKey,
        totpSecret,
        recoveryCode ->
        configResult.fold(
          onSuccess = { config ->
            when {
              config == null -> VaultState.NeedsSetup
              masterKey == null -> VaultState.Locked(config)
              else -> VaultState.Unlocked(config, masterKey, totpSecret, recoveryCode)
            }
          },
          onFailure = { err ->
            VaultState.Error(err.message ?: "Gagal memuatkan konfigurasi peti besi keselamatan.")
          },
        )
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

  fun clearVaultActionError() {
    vaultActionErrorFlow.value = null
  }

  fun clearFreshRecoveryCode() {
    freshRecoveryCodeFlow.value = null
  }

  fun resetAiState() {
    _aiSummaryState.value = AiSummaryState.Idle
  }

  // --- 2FA & E2EE Vault Operations ---

  fun setupVault(pin: String, enableTotp: Boolean) {
    if (pin.length != 6 || !pin.all { it.isDigit() }) {
      vaultActionErrorFlow.value = "PIN keselamatan mestilah tepat 6 digit nombor."
      return
    }
    viewModelScope.launch {
      vaultBusyFlow.value = true
      vaultActionErrorFlow.value = null
      try {
        val result =
          withContext(Dispatchers.Default) {
            val salt = E2eeCryptoManager.generateSaltBase64()
            val kek = E2eeCryptoManager.deriveKeyFromPin(pin, salt)
            val pinHash = E2eeCryptoManager.hashPinVerifier(pin, salt)
            val masterKey = E2eeCryptoManager.generateMasterKey()
            val wrappedMaster = E2eeCryptoManager.wrapSecretKey(masterKey, kek)
            val recoveryCode = E2eeCryptoManager.generateRecoveryCode()
            val recoveryHash = E2eeCryptoManager.sha256Base64(recoveryCode)

            var totpSecret: String? = null
            var wrappedTotpSecret: String? = null
            var wrappedTotpIv: String? = null
            if (enableTotp) {
              totpSecret = E2eeCryptoManager.generateTotpSecret()
              val encTotp = E2eeCryptoManager.encryptString(totpSecret, masterKey)
              wrappedTotpSecret = encTotp.ciphertextBase64
              wrappedTotpIv = encTotp.ivBase64
            }

            val config =
              VaultConfigDto(
                userId = currentUserId,
                pinHash = pinHash,
                kdfSalt = salt,
                wrappedMasterKey = wrappedMaster.ciphertextBase64,
                wrappedMasterKeyIv = wrappedMaster.ivBase64,
                totpEnabled = enableTotp,
                wrappedTotpSecret = wrappedTotpSecret,
                wrappedTotpIv = wrappedTotpIv,
                recoveryCodeHash = recoveryHash,
              )
            SetupPayload(config, masterKey, totpSecret, recoveryCode)
          }

        repository.saveVaultConfig(result.config, isNew = true)
        unlockedKeyFlow.value = result.masterKey
        unlockedTotpSecretFlow.value = result.totpSecret
        freshRecoveryCodeFlow.value = result.recoveryCode
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Vault setup failed", e)
        vaultActionErrorFlow.value =
          e.localizedMessage ?: "Gagal menyediakan peti besi penyulitan."
      } finally {
        vaultBusyFlow.value = false
      }
    }
  }

  fun unlockVault(config: VaultConfigDto, pin: String, totpCode: String) {
    if (pin.length != 6) {
      vaultActionErrorFlow.value = "Sila masukkan PIN 6 digit."
      return
    }
    if (config.totpEnabled && totpCode.trim().length != 6) {
      vaultActionErrorFlow.value = "Sila masukkan kod pengesahan 2FA (6 digit)."
      return
    }
    viewModelScope.launch {
      vaultBusyFlow.value = true
      vaultActionErrorFlow.value = null
      try {
        val unlocked =
          withContext(Dispatchers.Default) {
            val pinOk = E2eeCryptoManager.verifyPin(pin, config.kdfSalt, config.pinHash)
            if (!pinOk) {
              throw IllegalArgumentException("PIN keselamatan tidak tepat.")
            }
            val kek = E2eeCryptoManager.deriveKeyFromPin(pin, config.kdfSalt)
            val masterKey =
              E2eeCryptoManager.unwrapSecretKey(
                config.wrappedMasterKey,
                config.wrappedMasterKeyIv,
                kek,
              )
            var totpSecret: String? = null
            if (config.totpEnabled) {
              val cipher = config.wrappedTotpSecret
              val iv = config.wrappedTotpIv
              if (cipher.isNullOrBlank() || iv.isNullOrBlank()) {
                throw IllegalStateException("Konfigurasi TOTP rosak.")
              }
              totpSecret = E2eeCryptoManager.decryptString(cipher, iv, masterKey)
              if (!E2eeCryptoManager.verifyTotpCode(totpSecret, totpCode)) {
                throw IllegalArgumentException("Kod 2FA (TOTP) tidak sah atau telah tamat tempoh.")
              }
            }
            UnlockedPayload(masterKey, totpSecret)
          }
        unlockedKeyFlow.value = unlocked.masterKey
        unlockedTotpSecretFlow.value = unlocked.totpSecret
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Unlock vault failed: ${e.message}")
        vaultActionErrorFlow.value = e.message ?: "Pengesahan 2FA gagal."
      } finally {
        vaultBusyFlow.value = false
      }
    }
  }

  fun toggleTotpTwoFactor(enable: Boolean, onDone: (String?) -> Unit = {}) {
    val state = vaultState.value as? VaultState.Unlocked ?: return
    viewModelScope.launch {
      vaultBusyFlow.value = true
      vaultActionErrorFlow.value = null
      try {
        val newTotpSecret: String?
        val updatedConfig: VaultConfigDto
        if (enable) {
          val secret = E2eeCryptoManager.generateTotpSecret()
          val enc = E2eeCryptoManager.encryptString(secret, state.masterKey)
          newTotpSecret = secret
          updatedConfig =
            state.config.copy(
              totpEnabled = true,
              wrappedTotpSecret = enc.ciphertextBase64,
              wrappedTotpIv = enc.ivBase64,
            )
        } else {
          newTotpSecret = null
          updatedConfig =
            state.config.copy(
              totpEnabled = false,
              wrappedTotpSecret = null,
              wrappedTotpIv = null,
            )
        }
        repository.saveVaultConfig(updatedConfig, isNew = false)
        unlockedTotpSecretFlow.value = newTotpSecret
        onDone(newTotpSecret)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to update 2FA TOTP setting", e)
        vaultActionErrorFlow.value = e.localizedMessage ?: "Gagal mengemas kini tetapan 2FA."
      } finally {
        vaultBusyFlow.value = false
      }
    }
  }

  fun changeSecurityPin(currentPin: String, newPin: String, onSuccess: () -> Unit) {
    val state = vaultState.value as? VaultState.Unlocked ?: return
    if (newPin.length != 6 || !newPin.all { it.isDigit() }) {
      vaultActionErrorFlow.value = "PIN baharu mestilah tepat 6 digit."
      return
    }
    viewModelScope.launch {
      vaultBusyFlow.value = true
      vaultActionErrorFlow.value = null
      try {
        val updatedConfig =
          withContext(Dispatchers.Default) {
            if (!E2eeCryptoManager.verifyPin(currentPin, state.config.kdfSalt, state.config.pinHash)) {
              throw IllegalArgumentException("PIN semasa tidak tepat.")
            }
            val newSalt = E2eeCryptoManager.generateSaltBase64()
            val newKek = E2eeCryptoManager.deriveKeyFromPin(newPin, newSalt)
            val newHash = E2eeCryptoManager.hashPinVerifier(newPin, newSalt)
            val newWrapped = E2eeCryptoManager.wrapSecretKey(state.masterKey, newKek)
            state.config.copy(
              pinHash = newHash,
              kdfSalt = newSalt,
              wrappedMasterKey = newWrapped.ciphertextBase64,
              wrappedMasterKeyIv = newWrapped.ivBase64,
            )
          }
        repository.saveVaultConfig(updatedConfig, isNew = false)
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        vaultActionErrorFlow.value = e.message ?: "Gagal menukar PIN."
      } finally {
        vaultBusyFlow.value = false
      }
    }
  }

  fun lockVault() {
    unlockedKeyFlow.value = null
    unlockedTotpSecretFlow.value = null
    freshRecoveryCodeFlow.value = null
    vaultActionErrorFlow.value = null
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

    // 7-day trend ending today
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

  private data class SetupPayload(
    val config: VaultConfigDto,
    val masterKey: SecretKey,
    val totpSecret: String?,
    val recoveryCode: String,
  )

  private data class UnlockedPayload(
    val masterKey: SecretKey,
    val totpSecret: String?,
  )

  private companion object {
    const val TAG = "CounselorVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
