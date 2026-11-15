package com.example.data.ai

import android.util.Log
import com.example.model.ActivityCategory
import com.example.model.AiSummaryResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
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

  private val model by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig =
        generationConfig {
          responseMimeType = "application/json"
          responseSchema = summarySchema
        },
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

    try {
      val response = withTimeout(60_000L) { model.generateContent(prompt) }
      val jsonText =
        response.text?.takeIf { it.isNotBlank() }
          ?: throw IllegalStateException("Model AI memulangkan respons kosong.")

      val json = JSONObject(jsonText)
      return AiSummaryResult(
        matter = json.optString("perkara", "").trim(),
        questions = json.optString("persoalan", "").trim(),
        intervention = json.optString("huraianIntervensi", "").trim(),
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Janaan AI tamat tempoh", e)
      throw IllegalStateException("Janaan AI melebihi masa 60 saat. Sila cuba sebentar lagi.", e)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Ralat janaan AI", e)
      throw IllegalStateException(
        e.localizedMessage ?: "Gagal menjana rumusan AI. Sila semak sambungan internet.",
        e,
      )
    }
  }

  private companion object {
    const val TAG = "CounselorAiService"
  }
}
