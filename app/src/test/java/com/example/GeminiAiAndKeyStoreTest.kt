package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AndroidGeminiApiKeyStore
import com.example.data.ai.CounselorAiService
import com.example.data.ai.GeminiHttpTransport
import com.example.data.ai.GeminiKeyVerificationResult
import com.example.data.ai.HttpResponseData
import com.example.data.ai.InMemoryGeminiApiKeyStore
import com.example.data.ai.InvalidGeminiApiKeyException
import com.example.data.ai.MissingGeminiApiKeyException
import com.example.model.ActivityCategory
import com.example.model.CounselingRecord
import com.example.ui.screens.SecurityBackupScreen
import com.example.ui.theme.DiariKaunselorTheme
import com.example.ui.viewmodel.CloudSyncState
import com.example.ui.viewmodel.GeminiKeyTestState
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiAiAndKeyStoreTest {

  @get:Rule val composeRule = createComposeRule()

  @Test
  fun `androidGeminiApiKeyStore saves loads masks and clears per user`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = AndroidGeminiApiKeyStore(context)

    store.clearKey("u1")
    store.clearKey("u2")

    assertNull(store.loadKey("u1"))
    store.saveKey("u1", "  AIzaSyTestKey1234567890ABCD  ")
    assertEquals("AIzaSyTestKey1234567890ABCD", store.loadKey("u1"))
    assertNull(store.loadKey("u2"))
    assertEquals("AIzaSy••••••ABCD", AndroidGeminiApiKeyStore.maskKey("AIzaSyTestKey1234567890ABCD"))

    store.clearKey("u1")
    assertNull(store.loadKey("u1"))
  }

  @Test
  fun `inMemoryGeminiApiKeyStore trims whitespace and clears on blank`() {
    val store = InMemoryGeminiApiKeyStore()
    store.saveKey("u1", "  AIzaKey123  ")
    assertEquals("AIzaKey123", store.loadKey("u1"))
    store.saveKey("u1", "   ")
    assertNull(store.loadKey("u1"))
  }

  @Test
  fun `generateCounselingSummary throws MissingGeminiApiKeyException when blank`() = runTest {
    val service =
      CounselorAiService(
        transport =
          object : GeminiHttpTransport {
            override suspend fun get(url: String, apiKey: String) =
              error("Should not call network without key")

            override suspend fun postJson(url: String, apiKey: String, jsonBody: String) =
              error("Should not call network without key")
          }
      )

    var thrown: Throwable? = null
    try {
      service.generateCounselingSummary(sampleRecord(), "   ")
    } catch (t: Throwable) {
      thrown = t
    }
    assertTrue(thrown is MissingGeminiApiKeyException)
  }

  @Test
  fun `buildGenerateContentPayload formats REST JSON schema for standard and program categories`() {
    val individualPayload =
      JSONObject(
        CounselorAiService.buildGenerateContentPayload(
          sampleRecord(ActivityCategory.KAUNSELING_INDIVIDU)
        )
      )
    val individualSchema =
      individualPayload.getJSONObject("generationConfig").getJSONObject("responseSchema")
    assertEquals("OBJECT", individualSchema.getString("type"))
    assertTrue(individualSchema.getJSONObject("properties").has("perkara"))
    assertTrue(individualSchema.getJSONObject("properties").has("persoalan"))
    assertTrue(individualSchema.getJSONObject("properties").has("huraianIntervensi"))

    val programPayload =
      JSONObject(CounselorAiService.buildGenerateContentPayload(sampleRecord(ActivityCategory.PROGRAM)))
    val programSchema =
      programPayload.getJSONObject("generationConfig").getJSONObject("responseSchema")
    assertTrue(programSchema.getJSONObject("properties").has("rumusanProgram"))
    assertTrue(programSchema.getJSONObject("properties").has("objektif"))
    assertTrue(programSchema.getJSONObject("properties").has("kelebihanKelemahan"))
    assertTrue(programSchema.getJSONObject("properties").has("huraianIntervensi"))
  }

  @Test
  fun `generateCounselingSummary parses direct Gemini REST response and skips thought parts`() = runTest {
    var capturedUrl = ""
    var capturedKey = ""
    val transport =
      object : GeminiHttpTransport {
        override suspend fun get(url: String, apiKey: String) = HttpResponseData(200, "{}")

        override suspend fun postJson(url: String, apiKey: String, jsonBody: String): HttpResponseData {
          capturedUrl = url
          capturedKey = apiKey
          val body =
            """
            {
              "candidates": [
                {
                  "finishReason": "STOP",
                  "content": {
                    "parts": [
                      {"thought": true, "text": "internal reasoning"},
                      {"text": "{\"perkara\":\"Pelajar mengalami tekanan akademik\",\"persoalan\":\"Bagaimanakah pengurusan masa pelajar?\",\"huraianIntervensi\":\"Jadual belajar berstruktur dan susulan 2 minggu.\"}"}
                    ]
                  }
                }
              ]
            }
            """.trimIndent()
          return HttpResponseData(200, body)
        }
      }

    val service = CounselorAiService(transport)
    val result = service.generateCounselingSummary(sampleRecord(), "AIzaValidKey")

    assertEquals("AIzaValidKey", capturedKey)
    assertTrue(capturedUrl.contains("generativelanguage.googleapis.com/v1beta/models/"))
    assertEquals("Pelajar mengalami tekanan akademik", result.matter)
    assertEquals("Bagaimanakah pengurusan masa pelajar?", result.questions)
    assertEquals("Jadual belajar berstruktur dan susulan 2 minggu.", result.intervention)
  }

  @Test
  fun `generateCounselingSummary discovers fallback model on 404 and succeeds`() = runTest {
    val calledUrls = mutableListOf<String>()
    val transport =
      object : GeminiHttpTransport {
        override suspend fun get(url: String, apiKey: String): HttpResponseData {
          calledUrls.add(url)
          return HttpResponseData(
            200,
            """
            {
              "models": [
                {
                  "name": "models/gemini-4-custom-flash",
                  "supportedGenerationMethods": ["generateContent"]
                }
              ]
            }
            """.trimIndent(),
          )
        }

        override suspend fun postJson(url: String, apiKey: String, jsonBody: String): HttpResponseData {
          calledUrls.add(url)
          return if (url.contains("gemini-4-custom-flash")) {
            HttpResponseData(
              200,
              """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\"perkara\":\"Ringkas\",\"persoalan\":\"Soalan\",\"huraianIntervensi\":\"Intervensi\"}"}]}}]}""",
            )
          } else {
            HttpResponseData(
              404,
              """{"error":{"code":404,"message":"Model not found","status":"NOT_FOUND"}}""",
            )
          }
        }
      }

    val service = CounselorAiService(transport)
    val result = service.generateCounselingSummary(sampleRecord(), "AIzaValidKey")
    assertEquals("Ringkas", result.matter)
    assertTrue(calledUrls.any { it.contains("gemini-4-custom-flash:generateContent") })
  }

  @Test
  fun `generateCounselingSummary throws InvalidGeminiApiKeyException on 400 API_KEY_INVALID`() =
    runTest {
      val transport =
        object : GeminiHttpTransport {
          override suspend fun get(url: String, apiKey: String) = HttpResponseData(200, "{}")

          override suspend fun postJson(url: String, apiKey: String, jsonBody: String) =
            HttpResponseData(
              400,
              """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}""",
            )
        }

      var thrown: Throwable? = null
      try {
        CounselorAiService(transport).generateCounselingSummary(sampleRecord(), "AIzaBadKey")
      } catch (t: Throwable) {
        thrown = t
      }
      assertTrue(thrown is InvalidGeminiApiKeyException)
    }

  @Test
  fun `verifyApiKey distinguishes Valid InvalidKey and NetworkError`() = runTest {
    val validService =
      CounselorAiService(
        object : GeminiHttpTransport {
          override suspend fun get(url: String, apiKey: String) =
            HttpResponseData(
              200,
              """{"models":[{"name":"models/gemini-3.8-flash","supportedGenerationMethods":["generateContent"]}]}""",
            )

          override suspend fun postJson(url: String, apiKey: String, jsonBody: String) =
            HttpResponseData(200, "{}")
        }
      )
    val valid = validService.verifyApiKey("AIzaGoodKey")
    assertTrue(valid is GeminiKeyVerificationResult.Valid)
    assertEquals("gemini-3.8-flash", (valid as GeminiKeyVerificationResult.Valid).activeModel)

    val invalidService =
      CounselorAiService(
        object : GeminiHttpTransport {
          override suspend fun get(url: String, apiKey: String) =
            HttpResponseData(
              400,
              """{"error":{"code":400,"message":"API key not valid.","status":"INVALID_ARGUMENT"}}""",
            )

          override suspend fun postJson(url: String, apiKey: String, jsonBody: String) =
            HttpResponseData(200, "{}")
        }
      )
    assertTrue(invalidService.verifyApiKey("AIzaWrong") is GeminiKeyVerificationResult.InvalidKey)

    val offlineService =
      CounselorAiService(
        object : GeminiHttpTransport {
          override suspend fun get(url: String, apiKey: String): HttpResponseData =
            throw IOException("No internet")

          override suspend fun postJson(url: String, apiKey: String, jsonBody: String) =
            HttpResponseData(200, "{}")
        }
      )
    assertTrue(offlineService.verifyApiKey("AIzaKey") is GeminiKeyVerificationResult.NetworkError)
  }

  @Test
  fun `securityBackupScreen renders Gemini API key card and triggers save and clear`() {
    var savedSubmitted = ""
    var cleared = false

    composeRule.setContent {
      DiariKaunselorTheme {
        SecurityBackupScreen(
          userEmail = "kaunselor@sekolah.edu.my",
          cloudSyncState = CloudSyncState.Synced(System.currentTimeMillis(), 2),
          onVerifyCloudSync = {},
          onBuildEncryptedBackupJson = { "{}" },
          onSignOut = {},
          savedGeminiApiKey = "AIzaExisting1234567890",
          geminiKeyTestState = GeminiKeyTestState.Success("Kunci sah"),
          onSaveAndTestGeminiApiKey = { savedSubmitted = it },
          onClearGeminiApiKey = { cleared = true },
        )
      }
    }

    composeRule.onNodeWithTag("gemini_api_key_card").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("gemini_key_status_success").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Aktif").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("clear_gemini_key_button").performScrollTo().performClick()
    assertTrue(cleared)

    composeRule.onNodeWithTag("gemini_api_key_input").performScrollTo().performTextInput("AIzaNewKey12345")
    composeRule.onNodeWithTag("save_test_gemini_key_button").performScrollTo().performClick()
    assertEquals("AIzaNewKey12345", savedSubmitted)
  }

  private fun sampleRecord(category: ActivityCategory = ActivityCategory.KAUNSELING_INDIVIDU) =
    CounselingRecord(
      id = "rec-1",
      category = category,
      clientName = "Ahmad",
      className = "4 Amanah",
      dayName = "Isnin",
      timeRange = "09:00 - 09:45",
      durationMinutes = 45,
      notes = "Pelajar berkongsi tekanan pelajaran.",
    )
}
