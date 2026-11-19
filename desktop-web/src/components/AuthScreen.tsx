import {
  BookLock,
  CloudCog,
  Download,
  FileText,
  Loader2,
  ShieldCheck,
  Sparkles,
} from 'lucide-react';

interface AuthScreenProps {
  loading: boolean;
  error: string | null;
  canInstallPwa: boolean;
  onSignIn: () => void;
  onInstallPwa: () => void;
}

export function AuthScreen({
  loading,
  error,
  canInstallPwa,
  onSignIn,
  onInstallPwa,
}: AuthScreenProps) {
  return (
    <div className="auth-page">
      <div className="auth-shell">
        <div className="auth-hero">
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div
                style={{
                  width: 44,
                  height: 44,
                  borderRadius: 12,
                  background: 'rgba(255,255,255,0.16)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <BookLock size={24} />
              </div>
              <span
                style={{
                  fontSize: 12,
                  fontWeight: 700,
                  letterSpacing: '0.08em',
                  textTransform: 'uppercase',
                  opacity: 0.9,
                }}
              >
                Versi Desktop Windows (PWA)
              </span>
            </div>
            <h1>Diari Kaunselor</h1>
            <p>
              Ruang kerja skrin lebar untuk Guru Bimbingan &amp; Kaunseling. Semua
              catatan yang anda simpan di telefon Android boleh dibuka, disunting,
              dijana rumusan AI, dan dicetak terus pada komputer Windows anda.
            </p>
          </div>

          <div className="auth-features">
            <div className="auth-feature-item">
              <CloudCog size={18} style={{ flexShrink: 0, marginTop: 2 }} />
              <span>
                <strong>Segerak Masa Nyata Android ↔ Desktop:</strong> Data terus
                selari merentas peranti apabila menggunakan Akaun Google yang sama.
              </span>
            </div>
            <div className="auth-feature-item">
              <ShieldCheck size={18} style={{ flexShrink: 0, marginTop: 2 }} />
              <span>
                <strong>Penyulitan E2EE AES-256-GCM:</strong> Catatan sulit dinyahsulit
                secara automatik pada pelayar anda sahaja.
              </span>
            </div>
            <div className="auth-feature-item">
              <FileText size={18} style={{ flexShrink: 0, marginTop: 2 }} />
              <span>
                <strong>Eksport PDF &amp; Word (.docx):</strong> Jana laporan rasmi
                sedia cetak terus ke PC tanpa pelayan pihak ketiga.
              </span>
            </div>
            <div className="auth-feature-item">
              <Sparkles size={18} style={{ flexShrink: 0, marginTop: 2 }} />
              <span>
                <strong>Janaan AI Gemini API:</strong> Rumusan profesional mengikut
                kesemua kategori aktiviti kaunseling.
              </span>
            </div>
          </div>
        </div>

        <div className="auth-panel">
          <div>
            <h2>Log Masuk Akaun Kaunselor</h2>
            <p style={{ fontSize: 13.5, color: 'var(--ink-muted)', marginTop: 6, lineHeight: 1.55 }}>
              Gunakan <strong>Akaun Google yang sama</strong> seperti dalam aplikasi
              Android Diari Kaunselor supaya semua rekod awan anda dipaparkan secara
              automatik.
            </p>
          </div>

          {error && (
            <div className="alert-banner error" role="alert">
              <span>{error}</span>
            </div>
          )}

          <button
            type="button"
            className="google-btn"
            onClick={onSignIn}
            disabled={loading}
          >
            {loading ? (
              <>
                <Loader2 size={18} className="spin" />
                <span>Menghubungkan ke Akaun Google...</span>
              </>
            ) : (
              <>
                <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
                  <path
                    fill="#4285F4"
                    d="M23.49 12.27c0-.79-.07-1.54-.2-2.27H12v4.3h6.45a5.52 5.52 0 0 1-2.39 3.62v3h3.87c2.26-2.08 3.56-5.15 3.56-8.65z"
                  />
                  <path
                    fill="#34A853"
                    d="M12 24c3.24 0 5.95-1.07 7.93-2.9l-3.87-3c-1.07.72-2.44 1.14-4.06 1.14-3.12 0-5.77-2.11-6.72-4.95H1.28v3.1A12 12 0 0 0 12 24z"
                  />
                  <path
                    fill="#FBBC05"
                    d="M5.28 14.29A7.2 7.2 0 0 1 4.9 12c0-.79.14-1.56.38-2.29V6.61H1.28A12 12 0 0 0 0 12c0 1.94.46 3.77 1.28 5.39l4-3.1z"
                  />
                  <path
                    fill="#EA4335"
                    d="M12 4.76c1.76 0 3.34.61 4.59 1.8l3.44-3.44C17.95 1.19 15.24 0 12 0A12 12 0 0 0 1.28 6.61l4 3.1C6.23 6.87 8.88 4.76 12 4.76z"
                  />
                </svg>
                <span>Log Masuk dengan Google</span>
              </>
            )}
          </button>

          {canInstallPwa && (
            <button
              type="button"
              className="btn-install-pwa"
              onClick={onInstallPwa}
            >
              <Download size={16} />
              <span>Pasang sebagai Aplikasi Desktop Windows</span>
            </button>
          )}

          <div
            style={{
              fontSize: 12,
              color: 'var(--ink-muted)',
              background: 'var(--surface-alt)',
              padding: '12px 14px',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--outline)',
              lineHeight: 1.5,
            }}
          >
            <strong>Tip Chrome / Edge:</strong> Selepas log masuk, klik ikon{' '}
            <em>Pasang (Install)</em> di penjuru kanan bar alamat untuk meletakkan
            pintasan Diari Kaunselor di Desktop &amp; Taskbar Windows anda.
          </div>
        </div>
      </div>
    </div>
  );
}
