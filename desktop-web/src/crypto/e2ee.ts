export const ENCRYPTION_VERSION = 'AES-256-GCM-v1';

const KDF_ITERATIONS = 100_000;
const KEY_BITS = 256;
const GCM_IV_BYTES = 12;
const GCM_TAG_BITS = 128;

export interface EncryptedBlob {
  ciphertextBase64: string;
  ivBase64: string;
}

const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder();

function getSubtle(): SubtleCrypto {
  const subtle = globalThis.crypto?.subtle;
  if (!subtle) {
    throw new Error(
      'Web Crypto API tidak tersedia. Pastikan aplikasi dibuka melalui HTTPS atau localhost.'
    );
  }
  return subtle;
}

export function autoVaultSecret(uid: string): string {
  return `dk-e2ee-auto:${uid}`;
}

export function bytesToBase64(bytes: Uint8Array): string {
  if (typeof Buffer !== 'undefined' && typeof btoa === 'undefined') {
    return Buffer.from(bytes).toString('base64');
  }
  let binary = '';
  const chunkSize = 0x8000;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    const slice = bytes.subarray(i, i + chunkSize);
    binary += String.fromCharCode(...slice);
  }
  return btoa(binary);
}

export function base64ToBytes(base64: string): Uint8Array {
  const clean = base64.replace(/\s+/g, '');
  if (typeof Buffer !== 'undefined' && typeof atob === 'undefined') {
    return new Uint8Array(Buffer.from(clean, 'base64'));
  }
  const binary = atob(clean);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function concatBytes(a: Uint8Array, b: Uint8Array): Uint8Array {
  const combined = new Uint8Array(a.length + b.length);
  combined.set(a, 0);
  combined.set(b, a.length);
  return combined;
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  return copy.buffer;
}

export function generateSaltBase64(): string {
  const salt = new Uint8Array(16);
  globalThis.crypto.getRandomValues(salt);
  return bytesToBase64(salt);
}

export async function generateMasterKey(): Promise<CryptoKey> {
  return getSubtle().generateKey(
    { name: 'AES-GCM', length: KEY_BITS },
    true,
    ['encrypt', 'decrypt']
  );
}

export async function importRawAesKey(rawBytes: Uint8Array): Promise<CryptoKey> {
  return getSubtle().importKey(
    'raw',
    toArrayBuffer(rawBytes),
    { name: 'AES-GCM', length: KEY_BITS },
    true,
    ['encrypt', 'decrypt']
  );
}

export async function exportRawAesKey(key: CryptoKey): Promise<Uint8Array> {
  const raw = await getSubtle().exportKey('raw', key);
  return new Uint8Array(raw);
}

export async function deriveKeyFromSecret(
  secret: string,
  saltBase64: string,
  domainPrefix = 'kek'
): Promise<CryptoKey> {
  const subtle = getSubtle();
  const saltBytes = base64ToBytes(saltBase64);
  const prefixBytes = textEncoder.encode(`${domainPrefix}:`);
  const domainSalt = concatBytes(prefixBytes, saltBytes);

  const keyMaterial = await subtle.importKey(
    'raw',
    toArrayBuffer(textEncoder.encode(secret)),
    { name: 'PBKDF2' },
    false,
    ['deriveBits']
  );

  const derivedBits = await subtle.deriveBits(
    {
      name: 'PBKDF2',
      hash: 'SHA-256',
      salt: toArrayBuffer(domainSalt),
      iterations: KDF_ITERATIONS,
    },
    keyMaterial,
    KEY_BITS
  );

  return importRawAesKey(new Uint8Array(derivedBits));
}

export async function hashPinVerifier(
  secret: string,
  saltBase64: string
): Promise<string> {
  const verifierKey = await deriveKeyFromSecret(secret, saltBase64, 'verifier');
  const raw = await exportRawAesKey(verifierKey);
  return bytesToBase64(raw);
}

export async function encryptBytes(
  plainBytes: Uint8Array,
  key: CryptoKey
): Promise<EncryptedBlob> {
  const iv = new Uint8Array(GCM_IV_BYTES);
  globalThis.crypto.getRandomValues(iv);
  const encrypted = await getSubtle().encrypt(
    { name: 'AES-GCM', iv: toArrayBuffer(iv), tagLength: GCM_TAG_BITS },
    key,
    toArrayBuffer(plainBytes)
  );
  return {
    ciphertextBase64: bytesToBase64(new Uint8Array(encrypted)),
    ivBase64: bytesToBase64(iv),
  };
}

export async function decryptBytes(
  ciphertextBase64: string,
  ivBase64: string,
  key: CryptoKey
): Promise<Uint8Array> {
  const ciphertext = base64ToBytes(ciphertextBase64);
  const iv = base64ToBytes(ivBase64);
  const decrypted = await getSubtle().decrypt(
    { name: 'AES-GCM', iv: toArrayBuffer(iv), tagLength: GCM_TAG_BITS },
    key,
    toArrayBuffer(ciphertext)
  );
  return new Uint8Array(decrypted);
}

export async function encryptString(
  plaintext: string,
  key: CryptoKey
): Promise<EncryptedBlob> {
  return encryptBytes(textEncoder.encode(plaintext), key);
}

export async function decryptString(
  ciphertextBase64: string,
  ivBase64: string,
  key: CryptoKey
): Promise<string> {
  const plainBytes = await decryptBytes(ciphertextBase64, ivBase64, key);
  return textDecoder.decode(plainBytes);
}

export async function wrapSecretKey(
  targetKey: CryptoKey,
  wrappingKey: CryptoKey
): Promise<EncryptedBlob> {
  const raw = await exportRawAesKey(targetKey);
  return encryptBytes(raw, wrappingKey);
}

export async function unwrapSecretKey(
  wrappedKeyBase64: string,
  ivBase64: string,
  wrappingKey: CryptoKey
): Promise<CryptoKey> {
  const raw = await decryptBytes(wrappedKeyBase64, ivBase64, wrappingKey);
  return importRawAesKey(raw);
}
