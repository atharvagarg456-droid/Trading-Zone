const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_atharva_uid";
const ADMIN_EMAIL = "atharvagarg456@gmail.com";
const ALICE_UID = "alice_123";
const ALICE_EMAIL = "alice@example.com";
const BOB_UID = "bob_456";
const BOB_EMAIL = "bob@example.com";

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

function validUserPayload(uid, email, overrides = {}) {
  const now = new Date();
  return {
    userId: uid,
    email: email,
    fullName: "Alice Trader",
    phoneNumber: "+919876543210",
    city: "Mumbai",
    photoBase64: "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
    deviceId: "ANDROID_DEV_001",
    deviceModel: "Pixel 9 Pro",
    ipAddress: "192.168.1.10",
    accountStatus: "PENDING_APPROVAL",
    entitlementStatus: "NONE",
    trialUsed: false,
    validUntilMillis: 0,
    activePlanName: "Monthly Access",
    appLockPin: "1234",
    securityQuestion: "What is my favorite commodity?",
    securityAnswer: "Gold",
    termsAccepted: true,
    isCommodityMuted: false,
    hasPendingProfileEdit: false,
    pendingFullName: "",
    pendingPhoneNumber: "",
    pendingCity: "",
    pendingPhotoBase64: "",
    dailyReviewCount: 0,
    lastReviewDate: "2026-11-10",
    adminEmails: [ADMIN_EMAIL],
    lastActiveAt: now,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("broadcast_messages").where("visibility", "==", "VERIFIED_MEMBERS").get());
  await assertFails(unauthDb.collection("message_views").doc("msg1_alice").get());
  await assertFails(unauthDb.collection("community_reviews").where("visibility", "==", "VERIFIED_MEMBERS").get());
  await assertFails(unauthDb.collection("moderation_alerts").doc("alert_1").get());
  await assertFails(unauthDb.collection("presence").where("visibility", "==", "PUBLIC_PRESENCE").get());
  await assertFails(unauthDb.collection("app_config").doc("pricing").get());
  await assertFails(unauthDb.collection("device_registry").doc("ANDROID_DEV_001").get());
});

test("Regular user can register profile with appLockPin, request profile edit, and record message read receipt", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true })
    .firestore();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, ALICE_EMAIL))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      hasPendingProfileEdit: true,
      pendingFullName: "Alice Sharma",
      pendingCity: "Delhi",
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("message_views").doc(`msg_1_${ALICE_UID}`).set({
      messageId: "msg_1",
      userId: ALICE_UID,
      userName: "Alice Trader",
      userEmail: ALICE_EMAIL,
      userPhotoBase64: "photo",
      adminEmails: [ADMIN_EMAIL],
      viewedAt: now,
    })
  );

  await assertFails(bobDb.collection("message_views").doc(`msg_1_${ALICE_UID}`).get());
  await assertSucceeds(adminDb.collection("message_views").doc(`msg_1_${ALICE_UID}`).get());
});

test("Primary admin can publish messages with WhatsApp quoted reply fields and manage Commodity chat lock", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const now = new Date();

  await assertSucceeds(
    adminDb.collection("broadcast_messages").doc("msg_2").set({
      title: "MCX Gold Target 1 Hit — Trail SL",
      content: "Book 50% lots here and trail stop-loss to cost.",
      category: "Commodity",
      riskLevel: "MEDIUM",
      markedPhotoBase64: "base64_marked_chart",
      linkUrl: "",
      linkTitle: "",
      replyToMessageId: "msg_1",
      replyToTitle: "MCX Gold & Nifty Setup",
      replyToSnippet: "Key support levels marked on the attached chart.",
      replyToRiskLevel: "HIGH",
      isPinned: true,
      isEdited: false,
      authorId: ADMIN_UID,
      authorName: "Atharva Garg",
      visibility: "VERIFIED_MEMBERS",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Commodity review pinning & phone/ID moderation alert rules enforce ownership and admin-only alert reading", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true })
    .firestore();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("community_reviews").doc("rev_1").set({
      userId: ALICE_UID,
      userName: "Alice Trader",
      userPhotoBase64: "photo",
      tradeMessageId: "msg_1",
      tradeMessageTitle: "MCX Gold Setup",
      tradeRiskLevel: "HIGH",
      topicTag: "Commodity",
      publicContent: "Strong bullish momentum on Gold above 74500",
      hasBlurredWords: false,
      rating: 5,
      isPinned: false,
      visibility: "VERIFIED_MEMBERS",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    bobDb.collection("community_reviews").doc("rev_1").update({
      isPinned: true,
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    adminDb.collection("community_reviews").doc("rev_1").update({
      isPinned: true,
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("moderation_alerts").doc("alert_phone_1").set({
      userId: ALICE_UID,
      userEmail: ALICE_EMAIL,
      userName: "Alice Trader",
      userPhotoBase64: "photo",
      deviceId: "ANDROID_DEV_001",
      ipAddress: "192.168.1.10",
      tradeMessageTitle: "Commodity [BLOCKED PHONE / ID]",
      originalUneditedText: "Call me on 9876543210 for gold tips",
      blurredText: "[BLOCKED: Attempted to share Phone Number / Personal ID]",
      detectedWords: "Phone/ID: 9876543210",
      status: "OPEN",
      adminEmails: [ADMIN_EMAIL],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(bobDb.collection("moderation_alerts").doc("alert_phone_1").get());
  await assertSucceeds(adminDb.collection("moderation_alerts").doc("alert_phone_1").get());
});
