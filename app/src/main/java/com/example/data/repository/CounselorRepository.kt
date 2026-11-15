package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.crypto.E2eeCryptoManager
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.model.ActivityCategory
import com.example.model.CounselingRecord
import com.example.model.EncryptedCounselingRecordDto
import com.example.model.VaultConfigDto
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import javax.crypto.SecretKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class CounselorRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    db =
      FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
      ),
    auth = Firebase.auth,
  )

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("Pengguna mesti log masuk sebelum mengakses rekod awan.")

  // --- Security / E2EE Vault Configuration ---

  fun observeVaultConfig(userId: String): Flow<VaultConfigDto?> = flow {
    val docRef =
      db.collection("users").document(userId).collection("security").document(VAULT_DOC_ID)
    emitAll(
      docRef
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(
              VaultConfigDto::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
          } else {
            null
          }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
          }
          throw error
        }
    )
  }

  suspend fun getVaultConfig(): VaultConfigDto? {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("security").document(VAULT_DOC_ID)
    return try {
      val snapshot = docRef.get().await()
      if (snapshot.exists()) {
        snapshot.toObject(
          VaultConfigDto::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      } else {
        null
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      throw e
    }
  }

  suspend fun saveVaultConfig(config: VaultConfigDto, isNew: Boolean) {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("security").document(VAULT_DOC_ID)
    try {
      if (isNew) {
        val payload =
          mutableMapOf<String, Any>(
            "userId" to uid,
            "pinHash" to config.pinHash,
            "kdfSalt" to config.kdfSalt,
            "wrappedMasterKey" to config.wrappedMasterKey,
            "wrappedMasterKeyIv" to config.wrappedMasterKeyIv,
            "totpEnabled" to config.totpEnabled,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        config.wrappedTotpSecret?.let { payload["wrappedTotpSecret"] = it }
        config.wrappedTotpIv?.let { payload["wrappedTotpIv"] = it }
        config.recoveryCodeHash?.let { payload["recoveryCodeHash"] = it }
        docRef.set(payload).await()
      } else {
        requireNotNull(config.createdAt) { "VaultConfig createdAt tidak boleh null semasa kemas kini." }
        docRef.set(config.copy(id = VAULT_DOC_ID, userId = uid, updatedAt = null)).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(
        e,
        if (isNew) OperationType.CREATE else OperationType.UPDATE,
        docRef.path,
      )
      throw e
    }
  }

  // --- Counseling & Activity Records (E2EE Encrypted in Cloud Firestore) ---

  fun observeEncryptedRecords(userId: String): Flow<List<EncryptedCounselingRecordDto>> = flow {
    val colRef = db.collection("users").document(userId).collection("records")
    emitAll(
      colRef
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            EncryptedCounselingRecordDto::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
          }
          throw error
        }
    )
  }

  suspend fun getEncryptedRecords(): List<EncryptedCounselingRecordDto> {
    val uid = requireUserId()
    val colRef = db.collection("users").document(uid).collection("records")
    return try {
      colRef
        .get()
        .await()
        .toObjects(
          EncryptedCounselingRecordDto::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, colRef.path)
      throw e
    }
  }

  suspend fun getEncryptedRecordById(recordId: String): EncryptedCounselingRecordDto? {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("records").document(recordId)
    return try {
      docRef
        .get()
        .await()
        .toObject(
          EncryptedCounselingRecordDto::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      throw e
    }
  }

  suspend fun saveRecord(record: CounselingRecord, masterKey: SecretKey): String {
    val uid = requireUserId()
    val colRef = db.collection("users").document(uid).collection("records")
    val isNew = record.id.isBlank()
    val docRef = if (isNew) colRef.document() else colRef.document(record.id)

    val sensitiveJson =
      JSONObject()
        .apply {
          put("clientName", record.clientName)
          put("className", record.className)
          put("notes", record.notes)
          put("summaryMatter", record.summaryMatter)
          put("summaryQuestions", record.summaryQuestions)
          put("summaryIntervention", record.summaryIntervention)
        }
        .toString()

    val encrypted = E2eeCryptoManager.encryptString(sensitiveJson, masterKey)
    val hasSummary =
      record.summaryMatter.isNotBlank() ||
        record.summaryQuestions.isNotBlank() ||
        record.summaryIntervention.isNotBlank()

    try {
      if (isNew) {
        val payload =
          mapOf(
            "userId" to uid,
            "category" to record.category.firestoreKey,
            "sessionDateMillis" to record.sessionDateMillis,
            "dayName" to record.dayName.ifBlank { "Isnin" },
            "timeRange" to record.timeRange.ifBlank { "09:00 - 10:00" },
            "durationMinutes" to record.durationMinutes.coerceIn(0, 1440),
            "hasAiSummary" to hasSummary,
            "encryptionVersion" to E2eeCryptoManager.ENCRYPTION_VERSION,
            "encryptedPayload" to encrypted.ciphertextBase64,
            "payloadIv" to encrypted.ivBase64,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.set(payload).await()
      } else {
        requireNotNull(record.createdAt) { "createdAt mesti wujud untuk kemas kini rekod." }
        val dto =
          EncryptedCounselingRecordDto(
            id = record.id,
            userId = uid,
            category = record.category.firestoreKey,
            sessionDateMillis = record.sessionDateMillis,
            dayName = record.dayName.ifBlank { "Isnin" },
            timeRange = record.timeRange.ifBlank { "09:00 - 10:00" },
            durationMinutes = record.durationMinutes.coerceIn(0, 1440),
            hasAiSummary = hasSummary,
            encryptionVersion = E2eeCryptoManager.ENCRYPTION_VERSION,
            encryptedPayload = encrypted.ciphertextBase64,
            payloadIv = encrypted.ivBase64,
            createdAt = record.createdAt,
            updatedAt = null,
          )
        docRef.set(dto).await()
      }
      return docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(
        e,
        if (isNew) OperationType.CREATE else OperationType.UPDATE,
        docRef.path,
      )
      throw e
    }
  }

  suspend fun deleteRecord(recordId: String) {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("records").document(recordId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  fun decryptRecord(dto: EncryptedCounselingRecordDto, masterKey: SecretKey): CounselingRecord {
    val plaintext = E2eeCryptoManager.decryptString(dto.encryptedPayload, dto.payloadIv, masterKey)
    val json = JSONObject(plaintext)
    return CounselingRecord(
      id = dto.id,
      userId = dto.userId,
      category = ActivityCategory.fromKey(dto.category),
      clientName = json.optString("clientName", ""),
      className = json.optString("className", ""),
      sessionDateMillis = dto.sessionDateMillis,
      dayName = dto.dayName,
      timeRange = dto.timeRange,
      durationMinutes = dto.durationMinutes,
      notes = json.optString("notes", ""),
      summaryMatter = json.optString("summaryMatter", ""),
      summaryQuestions = json.optString("summaryQuestions", ""),
      summaryIntervention = json.optString("summaryIntervention", ""),
      hasAiSummary = dto.hasAiSummary,
      encryptionVersion = dto.encryptionVersion,
      createdAt = dto.createdAt,
      updatedAt = dto.updatedAt,
    )
  }

  companion object {
    const val VAULT_DOC_ID = "vaultConfig"
  }
}
