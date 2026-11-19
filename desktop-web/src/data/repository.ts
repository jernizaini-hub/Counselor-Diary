import {
  collection,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  getDocs,
  onSnapshot,
  serverTimestamp,
  setDoc,
  type DocumentData,
  type Unsubscribe,
} from 'firebase/firestore';
import { db } from '../config/firebase';
import {
  decryptString,
  encryptString,
  ENCRYPTION_VERSION,
} from '../crypto/e2ee';
import {
  normalizeCategory,
  type CounselingRecord,
  type EncryptedCounselingRecordDto,
  type VaultConfigDto,
} from '../models/counseling';

export const VAULT_DOC_ID = 'vaultConfig';

function vaultDocRef(userId: string) {
  return doc(db, 'users', userId, 'security', VAULT_DOC_ID);
}

function recordsColRef(userId: string) {
  return collection(db, 'users', userId, 'records');
}

function parseVaultConfig(id: string, data: DocumentData): VaultConfigDto {
  return {
    id,
    userId: String(data.userId ?? ''),
    pinHash: String(data.pinHash ?? ''),
    kdfSalt: String(data.kdfSalt ?? ''),
    wrappedMasterKey: String(data.wrappedMasterKey ?? ''),
    wrappedMasterKeyIv: String(data.wrappedMasterKeyIv ?? ''),
    totpEnabled: Boolean(data.totpEnabled),
    wrappedTotpSecret: data.wrappedTotpSecret ?? null,
    wrappedTotpIv: data.wrappedTotpIv ?? null,
    recoveryCodeHash: data.recoveryCodeHash ?? null,
    createdAt: data.createdAt ?? null,
    updatedAt: data.updatedAt ?? null,
  };
}

function parseEncryptedRecord(
  id: string,
  data: DocumentData
): EncryptedCounselingRecordDto {
  return {
    id,
    userId: String(data.userId ?? ''),
    category: String(data.category ?? 'KAUNSELING_INDIVIDU'),
    sessionDateMillis: Number(data.sessionDateMillis ?? 0),
    dayName: String(data.dayName ?? ''),
    timeRange: String(data.timeRange ?? ''),
    durationMinutes: Number(data.durationMinutes ?? 0),
    hasAiSummary: Boolean(data.hasAiSummary),
    encryptionVersion: String(data.encryptionVersion ?? ENCRYPTION_VERSION),
    encryptedPayload: String(data.encryptedPayload ?? ''),
    payloadIv: String(data.payloadIv ?? ''),
    createdAt: data.createdAt ?? null,
    updatedAt: data.updatedAt ?? null,
  };
}

export async function getVaultConfig(userId: string): Promise<VaultConfigDto | null> {
  const snap = await getDoc(vaultDocRef(userId));
  if (!snap.exists()) return null;
  return parseVaultConfig(snap.id, snap.data());
}

export function subscribeVaultConfig(
  userId: string,
  onData: (config: VaultConfigDto | null) => void,
  onError: (err: Error) => void
): Unsubscribe {
  return onSnapshot(
    vaultDocRef(userId),
    (snap) => {
      if (!snap.exists()) {
        onData(null);
      } else {
        onData(parseVaultConfig(snap.id, snap.data()));
      }
    },
    (err) => onError(err)
  );
}

export async function saveVaultConfig(
  userId: string,
  config: VaultConfigDto,
  isNew: boolean
): Promise<void> {
  const ref = vaultDocRef(userId);
  if (isNew) {
    const payload: Record<string, unknown> = {
      userId,
      pinHash: config.pinHash,
      kdfSalt: config.kdfSalt,
      wrappedMasterKey: config.wrappedMasterKey,
      wrappedMasterKeyIv: config.wrappedMasterKeyIv,
      totpEnabled: config.totpEnabled,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    };
    if (config.wrappedTotpSecret) payload.wrappedTotpSecret = config.wrappedTotpSecret;
    if (config.wrappedTotpIv) payload.wrappedTotpIv = config.wrappedTotpIv;
    if (config.recoveryCodeHash) payload.recoveryCodeHash = config.recoveryCodeHash;
    await setDoc(ref, payload);
  } else {
    if (!config.createdAt) {
      throw new Error('VaultConfig createdAt tidak boleh null semasa kemas kini.');
    }
    const updatePayload: Record<string, unknown> = {
      userId,
      pinHash: config.pinHash,
      kdfSalt: config.kdfSalt,
      wrappedMasterKey: config.wrappedMasterKey,
      wrappedMasterKeyIv: config.wrappedMasterKeyIv,
      totpEnabled: config.totpEnabled,
      wrappedTotpSecret: config.wrappedTotpSecret ?? deleteField(),
      wrappedTotpIv: config.wrappedTotpIv ?? deleteField(),
      recoveryCodeHash: config.recoveryCodeHash ?? deleteField(),
      createdAt: config.createdAt,
      updatedAt: serverTimestamp(),
    };
    await setDoc(ref, updatePayload);
  }
}

export function subscribeEncryptedRecords(
  userId: string,
  onData: (dtos: EncryptedCounselingRecordDto[]) => void,
  onError: (err: Error) => void
): Unsubscribe {
  return onSnapshot(
    recordsColRef(userId),
    (snapshot) => {
      const list = snapshot.docs.map((d) => parseEncryptedRecord(d.id, d.data()));
      onData(list);
    },
    (err) => onError(err)
  );
}

export async function getEncryptedRecords(
  userId: string
): Promise<EncryptedCounselingRecordDto[]> {
  const snapshot = await getDocs(recordsColRef(userId));
  return snapshot.docs.map((d) => parseEncryptedRecord(d.id, d.data()));
}

export function buildSensitivePayloadJson(record: CounselingRecord): string {
  return JSON.stringify({
    clientName: record.clientName,
    className: record.className,
    organizer: record.organizer,
    guardianName: record.guardianName,
    guardianPhone: record.guardianPhone,
    notes: record.notes,
    summaryMatter: record.summaryMatter,
    summaryQuestions: record.summaryQuestions,
    programStrengthsWeaknesses: record.programStrengthsWeaknesses,
    summaryIntervention: record.summaryIntervention,
  });
}

export function buildFirestoreRecordDocument(
  userId: string,
  record: CounselingRecord,
  encryptedPayload: string,
  payloadIv: string,
  isNew: boolean
): Record<string, unknown> {
  const hasSummary =
    record.category !== 'KELAS_GANTI' &&
    (record.summaryMatter.trim().length > 0 ||
      record.summaryQuestions.trim().length > 0 ||
      record.programStrengthsWeaknesses.trim().length > 0 ||
      record.summaryIntervention.trim().length > 0);

  const clampedDuration = Math.max(0, Math.min(1440, Math.round(record.durationMinutes)));

  if (!isNew && !record.createdAt) {
    throw new Error('createdAt mesti wujud untuk kemas kini rekod.');
  }

  return {
    userId,
    category: record.category,
    sessionDateMillis: Math.max(0, Math.floor(record.sessionDateMillis)),
    dayName: record.dayName.trim() || 'Isnin',
    timeRange: record.timeRange.trim() || '09:00 - 10:00',
    durationMinutes: clampedDuration,
    hasAiSummary: hasSummary,
    encryptionVersion: ENCRYPTION_VERSION,
    encryptedPayload,
    payloadIv,
    createdAt: isNew ? serverTimestamp() : record.createdAt,
    updatedAt: serverTimestamp(),
  };
}

export async function saveRecord(
  userId: string,
  record: CounselingRecord,
  masterKey: CryptoKey
): Promise<string> {
  const col = recordsColRef(userId);
  const isNew = !record.id.trim();
  const ref = isNew ? doc(col) : doc(col, record.id);

  const sensitiveJson = buildSensitivePayloadJson(record);
  const encrypted = await encryptString(sensitiveJson, masterKey);
  const payload = buildFirestoreRecordDocument(
    userId,
    record,
    encrypted.ciphertextBase64,
    encrypted.ivBase64,
    isNew
  );

  await setDoc(ref, payload);
  return ref.id;
}

export async function deleteRecord(userId: string, recordId: string): Promise<void> {
  await deleteDoc(doc(recordsColRef(userId), recordId));
}

export async function decryptRecord(
  dto: EncryptedCounselingRecordDto,
  masterKey: CryptoKey
): Promise<CounselingRecord> {
  const plaintext = await decryptString(dto.encryptedPayload, dto.payloadIv, masterKey);
  const json = JSON.parse(plaintext) as Record<string, unknown>;
  return {
    id: dto.id,
    userId: dto.userId,
    category: normalizeCategory(dto.category),
    clientName: String(json.clientName ?? ''),
    className: String(json.className ?? ''),
    organizer: String(json.organizer ?? ''),
    guardianName: String(json.guardianName ?? ''),
    guardianPhone: String(json.guardianPhone ?? ''),
    sessionDateMillis: dto.sessionDateMillis,
    dayName: dto.dayName,
    timeRange: dto.timeRange,
    durationMinutes: dto.durationMinutes,
    notes: String(json.notes ?? ''),
    summaryMatter: String(json.summaryMatter ?? ''),
    summaryQuestions: String(json.summaryQuestions ?? ''),
    programStrengthsWeaknesses: String(json.programStrengthsWeaknesses ?? ''),
    summaryIntervention: String(json.summaryIntervention ?? ''),
    hasAiSummary: dto.hasAiSummary,
    encryptionVersion: dto.encryptionVersion,
    createdAt: dto.createdAt,
    updatedAt: dto.updatedAt,
  };
}

export function buildEncryptedCloudBackupJson(
  userId: string,
  userEmail: string,
  dtos: EncryptedCounselingRecordDto[]
): string {
  const root = {
    app: 'Diari Kaunselor',
    userId,
    userEmail,
    exportedAtMillis: Date.now(),
    encryption: ENCRYPTION_VERSION,
    records: dtos.map((dto) => ({
      id: dto.id,
      category: dto.category,
      sessionDateMillis: dto.sessionDateMillis,
      dayName: dto.dayName,
      timeRange: dto.timeRange,
      durationMinutes: dto.durationMinutes,
      hasAiSummary: dto.hasAiSummary,
      encryptionVersion: dto.encryptionVersion,
      encryptedPayload: dto.encryptedPayload,
      payloadIv: dto.payloadIv,
    })),
  };
  return JSON.stringify(root, null, 2);
}
