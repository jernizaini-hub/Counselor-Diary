package com.example.data.ai

import android.content.Context
import android.util.Log
import com.example.data.firebase.ensureValidAppCheckToken
import com.example.model.ActivityCategory
import com.example.model.AiSummaryResult
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.HarmBlockThreshold
import com.google.firebase.ai.type.HarmCategory
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class CounselorAiService(
  private val appContext: Context? = null,
) {
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

  private val clinicalSafetySettings =
    listOf(
      SafetySetting(HarmCategory.HARASSMENT, HarmBlockThreshold.ONLY_HIGH),
      SafetySetting(HarmCategory.HATE_SPEECH, HarmBlockThreshold.ONLY_HIGH),
      SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, HarmBlockThreshold.ONLY_HIGH),
      SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.ONLY_HIGH),
    )

  private val counselorSystemInstruction =
    content {
      text(
        "Anda ialah pembantu dokumentasi klinikal rasmi untuk Guru Bimbingan dan Kaunseling (Kaunselor Berdaftar) " +
          "di institusi pendidikan Malaysia. Semua input adalah catatan sesi profesional untuk tujuan pendokumentasian, " +
          "penilaian risiko, sokongan psikososial, dan perancangan intervensi keselamatan klien. " +
          "Jangan sekali-kali menjana arahan mencederakan diri; sebaliknya, apabila nota menyebut risiko keselamatan atau " +
          "kecenderungan mencederakan diri, fokuskan huraian intervensi pada protokol saringan keselamatan segera, " +
          "rujukan pakar/waris/pentadbir mengikut SOP kaunseling, serta pelan sokongan protektif yang profesional dan beretika."
      )
    }

  private val fastModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.5-flash-lite",
      generationConfig = jsonGenerationConfig,
      safetySettings = clinicalSafetySettings,
      systemInstruction = counselorSystemInstruction,
    )
  }

  private val standardModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = jsonGenerationConfig,
      safetySettings = clinicalSafetySettings,
      systemInstruction = counselorSystemInstruction,
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
      Berdasarkan maklumat dan transkrip/catatan ringkas kaunselor di bawah, hasilkan analisis rumusan profesional dalam Bahasa Melayu yang jelas, empatik, dan beretika.

      Kategori Aktiviti: ${category.displayName}
      Nama Klien / Peserta: ${clientName.ifBlank { "Tidak dinyatakan" }}
      Kelas / Kumpulan: ${className.ifBlank { "Tidak dinyatakan" }}
      Hari & Masa: $dayName, $timeRange

      Transkrip Perbualan / Catatan Ringkas Kaunselor:
      $rawTranscriptOrNotes

      Arahan Output JSON:
      1. "perkara": Nyatakan perkara/isu pokok secara ringkas dan tepat (2-4 ayat).
      2. "persoalan": Senaraikan atau huraikan persoalan utama yang timbul dan aspek yang perlu diterokai lanjut.
      3. "huraianIntervensi": Berikan huraian intervensi kaunseling yang sesuai (contoh: teknik penstrukturan semula kognitif, terapi realiti, penilaian keselamatan, rujukan sokongan waris/pakar) dan tindakan susulan yang praktikal.
      """
        .trimIndent()

    return withContext(Dispatchers.IO) {
      // Step 1: Pre-flight App Check health check & auto-recovery after app restarts
      appContext?.let { ensureValidAppCheckToken(it, forceRefreshAndReset = false) }

      try {
        streamStructuredSummary(fastModel, prompt)
      } catch (e: CancellationException) {
        throw e
      } catch (e: PromptBlockedException) {
        throw mapSafetyBlockError(e)
      } catch (e: ResponseStoppedException) {
        throw mapResponseStoppedError(e)
      } catch (primaryError: Exception) {
        if (isAppCheckFailure(primaryError) && appContext != null) {
          Log.w(TAG, "App Check token rejected by server; resetting and retrying AI call", primaryError)
          ensureValidAppCheckToken(appContext, forceRefreshAndReset = true)
          try {
            return@withContext streamStructuredSummary(fastModel, prompt)
          } catch (e: CancellationException) {
            throw e
          } catch (e: PromptBlockedException) {
            throw mapSafetyBlockError(e)
          } catch (e: ResponseStoppedException) {
            throw mapResponseStoppedError(e)
          } catch (retryError: Exception) {
            if (isAppCheckFailure(retryError)) {
              val detail = retryError.localizedMessage?.takeIf { it.isNotBlank() } ?: "ditolak oleh pelayan"
              throw IllegalStateException(
                "Pengesahan App Check masih gagal selepas cubaan automatik ($detail). " +
                  "Sila pastikan internet aktif tanpa sekatan VPN/Private DNS dan Google Play Services dikemas kini.",
                retryError,
              )
            }
            return@withContext runStandardModelFallback(prompt, retryError)
          }
        }

        if (primaryError is TimeoutCancellationException) {
          Log.w(TAG, "Model pantas tamat tempoh, mencuba model standard", primaryError)
        } else {
          Log.w(TAG, "Model pantas gagal, mencuba model standard", primaryError)
        }
        runStandardModelFallback(prompt, primaryError)
      }
    }
  }

  private suspend fun runStandardModelFallback(
    prompt: String,
    primaryError: Exception,
  ): AiSummaryResult {
    return try {
      streamStructuredSummary(standardModel, prompt)
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Janaan AI tamat tempoh (model standard)", e)
      throw IllegalStateException(
        "Sambungan AI mengambil masa terlalu lama (melebihi 45 saat). Sila pastikan internet stabil dan cuba semula.",
        e,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: PromptBlockedException) {
      throw mapSafetyBlockError(e)
    } catch (e: ResponseStoppedException) {
      throw mapResponseStoppedError(e)
    } catch (fallbackError: Exception) {
      Log.e(TAG, "Ralat janaan AI", fallbackError)
      if (isAppCheckFailure(fallbackError)) {
        val detail = fallbackError.localizedMessage?.takeIf { it.isNotBlank() } ?: "token tidak sah"
        throw IllegalStateException(
          "Ralat pengesahan keselamatan aplikasi (Firebase App Check): $detail. " +
            "Sila pastikan internet aktif tanpa sekatan VPN/Private DNS, tarikh peranti adalah automatik, dan cuba semula.",
          fallbackError,
        )
      }
      val detail = fallbackError.localizedMessage ?: primaryError.localizedMessage
      throw IllegalStateException(
        detail ?: "Gagal menjana rumusan AI. Sila semak sambungan internet.",
        fallbackError,
      )
    }
  }

  private suspend fun streamStructuredSummary(
    model: GenerativeModel,
    prompt: String,
  ): AiSummaryResult {
    val accumulated = StringBuilder()
    withTimeout(AI_TIMEOUT_MS) {
      model.generateContentStream(prompt).collect { chunk ->
        val candidate = chunk.candidates.firstOrNull()
        if (candidate?.finishReason == FinishReason.SAFETY ||
          candidate?.finishReason == FinishReason.PROHIBITED_CONTENT ||
          candidate?.finishReason == FinishReason.BLOCKLIST
        ) {
          throw IllegalStateException(SAFETY_GUIDANCE_MESSAGE)
        }
        chunk.text?.let { accumulated.append(it) }
      }
    }
    val jsonText =
      accumulated.toString().trim().takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("Model AI memulangkan respons kosong. Sila cuba semula.")

    val json = JSONObject(jsonText)
    return AiSummaryResult(
      matter = json.optString("perkara", "").trim(),
      questions = json.optString("persoalan", "").trim(),
      intervention = json.optString("huraianIntervensi", "").trim(),
    )
  }

  private fun isAppCheckFailure(error: Throwable): Boolean {
    val message =
      generateSequence(error) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
        .lowercase()
    return message.contains("app check") ||
      message.contains("appcheck") ||
      message.contains("attestation")
  }

  private fun mapSafetyBlockError(error: PromptBlockedException): IllegalStateException {
    Log.w(TAG, "Prompt blocked by Gemini safety filter", error)
    return IllegalStateException(SAFETY_GUIDANCE_MESSAGE, error)
  }

  private fun mapResponseStoppedError(error: ResponseStoppedException): IllegalStateException {
    val reason = error.response.candidates.firstOrNull()?.finishReason
    Log.w(TAG, "Response stopped early: $reason", error)
    return when (reason) {
      FinishReason.SAFETY, FinishReason.PROHIBITED_CONTENT, FinishReason.BLOCKLIST ->
        IllegalStateException(SAFETY_GUIDANCE_MESSAGE, error)
      FinishReason.MAX_TOKENS ->
        IllegalStateException(
          "Catatan terlalu panjang untuk diproses serentak. Sila ringkaskan sedikit catatan dan cuba semula.",
          error,
        )
      else ->
        IllegalStateException(
          error.localizedMessage ?: "Janaan AI terhenti sebelum selesai. Sila cuba semula.",
          error,
        )
    }
  }

  private companion object {
    const val TAG = "CounselorAiService"
    const val AI_TIMEOUT_MS = 45_000L
    const val SAFETY_GUIDANCE_MESSAGE =
      "Catatan mengandungi istilah sensitif atau risiko tinggi yang ditahan oleh penapis keselamatan AI. " +
        "Sila guna ungkapan klinikal neutral (cth. 'risiko keselamatan diri / memerlukan saringan segera') untuk janaan AI, " +
        "atau isi kotak rumusan secara manual serta teruskan rujukan SOP kecemasan kaunseling jika perlu."
  }
}
