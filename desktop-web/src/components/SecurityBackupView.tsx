import { useState } from 'react';
import {
  CheckCircle2,
  Cloud,
  Download,
  ExternalLink,
  Eye,
  EyeOff,
  KeyRound,
  Laptop,
  Loader2,
  RefreshCw,
  ShieldCheck,
  Trash2,
} from 'lucide-react';
import { ENCRYPTION_VERSION } from '../crypto/e2ee';
import { maskGeminiKey } from '../data/geminiService';

export type KeyTestState =
  | { status: 'idle' }
  | { status: 'testing' }
  | { status: 'success'; message: string }
  | { status: 'error'; message: string };

interface SecurityBackupViewProps {
  userEmail: string;
  recordCount: number;
  lastSyncedLabel: string;
  savedGeminiKey: string;
  keyTestState: KeyTestState;
  onSaveAndVerifyKey: (rawKey: string) => Promise<void>;
  onClearKey: () => void;
  onVerifyCloudSync: () => Promise<void>;
  onExportEncryptedJson: () => void;
  canInstallPwa: boolean;
  onInstallPwa: () => void;
}

export function SecurityBackupView({
  userEmail,
  recordCount,
  lastSyncedLabel,
  savedGeminiKey,
  keyTestState,
  onSaveAndVerifyKey,
  onClearKey,
  onVerifyCloudSync,
  onExportEncryptedJson,
  canInstallPwa,
  onInstallPwa,
}: SecurityBackupViewProps) {
  const [keyInput, setKeyInput] = useState(savedGeminiKey);
  const [showKey, setShowKey] = useState(false);
  const [verifyingCloud, setVerifyingCloud] = useState(false);

  const handleVerifyCloud = async () => {
    setVerifyingCloud(true);
    try {
      await onVerifyCloudSync();
    } finally {
      setVerifyingCloud(false);
    }
  };

  return (
    <div className="scroll-page">
      <div className="page-container">
        <div>
          <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 26, margin: 0 }}>
            Keselamatan, Enjin AI &amp; Sandaran Awan
          </h1>
          <p style={{ margin: '6px 0 0', fontSize: 13.5, color: 'var(--ink-muted)' }}>
            Urus kunci Gemini API peribadi, pantau penyulitan E2EE merentas Android &amp;
            Windows, serta muat turun sandaran JSON tersulit.
          </p>
        </div>

        <div className="security-grid">
          {/* Card 1: Gemini API Key */}
          <section className="card" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 11 }}>
                <div
                  style={{
                    width: 40,
                    height: 40,
                    borderRadius: 11,
                    background: 'var(--ai-soft)',
                    color: 'var(--ai)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <KeyRound size={20} />
                </div>
                <div>
                  <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 17, margin: 0 }}>
                    Enjin Gemini API (Janaan AI)
                  </h2>
                  <p style={{ margin: '2px 0 0', fontSize: 12, color: 'var(--ink-muted)' }}>
                    {savedGeminiKey
                      ? `Aktif: ${maskGeminiKey(savedGeminiKey)}`
                      : 'Belum dikonfigurasi pada pelayar ini'}
                  </p>
                </div>
              </div>
              <a
                href="https://aistudio.google.com/apikey"
                target="_blank"
                rel="noreferrer"
                className="btn btn-outline"
                style={{ fontSize: 12, textDecoration: 'none' }}
              >
                <span>Dapatkan Kunci</span>
                <ExternalLink size={13} />
              </a>
            </div>

            <p style={{ margin: 0, fontSize: 13, color: 'var(--ink-secondary)', lineHeight: 1.55 }}>
              Anda boleh menampal <strong>kunci Gemini API yang sama</strong> seperti dalam
              aplikasi Android. Kunci ini disimpan setempat dalam pelayar komputer anda
              sahaja dan tidak pernah dimuat naik ke pangkalan data awan.
            </p>

            <div className="field-group">
              <label className="field-label" htmlFor="gemini-api-key-input">
                Kunci Gemini API (AIza...)
              </label>
              <div style={{ display: 'flex', gap: 8 }}>
                <input
                  id="gemini-api-key-input"
                  type={showKey ? 'text' : 'password'}
                  className="input"
                  placeholder="Tampal kunci Gemini API daripada Google AI Studio"
                  value={keyInput}
                  onChange={(e) => setKeyInput(e.target.value)}
                />
                <button
                  type="button"
                  className="btn btn-outline"
                  onClick={() => setShowKey((s) => !s)}
                  title={showKey ? 'Sembunyikan kunci' : 'Paparkan kunci'}
                >
                  {showKey ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            {keyTestState.status === 'success' && (
              <div className="alert-banner success">
                <span style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <CheckCircle2 size={16} />
                  <span>{keyTestState.message}</span>
                </span>
              </div>
            )}

            {keyTestState.status === 'error' && (
              <div className="alert-banner error">{keyTestState.message}</div>
            )}

            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              <button
                type="button"
                className="btn btn-ai"
                disabled={keyTestState.status === 'testing' || !keyInput.trim()}
                onClick={() => void onSaveAndVerifyKey(keyInput)}
              >
                {keyTestState.status === 'testing' ? (
                  <>
                    <Loader2 size={15} className="spin" />
                    <span>Menguji Kunci...</span>
                  </>
                ) : (
                  <span>Simpan &amp; Uji Kunci</span>
                )}
              </button>

              {savedGeminiKey && (
                <button
                  type="button"
                  className="btn btn-danger-outline"
                  onClick={() => {
                    onClearKey();
                    setKeyInput('');
                  }}
                >
                  <Trash2 size={15} />
                  <span>Padam Kunci</span>
                </button>
              )}
            </div>
          </section>

          {/* Card 2: E2EE Cross-Device Status */}
          <section className="card" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 11 }}>
              <div
                style={{
                  width: 40,
                  height: 40,
                  borderRadius: 11,
                  background: 'var(--primary-soft)',
                  color: 'var(--primary)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <ShieldCheck size={20} />
              </div>
              <div>
                <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 17, margin: 0 }}>
                  Penyulitan Hujung-ke-Hujung (E2EE)
                </h2>
                <p style={{ margin: '2px 0 0', fontSize: 12, color: 'var(--ink-muted)' }}>
                  Protokol seragam {ENCRYPTION_VERSION} (Android ↔ Desktop)
                </p>
              </div>
            </div>

            <div className="alert-banner info" style={{ flexDirection: 'column', gap: 6 }}>
              <div style={{ fontWeight: 700 }}>Akaun Aktif: {userEmail}</div>
              <div style={{ fontSize: 12.5, color: 'var(--ink-secondary)' }}>
                Semua nama klien, kelas, penjaga, transkrip dan rumusan AI disulitkan
                dengan kunci induk AES-256-GCM sebelum dihantar ke Cloud Firestore. Kedua-dua
                aplikasi Android dan Desktop membuka peti besi yang sama secara automatik.
              </div>
            </div>

            <div style={{ fontSize: 12.5, color: 'var(--ink-muted)', lineHeight: 1.6 }}>
              • Derivasi Kunci: <strong>PBKDF2-HMAC-SHA256 (100,000 lelaran)</strong>
              <br />
              • Algoritma Muatan: <strong>AES-256-GCM (12-bait IV, 128-bit tag)</strong>
              <br />
              • Jumlah Rekod Dinyahsulit: <strong>{recordCount} rekod</strong>
            </div>
          </section>

          {/* Card 3: Cloud Sync & E2EE JSON Export */}
          <section className="card" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 11 }}>
              <div
                style={{
                  width: 40,
                  height: 40,
                  borderRadius: 11,
                  background: 'var(--success-soft)',
                  color: 'var(--success)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Cloud size={20} />
              </div>
              <div>
                <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 17, margin: 0 }}>
                  Sandaran Awan &amp; Eksport JSON
                </h2>
                <p style={{ margin: '2px 0 0', fontSize: 12, color: 'var(--ink-muted)' }}>
                  {lastSyncedLabel}
                </p>
              </div>
            </div>

            <p style={{ margin: 0, fontSize: 13, color: 'var(--ink-secondary)', lineHeight: 1.55 }}>
              Anda boleh mengesahkan capaian awan atau memuat turun salinan arkib JSON
              tersulit ({recordCount} rekod) terus ke cakera keras Windows anda sebagai
              salinan keselamatan tambahan.
            </p>

            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              <button
                type="button"
                className="btn btn-outline"
                disabled={verifyingCloud}
                onClick={() => void handleVerifyCloud()}
              >
                <RefreshCw size={15} className={verifyingCloud ? 'spin' : ''} />
                <span>Sahkan Segerak Awan</span>
              </button>

              <button
                type="button"
                className="btn btn-primary"
                onClick={onExportEncryptedJson}
              >
                <Download size={15} />
                <span>Eksport Sandaran E2EE (.json)</span>
              </button>
            </div>
          </section>

          {/* Card 4: Windows PWA Install Guide */}
          <section className="card" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 11 }}>
              <div
                style={{
                  width: 40,
                  height: 40,
                  borderRadius: 11,
                  background: 'var(--secondary-soft)',
                  color: 'var(--secondary)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Laptop size={20} />
              </div>
              <div>
                <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 17, margin: 0 }}>
                  Pemasangan Aplikasi Desktop Windows
                </h2>
                <p style={{ margin: '2px 0 0', fontSize: 12, color: 'var(--ink-muted)' }}>
                  Serasi dengan Google Chrome &amp; Microsoft Edge
                </p>
              </div>
            </div>

            <ol
              style={{
                margin: 0,
                paddingLeft: 18,
                fontSize: 13,
                color: 'var(--ink-secondary)',
                lineHeight: 1.65,
              }}
            >
              <li>
                Buka aplikasi ini dalam pelayar <strong>Chrome</strong> atau{' '}
                <strong>Microsoft Edge</strong> di Windows.
              </li>
              <li>
                Klik butang <strong>Pasang Aplikasi Desktop</strong> di bawah atau ikon{' '}
                <em>Pasang (Install App)</em> di penjuru kanan bar alamat pelayar.
              </li>
              <li>
                Sematkan (<em>Pin</em>) ikon <strong>Diari Kaunselor</strong> pada Taskbar
                atau Desktop Windows untuk akses pantas seperti aplikasi natif.
              </li>
            </ol>

            {canInstallPwa && (
              <div>
                <button type="button" className="btn btn-primary" onClick={onInstallPwa}>
                  <Download size={15} />
                  <span>Pasang Sekarang pada Windows</span>
                </button>
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
