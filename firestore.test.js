const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function pastTime(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

test("Unauthenticated users are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("settings").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("test_items").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("debug_logs").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("test_items").doc("item_1").set({
      userId: ALICE_UID,
      title: "Rogue",
      notes: "",
      category: "AUTH",
      completed: false,
      deviceTag: "sdk_gphone",
      sdkInt: 34,
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );
});

test("UserProfile: owner create, partial update, immutable & ghost field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastTime(10000);
  const userRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    userRef.set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice Tester",
      lastDeviceModel: "sdk_gphone64_x86_64",
      lastAndroidRelease: "14 (API 34)",
      lastBuildType: "userdebug",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    userRef.update({
      lastDeviceModel: "Pixel 8 Pro",
      updatedAt: pastTime(1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    userRef.update({
      createdAt: pastTime(500),
      updatedAt: pastTime(500),
    })
  );

  // Reject ghost field
  await assertFails(
    userRef.update({
      isAdmin: true,
      updatedAt: pastTime(500),
    })
  );
});

test("DeviceSettings: owner create, partial update, validation bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastTime(10000);
  const settingsRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("settings")
    .doc("device_preferences");

  await assertSucceeds(
    settingsRef.set({
      userId: ALICE_UID,
      themeMode: "DARK",
      dynamicColorEnabled: true,
      compactDensityEnabled: false,
      hapticsEnabled: true,
      autoSyncEnabled: true,
      syncOnMeteredEnabled: true,
      verboseDebugOverlayEnabled: true,
      keepScreenAwakeInDebug: false,
      defaultTestEnvironment: "sdk_gphone_api34",
      offlineCacheSizeMb: 250,
      syncCounter: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    settingsRef.update({
      themeMode: "LIGHT",
      compactDensityEnabled: true,
      syncCounter: 2,
      updatedAt: pastTime(1000),
    })
  );

  // Invalid themeMode enum
  await assertFails(
    settingsRef.update({
      themeMode: "NEON",
      updatedAt: pastTime(500),
    })
  );

  // Out-of-bounds offlineCacheSizeMb
  await assertFails(
    settingsRef.update({
      offlineCacheSizeMb: 10,
      updatedAt: pastTime(500),
    })
  );

  // Future timestamp rejected
  await assertFails(
    settingsRef.update({
      themeMode: "SYSTEM",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("TestProgressItem: owner CRUD, empty list, no @DocumentId field in payload", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const itemsCol = aliceDb.collection("users").doc(ALICE_UID).collection("test_items");
  const created = pastTime(10000);

  // Empty list query must succeed (must not inspect existing() in allow list)
  await assertSucceeds(itemsCol.get());

  await assertSucceeds(
    itemsCol.doc("item_1").set({
      userId: ALICE_UID,
      title: "Verify Android 14 Credential Manager sign-in",
      notes: "Tested on sdk_gphone64_x86_64 userdebug build.",
      category: "AUTH",
      completed: false,
      deviceTag: "sdk_gphone64_x86_64",
      sdkInt: 34,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    itemsCol.doc("item_1").update({
      completed: true,
      updatedAt: pastTime(1000),
    })
  );

  // Reject payload containing redundant "id" field
  await assertFails(
    itemsCol.doc("item_2").set({
      id: "item_2",
      userId: ALICE_UID,
      title: "Bad payload with id",
      notes: "",
      category: "GENERAL",
      completed: false,
      deviceTag: "sdk_gphone64_x86_64",
      sdkInt: 34,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(itemsCol.doc("item_1").delete());
});

test("DebugLogEntry: owner append & delete succeed, update is forbidden", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const logsCol = aliceDb.collection("users").doc(ALICE_UID).collection("debug_logs");
  const created = pastTime(5000);

  await assertSucceeds(
    logsCol.doc("log_1").set({
      userId: ALICE_UID,
      eventType: "AUTH_CHECK",
      summary: "Google Sign-In token verified",
      details: "Provider: google.com, emailVerified: true",
      networkState: "ONLINE_WIFI",
      deviceModel: "sdk_gphone64_x86_64",
      androidVersion: "14 (API 34)",
      createdAt: created,
    })
  );

  await assertSucceeds(logsCol.get());

  // Append-only: updates must fail
  await assertFails(
    logsCol.doc("log_1").update({
      summary: "Tampered log",
    })
  );

  await assertSucceeds(logsCol.doc("log_1").delete());
});

test("Cross-user isolation: Bob cannot read or write Alice's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const ts = pastTime(10000);
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      lastDeviceModel: "sdk_gphone",
      lastAndroidRelease: "14",
      lastBuildType: "userdebug",
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("users").doc(ALICE_UID).collection("test_items").doc("a1").set({
      userId: ALICE_UID,
      title: "Alice secret note",
      notes: "",
      category: "DEVICE",
      completed: false,
      deviceTag: "sdk_gphone",
      sdkInt: 34,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("settings").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("test_items").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("test_items").doc("a1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("test_items").doc("a1").update({
      completed: true,
      updatedAt: pastTime(500),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("test_items").doc("a1").delete());
});

test("ID poisoning guard rejects oversized or invalid document IDs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badId = "bad id with spaces!";
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("test_items").doc(badId).set({
      userId: ALICE_UID,
      title: "Bad ID",
      notes: "",
      category: "GENERAL",
      completed: false,
      deviceTag: "sdk_gphone",
      sdkInt: 34,
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );
});
