import { jsPDF } from 'jspdf';
import {
  CATEGORY_MAP,
  formatMalayDate,
  formatMalayDateTime,
  type ActivityCategory,
  type CounselingRecord,
} from '../models/counseling';

export function buildSuggestedFileName(
  record: CounselingRecord,
  extension: string
): string {
  const base =
    record.clientName.trim() ||
    record.className.trim() ||
    CATEGORY_MAP[record.category].shortLabel;
  const safeName = base.replace(/[^a-zA-Z0-9_-]/g, '_').slice(0, 28) || 'Rekod';
  const d = new Date(record.sessionDateMillis);
  const tag = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(
    d.getDate()
  ).padStart(2, '0')}`;
  return `Laporan_${safeName}_${tag}.${extension}`;
}

export function buildExportMetaRows(
  record: CounselingRecord,
  formattedDate: string,
  counselorName: string,
  counselorEmail: string
): Array<[string, string]> {
  const counselorDisplay = [counselorName, counselorEmail]
    .filter((v) => v.trim())
    .join(' • ');
  const dateRow: [string, string] = [
    'Hari & Tarikh',
    `${record.dayName.trim() || '-'}, ${formattedDate}`,
  ];
  const timeRow: [string, string] = [
    'Masa & Tempoh',
    `${record.timeRange.trim() || '-'} (${record.durationMinutes} minit)`,
  ];
  const counselorRow: [string, string] = ['Kaunselor', counselorDisplay || '-'];
  const catName = CATEGORY_MAP[record.category].displayName;

  switch (record.category) {
    case 'PROGRAM':
      return [
        ['Kategori', catName],
        ['Nama Program', record.clientName.trim() || '-'],
        ['Anjuran', record.organizer.trim() || '-'],
        ['Sasaran', record.className.trim() || '-'],
        dateRow,
        timeRow,
        counselorRow,
      ];
    case 'KELAS_GANTI':
      return [
        ['Kategori', catName],
        ['Nama Kelas / Tingkatan', record.className.trim() || '-'],
        dateRow,
        timeRow,
        counselorRow,
      ];
    case 'MESYUARAT':
      return [
        ['Kategori', catName],
        ['Nama Mesyuarat', record.clientName.trim() || '-'],
        dateRow,
        timeRow,
        counselorRow,
      ];
    case 'ZIARAH_CAKNA':
    case 'KONSULTASI_IBUBAPA':
      return [
        ['Kategori', catName],
        ['Nama Murid', record.clientName.trim() || '-'],
        ['Kelas Murid', record.className.trim() || '-'],
        ['Nama Penjaga', record.guardianName.trim() || '-'],
        ['No. HP Penjaga', record.guardianPhone.trim() || '-'],
        dateRow,
        timeRow,
        counselorRow,
      ];
    default:
      return [
        ['Kategori', catName],
        ['Nama Klien / Peserta', record.clientName.trim() || '-'],
        ['Kelas / Kumpulan', record.className.trim() || '-'],
        dateRow,
        timeRow,
        counselorRow,
      ];
  }
}

export function notesHeadingFor(category: ActivityCategory): string {
  switch (category) {
    case 'KELAS_GANTI':
      return 'RINGKASAN AKTIVITI KELAS GANTI';
    case 'MESYUARAT':
      return 'CATATAN / MINIT RINGKAS MESYUARAT';
    case 'ZIARAH_CAKNA':
    case 'KONSULTASI_IBUBAPA':
      return 'CATATAN PERBINCANGAN BERSAMA PENJAGA';
    default:
      return 'TRANSKRIP PERBUALAN / CATATAN KAUNSELOR';
  }
}

export function summarySectionTitleFor(category: ActivityCategory): string {
  switch (category) {
    case 'PROGRAM':
      return 'KOTAK RUMUSAN PROGRAM & ANALISIS AI';
    case 'ZIARAH_CAKNA':
    case 'KONSULTASI_IBUBAPA':
      return 'KOTAK RUMUSAN PERBINCANGAN & ANALISIS AI';
    default:
      return 'KOTAK RUMUSAN & ANALISIS AI';
  }
}

export function buildExportSummaryBlocks(
  record: CounselingRecord
): Array<[string, string]> {
  switch (record.category) {
    case 'KELAS_GANTI':
      return [];
    case 'PROGRAM':
      return [
        ['1. RUMUSAN PROGRAM', record.summaryMatter],
        ['2. OBJEKTIF', record.summaryQuestions],
        ['3. KELEBIHAN & KELEMAHAN', record.programStrengthsWeaknesses],
        ['4. HURAIAN INTERVENSI', record.summaryIntervention],
      ];
    case 'ZIARAH_CAKNA':
    case 'KONSULTASI_IBUBAPA':
      return [
        ['1. RUMUSAN PERBINCANGAN', record.summaryMatter],
        ['2. ISU / PERSOALAN DIKEMUKAKAN', record.summaryQuestions],
        ['3. TINDAKAN / SUSULAN', record.summaryIntervention],
      ];
    case 'MESYUARAT':
      return [
        ['1. RUMUSAN AGENDA / PERKARA', record.summaryMatter],
        ['2. PERSOALAN / ISU BERBANGKIT', record.summaryQuestions],
        ['3. KEPUTUSAN & TINDAKAN SUSULAN', record.summaryIntervention],
      ];
    default:
      return [
        ['1. PERKARA (ISU UTAMA)', record.summaryMatter],
        ['2. PERSOALAN', record.summaryQuestions],
        ['3. HURAIAN INTERVENSI', record.summaryIntervention],
      ];
  }
}

// --- PDF Export (A4 layout matching Android ReportExporter.kt) ---

export function generatePdfBlob(
  record: CounselingRecord,
  counselorName: string,
  counselorEmail: string
): Blob {
  const doc = new jsPDF({ unit: 'pt', format: 'a4', orientation: 'portrait' });
  const pageWidth = doc.internal.pageSize.getWidth();
  const pageHeight = doc.internal.pageSize.getHeight();
  const margin = 42;
  const contentWidth = pageWidth - margin * 2;
  let pageNum = 1;

  const drawFooter = () => {
    const lineY = pageHeight - 36;
    doc.setDrawColor(214, 208, 196);
    doc.setLineWidth(0.8);
    doc.line(margin, lineY - 8, pageWidth - margin, lineY - 8);
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(8.5);
    doc.setTextColor(92, 102, 98);
    doc.text(
      `Dijana secara automatik oleh Diari Kaunselor • Sulit (E2EE ${record.encryptionVersion})`,
      margin,
      lineY + 6
    );
    doc.text(`Halaman ${pageNum}`, pageWidth - margin - 48, lineY + 6);
  };

  const nextPage = (): number => {
    drawFooter();
    doc.addPage();
    pageNum += 1;
    return margin;
  };

  // Header Banner
  doc.setFillColor(26, 107, 90);
  doc.roundedRect(margin, margin, contentWidth, 72, 10, 10, 'F');
  doc.setFont('times', 'bold');
  doc.setFontSize(16);
  doc.setTextColor(255, 255, 255);
  doc.text('LAPORAN SESI KAUNSELING & AKTIVITI', margin + 18, margin + 32);
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(9.5);
  doc.setTextColor(220, 240, 234);
  doc.text(
    'Unit Bimbingan dan Kaunseling • Dokumen Sulit & Terpelihara',
    margin + 18,
    margin + 52
  );

  let y = margin + 90;

  // Metadata Box
  const formattedDate = formatMalayDate(record.sessionDateMillis);
  const metaRows = buildExportMetaRows(
    record,
    formattedDate,
    counselorName,
    counselorEmail
  );
  const boxHeight = metaRows.length * 22 + 16;
  doc.setFillColor(245, 242, 235);
  doc.setDrawColor(214, 208, 196);
  doc.roundedRect(margin, y, contentWidth, boxHeight, 8, 8, 'FD');

  let metaY = y + 20;
  for (const [label, value] of metaRows) {
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(9);
    doc.setTextColor(92, 102, 98);
    doc.text(label.toUpperCase(), margin + 14, metaY);

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(10);
    doc.setTextColor(28, 35, 33);
    const trimmedVal = doc.splitTextToSize(value, contentWidth - 176)[0] ?? '-';
    doc.text(trimmedVal, margin + 158, metaY);
    metaY += 22;
  }

  y += boxHeight + 24;

  const drawTextBlock = (
    heading: string,
    text: string,
    accentRgb: [number, number, number]
  ) => {
    const content = text.trim() || 'Tiada maklumat direkodkan.';
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(10.5);
    const lines = doc.splitTextToSize(content, contentWidth - 18) as string[];
    const lineHeight = 14.5;
    const blockHeight = 24 + lines.length * lineHeight;

    if (y + Math.min(blockHeight, 120) > pageHeight - 62) {
      y = nextPage();
    }

    doc.setFillColor(accentRgb[0], accentRgb[1], accentRgb[2]);
    doc.rect(margin, y - 10, 4, 14, 'F');
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(11.5);
    doc.setTextColor(26, 107, 90);
    doc.text(heading, margin + 12, y + 1);
    y += 18;

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(10.5);
    doc.setTextColor(28, 35, 33);
    for (const line of lines) {
      if (y > pageHeight - 58) {
        y = nextPage();
        doc.setFont('helvetica', 'normal');
        doc.setFontSize(10.5);
        doc.setTextColor(28, 35, 33);
      }
      doc.text(line, margin + 12, y);
      y += lineHeight;
    }
    y += 14;
  };

  drawTextBlock(notesHeadingFor(record.category), record.notes, [26, 107, 90]);

  const summaryBlocks = buildExportSummaryBlocks(record);
  if (summaryBlocks.length > 0) {
    if (y + 54 > pageHeight - 60) {
      y = nextPage();
    }
    doc.setFillColor(194, 109, 56);
    doc.roundedRect(margin, y, contentWidth, 26, 6, 6, 'F');
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(10.5);
    doc.setTextColor(255, 255, 255);
    doc.text(summarySectionTitleFor(record.category), margin + 12, y + 17);
    y += 42;

    for (const [heading, content] of summaryBlocks) {
      drawTextBlock(heading, content, [194, 109, 56]);
    }
  }

  // Signature Block
  if (y + 76 > pageHeight - 55) {
    y = nextPage();
  }
  y += 10;
  doc.setDrawColor(160, 168, 164);
  doc.setLineWidth(0.9);
  doc.line(margin, y + 34, margin + 185, y + 34);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(9.5);
  doc.setTextColor(92, 102, 98);
  doc.text('Tandatangan Kaunselor', margin, y + 48);
  doc.text(
    `Tarikh Cetak: ${formatMalayDateTime(Date.now())}`,
    pageWidth - margin - 165,
    y + 48
  );

  drawFooter();
  return doc.output('blob');
}

// --- Pure Browser OOXML Word (.docx) Generator (Valid ZIP, zero backend) ---

const CRC32_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[i] = c >>> 0;
  }
  return table;
})();

function crc32(bytes: Uint8Array): number {
  let crc = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) {
    crc = CRC32_TABLE[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function buildZipBytes(entries: Array<{ name: string; content: string }>): Uint8Array {
  const encoder = new TextEncoder();
  const localParts: Uint8Array[] = [];
  const centralParts: Uint8Array[] = [];
  let offset = 0;

  for (const entry of entries) {
    const nameBytes = encoder.encode(entry.name);
    const dataBytes = encoder.encode(entry.content);
    const checksum = crc32(dataBytes);

    // Local File Header (30 bytes + name)
    const local = new Uint8Array(30 + nameBytes.length + dataBytes.length);
    const lv = new DataView(local.buffer);
    lv.setUint32(0, 0x04034b50, true); // local signature
    lv.setUint16(4, 20, true); // version needed
    lv.setUint16(6, 0x0800, true); // UTF-8 flag
    lv.setUint16(8, 0, true); // store (no compression)
    lv.setUint16(10, 0, true);
    lv.setUint16(12, 0, true);
    lv.setUint32(14, checksum, true);
    lv.setUint32(18, dataBytes.length, true);
    lv.setUint32(22, dataBytes.length, true);
    lv.setUint16(26, nameBytes.length, true);
    lv.setUint16(28, 0, true);
    local.set(nameBytes, 30);
    local.set(dataBytes, 30 + nameBytes.length);
    localParts.push(local);

    // Central Directory Header (46 bytes + name)
    const central = new Uint8Array(46 + nameBytes.length);
    const cv = new DataView(central.buffer);
    cv.setUint32(0, 0x02014b50, true);
    cv.setUint16(4, 20, true);
    cv.setUint16(6, 20, true);
    cv.setUint16(8, 0x0800, true);
    cv.setUint16(10, 0, true);
    cv.setUint16(12, 0, true);
    cv.setUint16(14, 0, true);
    cv.setUint32(16, checksum, true);
    cv.setUint32(20, dataBytes.length, true);
    cv.setUint32(24, dataBytes.length, true);
    cv.setUint16(28, nameBytes.length, true);
    cv.setUint16(30, 0, true);
    cv.setUint16(32, 0, true);
    cv.setUint16(34, 0, true);
    cv.setUint16(36, 0, true);
    cv.setUint32(38, 0, true);
    cv.setUint32(42, offset, true);
    central.set(nameBytes, 46);
    centralParts.push(central);

    offset += local.length;
  }

  const centralSize = centralParts.reduce((sum, p) => sum + p.length, 0);
  const eocd = new Uint8Array(22);
  const ev = new DataView(eocd.buffer);
  ev.setUint32(0, 0x06054b50, true);
  ev.setUint16(4, 0, true);
  ev.setUint16(6, 0, true);
  ev.setUint16(8, entries.length, true);
  ev.setUint16(10, entries.length, true);
  ev.setUint32(12, centralSize, true);
  ev.setUint32(16, offset, true);
  ev.setUint16(20, 0, true);

  const totalLen = offset + centralSize + eocd.length;
  const output = new Uint8Array(totalLen);
  let cursor = 0;
  for (const part of localParts) {
    output.set(part, cursor);
    cursor += part.length;
  }
  for (const part of centralParts) {
    output.set(part, cursor);
    cursor += part.length;
  }
  output.set(eocd, cursor);
  return output;
}

function escapeXml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function docxParagraph(
  text: string,
  bold = false,
  sizeHalfPt = 22,
  colorHex = '1C2321',
  spaceAfter = 120
): string {
  const lines = (text.trim() ? text : '-').split('\n');
  return lines
    .map(
      (line) =>
        `<w:p><w:pPr><w:spacing w:after="${spaceAfter}"/></w:pPr><w:r><w:rPr>${
          bold ? '<w:b/>' : ''
        }<w:sz w:val="${sizeHalfPt}"/><w:color w:val="${colorHex}"/></w:rPr><w:t xml:space="preserve">${escapeXml(
          line
        )}</w:t></w:r></w:p>`
    )
    .join('');
}

export function generateDocxBytes(
  record: CounselingRecord,
  counselorName: string,
  counselorEmail: string
): Uint8Array {
  const formattedDate = formatMalayDate(record.sessionDateMillis);
  const generatedOn = formatMalayDateTime(Date.now());

  const contentTypesXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>`;

  const rootRelsXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>`;

  const docRelsXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>`;

  const stylesXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:rPr>
      <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
      <w:sz w:val="22"/>
      <w:color w:val="1C2321"/>
    </w:rPr>
  </w:style>
</w:styles>`;

  const tableRowsXml = buildExportMetaRows(
    record,
    formattedDate,
    counselorName,
    counselorEmail
  )
    .map(
      ([label, value]) => `<w:tr>
        <w:tc>
          <w:tcPr><w:tcW w:w="3000" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="F3EFE6"/></w:tcPr>
          ${docxParagraph(label, true, 20, '1A6B5A', 60)}
        </w:tc>
        <w:tc>
          <w:tcPr><w:tcW w:w="6200" w:type="dxa"/></w:tcPr>
          ${docxParagraph(value.trim() || '-', false, 20, '1C2321', 60)}
        </w:tc>
      </w:tr>`
    )
    .join('\n');

  const summaryBlocks = buildExportSummaryBlocks(record);
  const summaryXml =
    summaryBlocks.length === 0
      ? ''
      : docxParagraph(
          summarySectionTitleFor(record.category),
          true,
          26,
          'C26D38',
          100
        ) +
        summaryBlocks
          .map(
            ([heading, value]) =>
              docxParagraph(heading, true, 22, '1A6B5A', 60) +
              docxParagraph(value.trim() || 'Tiada maklumat direkodkan.', false, 22, '1C2321', 140)
          )
          .join('');

  const documentXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    ${docxParagraph('LAPORAN SESI KAUNSELING & AKTIVITI', true, 32, '1A6B5A', 60)}
    ${docxParagraph('Unit Bimbingan dan Kaunseling • Rekod Sulit & Terpelihara (E2EE)', false, 18, '5C6662', 220)}
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
      ${tableRowsXml}
    </w:tbl>
    ${docxParagraph('', false, 22, '1C2321', 120)}
    ${docxParagraph(notesHeadingFor(record.category), true, 24, '1A6B5A', 80)}
    ${docxParagraph(record.notes.trim() || 'Tiada catatan direkodkan.', false, 22, '1C2321', 180)}
    ${summaryXml}
    ${docxParagraph('________________________________________', false, 22, '1C2321', 40)}
    ${docxParagraph(`Tandatangan Kaunselor • Dijana pada ${generatedOn}`, false, 18, '5C6662', 80)}
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/>
    </w:sectPr>
  </w:body>
</w:document>`;

  return buildZipBytes([
    { name: '[Content_Types].xml', content: contentTypesXml },
    { name: '_rels/.rels', content: rootRelsXml },
    { name: 'word/_rels/document.xml.rels', content: docRelsXml },
    { name: 'word/styles.xml', content: stylesXml },
    { name: 'word/document.xml', content: documentXml },
  ]);
}

export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
}

export function exportRecordAsPdf(
  record: CounselingRecord,
  counselorName: string,
  counselorEmail: string
): string {
  const blob = generatePdfBlob(record, counselorName, counselorEmail);
  const fileName = buildSuggestedFileName(record, 'pdf');
  downloadBlob(blob, fileName);
  return fileName;
}

export function exportRecordAsDocx(
  record: CounselingRecord,
  counselorName: string,
  counselorEmail: string
): string {
  const bytes = generateDocxBytes(record, counselorName, counselorEmail);
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  const blob = new Blob([copy.buffer], {
    type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  });
  const fileName = buildSuggestedFileName(record, 'docx');
  downloadBlob(blob, fileName);
  return fileName;
}

export function printRecordReport(
  record: CounselingRecord,
  counselorName: string,
  counselorEmail: string
): void {
  const blob = generatePdfBlob(record, counselorName, counselorEmail);
  const url = URL.createObjectURL(blob);
  const win = window.open(url, '_blank');
  if (!win) {
    downloadBlob(blob, buildSuggestedFileName(record, 'pdf'));
    return;
  }
  win.addEventListener('load', () => {
    setTimeout(() => win.print(), 300);
  });
}
