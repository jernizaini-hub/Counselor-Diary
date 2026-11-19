import { useEffect, useState } from 'react';
import {
  AlertTriangle,
  Clock,
  FileDown,
  FileSpreadsheet,
  Loader2,
  Printer,
  Save,
  ShieldAlert,
  Sparkles,
  Trash2,
} from 'lucide-react';
import {
  ACTIVITY_CATEGORIES,
  DURATION_PRESETS,
  calculateDurationMinutes,
  computeEndTime,
  formatMalayDate,
  formatTime24,
  fromDateInputValue,
  getMalayDayName,
  parseTimeRange,
  toDateInputValue,
  type ActivityCategory,
  type AiSummaryResult,
  type CounselingRecord,
} from '../models/counseling';
import {
  exportRecordAsDocx,
  exportRecordAsPdf,
  printRecordReport,
} from '../export/reportExporter';

interface RecordEditorPaneProps {
  draft: CounselingRecord;
  isDirty: boolean;
  saving: boolean;
  deleting: boolean;
  aiLoading: boolean;
  aiError: { message: string; requiresApiKeySetup: boolean } | null;
  counselorName: string;
  counselorEmail: string;
  onChangeDraft: (updater: (prev: CounselingRecord) => CounselingRecord) => void;
  onSave: () => void;
  onDelete: () => void;
  onGenerateAi: () => Promise<AiSummaryResult | null>;
  onOpenSecurityTab: () => void;
  onNotify: (msg: string) => void;
}

export function RecordEditorPane({
  draft,
  isDirty,
  saving,
  deleting,
  aiLoading,
  aiError,
  counselorName,
  counselorEmail,
  onChangeDraft,
  onSave,
  onDelete,
  onGenerateAi,
  onOpenSecurityTab,
  onNotify,
}: RecordEditorPaneProps) {
  const [confirmDelete, setConfirmDelete] = useState(false);

  const parsed = parseTimeRange(draft.timeRange, draft.durationMinutes);
  const [startTime, setStartTime] = useState(parsed.startTime);
  const [endTime, setEndTime] = useState(parsed.endTime);

  useEffect(() => {
    const p = parseTimeRange(draft.timeRange, draft.durationMinutes);
    setStartTime(p.startTime);
    setEndTime(p.endTime);
  }, [draft.id, draft.timeRange]);

  const computedDuration = calculateDurationMinutes(startTime, endTime);
  const isInvalidTimeOrder = computedDuration <= 0;

  const updateSchedule = (nextStart: string, nextEnd: string) => {
    setStartTime(nextStart);
    setEndTime(nextEnd);
    const mins = calculateDurationMinutes(nextStart, nextEnd);
    onChangeDraft((prev) => ({
      ...prev,
      timeRange: `${nextStart} - ${nextEnd}`,
      durationMinutes: mins > 0 ? mins : 0,
    }));
  };

  const applyPresetMinutes = (minutes: number) => {
    const nextEnd = computeEndTime(startTime, minutes);
    updateSchedule(startTime, nextEnd);
  };

  const applyNowTime = () => {
    const now = new Date();
    const s = formatTime24(now.getHours(), now.getMinutes());
    const dur = computedDuration > 0 ? computedDuration : 45;
    const e = computeEndTime(s, dur);
    const nowMillis = now.getTime();
    setStartTime(s);
    setEndTime(e);
    onChangeDraft((prev) => ({
      ...prev,
      sessionDateMillis: nowMillis,
      dayName: getMalayDayName(nowMillis),
      timeRange: `${s} - ${e}`,
      durationMinutes: Math.max(1, calculateDurationMinutes(s, e)),
    }));
  };

  const handleCategoryChange = (category: ActivityCategory) => {
    onChangeDraft((prev) => ({
      ...prev,
      category,
      hasAiSummary: category === 'KELAS_GANTI' ? false : prev.hasAiSummary,
    }));
  };

  const handleExportPdf = () => {
    const name = exportRecordAsPdf(draft, counselorName, counselorEmail);
    onNotify(`Laporan PDF dimuat turun: ${name}`);
  };

  const handleExportWord = () => {
    const name = exportRecordAsDocx(draft, counselorName, counselorEmail);
    onNotify(`Laporan Word (.docx) dimuat turun: ${name}`);
  };

  const handlePrint = () => {
    printRecordReport(draft, counselorName, counselorEmail);
  };

  const isProgram = draft.category === 'PROGRAM';
  const isKelasGanti = draft.category === 'KELAS_GANTI';
  const isMesyuarat = draft.category === 'MESYUARAT';
  const isGuardianCategory =
    draft.category === 'ZIARAH_CAKNA' || draft.category === 'KONSULTASI_IBUBAPA';

  const infoCardTitle = isProgram
    ? 'Maklumat Program'
    : isKelasGanti
    ? 'Maklumat Kelas Ganti & Aktiviti'
    : isMesyuarat
    ? 'Maklumat Mesyuarat'
    : isGuardianCategory
    ? 'Maklumat Murid & Penjaga'
    : 'Maklumat Sesi & Klien';

  const notesCardTitle = isMesyuarat
    ? 'Catatan / Minit Ringkas Mesyuarat'
    : isGuardianCategory
    ? 'Catatan Perbincangan Bersama Penjaga'
    : 'Transkrip / Catatan Ringkas';

  const summaryCardTitle = isProgram
    ? 'Kotak Rumusan Program & Analisis AI'
    : isGuardianCategory
    ? 'Kotak Rumusan Perbincangan & Analisis AI'
    : 'Kotak Rumusan & Analisis AI';

  const summaryLabels = isProgram
    ? {
        matter: '1. Rumusan Program',
        questions: '2. Objektif',
        strengths: '3. Kelebihan & Kelemahan',
        intervention: '4. Huraian Intervensi',
      }
    : isGuardianCategory
    ? {
        matter: '1. Rumusan Perbincangan',
        questions: '2. Isu / Persoalan Dikemukakan',
        strengths: '',
        intervention: '3. Tindakan / Susulan',
      }
    : isMesyuarat
    ? {
        matter: '1. Rumusan Agenda / Perkara',
        questions: '2. Persoalan / Isu Berbangkit',
        strengths: '',
        intervention: '3. Keputusan & Tindakan Susulan',
      }
    : {
        matter: '1. Perkara (Isu / Tema Utama)',
        questions: '2. Persoalan',
        strengths: '',
        intervention: '3. Huraian Intervensi',
      };

  const wordCount = draft.notes.trim()
    ? draft.notes.trim().split(/\s+/).length
    : 0;

  return (
    <div className="detail-pane">
      <div className="editor-toolbar">
        <div className="editor-toolbar-left">
          <h2 className="editor-toolbar-title">
            {draft.id ? 'Sunting Rekod' : 'Catatan Baharu'}
          </h2>
          {isDirty && <span className="unsaved-badge">Belum disimpan</span>}
        </div>

        <div className="editor-toolbar-actions">
          <button
            type="button"
            className="btn btn-outline"
            onClick={handleExportPdf}
            title="Muat turun laporan PDF rasmi"
          >
            <FileDown size={15} />
            <span>PDF</span>
          </button>

          <button
            type="button"
            className="btn btn-outline"
            onClick={handleExportWord}
            title="Muat turun dokumen Microsoft Word (.docx)"
          >
            <FileSpreadsheet size={15} />
            <span>Word (.docx)</span>
          </button>

          <button
            type="button"
            className="btn btn-outline"
            onClick={handlePrint}
            title="Cetak laporan"
          >
            <Printer size={15} />
            <span>Cetak</span>
          </button>

          {draft.id && (
            <button
              type="button"
              className="btn btn-danger-outline"
              onClick={() => setConfirmDelete(true)}
              disabled={deleting}
              title="Padam rekod ini"
            >
              <Trash2 size={15} />
              <span>Padam</span>
            </button>
          )}

          <button
            type="button"
            className="btn btn-primary"
            onClick={onSave}
            disabled={saving || isInvalidTimeOrder}
            title="Simpan ke Awan E2EE (Ctrl+S)"
          >
            {saving ? (
              <>
                <Loader2 size={15} className="spin" />
                <span>Menyimpan...</span>
              </>
            ) : (
              <>
                <Save size={15} />
                <span>Simpan</span>
              </>
            )}
          </button>
        </div>
      </div>

      <div className="editor-scroll">
        <div className="editor-stack">
          {/* Card 1: Category Selector */}
          <section className="editor-card">
            <div className="step-header">
              <div className="step-num">1</div>
              <div>
                <h3 className="step-title">Kategori Sesi / Aktiviti</h3>
                <p className="step-sub">
                  Medan borang dan struktur laporan berubah secara automatik mengikut kategori
                </p>
              </div>
            </div>
            <div className="category-grid">
              {ACTIVITY_CATEGORIES.map((cat) => (
                <button
                  key={cat.key}
                  type="button"
                  className={`cat-select-btn ${draft.category === cat.key ? 'active' : ''}`}
                  onClick={() => handleCategoryChange(cat.key)}
                >
                  <span
                    className="cat-dot"
                    style={{
                      background:
                        draft.category === cat.key ? 'currentColor' : cat.accentHex,
                    }}
                  />
                  <span>{cat.displayName}</span>
                </button>
              ))}
            </div>
          </section>

          {/* Card 2: Dynamic Metadata + Date & Auto Time/Duration */}
          <section className="editor-card">
            <div className="step-header">
              <div className="step-num">2</div>
              <div>
                <h3 className="step-title">{infoCardTitle}</h3>
                <p className="step-sub">
                  Tarikh, hari, masa dan tempoh dikira secara automatik &amp; disulitkan E2EE
                </p>
              </div>
            </div>

            {isProgram && (
              <>
                <div className="field-group">
                  <label className="field-label" htmlFor="field-program-name">
                    Nama Program *
                  </label>
                  <input
                    id="field-program-name"
                    className="input"
                    placeholder="cth. Bengkel Persediaan Kerjaya & Motivasi SPM"
                    value={draft.clientName}
                    onChange={(e) =>
                      onChangeDraft((p) => ({ ...p, clientName: e.target.value }))
                    }
                  />
                </div>
                <div className="form-grid-2">
                  <div className="field-group">
                    <label className="field-label" htmlFor="field-organizer">
                      Anjuran
                    </label>
                    <input
                      id="field-organizer"
                      className="input"
                      placeholder="cth. Unit Bimbingan & Kaunseling"
                      value={draft.organizer}
                      onChange={(e) =>
                        onChangeDraft((p) => ({ ...p, organizer: e.target.value }))
                      }
                    />
                  </div>
                  <div className="field-group">
                    <label className="field-label" htmlFor="field-target">
                      Sasaran
                    </label>
                    <input
                      id="field-target"
                      className="input"
                      placeholder="cth. Semua Murid Tingkatan 5"
                      value={draft.className}
                      onChange={(e) =>
                        onChangeDraft((p) => ({ ...p, className: e.target.value }))
                      }
                    />
                  </div>
                </div>
              </>
            )}

            {isKelasGanti && (
              <div className="field-group">
                <label className="field-label" htmlFor="field-kelas-ganti">
                  Nama Kelas / Tingkatan *
                </label>
                <input
                  id="field-kelas-ganti"
                  className="input"
                  placeholder="cth. 3 Amanah / 5 Sains 1"
                  value={draft.className}
                  onChange={(e) =>
                    onChangeDraft((p) => ({ ...p, className: e.target.value }))
                  }
                />
              </div>
            )}

            {isMesyuarat && (
              <div className="field-group">
                <label className="field-label" htmlFor="field-meeting-name">
                  Nama Mesyuarat *
                </label>
                <input
                  id="field-meeting-name"
                  className="input"
                  placeholder="cth. Mesyuarat Jawatankuasa HEM Bil. 2/2026"
                  value={draft.clientName}
                  onChange={(e) =>
                    onChangeDraft((p) => ({ ...p, clientName: e.target.value }))
                  }
                />
              </div>
            )}

            {isGuardianCategory && (
              <>
                <div className="form-grid-2">
                  <div className="field-group">
                    <label className="field-label" htmlFor="field-student-name">
                      Nama Murid *
                    </label>
                    <input
                      id="field-student-name"
                      className="input"
                      placeholder="Nama penuh murid"
                      value={draft.clientName}
                      onChange={(e) =>
                        onChangeDraft((p) => ({ ...p, clientName: e.target.value }))
                      }
                    />
                  </div>
                  <div className="field-group">
                    <label className="field-label" htmlFor="field-student-class">
                      Kelas Murid
                    </label>
                    <input
                      id="field-student-class"
                      className="input"
                      placeholder="cth. 4 Bestari"
                      value={draft.className}
                      onChange={(e) =>
                        onChangeDraft((p) => ({ ...p, className: e.target.value }))
                      }
                    />
                  </div>
                </div>
                <div className="form-grid-2">
                  <div className="field-group">
                    <label className="field-label" htmlFor="field-guardian-name">
                      Nama Penjaga
                    </label>
                    <input
                      id="field-guardian-name"
                      className="input"
                      placeholder="Nama ibu / bapa / penjaga"
                      value={draft.guardianName}
                      onChange={(e) =>
                        onChangeDraft((p) => ({ ...p, guardianName: e.target.value }))
                      }
                    />
                  </div>
                  <div className="field-group">
                    <label className="field-label" htmlFor="field-guardian-phone">
                      No. HP Penjaga
                    </label>
                    <input
                      id="field-guardian-phone"
                      className="input"
                      placeholder="cth. 012-3456789"
                      value={draft.guardianPhone}
                      onChange={(e) =>
                        onChangeDraft((p) => ({ ...p, guardianPhone: e.target.value }))
                      }
                    />
                  </div>
                </div>
              </>
            )}

            {!isProgram && !isKelasGanti && !isMesyuarat && !isGuardianCategory && (
              <div className="form-grid-2">
                <div className="field-group">
                  <label className="field-label" htmlFor="field-client-name">
                    Nama Klien / Peserta *
                  </label>
                  <input
                    id="field-client-name"
                    className="input"
                    placeholder="cth. Ahmad Daniel (atau Kod Klien)"
                    value={draft.clientName}
                    onChange={(e) =>
                      onChangeDraft((p) => ({ ...p, clientName: e.target.value }))
                    }
                  />
                </div>
                <div className="field-group">
                  <label className="field-label" htmlFor="field-class-name">
                    Kelas / Kumpulan
                  </label>
                  <input
                    id="field-class-name"
                    className="input"
                    placeholder="cth. 5 Dinamik / Kelompok A"
                    value={draft.className}
                    onChange={(e) =>
                      onChangeDraft((p) => ({ ...p, className: e.target.value }))
                    }
                  />
                </div>
              </div>
            )}

            {/* Date, Day & Auto Time Picker */}
            <div className="form-grid-3">
              <div className="field-group">
                <label className="field-label" htmlFor="field-date">
                  <span>Tarikh</span>
                  <span style={{ color: 'var(--primary)' }}>
                    {draft.dayName || getMalayDayName(draft.sessionDateMillis)}
                  </span>
                </label>
                <input
                  id="field-date"
                  type="date"
                  className="input"
                  value={toDateInputValue(draft.sessionDateMillis)}
                  onChange={(e) => {
                    const nextMillis = fromDateInputValue(
                      e.target.value,
                      draft.sessionDateMillis
                    );
                    onChangeDraft((p) => ({
                      ...p,
                      sessionDateMillis: nextMillis,
                      dayName: getMalayDayName(nextMillis),
                    }));
                  }}
                />
              </div>

              <div className="field-group">
                <label className="field-label" htmlFor="field-start-time">
                  Masa Mula
                </label>
                <input
                  id="field-start-time"
                  type="time"
                  className="input"
                  value={startTime}
                  onChange={(e) => {
                    if (e.target.value) updateSchedule(e.target.value, endTime);
                  }}
                />
              </div>

              <div className="field-group">
                <label className="field-label" htmlFor="field-end-time">
                  Masa Tamat
                </label>
                <input
                  id="field-end-time"
                  type="time"
                  className="input"
                  value={endTime}
                  onChange={(e) => {
                    if (e.target.value) updateSchedule(startTime, e.target.value);
                  }}
                />
              </div>
            </div>

            <div className="time-bar">
              <div className="preset-chips">
                <button
                  type="button"
                  className="preset-chip"
                  onClick={applyNowTime}
                  title="Tetapkan tarikh dan masa mula ke waktu sekarang"
                >
                  Sekarang
                </button>
                {DURATION_PRESETS.map((preset) => (
                  <button
                    key={preset.minutes}
                    type="button"
                    className={`preset-chip ${
                      computedDuration === preset.minutes ? 'active' : ''
                    }`}
                    onClick={() => applyPresetMinutes(preset.minutes)}
                  >
                    {preset.label}
                  </button>
                ))}
              </div>

              <span className={`duration-badge ${isInvalidTimeOrder ? 'invalid' : ''}`}>
                <Clock size={14} />
                <span>
                  {isInvalidTimeOrder
                    ? 'Masa tamat mesti selepas masa mula'
                    : `Tempoh Auto: ${computedDuration} min (${formatMalayDate(
                        draft.sessionDateMillis
                      )})`}
                </span>
              </span>
            </div>

            {isKelasGanti && (
              <div className="field-group" style={{ marginTop: 4 }}>
                <label className="field-label" htmlFor="field-kelas-ganti-notes">
                  <span>Ringkasan Aktiviti Kelas Ganti</span>
                  <span style={{ fontWeight: 500, color: 'var(--ink-muted)' }}>
                    Tiada janaan AI diperlukan bagi kategori ini
                  </span>
                </label>
                <textarea
                  id="field-kelas-ganti-notes"
                  className="textarea"
                  style={{ minHeight: 150 }}
                  placeholder="Catatkan aktiviti pengisian kelas ganti, kehadiran atau perkembangan murid..."
                  value={draft.notes}
                  onChange={(e) =>
                    onChangeDraft((p) => ({ ...p, notes: e.target.value }))
                  }
                />
              </div>
            )}
          </section>

          {/* Card 3: Notes / Transcript (omitted for KELAS_GANTI) */}
          {!isKelasGanti && (
            <section className="editor-card">
              <div className="step-header">
                <div className="step-num">3</div>
                <div style={{ flex: 1 }}>
                  <h3 className="step-title">{notesCardTitle}</h3>
                  <p className="step-sub">
                    Taip dengan selesa menggunakan papan kekunci desktop ({wordCount} perkataan)
                  </p>
                </div>
              </div>
              <textarea
                className="textarea"
                style={{ minHeight: 168 }}
                placeholder="Masukkan transkrip perbualan, pemerhatian klinikal, isu yang dibincangkan atau catatan pelaksanaan..."
                value={draft.notes}
                onChange={(e) =>
                  onChangeDraft((p) => ({ ...p, notes: e.target.value }))
                }
              />
            </section>
          )}

          {/* Card 4: AI Summary & Analysis Box (omitted for KELAS_GANTI) */}
          {!isKelasGanti && (
            <section className="editor-card ai-card">
              <div className="step-header" style={{ justifyContent: 'space-between', flexWrap: 'wrap' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 11 }}>
                  <div className="step-num">4</div>
                  <div>
                    <h3 className="step-title">{summaryCardTitle}</h3>
                    <p className="step-sub">
                      {isProgram
                        ? 'Rumusan Program, Objektif, Kelebihan & Kelemahan, dan Huraian Intervensi'
                        : 'Dijana melalui kunci Gemini API peribadi anda atau boleh disunting secara manual'}
                    </p>
                  </div>
                </div>

                <button
                  type="button"
                  className="btn btn-ai"
                  onClick={() => void onGenerateAi()}
                  disabled={aiLoading || !draft.notes.trim()}
                >
                  {aiLoading ? (
                    <>
                      <Loader2 size={16} className="spin" />
                      <span>Menjana Rumusan AI...</span>
                    </>
                  ) : (
                    <>
                      <Sparkles size={16} />
                      <span>Jana Rumusan AI daripada Catatan</span>
                    </>
                  )}
                </button>
              </div>

              {aiError && (
                <div className="alert-banner error" role="alert">
                  <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
                    <ShieldAlert size={18} style={{ flexShrink: 0, marginTop: 2 }} />
                    <span>{aiError.message}</span>
                  </div>
                  {aiError.requiresApiKeySetup && (
                    <button
                      type="button"
                      className="btn btn-outline"
                      onClick={onOpenSecurityTab}
                    >
                      Buka Tab Keselamatan
                    </button>
                  )}
                </div>
              )}

              <div className="field-group">
                <label className="field-label" htmlFor="field-summary-matter">
                  {summaryLabels.matter}
                </label>
                <textarea
                  id="field-summary-matter"
                  className="textarea"
                  style={{ minHeight: 92 }}
                  placeholder="Isi secara manual atau tekan Jana Rumusan AI di atas..."
                  value={draft.summaryMatter}
                  onChange={(e) =>
                    onChangeDraft((p) => ({ ...p, summaryMatter: e.target.value }))
                  }
                />
              </div>

              <div className="field-group">
                <label className="field-label" htmlFor="field-summary-questions">
                  {summaryLabels.questions}
                </label>
                <textarea
                  id="field-summary-questions"
                  className="textarea"
                  style={{ minHeight: 92 }}
                  placeholder="Persoalan utama / objektif..."
                  value={draft.summaryQuestions}
                  onChange={(e) =>
                    onChangeDraft((p) => ({ ...p, summaryQuestions: e.target.value }))
                  }
                />
              </div>

              {isProgram && (
                <div className="field-group">
                  <label className="field-label" htmlFor="field-summary-sw">
                    {summaryLabels.strengths}
                  </label>
                  <textarea
                    id="field-summary-sw"
                    className="textarea"
                    style={{ minHeight: 92 }}
                    placeholder="Kelebihan (kekuatan) & kelemahan / ruang penambahbaikan program..."
                    value={draft.programStrengthsWeaknesses}
                    onChange={(e) =>
                      onChangeDraft((p) => ({
                        ...p,
                        programStrengthsWeaknesses: e.target.value,
                      }))
                    }
                  />
                </div>
              )}

              <div className="field-group">
                <label className="field-label" htmlFor="field-summary-intervention">
                  {summaryLabels.intervention}
                </label>
                <textarea
                  id="field-summary-intervention"
                  className="textarea"
                  style={{ minHeight: 112 }}
                  placeholder="Huraian intervensi kaunseling, keputusan & pelan tindakan susulan..."
                  value={draft.summaryIntervention}
                  onChange={(e) =>
                    onChangeDraft((p) => ({
                      ...p,
                      summaryIntervention: e.target.value,
                    }))
                  }
                />
              </div>
            </section>
          )}
        </div>
      </div>

      {confirmDelete && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div
                style={{
                  width: 40,
                  height: 40,
                  borderRadius: 10,
                  background: 'var(--danger-soft)',
                  color: 'var(--danger)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <AlertTriangle size={20} />
              </div>
              <div>
                <h3 style={{ margin: 0, fontFamily: 'var(--font-display)' }}>
                  Padam Rekod Ini?
                </h3>
                <p style={{ margin: '3px 0 0', fontSize: 12.5, color: 'var(--ink-muted)' }}>
                  Tindakan ini akan memadam rekod di desktop dan di aplikasi Android anda.
                </p>
              </div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
              <button
                type="button"
                className="btn btn-outline"
                onClick={() => setConfirmDelete(false)}
              >
                Batal
              </button>
              <button
                type="button"
                className="btn btn-danger-outline"
                onClick={() => {
                  setConfirmDelete(false);
                  onDelete();
                }}
                style={{
                  background: 'var(--danger)',
                  color: '#fff',
                  borderColor: 'var(--danger)',
                }}
              >
                Ya, Padam Kekal
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
