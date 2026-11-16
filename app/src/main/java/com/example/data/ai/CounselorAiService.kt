package com.example.data.ai

import android.content.Context
import android.util.Log
import com.example.data.firebase.ensureValidAppCheckToken
import com.example.model.ActivityCategory
import com.example.model.AiSummaryResult
import com.example.model.CounselingRecord
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
                "Ringkasan padat isu utama, tema, rumusan perbincangan atau perkara pokok yang dijalankan dalam Bahasa Melayu profesional."
            ),
          "persoalan" to
            Schema.string(
              description =
                "Persoalan utama klien, isu berbangkit penjaga/murid, atau soalan penerokaan kritikal untuk tindakan susulan."
            ),
          "huraianIntervensi" to
            Schema.string(
              description =
                "Huraian terperinci intervensi kaunseling, keputusan, pendekatan psikologi, serta pelan tindakan susulan."
            ),
        )
    )

  private val programSchema =
    Schema.obj(
      properties =
        mapOf(
          "rumusanProgram" to
            Schema.string(
              description =
                "Rumusan Program: ringkasan menyeluruh pelaksanaan, pengisian, penyertaan serta impak program dalam Bahasa Melayu profesional."
            ),
          "objektif" to
            Schema.string(
              description =
                "Objektif Program: senarai objektif utama program yang disasarkan dan tahap pencapaiannya."
            ),
          "kelebihanKelemahan" to
            Schema.string(
              description =
                "Kelebihan & Kelemahan: kekuatan pelaksanaan program serta kelemahan atau ruang penambahbaikan pada masa hadapan."
            ),
          "huraianIntervensi" to
            Schema.string(
              description =
                "Huraian Intervensi: langkah susulan, bimbingan lanjutan atau tindakan penambahbaikan untuk kumpulan sasaran."
            ),
        )
    )

  private val jsonGenerationConfig = generationConfig {
    responseMimeType = "application/json"
    responseSchema = summarySchema
  }

  private val programGenerationConfig = generationConfig {
    responseMimeType = "application/json"
    responseSchema = programSchema
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

  private val programFastModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.5-flash-lite",
      generationConfig = programGenerationConfig,
      safetySettings = clinicalSafetySettings,
      systemInstruction = counselorSystemInstruction,
    )
  }

  private val programStandardModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = programGenerationConfig,
      safetySettings = clinicalSafetySettings,
      systemInstruction = counselorSystemInstruction,
    )
  }

  suspend fun generateCounselingSummary(draft: CounselingRecord): AiSummaryResult {
    val prompt = buildCategoryPrompt(draft)
    val primaryModel =
      if (draft.category == ActivityCategory.PROGRAM) programFastModel else fastModel
    val fallbackModel =
      if (draft.category == ActivityCategory.PROGRAM) programStandardModel else standardModel

    return withContext(Dispatchers.IO) {
      // Step 1: Pre-flight App Check health check & auto-recovery after app restarts
      appContext?.let { ensureValidAppCheckToken(it, forceRefreshAndReset = false) }

      try {
        streamStructuredSummary(primaryModel, draft.category, prompt)
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
            return@withContext streamStructuredSummary(primaryModel, draft.category, prompt)
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
            return@withContext runStandardModelFallback(
              fallbackModel,
              draft.category,
              prompt,
              retryError,
            )
          }
        }

        if (primaryError is TimeoutCancellationException) {
          Log.w(TAG, "Model pantas tamat tempoh, mencuba model standard", primaryError)
        } else {
          Log.w(TAG, "Model pantas gagal, mencuba model standard", primaryError)
        }
        runStandardModelFallback(fallbackModel, draft.category, prompt, primaryError)
      }
    }
  }

  private suspend fun runStandardModelFallback(
    model: GenerativeModel,
    category: ActivityCategory,
    prompt: String,
    primaryError: Exception,
  ): AiSummaryResult {
    return try {
      streamStructuredSummary(model, category, prompt)
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
    category: ActivityCategory,
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

    return parseSummaryJson(category, jsonText)
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

  internal companion object {
    const val TAG = "CounselorAiService"
    const val AI_TIMEOUT_MS = 45_000L
    const val SAFETY_GUIDANCE_MESSAGE =
      "Catatan mengandungi istilah sensitif atau risiko tinggi yang ditahan oleh penapis keselamatan AI. " +
        "Sila guna ungkapan klinikal neutral (cth. 'risiko keselamatan diri / memerlukan saringan segera') untuk janaan AI, " +
        "atau isi kotak rumusan secara manual serta teruskan rujukan SOP kecemasan kaunseling jika perlu."

    internal fun buildCategoryPrompt(draft: CounselingRecord): String {
      return when (draft.category) {
        ActivityCategory.PROGRAM ->
          """
          Berdasarkan maklumat program dan transkrip/catatan ringkas kaunselor di bawah, hasilkan analisis rumusan program rasmi dalam Bahasa Melayu yang jelas, tersusun, dan profesional.

          Kategori Aktiviti: ${draft.category.displayName}
          Nama Program: ${draft.clientName.ifBlank { "Tidak dinyatakan" }}
          Anjuran: ${draft.organizer.ifBlank { "Tidak dinyatakan" }}
          Sasaran: ${draft.className.ifBlank { "Tidak dinyatakan" }}
          Hari & Masa: ${draft.dayName}, ${draft.timeRange}

          Transkrip / Catatan Ringkas Pelaksanaan Program:
          ${draft.notes}

          Arahan Output JSON:
          1. "rumusanProgram": Rumusan keseluruhan pelaksanaan, pengisian utama, penyertaan sasaran, dan impak program (2-4 ayat).
          2. "objektif": Nyatakan objektif utama program dan tahap pencapaiannya berdasarkan catatan.
          3. "kelebihanKelemahan": Pecahkan secara jelas kelebihan (kekuatan) program serta kelemahan atau ruang penambahbaikan.
          4. "huraianIntervensi": Huraian intervensi atau pelan tindakan susulan kaunseling selepas program dijalankan.
          """.trimIndent()

        ActivityCategory.ZIARAH_CAKNA,
        ActivityCategory.KONSULTASI_IBUBAPA ->
          """
          Berdasarkan maklumat ziarah cakna / konsultasi ibu bapa dan catatan perbincangan di bawah, hasilkan rumusan perbincangan rasmi dalam Bahasa Melayu yang empatik, tepat, dan beretika.

          Kategori Aktiviti: ${draft.category.displayName}
          Nama Murid: ${draft.clientName.ifBlank { "Tidak dinyatakan" }}
          Kelas Murid: ${draft.className.ifBlank { "Tidak dinyatakan" }}
          Nama Penjaga: ${draft.guardianName.ifBlank { "Tidak dinyatakan" }}
          No. HP Penjaga: ${draft.guardianPhone.ifBlank { "Tidak dinyatakan" }}
          Hari & Masa: ${draft.dayName}, ${draft.timeRange}

          Catatan Perbincangan Bersama Penjaga / Murid:
          ${draft.notes}

          Arahan Output JSON:
          1. "perkara": Rumusan perbincangan utama antara kaunselor dan penjaga/ibu bapa secara padat dan jelas (2-4 ayat).
          2. "persoalan": Isu, kebimbangan atau persoalan utama yang dibangkitkan oleh penjaga atau dikenal pasti semasa sesi.
          3. "huraianIntervensi": Huraian intervensi sokongan, persetujuan bersama keluarga/sekolah, dan pelan tindakan susulan.
          """.trimIndent()

        ActivityCategory.MESYUARAT ->
          """
          Berdasarkan maklumat mesyuarat dan catatan minit ringkas di bawah, hasilkan rumusan mesyuarat rasmi dalam Bahasa Melayu yang padat dan profesional.

          Kategori Aktiviti: ${draft.category.displayName}
          Nama Mesyuarat: ${draft.clientName.ifBlank { "Tidak dinyatakan" }}
          Hari & Masa: ${draft.dayName}, ${draft.timeRange}

          Catatan / Perbincangan Mesyuarat:
          ${draft.notes}

          Arahan Output JSON:
          1. "perkara": Ringkasan perkara pokok dan agenda utama yang dibincangkan dalam mesyuarat.
          2. "persoalan": Isu berbangkit, persoalan atau perkara yang memerlukan perhatian/penyelarasan lanjut.
          3. "huraianIntervensi": Huraian keputusan mesyuarat, intervensi/tindakan susulan unit bimbingan, dan pihak bertanggungjawab.
          """.trimIndent()

        else ->
          """
          Berdasarkan maklumat dan transkrip/catatan ringkas kaunselor di bawah, hasilkan analisis rumusan profesional dalam Bahasa Melayu yang jelas, empatik, dan beretika.

          Kategori Aktiviti: ${draft.category.displayName}
          Nama Klien / Peserta: ${draft.clientName.ifBlank { "Tidak dinyatakan" }}
          Kelas / Kumpulan: ${draft.className.ifBlank { "Tidak dinyatakan" }}
          Hari & Masa: ${draft.dayName}, ${draft.timeRange}

          Transkrip Perbualan / Catatan Ringkas Kaunselor:
          ${draft.notes}

          Arahan Output JSON:
          1. "perkara": Nyatakan perkara/isu pokok secara ringkas dan tepat (2-4 ayat).
          2. "persoalan": Senaraikan atau huraikan persoalan utama yang timbul dan aspek yang perlu diterokai lanjut.
          3. "huraianIntervensi": Berikan huraian intervensi kaunseling yang sesuai (contoh: teknik penstrukturan semula kognitif, terapi realiti, penilaian keselamatan, rujukan sokongan waris/pakar) dan tindakan susulan yang praktikal.
          """.trimIndent()
      }
    }

    internal fun parseSummaryJson(category: ActivityCategory, jsonText: String): AiSummaryResult {
      val json = JSONObject(jsonText)
      return if (category == ActivityCategory.PROGRAM) {
        AiSummaryResult(
          matter = json.optString("rumusanProgram", json.optString("perkara", "")).trim(),
          questions = json.optString("objektif", json.optString("persoalan", "")).trim(),
          strengthsWeaknesses = json.optString("kelebihanKelemahan", "").trim(),
          intervention = json.optString("huraianIntervensi", "").trim(),
        )
      } else {
        AiSummaryResult(
          matter = json.optString("perkara", "").trim(),
          questions = json.optString("persoalan", "").trim(),
          intervention = json.optString("huraianIntervensi", "").trim(),
        )
      }
    }
  }
}
