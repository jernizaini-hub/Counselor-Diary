package com.example.crypto

import android.util.Base64
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedBlob(
  val ciphertextBase64: String,
  val ivBase64: String,
)

object E2eeCryptoManager {
  const val ENCRYPTION_VERSION = "AES-256-GCM-v1"
  private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
  private const val GCM_TAG_BITS = 128
  private const val GCM_IV_BYTES = 12
  private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
  private const val KDF_ITERATIONS = 100_000
  private const val KEY_BITS = 256
  private val secureRandom = SecureRandom()

  fun generateSaltBase64(): String {
    val salt = ByteArray(16)
    secureRandom.nextBytes(salt)
    return Base64.encodeToString(salt, Base64.NO_WRAP)
  }

  fun generateMasterKey(): SecretKey {
    val keyGen = KeyGenerator.getInstance("AES")
    keyGen.init(KEY_BITS, secureRandom)
    return keyGen.generateKey()
  }

  fun masterKeyFromBytes(bytes: ByteArray): SecretKey = SecretKeySpec(bytes, "AES")

  fun deriveKeyFromPin(pin: String, saltBase64: String, domainPrefix: String = "kek"): SecretKey {
    val saltBytes = Base64.decode(saltBase64, Base64.NO_WRAP)
    val domainSalt = "$domainPrefix:".toByteArray(Charsets.UTF_8) + saltBytes
    val spec = PBEKeySpec(pin.toCharArray(), domainSalt, KDF_ITERATIONS, KEY_BITS)
    val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
    val keyBytes = factory.generateSecret(spec).encoded
    spec.clearPassword()
    return SecretKeySpec(keyBytes, "AES")
  }

  fun hashPinVerifier(pin: String, saltBase64: String): String {
    val verifierKey = deriveKeyFromPin(pin, saltBase64, domainPrefix = "verifier")
    return Base64.encodeToString(verifierKey.encoded, Base64.NO_WRAP)
  }

  fun verifyPin(pin: String, saltBase64: String, expectedPinHash: String): Boolean {
    val actual = hashPinVerifier(pin, saltBase64)
    return MessageDigest.isEqual(
      actual.toByteArray(Charsets.UTF_8),
      expectedPinHash.toByteArray(Charsets.UTF_8),
    )
  }

  fun encryptBytes(plainBytes: ByteArray, key: SecretKey): EncryptedBlob {
    val iv = ByteArray(GCM_IV_BYTES)
    secureRandom.nextBytes(iv)
    val cipher = Cipher.getInstance(AES_TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
    val ciphertext = cipher.doFinal(plainBytes)
    return EncryptedBlob(
      ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
      ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
    )
  }

  fun decryptBytes(ciphertextBase64: String, ivBase64: String, key: SecretKey): ByteArray {
    val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
    val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
    val cipher = Cipher.getInstance(AES_TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
    return cipher.doFinal(ciphertext)
  }

  fun encryptString(plaintext: String, key: SecretKey): EncryptedBlob =
    encryptBytes(plaintext.toByteArray(Charsets.UTF_8), key)

  fun decryptString(ciphertextBase64: String, ivBase64: String, key: SecretKey): String =
    String(decryptBytes(ciphertextBase64, ivBase64, key), Charsets.UTF_8)

  fun wrapSecretKey(targetKey: SecretKey, wrappingKey: SecretKey): EncryptedBlob =
    encryptBytes(targetKey.encoded, wrappingKey)

  fun unwrapSecretKey(
    wrappedKeyBase64: String,
    ivBase64: String,
    wrappingKey: SecretKey,
  ): SecretKey {
    val unwrapped = decryptBytes(wrappedKeyBase64, ivBase64, wrappingKey)
    return SecretKeySpec(unwrapped, "AES")
  }

  // --- RFC 6238 TOTP (6-digit, 30s period, HmacSHA1) ---

  private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

  fun generateTotpSecret(): String {
    val bytes = ByteArray(20)
    secureRandom.nextBytes(bytes)
    return base32Encode(bytes)
  }

  fun generateTotpCode(base32Secret: String, timeMillis: Long = System.currentTimeMillis()): String {
    val keyBytes = base32Decode(base32Secret)
    val counter = (timeMillis / 1000L) / 30L
    val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(SecretKeySpec(keyBytes, "HmacSHA1"))
    val hash = mac.doFinal(counterBytes)
    val offset = hash.last().toInt() and 0x0F
    val binary =
      ((hash[offset].toInt() and 0x7F) shl 24) or
        ((hash[offset + 1].toInt() and 0xFF) shl 16) or
        ((hash[offset + 2].toInt() and 0xFF) shl 8) or
        (hash[offset + 3].toInt() and 0xFF)
    val otp = binary % 1_000_000
    return otp.toString().padStart(6, '0')
  }

  fun verifyTotpCode(
    base32Secret: String,
    code: String,
    timeMillis: Long = System.currentTimeMillis(),
  ): Boolean {
    val cleaned = code.trim()
    if (cleaned.length != 6 || !cleaned.all { it.isDigit() }) return false
    for (stepOffset in -1L..1L) {
      val candidate = generateTotpCode(base32Secret, timeMillis + stepOffset * 30_000L)
      if (MessageDigest.isEqual(candidate.toByteArray(), cleaned.toByteArray())) {
        return true
      }
    }
    return false
  }

  fun buildTotpOtpAuthUri(accountEmail: String, base32Secret: String): String {
    val label = "DiariKaunselor:${accountEmail.ifBlank { "kaunselor" }}"
    return "otpauth://totp/$label?secret=$base32Secret&issuer=DiariKaunselor&digits=6&period=30"
  }

  fun generateRecoveryCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    fun block() = (1..4).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
    return "DK-${block()}-${block()}-${block()}"
  }

  fun sha256Base64(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.trim().uppercase().toByteArray())
    return Base64.encodeToString(digest, Base64.NO_WRAP)
  }

  private fun base32Encode(data: ByteArray): String {
    val out = StringBuilder((data.size * 8 + 4) / 5)
    var buffer = 0
    var bitsLeft = 0
    for (b in data) {
      buffer = (buffer shl 8) or (b.toInt() and 0xFF)
      bitsLeft += 8
      while (bitsLeft >= 5) {
        out.append(BASE32_ALPHABET[(buffer shr (bitsLeft - 5)) and 0x1F])
        bitsLeft -= 5
      }
    }
    if (bitsLeft > 0) {
      out.append(BASE32_ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
    }
    return out.toString()
  }

  private fun base32Decode(input: String): ByteArray {
    val clean = input.uppercase().filter { it in BASE32_ALPHABET }
    val out = ArrayList<Byte>((clean.length * 5) / 8)
    var buffer = 0
    var bitsLeft = 0
    for (c in clean) {
      val value = BASE32_ALPHABET.indexOf(c)
      buffer = (buffer shl 5) or value
      bitsLeft += 5
      if (bitsLeft >= 8) {
        out.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
        bitsLeft -= 8
      }
    }
    return out.toByteArray()
  }
}
