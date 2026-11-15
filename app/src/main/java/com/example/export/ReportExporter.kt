package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.model.CounselingRecord
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ReportExporter {

  private val dateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("ms-MY"))
  private val dateTimeFormatter =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("ms-MY"))

  fun buildSuggestedFileName(record: CounselingRecord, extension: String): String {
    val safeName =
      record.clientName
        .ifBlank { record.category.shortLabel }
        .replace(Regex("[^a-zA-Z0-9_-]"), "_")
        .take(28)
    val dateTag = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(record.sessionDateMillis))
    return "Laporan_${safeName}_$dateTag.$extension"
  }

  fun generatePdfBytes(
    record: CounselingRecord,
    counselorName: String,
    counselorEmail: String,
  ): ByteArray {
    return try {
      generateAndroidPdfBytes(record, counselorName, counselorEmail)
    } catch (_: Throwable) {
      generateMinimalPdfFallback(record, counselorName, counselorEmail)
    }
  }

  private fun generateAndroidPdfBytes(
    record: CounselingRecord,
    counselorName: String,
    counselorEmail: String,
  ): ByteArray {
    val document = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 42f
    val contentWidth = (pageWidth - margin * 2).toInt()

    val sagePrimary = Color.rgb(26, 107, 90)
    val warmAccent = Color.rgb(194, 109, 56)
    val surfaceCream = Color.rgb(245, 242, 235)
    val borderColor = Color.rgb(214, 208, 196)
    val textDark = Color.rgb(28, 35, 33)
    val textMuted = Color.rgb(92, 102, 98)

    val titlePaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
      }
    val subtitlePaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(220, 240, 234)
        textSize = 9.5f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
      }
    val sectionTitlePaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = sagePrimary
        textSize = 12f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      }
    val labelPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textMuted
        textSize = 9.5f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      }
    val bodyPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textDark
        textSize = 10.5f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
      }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val strokePaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = borderColor
      }

    var pageNum = 1
    var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
    var page = document.startPage(pageInfo)
    var canvas = page.canvas

    fun drawFooter(c: Canvas, pNum: Int) {
      val footerPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          color = textMuted
          textSize = 8.5f
        }
      val lineY = pageHeight - 36f
      c.drawLine(margin, lineY - 8f, pageWidth - margin, lineY - 8f, strokePaint)
      c.drawText(
        "Dijana secara automatik oleh Diari Kaunselor • Sulit (E2EE ${record.encryptionVersion})",
        margin,
        lineY + 6f,
        footerPaint,
      )
      c.drawText("Halaman $pNum", pageWidth - margin - 52f, lineY + 6f, footerPaint)
    }

    fun newPage(): Float {
      drawFooter(canvas, pageNum)
      document.finishPage(page)
      pageNum += 1
      pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
      page = document.startPage(pageInfo)
      canvas = page.canvas
      return margin
    }

    // Header Banner
    fillPaint.color = sagePrimary
    canvas.drawRoundRect(
      RectF(margin, margin, pageWidth - margin, margin + 74f),
      10f,
      10f,
      fillPaint,
    )
    canvas.drawText(
      "LAPORAN SESI KAUNSELING & AKTIVITI",
      margin + 18f,
      margin + 32f,
      titlePaint,
    )
    canvas.drawText(
      "Unit Bimbingan dan Kaunseling • Dokumen Sulit & Terpelihara",
      margin + 18f,
      margin + 52f,
      subtitlePaint,
    )

    var y = margin + 92f

    // Metadata Box
    val formattedDate = dateFormatter.format(Date(record.sessionDateMillis))
    val metaRows =
      listOf(
        "Kategori" to record.category.displayName,
        "Nama Klien / Peserta" to record.clientName.ifBlank { "-" },
        "Kelas / Kumpulan" to record.className.ifBlank { "-" },
        "Hari & Tarikh" to "${record.dayName.ifBlank { "-" }}, $formattedDate",
        "Masa & Tempoh" to "${record.timeRange.ifBlank { "-" }} (${record.durationMinutes} minit)",
        "Kaunselor" to
          listOf(counselorName, counselorEmail).filter { it.isNotBlank() }.joinToString(" • "),
      )

    val boxHeight = metaRows.size * 22f + 16f
    fillPaint.color = surfaceCream
    canvas.drawRoundRect(
      RectF(margin, y, pageWidth - margin, y + boxHeight),
      8f,
      8f,
      fillPaint,
    )
    canvas.drawRoundRect(
      RectF(margin, y, pageWidth - margin, y + boxHeight),
      8f,
      8f,
      strokePaint,
    )

    var metaY = y + 20f
    for ((label, value) in metaRows) {
      canvas.drawText(label.uppercase(Locale.ROOT), margin + 14f, metaY, labelPaint)
      canvas.drawText(value.take(65), margin + 158f, metaY, bodyPaint)
      metaY += 22f
    }
    y += boxHeight + 22f

    fun drawTextBlock(heading: String, text: String, accentBarColor: Int) {
      val content = text.ifBlank { "Tiada maklumat direkodkan." }
      val layout =
        StaticLayout.Builder.obtain(
            content,
            0,
            content.length,
            bodyPaint,
            contentWidth - 20,
          )
          .setAlignment(Layout.Alignment.ALIGN_NORMAL)
          .setLineSpacing(2f, 1.15f)
          .build()

      if (y + 38f + layout.height > pageHeight - 60f) {
        y = newPage()
      }

      fillPaint.color = accentBarColor
      canvas.drawRect(margin, y - 10f, margin + 4f, y + 4f, fillPaint)
      canvas.drawText(heading, margin + 12f, y + 2f, sectionTitlePaint)
      y += 18f

      canvas.save()
      canvas.translate(margin + 12f, y)
      layout.draw(canvas)
      canvas.restore()
      y += layout.height + 20f
    }

    drawTextBlock(
      "TRANSKRIP PERBUALAN / CATATAN KAUNSELOR",
      record.notes,
      sagePrimary,
    )

    // Summary Section Header
    if (y + 40f > pageHeight - 60f) {
      y = newPage()
    }
    fillPaint.color = warmAccent
    canvas.drawRoundRect(
      RectF(margin, y, pageWidth - margin, y + 26f),
      6f,
      6f,
      fillPaint,
    )
    val summaryHeaderPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      }
    canvas.drawText(
      "KOTAK RUMUSAN & ANALISIS AI",
      margin + 12f,
      y + 17f,
      summaryHeaderPaint,
    )
    y += 42f

    drawTextBlock("1. PERKARA (ISU UTAMA)", record.summaryMatter, warmAccent)
    drawTextBlock("2. PERSOALAN", record.summaryQuestions, warmAccent)
    drawTextBlock("3. HURAIAN INTERVENSI", record.summaryIntervention, warmAccent)

    // Signature block
    if (y + 80f > pageHeight - 55f) {
      y = newPage()
    }
    y += 10f
    canvas.drawLine(margin, y + 36f, margin + 180f, y + 36f, strokePaint)
    canvas.drawText("Tandatangan Kaunselor", margin, y + 50f, labelPaint)
    canvas.drawText(
      "Tarikh Cetak: ${dateTimeFormatter.format(Date())}",
      pageWidth - margin - 165f,
      y + 50f,
      labelPaint,
    )

    drawFooter(canvas, pageNum)
    document.finishPage(page)

    val out = ByteArrayOutputStream()
    document.writeTo(out)
    document.close()
    return out.toByteArray()
  }

  private fun generateMinimalPdfFallback(
    record: CounselingRecord,
    counselorName: String,
    counselorEmail: String,
  ): ByteArray {
    val formattedDate = dateFormatter.format(Date(record.sessionDateMillis))
    val lines =
      listOf(
        "LAPORAN SESI KAUNSELING & AKTIVITI",
        "Kategori: ${record.category.displayName}",
        "Nama Klien: ${record.clientName.ifBlank { "-" }}",
        "Kelas: ${record.className.ifBlank { "-" }}",
        "Hari & Tarikh: ${record.dayName}, $formattedDate",
        "Masa: ${record.timeRange} (${record.durationMinutes} min)",
        "Kaunselor: $counselorName ($counselorEmail)",
        "Catatan: ${record.notes.take(120)}",
        "1. Perkara: ${record.summaryMatter.take(120)}",
        "2. Persoalan: ${record.summaryQuestions.take(120)}",
        "3. Huraian Intervensi: ${record.summaryIntervention.take(120)}",
      )
    fun pdfEsc(s: String) =
      s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replace("\n", " ")

    val streamBody =
      buildString {
        append("BT\n/F1 11 Tf\n50 780 Td\n16 TL\n")
        for (line in lines) {
          append("(${pdfEsc(line)}) Tj T*\n")
        }
        append("ET\n")
      }
    val streamBytes = streamBody.toByteArray(Charsets.ISO_8859_1)

    val out = ByteArrayOutputStream()
    val offsets = mutableListOf<Int>()
    fun writeStr(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))

    writeStr("%PDF-1.4\n")
    offsets.add(out.size())
    writeStr("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
    offsets.add(out.size())
    writeStr("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
    offsets.add(out.size())
    writeStr(
      "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n"
    )
    offsets.add(out.size())
    writeStr("4 0 obj\n<< /Length ${streamBytes.size} >>\nstream\n")
    out.write(streamBytes)
    writeStr("\nendstream\nendobj\n")
    offsets.add(out.size())
    writeStr("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n")

    val xrefPos = out.size()
    writeStr("xref\n0 6\n0000000000 65535 f \n")
    for (off in offsets) {
      writeStr(String.format(Locale.US, "%010d 00000 n \n", off))
    }
    writeStr("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
    return out.toByteArray()
  }

  fun generateDocxBytes(
    record: CounselingRecord,
    counselorName: String,
    counselorEmail: String,
  ): ByteArray {
    val formattedDate = dateFormatter.format(Date(record.sessionDateMillis))
    val generatedOn = dateTimeFormatter.format(Date())

    val contentTypesXml =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

    val rootRelsXml =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    val docRelsXml =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    val stylesXml =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:rPr>
      <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
      <w:sz w:val="22"/>
      <w:color w:val="1C2321"/>
    </w:rPr>
  </w:style>
</w:styles>"""

    fun esc(text: String): String =
      text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    fun paragraph(text: String, bold: Boolean = false, sizeHalfPt: Int = 22, colorHex: String = "1C2321", spaceAfter: Int = 120): String {
      val lines = text.ifBlank { "-" }.split("\n")
      return lines.joinToString("") { line ->
        """<w:p>
          <w:pPr><w:spacing w:after="$spaceAfter"/></w:pPr>
          <w:r>
            <w:rPr>
              ${if (bold) "<w:b/>" else ""}
              <w:sz w:val="$sizeHalfPt"/>
              <w:color w:val="$colorHex"/>
            </w:rPr>
            <w:t xml:space="preserve">${esc(line)}</w:t>
          </w:r>
        </w:p>"""
      }
    }

    fun tableRow(label: String, value: String): String =
      """<w:tr>
        <w:tc>
          <w:tcPr><w:tcW w:w="3000" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="F3EFE6"/></w:tcPr>
          ${paragraph(label, bold = true, sizeHalfPt = 20, colorHex = "1A6B5A", spaceAfter = 60)}
        </w:tc>
        <w:tc>
          <w:tcPr><w:tcW w:w="6200" w:type="dxa"/></w:tcPr>
          ${paragraph(value.ifBlank { "-" }, bold = false, sizeHalfPt = 20, spaceAfter = 60)}
        </w:tc>
      </w:tr>"""

    val documentXml =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    ${paragraph("LAPORAN SESI KAUNSELING & AKTIVITI", bold = true, sizeHalfPt = 32, colorHex = "1A6B5A", spaceAfter = 60)}
    ${paragraph("Unit Bimbingan dan Kaunseling • Rekod Sulit & Terpelihara (E2EE)", bold = false, sizeHalfPt = 18, colorHex = "5C6662", spaceAfter = 220)}
    <w:tbl>
      <w:tblPr>
        <w:tblW w:w="9200" w:type="dxa"/>
        <w:tblBorders>
          <w:top w:val="single" w:sz="4" w:space="0" w:color="D6D0C4"/>
          <w:left w:val="single" w:sz="4" w:space="0" w:color="D6D0C4"/>
          <w:bottom w:val="single" w:sz="4" w:space="0" w:color="D6D0C4"/>
          <w:right w:val="single" w:sz="4" w:space="0" w:color="D6D0C4"/>
          <w:insideH w:val="single" w:sz="4" w:space="0" w:color="D6D0C4"/>
          <w:insideV w:val="single" w:sz="4" w:space="0" w:color="D6D0C4"/>
        </w:tblBorders>
      </w:tblPr>
      ${tableRow("Kategori", record.category.displayName)}
      ${tableRow("Nama Klien / Peserta", record.clientName)}
      ${tableRow("Kelas / Kumpulan", record.className)}
      ${tableRow("Hari & Tarikh", "${record.dayName}, $formattedDate")}
      ${tableRow("Masa & Tempoh", "${record.timeRange} (${record.durationMinutes} minit)")}
      ${tableRow("Kaunselor", listOf(counselorName, counselorEmail).filter { it.isNotBlank() }.joinToString(" • "))}
    </w:tbl>
    ${paragraph("", spaceAfter = 120)}
    ${paragraph("TRANSKRIP PERBUALAN / CATATAN KAUNSELOR", bold = true, sizeHalfPt = 24, colorHex = "1A6B5A", spaceAfter = 80)}
    ${paragraph(record.notes.ifBlank { "Tiada catatan direkodkan." }, spaceAfter = 180)}
    ${paragraph("KOTAK RUMUSAN & ANALISIS AI", bold = true, sizeHalfPt = 26, colorHex = "C26D38", spaceAfter = 100)}
    ${paragraph("1. Perkara (Isu Utama)", bold = true, sizeHalfPt = 22, colorHex = "1A6B5A", spaceAfter = 60)}
    ${paragraph(record.summaryMatter.ifBlank { "Belum dijana." }, spaceAfter = 140)}
    ${paragraph("2. Persoalan", bold = true, sizeHalfPt = 22, colorHex = "1A6B5A", spaceAfter = 60)}
    ${paragraph(record.summaryQuestions.ifBlank { "Belum dijana." }, spaceAfter = 140)}
    ${paragraph("3. Huraian Intervensi", bold = true, sizeHalfPt = 22, colorHex = "1A6B5A", spaceAfter = 60)}
    ${paragraph(record.summaryIntervention.ifBlank { "Belum dijana." }, spaceAfter = 240)}
    ${paragraph("________________________________________", spaceAfter = 40)}
    ${paragraph("Tandatangan Kaunselor • Dijana pada $generatedOn", sizeHalfPt = 18, colorHex = "5C6662", spaceAfter = 80)}
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/>
    </w:sectPr>
  </w:body>
</w:document>"""

    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
      fun putEntry(name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
      }
      putEntry("[Content_Types].xml", contentTypesXml)
      putEntry("_rels/.rels", rootRelsXml)
      putEntry("word/_rels/document.xml.rels", docRelsXml)
      putEntry("word/styles.xml", stylesXml)
      putEntry("word/document.xml", documentXml)
    }
    return out.toByteArray()
  }

  fun printPdfDirect(
    context: Context,
    record: CounselingRecord,
    counselorName: String,
    counselorEmail: String,
  ) {
    val pdfBytes = generatePdfBytes(record, counselorName, counselorEmail)
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val jobName = buildSuggestedFileName(record, "pdf")
    val adapter =
      object : PrintDocumentAdapter() {
        override fun onLayout(
          oldAttributes: PrintAttributes?,
          newAttributes: PrintAttributes,
          cancellationSignal: CancellationSignal?,
          callback: LayoutResultCallback,
          extras: Bundle?,
        ) {
          if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
          }
          val info =
            PrintDocumentInfo.Builder(jobName)
              .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
              .build()
          callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
          pages: Array<out PageRange>?,
          destination: ParcelFileDescriptor,
          cancellationSignal: CancellationSignal?,
          callback: WriteResultCallback,
        ) {
          try {
            FileOutputStream(destination.fileDescriptor).use { it.write(pdfBytes) }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
          } catch (e: Exception) {
            callback.onWriteFailed(e.message)
          }
        }
      }
    printManager.print(jobName, adapter, PrintAttributes.Builder().build())
  }

  fun shareReportFile(
    context: Context,
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
  ) {
    val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
    val file = File(reportsDir, fileName)
    file.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    context.startActivity(Intent.createChooser(intent, "Kongsi / Cetak Laporan"))
  }
}
