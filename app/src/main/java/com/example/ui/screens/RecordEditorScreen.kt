package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Update
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
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.export.ReportExporter
import com.example.model.ActivityCategory
import com.example.model.CounselingRecord
import com.example.ui.components.SectionStepHeader
import com.example.ui.components.rememberCategoryVisualMap
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentSky
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
  onGenerateAiSummary: (CounselingRecord) -> Unit,
  onResetAiState: () -> Unit,
  onSaveRecord: (CounselingRecord, onResult: (Boolean) -> Unit) -> Unit,
  onDeleteRecord: (String, onDone: () -> Unit) -> Unit,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val categoryVisuals = rememberCategoryVisualMap()

  var category by rememberSaveable {
    mutableStateOf(existingRecord?.category ?: initialCategory)
  }
  var clientName by rememberSaveable { mutableStateOf(existingRecord?.clientName.orEmpty()) }
  var className by rememberSaveable { mutableStateOf(existingRecord?.className.orEmpty()) }
  var organizer by rememberSaveable { mutableStateOf(existingRecord?.organizer.orEmpty()) }
  var guardianName by rememberSaveable { mutableStateOf(existingRecord?.guardianName.orEmpty()) }
  var guardianPhone by rememberSaveable { mutableStateOf(existingRecord?.guardianPhone.orEmpty()) }
  var sessionDateMillis by rememberSaveable {
    mutableLongStateOf(existingRecord?.sessionDateMillis ?: System.currentTimeMillis())
  }
  var dayName by rememberSaveable {
    mutableStateOf(existingRecord?.dayName?.ifBlank { null } ?: malayDayName(sessionDateMillis))
  }
  val initialSchedule =
    remember(existingRecord?.id) {
      parseInitialTimeSchedule(
        timeRange = existingRecord?.timeRange,
        fallbackDurationMinutes = existingRecord?.durationMinutes ?: 45,
        nowMinutes = currentMinutesOfDay(),
      )
    }
  var startMinutesOfDay by rememberSaveable { mutableIntStateOf(initialSchedule.first) }
  var endMinutesOfDay by rememberSaveable { mutableIntStateOf(initialSchedule.second) }
  var endTimeCustomized by rememberSaveable { mutableStateOf(existingRecord != null) }

  val timeRange =
    remember(startMinutesOfDay, endMinutesOfDay) {
      formatTimeRange(startMinutesOfDay, endMinutesOfDay)
    }
  val scheduleEval =
    remember(startMinutesOfDay, endMinutesOfDay) {
      calculateDurationFromSchedule(startMinutesOfDay, endMinutesOfDay)
    }

  var notes by rememberSaveable { mutableStateOf(existingRecord?.notes.orEmpty()) }
  var summaryMatter by rememberSaveable { mutableStateOf(existingRecord?.summaryMatter.orEmpty()) }
  var summaryQuestions by rememberSaveable {
    mutableStateOf(existingRecord?.summaryQuestions.orEmpty())
  }
  var programStrengthsWeaknesses by rememberSaveable {
    mutableStateOf(existingRecord?.programStrengthsWeaknesses.orEmpty())
  }
  var summaryIntervention by rememberSaveable {
    mutableStateOf(existingRecord?.summaryIntervention.orEmpty())
  }

  var isSaving by remember { mutableStateOf(false) }
  var showDatePicker by remember { mutableStateOf(false) }
  var activeTimePickerTarget by remember { mutableStateOf<TimePickerTarget?>(null) }
  var showDeleteDialog by remember { mutableStateOf(false) }
  var showExportMenuDialog by remember { mutableStateOf<ExportTarget?>(null) }
  var validationError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(aiSummaryState) {
    if (aiSummaryState is AiSummaryState.Success) {
      summaryMatter = aiSummaryState.result.matter
      summaryQuestions = aiSummaryState.result.questions
      if (category == ActivityCategory.PROGRAM) {
        programStrengthsWeaknesses = aiSummaryState.result.strengthsWeaknesses
      }
      summaryIntervention = aiSummaryState.result.intervention
      onResetAiState()
      Toast.makeText(context, "Rumusan AI berjaya dijana.", Toast.LENGTH_SHORT).show()
    }
  }

  fun applyStartTime(newStartMinutes: Int) {
    val normalized = ((newStartMinutes % 1440) + 1440) % 1440
    val previousValidDuration =
      calculateDurationFromSchedule(startMinutesOfDay, endMinutesOfDay)
        .takeIf { !it.isInvalidOrder && it.durationMinutes > 0 }
        ?.durationMinutes
        ?: 45
    val keptOrder = calculateDurationFromSchedule(normalized, endMinutesOfDay)
    startMinutesOfDay = normalized
    if (!endTimeCustomized || keptOrder.isInvalidOrder) {
      endMinutesOfDay = (normalized + previousValidDuration) % 1440
    }
  }

  fun applyEndTime(newEndMinutes: Int) {
    val normalized = ((newEndMinutes % 1440) + 1440) % 1440
    endMinutesOfDay = normalized
    endTimeCustomized = true
  }

  fun applyDurationPreset(presetMinutes: Int) {
    endMinutesOfDay = (startMinutesOfDay + presetMinutes) % 1440
    endTimeCustomized = false
  }

  fun buildDraftRecord(): CounselingRecord {
    val dur = scheduleEval.durationMinutes.coerceIn(0, 1440)
    val isSubstituteClass = category == ActivityCategory.KELAS_GANTI
    val hasSummaryContent =
      !isSubstituteClass &&
        (summaryMatter.isNotBlank() ||
          summaryQuestions.isNotBlank() ||
          programStrengthsWeaknesses.isNotBlank() ||
          summaryIntervention.isNotBlank())
    return CounselingRecord(
      id = existingRecord?.id.orEmpty(),
      userId = existingRecord?.userId.orEmpty(),
      category = category,
      clientName = if (isSubstituteClass) "" else clientName.trim(),
      className = if (category == ActivityCategory.MESYUARAT) "" else className.trim(),
      organizer = if (category == ActivityCategory.PROGRAM) organizer.trim() else "",
      guardianName =
        if (category == ActivityCategory.ZIARAH_CAKNA ||
          category == ActivityCategory.KONSULTASI_IBUBAPA
        ) {
          guardianName.trim()
        } else {
          ""
        },
      guardianPhone =
        if (category == ActivityCategory.ZIARAH_CAKNA ||
          category == ActivityCategory.KONSULTASI_IBUBAPA
        ) {
          guardianPhone.trim()
        } else {
          ""
        },
      sessionDateMillis = sessionDateMillis,
      dayName = dayName.trim().ifBlank { malayDayName(sessionDateMillis) },
      timeRange = timeRange,
      durationMinutes = dur,
      notes = notes.trim(),
      summaryMatter = if (isSubstituteClass) "" else summaryMatter.trim(),
      summaryQuestions = if (isSubstituteClass) "" else summaryQuestions.trim(),
      programStrengthsWeaknesses =
        if (category == ActivityCategory.PROGRAM) programStrengthsWeaknesses.trim() else "",
      summaryIntervention = if (isSubstituteClass) "" else summaryIntervention.trim(),
      hasAiSummary = hasSummaryContent,
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
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          ),
        title = {
          Column {
            Text(
              text = if (existingRecord == null) "Catatan Baharu" else "Butiran & Rumusan Rekod",
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text = if (existingRecord == null) "Sesi / Aktiviti Baharu" else "Rekod Disulitkan E2EE",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // 1. Kategori Sesi & Aktiviti
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          shape = RoundedCornerShape(22.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionStepHeader(
              stepNumber = "1",
              title = "Kategori Sesi / Aktiviti",
              subtitle = "Pilih jenis catatan kaunseling atau aktiviti harian",
              accentColor = categoryVisuals[category]?.color ?: MaterialTheme.colorScheme.primary,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              ActivityCategory.entries.forEach { cat ->
                val visual = categoryVisuals[cat]
                val accent = visual?.color ?: MaterialTheme.colorScheme.primary
                FilterChip(
                  selected = category == cat,
                  onClick = { category = cat },
                  leadingIcon =
                    visual?.let {
                      {
                        Icon(
                          imageVector = it.icon,
                          contentDescription = null,
                          modifier = Modifier.size(16.dp),
                          tint = if (category == cat) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    },
                  label = { Text(cat.displayName) },
                  shape = RoundedCornerShape(50),
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = accent.copy(alpha = 0.14f),
                      selectedLabelColor = accent,
                    ),
                  modifier = Modifier.testTag("editor_cat_${cat.name.lowercase()}"),
                )
              }
            }
          }
        }

        // 2. Maklumat Mengikut Kategori (Sesi, Program, Kelas Ganti, Mesyuarat, Ziarah Cakna / Konsultasi)
        val step2HeaderTitle =
          when (category) {
            ActivityCategory.PROGRAM -> "Maklumat Program"
            ActivityCategory.KELAS_GANTI -> "Maklumat Kelas Ganti"
            ActivityCategory.MESYUARAT -> "Maklumat Mesyuarat"
            ActivityCategory.ZIARAH_CAKNA -> "Maklumat Ziarah Cakna"
            ActivityCategory.KONSULTASI_IBUBAPA -> "Maklumat Konsultasi Ibu Bapa"
            else -> "Maklumat Sesi & Klien"
          }
        val step2HeaderSubtitle =
          when (category) {
            ActivityCategory.PROGRAM -> "Nama program, anjuran, sasaran, tarikh, hari & masa"
            ActivityCategory.KELAS_GANTI ->
              "Nama kelas/tingkatan, tarikh, masa & ringkasan aktiviti"
            ActivityCategory.MESYUARAT -> "Nama mesyuarat, tarikh, hari & masa"
            ActivityCategory.ZIARAH_CAKNA,
            ActivityCategory.KONSULTASI_IBUBAPA ->
              "Nama & kelas murid, nama penjaga, no. HP, tarikh & masa"
            else -> "Nama klien/peserta, kelas, hari & masa"
          }

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
            SectionStepHeader(
              stepNumber = "2",
              title = step2HeaderTitle,
              subtitle = step2HeaderSubtitle,
              accentColor = MaterialTheme.colorScheme.primary,
            )

            when (category) {
              ActivityCategory.PROGRAM -> {
                OutlinedTextField(
                  value = clientName,
                  onValueChange = { clientName = it },
                  label = { Text("Nama Program") },
                  placeholder = { Text("cth. Program Minda Sihat & Motivasi SPM") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("client_name_input"),
                )
                OutlinedTextField(
                  value = organizer,
                  onValueChange = { organizer = it },
                  label = { Text("Anjuran") },
                  placeholder = { Text("cth. Unit Bimbingan dan Kaunseling / PIBG") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("organizer_input"),
                )
                OutlinedTextField(
                  value = className,
                  onValueChange = { className = it },
                  label = { Text("Sasaran") },
                  placeholder = { Text("cth. Semua Murid Tingkatan 5") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("class_name_input"),
                )
              }

              ActivityCategory.KELAS_GANTI -> {
                OutlinedTextField(
                  value = className,
                  onValueChange = { className = it },
                  label = { Text("Nama Kelas / Tingkatan") },
                  placeholder = { Text("cth. 4 Sains 2 / 3 Amanah") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("class_name_input"),
                )
              }

              ActivityCategory.MESYUARAT -> {
                OutlinedTextField(
                  value = clientName,
                  onValueChange = { clientName = it },
                  label = { Text("Nama Mesyuarat") },
                  placeholder = { Text("cth. Mesyuarat Unit Bimbingan & Kaunseling Bil. 2") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("client_name_input"),
                )
              }

              ActivityCategory.ZIARAH_CAKNA,
              ActivityCategory.KONSULTASI_IBUBAPA -> {
                OutlinedTextField(
                  value = clientName,
                  onValueChange = { clientName = it },
                  label = { Text("Nama Murid") },
                  placeholder = { Text("cth. Nurul Aina binti Hassan") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("client_name_input"),
                )
                OutlinedTextField(
                  value = className,
                  onValueChange = { className = it },
                  label = { Text("Kelas Murid") },
                  placeholder = { Text("cth. 5 Sains 1") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("class_name_input"),
                )
                OutlinedTextField(
                  value = guardianName,
                  onValueChange = { guardianName = it },
                  label = { Text("Nama Penjaga / Ibu Bapa") },
                  placeholder = { Text("cth. Encik Hassan bin Ahmad") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("guardian_name_input"),
                )
                OutlinedTextField(
                  value = guardianPhone,
                  onValueChange = { guardianPhone = it },
                  label = { Text("No. HP Penjaga") },
                  placeholder = { Text("cth. 012-3456789") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("guardian_phone_input"),
                )
              }

              else -> {
                OutlinedTextField(
                  value = clientName,
                  onValueChange = { clientName = it },
                  label = { Text("Nama Klien / Ahli Kelompok") },
                  placeholder = { Text("cth. Nurul Aina binti Hassan") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("client_name_input"),
                )
                OutlinedTextField(
                  value = className,
                  onValueChange = { className = it },
                  label = { Text("Kelas / Tingkatan / Lokasi") },
                  placeholder = { Text("cth. 5 Sains 1 / Bilik Kaunseling") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("class_name_input"),
                )
              }
            }

            val dateDisplay =
              SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("ms-MY"))
                .format(Date(sessionDateMillis))

            if (category == ActivityCategory.KELAS_GANTI) {
              OutlinedButton(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("date_picker_button"),
              ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tarikh: $dateDisplay (${dayName})", maxLines = 1)
              }
            } else {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                OutlinedButton(
                  onClick = { showDatePicker = true },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.weight(1f).height(54.dp).testTag("date_picker_button"),
                ) {
                  Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(dateDisplay, maxLines = 1)
                }

                OutlinedTextField(
                  value = dayName,
                  onValueChange = { dayName = it },
                  label = { Text("Hari (Auto)") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.weight(0.75f).testTag("day_input"),
                )
              }
            }

            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              shape = RoundedCornerShape(16.dp),
              border =
                BorderStroke(
                  1.dp,
                  if (scheduleEval.isInvalidOrder) MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                  else MaterialTheme.colorScheme.outlineVariant,
                ),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Schedule,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(16.dp),
                    )
                    Text(
                      text = "Masa Sesi & Kiraan Tempoh",
                      style = MaterialTheme.typography.labelLarge,
                      fontWeight = FontWeight.Bold,
                    )
                  }
                  Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(50),
                    modifier =
                      Modifier.clip(RoundedCornerShape(50))
                        .clickable { applyStartTime(currentMinutesOfDay()) }
                        .testTag("time_now_button"),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Filled.Update,
                        contentDescription = "Guna masa semasa",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp),
                      )
                      Text(
                        text = "Sekarang",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                      )
                    }
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  TimeSelectorTile(
                    label = "MASA MULA",
                    time24h = formatTime24Hour(startMinutesOfDay),
                    time12h = formatTimeMalay12Hour(startMinutesOfDay),
                    isError = false,
                    onClick = { activeTimePickerTarget = TimePickerTarget.START },
                    modifier = Modifier.weight(1f).testTag("start_time_button"),
                  )
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "hingga",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                  )
                  TimeSelectorTile(
                    label = "MASA TAMAT",
                    time24h = formatTime24Hour(endMinutesOfDay),
                    time12h = formatTimeMalay12Hour(endMinutesOfDay),
                    isError = scheduleEval.isInvalidOrder,
                    onClick = { activeTimePickerTarget = TimePickerTarget.END },
                    modifier = Modifier.weight(1f).testTag("end_time_button"),
                  )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    text = "Tempoh Pantas (tetap Masa Tamat automatik):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    listOf(15, 30, 45, 60, 90).forEach { presetMins ->
                      val isSelected =
                        !scheduleEval.isInvalidOrder && scheduleEval.durationMinutes == presetMins
                      val labelText =
                        when (presetMins) {
                          60 -> "1 jam"
                          90 -> "1 jam 30m"
                          else -> "$presetMins min"
                        }
                      FilterChip(
                        selected = isSelected,
                        onClick = { applyDurationPreset(presetMins) },
                        label = {
                          Text(
                            text = labelText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                          )
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("duration_preset_$presetMins"),
                      )
                    }
                  }
                }

                Surface(
                  color =
                    if (scheduleEval.isInvalidOrder) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                  shape = RoundedCornerShape(12.dp),
                  modifier =
                    Modifier.fillMaxWidth()
                      .semantics(mergeDescendants = true) {}
                      .testTag("duration_auto_display"),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Row(
                      modifier = Modifier.weight(1f),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        tint =
                          if (scheduleEval.isInvalidOrder) MaterialTheme.colorScheme.error
                          else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                      )
                      Column {
                        Text(
                          text =
                            if (scheduleEval.isInvalidOrder) {
                              "Masa tamat mesti selepas masa mula"
                            } else {
                              "Tempoh Automatik ($timeRange)"
                            },
                          style = MaterialTheme.typography.labelMedium,
                          fontWeight = FontWeight.SemiBold,
                          color =
                            if (scheduleEval.isInvalidOrder) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        if (!scheduleEval.isInvalidOrder) {
                          Text(
                            text = "Dikira automatik daripada masa mula & tamat",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                          )
                        }
                      }
                    }
                    Surface(
                      color =
                        if (scheduleEval.isInvalidOrder) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                      shape = RoundedCornerShape(8.dp),
                    ) {
                      Text(
                        text =
                          if (scheduleEval.isInvalidOrder) "Tidak sah"
                          else formatDurationSummary(scheduleEval.durationMinutes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color =
                          if (scheduleEval.isInvalidOrder) MaterialTheme.colorScheme.onError
                          else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                      )
                    }
                  }
                }
              }
            }

            if (category == ActivityCategory.KELAS_GANTI) {
              OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Ringkasan Aktiviti") },
                placeholder = {
                  Text(
                    "Catatkan pengisian atau aktiviti yang dijalankan bersama murid semasa kelas ganti..."
                  )
                },
                minLines = 4,
                maxLines = 10,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("notes_input"),
              )
            }
          }
        }

        if (category != ActivityCategory.KELAS_GANTI) {
          // 3. Transkrip Perbualan / Catatan Ringkas
          val notesTitle =
            when (category) {
              ActivityCategory.MESYUARAT -> "Catatan / Minit Ringkas Mesyuarat"
              ActivityCategory.ZIARAH_CAKNA,
              ActivityCategory.KONSULTASI_IBUBAPA -> "Catatan / Transkrip Perbincangan"
              else -> "Transkrip / Catatan Ringkas"
            }
          val notesSubtitle =
            when (category) {
              ActivityCategory.PROGRAM -> "Catatan pelaksanaan program, pengisian dan pemerhatian"
              ActivityCategory.MESYUARAT -> "Agenda, perbincangan dan ketetapan mesyuarat"
              ActivityCategory.ZIARAH_CAKNA,
              ActivityCategory.KONSULTASI_IBUBAPA ->
                "Catatan perbualan bersama penjaga / murid semasa sesi"
              else -> "Catatan perbualan atau pemerhatian kaunselor"
            }
          val notesPlaceholder =
            when (category) {
              ActivityCategory.PROGRAM ->
                "Catatkan aktiviti utama program, penglibatan peserta, kekuatan serta cabaran yang dihadapi..."
              ActivityCategory.MESYUARAT ->
                "Catatkan agenda yang dibincangkan, isu berbangkit, dan ketetapan mesyuarat..."
              ActivityCategory.ZIARAH_CAKNA,
              ActivityCategory.KONSULTASI_IBUBAPA ->
                "Catatkan inti pati perbincangan bersama ibu bapa/penjaga, latar belakang isu, dan persetujuan bersama..."
              else ->
                "Catatkan transkrip perbualan, pemerhatian tingkah laku, isu yang dikongsi klien, atau butiran aktiviti..."
            }

          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              SectionStepHeader(
                stepNumber = "3",
                title = notesTitle,
                subtitle = notesSubtitle,
                accentColor = AccentCoral,
              )
              OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text(notesPlaceholder) },
                minLines = 5,
                maxLines = 12,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("notes_input"),
              )
            }
          }

          // 4. Kotak Rumusan & Janaan AI (mengikut kategori)
          val aiBoxTitle =
            when (category) {
              ActivityCategory.ZIARAH_CAKNA,
              ActivityCategory.KONSULTASI_IBUBAPA -> "Kotak Rumusan Perbincangan & AI"
              else -> "Kotak Rumusan & Analisis AI"
            }
          val aiBoxSubtitle =
            when (category) {
              ActivityCategory.PROGRAM ->
                "Rumusan Program, Objektif, Kelebihan & Kelemahan & Huraian Intervensi"
              ActivityCategory.ZIARAH_CAKNA,
              ActivityCategory.KONSULTASI_IBUBAPA ->
                "Rumusan perbincangan bersama penjaga (isi terus atau jana AI)"
              ActivityCategory.MESYUARAT ->
                "Rumusan Agenda, Isu Berbangkit & Keputusan Mesyuarat"
              else -> "Perkara, Persoalan & Huraian Intervensi automatik"
            }
          val aiButtonLabel =
            when (category) {
              ActivityCategory.PROGRAM -> "Jana Rumusan Program AI daripada Catatan"
              ActivityCategory.ZIARAH_CAKNA,
              ActivityCategory.KONSULTASI_IBUBAPA -> "Jana Rumusan Perbincangan AI daripada Catatan"
              else -> "Jana Rumusan AI daripada Catatan"
            }

          val aiBorderBrush =
            Brush.linearGradient(
              listOf(MaterialTheme.colorScheme.primary, AccentIndigo, AccentCoral)
            )
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, aiBorderBrush),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().testTag("ai_summary_box"),
          ) {
            Column(
              modifier =
                Modifier.background(
                    Brush.verticalGradient(
                      listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                        MaterialTheme.colorScheme.surface,
                      )
                    )
                  )
                  .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              SectionStepHeader(
                stepNumber = "4",
                title = aiBoxTitle,
                subtitle = aiBoxSubtitle,
                accentColor = MaterialTheme.colorScheme.tertiary,
              )

              FilledTonalButton(
                onClick = { onGenerateAiSummary(buildDraftRecord()) },
                enabled = aiSummaryState !is AiSummaryState.Loading,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("generate_ai_button"),
                shape = RoundedCornerShape(14.dp),
                colors =
                  ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                  ),
              ) {
                if (aiSummaryState is AiSummaryState.Loading) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text("AI sedang menganalisis catatan...", fontWeight = FontWeight.Bold)
                } else {
                  Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(aiButtonLabel, fontWeight = FontWeight.Bold)
                }
              }

              if (aiSummaryState is AiSummaryState.Error) {
                Surface(
                  color = MaterialTheme.colorScheme.errorContainer,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = aiSummaryState.message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp),
                  )
                }
              }

              when (category) {
                ActivityCategory.PROGRAM -> {
                  OutlinedTextField(
                    value = summaryMatter,
                    onValueChange = { summaryMatter = it },
                    label = { Text("1. Rumusan Program") },
                    minLines = 2,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("summary_matter_input"),
                  )
                  OutlinedTextField(
                    value = summaryQuestions,
                    onValueChange = { summaryQuestions = it },
                    label = { Text("2. Objektif") },
                    minLines = 2,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("summary_questions_input"),
                  )
                  OutlinedTextField(
                    value = programStrengthsWeaknesses,
                    onValueChange = { programStrengthsWeaknesses = it },
                    label = { Text("3. Kelebihan & Kelemahan") },
                    minLines = 2,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("program_strengths_weaknesses_input"),
                  )
                  OutlinedTextField(
                    value = summaryIntervention,
                    onValueChange = { summaryIntervention = it },
                    label = { Text("4. Huraian Intervensi") },
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("summary_intervention_input"),
                  )
                }

                ActivityCategory.ZIARAH_CAKNA,
                ActivityCategory.KONSULTASI_IBUBAPA -> {
                  OutlinedTextField(
                    value = summaryMatter,
                    onValueChange = { summaryMatter = it },
                    label = { Text("Rumusan Perbincangan") },
                    placeholder = { Text("Ringkasan inti pati perbincangan bersama penjaga & murid...") },
                    minLines = 3,
                    maxLines = 7,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("summary_matter_input"),
                  )
                  OutlinedTextField(
                    value = summaryQuestions,
                    onValueChange = { summaryQuestions = it },
                    label = { Text("Isu / Persoalan Dikemukakan") },
                    minLines = 2,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("summary_questions_input"),
                  )
                  OutlinedTextField(
                    value = summaryIntervention,
                    onValueChange = { summaryIntervention = it },
                    label = { Text("Tindakan / Susulan Penjaga & Kaunselor") },
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("summary_intervention_input"),
                  )
                }

                ActivityCategory.MESYUARAT -> {
                  OutlinedTextField(
                    value = summaryMatter,
                    onValueChange = { summaryMatter = it },
                    label = { Text("1. Rumusan Agenda / Perkara") },
                    minLines = 2,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("summary_matter_input"),
                  )
                  OutlinedTextField(
                    value = summaryQuestions,
                    onValueChange = { summaryQuestions = it },
                    label = { Text("2. Persoalan / Isu Berbangkit") },
                    minLines = 2,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("summary_questions_input"),
                  )
                  OutlinedTextField(
                    value = summaryIntervention,
                    onValueChange = { summaryIntervention = it },
                    label = { Text("3. Keputusan & Tindakan Susulan") },
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("summary_intervention_input"),
                  )
                }

                else -> {
                  OutlinedTextField(
                    value = summaryMatter,
                    onValueChange = { summaryMatter = it },
                    label = { Text("1. Perkara (Isu / Tema Utama)") },
                    minLines = 2,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("summary_matter_input"),
                  )
                  OutlinedTextField(
                    value = summaryQuestions,
                    onValueChange = { summaryQuestions = it },
                    label = { Text("2. Persoalan") },
                    minLines = 2,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("summary_questions_input"),
                  )
                  OutlinedTextField(
                    value = summaryIntervention,
                    onValueChange = { summaryIntervention = it },
                    label = { Text("3. Huraian Intervensi") },
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("summary_intervention_input"),
                  )
                }
              }
            }
          }
        }

        if (validationError != null) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = validationError!!,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        // 5. Butang Simpan Rekod (E2EE + Sandaran Awan)
        Button(
          onClick = {
            validationError = null
            val hasPrimaryContent =
              when (category) {
                ActivityCategory.KELAS_GANTI -> className.isNotBlank() || notes.isNotBlank()
                ActivityCategory.ZIARAH_CAKNA,
                ActivityCategory.KONSULTASI_IBUBAPA ->
                  clientName.isNotBlank() ||
                    guardianName.isNotBlank() ||
                    summaryMatter.isNotBlank() ||
                    notes.isNotBlank()
                else ->
                  clientName.isNotBlank() || notes.isNotBlank() || summaryMatter.isNotBlank()
              }
            if (!hasPrimaryContent) {
              validationError =
                when (category) {
                  ActivityCategory.KELAS_GANTI ->
                    "Sila isi sekurang-kurangnya Nama Kelas / Tingkatan atau Ringkasan Aktiviti."
                  ActivityCategory.ZIARAH_CAKNA,
                  ActivityCategory.KONSULTASI_IBUBAPA ->
                    "Sila isi sekurang-kurangnya Nama Murid, Nama Penjaga atau Rumusan Perbincangan."
                  ActivityCategory.PROGRAM ->
                    "Sila isi sekurang-kurangnya Nama Program atau Catatan."
                  ActivityCategory.MESYUARAT ->
                    "Sila isi sekurang-kurangnya Nama Mesyuarat atau Catatan."
                  else -> "Sila isi sekurang-kurangnya Nama Klien/Sasaran atau Catatan."
                }
              return@Button
            }
            if (scheduleEval.isInvalidOrder) {
              validationError =
                "Sila pilih Masa Tamat selepas Masa Mula atau ketik pilihan tempoh pantas."
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
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("save_record_button"),
          shape = RoundedCornerShape(16.dp),
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
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          shape = RoundedCornerShape(22.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                border = BorderStroke(1.dp, AccentCoral.copy(alpha = 0.45f)),
                colors =
                  ButtonDefaults.outlinedButtonColors(
                    containerColor = AccentCoral.copy(alpha = 0.08f),
                    contentColor = AccentCoral,
                  ),
                modifier = Modifier.weight(1f).height(50.dp).testTag("export_pdf_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(
                  Icons.Filled.PictureAsPdf,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("PDF / Cetak", fontWeight = FontWeight.Bold)
              }
              OutlinedButton(
                onClick = { showExportMenuDialog = ExportTarget.WORD },
                border = BorderStroke(1.dp, AccentSky.copy(alpha = 0.45f)),
                colors =
                  ButtonDefaults.outlinedButtonColors(
                    containerColor = AccentSky.copy(alpha = 0.08f),
                    contentColor = AccentSky,
                  ),
                modifier = Modifier.weight(1f).height(50.dp).testTag("export_word_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(
                  Icons.Filled.Description,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Word (.docx)", fontWeight = FontWeight.Bold)
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

  activeTimePickerTarget?.let { target ->
    val initialMins =
      if (target == TimePickerTarget.START) startMinutesOfDay else endMinutesOfDay
    val pickerState =
      rememberTimePickerState(
        initialHour = (initialMins / 60).coerceIn(0, 23),
        initialMinute = (initialMins % 60).coerceIn(0, 59),
        is24Hour = true,
      )
    var useKeyboardInput by remember { mutableStateOf(false) }

    AlertDialog(
      onDismissRequest = { activeTimePickerTarget = null },
      title = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text =
              if (target == TimePickerTarget.START) "Pilih Masa Mula" else "Pilih Masa Tamat",
            style = MaterialTheme.typography.titleLarge,
          )
          IconButton(
            onClick = { useKeyboardInput = !useKeyboardInput },
            modifier = Modifier.testTag("toggle_time_input_mode"),
          ) {
            Icon(
              imageVector =
                if (useKeyboardInput) Icons.Filled.Schedule else Icons.Filled.Keyboard,
              contentDescription =
                if (useKeyboardInput) "Tukar ke jam analog" else "Tukar ke input papan kekunci",
            )
          }
        }
      },
      text = {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          if (useKeyboardInput) {
            TimeInput(state = pickerState)
          } else {
            TimePicker(state = pickerState)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val chosenMinutes = pickerState.hour * 60 + pickerState.minute
            if (target == TimePickerTarget.START) {
              applyStartTime(chosenMinutes)
            } else {
              applyEndTime(chosenMinutes)
            }
            activeTimePickerTarget = null
          },
          modifier = Modifier.testTag("confirm_time_picker_button"),
        ) {
          Text("Tetapkan")
        }
      },
      dismissButton = {
        TextButton(onClick = { activeTimePickerTarget = null }) { Text("Batal") }
      },
    )
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

@Composable
private fun TimeSelectorTile(
  label: String,
  time24h: String,
  time12h: String,
  isError: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    color =
      if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
      else MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(14.dp),
    border =
      BorderStroke(
        1.dp,
        if (isError) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.outlineVariant,
      ),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color =
          if (isError) MaterialTheme.colorScheme.error
          else MaterialTheme.colorScheme.primary,
      )
      Text(
        text = time24h,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color =
          if (isError) MaterialTheme.colorScheme.error
          else MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = time12h,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private enum class TimePickerTarget {
  START,
  END,
}

private enum class ExportTarget {
  PDF,
  WORD,
}

internal data class TimeCalculationResult(
  val durationMinutes: Int,
  val isInvalidOrder: Boolean,
)

internal fun currentMinutesOfDay(calendar: Calendar = Calendar.getInstance()): Int =
  calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

internal fun formatTime24Hour(minutesOfDay: Int): String {
  val normalized = ((minutesOfDay % 1440) + 1440) % 1440
  return String.format(Locale.US, "%02d:%02d", normalized / 60, normalized % 60)
}

internal fun formatTimeMalay12Hour(minutesOfDay: Int): String {
  val normalized = ((minutesOfDay % 1440) + 1440) % 1440
  val hour24 = normalized / 60
  val minute = normalized % 60
  val hour12 =
    when {
      hour24 == 0 -> 12
      hour24 > 12 -> hour24 - 12
      else -> hour24
    }
  val period =
    when (hour24) {
      in 0..11 -> "PG"
      12 -> "TGH"
      in 13..18 -> "PTG"
      else -> "MLM"
    }
  return String.format(Locale.US, "%d:%02d %s", hour12, minute, period)
}

internal fun formatTimeRange(startMinutesOfDay: Int, endMinutesOfDay: Int): String =
  "${formatTime24Hour(startMinutesOfDay)} - ${formatTime24Hour(endMinutesOfDay)}"

internal fun calculateDurationFromSchedule(
  startMinutesOfDay: Int,
  endMinutesOfDay: Int,
): TimeCalculationResult {
  val start = ((startMinutesOfDay % 1440) + 1440) % 1440
  val end = ((endMinutesOfDay % 1440) + 1440) % 1440
  return when {
    end > start -> TimeCalculationResult(durationMinutes = end - start, isInvalidOrder = false)
    end == start -> TimeCalculationResult(durationMinutes = 0, isInvalidOrder = true)
    start >= 18 * 60 && end < 6 * 60 ->
      TimeCalculationResult(durationMinutes = (1440 - start) + end, isInvalidOrder = false)
    else -> TimeCalculationResult(durationMinutes = 0, isInvalidOrder = true)
  }
}

internal fun formatDurationSummary(durationMinutes: Int): String {
  if (durationMinutes <= 0) return "0 min"
  if (durationMinutes < 60) return "$durationMinutes min"
  val hours = durationMinutes / 60
  val mins = durationMinutes % 60
  return if (mins == 0) "$durationMinutes min ($hours jam)"
  else "$durationMinutes min ($hours jam $mins m)"
}

internal fun parseTimeTokenToMinutes(token: String): Int? {
  val match =
    Regex("""(?i)(\d{1,2})[:.](\d{2})(?:\s*(AM|PM|PG|PTG|TGH|MLM))?""").find(token.trim())
      ?: return null
  var hour = match.groupValues[1].toIntOrNull() ?: return null
  val minute = match.groupValues[2].toIntOrNull() ?: return null
  if (minute !in 0..59) return null
  val suffix = match.groupValues[3].uppercase(Locale.US)
  when (suffix) {
    "AM", "PG" -> {
      if (hour !in 1..12) return null
      if (hour == 12) hour = 0
    }
    "PM", "PTG", "MLM" -> {
      if (hour !in 1..12) return null
      if (hour != 12) hour += 12
    }
    "TGH" -> {
      if (hour !in 1..12) return null
      if (hour != 12) hour += 12
    }
    else -> {
      if (hour !in 0..23) return null
    }
  }
  return hour * 60 + minute
}

internal fun parseInitialTimeSchedule(
  timeRange: String?,
  fallbackDurationMinutes: Int,
  nowMinutes: Int,
): Pair<Int, Int> {
  val defaultDuration = fallbackDurationMinutes.takeIf { it > 0 } ?: 45
  if (timeRange.isNullOrBlank()) {
    return Pair(nowMinutes, (nowMinutes + defaultDuration) % 1440)
  }
  val matches =
    Regex("""(?i)\d{1,2}[:.]\d{2}(?:\s*(?:AM|PM|PG|PTG|TGH|MLM))?""")
      .findAll(timeRange)
      .mapNotNull { parseTimeTokenToMinutes(it.value) }
      .toList()
  return when {
    matches.size >= 2 -> Pair(matches[0], matches[1])
    matches.size == 1 -> Pair(matches[0], (matches[0] + defaultDuration) % 1440)
    else -> Pair(nowMinutes, (nowMinutes + defaultDuration) % 1440)
  }
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
