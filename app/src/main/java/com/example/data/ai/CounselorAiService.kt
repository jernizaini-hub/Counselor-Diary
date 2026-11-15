package com.example.data.ai

import android.util.Log
import com.example.model.ActivityCategory
import com.example.model.AiSummaryResult
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class CounselorAiService {
  private val summarySchema =
    Schema.obj(
      properties =
        mapOf(
          "perkara" to
            Schema.string(
              description =
                "Ringkasan padat isu utama, tema atau perkara pokok yang dibincangkan/dijalankan dalam Bahasa Melayu profesional."
            ),
          "persoalan" to
            Schema.string(
              description =
                "Persoalan utama klien, punca masalah, atau soalan penerokaan kritikal untuk sesi seterusnya."
            ),
          "huraianIntervensi" to
            Schema.string(
              description =
                "Huraian terperinci intervensi kaunseling, teknik/pendekatan psikologi, serta pelan tindakan susulan."
            ),
        )
    )

  private val jsonGenerationConfig = generationConfig {
    responseMimeType = "application/json"
    responseSchema = summarySchema
  }

  private val fastModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.5-flash-lite",
      generationConfig = jsonGenerationConfig,
    )
  }

  private val standardModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = jsonGenerationConfig,
    )
  }

  suspend fun generateCounselingSummary(
    category: ActivityCategory,
    clientName: String,
    className: String,
    dayName: String,
    timeRange: String,
    rawTranscriptOrNotes: String,
  ): AiSummaryResult {
    val prompt =
      """
      Anda ialah pembantu profesional Guru Bimbingan dan Kaunseling (Kaunselor Berdaftar) di Malaysia.
      Berdasarkan maklumat dan transkrip/catatan ringkas di bawah, hasilkan analisis rumusan profesional dalam Bahasa Melayu yang jelas, empatik, dan beretika.

      Kategori Aktiviti: ${category.displayName}
      Nama Klien / Peserta: ${clientName.ifBlank { "Tidak dinyatakan" }}
      Kelas / Kumpulan: ${className.ifBlank { "Tidak dinyatakan" }}
      Hari & Masa: $dayName, $timeRange

      Transkrip Perbualan / Catatan Ringkas Kaunselor:
      $rawTranscriptOrNotes

      Arahan Output JSON:
      1. "perkara": Nyatakan perkara/isu pokok secara ringkas dan tepat (2-4 ayat).
      2. "persoalan": Senaraikan atau huraikan persoalan utama yang timbul dan aspek yang perlu diterokai lanjut.
      3. "huraianIntervensi": Berikan huraian intervensi kaunseling yang sesuai (contoh: teknik penstrukturan semula kognitif, terapi realiti, sokongan emosi, konsultasi) dan tindakan susulan yang praktikal.
      """
        .trimIndent()

    return withContext(Dispatchers.IO) {
      try {
        streamStructuredSummary(fastModel, prompt)
      } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Janaan AI tamat tempoh (model pantas)", e)
        throw IllegalStateException(
          "Sambungan AI mengambil masa terlalu lama (melebihi 45 saat). Sila pastikan internet aktif dan cuba semula.",
          e,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (primaryError: Exception) {
        Log.w(TAG, "Model pantas gagal, mencuba model standard", primaryError)
        try {
          streamStructuredSummary(standardModel, prompt)
        } catch (e: TimeoutCancellationException) {
          Log.e(TAG, "Janaan AI tamat tempoh (model standard)", e)
          throw IllegalStateException(
            "Sambungan AI melebihi had masa. Sila semak sambungan internet dan cuba sebentar lagi.",
            e,
          )
        } catch (e: CancellationException) {
          throw e
        } catch (fallbackError: Exception) {
          Log.e(TAG, "Ralat janaan AI", fallbackError)
          val detail = fallbackError.localizedMessage ?: primaryError.localizedMessage
          throw IllegalStateException(
            detail ?: "Gagal menjana rumusan AI. Sila semak sambungan internet.",
            fallbackError,
          )
        }
      }
    }
  }

  private suspend fun streamStructuredSummary(
    model: GenerativeModel,
    prompt: String,
  ): AiSummaryResult {
    val accumulated = StringBuilder()
    withTimeout(AI_TIMEOUT_MS) {
      model.generateContentStream(prompt).collect { chunk ->
        chunk.text?.let { accumulated.append(it) }
      }
    }
    val jsonText =
      accumulated.toString().trim().takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("Model AI memulangkan respons kosong.")

    val json = JSONObject(jsonText)
    return AiSummaryResult(
      matter = json.optString("perkara", "").trim(),
      questions = json.optString("persoalan", "").trim(),
      intervention = json.optString("huraianIntervensi", "").trim(),
    )
  }

  private companion object {
    const val TAG = "CounselorAiService"
    const val AI_TIMEOUT_MS = 45_000L
  }
}
