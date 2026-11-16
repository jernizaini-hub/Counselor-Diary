package com.example.data.ai

import android.util.Log
import com.example.model.ActivityCategory
import com.example.model.AiSummaryResult
import com.example.model.CounselingRecord
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

class MissingGeminiApiKeyException(
  message: String =
    "Kunci Gemini API belum dimasukkan. Sila ke tab Keselamatan untuk memasukkan kunci Gemini API anda terlebih dahulu."
) : IllegalStateException(message)

class InvalidGeminiApiKeyException(
  message: String,
  cause: Throwable? = null,
) : IllegalStateException(message, cause)

data class HttpResponseData(
  val code: Int,
  val body: String,
)

sealed interface GeminiKeyVerificationResult {
  data class Valid(val activeModel: String) : GeminiKeyVerificationResult

  data class InvalidKey(val message: String) : GeminiKeyVerificationResult

  data class NetworkError(val message: String) : GeminiKeyVerificationResult
}

interface GeminiHttpTransport {
  suspend fun get(url: String, apiKey: String): HttpResponseData

  suspend fun postJson(url: String, apiKey: String, jsonBody: String): HttpResponseData
}

object DefaultGeminiHttpTransport : GeminiHttpTransport {
  private const val CONNECT_TIMEOUT_MS = 15_000
  private const val READ_TIMEOUT_MS = 45_000

  override suspend fun get(url: String, apiKey: String): HttpResponseData =
    withContext(Dispatchers.IO) {
      val connection = (URL(url).openConnection() as HttpURLConnection)
      try {
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("x-goog-api-key", apiKey)
        connection.setRequestProperty("Accept", "application/json")
        readHttpResponse(connection)
      } finally {
        connection.disconnect()
      }
    }

  override suspend fun postJson(url: String, apiKey: String, jsonBody: String): HttpResponseData =
    withContext(Dispatchers.IO) {
      val bodyBytes = jsonBody.toByteArray(StandardCharsets.UTF_8)
      val connection = (URL(url).openConnection() as HttpURLConnection)
      try {
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(bodyBytes.size)
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("x-goog-api-key", apiKey)
        connection.outputStream.use { out ->
          out.write(bodyBytes)
          out.flush()
        }
        readHttpResponse(connection)
      } finally {
        connection.disconnect()
      }
    }

  private fun readHttpResponse(connection: HttpURLConnection): HttpResponseData {
    val code = connection.responseCode
    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
    val body =
      if (stream != null) {
        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
      } else {
        ""
      }
    return HttpResponseData(code = code, body = body)
  }
}

class CounselorAiService(
  private val transport: GeminiHttpTransport = DefaultGeminiHttpTransport,
) {

  suspend fun verifyApiKey(rawKey: String): GeminiKeyVerificationResult {
    val apiKey = rawKey.trim()
    if (apiKey.isEmpty()) {
      return GeminiKeyVerificationResult.InvalidKey("Sila masukkan kunci Gemini API terlebih dahulu.")
    }
    if (apiKey.any { it.isWhitespace() }) {
      return GeminiKeyVerificationResult.InvalidKey(
        "Format kunci API tidak sah kerana mengandungi ruang kosong."
      )
    }

    return withContext(Dispatchers.IO) {
      try {
        val response =
          withTimeout(VERIFY_TIMEOUT_MS) {
            transport.get(
              url = "$GEMINI_API_BASE/models?pageSize=50",
              apiKey = apiKey,
            )
          }
        if (response.code in 200..299) {
          val models = parseAvailableGenerateContentModels(response.body)
          val selected = pickBestFlashModel(models) ?: PREFERRED_MODELS.first()
          GeminiKeyVerificationResult.Valid(selected)
        } else if (isAuthOrKeyError(response.code, response.body)) {
          val detail = extractApiErrorMessage(response.code, response.body)
          GeminiKeyVerificationResult.InvalidKey(
            "Kunci Gemini API ditolak oleh Google ($detail). Sila semak kunci di Google AI Studio."
          )
        } else {
          val detail = extractApiErrorMessage(response.code, response.body)
          GeminiKeyVerificationResult.NetworkError("Pelayan Gemini memulangkan ralat: $detail")
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: TimeoutCancellationException) {
        GeminiKeyVerificationResult.NetworkError(
          "Masa menunggu pengesahan tamat. Sila semak sambungan internet anda."
        )
      } catch (e: IOException) {
        GeminiKeyVerificationResult.NetworkError(
          "Tidak dapat menghubungi pelayan Gemini (${e.localizedMessage ?: "tiada sambungan"})."
        )
      } catch (e: Exception) {
        GeminiKeyVerificationResult.NetworkError(
          e.localizedMessage ?: "Ralat semasa menguji kunci Gemini API."
        )
      }
    }
  }

  suspend fun generateCounselingSummary(
    draft: CounselingRecord,
    rawApiKey: String,
  ): AiSummaryResult {
    val apiKey = rawApiKey.trim()
    if (apiKey.isEmpty()) {
      throw MissingGeminiApiKeyException()
    }

    val requestPayload = buildGenerateContentPayload(draft)

    return withContext(Dispatchers.IO) {
      var lastError: Exception? = null
      val attempted = linkedSetOf<String>()
      val candidatesToTry = ArrayDeque(PREFERRED_MODELS)
      var discoveredModels = false

      while (candidatesToTry.isNotEmpty()) {
        val modelName = candidatesToTry.removeFirst()
        if (!attempted.add(modelName)) continue

        try {
          val response =
            withTimeout(AI_TIMEOUT_MS) {
              transport.postJson(
                url = "$GEMINI_API_BASE/models/$modelName:generateContent",
                apiKey = apiKey,
                jsonBody = requestPayload,
              )
            }

          if (response.code in 200..299) {
            return@withContext extractSummaryFromGeminiResponse(draft.category, response.body)
          }

          if (isAuthOrKeyError(response.code, response.body)) {
            val detail = extractApiErrorMessage(response.code, response.body)
            throw InvalidGeminiApiKeyException(
              "Kunci Gemini API tidak sah atau telah dibatalkan ($detail). Sila semak kunci anda di tab Keselamatan."
            )
          }

          val errorMsg = extractApiErrorMessage(response.code, response.body)
          if (response.code == 404 && !discoveredModels) {
            discoveredModels = true
            discoverFlashModelsSafely(apiKey).forEach { discovered ->
              if (discovered !in attempted) {
                candidatesToTry.addLast(discovered)
              }
            }
            lastError = IllegalStateException(errorMsg)
            continue
          }

          if (response.code == 429 || response.code >= 500 || response.code == 404) {
            Log.w(TAG, "Model $modelName failed (${response.code}): $errorMsg")
            lastError = IllegalStateException(mapHttpStatusToMalayMessage(response.code, errorMsg))
            continue
          }

          throw IllegalStateException(mapHttpStatusToMalayMessage(response.code, errorMsg))
        } catch (e: CancellationException) {
          throw e
        } catch (e: InvalidGeminiApiKeyException) {
          throw e
        } catch (e: TimeoutCancellationException) {
          Log.w(TAG, "Model $modelName timed out", e)
          lastError =
            IllegalStateException(
              "Sambungan Gemini API mengambil masa terlalu lama (melebihi 45 saat). Sila pastikan internet stabil dan cuba semula.",
              e,
            )
        } catch (e: SocketTimeoutException) {
          Log.w(TAG, "Model $modelName socket timed out", e)
          lastError =
            IllegalStateException(
              "Sambungan Gemini API mengambil masa terlalu lama. Sila cuba semula.",
              e,
            )
        } catch (e: IOException) {
          Log.e(TAG, "Network error calling Gemini API ($modelName)", e)
          lastError =
            IllegalStateException(
              "Gagal menghubungi pelayan Gemini API. Sila pastikan internet aktif (${e.localizedMessage ?: "ralat rangkaian"}).",
              e,
            )
        }
      }

      throw lastError
        ?: IllegalStateException("Gagal menjana rumusan AI. Sila cuba semula sebentar lagi.")
    }
  }

  private suspend fun discoverFlashModelsSafely(apiKey: String): List<String> {
    return try {
      val response =
        withTimeout(VERIFY_TIMEOUT_MS) {
          transport.get(
            url = "$GEMINI_API_BASE/models?pageSize=50",
            apiKey = apiKey,
          )
        }
      if (response.code in 200..299) {
        parseAvailableGenerateContentModels(response.body)
      } else {
        emptyList()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      emptyList()
    }
  }

  internal companion object {
    const val TAG = "CounselorAiService"
    const val GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta"
    const val AI_TIMEOUT_MS = 45_000L
    const val VERIFY_TIMEOUT_MS = 15_000L
    val PREFERRED_MODELS =
      listOf(
        "gemini-3.8-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.5-flash",
        "gemini-2.5-flash",
      )

    const val SAFETY_GUIDANCE_MESSAGE =
      "Catatan mengandungi istilah sensitif atau risiko tinggi yang ditahan oleh penapis keselamatan AI. " +
        "Sila guna ungkapan klinikal neutral (cth. 'risiko keselamatan diri / memerlukan saringan segera') untuk janaan AI, " +
        "atau isi kotak rumusan secara manual serta teruskan rujukan SOP kecemasan kaunseling jika perlu."

    private const val SYSTEM_INSTRUCTION_TEXT =
      "Anda ialah pembantu dokumentasi klinikal rasmi untuk Guru Bimbingan dan Kaunseling (Kaunselor Berdaftar) " +
        "di institusi pendidikan Malaysia. Semua input adalah catatan sesi profesional untuk tujuan pendokumentasian, " +
        "penilaian risiko, sokongan psikososial, dan perancangan intervensi keselamatan klien. " +
        "Jangan sekali-kali menjana arahan mencederakan diri; sebaliknya, apabila nota menyebut risiko keselamatan atau " +
        "kecenderungan mencederakan diri, fokuskan huraian intervensi pada protokol saringan keselamatan segera, " +
        "rujukan pakar/waris/pentadbir mengikut SOP kaunseling, serta pelan sokongan protektif yang profesional dan beretika."

    internal fun buildGenerateContentPayload(draft: CounselingRecord): String {
      val prompt = buildCategoryPrompt(draft)
      val schemaJson = buildResponseSchemaJson(draft.category)

      val safetySettings =
        JSONArray().apply {
          listOf(
              "HARM_CATEGORY_HARASSMENT",
              "HARM_CATEGORY_HATE_SPEECH",
              "HARM_CATEGORY_SEXUALLY_EXPLICIT",
              "HARM_CATEGORY_DANGEROUS_CONTENT",
            )
            .forEach { harmCategory ->
              put(
                JSONObject().apply {
                  put("category", harmCategory)
                  put("threshold", "BLOCK_ONLY_HIGH")
                }
              )
            }
        }

      return JSONObject()
        .apply {
          put(
            "systemInstruction",
            JSONObject().apply {
              put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_INSTRUCTION_TEXT)))
            },
          )
          put(
            "contents",
            JSONArray()
              .put(
                JSONObject().apply {
                  put("role", "user")
                  put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }
              ),
          )
          put("safetySettings", safetySettings)
          put(
            "generationConfig",
            JSONObject().apply {
              put("responseMimeType", "application/json")
              put("responseSchema", schemaJson)
            },
          )
        }
        .toString()
    }

    internal fun buildResponseSchemaJson(category: ActivityCategory): JSONObject {
      return if (category == ActivityCategory.PROGRAM) {
        JSONObject().apply {
          put("type", "OBJECT")
          put(
            "properties",
            JSONObject().apply {
              put(
                "rumusanProgram",
                JSONObject().apply {
                  put("type", "STRING")
                  put(
                    "description",
                    "Rumusan Program: ringkasan menyeluruh pelaksanaan, pengisian, penyertaan serta impak program dalam Bahasa Melayu profesional.",
                  )
                },
              )
              put(
                "objektif",
                JSONObject().apply {
                  put("type", "STRING")
                  put(
                    "description",
                    "Objektif Program: senarai objektif utama program yang disasarkan dan tahap pencapaiannya.",
                  )
                },
              )
              put(
                "kelebihanKelemahan",
                JSONObject().apply {
                  put("type", "STRING")
                  put(
                    "description",
                    "Kelebihan & Kelemahan: kekuatan pelaksanaan program serta kelemahan atau ruang penambahbaikan pada masa hadapan.",
                  )
                },
              )
              put(
                "huraianIntervensi",
                JSONObject().apply {
                  put("type", "STRING")
                  put(
                    "description",
                    "Huraian Intervensi: langkah susulan, bimbingan lanjutan atau tindakan penambahbaikan untuk kumpulan sasaran.",
                  )
                },
              )
            },
          )
          put(
            "required",
            JSONArray(
              listOf("rumusanProgram", "objektif", "kelebihanKelemahan", "huraianIntervensi")
            ),
          )
        }
      } else {
        JSONObject().apply {
          put("type", "OBJECT")
          put(
            "properties",
            JSONObject().apply {
              put(
                "perkara",
                JSONObject().apply {
                  put("type", "STRING")
                  put(
                    "description",
                    "Ringkasan padat isu utama, tema, rumusan perbincangan atau perkara pokok yang dijalankan dalam Bahasa Melayu profesional.",
                  )
                },
              )
              put(
                "persoalan",
                JSONObject().apply {
                  put("type", "STRING")
                  put(
                    "description",
                    "Persoalan utama klien, isu berbangkit penjaga/murid, atau soalan penerokaan kritikal untuk tindakan susulan.",
                  )
                },
              )
              put(
                "huraianIntervensi",
                JSONObject().apply {
                  put("type", "STRING")
                  put(
                    "description",
                    "Huraian terperinci intervensi kaunseling, keputusan, pendekatan psikologi, serta pelan tindakan susulan.",
                  )
                },
              )
            },
          )
          put("required", JSONArray(listOf("perkara", "persoalan", "huraianIntervensi")))
        }
      }
    }

    internal fun extractSummaryFromGeminiResponse(
      category: ActivityCategory,
      responseBody: String,
    ): AiSummaryResult {
      val root = JSONObject(responseBody)

      val promptFeedback = root.optJSONObject("promptFeedback")
      val blockReason = promptFeedback?.optString("blockReason", "")?.trim().orEmpty()
      if (blockReason.isNotEmpty() && blockReason != "BLOCK_REASON_UNSPECIFIED") {
        throw IllegalStateException(SAFETY_GUIDANCE_MESSAGE)
      }

      val candidates = root.optJSONArray("candidates")
      val firstCandidate =
        candidates?.takeIf { it.length() > 0 }?.optJSONObject(0)
          ?: throw IllegalStateException("Model AI tidak memulangkan calon jawapan. Sila cuba semula.")

      val finishReason = firstCandidate.optString("finishReason", "").trim()
      if (finishReason in setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII")) {
        throw IllegalStateException(SAFETY_GUIDANCE_MESSAGE)
      }

      val parts = firstCandidate.optJSONObject("content")?.optJSONArray("parts")
      val textBuilder = StringBuilder()
      if (parts != null) {
        for (i in 0 until parts.length()) {
          val part = parts.optJSONObject(i) ?: continue
          if (part.optBoolean("thought", false)) continue
          val partText = part.optString("text", "")
          if (partText.isNotEmpty()) {
            textBuilder.append(partText)
          }
        }
      }

      val rawText = textBuilder.toString().trim()
      if (rawText.isEmpty()) {
        if (finishReason == "MAX_TOKENS") {
          throw IllegalStateException(
            "Catatan terlalu panjang untuk diproses serentak. Sila ringkaskan sedikit catatan dan cuba semula."
          )
        }
        throw IllegalStateException("Model AI memulangkan respons kosong. Sila cuba semula.")
      }

      val cleanedJson = stripOptionalMarkdownFences(rawText)
      return parseSummaryJson(category, cleanedJson)
    }

    internal fun stripOptionalMarkdownFences(raw: String): String {
      val trimmed = raw.trim()
      if (!trimmed.startsWith("```")) return trimmed
      return trimmed
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    }

    internal fun parseAvailableGenerateContentModels(responseBody: String): List<String> {
      return try {
        val root = JSONObject(responseBody)
        val arr = root.optJSONArray("models") ?: return emptyList()
        val names = mutableListOf<String>()
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val methods = obj.optJSONArray("supportedGenerationMethods") ?: continue
          val supportsGenerate =
            (0 until methods.length()).any { idx ->
              methods.optString(idx) == "generateContent"
            }
          if (!supportsGenerate) continue
          val fullName = obj.optString("name", "").trim()
          val shortName = fullName.removePrefix("models/")
          if (shortName.startsWith("gemini-") && !shortName.contains("image") && !shortName.contains("tts")) {
            names.add(shortName)
          }
        }
        names
      } catch (_: Exception) {
        emptyList()
      }
    }

    internal fun pickBestFlashModel(available: List<String>): String? {
      for (preferred in PREFERRED_MODELS) {
        if (preferred in available) return preferred
      }
      return available.firstOrNull { it.contains("flash", ignoreCase = true) }
        ?: available.firstOrNull()
    }

    internal fun isAuthOrKeyError(statusCode: Int, responseBody: String): Boolean {
      val lower = responseBody.lowercase()
      return statusCode == 401 ||
        (statusCode == 400 && (lower.contains("api_key_invalid") || lower.contains("api key not valid"))) ||
        (statusCode == 403 && (lower.contains("api_key") || lower.contains("api key") || lower.contains("consumer")))
    }

    internal fun extractApiErrorMessage(statusCode: Int, responseBody: String): String {
      val trimmed = responseBody.trim()
      if (trimmed.isNotEmpty()) {
        try {
          val err = JSONObject(trimmed).optJSONObject("error")
          val msg = err?.optString("message", "")?.trim().orEmpty()
          val status = err?.optString("status", "")?.trim().orEmpty()
          if (msg.isNotEmpty()) {
            return if (status.isNotEmpty()) "HTTP $statusCode ($status): $msg" else "HTTP $statusCode: $msg"
          }
        } catch (_: Exception) {
          // ignore
        }
        return "HTTP $statusCode: ${trimmed.take(180)}"
      }
      return "HTTP $statusCode daripada pelayan Gemini API"
    }

    private fun mapHttpStatusToMalayMessage(statusCode: Int, detail: String): String =
      when (statusCode) {
        429 ->
          "Had kuota Gemini API sementara dicapai ($detail). Sila tunggu sebentar dan cuba semula."
        404 ->
          "Model Gemini tidak ditemui untuk kunci API ini ($detail)."
        in 500..599 ->
          "Pelayan Gemini API sedang sibuk atau mengalami gangguan sementara ($detail). Sila cuba semula."
        else ->
          "Ralat Gemini API ($detail)."
      }

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
