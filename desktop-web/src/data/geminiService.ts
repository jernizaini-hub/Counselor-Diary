import {
  CATEGORY_MAP,
  type ActivityCategory,
  type AiSummaryResult,
  type CounselingRecord,
} from '../models/counseling';

const GEMINI_API_BASE = 'https://generativelanguage.googleapis.com/v1beta';
const PREFERRED_MODELS = [
  'gemini-3.8-flash',
  'gemini-3.5-flash-lite',
  'gemini-3.5-flash',
  'gemini-2.5-flash',
];

const SAFETY_GUIDANCE_MESSAGE =
  'Catatan mengandungi istilah sensitif atau risiko tinggi yang ditahan oleh penapis keselamatan AI. ' +
  "Sila guna ungkapan klinikal neutral (cth. 'risiko keselamatan diri / memerlukan saringan segera') untuk janaan AI, " +
  'atau isi kotak rumusan secara manual serta teruskan rujukan SOP kecemasan kaunseling jika perlu.';

const SYSTEM_INSTRUCTION_TEXT =
  'Anda ialah pembantu dokumentasi klinikal rasmi untuk Guru Bimbingan dan Kaunseling (Kaunselor Berdaftar) ' +
  'di institusi pendidikan Malaysia. Semua input adalah catatan sesi profesional untuk tujuan pendokumentasian, ' +
  'penilaian risiko, sokongan psikososial, dan perancangan intervensi keselamatan klien. ' +
  'Jangan sekali-kali menjana arahan mencederakan diri; sebaliknya, apabila nota menyebut risiko keselamatan atau ' +
  'kecenderungan mencederakan diri, fokuskan huraian intervensi pada protokol saringan keselamatan segera, ' +
  'rujukan pakar/waris/pentadbir mengikut SOP kaunseling, serta pelan sokongan protektif yang profesional dan beretika.';

const STORAGE_PREFIX = 'diari_kaunselor_gemini_key_';

export class MissingGeminiApiKeyError extends Error {
  constructor() {
    super(
      'Kunci Gemini API belum dimasukkan. Sila ke tab Keselamatan untuk memasukkan kunci Gemini API anda terlebih dahulu.'
    );
    this.name = 'MissingGeminiApiKeyError';
  }
}

export class InvalidGeminiApiKeyError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'InvalidGeminiApiKeyError';
  }
}

export type GeminiKeyVerificationResult =
  | { status: 'valid'; activeModel: string }
  | { status: 'invalid'; message: string }
  | { status: 'network_error'; message: string };

export function loadStoredGeminiApiKey(userId: string): string {
  try {
    return localStorage.getItem(`${STORAGE_PREFIX}${userId}`)?.trim() ?? '';
  } catch {
    return '';
  }
}

export function saveStoredGeminiApiKey(userId: string, apiKey: string): void {
  try {
    localStorage.setItem(`${STORAGE_PREFIX}${userId}`, apiKey.trim());
  } catch {
    // ignore storage errors
  }
}

export function clearStoredGeminiApiKey(userId: string): void {
  try {
    localStorage.removeItem(`${STORAGE_PREFIX}${userId}`);
  } catch {
    // ignore
  }
}

export function maskGeminiKey(rawKey: string): string {
  const key = rawKey.trim();
  if (!key) return '';
  if (key.length <= 8) return '••••••••';
  return `${key.slice(0, 6)}••••••${key.slice(-4)}`;
}

function parseAvailableGenerateContentModels(responseText: string): string[] {
  try {
    const root = JSON.parse(responseText) as {
      models?: Array<{ name?: string; supportedGenerationMethods?: string[] }>;
    };
    const list: string[] = [];
    for (const model of root.models ?? []) {
      const supports = model.supportedGenerationMethods?.includes('generateContent');
      if (!supports) continue;
      const shortName = (model.name ?? '').replace(/^models\//, '').trim();
      if (
        shortName.startsWith('gemini-') &&
        !shortName.includes('image') &&
        !shortName.includes('tts')
      ) {
        list.push(shortName);
      }
    }
    return list;
  } catch {
    return [];
  }
}

function pickBestFlashModel(available: string[]): string | null {
  for (const preferred of PREFERRED_MODELS) {
    if (available.includes(preferred)) return preferred;
  }
  return (
    available.find((m) => m.toLowerCase().includes('flash')) ??
    available[0] ??
    null
  );
}

function isAuthOrKeyError(statusCode: number, body: string): boolean {
  const lower = body.toLowerCase();
  return (
    statusCode === 401 ||
    (statusCode === 400 &&
      (lower.includes('api_key_invalid') || lower.includes('api key not valid'))) ||
    (statusCode === 403 &&
      (lower.includes('api_key') || lower.includes('api key') || lower.includes('consumer')))
  );
}

function extractApiErrorMessage(statusCode: number, body: string): string {
  const trimmed = body.trim();
  if (trimmed) {
    try {
      const parsed = JSON.parse(trimmed) as {
        error?: { message?: string; status?: string };
      };
      const msg = parsed.error?.message?.trim() ?? '';
      const status = parsed.error?.status?.trim() ?? '';
      if (msg) {
        return status ? `HTTP ${statusCode} (${status}): ${msg}` : `HTTP ${statusCode}: ${msg}`;
      }
    } catch {
      // ignore
    }
    return `HTTP ${statusCode}: ${trimmed.slice(0, 180)}`;
  }
  return `HTTP ${statusCode} daripada pelayan Gemini API`;
}

function mapHttpStatusToMalayMessage(statusCode: number, detail: string): string {
  if (statusCode === 429) {
    return `Had kuota Gemini API sementara dicapai (${detail}). Sila tunggu sebentar dan cuba semula.`;
  }
  if (statusCode === 404) {
    return `Model Gemini tidak ditemui untuk kunci API ini (${detail}).`;
  }
  if (statusCode >= 500 && statusCode <= 599) {
    return `Pelayan Gemini API sedang sibuk atau mengalami gangguan sementara (${detail}). Sila cuba semula.`;
  }
  return `Ralat Gemini API (${detail}).`;
}

async function fetchWithTimeout(
  url: string,
  options: RequestInit,
  timeoutMs: number
): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

export async function verifyGeminiApiKey(
  rawKey: string
): Promise<GeminiKeyVerificationResult> {
  const apiKey = rawKey.trim();
  if (!apiKey) {
    return {
      status: 'invalid',
      message: 'Sila masukkan kunci Gemini API terlebih dahulu.',
    };
  }
  if (/\s/.test(apiKey)) {
    return {
      status: 'invalid',
      message: 'Format kunci API tidak sah kerana mengandungi ruang kosong.',
    };
  }

  try {
    const res = await fetchWithTimeout(
      `${GEMINI_API_BASE}/models?pageSize=50`,
      {
        method: 'GET',
        headers: {
          'x-goog-api-key': apiKey,
          Accept: 'application/json',
        },
      },
      15_000
    );
    const body = await res.text();
    if (res.ok) {
      const models = parseAvailableGenerateContentModels(body);
      const selected = pickBestFlashModel(models) ?? PREFERRED_MODELS[0];
      return { status: 'valid', activeModel: selected };
    }
    if (isAuthOrKeyError(res.status, body)) {
      const detail = extractApiErrorMessage(res.status, body);
      return {
        status: 'invalid',
        message: `Kunci Gemini API ditolak oleh Google (${detail}). Sila semak kunci di Google AI Studio.`,
      };
    }
    const detail = extractApiErrorMessage(res.status, body);
    return {
      status: 'network_error',
      message: `Pelayan Gemini memulangkan ralat: ${detail}`,
    };
  } catch (err: unknown) {
    if ((err as { name?: string })?.name === 'AbortError') {
      return {
        status: 'network_error',
        message: 'Masa menunggu pengesahan tamat. Sila semak sambungan internet anda.',
      };
    }
    const msg = (err as { message?: string })?.message ?? 'tiada sambungan';
    return {
      status: 'network_error',
      message: `Tidak dapat menghubungi pelayan Gemini (${msg}).`,
    };
  }
}

function buildCategoryPrompt(draft: CounselingRecord): string {
  const displayName = CATEGORY_MAP[draft.category].displayName;
  switch (draft.category) {
    case 'PROGRAM':
      return [
        'Berdasarkan maklumat program dan transkrip/catatan ringkas kaunselor di bawah, hasilkan analisis rumusan program rasmi dalam Bahasa Melayu yang jelas, tersusun, dan profesional.',
        '',
        `Kategori Aktiviti: ${displayName}`,
        `Nama Program: ${draft.clientName.trim() || 'Tidak dinyatakan'}`,
        `Anjuran: ${draft.organizer.trim() || 'Tidak dinyatakan'}`,
        `Sasaran: ${draft.className.trim() || 'Tidak dinyatakan'}`,
        `Hari & Masa: ${draft.dayName}, ${draft.timeRange}`,
        '',
        'Transkrip / Catatan Ringkas Pelaksanaan Program:',
        draft.notes,
        '',
        'Arahan Output JSON:',
        '1. "rumusanProgram": Rumusan keseluruhan pelaksanaan, pengisian utama, penyertaan sasaran, dan impak program (2-4 ayat).',
        '2. "objektif": Nyatakan objektif utama program dan tahap pencapaiannya berdasarkan catatan.',
        '3. "kelebihanKelemahan": Pecahkan secara jelas kelebihan (kekuatan) program serta kelemahan atau ruang penambahbaikan.',
        '4. "huraianIntervensi": Huraian intervensi atau pelan tindakan susulan kaunseling selepas program dijalankan.',
      ].join('\n');

    case 'ZIARAH_CAKNA':
    case 'KONSULTASI_IBUBAPA':
      return [
        'Berdasarkan maklumat ziarah cakna / konsultasi ibu bapa dan catatan perbincangan di bawah, hasilkan rumusan perbincangan rasmi dalam Bahasa Melayu yang empatik, tepat, dan beretika.',
        '',
        `Kategori Aktiviti: ${displayName}`,
        `Nama Murid: ${draft.clientName.trim() || 'Tidak dinyatakan'}`,
        `Kelas Murid: ${draft.className.trim() || 'Tidak dinyatakan'}`,
        `Nama Penjaga: ${draft.guardianName.trim() || 'Tidak dinyatakan'}`,
        `No. HP Penjaga: ${draft.guardianPhone.trim() || 'Tidak dinyatakan'}`,
        `Hari & Masa: ${draft.dayName}, ${draft.timeRange}`,
        '',
        'Catatan Perbincangan Bersama Penjaga / Murid:',
        draft.notes,
        '',
        'Arahan Output JSON:',
        '1. "perkara": Rumusan perbincangan utama antara kaunselor dan penjaga/ibu bapa secara padat dan jelas (2-4 ayat).',
        '2. "persoalan": Isu, kebimbangan atau persoalan utama yang dibangkitkan oleh penjaga atau dikenal pasti semasa sesi.',
        '3. "huraianIntervensi": Huraian intervensi sokongan, persetujuan bersama keluarga/sekolah, dan pelan tindakan susulan.',
      ].join('\n');

    case 'MESYUARAT':
      return [
        'Berdasarkan maklumat mesyuarat dan catatan minit ringkas di bawah, hasilkan rumusan mesyuarat rasmi dalam Bahasa Melayu yang padat dan profesional.',
        '',
        `Kategori Aktiviti: ${displayName}`,
        `Nama Mesyuarat: ${draft.clientName.trim() || 'Tidak dinyatakan'}`,
        `Hari & Masa: ${draft.dayName}, ${draft.timeRange}`,
        '',
        'Catatan / Perbincangan Mesyuarat:',
        draft.notes,
        '',
        'Arahan Output JSON:',
        '1. "perkara": Ringkasan perkara pokok dan agenda utama yang dibincangkan dalam mesyuarat.',
        '2. "persoalan": Isu berbangkit, persoalan atau perkara yang memerlukan perhatian/penyelarasan lanjut.',
        '3. "huraianIntervensi": Huraian keputusan mesyuarat, intervensi/tindakan susulan unit bimbingan, dan pihak bertanggungjawab.',
      ].join('\n');

    default:
      return [
        'Berdasarkan maklumat dan transkrip/catatan ringkas kaunselor di bawah, hasilkan analisis rumusan profesional dalam Bahasa Melayu yang jelas, empatik, dan beretika.',
        '',
        `Kategori Aktiviti: ${displayName}`,
        `Nama Klien / Peserta: ${draft.clientName.trim() || 'Tidak dinyatakan'}`,
        `Kelas / Kumpulan: ${draft.className.trim() || 'Tidak dinyatakan'}`,
        `Hari & Masa: ${draft.dayName}, ${draft.timeRange}`,
        '',
        'Transkrip Perbualan / Catatan Ringkas Kaunselor:',
        draft.notes,
        '',
        'Arahan Output JSON:',
        '1. "perkara": Nyatakan perkara/isu pokok secara ringkas dan tepat (2-4 ayat).',
        '2. "persoalan": Senaraikan atau huraikan persoalan utama yang timbul dan aspek yang perlu diterokai lanjut.',
        '3. "huraianIntervensi": Berikan huraian intervensi kaunseling yang sesuai (contoh: teknik penstrukturan semula kognitif, terapi realiti, penilaian keselamatan, rujukan sokongan waris/pakar) dan tindakan susulan yang praktikal.',
      ].join('\n');
  }
}

function buildResponseSchema(category: ActivityCategory) {
  if (category === 'PROGRAM') {
    return {
      type: 'OBJECT',
      properties: {
        rumusanProgram: {
          type: 'STRING',
          description:
            'Rumusan Program: ringkasan menyeluruh pelaksanaan, pengisian, penyertaan serta impak program dalam Bahasa Melayu profesional.',
        },
        objektif: {
          type: 'STRING',
          description:
            'Objektif Program: senarai objektif utama program yang disasarkan dan tahap pencapaiannya.',
        },
        kelebihanKelemahan: {
          type: 'STRING',
          description:
            'Kelebihan & Kelemahan: kekuatan pelaksanaan program serta kelemahan atau ruang penambahbaikan pada masa hadapan.',
        },
        huraianIntervensi: {
          type: 'STRING',
          description:
            'Huraian Intervensi: langkah susulan, bimbingan lanjutan atau tindakan penambahbaikan untuk kumpulan sasaran.',
        },
      },
      required: [
        'rumusanProgram',
        'objektif',
        'kelebihanKelemahan',
        'huraianIntervensi',
      ],
    };
  }

  return {
    type: 'OBJECT',
    properties: {
      perkara: {
        type: 'STRING',
        description:
          'Ringkasan padat isu utama, tema, rumusan perbincangan atau perkara pokok yang dijalankan dalam Bahasa Melayu profesional.',
      },
      persoalan: {
        type: 'STRING',
        description:
          'Persoalan utama klien, isu berbangkit penjaga/murid, atau soalan penerokaan kritikal untuk tindakan susulan.',
      },
      huraianIntervensi: {
        type: 'STRING',
        description:
          'Huraian terperinci intervensi kaunseling, keputusan, pendekatan psikologi, serta pelan tindakan susulan.',
      },
    },
    required: ['perkara', 'persoalan', 'huraianIntervensi'],
  };
}

function stripMarkdownFences(raw: string): string {
  const trimmed = raw.trim();
  if (!trimmed.startsWith('```')) return trimmed;
  return trimmed
    .replace(/^```(?:json|JSON)?\s*/i, '')
    .replace(/\s*```$/, '')
    .trim();
}

function extractSummaryFromResponse(
  category: ActivityCategory,
  responseBody: string
): AiSummaryResult {
  const root = JSON.parse(responseBody) as {
    promptFeedback?: { blockReason?: string };
    candidates?: Array<{
      finishReason?: string;
      content?: { parts?: Array<{ text?: string; thought?: boolean }> };
    }>;
  };

  const blockReason = root.promptFeedback?.blockReason?.trim() ?? '';
  if (blockReason && blockReason !== 'BLOCK_REASON_UNSPECIFIED') {
    throw new Error(SAFETY_GUIDANCE_MESSAGE);
  }

  const candidate = root.candidates?.[0];
  if (!candidate) {
    throw new Error('Model AI tidak memulangkan calon jawapan. Sila cuba semula.');
  }

  const finishReason = candidate.finishReason?.trim() ?? '';
  if (['SAFETY', 'PROHIBITED_CONTENT', 'BLOCKLIST', 'SPII'].includes(finishReason)) {
    throw new Error(SAFETY_GUIDANCE_MESSAGE);
  }

  const parts = candidate.content?.parts ?? [];
  const rawText = parts
    .filter((p) => !p.thought && typeof p.text === 'string')
    .map((p) => p.text ?? '')
    .join('')
    .trim();

  if (!rawText) {
    if (finishReason === 'MAX_TOKENS') {
      throw new Error(
        'Catatan terlalu panjang untuk diproses serentak. Sila ringkaskan sedikit catatan dan cuba semula.'
      );
    }
    throw new Error('Model AI memulangkan respons kosong. Sila cuba semula.');
  }

  const parsed = JSON.parse(stripMarkdownFences(rawText)) as Record<string, unknown>;
  if (category === 'PROGRAM') {
    return {
      matter: String(parsed.rumusanProgram ?? parsed.perkara ?? '').trim(),
      questions: String(parsed.objektif ?? parsed.persoalan ?? '').trim(),
      strengthsWeaknesses: String(parsed.kelebihanKelemahan ?? '').trim(),
      intervention: String(parsed.huraianIntervensi ?? '').trim(),
    };
  }
  return {
    matter: String(parsed.perkara ?? '').trim(),
    questions: String(parsed.persoalan ?? '').trim(),
    strengthsWeaknesses: '',
    intervention: String(parsed.huraianIntervensi ?? '').trim(),
  };
}

async function discoverFlashModels(apiKey: string): Promise<string[]> {
  try {
    const res = await fetchWithTimeout(
      `${GEMINI_API_BASE}/models?pageSize=50`,
      {
        method: 'GET',
        headers: { 'x-goog-api-key': apiKey, Accept: 'application/json' },
      },
      15_000
    );
    if (!res.ok) return [];
    return parseAvailableGenerateContentModels(await res.text());
  } catch {
    return [];
  }
}

export async function generateCounselingSummary(
  draft: CounselingRecord,
  rawApiKey: string
): Promise<AiSummaryResult> {
  const apiKey = rawApiKey.trim();
  if (!apiKey) {
    throw new MissingGeminiApiKeyError();
  }

  const payload = JSON.stringify({
    systemInstruction: {
      parts: [{ text: SYSTEM_INSTRUCTION_TEXT }],
    },
    contents: [
      {
        role: 'user',
        parts: [{ text: buildCategoryPrompt(draft) }],
      },
    ],
    safetySettings: [
      'HARM_CATEGORY_HARASSMENT',
      'HARM_CATEGORY_HATE_SPEECH',
      'HARM_CATEGORY_SEXUALLY_EXPLICIT',
      'HARM_CATEGORY_DANGEROUS_CONTENT',
    ].map((category) => ({ category, threshold: 'BLOCK_ONLY_HIGH' })),
    generationConfig: {
      responseMimeType: 'application/json',
      responseSchema: buildResponseSchema(draft.category),
    },
  });

  const attempted = new Set<string>();
  const candidates = [...PREFERRED_MODELS];
  let discovered = false;
  let lastError: Error | null = null;

  while (candidates.length > 0) {
    const modelName = candidates.shift()!;
    if (attempted.has(modelName)) continue;
    attempted.add(modelName);

    try {
      const res = await fetchWithTimeout(
        `${GEMINI_API_BASE}/models/${modelName}:generateContent`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json; charset=UTF-8',
            Accept: 'application/json',
            'x-goog-api-key': apiKey,
          },
          body: payload,
        },
        45_000
      );

      const body = await res.text();
      if (res.ok) {
        return extractSummaryFromResponse(draft.category, body);
      }

      if (isAuthOrKeyError(res.status, body)) {
        const detail = extractApiErrorMessage(res.status, body);
        throw new InvalidGeminiApiKeyError(
          `Kunci Gemini API tidak sah atau telah dibatalkan (${detail}). Sila semak kunci anda di tab Keselamatan.`
        );
      }

      const errorMsg = extractApiErrorMessage(res.status, body);
      if (res.status === 404 && !discovered) {
        discovered = true;
        const found = await discoverFlashModels(apiKey);
        for (const m of found) {
          if (!attempted.has(m)) candidates.push(m);
        }
        lastError = new Error(mapHttpStatusToMalayMessage(res.status, errorMsg));
        continue;
      }

      if (res.status === 429 || res.status >= 500 || res.status === 404) {
        lastError = new Error(mapHttpStatusToMalayMessage(res.status, errorMsg));
        continue;
      }

      throw new Error(mapHttpStatusToMalayMessage(res.status, errorMsg));
    } catch (err: unknown) {
      if (err instanceof InvalidGeminiApiKeyError) throw err;
      if ((err as { name?: string })?.name === 'AbortError') {
        lastError = new Error(
          'Sambungan Gemini API mengambil masa terlalu lama (melebihi 45 saat). Sila pastikan internet stabil dan cuba semula.'
        );
      } else if (err instanceof Error) {
        lastError = err;
      } else {
        lastError = new Error('Gagal menghubungi pelayan Gemini API.');
      }
    }
  }

  throw (
    lastError ??
    new Error('Gagal menjana rumusan AI. Sila cuba semula sebentar lagi.')
  );
}
