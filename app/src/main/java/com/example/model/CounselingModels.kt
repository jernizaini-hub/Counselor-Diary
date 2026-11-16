package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ActivityCategory(
  val firestoreKey: String,
  val displayName: String,
  val shortLabel: String,
  val isCounselingSession: Boolean,
) {
  KAUNSELING_INDIVIDU("KAUNSELING_INDIVIDU", "Kaunseling Individu", "Individu", true),
  KAUNSELING_KELOMPOK("KAUNSELING_KELOMPOK", "Kaunseling Kelompok", "Kelompok", true),
  PROGRAM("PROGRAM", "Program Bimbingan", "Program", false),
  MESYUARAT("MESYUARAT", "Mesyuarat", "Mesyuarat", false),
  KELAS_GANTI("KELAS_GANTI", "Kelas Ganti", "Kelas Ganti", false),
  ZIARAH_CAKNA("ZIARAH_CAKNA", "Ziarah Cakna", "Ziarah Cakna", false),
  KONSULTASI_IBUBAPA("KONSULTASI_IBUBAPA", "Konsultasi Ibu Bapa", "Ibu Bapa", false);

  companion object {
    fun fromKey(key: String): ActivityCategory =
      entries.firstOrNull { it.firestoreKey == key } ?: KAUNSELING_INDIVIDU
  }
}

data class CounselingRecord(
  val id: String = "",
  val userId: String = "",
  val category: ActivityCategory = ActivityCategory.KAUNSELING_INDIVIDU,
  val clientName: String = "",
  val className: String = "",
  val organizer: String = "",
  val guardianName: String = "",
  val guardianPhone: String = "",
  val sessionDateMillis: Long = System.currentTimeMillis(),
  val dayName: String = "",
  val timeRange: String = "",
  val durationMinutes: Int = 45,
  val notes: String = "",
  val summaryMatter: String = "",
  val summaryQuestions: String = "",
  val programStrengthsWeaknesses: String = "",
  val summaryIntervention: String = "",
  val hasAiSummary: Boolean = false,
  val encryptionVersion: String = "AES-256-GCM-v1",
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class EncryptedCounselingRecordDto(
  @DocumentId val id: String = "",
  val userId: String = "",
  val category: String = ActivityCategory.KAUNSELING_INDIVIDU.firestoreKey,
  val sessionDateMillis: Long = 0L,
  val dayName: String = "",
  val timeRange: String = "",
  val durationMinutes: Int = 0,
  val hasAiSummary: Boolean = false,
  val encryptionVersion: String = "AES-256-GCM-v1",
  val encryptedPayload: String = "",
  val payloadIv: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class VaultConfigDto(
  @DocumentId val id: String = "",
  val userId: String = "",
  val pinHash: String = "",
  val kdfSalt: String = "",
  val wrappedMasterKey: String = "",
  val wrappedMasterKeyIv: String = "",
  val totpEnabled: Boolean = false,
  val wrappedTotpSecret: String? = null,
  val wrappedTotpIv: String? = null,
  val recoveryCodeHash: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class AiSummaryResult(
  val matter: String,
  val questions: String,
  val intervention: String,
  val strengthsWeaknesses: String = "",
)
