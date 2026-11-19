import {
  BookLock,
  BookOpenText,
  Download,
  LayoutDashboard,
  LogOut,
  Moon,
  Plus,
  ShieldCheck,
  Sun,
} from 'lucide-react';
import type { User } from 'firebase/auth';

export type DesktopTab = 'dashboard' | 'records' | 'security';

interface SidebarProps {
  activeTab: DesktopTab;
  onSelectTab: (tab: DesktopTab) => void;
  onNewRecord: () => void;
  recordCount: number;
  hasGeminiKey: boolean;
  syncStatus: 'syncing' | 'synced' | 'error';
  syncLabel: string;
  user: User;
  theme: 'light' | 'dark';
  onToggleTheme: () => void;
  canInstallPwa: boolean;
  onInstallPwa: () => void;
  onSignOut: () => void;
}

export function Sidebar({
  activeTab,
  onSelectTab,
  onNewRecord,
  recordCount,
  hasGeminiKey,
  syncStatus,
  syncLabel,
  user,
  theme,
  onToggleTheme,
  canInstallPwa,
  onInstallPwa,
  onSignOut,
}: SidebarProps) {
  const initials = (user.displayName || user.email || 'K')
    .trim()
    .slice(0, 1)
    .toUpperCase();

  return (
    <aside className="sidebar" aria-label="Navigasi Utama">
      <div className="brand-box">
        <div className="brand-emblem" aria-hidden="true">
          <BookLock size={21} />
        </div>
        <div>
          <div className="brand-title">Diari Kaunselor</div>
          <div className="brand-sub">Desktop PWA • E2EE</div>
        </div>
      </div>

      <button
        type="button"
        className="btn-primary-cta"
        onClick={onNewRecord}
        title="Cipta Catatan Baharu (Ctrl+N)"
      >
        <Plus size={17} />
        <span>Catatan Baharu</span>
        <span className="kbd-hint">Ctrl+N</span>
      </button>

      <nav className="nav-section">
        <button
          type="button"
          className={`nav-item ${activeTab === 'dashboard' ? 'active' : ''}`}
          onClick={() => onSelectTab('dashboard')}
        >
          <LayoutDashboard size={18} />
          <span>Papan Pemuka</span>
        </button>

        <button
          type="button"
          className={`nav-item ${activeTab === 'records' ? 'active' : ''}`}
          onClick={() => onSelectTab('records')}
        >
          <BookOpenText size={18} />
          <span>Rekod &amp; Diari</span>
          <span className="nav-badge">{recordCount}</span>
        </button>

        <button
          type="button"
          className={`nav-item ${activeTab === 'security' ? 'active' : ''}`}
          onClick={() => onSelectTab('security')}
        >
          <ShieldCheck size={18} />
          <span>Keselamatan &amp; AI</span>
          {!hasGeminiKey && (
            <span
              className="nav-badge"
              style={{ background: 'var(--secondary-soft)', color: 'var(--secondary)' }}
            >
              AI
            </span>
          )}
        </button>
      </nav>

      <div className="sync-card">
        <div className="sync-row">
          <span
            className={`sync-dot ${
              syncStatus === 'syncing'
                ? 'syncing'
                : syncStatus === 'error'
                ? 'error'
                : ''
            }`}
          />
          <span>
            {syncStatus === 'syncing'
              ? 'Menyegerak Awan...'
              : syncStatus === 'error'
              ? 'Ralat Segerak'
              : 'Segerak Android Aktif'}
          </span>
        </div>
        <div style={{ fontSize: 11.5, color: 'var(--ink-muted)', lineHeight: 1.4 }}>
          {syncLabel}
        </div>
      </div>

      {canInstallPwa && (
        <button
          type="button"
          className="btn-install-pwa"
          onClick={onInstallPwa}
        >
          <Download size={15} />
          <span>Pasang Aplikasi Desktop</span>
        </button>
      )}

      <div className="user-footer">
        <div className="user-row">
          {user.photoURL ? (
            <img
              src={user.photoURL}
              alt={user.displayName || 'Profil Kaunselor'}
              className="user-avatar"
              referrerPolicy="no-referrer"
            />
          ) : (
            <div className="user-avatar">{initials}</div>
          )}
          <div className="user-meta">
            <div className="user-name">{user.displayName || 'Kaunselor'}</div>
            <div className="user-email">{user.email || ''}</div>
          </div>
        </div>

        <div className="sidebar-tools">
          <button
            type="button"
            className="btn-ghost"
            onClick={onToggleTheme}
            title={theme === 'light' ? 'Tukar ke Mod Gelap' : 'Tukar ke Mod Cerah'}
          >
            {theme === 'light' ? <Moon size={14} /> : <Sun size={14} />}
            <span>{theme === 'light' ? 'Gelap' : 'Cerah'}</span>
          </button>

          <button
            type="button"
            className="btn-ghost"
            onClick={onSignOut}
            title="Log Keluar Selamat"
          >
            <LogOut size={14} />
            <span>Keluar</span>
          </button>
        </div>
      </div>
    </aside>
  );
}
