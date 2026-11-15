package com.example.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.model.VaultConfigDto
import com.google.firebase.Timestamp
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

data class CachedVaultEntry(
  val config: VaultConfigDto,
  val masterKey: SecretKey,
)

interface VaultKeyCache {
  fun load(userId: String): CachedVaultEntry?

  fun save(userId: String, config: VaultConfigDto, masterKey: SecretKey)

  fun clear(userId: String)
}

object NoOpVaultKeyCache : VaultKeyCache {
  override fun load(userId: String): CachedVaultEntry? = null

  override fun save(userId: String, config: VaultConfigDto, masterKey: SecretKey) = Unit

  override fun clear(userId: String) = Unit
}

class AndroidVaultKeyCache(context: Context) : VaultKeyCache {
  private val prefs =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun load(userId: String): CachedVaultEntry? {
    val raw = prefs.getString(keyFor(userId), null) ?: return null
    return try {
      val json = JSONObject(raw)
      val wrappingKey = getOrCreateWrappingKey() ?: return null
      val encryptedMaster = Base64.decode(json.getString("localWrappedKey"), Base64.NO_WRAP)
      val iv = Base64.decode(json.getString("localWrappedIv"), Base64.NO_WRAP)

      val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
      val masterBytes = cipher.doFinal(encryptedMaster)
      val masterKey = E2eeCryptoManager.masterKeyFromBytes(masterBytes)

      val createdAt =
        if (json.has("createdAtSec")) {
          Timestamp(json.getLong("createdAtSec"), json.getInt("createdAtNano"))
        } else {
          null
        }

      val config =
        VaultConfigDto(
          userId = userId,
          pinHash = json.getString("pinHash"),
          kdfSalt = json.getString("kdfSalt"),
          wrappedMasterKey = json.getString("wrappedMasterKey"),
          wrappedMasterKeyIv = json.getString("wrappedMasterKeyIv"),
          totpEnabled = false,
          createdAt = createdAt,
          updatedAt = createdAt,
        )
      CachedVaultEntry(config = config, masterKey = masterKey)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to load cached vault key; clearing stale entry", e)
      clear(userId)
      null
    }
  }

  override fun save(userId: String, config: VaultConfigDto, masterKey: SecretKey) {
    try {
      val wrappingKey = getOrCreateWrappingKey() ?: return
      val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
      val encryptedMaster = cipher.doFinal(masterKey.encoded)
      val iv = cipher.iv

      val json =
        JSONObject().apply {
          put("localWrappedKey", Base64.encodeToString(encryptedMaster, Base64.NO_WRAP))
          put("localWrappedIv", Base64.encodeToString(iv, Base64.NO_WRAP))
          put("pinHash", config.pinHash)
          put("kdfSalt", config.kdfSalt)
          put("wrappedMasterKey", config.wrappedMasterKey)
          put("wrappedMasterKeyIv", config.wrappedMasterKeyIv)
          config.createdAt?.let { ts ->
            put("createdAtSec", ts.seconds)
            put("createdAtNano", ts.nanoseconds)
          }
        }
      prefs.edit().putString(keyFor(userId), json.toString()).apply()
    } catch (e: Exception) {
      Log.w(TAG, "Unable to cache vault key in Android Keystore", e)
    }
  }

  override fun clear(userId: String) {
    prefs.edit().remove(keyFor(userId)).apply()
  }

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

  private fun keyFor(userId: String): String = "vault_entry_$userId"

  private companion object {
    const val TAG = "LocalVaultKeyStore"
    const val PREFS_NAME = "diari_kaunselor_vault_cache"
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val KEYSTORE_ALIAS = "dk_vault_cache_wrap_v1"
    const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_TAG_BITS = 128
  }
}
