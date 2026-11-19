import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { onAuthStateChanged, type User } from 'firebase/auth';
import { CheckCircle2, Loader2, RefreshCw, ShieldAlert } from 'lucide-react';
import {
  auth,
  completePendingRedirectSignIn,
  formatAuthErrorMalay,
  signInWithGoogleDesktop,
  signOutDesktop,
} from './config/firebase';
import {
  autoVaultSecret,
  deriveKeyFromSecret,
  generateMasterKey,
  generateSaltBase64,
  hashPinVerifier,
  unwrapSecretKey,
  wrapSecretKey,
} from './crypto/e2ee';
import {
  buildEncryptedCloudBackupJson,
  decryptRecord,
  deleteRecord,
  getEncryptedRecords,
  getVaultConfig,
  saveRecord,
  saveVaultConfig,
  subscribeEncryptedRecords,
  subscribeVaultConfig,
} from './data/repository';
import {
  InvalidGeminiApiKeyError,
  MissingGeminiApiKeyError,
  clearStoredGeminiApiKey,
  generateCounselingSummary,
  loadStoredGeminiApiKey,
  maskGeminiKey,
  saveStoredGeminiApiKey,
  verifyGeminiApiKey,
} from './data/geminiService';
import { downloadBlob } from './export/reportExporter';
import {
  computeDashboardStats,
  createDefaultDraft,
  formatMalayDateTime,
  type ActivityCategory,
  type AiSummaryResult,
  type CounselingRecord,
  type EncryptedCounselingRecordDto,
  type StatsPeriod,
  type VaultConfigDto,
} from './models/counseling';
import { AuthScreen } from './components/AuthScreen';
import { DashboardView } from './components/DashboardView';
import { LegacyPinMigrationModal } from './components/LegacyPinMigrationModal';
import { RecordsWorkspaceView } from './components/RecordsWorkspaceView';
import {
  SecurityBackupView,
  type KeyTestState,
} from './components/SecurityBackupView';
import { Sidebar, type DesktopTab } from './components/Sidebar';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

type VaultStatus =
  | { state: 'idle' }
  | { state: 'loading' }
  | { state: 'legacy_pin'; config: VaultConfigDto }
  | { state: 'unlocked'; config: VaultConfigDto; masterKey: CryptoKey }
  | { state: 'error'; message: string };

export function App() {
  const [user, setUser] = useState<User | null>(null);
  const [authReady, setAuthReady] = useState(false);
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);

  const [vault, setVault] = useState<VaultStatus>({ state: 'idle' });
  const [legacyPinBusy, setLegacyPinBusy] = useState(false);
  const [legacyPinError, setLegacyPinError] = useState<string | null>(null);

  const [encryptedDtos, setEncryptedDtos] = useState<EncryptedCounselingRecordDto[]>([]);
  const [records, setRecords] = useState<CounselingRecord[]>([]);
  const [undecryptableCount, setUndecryptableCount] = useState(0);
  const [syncStatus, setSyncStatus] = useState<'syncing' | 'synced' | 'error'>('syncing');
  const [syncLabel, setSyncLabel] = useState('Menyambung ke awan...');

  const [activeTab, setActiveTab] = useState<DesktopTab>('dashboard');
  const [statsPeriod, setStatsPeriod] = useState<StatsPeriod>('MONTH');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<ActivityCategory | null>(null);

  const [activeDraft, setActiveDraft] = useState<CounselingRecord | null>(null);
  const [isDirty, setIsDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const [savedGeminiKey, setSavedGeminiKey] = useState('');
  const [keyTestState, setKeyTestState] = useState<KeyTestState>({ status: 'idle' });
  const [aiLoading, setAiLoading] = useState(false);
  const [aiError, setAiError] = useState<{
    message: string;
    requiresApiKeySetup: boolean;
  } | null>(null);

  const [theme, setTheme] = useState<'light' | 'dark'>(() => {
    const stored = localStorage.getItem('diari_kaunselor_theme');
    return stored === 'dark' ? 'dark' : 'light';
  });

  const [installPrompt, setInstallPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const toastTimerRef = useRef<number | null>(null);

  const notify = useCallback((msg: string) => {
    setToast(msg);
    if (toastTimerRef.current) window.clearTimeout(toastTimerRef.current);
    toastTimerRef.current = window.setTimeout(() => setToast(null), 4000);
  }, []);

  // Theme sync
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('diari_kaunselor_theme', theme);
  }, [theme]);

  // PWA install prompt listener
  useEffect(() => {
    const handler = (e: Event) => {
      e.preventDefault();
      setInstallPrompt(e as BeforeInstallPromptEvent);
    };
    window.addEventListener('beforeinstallprompt', handler);
    return () => window.removeEventListener('beforeinstallprompt', handler);
  }, []);

  const handleInstallPwa = async () => {
    if (!installPrompt) return;
    await installPrompt.prompt();
    const choice = await installPrompt.userChoice;
    if (choice.outcome === 'accepted') {
      notify('Diari Kaunselor sedang dipasang sebagai aplikasi desktop Windows.');
      setInstallPrompt(null);
    }
  };

  // Auth listener
  useEffect(() => {
    void completePendingRedirectSignIn();
    const unsub = onAuthStateChanged(auth, (nextUser) => {
      setUser(nextUser);
      setAuthReady(true);
      setAuthLoading(false);
      if (!nextUser) {
        setVault({ state: 'idle' });
        setEncryptedDtos([]);
        setRecords([]);
        setUndecryptableCount(0);
        setActiveDraft(null);
        setIsDirty(false);
        setSavedGeminiKey('');
      } else {
        setSavedGeminiKey(loadStoredGeminiApiKey(nextUser.uid));
      }
    });
    return () => unsub();
  }, []);

  // Initialize / Unlock E2EE Vault matching Android CounselorViewModel byte-for-byte
  const initializeVault = useCallback(async (uid: string) => {
    setVault({ state: 'loading' });
    setSyncStatus('syncing');
    setSyncLabel('Membuka peti besi E2EE...');
    try {
      const serverConfig = await getVaultConfig(uid);
      const secret = autoVaultSecret(uid);

      if (!serverConfig) {
        const salt = generateSaltBase64();
        const [kek, pinHash, masterKey] = await Promise.all([
          deriveKeyFromSecret(secret, salt, 'kek'),
          hashPinVerifier(secret, salt),
          generateMasterKey(),
        ]);
        const wrapped = await wrapSecretKey(masterKey, kek);
        const newConfig: VaultConfigDto = {
          id: 'vaultConfig',
          userId: uid,
          pinHash,
          kdfSalt: salt,
          wrappedMasterKey: wrapped.ciphertextBase64,
          wrappedMasterKeyIv: wrapped.ivBase64,
          totpEnabled: false,
          createdAt: null,
          updatedAt: null,
        };
        await saveVaultConfig(uid, newConfig, true);
        setVault({ state: 'unlocked', config: newConfig, masterKey });
        return;
      }

      // Attempt automatic unwrap (exact parity with Android)
      try {
        const kek = await deriveKeyFromSecret(secret, serverConfig.kdfSalt, 'kek');
        const masterKey = await unwrapSecretKey(
          serverConfig.wrappedMasterKey,
          serverConfig.wrappedMasterKeyIv,
          kek
        );
        setVault({ state: 'unlocked', config: serverConfig, masterKey });
      } catch {
        // Vault was created with legacy 6-digit PIN
        setVault({ state: 'legacy_pin', config: serverConfig });
      }
    } catch (err: unknown) {
      const msg = (err as { message?: string })?.message ?? 'Gagal membuka peti besi E2EE.';
      setVault({ state: 'error', message: msg });
      setSyncStatus('error');
      setSyncLabel('Gagal menyambung ke peti besi');
    }
  }, []);

  useEffect(() => {
    if (!user) return;
    void initializeVault(user.uid);
    const unsub = subscribeVaultConfig(
      user.uid,
      (latestConfig) => {
        if (!latestConfig) return;
        setVault((current) => {
          if (
            current.state === 'unlocked' &&
            current.config.wrappedMasterKey === latestConfig.wrappedMasterKey
          ) {
            return current;
          }
          // Re-unwrap automatically when Android updates vaultConfig in cloud
          void (async () => {
            try {
              const kek = await deriveKeyFromSecret(
                autoVaultSecret(user.uid),
                latestConfig.kdfSalt,
                'kek'
              );
              const masterKey = await unwrapSecretKey(
                latestConfig.wrappedMasterKey,
                latestConfig.wrappedMasterKeyIv,
                kek
              );
              setVault({ state: 'unlocked', config: latestConfig, masterKey });
            } catch {
              // ignore if still legacy PIN
            }
          })();
          return current;
        });
      },
      () => undefined
    );
    return () => unsub();
  }, [user, initializeVault]);

  // Real-time Firestore listener for encrypted records
  useEffect(() => {
    if (!user || vault.state !== 'unlocked') return;

    setSyncStatus('syncing');
    const unsub = subscribeEncryptedRecords(
      user.uid,
      (dtos) => {
        setEncryptedDtos(dtos);
        setSyncStatus('synced');
        setSyncLabel(
          `${dtos.length} rekod • Dikemas kini ${formatMalayDateTime(Date.now())}`
        );
      },
      (err) => {
        setSyncStatus('error');
        setSyncLabel(err.message || 'Ralat sambungan awan');
      }
    );

    return () => unsub();
  }, [user, vault.state]);

  // Decrypt all records whenever snapshot updates
  useEffect(() => {
    if (vault.state !== 'unlocked') return;
    let cancelled = false;
    const masterKey = vault.masterKey;

    (async () => {
      const decryptedList: CounselingRecord[] = [];
      for (const dto of encryptedDtos) {
        try {
          const rec = await decryptRecord(dto, masterKey);
          decryptedList.push(rec);
        } catch (err) {
          console.error(`Gagal menyahsulit rekod ${dto.id}:`, err);
        }
      }
      decryptedList.sort((a, b) => b.sessionDateMillis - a.sessionDateMillis);
      if (!cancelled) {
        setRecords(decryptedList);
        setUndecryptableCount(Math.max(0, encryptedDtos.length - decryptedList.length));
        // Keep activeDraft synced if clean & updated from Android
        setActiveDraft((current) => {
          if (!current || !current.id || isDirty) return current;
          const updated = decryptedList.find((r) => r.id === current.id);
          return updated ?? current;
        });
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [encryptedDtos, vault, isDirty]);

  const dashboardStats = useMemo(
    () => computeDashboardStats(records, statsPeriod),
    [records, statsPeriod]
  );

  const handleSignIn = async () => {
    setAuthLoading(true);
    setAuthError(null);
    try {
      await signInWithGoogleDesktop();
    } catch (err) {
      setAuthError(formatAuthErrorMalay(err));
      setAuthLoading(false);
    }
  };

  const handleSignOut = async () => {
    await signOutDesktop();
    notify('Anda telah log keluar dengan selamat.');
  };

  const handleLegacyPinMigration = async (oldPin: string) => {
    if (!user || vault.state !== 'legacy_pin') return;
    setLegacyPinBusy(true);
    setLegacyPinError(null);
    try {
      const config = vault.config;
      const oldKek = await deriveKeyFromSecret(oldPin, config.kdfSalt, 'kek');
      let masterKey: CryptoKey;
      try {
        masterKey = await unwrapSecretKey(
          config.wrappedMasterKey,
          config.wrappedMasterKeyIv,
          oldKek
        );
      } catch {
        throw new Error('PIN lama 6 digit tidak tepat. Sila cuba semula.');
      }

      const secret = autoVaultSecret(user.uid);
      const newSalt = generateSaltBase64();
      const [newKek, newPinHash] = await Promise.all([
        deriveKeyFromSecret(secret, newSalt, 'kek'),
        hashPinVerifier(secret, newSalt),
      ]);
      const wrapped = await wrapSecretKey(masterKey, newKek);
      const updatedConfig: VaultConfigDto = {
        ...config,
        pinHash: newPinHash,
        kdfSalt: newSalt,
        wrappedMasterKey: wrapped.ciphertextBase64,
        wrappedMasterKeyIv: wrapped.ivBase64,
        totpEnabled: false,
        wrappedTotpSecret: null,
        wrappedTotpIv: null,
        recoveryCodeHash: null,
      };
      await saveVaultConfig(user.uid, updatedConfig, false);
      setVault({ state: 'unlocked', config: updatedConfig, masterKey });
      notify('Peti besi berjaya dinaik taraf ke E2EE automatik.');
    } catch (err: unknown) {
      setLegacyPinError(
        (err as { message?: string })?.message ?? 'Gagal menyahsulit rekod lama.'
      );
    } finally {
      setLegacyPinBusy(false);
    }
  };

  const handleCreateNewRecord = useCallback(
    (category: ActivityCategory = 'KAUNSELING_INDIVIDU') => {
      if (!user) return;
      setActiveDraft(createDefaultDraft(category, user.uid));
      setIsDirty(true);
      setAiError(null);
      setActiveTab('records');
    },
    [user]
  );

  const handleOpenRecord = useCallback((record: CounselingRecord) => {
    setActiveDraft({ ...record });
    setIsDirty(false);
    setAiError(null);
    setActiveTab('records');
  }, []);

  const handleDraftChange = useCallback(
    (updater: (prev: CounselingRecord) => CounselingRecord) => {
      setActiveDraft((prev) => (prev ? updater(prev) : prev));
      setIsDirty(true);
    },
    []
  );

  const handleSaveDraft = useCallback(async () => {
    if (!user || vault.state !== 'unlocked' || !activeDraft) return;
    if (activeDraft.category === 'KELAS_GANTI' && !activeDraft.className.trim()) {
      notify('Sila masukkan Nama Kelas / Tingkatan terlebih dahulu.');
      return;
    }
    if (activeDraft.category !== 'KELAS_GANTI' && !activeDraft.clientName.trim()) {
      const label =
        activeDraft.category === 'PROGRAM'
          ? 'Nama Program'
          : activeDraft.category === 'MESYUARAT'
          ? 'Nama Mesyuarat'
          : 'Nama Klien / Murid';
      notify(`Sila masukkan ${label} terlebih dahulu.`);
      return;
    }
    if (activeDraft.durationMinutes <= 0) {
      notify('Masa tamat mesti selepas masa mula.');
      return;
    }

    setSaving(true);
    setSyncStatus('syncing');
    try {
      const savedId = await saveRecord(user.uid, activeDraft, vault.masterKey);
      setActiveDraft((prev) => (prev ? { ...prev, id: savedId } : prev));
      setIsDirty(false);
      notify('Rekod berjaya disulitkan dan disegerakkan ke Android & awan.');
    } catch (err: unknown) {
      const msg = (err as { message?: string })?.message ?? 'Gagal menyimpan rekod.';
      setSyncStatus('error');
      notify(`Ralat simpan: ${msg}`);
    } finally {
      setSaving(false);
    }
  }, [user, vault, activeDraft, notify]);

  const handleDeleteDraft = async () => {
    if (!user || !activeDraft?.id) return;
    setDeleting(true);
    try {
      await deleteRecord(user.uid, activeDraft.id);
      setActiveDraft(null);
      setIsDirty(false);
      notify('Rekod telah dipadam daripada awan & peranti.');
    } catch (err: unknown) {
      notify(`Gagal memadam: ${(err as { message?: string })?.message ?? 'Ralat'}`);
    } finally {
      setDeleting(false);
    }
  };

  const handleGenerateAi = async (): Promise<AiSummaryResult | null> => {
    if (!activeDraft || !activeDraft.notes.trim()) {
      setAiError({
        message: 'Sila masukkan transkrip atau catatan ringkas terlebih dahulu.',
        requiresApiKeySetup: false,
      });
      return null;
    }
    setAiLoading(true);
    setAiError(null);
    try {
      const result = await generateCounselingSummary(activeDraft, savedGeminiKey);
      setActiveDraft((prev) =>
        prev
          ? {
              ...prev,
              summaryMatter: result.matter,
              summaryQuestions: result.questions,
              programStrengthsWeaknesses: result.strengthsWeaknesses,
              summaryIntervention: result.intervention,
              hasAiSummary: true,
            }
          : prev
      );
      setIsDirty(true);
      notify('Rumusan AI berjaya dijana. Tekan Simpan untuk menyimpan ke awan.');
      return result;
    } catch (err: unknown) {
      const requiresSetup =
        err instanceof MissingGeminiApiKeyError ||
        err instanceof InvalidGeminiApiKeyError;
      setAiError({
        message: (err as { message?: string })?.message ?? 'Gagal menjana rumusan AI.',
        requiresApiKeySetup: requiresSetup,
      });
      return null;
    } finally {
      setAiLoading(false);
    }
  };

  const handleSaveAndVerifyGeminiKey = async (rawKey: string) => {
    if (!user) return;
    const trimmed = rawKey.trim();
    setKeyTestState({ status: 'testing' });
    const res = await verifyGeminiApiKey(trimmed);
    if (res.status === 'valid') {
      saveStoredGeminiApiKey(user.uid, trimmed);
      setSavedGeminiKey(trimmed);
      setAiError(null);
      setKeyTestState({
        status: 'success',
        message: `Kunci Gemini API (${maskGeminiKey(trimmed)}) sah dan sedia digunakan (${res.activeModel}).`,
      });
      notify('Kunci Gemini API berjaya disahkan.');
    } else if (res.status === 'invalid') {
      setKeyTestState({ status: 'error', message: res.message });
    } else {
      saveStoredGeminiApiKey(user.uid, trimmed);
      setSavedGeminiKey(trimmed);
      setKeyTestState({
        status: 'error',
        message: `Kunci disimpan setempat, tetapi ujian gagal: ${res.message}`,
      });
    }
  };

  const handleClearGeminiKey = () => {
    if (!user) return;
    clearStoredGeminiApiKey(user.uid);
    setSavedGeminiKey('');
    setKeyTestState({ status: 'idle' });
    notify('Kunci Gemini API telah dipadam daripada pelayar ini.');
  };

  const handleVerifyCloudSync = async () => {
    if (!user) return;
    setSyncStatus('syncing');
    try {
      const fresh = await getEncryptedRecords(user.uid);
      setEncryptedDtos(fresh);
      setSyncStatus('synced');
      setSyncLabel(
        `${fresh.length} rekod • Disahkan ${formatMalayDateTime(Date.now())}`
      );
      notify(`Segerak awan disahkan: ${fresh.length} rekod tersulit aktif.`);
    } catch (err: unknown) {
      setSyncStatus('error');
      notify(`Ralat pengesahan awan: ${(err as { message?: string })?.message ?? ''}`);
    }
  };

  const handleExportEncryptedJson = () => {
    if (!user) return;
    const json = buildEncryptedCloudBackupJson(
      user.uid,
      user.email ?? '',
      encryptedDtos
    );
    const blob = new Blob([json], { type: 'application/json;charset=utf-8' });
    const stamp = new Date().toISOString().slice(0, 10);
    downloadBlob(blob, `Sandaran_E2EE_DiariKaunselor_${stamp}.json`);
    notify(`Sandaran E2EE JSON (${encryptedDtos.length} rekod) dimuat turun.`);
  };

  // Global desktop keyboard shortcuts: Ctrl+S (save), Ctrl+N (new record)
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (!(e.ctrlKey || e.metaKey)) return;
      const key = e.key.toLowerCase();
      if (key === 's' && activeTab === 'records' && activeDraft) {
        e.preventDefault();
        void handleSaveDraft();
      } else if (key === 'n' && user && vault.state === 'unlocked') {
        e.preventDefault();
        handleCreateNewRecord();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [activeTab, activeDraft, user, vault.state, handleSaveDraft, handleCreateNewRecord]);

  if (!authReady) {
    return (
      <div className="auth-page">
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, color: 'var(--primary)' }}>
          <Loader2 size={24} className="spin" />
          <span style={{ fontWeight: 700 }}>Memuatkan Diari Kaunselor...</span>
        </div>
      </div>
    );
  }

  if (!user) {
    return (
      <AuthScreen
        loading={authLoading}
        error={authError}
        canInstallPwa={installPrompt !== null}
        onSignIn={() => void handleSignIn()}
        onInstallPwa={() => void handleInstallPwa()}
      />
    );
  }

  return (
    <div className="app-shell">
      <Sidebar
        activeTab={activeTab}
        onSelectTab={setActiveTab}
        onNewRecord={() => handleCreateNewRecord()}
        recordCount={records.length}
        hasGeminiKey={savedGeminiKey.length > 0}
        syncStatus={syncStatus}
        syncLabel={syncLabel}
        user={user}
        theme={theme}
        onToggleTheme={() => setTheme((t) => (t === 'light' ? 'dark' : 'light'))}
        canInstallPwa={installPrompt !== null}
        onInstallPwa={() => void handleInstallPwa()}
        onSignOut={() => void handleSignOut()}
      />

      <main className="main-content">
        {vault.state === 'loading' && (
          <div className="empty-state">
            <Loader2 size={34} className="spin" style={{ color: 'var(--primary)' }} />
            <div style={{ fontWeight: 700, color: 'var(--ink)' }}>
              Menyahsulit Peti Besi E2EE Anda...
            </div>
            <div style={{ fontSize: 13 }}>
              Menyambung ke pangkalan data Cloud Firestore yang sama dengan aplikasi Android.
            </div>
          </div>
        )}

        {vault.state === 'error' && (
          <div className="empty-state">
            <div className="alert-banner error" style={{ maxWidth: 520 }}>
              {vault.message}
            </div>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => void initializeVault(user.uid)}
            >
              Cuba Semula
            </button>
          </div>
        )}

        {vault.state === 'unlocked' && undecryptableCount > 0 && (
          <div
            style={{
              padding: '12px 24px',
              background: 'var(--secondary-soft)',
              borderBottom: '1px solid var(--outline)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 14,
              flexWrap: 'wrap',
              fontSize: 13,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <ShieldAlert size={18} style={{ color: 'var(--secondary)', flexShrink: 0 }} />
              <span>
                <strong>{undecryptableCount} rekod tersulit</strong> ditemui di awan daripada
                aplikasi Android, menunggu penyelarasan kunci E2EE. Sila kemas kini dan buka
                aplikasi Android anda sekali (<strong>jangan nyahpasang / uninstall</strong>)
                supaya kunci disegerakkan secara automatik.
              </span>
            </div>
            <button
              type="button"
              className="btn btn-outline"
              onClick={() => void initializeVault(user.uid)}
            >
              <RefreshCw size={14} />
              <span>Semak Semula Kunci</span>
            </button>
          </div>
        )}

        {vault.state === 'unlocked' && activeTab === 'dashboard' && (
          <DashboardView
            counselorName={user.displayName || 'Kaunselor'}
            stats={dashboardStats}
            period={statsPeriod}
            onChangePeriod={setStatsPeriod}
            records={records}
            onOpenRecord={handleOpenRecord}
            onNewRecordWithCategory={(cat) => handleCreateNewRecord(cat)}
            onFilterByCategory={(cat) => {
              setSelectedCategory(cat);
              setActiveTab('records');
            }}
          />
        )}

        {vault.state === 'unlocked' && activeTab === 'records' && (
          <RecordsWorkspaceView
            records={records}
            searchQuery={searchQuery}
            onChangeSearch={setSearchQuery}
            selectedCategory={selectedCategory}
            onChangeCategory={setSelectedCategory}
            activeDraft={activeDraft}
            isDirty={isDirty}
            saving={saving}
            deleting={deleting}
            aiLoading={aiLoading}
            aiError={aiError}
            counselorName={user.displayName || 'Kaunselor'}
            counselorEmail={user.email || ''}
            onSelectRecord={handleOpenRecord}
            onCreateNew={() => handleCreateNewRecord(selectedCategory ?? 'KAUNSELING_INDIVIDU')}
            onChangeDraft={handleDraftChange}
            onSave={() => void handleSaveDraft()}
            onDelete={() => void handleDeleteDraft()}
            onGenerateAi={handleGenerateAi}
            onOpenSecurityTab={() => setActiveTab('security')}
            onNotify={notify}
          />
        )}

        {vault.state === 'unlocked' && activeTab === 'security' && (
          <SecurityBackupView
            userEmail={user.email || ''}
            recordCount={records.length}
            lastSyncedLabel={syncLabel}
            savedGeminiKey={savedGeminiKey}
            keyTestState={keyTestState}
            onSaveAndVerifyKey={handleSaveAndVerifyGeminiKey}
            onClearKey={handleClearGeminiKey}
            onVerifyCloudSync={handleVerifyCloudSync}
            onExportEncryptedJson={handleExportEncryptedJson}
            canInstallPwa={installPrompt !== null}
            onInstallPwa={() => void handleInstallPwa()}
          />
        )}
      </main>

      {vault.state === 'legacy_pin' && (
        <LegacyPinMigrationModal
          busy={legacyPinBusy}
          error={legacyPinError}
          onMigrate={(pin) => void handleLegacyPinMigration(pin)}
          onSignOut={() => void handleSignOut()}
        />
      )}

      {toast && (
        <div className="toast" role="status" aria-live="polite">
          <CheckCircle2 size={17} style={{ color: '#5EEAD4', flexShrink: 0 }} />
          <span>{toast}</span>
        </div>
      )}
    </div>
  );
}
