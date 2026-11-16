package com.example.data.ai

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

interface GeminiApiKeyStore {
  fun loadKey(userId: String): String?

  fun saveKey(userId: String, apiKey: String)

  fun clearKey(userId: String)
}

object NoOpGeminiApiKeyStore : GeminiApiKeyStore {
  override fun loadKey(userId: String): String? = null

  override fun saveKey(userId: String, apiKey: String) = Unit

  override fun clearKey(userId: String) = Unit
}

class InMemoryGeminiApiKeyStore(initialKeys: Map<String, String> = emptyMap()) : GeminiApiKeyStore {
  private val keys = initialKeys.toMutableMap()

  override fun loadKey(userId: String): String? = keys[userId]

  override fun saveKey(userId: String, apiKey: String) {
    val trimmed = apiKey.trim()
    if (trimmed.isEmpty()) {
      keys.remove(userId)
    } else {
      keys[userId] = trimmed
    }
  }

  override fun clearKey(userId: String) {
    keys.remove(userId)
  }
}

/**
 * Stores the user's personal Gemini API key encrypted with an AES-256-GCM key
 * held inside AndroidKeyStore so the key stays private on-device.
 */
class AndroidGeminiApiKeyStore(context: Context) : GeminiApiKeyStore {
  private val prefs =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun loadKey(userId: String): String? {
    val raw = prefs.getString(keyFor(userId), null) ?: return null
    return try {
      val json = JSONObject(raw)
      if (json.optBoolean("plainFallback", false)) {
        return json.optString("value", "").trim().takeIf { it.isNotEmpty() }
      }
      val wrappingKey = getOrCreateWrappingKey() ?: return null
      val ciphertext = Base64.decode(json.getString("ciphertext"), Base64.NO_WRAP)
      val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
      val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
      val plainBytes = cipher.doFinal(ciphertext)
      String(plainBytes, StandardCharsets.UTF_8).trim().takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to decrypt stored Gemini API key", e)
      null
    }
  }

  override fun saveKey(userId: String, apiKey: String) {
    val trimmed = apiKey.trim()
    if (trimmed.isEmpty()) {
      clearKey(userId)
      return
    }
    try {
      val payload =
        if (isAndroidKeystoreAvailable()) {
          val wrappingKey = getOrCreateWrappingKey()
          if (wrappingKey != null) {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
            val encrypted = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))
            JSONObject().apply {
              put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
              put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            }
          } else {
            fallbackPayload(trimmed)
          }
        } else {
          fallbackPayload(trimmed)
        }
      prefs.edit().putString(keyFor(userId), payload.toString()).commit()
    } catch (e: Exception) {
      Log.w(TAG, "Keystore encryption unavailable; storing in private app prefs", e)
      prefs.edit().putString(keyFor(userId), fallbackPayload(trimmed).toString()).commit()
    }
  }

  override fun clearKey(userId: String) {
    prefs.edit().remove(keyFor(userId)).commit()
  }

  private fun fallbackPayload(apiKey: String): JSONObject =
    JSONObject().apply {
      put("plainFallback", true)
      put("value", apiKey)
    }

  private fun isAndroidKeystoreAvailable(): Boolean =
    !Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

  private fun getOrCreateWrappingKey(): SecretKey? {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val existing = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
    if (existing != null) return existing

    val keyGenerator =
      KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec =
      KeyGenParameterSpec.Builder(
          KEYSTORE_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  private fun keyFor(userId: String): String = "gemini_key_$userId"

  companion object {
    private const val TAG = "GeminiApiKeyStore"
    private const val PREFS_NAME = "diari_kaunselor_ai_prefs"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "dk_gemini_api_key_wrap_v1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    fun maskKey(apiKey: String?): String? {
      val trimmed = apiKey?.trim().orEmpty()
      if (trimmed.isEmpty()) return null
      return if (trimmed.length <= 10) {
        "${trimmed.take(2)}••••${trimmed.takeLast(2)}"
      } else {
        "${trimmed.take(6)}••••••${trimmed.takeLast(4)}"
      }
    }
  }
}
