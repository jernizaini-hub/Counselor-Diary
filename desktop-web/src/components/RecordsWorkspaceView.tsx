import { BookOpenText, Clock, Plus, Search, Sparkles } from 'lucide-react';
import {
  ACTIVITY_CATEGORIES,
  CATEGORY_MAP,
  formatMalayDate,
  getRecordSubtitle,
  getRecordTitle,
  type ActivityCategory,
  type AiSummaryResult,
  type CounselingRecord,
} from '../models/counseling';
import { RecordEditorPane } from './RecordEditorPane';

interface RecordsWorkspaceViewProps {
  records: CounselingRecord[];
  searchQuery: string;
  onChangeSearch: (q: string) => void;
  selectedCategory: ActivityCategory | null;
  onChangeCategory: (cat: ActivityCategory | null) => void;
  activeDraft: CounselingRecord | null;
  isDirty: boolean;
  saving: boolean;
  deleting: boolean;
  aiLoading: boolean;
  aiError: { message: string; requiresApiKeySetup: boolean } | null;
  counselorName: string;
  counselorEmail: string;
  onSelectRecord: (record: CounselingRecord) => void;
  onCreateNew: () => void;
  onChangeDraft: (updater: (prev: CounselingRecord) => CounselingRecord) => void;
  onSave: () => void;
  onDelete: () => void;
  onGenerateAi: () => Promise<AiSummaryResult | null>;
  onOpenSecurityTab: () => void;
  onNotify: (msg: string) => void;
}

export function RecordsWorkspaceView({
  records,
  searchQuery,
  onChangeSearch,
  selectedCategory,
  onChangeCategory,
  activeDraft,
  isDirty,
  saving,
  deleting,
  aiLoading,
  aiError,
  counselorName,
  counselorEmail,
  onSelectRecord,
  onCreateNew,
  onChangeDraft,
  onSave,
  onDelete,
  onGenerateAi,
  onOpenSecurityTab,
  onNotify,
}: RecordsWorkspaceViewProps) {
  const q = searchQuery.trim().toLowerCase();
  const filtered = records.filter((rec) => {
    if (selectedCategory && rec.category !== selectedCategory) return false;
    if (!q) return true;
    const haystack = [
      rec.clientName,
      rec.className,
      rec.organizer,
      rec.guardianName,
      rec.guardianPhone,
      rec.notes,
      rec.summaryMatter,
      rec.summaryQuestions,
      rec.programStrengthsWeaknesses,
      rec.summaryIntervention,
      CATEGORY_MAP[rec.category].displayName,
    ]
      .join(' ')
      .toLowerCase();
    return haystack.includes(q);
  });

  return (
    <div className="split-workspace">
      {/* Left Master List Pane */}
      <section className="master-pane" aria-label="Senarai Rekod">
        <div className="master-header">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div>
              <div style={{ fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 17 }}>
                Rekod &amp; Diari
              </div>
              <div style={{ fontSize: 12, color: 'var(--ink-muted)' }}>
                {filtered.length} daripada {records.length} rekod
              </div>
            </div>
            <button
              type="button"
              className="btn btn-outline"
              style={{ padding: '6px 10px', fontSize: 12 }}
              onClick={onCreateNew}
            >
              <Plus size={14} />
              <span>Baharu</span>
            </button>
          </div>

          <div className="search-box">
            <Search size={15} />
            <input
              type="search"
              className="search-input"
              placeholder="Cari nama, kelas, anjuran, nota..."
              value={searchQuery}
              onChange={(e) => onChangeSearch(e.target.value)}
            />
          </div>

          <div className="filter-pills">
            <button
              type="button"
              className={`filter-pill ${selectedCategory === null ? 'active' : ''}`}
              onClick={() => onChangeCategory(null)}
            >
              Semua ({records.length})
            </button>
            {ACTIVITY_CATEGORIES.map((cat) => {
              const count = records.filter((r) => r.category === cat.key).length;
              return (
                <button
                  key={cat.key}
                  type="button"
                  className={`filter-pill ${selectedCategory === cat.key ? 'active' : ''}`}
                  onClick={() =>
                    onChangeCategory(selectedCategory === cat.key ? null : cat.key)
                  }
                >
                  {cat.shortLabel} ({count})
                </button>
              );
            })}
          </div>
        </div>

        <div className="master-list">
          {filtered.length === 0 ? (
            <div className="empty-state" style={{ padding: '32px 14px' }}>
              <BookOpenText size={28} />
              <div style={{ fontWeight: 700, color: 'var(--ink)', fontSize: 14 }}>
                Tiada rekod sepadan
              </div>
              <div style={{ fontSize: 12.5 }}>
                Cuba ubah kata carian atau cipta catatan baharu.
              </div>
            </div>
          ) : (
            filtered.map((rec) => {
              const meta = CATEGORY_MAP[rec.category];
              const isSelected = activeDraft?.id === rec.id && rec.id !== '';
              const subtitle = getRecordSubtitle(rec);

              return (
                <button
                  key={rec.id}
                  type="button"
                  className={`record-list-card ${isSelected ? 'selected' : ''}`}
                  onClick={() => onSelectRecord(rec)}
                >
                  <div className="record-card-top">
                    <span
                      className="cat-pill"
                      style={{ background: meta.softBgHex, color: meta.accentHex }}
                    >
                      {meta.shortLabel}
                    </span>
                    {rec.category !== 'KELAS_GANTI' && rec.hasAiSummary && (
                      <span className="ai-pill">
                        <Sparkles size={10} />
                        <span>AI</span>
                      </span>
                    )}
                  </div>
                  <div className="record-card-title">{getRecordTitle(rec)}</div>
                  {subtitle && <div className="record-card-sub">{subtitle}</div>}
                  <div className="record-card-meta">
                    <span>
                      {rec.dayName}, {formatMalayDate(rec.sessionDateMillis)}
                    </span>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3 }}>
                      <Clock size={11} />
                      {rec.durationMinutes} min
                    </span>
                  </div>
                </button>
              );
            })
          )}
        </div>
      </section>

      {/* Right Detail / Editor Pane */}
      {activeDraft ? (
        <RecordEditorPane
          draft={activeDraft}
          isDirty={isDirty}
          saving={saving}
          deleting={deleting}
          aiLoading={aiLoading}
          aiError={aiError}
          counselorName={counselorName}
          counselorEmail={counselorEmail}
          onChangeDraft={onChangeDraft}
          onSave={onSave}
          onDelete={onDelete}
          onGenerateAi={onGenerateAi}
          onOpenSecurityTab={onOpenSecurityTab}
          onNotify={onNotify}
        />
      ) : (
        <div className="detail-pane">
          <div className="empty-state">
            <BookOpenText size={44} />
            <h2 style={{ fontFamily: 'var(--font-display)', color: 'var(--ink)', margin: 0 }}>
              Pilih Rekod atau Cipta Catatan Baharu
            </h2>
            <p style={{ maxWidth: 420, fontSize: 13.5, lineHeight: 1.55 }}>
              Klik mana-mana rekod di senarai kiri untuk melihat, menyunting, menjana
              rumusan AI, atau mengeksport laporan PDF dan Word (.docx).
            </p>
            <button type="button" className="btn btn-primary" onClick={onCreateNew}>
              <Plus size={16} />
              <span>Cipta Catatan Baharu</span>
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
