package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.configureAppCheck
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
