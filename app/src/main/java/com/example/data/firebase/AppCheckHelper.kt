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

private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val HAS_INTENT_DEBUG_TOKEN_KEY = "dk_has_intent_debug_token"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
  try {
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
    }
    if (FirebaseApp.getApps(context).isEmpty()) {
      Log.w(TAG, "FirebaseApp is not initialized (missing google-services.json); skipping App Check.")
      return
    }

    val persistenceKey = FirebaseApp.getInstance().persistenceKey
    val prefs =
      context.getSharedPreferences(
        "$DEBUG_STORE_PREFIX$persistenceKey",
        Context.MODE_PRIVATE,
      )

    if (BuildConfig.DEBUG) {
      val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

      if (!debugToken.isNullOrEmpty()) {
        // CRITICAL: MUST use commit() instead of apply() so the secret is written
        // synchronously before DebugAppCheckProviderFactory reads it.
        prefs.edit()
          .putString(DEBUG_SECRET_KEY, debugToken)
          .putBoolean(HAS_INTENT_DEBUG_TOKEN_KEY, true)
          .commit()
        Log.d(TAG, "App Check debug secret configured successfully.")
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
          DebugAppCheckProviderFactory.getInstance()
        )
        Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        return
      }

      val hasStoredIntentDebugToken = prefs.getBoolean(HAS_INTENT_DEBUG_TOKEN_KEY, false)
      if (hasStoredIntentDebugToken || isEmulatorOrTestRuntime()) {
        Log.w(TAG, "No debug token found in Intent extra; using DebugAppCheckProviderFactory for emulator/test.")
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
          DebugAppCheckProviderFactory.getInstance()
        )
        return
      }

      // On sideloaded physical devices (e.g. GitHub Actions debug APK) where no launch-intent
      // debug token exists, use the provisioned reCAPTCHA Enterprise App Check provider so
      // Firebase AI Logic receives valid attestation tokens instead of an unregistered random UUID.
      if (installRecaptchaProviderIfAvailable(context)) {
        return
      }

      FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        DebugAppCheckProviderFactory.getInstance()
      )
      Log.d(TAG, "DebugAppCheckProviderFactory installed (fallback).")
    } else {
      installRecaptchaProviderIfAvailable(context)
    }
  } catch (e: Exception) {
    Log.e(TAG, "Failed to initialize Firebase App Check", e)
  }
}

private fun installRecaptchaProviderIfAvailable(context: Context): Boolean {
  val resId =
    context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
  if (resId == 0) {
    Log.e(TAG, "recaptcha_site_key string resource not found in package.")
    return false
  }
  val siteKey = context.getString(resId)
  if (siteKey.isBlank()) {
    Log.e(TAG, "recaptcha_site_key resource string is blank.")
    return false
  }
  FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
    RecaptchaAppCheckProviderFactory.getInstance(siteKey)
  )
  Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
  return true
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
