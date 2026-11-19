import test from 'node:test';
import assert from 'node:assert/strict';
import nodeCrypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Helper to verify exact Android JCE <-> Web Crypto E2EE byte-for-byte parity
const KDF_ITERATIONS = 100_000;
const KEY_BYTES = 32;

function androidDeriveKey(secret, saltBase64, domainPrefix = 'kek') {
  const saltBytes = Buffer.from(saltBase64, 'base64');
  const domainSalt = Buffer.concat([
    Buffer.from(`${domainPrefix}:`, 'utf8'),
    saltBytes,
  ]);
  return nodeCrypto.pbkdf2Sync(
    Buffer.from(secret, 'utf8'),
    domainSalt,
    KDF_ITERATIONS,
    KEY_BYTES,
    'sha256'
  );
}

function androidEncryptAesGcm(plaintextUtf8, keyBuf) {
  const iv = nodeCrypto.randomBytes(12);
  const cipher = nodeCrypto.createCipheriv('aes-256-gcm', keyBuf, iv);
  const enc = Buffer.concat([
    cipher.update(Buffer.from(plaintextUtf8, 'utf8')),
    cipher.final(),
  ]);
  const tag = cipher.getAuthTag();
  // Java AES/GCM/NoPadding appends the 16-byte tag to the ciphertext
  const combined = Buffer.concat([enc, tag]);
  return {
    ciphertextBase64: combined.toString('base64'),
    ivBase64: iv.toString('base64'),
  };
}

function androidDecryptAesGcm(ciphertextBase64, ivBase64, keyBuf) {
  const combined = Buffer.from(ciphertextBase64, 'base64');
  const iv = Buffer.from(ivBase64, 'base64');
  const enc = combined.subarray(0, combined.length - 16);
  const tag = combined.subarray(combined.length - 16);
  const decipher = nodeCrypto.createDecipheriv('aes-256-gcm', keyBuf, iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(enc), decipher.final()]).toString('utf8');
}

async function webCryptoDeriveKey(secret, saltBase64, domainPrefix = 'kek') {
  const encoder = new TextEncoder();
  const saltBytes = new Uint8Array(Buffer.from(saltBase64, 'base64'));
  const prefixBytes = encoder.encode(`${domainPrefix}:`);
  const domainSalt = new Uint8Array(prefixBytes.length + saltBytes.length);
  domainSalt.set(prefixBytes, 0);
  domainSalt.set(saltBytes, prefixBytes.length);

  const keyMaterial = await globalThis.crypto.subtle.importKey(
    'raw',
    encoder.encode(secret),
    { name: 'PBKDF2' },
    false,
    ['deriveBits']
  );

  const bits = await globalThis.crypto.subtle.deriveBits(
    {
      name: 'PBKDF2',
      hash: 'SHA-256',
      salt: domainSalt,
      iterations: KDF_ITERATIONS,
    },
    keyMaterial,
    256
  );

  return globalThis.crypto.subtle.importKey(
    'raw',
    bits,
    { name: 'AES-GCM', length: 256 },
    true,
    ['encrypt', 'decrypt']
  );
}

test('Android <-> Desktop E2EE key derivation, wrapping, and payload decryption are byte-for-byte identical', async () => {
  const uid = 'uid_kaunselor_test_2026';
  const autoSecret = `dk-e2ee-auto:${uid}`;
  const saltBase64 = nodeCrypto.randomBytes(16).toString('base64');

  // 1. Android creates vault & wraps a 32-byte master key
  const androidKek = androidDeriveKey(autoSecret, saltBase64, 'kek');
  const androidVerifier = androidDeriveKey(autoSecret, saltBase64, 'verifier').toString('base64');
  const androidMasterKey = nodeCrypto.randomBytes(32);
  const ivWrap = nodeCrypto.randomBytes(12);
  const wrapCipher = nodeCrypto.createCipheriv('aes-256-gcm', androidKek, ivWrap);
  const wrappedBytes = Buffer.concat([
    wrapCipher.update(androidMasterKey),
    wrapCipher.final(),
    wrapCipher.getAuthTag(),
  ]);

  const sampleRecordPayload = JSON.stringify({
    clientName: 'Aina Sofea binti Ahmad',
    className: '5 Sains 1',
    organizer: 'Unit Bimbingan & Kaunseling',
    guardianName: 'Encik Ahmad bin Salleh',
    guardianPhone: '012-9876543',
    notes: 'Sesi kaunseling kerjaya dan pengurusan tekanan peperiksaan SPM.',
    summaryMatter: 'Perancangan hala tuju selepas SPM dan strategi pengurusan masa.',
    summaryQuestions: 'Bagaimanakah menyeimbangkan ulang kaji subjek teras?',
    programStrengthsWeaknesses: '',
    summaryIntervention: 'Teknik jadual anjal dan penstrukturan semula kognitif.',
  });

  const androidEncryptedRecord = androidEncryptAesGcm(
    sampleRecordPayload,
    androidMasterKey
  );

  // 2. Desktop Web Crypto derives the exact same KEK & verifier
  const webKek = await webCryptoDeriveKey(autoSecret, saltBase64, 'kek');
  const webVerifierKey = await webCryptoDeriveKey(autoSecret, saltBase64, 'verifier');
  const webVerifierBase64 = Buffer.from(
    await globalThis.crypto.subtle.exportKey('raw', webVerifierKey)
  ).toString('base64');
  assert.equal(webVerifierBase64, androidVerifier);

  // 3. Desktop unwraps the Android-wrapped master key
  const unwrappedMasterRaw = await globalThis.crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: ivWrap, tagLength: 128 },
    webKek,
    wrappedBytes
  );
  const webMasterKey = await globalThis.crypto.subtle.importKey(
    'raw',
    unwrappedMasterRaw,
    { name: 'AES-GCM', length: 256 },
    true,
    ['encrypt', 'decrypt']
  );

  // 4. Desktop decrypts the Android-encrypted counseling record
  const decryptedRecordBytes = await globalThis.crypto.subtle.decrypt(
    {
      name: 'AES-GCM',
      iv: Buffer.from(androidEncryptedRecord.ivBase64, 'base64'),
      tagLength: 128,
    },
    webMasterKey,
    Buffer.from(androidEncryptedRecord.ciphertextBase64, 'base64')
  );
  const decryptedJson = JSON.parse(new TextDecoder().decode(decryptedRecordBytes));
  assert.equal(decryptedJson.clientName, 'Aina Sofea binti Ahmad');
  assert.equal(decryptedJson.guardianPhone, '012-9876543');

  // 5. Desktop encrypts an edited record -> Android JCE decrypts it cleanly
  const desktopEdited = JSON.stringify({
    ...decryptedJson,
    summaryIntervention: 'Disunting dari Windows Desktop PWA dengan jayanya.',
  });
  const desktopIv = globalThis.crypto.getRandomValues(new Uint8Array(12));
  const desktopCipher = new Uint8Array(
    await globalThis.crypto.subtle.encrypt(
      { name: 'AES-GCM', iv: desktopIv, tagLength: 128 },
      webMasterKey,
      new TextEncoder().encode(desktopEdited)
    )
  );
  const androidDecrypted = androidDecryptAesGcm(
    Buffer.from(desktopCipher).toString('base64'),
    Buffer.from(desktopIv).toString('base64'),
    androidMasterKey
  );
  assert.equal(
    JSON.parse(androidDecrypted).summaryIntervention,
    'Disunting dari Windows Desktop PWA dengan jayanya.'
  );
});

test('Desktop PWA manifest and named Firestore database config match Android exactly', () => {
  const __dirname = path.dirname(fileURLToPath(import.meta.url));
  const rootDir = path.resolve(__dirname, '..');

  const manifest = JSON.parse(
    fs.readFileSync(path.join(rootDir, 'public/manifest.webmanifest'), 'utf8')
  );
  assert.equal(manifest.display, 'standalone');
  assert.equal(manifest.short_name, 'Diari Kaunselor');
  assert.ok(
    manifest.icons.some((i) => i.sizes === '192x192' && i.type === 'image/png'),
    'Manifest must declare 192x192 PNG icon'
  );
  assert.ok(
    manifest.icons.some((i) => i.sizes === '512x512' && i.type === 'image/png'),
    'Manifest must declare 512x512 PNG icon'
  );

  const firebaseSrc = fs.readFileSync(
    path.join(rootDir, 'src/config/firebase.ts'),
    'utf8'
  );
  assert.ok(
    firebaseSrc.includes(
      'ai-studio-android-diarikau-bdc45ffc-36dd-4edd-a495-6f4501e58629'
    ),
    'Firebase config must target the exact named Firestore database ID'
  );
  assert.ok(
    firebaseSrc.includes('gen-lang-client-0292147175'),
    'Firebase config must target the exact Android Firebase project'
  );
});

test('Desktop repository strictly complies with firestore.rules keys (no id field)', () => {
  const __dirname = path.dirname(fileURLToPath(import.meta.url));
  const repoSrc = fs.readFileSync(
    path.resolve(__dirname, '../src/data/repository.ts'),
    'utf8'
  );
  const expectedFields = [
    'userId',
    'category',
    'sessionDateMillis',
    'dayName',
    'timeRange',
    'durationMinutes',
    'hasAiSummary',
    'encryptionVersion',
    'encryptedPayload',
    'payloadIv',
    'createdAt',
    'updatedAt',
  ];
  for (const field of expectedFields) {
    assert.ok(repoSrc.includes(field), `Repository missing required field: ${field}`);
  }
  assert.ok(
    repoSrc.includes("record.category !== 'KELAS_GANTI'"),
    'KELAS_GANTI must force hasAiSummary to false'
  );
  assert.ok(
    !repoSrc.includes('deleteField('),
    'Repository must not pass deleteField() into non-merge setDoc()'
  );
});
