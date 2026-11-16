package com.example

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.ResilientAppCheckToken
import com.example.data.firebase.chooseMostInformativeError
import com.example.data.firebase.configureAppCheck
import com.example.data.firebase.extractAppCheckErrorMessage
import com.example.data.firebase.isDummyOrInvalidAppCheckToken
import com.example.data.firebase.isUsableAppCheckToken
import com.example.data.firebase.parseAppCheckTtlMillis
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppCheckHelperTest {

  @Test
  fun `configureAppCheck persists intent debug secret synchronously`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
    }
    if (FirebaseApp.getApps(context).isEmpty()) return

    val persistenceKey = FirebaseApp.getInstance().persistenceKey
    val debugPrefs =
      context.getSharedPreferences(
        "com.google.firebase.appcheck.debug.store.$persistenceKey",
        Context.MODE_PRIVATE,
      )
    debugPrefs.edit().clear().commit()

    val intent =
      Intent().putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", "test-secret-1234-5678")
    configureAppCheck(context, intent)

    assertEquals(
      "test-secret-1234-5678",
      debugPrefs.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null),
    )
    assertTrue(debugPrefs.getBoolean("dk_has_intent_debug_token", false))
  }

  @Test
  fun `configureAppCheck clears stale cached token on first setup`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
    }
    if (FirebaseApp.getApps(context).isEmpty()) return

    val persistenceKey = FirebaseApp.getInstance().persistenceKey
    val debugPrefs =
      context.getSharedPreferences(
        "com.google.firebase.appcheck.debug.store.$persistenceKey",
        Context.MODE_PRIVATE,
      )
    val tokenPrefs =
      context.getSharedPreferences(
        "com.google.firebase.appcheck.store.$persistenceKey",
        Context.MODE_PRIVATE,
      )
    debugPrefs.edit().clear().commit()
    tokenPrefs
      .edit()
      .putString("com.google.firebase.appcheck.APP_CHECK_TOKEN", "stale-invalid-token")
      .putString("com.google.firebase.appcheck.TOKEN_TYPE", "DEFAULT_APP_CHECK_TOKEN")
      .commit()

    configureAppCheck(context, Intent())

    assertNull(tokenPrefs.getString("com.google.firebase.appcheck.APP_CHECK_TOKEN", null))
    assertNull(tokenPrefs.getString("com.google.firebase.appcheck.TOKEN_TYPE", null))
  }

  @Test
  fun `parseAppCheckTtlMillis parses response ttl string and jwt claims`() {
    assertEquals(3_600_000L, parseAppCheckTtlMillis("3600s"))
    assertEquals(1_800_500L, parseAppCheckTtlMillis("1800.5s"))

    val header = Base64.encodeToString("{\"alg\":\"RS256\"}".toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    val payload =
      Base64.encodeToString(
        "{\"iat\":1700000000,\"exp\":1700007200}".toByteArray(StandardCharsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
      )
    val jwt = "$header.$payload.sig"
    assertEquals(7_200_000L, parseAppCheckTtlMillis("", jwt))
    assertEquals(3_600_000L, parseAppCheckTtlMillis(null, "invalid"))
  }

  @Test
  fun `extractAppCheckErrorMessage extracts Google JSON error details`() {
    val json =
      """{"error":{"code":403,"message":"App attestation failed.","status":"PERMISSION_DENIED"}}"""
    assertEquals(
      "HTTP 403 (PERMISSION_DENIED): App attestation failed.",
      extractAppCheckErrorMessage(403, json),
    )
    assertEquals(
      "HTTP 400: Bad Request body",
      extractAppCheckErrorMessage(400, "Bad Request body"),
    )
  }

  @Test
  fun `chooseMostInformativeError preserves real HTTP or reCAPTCHA cause over Too many attempts`() {
    val realError = FirebaseException("HTTP 403 (PERMISSION_DENIED): App attestation failed.")
    val throttleError = FirebaseException("Too many attempts.")
    assertSame(realError, chooseMostInformativeError(realError, throttleError))

    val freshSpecificError = FirebaseException("reCAPTCHA Enterprise gagal: Network unavailable")
    assertSame(freshSpecificError, chooseMostInformativeError(realError, freshSpecificError))
  }

  @Test
  fun `isUsableAppCheckToken rejects dummy error token and nearly expired tokens`() {
    assertTrue(isDummyOrInvalidAppCheckToken("eyJlcnJvciI6IlVOS05PV05fRVJST1IifQ=="))
    assertTrue(isDummyOrInvalidAppCheckToken("  "))
    assertFalse(isDummyOrInvalidAppCheckToken("valid.jwt.token"))

    val dummy =
      ResilientAppCheckToken(
        "eyJlcnJvciI6IlVOS05PV05fRVJST1IifQ==",
        System.currentTimeMillis() + 3_600_000L,
      )
    assertFalse(isUsableAppCheckToken(dummy))

    val expiredSoon =
      ResilientAppCheckToken("valid.jwt.token", System.currentTimeMillis() + 5_000L)
    assertFalse(isUsableAppCheckToken(expiredSoon))

    val valid =
      ResilientAppCheckToken("valid.jwt.token", System.currentTimeMillis() + 3_600_000L)
    assertTrue(isUsableAppCheckToken(valid))
  }
}
