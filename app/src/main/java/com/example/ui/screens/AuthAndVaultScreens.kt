package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentSky

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val primaryGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
  val secondaryGlow = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)

  LaunchedEffect(Unit) {
    GoogleAuthHelper.attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = scope,
    )
  }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .drawBehind {
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(primaryGlow, Color.Transparent),
                center = Offset(size.width * 0.15f, size.height * 0.12f),
                radius = size.maxDimension * 0.45f,
              )
          )
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(secondaryGlow, Color.Transparent),
                center = Offset(size.width * 0.9f, size.height * 0.85f),
                radius = size.maxDimension * 0.4f,
              )
          )
        }
        .systemBarsPadding(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .widthIn(max = 560.dp)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 22.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Surface(
          shape = RoundedCornerShape(28.dp),
          shadowElevation = 6.dp,
          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
          Box {
            Image(
              painter = painterResource(id = R.drawable.img_counselor_hero),
              contentDescription = "Ilustrasi ruang kerja kaunselor",
              modifier = Modifier.fillMaxWidth().height(216.dp),
              contentScale = ContentScale.Crop,
            )
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(72.dp)
                  .align(Alignment.BottomCenter)
                  .background(
                    Brush.verticalGradient(
                      listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                    )
                  )
            )
          }
        }

        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(50),
          shadowElevation = 4.dp,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
          modifier = Modifier.offset(y = 0.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.VerifiedUser,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Unit Bimbingan & Kaunseling",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Diari Kaunselor",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text =
          "Ruang kerja digital moden untuk mencatat sesi kaunseling, menjana analisis AI berstruktur, dan mencetak laporan PDF/Word dengan penyulitan hujung ke hujung automatik.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(20.dp))

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FeatureHighlightPill(Icons.Filled.AutoAwesome, "Rumusan AI", AccentCoral)
        FeatureHighlightPill(Icons.Filled.EnhancedEncryption, "E2EE Automatik", AccentEmerald)
        FeatureHighlightPill(Icons.Filled.Description, "PDF & Word", AccentIndigo)
        FeatureHighlightPill(Icons.Filled.CloudDone, "Sandaran Awan", AccentSky)
      }

      Spacer(modifier = Modifier.height(24.dp))

      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = "Mulakan Sesi Kerja Anda",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Log masuk pantas & selamat terus menggunakan Akaun Google",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(16.dp))

          if (errorMessage != null) {
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            ) {
              Text(
                text = errorMessage!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(12.dp),
              )
            }
          }

          Button(
            onClick = {
              isLoading = true
              errorMessage = null
              GoogleAuthHelper.onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                  isLoading = false
                  onAuthSuccess()
                },
                onAuthError = { msg ->
                  isLoading = false
                  errorMessage = msg
                },
                scope = scope,
                onAuthCancelled = { isLoading = false },
              )
            },
            enabled = !isLoading,
            modifier =
              Modifier.fillMaxWidth()
                .height(54.dp)
                .testTag("google_sign_in_button"),
            shape = RoundedCornerShape(16.dp),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.5.dp,
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text("Mengesahkan akaun...")
            } else {
              Icon(
                imageVector = Icons.Filled.Key,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "Log Masuk dengan Google",
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FeatureHighlightPill(icon: ImageVector, label: String, accentColor: Color) {
  Surface(
    color = accentColor.copy(alpha = 0.11f),
    shape = RoundedCornerShape(50),
    border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.28f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(15.dp),
        tint = accentColor,
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = accentColor,
      )
    }
  }
}

/**
 * Shown only once if an existing account already had encrypted records protected by an older manual
 * PIN before 2FA removal, so data can be unlocked and migrated to seamless automatic E2EE.
 */
@Composable
fun LegacyPinMigrationScreen(
  userEmail: String,
  isBusy: Boolean,
  errorMessage: String?,
  onMigrate: (oldPin: String) -> Unit,
  onSignOut: () -> Unit,
) {
  var oldPin by rememberSaveable { mutableStateOf("") }
  var showPin by remember { mutableStateOf(false) }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .systemBarsPadding(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .widthIn(max = 500.dp)
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
        modifier = Modifier.size(68.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Filled.LockOpen,
            contentDescription = "Nyahsulit rekod terdahulu",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(32.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Nyahsulit Rekod Lama (Sekali Sahaja)",
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = userEmail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text =
          "Pengesahan 2FA telah dibuang. Masukkan PIN 6-digit terdahulu anda sekali sahaja untuk memindahkan rekod lama ke mod E2EE automatik.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(16.dp))

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(6) { idx ->
          val filled = idx < oldPin.length
          val color by
            animateColorAsState(
              targetValue =
                if (filled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
              label = "legacyPinDot",
            )
          Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      OutlinedTextField(
        value = oldPin,
        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) oldPin = it },
        label = { Text("PIN Lama (6 Digit)") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation =
          if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
          IconButton(onClick = { showPin = !showPin }) {
            Icon(
              imageVector = if (showPin) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
              contentDescription = "Tunjuk atau sembunyi PIN",
            )
          }
        },
        modifier = Modifier.fillMaxWidth().testTag("legacy_pin_input"),
      )

      if (errorMessage != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = errorMessage,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
          textAlign = TextAlign.Center,
        )
      }

      Spacer(modifier = Modifier.height(18.dp))

      Button(
        onClick = { onMigrate(oldPin) },
        enabled = !isBusy && oldPin.length == 6,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("migrate_legacy_pin_button"),
        shape = RoundedCornerShape(16.dp),
      ) {
        if (isBusy) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
          )
        } else {
          Text("Nyahsulit & Masuk Terus")
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      OutlinedButton(
        onClick = onSignOut,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(16.dp),
      ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Tukar Akaun Google")
      }
    }
  }
}
