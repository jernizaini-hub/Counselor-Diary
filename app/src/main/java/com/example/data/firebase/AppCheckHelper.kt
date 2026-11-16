package com.example.data.firebase

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaTasksClient
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.appcheck.AppCheckProvider
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val TOKEN_STORE_PREFIX = "com.google.firebase.appcheck.store."
private const val STORED_TOKEN_KEY = "com.google.firebase.appcheck.APP_CHECK_TOKEN"
private const val STORED_TOKEN_TYPE_KEY = "com.google.firebase.appcheck.TOKEN_TYPE"
private const val HAS_INTENT_DEBUG_TOKEN_KEY = "dk_has_intent_debug_token"
private const val LAST_PROVIDER_MODE_KEY = "dk_last_app_check_mode"
private const val MODE_DEBUG = "debug"
private const val MODE_RECAPTCHA = "recaptcha"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"
private const val RECAPTCHA_APP_CHECK_ACTION = "fire_app_check"
private const val TOKEN_FETCH_TIMEOUT_MS = 20_000L
private const val RECAPTCHA_TIMEOUT_MS = 12_000L
private const val HTTP_TIMEOUT_MS = 15_000
private const val DEFAULT_TOKEN_TTL_MILLIS = 3_600_000L
private const val TOKEN_EXPIRY_BUFFER_MILLIS = 30_000L
private const val DUMMY_ERROR_TOKEN_PREFIX = "eyJlcnJvciI6"

private val appCheckMutex = Mutex()
@Volatile private var installedModeInProcess: String? = null

fun configureAppCheck(context: Context, intent: Intent?) {
  try {
    val appContext = context.applicationContext ?: context
    if (!ensureFirebaseAppInitialized(appContext)) {
      Log.w(TAG, "FirebaseApp is not initialized (missing google-services.json); skipping App Check.")
      return
    }

    val persistenceKey = FirebaseApp.getInstance().persistenceKey
    val debugPrefs =
      appContext.getSharedPreferences(
        "$DEBUG_STORE_PREFIX$persistenceKey",
        Context.MODE_PRIVATE,
      )

    val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
    if (BuildConfig.DEBUG && !debugToken.isNullOrEmpty()) {
      // CRITICAL: MUST use commit() instead of apply() so the secret is written
      // synchronously before DebugAppCheckProviderFactory reads it.
      debugPrefs.edit()
        .putString(DEBUG_SECRET_KEY, debugToken)
        .putBoolean(HAS_INTENT_DEBUG_TOKEN_KEY, true)
        .commit()
      Log.d(TAG, "App Check debug secret configured successfully.")
    } else if (BuildConfig.DEBUG) {
      Log.w(TAG, "No debug token found in Intent extra.")
    }

    val hasStoredIntentDebugToken =
      BuildConfig.DEBUG && debugPrefs.getBoolean(HAS_INTENT_DEBUG_TOKEN_KEY, false)
    val shouldUseDebugProvider =
      BuildConfig.DEBUG &&
        (!debugToken.isNullOrEmpty() || hasStoredIntentDebugToken || isEmulatorOrTestRuntime())

    val siteKey = resolveRecaptchaSiteKey(appContext)
    val targetMode =
      if (shouldUseDebugProvider || siteKey.isNullOrBlank()) MODE_DEBUG else MODE_RECAPTCHA

    val previousMode = debugPrefs.getString(LAST_PROVIDER_MODE_KEY, null)
    val modeChanged = previousMode != null && previousMode != targetMode

    if (modeChanged || previousMode == null) {
      // Clear any stale cached App Check token (e.g. from an older APK build) synchronously
      // BEFORE FirebaseAppCheck.getInstance() reads stored tokens on cold start.
      clearPersistedAppCheckToken(appContext, persistenceKey)
      debugPrefs.edit().putString(LAST_PROVIDER_MODE_KEY, targetMode).commit()
      installedModeInProcess = null
    } else if (debugToken.isNullOrEmpty() && installedModeInProcess == targetMode) {
      // Avoid recreating RecaptchaTasksClient on activity recreation if already active with the same mode
      // and no fresh debug token Intent extra arrived.
      return
    }

    installProviderFactory(FirebaseAppCheck.getInstance(), targetMode, siteKey)
  } catch (e: Exception) {
    Log.e(TAG, "Failed to initialize Firebase App Check", e)
  }
}

/**
 * Ensures a valid App Check token is available before AI Logic requests.
 * Uses a resilient non-locking reCAPTCHA provider on physical devices so transient cold-start
 * or background listener failures never trigger a 24-hour SDK RetryManager "Too many attempts." lockout.
 */
suspend fun ensureValidAppCheckToken(
  context: Context,
  forceRefreshAndReset: Boolean = false,
) {
  val appContext = context.applicationContext ?: context
  if (!ensureFirebaseAppInitialized(appContext)) return

  appCheckMutex.withLock {
    withContext(Dispatchers.IO) {
      val appCheck = FirebaseAppCheck.getInstance()
      var firstError: Throwable? = null

      if (!forceRefreshAndReset) {
        try {
          val token = withTimeout(TOKEN_FETCH_TIMEOUT_MS) { appCheck.getAppCheckToken(false).await() }
          if (isUsableAppCheckToken(token)) {
            return@withContext
          }
          firstError = IllegalStateException("Token App Check kosong atau hampir tamat tempoh.")
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          firstError = e
          Log.w(TAG, "Cached/initial App Check token check failed; resetting & trying once", e)
        }
        delay(350L)
      }

      var refreshError: Throwable? = null
      try {
        reinstallActiveProvider(appContext, appCheck)
        val refreshed =
          withTimeout(TOKEN_FETCH_TIMEOUT_MS) { appCheck.getAppCheckToken(true).await() }
        if (isUsableAppCheckToken(refreshed)) {
          Log.d(TAG, "Fresh App Check token obtained.")
          return@withContext
        }
        refreshError = IllegalStateException("Token App Check baharu tidak sah atau kosong.")
      } catch (e: CancellationException) {
        throw e
      } catch (e: TimeoutCancellationException) {
        refreshError = e
        Log.w(TAG, "App Check token refresh timed out", e)
      } catch (e: Exception) {
        refreshError = e
        Log.w(TAG, "App Check token refresh failed", e)
      }

      val bestError = chooseMostInformativeError(firstError, refreshError)
      val detail = formatDiagnosticError(bestError)
      throw IllegalStateException(
        "Pengesahan keselamatan aplikasi (Firebase App Check) gagal: $detail. " +
          "Sila pastikan internet aktif tanpa sekatan VPN/Private DNS, tarikh & masa telefon adalah automatik, " +
          "dan Google Play Services dikemas kini.",
        bestError,
      )
    }
  }
}

private fun reinstallActiveProvider(appContext: Context, appCheck: FirebaseAppCheck) {
  val persistenceKey = FirebaseApp.getInstance().persistenceKey
  val debugPrefs =
    appContext.getSharedPreferences(
      "$DEBUG_STORE_PREFIX$persistenceKey",
      Context.MODE_PRIVATE,
    )
  val hasStoredIntentDebugToken =
    BuildConfig.DEBUG && debugPrefs.getBoolean(HAS_INTENT_DEBUG_TOKEN_KEY, false)
  val shouldUseDebugProvider =
    BuildConfig.DEBUG && (hasStoredIntentDebugToken || isEmulatorOrTestRuntime())
  val siteKey = resolveRecaptchaSiteKey(appContext)
  val targetMode =
    if (shouldUseDebugProvider || siteKey.isNullOrBlank()) MODE_DEBUG else MODE_RECAPTCHA

  clearPersistedAppCheckToken(appContext, persistenceKey)
  installedModeInProcess = null
  debugPrefs.edit().putString(LAST_PROVIDER_MODE_KEY, targetMode).commit()
  installProviderFactory(appCheck, targetMode, siteKey)
}

private fun clearPersistedAppCheckToken(appContext: Context, persistenceKey: String) {
  appContext
    .getSharedPreferences("$TOKEN_STORE_PREFIX$persistenceKey", Context.MODE_PRIVATE)
    .edit()
    .remove(STORED_TOKEN_KEY)
    .remove(STORED_TOKEN_TYPE_KEY)
    .commit()
}

private fun installProviderFactory(
  appCheck: FirebaseAppCheck,
  targetMode: String,
  siteKey: String?,
) {
  if (targetMode == MODE_RECAPTCHA && !siteKey.isNullOrBlank()) {
    appCheck.installAppCheckProviderFactory(
      ResilientRecaptchaAppCheckProviderFactory(siteKey)
    )
    installedModeInProcess = MODE_RECAPTCHA
    Log.d(TAG, "ResilientRecaptchaAppCheckProviderFactory installed.")
  } else {
    appCheck.installAppCheckProviderFactory(
      DebugAppCheckProviderFactory.getInstance()
    )
    installedModeInProcess = MODE_DEBUG
    Log.d(TAG, "DebugAppCheckProviderFactory installed.")
  }
}

/**
 * Custom AppCheckProviderFactory using the official public AppCheckProvider extension contract.
 * Avoids the Firebase SDK's singleton ProviderMultiResourceComponent and 24-hour RetryManager lockout,
 * clears stale RecaptchaTasksClient instances on failure, and surfaces exact HTTP error details.
 */
internal class ResilientRecaptchaAppCheckProviderFactory(
  private val siteKey: String,
) : AppCheckProviderFactory {
  override fun create(firebaseApp: FirebaseApp): AppCheckProvider =
    ResilientRecaptchaAppCheckProvider(firebaseApp, siteKey)
}

internal class ResilientAppCheckToken(
  private val rawToken: String,
  private val expireTimestampMillis: Long,
) : AppCheckToken() {
  override fun getToken(): String = rawToken

  override fun getExpireTimeMillis(): Long = expireTimestampMillis
}

internal class ResilientRecaptchaAppCheckProvider(
  private val firebaseApp: FirebaseApp,
  private val siteKey: String,
) : AppCheckProvider {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val stateMutex = Mutex()
  @Volatile private var cachedClient: RecaptchaTasksClient? = null
  @Volatile private var inFlightStandardTask: Task<AppCheckToken>? = null
  @Volatile private var inFlightLimitedTask: Task<AppCheckToken>? = null

  override fun getToken(): Task<AppCheckToken> = launchCoalescedFetch(limitedUse = false)

  override fun getLimitedUseToken(): Task<AppCheckToken> = launchCoalescedFetch(limitedUse = true)

  private fun launchCoalescedFetch(limitedUse: Boolean): Task<AppCheckToken> {
    synchronized(this) {
      val existing = if (limitedUse) inFlightLimitedTask else inFlightStandardTask
      if (existing != null && !existing.isComplete) {
        return existing
      }
      val tcs = TaskCompletionSource<AppCheckToken>()
      val task = tcs.task
      if (limitedUse) {
        inFlightLimitedTask = task
      } else {
        inFlightStandardTask = task
      }

      scope.launch {
        try {
          val token = fetchFreshToken(limitedUse)
          tcs.trySetResult(token)
        } catch (e: Exception) {
          tcs.trySetException(e)
        } finally {
          synchronized(this@ResilientRecaptchaAppCheckProvider) {
            if (limitedUse) {
              if (inFlightLimitedTask === task) inFlightLimitedTask = null
            } else {
              if (inFlightStandardTask === task) inFlightStandardTask = null
            }
          }
        }
      }
      return task
    }
  }

  private suspend fun fetchFreshToken(limitedUse: Boolean): AppCheckToken =
    stateMutex.withLock {
      val appContext = firebaseApp.applicationContext
      val application =
        (appContext as? Application)
          ?: (appContext.applicationContext as? Application)
          ?: throw FirebaseException("Konteks aplikasi Android tidak tersedia untuk reCAPTCHA Enterprise.")

      val options = firebaseApp.options
      val projectId =
        options.projectId?.takeIf { it.isNotBlank() }
          ?: throw FirebaseException("Firebase Project ID tiada dalam konfigurasi.")
      val appId = options.applicationId
      val apiKey = options.apiKey

      val recaptchaToken = acquireRecaptchaToken(application)
      exchangeRecaptchaForAppCheckToken(
        context = application,
        projectId = projectId,
        appId = appId,
        apiKey = apiKey,
        recaptchaToken = recaptchaToken,
        limitedUse = limitedUse,
      )
    }

  private suspend fun acquireRecaptchaToken(application: Application): String {
    var hadCachedClient = cachedClient != null
    var lastError: Exception? = null
    val attempts = if (hadCachedClient) 2 else 1

    repeat(attempts) { attempt ->
      try {
        val client =
          cachedClient
            ?: withTimeout(RECAPTCHA_TIMEOUT_MS) {
                Recaptcha.fetchTaskClient(application, siteKey).await()
              }
              .also { cachedClient = it }
        return withTimeout(RECAPTCHA_TIMEOUT_MS) {
          client.executeTask(RecaptchaAction.custom(RECAPTCHA_APP_CHECK_ACTION)).await()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        lastError = e
        // Never keep a failed or stale RecaptchaTasksClient cached
        cachedClient = null
        if (attempt == 0 && hadCachedClient) {
          hadCachedClient = false
          Log.w(TAG, "Cached RecaptchaTasksClient failed; re-initializing client once", e)
        } else {
          throw FirebaseException(
            "reCAPTCHA Enterprise gagal: ${e.localizedMessage ?: e.javaClass.simpleName}",
            e,
          )
        }
      }
    }

    val fallbackMsg =
      "reCAPTCHA Enterprise gagal: ${lastError?.localizedMessage ?: "Ralat tidak diketahui"}"
    throw if (lastError != null) {
      FirebaseException(fallbackMsg, lastError)
    } else {
      FirebaseException(fallbackMsg)
    }
  }

  private suspend fun exchangeRecaptchaForAppCheckToken(
    context: Context,
    projectId: String,
    appId: String,
    apiKey: String,
    recaptchaToken: String,
    limitedUse: Boolean,
  ): AppCheckToken =
    withContext(Dispatchers.IO) {
      val endpoint =
        "https://firebaseappcheck.googleapis.com/v1/projects/$projectId/apps/$appId:exchangeRecaptchaEnterpriseToken?key=$apiKey"
      val payloadBytes =
        buildExchangeRecaptchaPayloadJson(
          recaptchaToken = recaptchaToken,
          limitedUse = limitedUse,
        ).toByteArray(StandardCharsets.UTF_8)

      val url = URL(endpoint)
      val connection = (url.openConnection() as HttpURLConnection)
      try {
        connection.requestMethod = "POST"
        connection.connectTimeout = HTTP_TIMEOUT_MS
        connection.readTimeout = HTTP_TIMEOUT_MS
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(payloadBytes.size)
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.setRequestProperty("X-Android-Package", context.packageName)
        getPackageCertificateSha1Hex(context)?.let { certHex ->
          connection.setRequestProperty("X-Android-Cert", certHex)
        }

        connection.outputStream.use { out ->
          out.write(payloadBytes)
          out.flush()
        }

        val responseCode = connection.responseCode
        val stream =
          if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val responseBody =
          if (stream != null) {
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
          } else {
            ""
          }

        if (responseCode !in 200..299) {
          val diagnostic = extractAppCheckErrorMessage(responseCode, responseBody)
          Log.e(TAG, "App Check token exchange failed: $diagnostic")
          throw FirebaseException(diagnostic)
        }

        val json = JSONObject(responseBody)
        val appCheckToken = json.optString("token", "").trim()
        if (appCheckToken.isBlank() || isDummyOrInvalidAppCheckToken(appCheckToken)) {
          throw FirebaseException("Pelayan App Check memulangkan token kosong.")
        }
        val ttlMillis = parseAppCheckTtlMillis(json.optString("ttl", ""), appCheckToken)
        ResilientAppCheckToken(
          rawToken = appCheckToken,
          expireTimestampMillis = System.currentTimeMillis() + ttlMillis,
        )
      } finally {
        connection.disconnect()
      }
    }
}

internal fun buildExchangeRecaptchaPayloadJson(
  recaptchaToken: String,
  limitedUse: Boolean,
): String =
  JSONObject()
    .apply {
      put("recaptchaEnterpriseToken", recaptchaToken)
      if (limitedUse) {
        put("limited_use", true)
      }
    }
    .toString()

internal fun isUsableAppCheckToken(token: AppCheckToken?): Boolean {
  if (token == null) return false
  val value = token.token
  if (value.isBlank() || isDummyOrInvalidAppCheckToken(value)) return false
  return token.expireTimeMillis > System.currentTimeMillis() + TOKEN_EXPIRY_BUFFER_MILLIS
}

internal fun isDummyOrInvalidAppCheckToken(rawToken: String?): Boolean {
  val trimmed = rawToken?.trim().orEmpty()
  if (trimmed.isEmpty()) return true
  if (trimmed.startsWith(DUMMY_ERROR_TOKEN_PREFIX)) return true
  return false
}

internal fun parseAppCheckTtlMillis(ttlRaw: String?, rawJwtToken: String? = null): Long {
  val cleaned = ttlRaw?.trim()?.removeSuffix("s")?.trim()
  val seconds = cleaned?.toDoubleOrNull()
  if (seconds != null && seconds > 0.0) {
    return (seconds * 1000.0).toLong()
  }
  if (!rawJwtToken.isNullOrBlank()) {
    try {
      val parts = rawJwtToken.split(".")
      if (parts.size >= 2) {
        val decoded =
          String(
            Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
            StandardCharsets.UTF_8,
          )
        val claims = JSONObject(decoded)
        val iat = claims.optLong("iat", 0L)
        val exp = claims.optLong("exp", 0L)
        if (exp > iat && iat > 0L) {
          return (exp - iat) * 1000L
        }
      }
    } catch (_: Exception) {
      // Ignore malformed JWT claims and fall back to default TTL
    }
  }
  return DEFAULT_TOKEN_TTL_MILLIS
}

internal fun extractAppCheckErrorMessage(statusCode: Int, responseBody: String?): String {
  val body = responseBody?.trim().orEmpty()
  if (body.isNotEmpty()) {
    try {
      val root = JSONObject(body)
      val errObj = root.optJSONObject("error")
      val msg = errObj?.optString("message")?.trim().orEmpty()
      val status = errObj?.optString("status")?.trim().orEmpty()
      if (msg.isNotEmpty()) {
        return if (status.isNotEmpty()) "HTTP $statusCode ($status): $msg" else "HTTP $statusCode: $msg"
      }
    } catch (_: Exception) {
      // Fall back to compact body text
    }
    return "HTTP $statusCode: ${body.take(180)}"
  }
  return "HTTP $statusCode daripada pelayan Firebase App Check"
}

internal fun chooseMostInformativeError(first: Throwable?, second: Throwable?): Throwable {
  if (second == null) return first ?: IllegalStateException("Ralat App Check tidak diketahui")
  if (first == null) return second
  val secondMsg = second.message.orEmpty().lowercase(Locale.ROOT)
  val firstMsg = first.message.orEmpty().lowercase(Locale.ROOT)
  val secondIsGenericThrottle =
    secondMsg.contains("too many attempts") || second is TimeoutCancellationException
  val firstHasSpecificHttpOrRecaptcha =
    firstMsg.contains("http ") ||
      firstMsg.contains("code:") ||
      firstMsg.contains("recaptcha") ||
      firstMsg.contains("403") ||
      firstMsg.contains("400")
  return if (secondIsGenericThrottle && firstHasSpecificHttpOrRecaptcha) first else second
}

internal fun formatDiagnosticError(error: Throwable): String {
  if (error is TimeoutCancellationException) {
    return "Masa menunggu sambungan ke Google Play Services / pelayan tamat (timeout)"
  }
  val raw = error.localizedMessage?.trim().orEmpty()
  return if (raw.isNotEmpty()) raw else error.javaClass.simpleName
}

@Suppress("DEPRECATION")
internal fun getPackageCertificateSha1Hex(context: Context): String? {
  return try {
    val pm = context.packageManager
    val packageName = context.packageName
    val signatureBytes: ByteArray? =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signingInfo = info.signingInfo
        val signer =
          signingInfo?.apkContentsSigners?.firstOrNull()
            ?: signingInfo?.signingCertificateHistory?.firstOrNull()
        signer?.toByteArray()
      } else {
        val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        info.signatures?.firstOrNull()?.toByteArray()
      }
    if (signatureBytes == null) return null
    val digest = MessageDigest.getInstance("SHA-1").digest(signatureBytes)
    digest.joinToString("") { byte -> "%02X".format(Locale.US, byte) }
  } catch (e: Exception) {
    Log.w(TAG, "Unable to compute package SHA-1 certificate fingerprint", e)
    null
  }
}

private fun ensureFirebaseAppInitialized(appContext: Context): Boolean {
  if (FirebaseApp.getApps(appContext).isEmpty()) {
    FirebaseApp.initializeApp(appContext)
  }
  return FirebaseApp.getApps(appContext).isNotEmpty()
}

private fun resolveRecaptchaSiteKey(appContext: Context): String? {
  val resId =
    appContext.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", appContext.packageName)
  if (resId == 0) {
    Log.e(TAG, "recaptcha_site_key string resource not found in package.")
    return null
  }
  val siteKey = appContext.getString(resId)
  if (siteKey.isBlank()) {
    Log.e(TAG, "recaptcha_site_key resource string is blank.")
    return null
  }
  return siteKey
}

private fun isEmulatorOrTestRuntime(): Boolean {
  val fingerprint = Build.FINGERPRINT
  val model = Build.MODEL
  val manufacturer = Build.MANUFACTURER
  val brand = Build.BRAND
  val device = Build.DEVICE
  val product = Build.PRODUCT
  val hardware = Build.HARDWARE
  return fingerprint.startsWith("generic") ||
    fingerprint.startsWith("unknown") ||
    fingerprint.equals("robolectric", ignoreCase = true) ||
    model.contains("google_sdk", ignoreCase = true) ||
    model.contains("Emulator", ignoreCase = true) ||
    model.contains("Android SDK built for", ignoreCase = true) ||
    manufacturer.contains("Genymotion", ignoreCase = true) ||
    hardware.contains("goldfish", ignoreCase = true) ||
    hardware.contains("ranchu", ignoreCase = true) ||
    product.contains("sdk_gphone", ignoreCase = true) ||
    product.contains("emulator", ignoreCase = true) ||
    (brand.startsWith("generic") && device.startsWith("generic"))
}
