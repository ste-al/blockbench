const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

function validProfile(uid, extra = {}) {
  const ts = pastTimestamp();
  return {
    uid,
    displayName: "Alice Miller",
    neighborhoodLabel: "Oak & 14th St",
    activeCircleId: "",
    activeCircleName: "",
    activeInviteCode: "",
    trustScore: 5.0,
    completedHandoffs: 0,
    onTimeReturns: 0,
    totalEarnedCents: 0,
    totalFeesSavedOrPaidCents: 0,
    proAlertsEnabled: false,
    alertRadiusMiles: 1.5,
    alertCategories: ["Power Tools"],
    createdAt: ts,
    updatedAt: ts,
    ...extra,
  };
}

function validCircle(creatorUid, extra = {}) {
  const ts = pastTimestamp();
  return {
    creatorUid,
    name: "Oak & 14th Block",
    inviteCode: "OAK482",
    radiusMiles: 1.5,
    memberUids: [creatorUid],
    createdAt: ts,
    updatedAt: ts,
    ...extra,
  };
}

function validListing(circleId, ownerUid, memberUids, extra = {}) {
  const ts = pastTimestamp();
  return {
    circleId,
    ownerUid,
    ownerName: "Alice Miller",
    ownerTrustScore: 4.9,
    memberUids,
    kind: "tool",
    title: "DeWalt Pressure Washer 2100 PSI",
    description: "Includes 25ft hose and patio nozzle.",
    category: "Yard & Outdoor",
    rateCents: 1800,
    depositCents: 4000,
    distanceMiles: 0.3,
    instantPickup: true,
    photoUri: "",
    status: "available",
    createdAt: ts,
    updatedAt: ts,
    ...extra,
  };
}

function validBooking(listingId, circleId, requesterUid, providerUid, extra = {}) {
  const ts = pastTimestamp();
  return {
    listingId,
    listingTitle: "DeWalt Pressure Washer 2100 PSI",
    listingKind: "tool",
    circleId,
    requesterUid,
    requesterName: "Bob Carter",
    providerUid,
    providerName: "Alice Miller",
    participantUids: [requesterUid, providerUid],
    grossAmountCents: 1800,
    depositCents: 4000,
    platformFeeCents: 90,
    providerNetCents: 1710,
    status: "requested",
    notes: "Porch locker #2",
    createdAt: ts,
    updatedAt: ts,
    ...extra,
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

test("Unauthenticated caller is denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("circles").get());
  await assertFails(unauthDb.collection("circleInvites").doc("OAK482").get());
  await assertFails(unauthDb.collection("listings").get());
  await assertFails(unauthDb.collection("bookings").get());
});

test("User profile: owner isolation, shadow field rejection, and valid CRUD", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Shadow field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID, { isAdmin: true }))
  );

  // Valid create succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );

  // Cross-user read/write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked", updatedAt: pastTimestamp() })
  );

  // Owner partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      proAlertsEnabled: true,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Circles and invite codes: non-listable invites, additive join, and listing discovery", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Alice creates circle + invite code
  await assertSucceeds(
    aliceDb.collection("circles").doc("circle_oak").set(validCircle(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("circleInvites").doc("OAK482").set({
      inviteCode: "OAK482",
      circleId: "circle_oak",
      circleName: "Oak & 14th Block",
      creatorUid: ALICE_UID,
      createdAt: pastTimestamp(),
    })
  );

  // Alice creates listing in circle
  await assertSucceeds(
    aliceDb.collection("listings").doc("listing_washer").set(
      validListing("circle_oak", ALICE_UID, [ALICE_UID])
    )
  );

  // Bob cannot list circleInvites or unfiltered listings
  await assertFails(bobDb.collection("circleInvites").get());
  await assertFails(bobDb.collection("listings").get());
  await assertFails(bobDb.collection("listings").doc("listing_washer").get());

  // Bob can resolve exact invite code via get(), join circle, and sync listing memberUids
  await assertSucceeds(bobDb.collection("circleInvites").doc("OAK482").get());
  await assertSucceeds(
    bobDb.collection("circles").doc("circle_oak").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb.collection("listings").doc("listing_washer").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastTimestamp(),
    })
  );

  // Now Bob's scoped query succeeds
  await assertSucceeds(
    bobDb.collection("listings").where("memberUids", "array-contains", BOB_UID).get()
  );
});

test("Bookings escrow state machine: participant isolation, step ordering, and terminal lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  await aliceDb.collection("circles").doc("circle_oak").set(validCircle(ALICE_UID));
  await bobDb.collection("circles").doc("circle_oak").update({
    memberUids: [ALICE_UID, BOB_UID],
    updatedAt: pastTimestamp(),
  });
  await aliceDb.collection("listings").doc("listing_washer").set(
    validListing("circle_oak", ALICE_UID, [ALICE_UID, BOB_UID])
  );

  // Bob creates escrow request
  await assertSucceeds(
    bobDb.collection("bookings").doc("booking_1").set(
      validBooking("listing_washer", "circle_oak", BOB_UID, ALICE_UID)
    )
  );

  // Charlie cannot read or query bookings
  await assertFails(charlieDb.collection("bookings").doc("booking_1").get());
  await assertFails(charlieDb.collection("bookings").get());

  // Cannot skip directly from requested -> completed
  await assertFails(
    aliceDb.collection("bookings").doc("booking_1").update({
      status: "completed",
      updatedAt: pastTimestamp(),
    })
  );

  // Cannot tamper with financial fields
  await assertFails(
    aliceDb.collection("bookings").doc("booking_1").update({
      status: "escrow_held",
      platformFeeCents: 0,
      updatedAt: pastTimestamp(),
    })
  );

  // Valid sequential transitions: requested -> escrow_held -> in_progress -> completed
  await assertSucceeds(
    aliceDb.collection("bookings").doc("booking_1").update({
      status: "escrow_held",
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb.collection("bookings").doc("booking_1").update({
      status: "in_progress",
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("bookings").doc("booking_1").update({
      status: "completed",
      updatedAt: pastTimestamp(),
    })
  );

  // Terminal state lock: no further updates or deletes allowed
  await assertFails(
    aliceDb.collection("bookings").doc("booking_1").update({
      status: "cancelled",
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(aliceDb.collection("bookings").doc("booking_1").delete());
});

test("Listings owner edits and non-creator circle leave follow rules", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await aliceDb.collection("circles").doc("circle_oak").set(validCircle(ALICE_UID));
  await bobDb.collection("circles").doc("circle_oak").update({
    memberUids: [ALICE_UID, BOB_UID],
    updatedAt: pastTimestamp(),
  });
  await aliceDb.collection("listings").doc("listing_washer").set(
    validListing("circle_oak", ALICE_UID, [ALICE_UID, BOB_UID])
  );

  // Owner can edit listing details
  await assertSucceeds(
    aliceDb.collection("listings").doc("listing_washer").update({
      title: "DeWalt Pressure Washer 2500 PSI",
      rateCents: 2200,
      distanceMiles: 0.8,
      updatedAt: pastTimestamp(),
    })
  );

  // Non-owner cannot edit listing details
  await assertFails(
    bobDb.collection("listings").doc("listing_washer").update({
      title: "Tampered Title",
      updatedAt: pastTimestamp(),
    })
  );

  // Creator cannot leave own circle via Action 3
  await assertFails(
    aliceDb.collection("circles").doc("circle_oak").update({
      memberUids: [BOB_UID],
      updatedAt: pastTimestamp(),
    })
  );

  // Non-creator member can leave circle
  await assertSucceeds(
    bobDb.collection("circles").doc("circle_oak").update({
      memberUids: [ALICE_UID],
      updatedAt: pastTimestamp(),
    })
  );
});

