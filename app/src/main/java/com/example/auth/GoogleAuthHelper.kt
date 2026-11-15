package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
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
    val clientId =
      try {
        context.getString(R.string.default_web_client_id)
      } catch (_: Exception) {
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
        val activityContext = context.findActivity() ?: context
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
    onAuthCancelled: () -> Unit = {},
  ) {
    val clientId =
      try {
        context.getString(R.string.default_web_client_id)
      } catch (_: Exception) {
        onAuthError("Konfigurasi Google Sign-In tiada: default_web_client_id tidak ditemui.")
        return
      }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
      try {
        val activityContext = context.findActivity() ?: context
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
        onAuthError("Pengesahan Google mengambil masa terlalu lama. Sila semak internet dan cuba semula.")
      } catch (e: GetCredentialCancellationException) {
        Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
        onAuthCancelled()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Google Sign-In failed", e)
        onAuthError(e.localizedMessage ?: "Log masuk Google gagal. Sila cuba lagi.")
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
