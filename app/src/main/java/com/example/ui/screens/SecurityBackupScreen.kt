package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.crypto.E2eeCryptoManager
import com.example.model.VaultConfigDto
import com.example.ui.viewmodel.CloudSyncState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun SecurityBackupScreen(
  userEmail: String,
  vaultConfig: VaultConfigDto,
  totpSecret: String?,
  cloudSyncState: CloudSyncState,
  onToggleTotp: (Boolean) -> Unit,
  onChangePin: (currentPin: String, newPin: String, onSuccess: () -> Unit) -> Unit,
  onVerifyCloudSync: () -> Unit,
  onBuildEncryptedBackupJson: () -> String,
  onLockVault: () -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  var showPinDialog by remember { mutableStateOf(false) }

  var currentTotpCode by remember(totpSecret) {
    mutableStateOf(totpSecret?.let { E2eeCryptoManager.generateTotpCode(it) }.orEmpty())
  }
  var secondsRemaining by remember { mutableIntStateOf(30) }

  LaunchedEffect(totpSecret) {
    while (totpSecret != null) {
      val now = System.currentTimeMillis()
      currentTotpCode = E2eeCryptoManager.generateTotpCode(totpSecret, now)
      secondsRemaining = (30 - ((now / 1000L) % 30L)).toInt()
      delay(1000L)
    }
  }

  val backupSaveLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
      if (uri != null) {
        try {
          val json = onBuildEncryptedBackupJson()
          context.contentResolver.openOutputStream(uri)?.use {
            it.write(json.toByteArray(Charsets.UTF_8))
          }
          Toast.makeText(
            context,
            "Sandaran tersulit (.json) berjaya dieksport.",
            Toast.LENGTH_LONG,
          )
            .show()
        } catch (e: Exception) {
          Toast.makeText(context, "Gagal mengeksport sandaran: ${e.message}", Toast.LENGTH_LONG)
            .show()
        }
      }
    }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Text(
        text = "Keselamatan 2FA & Sandaran Awan",
        style = MaterialTheme.typography.headlineMedium,
      )
      Text(
        text = "Akaun Aktif: $userEmail",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // E2EE Card
    item {
      Card(
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Filled.EnhancedEncryption,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(34.dp),
          )
          Column {
            Text(
              text = "Penyulitan Hujung ke Hujung (E2EE)",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
              text =
                "Cipher AES-256-GCM aktif. Semua nama klien, kelas, transkrip & rumusan AI disulitkan pada peranti sebelum disimpan ke awan.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
          }
        }
      }
    }

    // Cloud Backup Card
    item {
      Card(
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector =
                if (cloudSyncState is CloudSyncState.Synced) Icons.Filled.CloudDone
                else Icons.Filled.CloudSync,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Sandaran Data Automatik ke Awan",
                style = MaterialTheme.typography.titleMedium,
              )
              val syncSub =
                when (cloudSyncState) {
                  is CloudSyncState.Synced -> {
                    val timeStr =
                      SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.forLanguageTag("ms-MY"))
                        .format(Date(cloudSyncState.timestampMillis))
                    "${cloudSyncState.recordCount} rekod disandarkan • Kemas kini: $timeStr"
                  }
                  CloudSyncState.Syncing -> "Sedang menyegerakkan data dengan Cloud Firestore..."
                  is CloudSyncState.Error -> "Ralat segerakan: ${cloudSyncState.message}"
                }
              Text(
                text = syncSub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            FilledTonalButton(
              onClick = onVerifyCloudSync,
              modifier = Modifier.weight(1f).height(48.dp).testTag("sync_backup_button"),
            ) {
              Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Sahkan Awan")
            }
            OutlinedButton(
              onClick = {
                val tag = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                backupSaveLauncher.launch("DiariKaunselor_E2EE_Backup_$tag.json")
              },
              modifier =
                Modifier.weight(1f).height(48.dp).testTag("export_encrypted_backup_button"),
            ) {
              Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Eksport JSON")
            }
          }
        }
      }
    }

    // 2FA Controls Card
    item {
      Card(
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Security,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
            )
            Text(
              text = "Pengesahan Dua Faktor (2FA)",
              style = MaterialTheme.typography.titleMedium,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(
                text = "Kod TOTP Authenticator (RFC 6238)",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text =
                  if (vaultConfig.totpEnabled)
                    "Aktif: Memerlukan kod 6-digit selain PIN untuk membuka peti besi."
                  else "Tidak aktif: Hanya menggunakan PIN 6-digit selepas log masuk Google.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = vaultConfig.totpEnabled,
              onCheckedChange = onToggleTotp,
              modifier = Modifier.testTag("totp_toggle"),
            )
          }

          if (vaultConfig.totpEnabled && totpSecret != null) {
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column {
                    Text(
                      text = "Kod TOTP Semasa (${secondsRemaining}s)",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                      text = "${currentTotpCode.take(3)} ${currentTotpCode.drop(3)}",
                      style = MaterialTheme.typography.headlineMedium,
                      fontFamily = FontFamily.Monospace,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                  IconButton(
                    onClick = {
                      val uri = E2eeCryptoManager.buildTotpOtpAuthUri(userEmail, totpSecret)
                      clipboard.setText(AnnotatedString(uri))
                      Toast.makeText(
                        context,
                        "Pautan otpauth:// disalin untuk aplikasi Authenticator.",
                        Toast.LENGTH_SHORT,
                      )
                        .show()
                    }
                  ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Salin pautan Authenticator")
                  }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                  progress = { secondsRemaining / 30f },
                  modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "Kunci Rahsia Base32: $totpSecret",
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = FontFamily.Monospace,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          OutlinedButton(
            onClick = { showPinDialog = true },
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("change_pin_button"),
          ) {
            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Tukar PIN Keselamatan 6-Digit")
          }
        }
      }
    }

    // Session Actions
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
          onClick = onLockVault,
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("lock_vault_button"),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(Icons.Filled.Lock, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Kunci Peti Besi Sekarang")
        }

        OutlinedButton(
          onClick = onSignOut,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_button"),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Log Keluar Akaun Google")
        }
      }
    }
  }

  if (showPinDialog) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var localErr by remember { mutableStateOf<String?>(null) }

    AlertDialog(
      onDismissRequest = { showPinDialog = false },
      title = { Text("Tukar PIN Keselamatan") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = currentPin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) currentPin = it },
            label = { Text("PIN Semasa (6 Digit)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          )
          OutlinedTextField(
            value = newPin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPin = it },
            label = { Text("PIN Baharu (6 Digit)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          )
          if (localErr != null) {
            Text(localErr!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (currentPin.length != 6 || newPin.length != 6) {
              localErr = "Kedua-dua PIN mestilah 6 digit."
            } else {
              onChangePin(currentPin, newPin) {
                showPinDialog = false
                Toast.makeText(context, "PIN keselamatan berjaya dikemas kini.", Toast.LENGTH_SHORT)
                  .show()
              }
            }
          }
        ) {
          Text("Simpan PIN")
        }
      },
      dismissButton = {
        TextButton(onClick = { showPinDialog = false }) { Text("Batal") }
      },
    )
  }
}
