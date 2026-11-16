package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.model.ActivityCategory
import com.example.model.CounselingRecord
import com.example.ui.screens.RecordEditorScreen
import com.example.ui.screens.calculateDurationFromSchedule
import com.example.ui.screens.formatDurationSummary
import com.example.ui.screens.formatTime24Hour
import com.example.ui.screens.formatTimeMalay12Hour
import com.example.ui.screens.formatTimeRange
import com.example.ui.screens.parseInitialTimeSchedule
import com.example.ui.screens.parseTimeTokenToMinutes
import com.example.ui.theme.DiariKaunselorTheme
import com.example.ui.viewmodel.AiSummaryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecordEditorTimeTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun parseInitialTimeSchedule_defaultsToNowAnd45MinutesForNewRecord() {
    val (start, end) = parseInitialTimeSchedule(timeRange = null, fallbackDurationMinutes = 45, nowMinutes = 14 * 60 + 15)
    assertEquals(14 * 60 + 15, start)
    assertEquals(15 * 60, end)
    assertEquals("14:15 - 15:00", formatTimeRange(start, end))
  }

  @Test
  fun parseInitialTimeSchedule_wrapsPastMidnightForNewRecord() {
    val (start, end) = parseInitialTimeSchedule(timeRange = "  ", fallbackDurationMinutes = 45, nowMinutes = 23 * 60 + 40)
    assertEquals(23 * 60 + 40, start)
    assertEquals(25, end)
    val eval = calculateDurationFromSchedule(start, end)
    assertFalse(eval.isInvalidOrder)
    assertEquals(45, eval.durationMinutes)
  }

  @Test
  fun parseInitialTimeSchedule_parses24hAndMalay12hStrings() {
    assertEquals(Pair(9 * 60, 9 * 60 + 45), parseInitialTimeSchedule("09:00 - 09:45", 45, 0))
    assertEquals(Pair(14 * 60 + 30, 15 * 60 + 15), parseInitialTimeSchedule("2:30 PTG - 3:15 PTG", 45, 0))
    assertEquals(9 * 60 + 5, parseTimeTokenToMinutes("9.05 PG"))
    assertEquals(20 * 60 + 15, parseTimeTokenToMinutes("8:15 MLM"))
    assertEquals(12 * 60 + 30, parseTimeTokenToMinutes("12:30 TGH"))
  }

  @Test
  fun calculateDurationFromSchedule_handlesNormalOvernightAndInvalidOrder() {
    val normal = calculateDurationFromSchedule(8 * 60 + 30, 10 * 60)
    assertFalse(normal.isInvalidOrder)
    assertEquals(90, normal.durationMinutes)

    val overnight = calculateDurationFromSchedule(23 * 60 + 30, 15)
    assertFalse(overnight.isInvalidOrder)
    assertEquals(45, overnight.durationMinutes)

    val same = calculateDurationFromSchedule(10 * 60, 10 * 60)
    assertTrue(same.isInvalidOrder)
    assertEquals(0, same.durationMinutes)

    val backwards = calculateDurationFromSchedule(14 * 60, 13 * 60)
    assertTrue(backwards.isInvalidOrder)
    assertEquals(0, backwards.durationMinutes)
  }

  @Test
  fun formattingHelpers_produceExpectedDisplayStrings() {
    assertEquals("08:05", formatTime24Hour(8 * 60 + 5))
    assertEquals("8:05 PG", formatTimeMalay12Hour(8 * 60 + 5))
    assertEquals("12:10 TGH", formatTimeMalay12Hour(12 * 60 + 10))
    assertEquals("3:45 PTG", formatTimeMalay12Hour(15 * 60 + 45))
    assertEquals("9:00 MLM", formatTimeMalay12Hour(21 * 60))
    assertEquals("45 min", formatDurationSummary(45))
    assertEquals("60 min (1 jam)", formatDurationSummary(60))
    assertEquals("90 min (1 jam 30 m)", formatDurationSummary(90))
  }

  @Test
  fun editorScreen_newRecordUpdatesDurationAutomaticallyOnPresetTapAndSavesComputedDuration() {
    var savedRecord: CounselingRecord? = null

    composeTestRule.setContent {
      DiariKaunselorTheme {
        RecordEditorScreen(
          existingRecord = null,
          initialCategory = ActivityCategory.KAUNSELING_INDIVIDU,
          counselorName = "Cikgu Aina",
          counselorEmail = "aina@example.com",
          aiSummaryState = AiSummaryState.Idle,
          onGenerateAiSummary = { _ -> },
          onResetAiState = {},
          onSaveRecord = { record, onResult ->
            savedRecord = record
            onResult(true)
          },
          onDeleteRecord = { _, _ -> },
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("start_time_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("end_time_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("duration_auto_display").assertTextContains("45 min", substring = true)

    composeTestRule.onNodeWithTag("duration_preset_60").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("duration_auto_display").assertTextContains("60 min (1 jam)", substring = true)

    composeTestRule.onNodeWithTag("client_name_input").performScrollTo().performTextInput("Ahmad Daniel")
    composeTestRule.onNodeWithTag("save_record_button").performScrollTo().performClick()

    composeTestRule.waitForIdle()
    assertNotNull(savedRecord)
    assertEquals(60, savedRecord?.durationMinutes)
    assertTrue(savedRecord?.timeRange?.contains(" - ") == true)
  }

  @Test
  fun editorScreen_existingRecordCalculatesInitialDurationAndOpensTimePickerDialog() {
    val existing =
      CounselingRecord(
        id = "rec-1",
        userId = "user-1",
        category = ActivityCategory.KAUNSELING_KELOMPOK,
        clientName = "5 Sains 1",
        className = "Bilik Kaunseling",
        sessionDateMillis = System.currentTimeMillis(),
        dayName = "Isnin",
        timeRange = "08:30 - 10:00",
        durationMinutes = 90,
        notes = "Perbincangan kerjaya",
      )

    composeTestRule.setContent {
      DiariKaunselorTheme {
        RecordEditorScreen(
          existingRecord = existing,
          initialCategory = ActivityCategory.KAUNSELING_KELOMPOK,
          counselorName = "Cikgu Aina",
          counselorEmail = "aina@example.com",
          aiSummaryState = AiSummaryState.Idle,
          onGenerateAiSummary = { _ -> },
          onResetAiState = {},
          onSaveRecord = { _, _ -> },
          onDeleteRecord = { _, _ -> },
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("start_time_button").performScrollTo().assertTextContains("08:30", substring = true)
    composeTestRule.onNodeWithTag("end_time_button").assertTextContains("10:00", substring = true)
    composeTestRule.onNodeWithTag("duration_auto_display").assertTextContains("90 min (1 jam 30 m)", substring = true)

    composeTestRule.onNodeWithTag("start_time_button").performClick()
    composeTestRule.onNodeWithText("Pilih Masa Mula").assertIsDisplayed()
    composeTestRule.onNodeWithTag("toggle_time_input_mode").performClick()
    composeTestRule.onNodeWithTag("confirm_time_picker_button").performClick()
  }

  @Test
  fun editorScreen_programCategoryShowsProgramInfoAndFourSplitSummaryFields() {
    var savedRecord: CounselingRecord? = null

    composeTestRule.setContent {
      DiariKaunselorTheme {
        RecordEditorScreen(
          existingRecord = null,
          initialCategory = ActivityCategory.PROGRAM,
          counselorName = "Cikgu Aina",
          counselorEmail = "aina@example.com",
          aiSummaryState = AiSummaryState.Idle,
          onGenerateAiSummary = { _ -> },
          onResetAiState = {},
          onSaveRecord = { record, onResult ->
            savedRecord = record
            onResult(true)
          },
          onDeleteRecord = { _, _ -> },
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Maklumat Program").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("client_name_input").performScrollTo().performTextInput("Program Minda Sihat")
    composeTestRule.onNodeWithTag("organizer_input").performScrollTo().performTextInput("Unit Kaunseling")
    composeTestRule.onNodeWithTag("class_name_input").performScrollTo().performTextInput("Tingkatan 5")
    composeTestRule.onNodeWithTag("day_input").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("summary_matter_input").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("summary_questions_input").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("program_strengths_weaknesses_input").performScrollTo().performTextInput("Kekuatan: penyertaan aktif")
    composeTestRule.onNodeWithTag("summary_intervention_input").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("save_record_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(ActivityCategory.PROGRAM, savedRecord?.category)
    assertEquals("Program Minda Sihat", savedRecord?.clientName)
    assertEquals("Unit Kaunseling", savedRecord?.organizer)
    assertEquals("Tingkatan 5", savedRecord?.className)
    assertEquals("Kekuatan: penyertaan aktif", savedRecord?.programStrengthsWeaknesses)
  }

  @Test
  fun editorScreen_kelasGantiHidesAiBoxAndSavesActivitySummaryFromCard2() {
    var savedRecord: CounselingRecord? = null

    composeTestRule.setContent {
      DiariKaunselorTheme {
        RecordEditorScreen(
          existingRecord = null,
          initialCategory = ActivityCategory.KELAS_GANTI,
          counselorName = "Cikgu Aina",
          counselorEmail = "aina@example.com",
          aiSummaryState = AiSummaryState.Idle,
          onGenerateAiSummary = { _ -> },
          onResetAiState = {},
          onSaveRecord = { record, onResult ->
            savedRecord = record
            onResult(true)
          },
          onDeleteRecord = { _, _ -> },
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Maklumat Kelas Ganti").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("class_name_input").performScrollTo().performTextInput("4 Sains 2")
    composeTestRule.onNodeWithTag("notes_input").performScrollTo().performTextInput("Latihan pengukuhan minda dan kuiz kerjaya")
    composeTestRule.onNodeWithTag("ai_summary_box").assertDoesNotExist()
    composeTestRule.onNodeWithTag("generate_ai_button").assertDoesNotExist()

    composeTestRule.onNodeWithTag("save_record_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(ActivityCategory.KELAS_GANTI, savedRecord?.category)
    assertEquals("4 Sains 2", savedRecord?.className)
    assertEquals("Latihan pengukuhan minda dan kuiz kerjaya", savedRecord?.notes)
    assertFalse(savedRecord?.hasAiSummary == true)
  }

  @Test
  fun editorScreen_ziarahCaknaShowsGuardianFieldsAndDiscussionSummaryBox() {
    var savedRecord: CounselingRecord? = null

    composeTestRule.setContent {
      DiariKaunselorTheme {
        RecordEditorScreen(
          existingRecord = null,
          initialCategory = ActivityCategory.ZIARAH_CAKNA,
          counselorName = "Cikgu Aina",
          counselorEmail = "aina@example.com",
          aiSummaryState = AiSummaryState.Idle,
          onGenerateAiSummary = { _ -> },
          onResetAiState = {},
          onSaveRecord = { record, onResult ->
            savedRecord = record
            onResult(true)
          },
          onDeleteRecord = { _, _ -> },
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Maklumat Ziarah Cakna").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("client_name_input").performScrollTo().performTextInput("Aiman Hakim")
    composeTestRule.onNodeWithTag("class_name_input").performScrollTo().performTextInput("3 Cekal")
    composeTestRule.onNodeWithTag("guardian_name_input").performScrollTo().performTextInput("Puan Salma")
    composeTestRule.onNodeWithTag("guardian_phone_input").performScrollTo().performTextInput("013-9988776")
    composeTestRule.onNodeWithTag("summary_matter_input").performScrollTo().performTextInput("Penjaga bersetuju memantau jadual tidur murid.")

    composeTestRule.onNodeWithTag("save_record_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(ActivityCategory.ZIARAH_CAKNA, savedRecord?.category)
    assertEquals("Aiman Hakim", savedRecord?.clientName)
    assertEquals("3 Cekal", savedRecord?.className)
    assertEquals("Puan Salma", savedRecord?.guardianName)
    assertEquals("013-9988776", savedRecord?.guardianPhone)
    assertEquals("Penjaga bersetuju memantau jadual tidur murid.", savedRecord?.summaryMatter)
  }

  @Test
  fun editorScreen_mesyuaratShowsMeetingNameDateDayAndAutoTime() {
    composeTestRule.setContent {
      DiariKaunselorTheme {
        RecordEditorScreen(
          existingRecord = null,
          initialCategory = ActivityCategory.MESYUARAT,
          counselorName = "Cikgu Aina",
          counselorEmail = "aina@example.com",
          aiSummaryState = AiSummaryState.Idle,
          onGenerateAiSummary = { _ -> },
          onResetAiState = {},
          onSaveRecord = { _, _ -> },
          onDeleteRecord = { _, _ -> },
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Maklumat Mesyuarat").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("client_name_input").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("day_input").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("start_time_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("class_name_input").assertDoesNotExist()
  }
}
