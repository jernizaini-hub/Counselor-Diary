package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.crypto.E2eeCryptoManager
import com.example.model.ActivityCategory
import com.example.model.CounselingRecord
import com.example.model.VaultConfigDto
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CounselorRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun saveAndDecryptRecord_authenticatedOwner_succeedsAndPreservesE2ee() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = CounselorRepository(firestore, auth)
    val masterKey = E2eeCryptoManager.generateMasterKey()

    val record =
      CounselingRecord(
        category = ActivityCategory.KAUNSELING_INDIVIDU,
        clientName = "Ahmad Zaki",
        className = "5 Bestari",
        dayName = "Isnin",
        timeRange = "09:00 - 09:45",
        durationMinutes = 45,
        notes = "Klien berkongsi tekanan peperiksaan SPM.",
        summaryMatter = "Tekanan akademik menjelang peperiksaan.",
        summaryQuestions = "Bagaimana membahagikan masa ulang kaji?",
        summaryIntervention = "Latihan teknik pernafasan dan jadual belajar realistik.",
      )

    val recordId = withTimeout(DEFAULT_TIMEOUT_MS) { repo.saveRecord(record, masterKey) }
    assertTrue(recordId.isNotBlank())

    val dto = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getEncryptedRecordById(recordId) }
    assertNotNull(dto)
    assertTrue(!dto!!.encryptedPayload.contains("Ahmad Zaki"))

    val decrypted = repo.decryptRecord(dto, masterKey)
    assertEquals("Ahmad Zaki", decrypted.clientName)
    assertEquals("5 Bestari", decrypted.className)
    assertEquals("Tekanan akademik menjelang peperiksaan.", decrypted.summaryMatter)

    // Test update while preserving createdAt
    val updatedId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.saveRecord(
          decrypted.copy(category = ActivityCategory.ZIARAH_CAKNA, durationMinutes = 60),
          masterKey,
        )
      }
    assertEquals(recordId, updatedId)
  }

  @Test
  fun saveAndLoadVaultConfig_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = CounselorRepository(firestore, auth)
    val salt = E2eeCryptoManager.generateSaltBase64()
    val kek = E2eeCryptoManager.deriveKeyFromPin("123456", salt)
    val masterKey = E2eeCryptoManager.generateMasterKey()
    val wrapped = E2eeCryptoManager.wrapSecretKey(masterKey, kek)

    val config =
      VaultConfigDto(
        userId = uid,
        pinHash = E2eeCryptoManager.hashPinVerifier("123456", salt),
        kdfSalt = salt,
        wrappedMasterKey = wrapped.ciphertextBase64,
        wrappedMasterKeyIv = wrapped.ivBase64,
        totpEnabled = false,
      )

    withTimeout(DEFAULT_TIMEOUT_MS) { repo.saveVaultConfig(config, isNew = true) }
    val loaded = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getVaultConfig() }
    assertNotNull(loaded)
    assertEquals(uid, loaded!!.userId)
  }

  @Test
  fun crossUserRecordRead_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = CounselorRepository(firestore, auth)
    val masterKey = E2eeCryptoManager.generateMasterKey()
    val recordId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo.saveRecord(
          CounselingRecord(
            clientName = "Rahsia Klien",
            className = "4 Amanah",
            dayName = "Selasa",
            timeRange = "10:00 - 11:00",
            notes = "Catatan sulit.",
          ),
          masterKey,
        )
      }

    signInTestUser(BOB_EMAIL)
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore
          .collection("users")
          .document(aliceUid)
          .collection("records")
          .document(recordId)
          .get()
          .let { com.google.android.gms.tasks.Tasks.await(it) }
      }
      fail("Expected PERMISSION_DENIED for cross-user read")
    } catch (e: Exception) {
      val firestoreEx = e.unwrapFirestoreException()
      assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreEx)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
    }
  }

  @Test
  fun observeEncryptedRecords_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = CounselorRepository(firestore, auth)

    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeEncryptedRecords("unauth_user_123").first() }
      fail("Expected PERMISSION_DENIED for unauthenticated stream")
    } catch (e: Exception) {
      val firestoreEx = e.unwrapFirestoreException()
      assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreEx)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice_kaunselor@test.com"
    const val BOB_EMAIL = "bob_kaunselor@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
