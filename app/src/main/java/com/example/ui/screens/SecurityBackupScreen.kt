package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentSky
import com.example.ui.theme.SagePrimaryBright
import com.example.ui.viewmodel.CloudSyncState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SecurityBackupScreen(
  userEmail: String,
  cloudSyncState: CloudSyncState,
  onVerifyCloudSync: () -> Unit,
  onBuildEncryptedBackupJson: () -> String,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

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
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Column {
        Text(
          text = "Keselamatan Data & Sandaran",
          style = MaterialTheme.typography.headlineMedium,
        )
        Text(
          text = "Akaun Aktif: $userEmail",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // E2EE Gradient Shield Hero Card
    item {
      val shieldGradient =
        Brush.linearGradient(
          listOf(MaterialTheme.colorScheme.primary, SagePrimaryBright, AccentEmerald)
        )
      Surface(
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .background(shieldGradient)
              .drawBehind {
                drawCircle(
                  color = Color.White.copy(alpha = 0.12f),
                  radius = 70.dp.toPx(),
                  center = Offset(size.width * 0.9f, size.height * 0.2f),
                )
              }
              .padding(18.dp)
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier.size(52.dp)
                  .clip(CircleShape)
                  .background(Color.White.copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.EnhancedEncryption,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
              )
            }
            Column {
              Text(
                text = "Penyulitan Hujung ke Hujung (E2EE)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text =
                  "AES-256-GCM berjalan secara automatik tanpa perlu PIN atau kod 2FA tambahan. Semua nama klien, kelas, transkrip & rumusan AI disulitkan pada peranti sebelum disimpan ke awan.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.92f),
              )
            }
          }
        }
      }
    }

    // Account Protection Info Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier =
              Modifier.size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.VerifiedUser,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
          }
          Column {
            Text(
              text = "Akses Pantas Akaun Google",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Akses aplikasi dilindungi oleh Akaun Google anda dengan pengasingan data peribadi pada awan.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    // Cloud Backup Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Box(
              modifier =
                Modifier.size(42.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(AccentSky.copy(alpha = 0.14f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector =
                  if (cloudSyncState is CloudSyncState.Synced) Icons.Filled.CloudDone
                  else Icons.Filled.CloudSync,
                contentDescription = null,
                tint = AccentSky,
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Sandaran Data Automatik ke Awan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              val syncSub =
                when (cloudSyncState) {
                  is CloudSyncState.Synced -> {
                    val timeStr =
                      SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.forLanguageTag("ms-MY"))
                        .format(Date(cloudSyncState.timestampMillis))
                    "${cloudSyncState.recordCount} rekod disandarkan • $timeStr"
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
              shape = RoundedCornerShape(14.dp),
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
              shape = RoundedCornerShape(14.dp),
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

    // Sign Out Action
    item {
      OutlinedButton(
        onClick = onSignOut,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("sign_out_button"),
        shape = RoundedCornerShape(16.dp),
      ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Log Keluar Akaun Google")
      }
    }
  }
}
