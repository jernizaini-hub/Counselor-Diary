package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.export.ReportExporter
import com.example.model.ActivityCategory
import com.example.model.CounselingRecord
import com.example.ui.viewmodel.AiSummaryState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordEditorScreen(
  existingRecord: CounselingRecord?,
  initialCategory: ActivityCategory,
  counselorName: String,
  counselorEmail: String,
  aiSummaryState: AiSummaryState,
  onGenerateAiSummary: (
    category: ActivityCategory,
    clientName: String,
    className: String,
    dayName: String,
    timeRange: String,
    rawNotes: String,
  ) -> Unit,
  onResetAiState: () -> Unit,
  onSaveRecord: (CounselingRecord, onResult: (Boolean) -> Unit) -> Unit,
  onDeleteRecord: (String, onDone: () -> Unit) -> Unit,
  onBack: () -> Unit,
) {
  val context = LocalContext.current

  var category by rememberSaveable {
    mutableStateOf(existingRecord?.category ?: initialCategory)
  }
  var clientName by rememberSaveable { mutableStateOf(existingRecord?.clientName.orEmpty()) }
  var className by rememberSaveable { mutableStateOf(existingRecord?.className.orEmpty()) }
  var sessionDateMillis by rememberSaveable {
    mutableLongStateOf(existingRecord?.sessionDateMillis ?: System.currentTimeMillis())
  }
  var dayName by rememberSaveable {
    mutableStateOf(existingRecord?.dayName?.ifBlank { null } ?: malayDayName(sessionDateMillis))
  }
  var timeRange by rememberSaveable {
    mutableStateOf(existingRecord?.timeRange?.ifBlank { null } ?: "09:00 - 09:45")
  }
  var durationText by rememberSaveable {
    mutableStateOf((existingRecord?.durationMinutes ?: 45).toString())
  }
  var notes by rememberSaveable { mutableStateOf(existingRecord?.notes.orEmpty()) }
  var summaryMatter by rememberSaveable { mutableStateOf(existingRecord?.summaryMatter.orEmpty()) }
  var summaryQuestions by rememberSaveable {
    mutableStateOf(existingRecord?.summaryQuestions.orEmpty())
  }
  var summaryIntervention by rememberSaveable {
    mutableStateOf(existingRecord?.summaryIntervention.orEmpty())
  }

  var isSaving by remember { mutableStateOf(false) }
  var showDatePicker by remember { mutableStateOf(false) }
  var showDeleteDialog by remember { mutableStateOf(false) }
  var showExportMenuDialog by remember { mutableStateOf<ExportTarget?>(null) }
  var validationError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(aiSummaryState) {
    if (aiSummaryState is AiSummaryState.Success) {
      summaryMatter = aiSummaryState.result.matter
      summaryQuestions = aiSummaryState.result.questions
      summaryIntervention = aiSummaryState.result.intervention
      onResetAiState()
      Toast.makeText(context, "Rumusan AI berjaya dijana.", Toast.LENGTH_SHORT).show()
    }
  }

  fun buildDraftRecord(): CounselingRecord {
    val dur = durationText.toIntOrNull()?.coerceIn(0, 1440) ?: 45
    return CounselingRecord(
      id = existingRecord?.id.orEmpty(),
      userId = existingRecord?.userId.orEmpty(),
      category = category,
      clientName = clientName.trim(),
      className = className.trim(),
      sessionDateMillis = sessionDateMillis,
      dayName = dayName.trim().ifBlank { malayDayName(sessionDateMillis) },
      timeRange = timeRange.trim().ifBlank { "09:00 - 09:45" },
      durationMinutes = dur,
      notes = notes.trim(),
      summaryMatter = summaryMatter.trim(),
      summaryQuestions = summaryQuestions.trim(),
      summaryIntervention = summaryIntervention.trim(),
      hasAiSummary =
        summaryMatter.isNotBlank() || summaryQuestions.isNotBlank() || summaryIntervention.isNotBlank(),
      createdAt = existingRecord?.createdAt,
      updatedAt = existingRecord?.updatedAt,
    )
  }

  val savePdfLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
      if (uri != null) {
        try {
          val bytes =
            ReportExporter.generatePdfBytes(buildDraftRecord(), counselorName, counselorEmail)
          context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
          Toast.makeText(context, "Laporan PDF berjaya disimpan.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
          Toast.makeText(context, "Gagal menyimpan PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
      }
    }

  val saveWordLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      )
    ) { uri ->
      if (uri != null) {
        try {
          val bytes =
            ReportExporter.generateDocxBytes(buildDraftRecord(), counselorName, counselorEmail)
          context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
          Toast.makeText(context, "Laporan Word (.docx) berjaya disimpan.", Toast.LENGTH_LONG)
            .show()
        } catch (e: Exception) {
          Toast.makeText(context, "Gagal menyimpan Word: ${e.message}", Toast.LENGTH_LONG).show()
        }
      }
    }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = if (existingRecord == null) "Catatan Baharu" else "Butiran & Rumusan Rekod",
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
          }
        },
        actions = {
          if (existingRecord != null) {
            IconButton(
              onClick = { showDeleteDialog = true },
              modifier = Modifier.testTag("delete_record_button"),
            ) {
              Icon(
                Icons.Filled.Delete,
                contentDescription = "Padam rekod",
                tint = MaterialTheme.colorScheme.error,
              )
            }
          }
        },
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .widthIn(max = 720.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // 1. Kategori Sesi & Aktiviti
        Text(
          text = "Kategori Sesi / Aktiviti",
          style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          ActivityCategory.entries.forEach { cat ->
            FilterChip(
              selected = category == cat,
              onClick = { category = cat },
              label = { Text(cat.displayName) },
              modifier = Modifier.testTag("editor_cat_${cat.name.lowercase()}"),
            )
          }
        }

        // 2. Maklumat Klien, Kelas, Hari & Masa
        Card(
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = "Maklumat Sesi & Klien",
              style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
              value = clientName,
              onValueChange = { clientName = it },
              label = {
                Text(
                  if (category.isCounselingSession) "Nama Klien / Ahli Kelompok"
                  else "Nama Klien / Kumpulan Sasaran / Tajuk"
                )
              },
              placeholder = { Text("cth. Nurul Aina binti Hassan") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("client_name_input"),
            )

            OutlinedTextField(
              value = className,
              onValueChange = { className = it },
              label = { Text("Kelas / Tingkatan / Lokasi") },
              placeholder = { Text("cth. 5 Sains 1 / Bilik Kaunseling") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("class_name_input"),
            )

            val dateDisplay =
              SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("ms-MY"))
                .format(Date(sessionDateMillis))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedButton(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(54.dp).testTag("date_picker_button"),
              ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(dateDisplay, maxLines = 1)
              }

              OutlinedTextField(
                value = dayName,
                onValueChange = { dayName = it },
                label = { Text("Hari") },
                singleLine = true,
                modifier = Modifier.weight(0.75f).testTag("day_input"),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedTextField(
                value = timeRange,
                onValueChange = { timeRange = it },
                label = { Text("Masa") },
                placeholder = { Text("09:00 - 09:45") },
                singleLine = true,
                modifier = Modifier.weight(1.2f).testTag("time_input"),
              )
              OutlinedTextField(
                value = durationText,
                onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) durationText = it },
                label = { Text("Tempoh (min)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(0.8f).testTag("duration_input"),
              )
            }
          }
        }

        // 3. Transkrip Perbualan / Catatan Ringkas
        Card(
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Transkrip Perbualan / Catatan Ringkas Kaunselor",
              style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
              value = notes,
              onValueChange = { notes = it },
              placeholder = {
                Text(
                  "Catatkan transkrip perbualan, pemerhatian tingkah laku, isu yang dikongsi klien, atau butiran aktiviti..."
                )
              },
              minLines = 5,
              maxLines = 12,
              modifier = Modifier.fillMaxWidth().testTag("notes_input"),
            )
          }
        }

        // 4. Kotak Rumusan & Janaan AI (Perkara, Persoalan, Huraian Intervensi)
        Card(
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
          border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.fillMaxWidth().testTag("ai_summary_box"),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Kotak Rumusan & Analisis AI",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = "Analisis automatik Perkara, Persoalan & Huraian Intervensi",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            FilledTonalButton(
              onClick = {
                onGenerateAiSummary(
                  category,
                  clientName,
                  className,
                  dayName,
                  timeRange,
                  notes,
                )
              },
              enabled = aiSummaryState !is AiSummaryState.Loading,
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_ai_button"),
              shape = RoundedCornerShape(12.dp),
            ) {
              if (aiSummaryState is AiSummaryState.Loading) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("AI sedang menganalisis catatan...")
              } else {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Jana Rumusan AI daripada Catatan")
              }
            }

            if (aiSummaryState is AiSummaryState.Error) {
              Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = aiSummaryState.message,
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.padding(10.dp),
                )
              }
            }

            OutlinedTextField(
              value = summaryMatter,
              onValueChange = { summaryMatter = it },
              label = { Text("1. Perkara (Isu / Tema Utama)") },
              minLines = 2,
              maxLines = 6,
              modifier = Modifier.fillMaxWidth().testTag("summary_matter_input"),
            )

            OutlinedTextField(
              value = summaryQuestions,
              onValueChange = { summaryQuestions = it },
              label = { Text("2. Persoalan") },
              minLines = 2,
              maxLines = 6,
              modifier = Modifier.fillMaxWidth().testTag("summary_questions_input"),
            )

            OutlinedTextField(
              value = summaryIntervention,
              onValueChange = { summaryIntervention = it },
              label = { Text("3. Huraian Intervensi") },
              minLines = 3,
              maxLines = 8,
              modifier = Modifier.fillMaxWidth().testTag("summary_intervention_input"),
            )
          }
        }

        if (validationError != null) {
          Text(
            text = validationError!!,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }

        // 5. Butang Simpan Rekod (E2EE + Sandaran Awan)
        Button(
          onClick = {
            validationError = null
            if (clientName.isBlank() && notes.isBlank()) {
              validationError = "Sila isi sekurang-kurangnya Nama Klien/Sasaran atau Catatan."
              return@Button
            }
            isSaving = true
            onSaveRecord(buildDraftRecord()) { success ->
              isSaving = false
              if (success) {
                Toast.makeText(
                  context,
                  "Rekod disulitkan (E2EE) & disandarkan ke awan.",
                  Toast.LENGTH_SHORT,
                ).show()
                onBack()
              }
            }
          },
          enabled = !isSaving,
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_record_button"),
          shape = RoundedCornerShape(14.dp),
        ) {
          if (isSaving) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Menyulit & Menyandar ke Awan...")
          } else {
            Icon(Icons.Filled.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Simpan Rekod (E2EE & Awan)", style = MaterialTheme.typography.labelLarge)
          }
        }

        // 6. Pilihan Cetak / Eksport Automatik PDF & Word (.docx)
        Card(
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Cetak & Eksport Laporan Automatik",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedButton(
                onClick = { showExportMenuDialog = ExportTarget.PDF },
                modifier = Modifier.weight(1f).height(48.dp).testTag("export_pdf_button"),
                shape = RoundedCornerShape(12.dp),
              ) {
                Icon(
                  Icons.Filled.PictureAsPdf,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("PDF / Cetak")
              }
              OutlinedButton(
                onClick = { showExportMenuDialog = ExportTarget.WORD },
                modifier = Modifier.weight(1f).height(48.dp).testTag("export_word_button"),
                shape = RoundedCornerShape(12.dp),
              ) {
                Icon(
                  Icons.Filled.Description,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.tertiary,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Word (.docx)")
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(32.dp))
      }
    }
  }

  if (showDatePicker) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = sessionDateMillis)
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            pickerState.selectedDateMillis?.let { chosen ->
              sessionDateMillis = chosen
              dayName = malayDayName(chosen)
            }
            showDatePicker = false
          }
        ) {
          Text("Pilih")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
      },
    ) {
      DatePicker(state = pickerState)
    }
  }

  if (showDeleteDialog && existingRecord != null) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text("Padam Rekod?") },
      text = {
        Text("Rekod ini akan dipadam secara kekal daripada peti besi awan anda. Tindakan ini tidak boleh dibatalkan.")
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteDialog = false
            onDeleteRecord(existingRecord.id) { onBack() }
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Padam")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) { Text("Batal") }
      },
    )
  }

  showExportMenuDialog?.let { target ->
    val draft = buildDraftRecord()
    AlertDialog(
      onDismissRequest = { showExportMenuDialog = null },
      title = {
        Text(
          if (target == ExportTarget.PDF) "Laporan PDF Automatik"
          else "Laporan Microsoft Word (.docx)"
        )
      },
      text = {
        Text(
          if (target == ExportTarget.PDF) {
            "Pilih sama ada untuk mencetak terus ke pencetak/PDF sistem, menyimpan fail PDF ke peranti, atau berkongsi laporan."
          } else {
            "Simpan fail Word (.docx) yang boleh disunting atau kongsikannya terus melalui aplikasi lain."
          }
        )
      },
      confirmButton = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (target == ExportTarget.PDF) {
            Button(
              onClick = {
                showExportMenuDialog = null
                ReportExporter.printPdfDirect(context, draft, counselorName, counselorEmail)
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Cetak Terus (PrintManager)")
            }
          }
          FilledTonalButton(
            onClick = {
              showExportMenuDialog = null
              val ext = if (target == ExportTarget.PDF) "pdf" else "docx"
              val filename = ReportExporter.buildSuggestedFileName(draft, ext)
              if (target == ExportTarget.PDF) savePdfLauncher.launch(filename)
              else saveWordLauncher.launch(filename)
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Simpan ke Fail Peranti")
          }
          OutlinedButton(
            onClick = {
              showExportMenuDialog = null
              val ext = if (target == ExportTarget.PDF) "pdf" else "docx"
              val mime =
                if (target == ExportTarget.PDF) "application/pdf"
                else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
              val bytes =
                if (target == ExportTarget.PDF) {
                  ReportExporter.generatePdfBytes(draft, counselorName, counselorEmail)
                } else {
                  ReportExporter.generateDocxBytes(draft, counselorName, counselorEmail)
                }
              ReportExporter.shareReportFile(
                context,
                ReportExporter.buildSuggestedFileName(draft, ext),
                mime,
                bytes,
              )
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Kongsi Fail")
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { showExportMenuDialog = null }) { Text("Tutup") }
      },
    )
  }
}

private enum class ExportTarget {
  PDF,
  WORD,
}

private fun malayDayName(millis: Long): String {
  val cal = Calendar.getInstance().apply { timeInMillis = millis }
  return when (cal.get(Calendar.DAY_OF_WEEK)) {
    Calendar.MONDAY -> "Isnin"
    Calendar.TUESDAY -> "Selasa"
    Calendar.WEDNESDAY -> "Rabu"
    Calendar.THURSDAY -> "Khamis"
    Calendar.FRIDAY -> "Jumaat"
    Calendar.SATURDAY -> "Sabtu"
    Calendar.SUNDAY -> "Ahad"
    else -> "Isnin"
  }
}
