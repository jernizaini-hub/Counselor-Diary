package com.example.data.firebase

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
private const val TOKEN_FETCH_TIMEOUT_MS = 12_000L

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
 * If the cached token or cold-start RecaptchaTasksClient failed (e.g. after closing & reopening),
 * clears stale token storage, reinstalls the provider factory, and fetches a fresh token cleanly.
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
      if (!forceRefreshAndReset) {
        try {
          val token = withTimeout(TOKEN_FETCH_TIMEOUT_MS) { appCheck.getAppCheckToken(false).await() }
          if (token.token.isNotBlank()) {
            return@withContext
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.w(TAG, "Cached/initial App Check token check failed; resetting & refreshing", e)
        }
      }

      var lastError: Throwable? = null
      repeat(2) { attempt ->
        try {
          reinstallActiveProvider(appContext, appCheck)
          val refreshed =
            withTimeout(TOKEN_FETCH_TIMEOUT_MS) { appCheck.getAppCheckToken(true).await() }
          if (refreshed.token.isNotBlank()) {
            Log.d(TAG, "Fresh App Check token obtained (attempt ${attempt + 1}).")
            return@withContext
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: TimeoutCancellationException) {
          lastError = e
          Log.w(TAG, "App Check token refresh timed out on attempt ${attempt + 1}", e)
        } catch (e: Exception) {
          lastError = e
          Log.w(TAG, "App Check token refresh failed on attempt ${attempt + 1}", e)
        }
        if (attempt == 0) {
          delay(650L)
        }
      }

      val detail =
        lastError?.localizedMessage?.takeIf { it.isNotBlank() }
          ?: lastError?.javaClass?.simpleName
          ?: "Token pengesahan kosong"
      throw IllegalStateException(
        "Pengesahan keselamatan aplikasi (Firebase App Check) gagal diperbaharui: $detail. " +
          "Sila pastikan internet aktif tanpa sekatan VPN/Private DNS, tarikh & masa telefon adalah automatik, " +
          "dan Google Play Services dikemas kini.",
        lastError,
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
      RecaptchaAppCheckProviderFactory.getInstance(siteKey)
    )
    installedModeInProcess = MODE_RECAPTCHA
    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
  } else {
    appCheck.installAppCheckProviderFactory(
      DebugAppCheckProviderFactory.getInstance()
    )
    installedModeInProcess = MODE_DEBUG
    Log.d(TAG, "DebugAppCheckProviderFactory installed.")
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
