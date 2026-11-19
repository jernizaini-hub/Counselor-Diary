import type { Timestamp } from 'firebase/firestore';

export type ActivityCategory =
  | 'KAUNSELING_INDIVIDU'
  | 'KAUNSELING_KELOMPOK'
  | 'PROGRAM'
  | 'MESYUARAT'
  | 'KELAS_GANTI'
  | 'ZIARAH_CAKNA'
  | 'KONSULTASI_IBUBAPA';

export interface CategoryMeta {
  key: ActivityCategory;
  displayName: string;
  shortLabel: string;
  isCounselingSession: boolean;
  accentHex: string;
  softBgHex: string;
  description: string;
}

export const ACTIVITY_CATEGORIES: CategoryMeta[] = [
  {
    key: 'KAUNSELING_INDIVIDU',
    displayName: 'Kaunseling Individu',
    shortLabel: 'Individu',
    isCounselingSession: true,
    accentHex: '#0D8A7B',
    softBgHex: '#E6F5F3',
    description: 'Sesi kaunseling satu-sama-satu bersama murid atau klien',
  },
  {
    key: 'KAUNSELING_KELOMPOK',
    displayName: 'Kaunseling Kelompok',
    shortLabel: 'Kelompok',
    isCounselingSession: true,
    accentHex: '#0F766E',
    softBgHex: '#E4F4F2',
    description: 'Sesi kaunseling berkumpulan bersama beberapa peserta',
  },
  {
    key: 'PROGRAM',
    displayName: 'Program Bimbingan',
    shortLabel: 'Program',
    isCounselingSession: false,
    accentHex: '#EA6A2B',
    softBgHex: '#FEF0E8',
    description: 'Ceramah, bengkel, kem atau program bimbingan terancang',
  },
  {
    key: 'MESYUARAT',
    displayName: 'Mesyuarat',
    shortLabel: 'Mesyuarat',
    isCounselingSession: false,
    accentHex: '#2563EB',
    softBgHex: '#EAF1FF',
    description: 'Mesyuarat pengurusan, jawatankuasa atau penyelarasan unit',
  },
  {
    key: 'KELAS_GANTI',
    displayName: 'Kelas Ganti',
    shortLabel: 'Kelas Ganti',
    isCounselingSession: false,
    accentHex: '#D97706',
    softBgHex: '#FEF5E4',
    description: 'Aktiviti ringkas pengisian kelas ganti (tanpa analisis AI)',
  },
  {
    key: 'ZIARAH_CAKNA',
    displayName: 'Ziarah Cakna',
    shortLabel: 'Ziarah Cakna',
    isCounselingSession: false,
    accentHex: '#9333EA',
    softBgHex: '#F4EAFF',
    description: 'Lawatan ke rumah murid dan perbincangan bersama penjaga',
  },
  {
    key: 'KONSULTASI_IBUBAPA',
    displayName: 'Konsultasi Ibu Bapa',
    shortLabel: 'Ibu Bapa',
    isCounselingSession: false,
    accentHex: '#DB2777',
    softBgHex: '#FDECF4',
    description: 'Perbincangan atau konsultasi khas bersama ibu bapa/penjaga',
  },
];

export const CATEGORY_MAP: Record<ActivityCategory, CategoryMeta> =
  ACTIVITY_CATEGORIES.reduce(
    (acc, item) => {
      acc[item.key] = item;
      return acc;
    },
    {} as Record<ActivityCategory, CategoryMeta>
  );

export function normalizeCategory(key: string): ActivityCategory {
  return key in CATEGORY_MAP ? (key as ActivityCategory) : 'KAUNSELING_INDIVIDU';
}

export interface CounselingRecord {
  id: string;
  userId: string;
  category: ActivityCategory;
  clientName: string;
  className: string;
  organizer: string;
  guardianName: string;
  guardianPhone: string;
  sessionDateMillis: number;
  dayName: string;
  timeRange: string;
  durationMinutes: number;
  notes: string;
  summaryMatter: string;
  summaryQuestions: string;
  programStrengthsWeaknesses: string;
  summaryIntervention: string;
  hasAiSummary: boolean;
  encryptionVersion: string;
  createdAt: Timestamp | null;
  updatedAt: Timestamp | null;
}

export interface EncryptedCounselingRecordDto {
  id: string;
  userId: string;
  category: string;
  sessionDateMillis: number;
  dayName: string;
  timeRange: string;
  durationMinutes: number;
  hasAiSummary: boolean;
  encryptionVersion: string;
  encryptedPayload: string;
  payloadIv: string;
  createdAt: Timestamp | null;
  updatedAt: Timestamp | null;
}

export interface VaultConfigDto {
  id: string;
  userId: string;
  pinHash: string;
  kdfSalt: string;
  wrappedMasterKey: string;
  wrappedMasterKeyIv: string;
  totpEnabled: boolean;
  wrappedTotpSecret?: string | null;
  wrappedTotpIv?: string | null;
  recoveryCodeHash?: string | null;
  createdAt: Timestamp | null;
  updatedAt: Timestamp | null;
}

export interface AiSummaryResult {
  matter: string;
  questions: string;
  intervention: string;
  strengthsWeaknesses: string;
}

export type StatsPeriod = 'WEEK' | 'MONTH' | 'ALL';

export const STATS_PERIODS: { key: StatsPeriod; label: string }[] = [
  { key: 'WEEK', label: 'Minggu Ini' },
  { key: 'MONTH', label: 'Bulan Ini' },
  { key: 'ALL', label: 'Semua Masa' },
];

export interface DailyTrendPoint {
  dayLabel: string;
  dateLabel: string;
  count: number;
}

export interface DashboardStats {
  totalRecords: number;
  counselingSessionsCount: number;
  activitiesCount: number;
  aiSummaryCount: number;
  totalMinutes: number;
  categoryCounts: Record<ActivityCategory, number>;
  weeklyTrend: DailyTrendPoint[];
}

export const DURATION_PRESETS = [
  { label: '15 min', minutes: 15 },
  { label: '30 min', minutes: 30 },
  { label: '45 min', minutes: 45 },
  { label: '1 jam', minutes: 60 },
  { label: '1 jam 30m', minutes: 90 },
];

const MALAY_DAYS = [
  'Ahad',
  'Isnin',
  'Selasa',
  'Rabu',
  'Khamis',
  'Jumaat',
  'Sabtu',
];

const MALAY_DAYS_SHORT = ['Ahd', 'Isn', 'Sel', 'Rab', 'Kha', 'Jum', 'Sab'];

const MALAY_MONTHS = [
  'Januari',
  'Februari',
  'Mac',
  'April',
  'Mei',
  'Jun',
  'Julai',
  'Ogos',
  'September',
  'Oktober',
  'November',
  'Disember',
];

export function getMalayDayName(dateMillis: number): string {
  const date = new Date(dateMillis);
  return MALAY_DAYS[date.getDay()] ?? 'Isnin';
}

export function formatMalayDate(dateMillis: number): string {
  const d = new Date(dateMillis);
  const dd = String(d.getDate()).padStart(2, '0');
  const month = MALAY_MONTHS[d.getMonth()] ?? 'Januari';
  return `${dd} ${month} ${d.getFullYear()}`;
}

export function formatMalayDateTime(dateMillis: number): string {
  const d = new Date(dateMillis);
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const hh = String(d.getHours()).padStart(2, '0');
  const min = String(d.getMinutes()).padStart(2, '0');
  return `${dd}/${mm}/${d.getFullYear()} ${hh}:${min}`;
}

export function toDateInputValue(dateMillis: number): string {
  const d = new Date(dateMillis);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function fromDateInputValue(value: string, fallbackMillis: number): number {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value.trim());
  if (!match) return fallbackMillis;
  const [, y, m, d] = match;
  const date = new Date(Number(y), Number(m) - 1, Number(d), 12, 0, 0, 0);
  return date.getTime();
}

export function formatTime24(hours: number, minutes: number): string {
  const clampedH = Math.max(0, Math.min(23, Math.floor(hours)));
  const clampedM = Math.max(0, Math.min(59, Math.floor(minutes)));
  return `${String(clampedH).padStart(2, '0')}:${String(clampedM).padStart(2, '0')}`;
}

export function timeStringToMinutes(timeStr: string): number | null {
  const match = /^(\d{1,2}):(\d{2})$/.exec(timeStr.trim());
  if (!match) return null;
  const h = Number(match[1]);
  const m = Number(match[2]);
  if (h < 0 || h > 23 || m < 0 || m > 59) return null;
  return h * 60 + m;
}

export function computeEndTime(startTime: string, durationMinutes: number): string {
  const startTotal = timeStringToMinutes(startTime);
  if (startTotal === null) return '09:45';
  const endTotal = Math.min(23 * 60 + 59, startTotal + Math.max(1, durationMinutes));
  return formatTime24(Math.floor(endTotal / 60), endTotal % 60);
}

export function calculateDurationMinutes(startTime: string, endTime: string): number {
  const start = timeStringToMinutes(startTime);
  const end = timeStringToMinutes(endTime);
  if (start === null || end === null) return 0;
  return end - start;
}

export function parseTimeRange(
  timeRange: string,
  fallbackDuration = 45
): { startTime: string; endTime: string } {
  const parts = timeRange.split('-').map((p) => p.trim());
  if (parts.length === 2) {
    const start = timeStringToMinutes(parts[0]);
    const end = timeStringToMinutes(parts[1]);
    if (start !== null && end !== null) {
      return {
        startTime: formatTime24(Math.floor(start / 60), start % 60),
        endTime: formatTime24(Math.floor(end / 60), end % 60),
      };
    }
  }
  const now = new Date();
  const startTime = formatTime24(now.getHours(), now.getMinutes());
  return {
    startTime,
    endTime: computeEndTime(startTime, fallbackDuration > 0 ? fallbackDuration : 45),
  };
}

export function createDefaultDraft(
  category: ActivityCategory = 'KAUNSELING_INDIVIDU',
  userId = ''
): CounselingRecord {
  const now = new Date();
  const startTime = formatTime24(now.getHours(), now.getMinutes());
  const endTime = computeEndTime(startTime, 45);
  const duration = Math.max(1, calculateDurationMinutes(startTime, endTime));
  const nowMillis = now.getTime();

  return {
    id: '',
    userId,
    category,
    clientName: '',
    className: '',
    organizer: '',
    guardianName: '',
    guardianPhone: '',
    sessionDateMillis: nowMillis,
    dayName: getMalayDayName(nowMillis),
    timeRange: `${startTime} - ${endTime}`,
    durationMinutes: duration,
    notes: '',
    summaryMatter: '',
    summaryQuestions: '',
    programStrengthsWeaknesses: '',
    summaryIntervention: '',
    hasAiSummary: false,
    encryptionVersion: 'AES-256-GCM-v1',
    createdAt: null,
    updatedAt: null,
  };
}

export function getRecordTitle(record: CounselingRecord): string {
  if (record.category === 'KELAS_GANTI') {
    return record.className.trim() || 'Kelas Ganti Tanpa Nama';
  }
  if (record.category === 'PROGRAM') {
    return record.clientName.trim() || 'Program Tanpa Tajuk';
  }
  if (record.category === 'MESYUARAT') {
    return record.clientName.trim() || 'Mesyuarat Tanpa Tajuk';
  }
  return record.clientName.trim() || 'Klien Tanpa Nama';
}

export function getRecordSubtitle(record: CounselingRecord): string {
  switch (record.category) {
    case 'PROGRAM':
      return [record.organizer, record.className].filter((v) => v.trim()).join(' • ');
    case 'KELAS_GANTI':
      return record.notes.trim().slice(0, 72);
    case 'MESYUARAT':
      return record.notes.trim().slice(0, 72);
    case 'ZIARAH_CAKNA':
    case 'KONSULTASI_IBUBAPA':
      return [
        record.className,
        record.guardianName ? `Penjaga: ${record.guardianName}` : '',
      ]
        .filter((v) => v.trim())
        .join(' • ');
    default:
      return record.className.trim();
  }
}

export function formatDurationHuman(totalMinutes: number): string {
  if (totalMinutes <= 0) return '0 min';
  const hours = Math.floor(totalMinutes / 60);
  const mins = totalMinutes % 60;
  if (hours === 0) return `${mins} min`;
  if (mins === 0) return `${hours} jam`;
  return `${hours} jam ${mins} min`;
}

export function computeDashboardStats(
  records: CounselingRecord[],
  period: StatsPeriod,
  nowMillis = Date.now()
): DashboardStats {
  const now = new Date(nowMillis);
  let filtered = records;

  if (period === 'WEEK') {
    const start = new Date(now);
    start.setDate(start.getDate() - 6);
    start.setHours(0, 0, 0, 0);
    filtered = records.filter((r) => r.sessionDateMillis >= start.getTime());
  } else if (period === 'MONTH') {
    const start = new Date(now.getFullYear(), now.getMonth(), 1, 0, 0, 0, 0);
    filtered = records.filter((r) => r.sessionDateMillis >= start.getTime());
  }

  const categoryCounts = ACTIVITY_CATEGORIES.reduce(
    (acc, item) => {
      acc[item.key] = 0;
      return acc;
    },
    {} as Record<ActivityCategory, number>
  );

  let counselingSessionsCount = 0;
  let aiSummaryCount = 0;
  let totalMinutes = 0;

  for (const r of filtered) {
    categoryCounts[r.category] = (categoryCounts[r.category] ?? 0) + 1;
    if (CATEGORY_MAP[r.category].isCounselingSession) {
      counselingSessionsCount += 1;
    }
    if (r.hasAiSummary) {
      aiSummaryCount += 1;
    }
    totalMinutes += r.durationMinutes;
  }

  const keyForDate = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
      d.getDate()
    ).padStart(2, '0')}`;

  const byDay = new Map<string, number>();
  for (const r of filtered) {
    const k = keyForDate(new Date(r.sessionDateMillis));
    byDay.set(k, (byDay.get(k) ?? 0) + 1);
  }

  const weeklyTrend: DailyTrendPoint[] = [];
  for (let offset = 6; offset >= 0; offset--) {
    const d = new Date(nowMillis);
    d.setDate(d.getDate() - offset);
    const key = keyForDate(d);
    weeklyTrend.push({
      dayLabel: MALAY_DAYS_SHORT[d.getDay()] ?? 'Isn',
      dateLabel: `${d.getDate()}/${d.getMonth() + 1}`,
      count: byDay.get(key) ?? 0,
    });
  }

  return {
    totalRecords: filtered.length,
    counselingSessionsCount,
    activitiesCount: filtered.length - counselingSessionsCount,
    aiSummaryCount,
    totalMinutes,
    categoryCounts,
    weeklyTrend,
  };
}
