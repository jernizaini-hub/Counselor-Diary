package com.example

import com.example.crypto.E2eeCryptoManager
import com.example.export.ReportExporter
import com.example.model.ActivityCategory
import com.example.model.CounselingRecord
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CryptoAndExporterTest {

  @Test
  fun e2eePinWrappingAndAesGcmRoundTrip_succeeds() {
    val salt = E2eeCryptoManager.generateSaltBase64()
    val pinHash = E2eeCryptoManager.hashPinVerifier("246810", salt)
    assertTrue(E2eeCryptoManager.verifyPin("246810", salt, pinHash))
    assertFalse(E2eeCryptoManager.verifyPin("000000", salt, pinHash))

    val kek = E2eeCryptoManager.deriveKeyFromPin("246810", salt)
    val masterKey = E2eeCryptoManager.generateMasterKey()
    val wrapped = E2eeCryptoManager.wrapSecretKey(masterKey, kek)
    val unwrapped = E2eeCryptoManager.unwrapSecretKey(wrapped.ciphertextBase64, wrapped.ivBase64, kek)

    val secretNote = "Klien mengalami kebimbangan peperiksaan dan masalah keluarga."
    val encrypted = E2eeCryptoManager.encryptString(secretNote, unwrapped)
    assertFalse(encrypted.ciphertextBase64.contains("kebimbangan"))
    val decrypted = E2eeCryptoManager.decryptString(encrypted.ciphertextBase64, encrypted.ivBase64, masterKey)
    assertEquals(secretNote, decrypted)
  }

  @Test
  fun totpGenerationAndVerification_succeeds() {
    val secret = E2eeCryptoManager.generateTotpSecret()
    val now = 1_700_000_000_000L
    val code = E2eeCryptoManager.generateTotpCode(secret, now)
    assertEquals(6, code.length)
    assertTrue(E2eeCryptoManager.verifyTotpCode(secret, code, now))
    assertFalse(E2eeCryptoManager.verifyTotpCode(secret, "999999", now + 600_000L))
  }

  @Test
  fun reportExporter_generatesValidPdfAndDocx() {
    val record =
      CounselingRecord(
        id = "rec_1",
        category = ActivityCategory.KAUNSELING_INDIVIDU,
        clientName = "Muhammad Amirul",
        className = "5 Cemerlang",
        dayName = "Rabu",
        timeRange = "10:00 - 11:00",
        durationMinutes = 60,
        notes = "Perbincangan mengenai pengurusan masa dan motivasi belajar.",
        summaryMatter = "Kurang fokus mengulang kaji subjek teras.",
        summaryQuestions = "Apakah halangan utama di rumah selepas waktu sekolah?",
        summaryIntervention = "Membina jadual belajar mingguan dan teknik Pomodoro.",
        hasAiSummary = true,
      )

    val pdfBytes = ReportExporter.generatePdfBytes(record, "Cikgu Aisyah", "aisyah@sekolah.edu.my")
    assertTrue(pdfBytes.size > 500)
    assertEquals("%PDF", String(pdfBytes.copyOfRange(0, 4), Charsets.US_ASCII))

    val docxBytes = ReportExporter.generateDocxBytes(record, "Cikgu Aisyah", "aisyah@sekolah.edu.my")
    assertTrue(docxBytes.size > 500)
    val entries = mutableListOf<String>()
    ZipInputStream(ByteArrayInputStream(docxBytes)).use { zip ->
      var entry = zip.nextEntry
      while (entry != null) {
        entries.add(entry.name)
        entry = zip.nextEntry
      }
    }
    assertTrue(entries.contains("[Content_Types].xml"))
    assertTrue(entries.contains("word/document.xml"))
    assertTrue(entries.contains("word/styles.xml"))
  }

  @Test
  fun counselorAiParser_parsesProgramFourFieldsAndExporterAdaptsCategories() {
    val programJson =
      """
      {
        "rumusanProgram": "Program berjalan lancar dengan penyertaan 120 murid.",
        "objektif": "Meningkatkan kesedaran kesihatan mental murid.",
        "kelebihanKelemahan": "Kelebihan: penceramah berkesan. Kelemahan: masa soal jawab terhad.",
        "huraianIntervensi": "Edar borang saringan DASS kepada murid berisiko."
      }
      """.trimIndent()
    val parsed = com.example.data.ai.CounselorAiService.parseSummaryJson(ActivityCategory.PROGRAM, programJson)
    assertEquals("Program berjalan lancar dengan penyertaan 120 murid.", parsed.matter)
    assertEquals("Meningkatkan kesedaran kesihatan mental murid.", parsed.questions)
    assertEquals(
      "Kelebihan: penceramah berkesan. Kelemahan: masa soal jawab terhad.",
      parsed.strengthsWeaknesses,
    )
    assertEquals("Edar borang saringan DASS kepada murid berisiko.", parsed.intervention)

    val kelasGanti =
      CounselingRecord(
        id = "rec_kg",
        category = ActivityCategory.KELAS_GANTI,
        className = "4 Tekun",
        dayName = "Khamis",
        timeRange = "10:30 - 11:15",
        durationMinutes = 45,
        notes = "Aktiviti ulang kaji bersama kelas.",
      )
    assertTrue(ReportExporter.buildExportSummaryBlocks(kelasGanti).isEmpty())
    assertTrue(ReportExporter.buildSuggestedFileName(kelasGanti, "pdf").contains("4_Tekun"))

    val docxBytes = ReportExporter.generateDocxBytes(kelasGanti, "Cikgu Aisyah", "aisyah@sekolah.edu.my")
    assertTrue(docxBytes.size > 500)
  }
}
