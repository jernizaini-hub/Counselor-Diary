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
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.model.VaultConfigDto
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
      // Hero Illustration with overlapping floating badge
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
          "Ruang kerja digital moden untuk mencatat sesi kaunseling, menjana analisis AI berstruktur, dan mencetak laporan PDF/Word dengan penyulitan hujung ke hujung.",
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
        FeatureHighlightPill(Icons.Filled.EnhancedEncryption, "E2EE AES-256", AccentEmerald)
        FeatureHighlightPill(Icons.Filled.Security, "2FA PIN & TOTP", AccentIndigo)
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
            text = "Mulakan Sesi Selamat Anda",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Pengesahan Faktor Pertama melalui Akaun Google",
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

@Composable
fun VaultSetupScreen(
  userEmail: String,
  isBusy: Boolean,
  errorMessage: String?,
  onSetupVault: (pin: String, enableTotp: Boolean) -> Unit,
  onSignOut: () -> Unit,
) {
  var pin by rememberSaveable { mutableStateOf("") }
  var confirmPin by rememberSaveable { mutableStateOf("") }
  var enableTotp by rememberSaveable { mutableStateOf(true) }
  var showPin by remember { mutableStateOf(false) }
  var localError by remember { mutableStateOf<String?>(null) }

  val glowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .drawBehind {
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(glowColor, Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.14f),
                radius = size.maxDimension * 0.45f,
              )
          )
        }
        .systemBarsPadding(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .widthIn(max = 520.dp)
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(contentAlignment = Alignment.Center) {
        Box(
          modifier =
            Modifier.size(88.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
        )
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = CircleShape,
          modifier = Modifier.size(66.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Filled.EnhancedEncryption,
              contentDescription = "Penyediaan Keselamatan 2FA",
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(32.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Sediakan Peti Besi 2FA & E2EE",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(6.dp))

      Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(50),
      ) {
        Text(
          text = userEmail,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text =
          "Tetapkan PIN Keselamatan 6-digit untuk melindungi kunci penyulitan AES-256-GCM bagi semua rekod sulit klien anda.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(18.dp))

      PinDotsIndicator(enteredCount = pin.length, accentColor = MaterialTheme.colorScheme.primary)

      Spacer(modifier = Modifier.height(16.dp))

      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
            label = { Text("PIN Keselamatan (6 Digit)") },
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
            modifier = Modifier.fillMaxWidth().testTag("setup_pin_input"),
          )

          Spacer(modifier = Modifier.height(12.dp))

          OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it },
            label = { Text("Sahkan PIN Keselamatan") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation =
              if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("setup_confirm_pin_input"),
          )

          Spacer(modifier = Modifier.height(16.dp))

          Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                  text = "Aktifkan Pengesahan TOTP 2FA",
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text = "Lapisan kod 6-digit berasaskan masa (RFC 6238 Authenticator).",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = enableTotp,
                onCheckedChange = { enableTotp = it },
                modifier = Modifier.testTag("setup_totp_switch"),
              )
            }
          }
        }
      }

      val shownError = localError ?: errorMessage
      if (shownError != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = shownError,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Button(
        onClick = {
          localError = null
          when {
            pin.length != 6 -> localError = "PIN mestilah tepat 6 digit."
            pin != confirmPin -> localError = "PIN pengesahan tidak sepadan."
            else -> onSetupVault(pin, enableTotp)
          }
        },
        enabled = !isBusy,
        modifier =
          Modifier.fillMaxWidth().height(54.dp).testTag("complete_vault_setup_button"),
        shape = RoundedCornerShape(16.dp),
      ) {
        if (isBusy) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
          )
        } else {
          Text("Aktifkan Peti Besi & Masuk", style = MaterialTheme.typography.labelLarge)
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      TextButton(onClick = onSignOut) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Log Keluar Akaun")
      }
    }
  }
}

@Composable
fun VaultUnlockScreen(
  config: VaultConfigDto,
  userEmail: String,
  isBusy: Boolean,
  errorMessage: String?,
  onUnlock: (pin: String, totpCode: String) -> Unit,
  onSignOut: () -> Unit,
) {
  var pin by rememberSaveable { mutableStateOf("") }
  var totpCode by rememberSaveable { mutableStateOf("") }
  var showPin by remember { mutableStateOf(false) }

  val glowColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .drawBehind {
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(glowColor, Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.15f),
                radius = size.maxDimension * 0.45f,
              )
          )
        }
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
      Box(contentAlignment = Alignment.Center) {
        Box(
          modifier =
            Modifier.size(90.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
        )
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = CircleShape,
          modifier = Modifier.size(68.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = "Peti besi dikunci",
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(32.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Pengesahan Dua Faktor (2FA)",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(6.dp))

      Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(50),
      ) {
        Text(
          text = userEmail,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text =
          if (config.totpEnabled) {
            "Masukkan PIN keselamatan 6-digit dan kod TOTP Authenticator anda untuk menyahsulit rekod diari."
          } else {
            "Masukkan PIN keselamatan 6-digit anda untuk membuka peti besi penyulitan E2EE."
          },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(18.dp))

      PinDotsIndicator(enteredCount = pin.length, accentColor = MaterialTheme.colorScheme.primary)

      Spacer(modifier = Modifier.height(16.dp))

      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
            label = { Text("PIN Keselamatan (6 Digit)") },
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
            modifier = Modifier.fillMaxWidth().testTag("vault_pin_input"),
          )

          if (config.totpEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
              value = totpCode,
              onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) totpCode = it },
              label = { Text("Kod Pengesahan TOTP (6 Digit)") },
              placeholder = { Text("cth. 482910") },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth().testTag("vault_totp_input"),
            )
          }
        }
      }

      if (errorMessage != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(12.dp),
            textAlign = TextAlign.Center,
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Button(
        onClick = { onUnlock(pin, totpCode) },
        enabled = !isBusy && pin.length == 6 && (!config.totpEnabled || totpCode.length == 6),
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("unlock_vault_button"),
        shape = RoundedCornerShape(16.dp),
        colors =
          ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
      ) {
        if (isBusy) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
          )
        } else {
          Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Buka Peti Besi & Nyahsulit", style = MaterialTheme.typography.labelLarge)
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

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

@Composable
private fun PinDotsIndicator(
  enteredCount: Int,
  accentColor: Color,
  totalDots: Int = 6,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
    repeat(totalDots) { index ->
      val filled = index < enteredCount
      val dotColor by
        animateColorAsState(
          targetValue = if (filled) accentColor else MaterialTheme.colorScheme.outlineVariant,
          label = "pinDot",
        )
      Box(
        modifier =
          Modifier.size(if (filled) 14.dp else 12.dp)
            .clip(CircleShape)
            .background(dotColor)
      )
    }
  }
}
