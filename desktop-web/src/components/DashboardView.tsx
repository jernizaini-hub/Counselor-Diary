import {
  ArrowRight,
  Clock,
  FileCheck2,
  FolderKanban,
  PlusCircle,
  Sparkles,
  Users,
} from 'lucide-react';
import {
  ACTIVITY_CATEGORIES,
  CATEGORY_MAP,
  STATS_PERIODS,
  formatDurationHuman,
  formatMalayDate,
  getRecordSubtitle,
  getRecordTitle,
  type ActivityCategory,
  type CounselingRecord,
  type DashboardStats,
  type StatsPeriod,
} from '../models/counseling';

interface DashboardViewProps {
  counselorName: string;
  stats: DashboardStats;
  period: StatsPeriod;
  onChangePeriod: (p: StatsPeriod) => void;
  records: CounselingRecord[];
  onOpenRecord: (record: CounselingRecord) => void;
  onNewRecordWithCategory: (cat: ActivityCategory) => void;
  onFilterByCategory: (cat: ActivityCategory) => void;
}

export function DashboardView({
  counselorName,
  stats,
  period,
  onChangePeriod,
  records,
  onOpenRecord,
  onNewRecordWithCategory,
  onFilterByCategory,
}: DashboardViewProps) {
  const firstName = counselorName.split(' ')[0] || 'Kaunselor';
  const maxTrend = Math.max(1, ...stats.weeklyTrend.map((d) => d.count));
  const maxCategory = Math.max(1, ...Object.values(stats.categoryCounts));
  const recentRecords = records.slice(0, 6);

  return (
    <div className="scroll-page">
      <div className="page-container">
        <section className="hero-banner">
          <div>
            <h1 className="hero-title">Selamat datang, {firstName}</h1>
            <p className="hero-sub">
              Ringkasan prestasi sesi kaunseling dan aktiviti bimbingan anda. Semua data
              disegerakkan secara masa nyata dengan aplikasi Android dalam format E2EE.
            </p>
          </div>
          <div className="period-tabs" role="tablist" aria-label="Tempoh Statistik">
            {STATS_PERIODS.map((p) => (
              <button
                key={p.key}
                type="button"
                role="tab"
                aria-selected={period === p.key}
                className={`period-tab ${period === p.key ? 'active' : ''}`}
                onClick={() => onChangePeriod(p.key)}
              >
                {p.label}
              </button>
            ))}
          </div>
        </section>

        <section className="kpi-grid" aria-label="Metrik Utama">
          <div className="kpi-card">
            <div className="kpi-header">
              <span>Jumlah Rekod</span>
              <span
                className="kpi-icon"
                style={{ background: 'var(--primary-soft)', color: 'var(--primary)' }}
              >
                <FolderKanban size={18} />
              </span>
            </div>
            <div className="kpi-value">{stats.totalRecords}</div>
            <div className="kpi-sub">
              {stats.activitiesCount} aktiviti &amp; program lain
            </div>
          </div>

          <div className="kpi-card">
            <div className="kpi-header">
              <span>Sesi Kaunseling</span>
              <span
                className="kpi-icon"
                style={{ background: '#E4F4F2', color: '#0F766E' }}
              >
                <Users size={18} />
              </span>
            </div>
            <div className="kpi-value">{stats.counselingSessionsCount}</div>
            <div className="kpi-sub">Individu &amp; kelompok</div>
          </div>

          <div className="kpi-card">
            <div className="kpi-header">
              <span>Rumusan AI</span>
              <span
                className="kpi-icon"
                style={{ background: 'var(--ai-soft)', color: 'var(--ai)' }}
              >
                <Sparkles size={18} />
              </span>
            </div>
            <div className="kpi-value">{stats.aiSummaryCount}</div>
            <div className="kpi-sub">Rekod lengkap dengan rumusan</div>
          </div>

          <div className="kpi-card">
            <div className="kpi-header">
              <span>Jumlah Tempoh</span>
              <span
                className="kpi-icon"
                style={{ background: 'var(--secondary-soft)', color: 'var(--secondary)' }}
              >
                <Clock size={18} />
              </span>
            </div>
            <div className="kpi-value">{formatDurationHuman(stats.totalMinutes)}</div>
            <div className="kpi-sub">{stats.totalMinutes} minit keseluruhan</div>
          </div>
        </section>

        <section className="charts-grid">
          <div className="card">
            <div className="section-heading">
              <div>
                <h2>Tren Aktiviti 7 Hari Terkini</h2>
                <p>Bilangan sesi dan aktiviti direkodkan mengikut hari</p>
              </div>
            </div>
            <div className="bar-chart" role="img" aria-label="Carta bar 7 hari terkini">
              {stats.weeklyTrend.map((point) => {
                const pct = point.count === 0 ? 4 : Math.max(12, Math.round((point.count / maxTrend) * 100));
                return (
                  <div key={`${point.dayLabel}-${point.dateLabel}`} className="bar-col">
                    <span className="bar-count">{point.count}</span>
                    <div className="bar-track" title={`${point.dayLabel} (${point.dateLabel}): ${point.count} rekod`}>
                      <div
                        className="bar-fill"
                        style={{
                          height: `${pct}%`,
                          opacity: point.count === 0 ? 0.25 : 1,
                        }}
                      />
                    </div>
                    <span className="bar-day">{point.dayLabel}</span>
                    <span className="bar-date">{point.dateLabel}</span>
                  </div>
                );
              })}
            </div>
          </div>

          <div className="card">
            <div className="section-heading">
              <div>
                <h2>Pecahan Mengikut Kategori</h2>
                <p>Klik mana-mana kategori untuk menapis di ruang kerja 2 panel</p>
              </div>
            </div>
            <div className="category-bars">
              {ACTIVITY_CATEGORIES.map((cat) => {
                const count = stats.categoryCounts[cat.key] ?? 0;
                const widthPct = count === 0 ? 2 : Math.max(6, Math.round((count / maxCategory) * 100));
                return (
                  <button
                    key={cat.key}
                    type="button"
                    className="cat-bar-row"
                    onClick={() => onFilterByCategory(cat.key)}
                  >
                    <div className="cat-bar-top">
                      <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span className="cat-dot" style={{ background: cat.accentHex }} />
                        <span>{cat.displayName}</span>
                      </span>
                      <span style={{ color: 'var(--ink-secondary)' }}>{count}</span>
                    </div>
                    <div className="cat-bar-track">
                      <div
                        className="cat-bar-fill"
                        style={{
                          width: `${widthPct}%`,
                          background: cat.accentHex,
                          opacity: count === 0 ? 0.2 : 1,
                        }}
                      />
                    </div>
                  </button>
                );
              })}
            </div>
          </div>
        </section>

        <section className="card">
          <div className="section-heading">
            <div>
              <h2>Pintasan Cipta Catatan Pantas</h2>
              <p>Mulakan borang kategori khusus terus dalam paparan editor 2 panel</p>
            </div>
          </div>
          <div className="quick-actions-grid">
            {ACTIVITY_CATEGORIES.map((cat) => (
              <button
                key={cat.key}
                type="button"
                className="quick-cat-btn"
                onClick={() => onNewRecordWithCategory(cat.key)}
              >
                <PlusCircle size={17} style={{ color: cat.accentHex, flexShrink: 0 }} />
                <span>{cat.displayName}</span>
              </button>
            ))}
          </div>
        </section>

        <section className="card">
          <div className="section-heading">
            <div>
              <h2>Rekod Terkini (Android &amp; Desktop)</h2>
              <p>Klik mana-mana baris untuk membuka, menyunting atau mengeksport PDF/Word</p>
            </div>
          </div>
          {recentRecords.length === 0 ? (
            <div className="empty-state" style={{ padding: '28px 16px' }}>
              <FileCheck2 size={32} />
              <div style={{ fontWeight: 700, color: 'var(--ink)' }}>
                Tiada rekod lagi dalam tempoh ini
              </div>
              <div style={{ fontSize: 13 }}>
                Cipta catatan pertama anda di atas atau tunggu penyegerakan daripada
                aplikasi Android.
              </div>
            </div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table className="recent-table">
                <thead>
                  <tr>
                    <th>Kategori</th>
                    <th>Tajuk / Klien / Kelas</th>
                    <th>Tarikh &amp; Masa</th>
                    <th>Tempoh</th>
                    <th>Status</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {recentRecords.map((rec) => {
                    const meta = CATEGORY_MAP[rec.category];
                    return (
                      <tr key={rec.id} onClick={() => onOpenRecord(rec)}>
                        <td>
                          <span
                            className="cat-pill"
                            style={{
                              background: meta.softBgHex,
                              color: meta.accentHex,
                            }}
                          >
                            {meta.shortLabel}
                          </span>
                        </td>
                        <td>
                          <div style={{ fontWeight: 700 }}>{getRecordTitle(rec)}</div>
                          {getRecordSubtitle(rec) && (
                            <div style={{ fontSize: 12, color: 'var(--ink-muted)' }}>
                              {getRecordSubtitle(rec)}
                            </div>
                          )}
                        </td>
                        <td>
                          <div>{formatMalayDate(rec.sessionDateMillis)}</div>
                          <div style={{ fontSize: 11.5, color: 'var(--ink-muted)' }}>
                            {rec.dayName} • {rec.timeRange}
                          </div>
                        </td>
                        <td>{rec.durationMinutes} min</td>
                        <td>
                          {rec.category === 'KELAS_GANTI' ? (
                            <span style={{ fontSize: 12, color: 'var(--ink-muted)' }}>
                              Ringkas
                            </span>
                          ) : rec.hasAiSummary ? (
                            <span className="ai-pill">
                              <Sparkles size={11} />
                              <span>Rumusan</span>
                            </span>
                          ) : (
                            <span style={{ fontSize: 12, color: 'var(--ink-muted)' }}>
                              Draf
                            </span>
                          )}
                        </td>
                        <td style={{ textAlign: 'right', color: 'var(--primary)' }}>
                          <ArrowRight size={16} />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
