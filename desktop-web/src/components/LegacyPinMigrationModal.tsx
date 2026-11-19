import { useState } from 'react';
import { KeyRound, Loader2, LogOut } from 'lucide-react';

interface LegacyPinMigrationModalProps {
  busy: boolean;
  error: string | null;
  onMigrate: (pin: string) => void;
  onSignOut: () => void;
}

export function LegacyPinMigrationModal({
  busy,
  error,
  onMigrate,
  onSignOut,
}: LegacyPinMigrationModalProps) {
  const [pin, setPin] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onMigrate(pin.trim());
  };

  return (
    <div className="modal-backdrop">
      <form className="modal-card" onSubmit={handleSubmit}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div
            style={{
              width: 42,
              height: 42,
              borderRadius: 12,
              background: 'var(--primary-soft)',
              color: 'var(--primary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <KeyRound size={22} />
          </div>
          <div>
            <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 19, margin: 0 }}>
              Migrasi Peti Besi PIN Lama
            </h2>
            <p style={{ fontSize: 12.5, color: 'var(--ink-muted)', margin: '3px 0 0' }}>
              Akaun anda menggunakan PIN 6 digit versi terdahulu.
            </p>
          </div>
        </div>

        <p style={{ fontSize: 13.5, color: 'var(--ink-secondary)', margin: 0, lineHeight: 1.55 }}>
          Masukkan PIN 6 digit lama anda sekali sahaja untuk menyahsulit rekod sedia ada
          dan menaik taraf ke mod penyulitan automatik E2EE merentas Android &amp; Desktop.
        </p>

        {error && <div className="alert-banner error">{error}</div>}

        <div className="field-group">
          <label className="field-label" htmlFor="legacy-pin-input">
            PIN Keselamatan Lama (6 Digit)
          </label>
          <input
            id="legacy-pin-input"
            type="password"
            inputMode="numeric"
            maxLength={6}
            className="input"
            placeholder="••••••"
            value={pin}
            onChange={(e) => setPin(e.target.value.replace(/\D/g, '').slice(0, 6))}
            autoFocus
          />
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10 }}>
          <button
            type="button"
            className="btn btn-outline"
            onClick={onSignOut}
            disabled={busy}
          >
            <LogOut size={15} />
            <span>Log Keluar</span>
          </button>
          <button
            type="submit"
            className="btn btn-primary"
            disabled={busy || pin.length !== 6}
          >
            {busy ? (
              <>
                <Loader2 size={15} className="spin" />
                <span>Menyahsulit...</span>
              </>
            ) : (
              <span>Buka &amp; Naik Taraf</span>
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
