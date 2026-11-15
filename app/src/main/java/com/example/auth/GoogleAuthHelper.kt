package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

object GoogleAuthHelper {
  private const val TAG = "Auth"
  private const val AUTO_SIGN_IN_TIMEOUT_MS = 3_500L
  private const val FIREBASE_SIGN_IN_TIMEOUT_MS = 15_000L

  private tailrec fun Context.findActivity(): Activity? =
    when (this) {
      is Activity -> this
      is ContextWrapper -> baseContext.findActivity()
      else -> null
    }

  private fun resolveWebClientId(context: Context): String? {
    val resId =
      context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (resId == 0) return null
    return try {
      context.getString(resId).takeIf { it.isNotBlank() }
    } catch (_: Exception) {
      null
    }
  }

  @Suppress("DEPRECATION")
  fun getAppSigningSha1(context: Context): String? =
    try {
      val pm = context.packageManager
      val pkgName = context.packageName
      val sigBytes =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
          info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
          val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES)
          info.signatures?.firstOrNull()?.toByteArray()
        }
      sigBytes?.let { bytes ->
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        digest.joinToString(":") { b -> "%02X".format(b) }
      }
    } catch (_: Exception) {
      null
    }

  private fun buildAuthDiagnosticSuffix(context: Context, detail: String?): String {
    val sha1 = getAppSigningSha1(context) ?: "tidak dapat dibaca"
    val cleanDetail = detail?.takeIf { it.isNotBlank() } ?: "Tiada mesej tambahan"
    return "Butiran: $cleanDetail\nSHA-1 APK semasa: $sha1"
  }

  fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope,
  ): Job? {
    if (Firebase.auth.currentUser != null) {
      onAuthSuccess()
      return null
    }
    val clientId = resolveWebClientId(context)
    if (clientId == null) {
      onUnauthenticated()
      return null
    }

    val googleIdOption =
      GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    return scope.launch {
      try {
        val activityContext = context.findActivity()?.let { MutableContextWrapper(it) } ?: context
        val result =
          withTimeoutOrNull(AUTO_SIGN_IN_TIMEOUT_MS) {
            credentialManager.getCredential(activityContext, request)
          }
        val credential = result?.credential
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
          val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
          withTimeout(FIREBASE_SIGN_IN_TIMEOUT_MS) {
            Firebase.auth.signInWithCredential(authCredential).await()
          }
          onAuthSuccess()
        } else {
          onUnauthenticated()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        onUnauthenticated()
      }
    }
  }

  fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: (diagnosticHint: String) -> Unit = {},
  ) {
    val clientId = resolveWebClientId(context)
    if (clientId == null) {
      onAuthError(
        "Konfigurasi Google Sign-In tiada: default_web_client_id (google-services.json) tidak ditemui dalam binaan ini."
      )
      return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
      try {
        val activityContext = context.findActivity()?.let { MutableContextWrapper(it) } ?: context
        val result = credentialManager.getCredential(activityContext, request)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
          val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
          withTimeout(FIREBASE_SIGN_IN_TIMEOUT_MS) {
            Firebase.auth.signInWithCredential(authCredential).await()
          }
          onAuthSuccess()
        } else {
          onAuthError("Jenis kelayakan log masuk tidak dijangka.")
        }
      } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Firebase credential sign-in timed out", e)
        onAuthError(
          "Pengesahan Google mengambil masa terlalu lama. Sila semak sambungan internet dan cuba semula."
        )
      } catch (e: GetCredentialCancellationException) {
        Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
        val hint =
          "Log masuk Google tidak diselesaikan atau ditolak selepas memilih akaun.\n" +
            "Jika anda telah memilih akaun Gmail tetapi kembali ke skrin ini, punca paling kerap ialah sijil SHA-1 APK binaan GitHub tidak sepadan dengan Firebase (debug.keystore berbeza) atau Google Play Services membatalkan sesi.\n" +
            buildAuthDiagnosticSuffix(context, e.errorMessage?.toString() ?: e.message)
        onAuthCancelled(hint)
      } catch (e: GetCredentialException) {
        Log.e(TAG, "CredentialManager error (${e.type}): ${e.errorMessage}", e)
        val detail = "${e.type}: ${e.errorMessage ?: e.localizedMessage.orEmpty()}"
        onAuthError(
          "Log masuk Google gagal.\n" + buildAuthDiagnosticSuffix(context, detail)
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Google Sign-In failed", e)
        val msg = e.localizedMessage ?: "Log masuk Google gagal. Sila cuba lagi."
        onAuthError("$msg\n" + buildAuthDiagnosticSuffix(context, e.javaClass.simpleName))
      }
    }
  }

  fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {},
  ) {
    scope.launch {
      withContext(NonCancellable) {
        try {
          credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
          Log.w(TAG, "Failed to clear credential state: ${e.message}")
        } finally {
          Firebase.auth.signOut()
          onSignOutComplete()
        }
      }
    }
  }
}
