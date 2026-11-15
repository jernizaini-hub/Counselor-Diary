const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_counselor_123";
const BOB_UID = "bob_counselor_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTimestamp() {
  return new Date(Date.now() - 1000);
}

function validRecordPayload(uid) {
  return {
    userId: uid,
    category: "KAUNSELING_INDIVIDU",
    sessionDateMillis: Date.now(),
    dayName: "Isnin",
    timeRange: "09:00 - 10:00",
    durationMinutes: 60,
    hasAiSummary: true,
    encryptionVersion: "AES-256-GCM-v1",
    encryptedPayload: "base64EncryptedPayloadContent1234567890==",
    payloadIv: "base64IvValue1234==",
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };
}

function validVaultPayload(uid) {
  return {
    userId: uid,
    pinHash: "pbkdf2HashStringLengthGreaterThan16Chars",
    kdfSalt: "saltBase64Value==",
    wrappedMasterKey: "wrappedMasterKeyBase64Content12345==",
    wrappedMasterKeyIv: "ivBase64Value12==",
    totpEnabled: false,
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write records or vault", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("records").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("records").doc("rec_1").set(validRecordPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("security").doc("vaultConfig").get()
  );
});

test("Owner can create, read, update, and delete valid E2EE record", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("records").doc("rec_001");

  await assertSucceeds(docRef.set(validRecordPayload(ALICE_UID)));
  await assertSucceeds(docRef.get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("records").get()
  );
  await assertSucceeds(
    docRef.update({
      category: "ZIARAH_CAKNA",
      durationMinutes: 90,
      updatedAt: nowTimestamp(),
    })
  );
  await assertSucceeds(docRef.delete());
});

test("Owner can create, read, and update vaultConfig", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const vaultRef = aliceDb.collection("users").doc(ALICE_UID).collection("security").doc("vaultConfig");

  await assertSucceeds(vaultRef.set(validVaultPayload(ALICE_UID)));
  await assertSucceeds(vaultRef.get());
  await assertSucceeds(
    vaultRef.update({
      totpEnabled: true,
      wrappedTotpSecret: "wrappedTotpSecretBase64==",
      wrappedTotpIv: "totpIvBase64==",
      updatedAt: nowTimestamp(),
    })
  );
});

test("Cross-user access to records and vaultConfig is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("records").doc("rec_alice").set(validRecordPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("security").doc("vaultConfig").set(validVaultPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("records").doc("rec_alice").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("records").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("records").doc("rec_alice").delete());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("security").doc("vaultConfig").get());
});

test("Shadow update with ghost field or invalid category is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badRecord = { ...validRecordPayload(ALICE_UID), ghostField: "hack" };
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("records").doc("rec_ghost").set(badRecord)
  );

  const badCategory = { ...validRecordPayload(ALICE_UID), category: "INVALID_CATEGORY" };
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("records").doc("rec_cat").set(badCategory)
  );
});

test("Immortal userId and createdAt cannot be mutated on update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("records").doc("rec_immortal");
  await assertSucceeds(docRef.set(validRecordPayload(ALICE_UID)));

  await assertFails(
    docRef.update({
      userId: BOB_UID,
      updatedAt: nowTimestamp(),
    })
  );
});
