// Security-rules tests for firestore.rules.
//
// Run with `npm test` (starts the Firestore emulator for you), or against an already running
// emulator with `npm run test:rules`.
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, describe, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const {
  arrayRemove,
  arrayUnion,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  setLogLevel,
  updateDoc,
  where,
  writeBatch,
} = require("firebase/firestore");

// Denied writes are the point of half of these tests; keep the SDK from logging each of them.
setLogLevel("silent");

const PROJECT_ID = process.env.GCLOUD_PROJECT || "demo-no-project";
const [EMULATOR_HOST, EMULATOR_PORT] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");

const ALICE = "alice_123";
const BOB = "bob_456";
const CHARLIE = "charlie_789";

const CIRCLE = "circle_oak";
const CODE = "OAK7K2MQ";
const LISTING = "listing_washer";
const BOOKING = "booking_1";

// A tiny but real base64 payload is enough: the rules validate the shape, not the pixels.
const PHOTO = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAMCAgICAgMCAgIDAwMDBAYEBAQEBAgGBgUGCQgKCgkICQkKDA8MCgsOCwkJDRENDg8QEBEQCgwSExIQEw8QEBD/yQALCAABAAEBAREA/8wABgAQEAX/2gAIAQEAAD8A0s8g/9k=";

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync(path.join(__dirname, "firestore.rules"), "utf8"),
      host: EMULATOR_HOST,
      port: Number(EMULATOR_PORT),
    },
  });
});

after(async () => {
  await testEnv?.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

// ------------------------------------------------------------------ fixtures

/** A Firestore handle authenticated as `uid`. Keep one per user per test: handles can't be mixed. */
function as(uid) {
  return testEnv.authenticatedContext(uid).firestore();
}

function anonymous() {
  return testEnv.unauthenticatedContext().firestore();
}

/** Writes documents with rules switched off, e.g. to stage data older clients left behind. */
async function seed(writes) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    for (const [docPath, data] of Object.entries(writes)) {
      await setDoc(doc(db, docPath), data);
    }
  });
}

const longAgo = () => new Date("2020-01-01T00:00:00Z");

function profile(uid, overrides = {}) {
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
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

function circle(creatorUid, overrides = {}) {
  return {
    creatorUid,
    name: "Oak & 14th Block",
    inviteCode: CODE,
    radiusMiles: 1.5,
    memberUids: [creatorUid],
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

function invite(creatorUid, overrides = {}) {
  return {
    inviteCode: CODE,
    circleId: CIRCLE,
    circleName: "Oak & 14th Block",
    creatorUid,
    createdAt: serverTimestamp(),
    ...overrides,
  };
}

function listing(ownerUid, overrides = {}) {
  return {
    circleId: CIRCLE,
    ownerUid,
    ownerName: "Alice Miller",
    ownerTrustScore: 4.9,
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
    activeBookingId: "",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

/** Bob borrowing Alice's pressure washer, with the terms copied from `listing(ALICE)`. */
function toolBooking(overrides = {}) {
  return {
    listingId: LISTING,
    listingTitle: "DeWalt Pressure Washer 2100 PSI",
    listingKind: "tool",
    circleId: CIRCLE,
    requesterUid: BOB,
    requesterName: "Bob Carter",
    providerUid: ALICE,
    providerName: "Alice Miller",
    participantUids: [BOB, ALICE],
    grossAmountCents: 1800,
    depositCents: 4000,
    platformFeeCents: 90,
    providerNetCents: 1710,
    status: "requested",
    notes: "Pickup at 5pm",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

const touch = (fields = {}) => ({ ...fields, updatedAt: serverTimestamp() });

// --------------------------------------------------------- client-like flows

/** Creates the circle and its invite in one batch, the way the app does. */
function createCircle(db, { circleId = CIRCLE, code = CODE, circleData, inviteData } = {}) {
  const batch = writeBatch(db);
  batch.set(doc(db, "circles", circleId), circleData);
  batch.set(doc(db, "circleInvites", code), inviteData);
  return batch.commit();
}

function joinCircle(db, uid, circleId = CIRCLE) {
  return updateDoc(doc(db, "circles", circleId), touch({ memberUids: arrayUnion(uid) }));
}

function leaveCircle(db, uid, circleId = CIRCLE) {
  return updateDoc(doc(db, "circles", circleId), touch({ memberUids: arrayRemove(uid) }));
}

/** Creates a booking and reserves its listing in one batch, the way the app does. */
function book(db, bookingData, { bookingId = BOOKING, listingId = LISTING } = {}) {
  const batch = writeBatch(db);
  batch.set(doc(db, "bookings", bookingId), bookingData);
  batch.update(doc(db, "listings", listingId), touch({ status: "reserved", activeBookingId: bookingId }));
  return batch.commit();
}

/** Moves a booking to `status`; `release` also frees the listing in the same batch. */
function transition(db, status, { bookingId = BOOKING, listingId = LISTING, release = false, notes } = {}) {
  const batch = writeBatch(db);
  batch.update(doc(db, "bookings", bookingId), touch(notes === undefined ? { status } : { status, notes }));
  if (release) {
    batch.update(doc(db, "listings", listingId), touch({ status: "available", activeBookingId: "" }));
  }
  return batch.commit();
}

/** Alice's circle with Bob as a member and (unless told otherwise) Alice's listing in it. */
async function blockWithAliceAndBob({ listingData = listing(ALICE) } = {}) {
  const alice = as(ALICE);
  const bob = as(BOB);
  await createCircle(alice, { circleData: circle(ALICE), inviteData: invite(ALICE) });
  await joinCircle(bob, BOB);
  if (listingData) await setDoc(doc(alice, "listings", LISTING), listingData);
  return { alice, bob };
}

// ---------------------------------------------------------------------- tests

describe("signed-out callers", () => {
  test("are denied everywhere", async () => {
    await seed({
      [`users/${ALICE}`]: profile(ALICE, { createdAt: longAgo(), updatedAt: longAgo() }),
      [`circles/${CIRCLE}`]: circle(ALICE, { createdAt: longAgo(), updatedAt: longAgo() }),
      [`circleInvites/${CODE}`]: invite(ALICE, { createdAt: longAgo() }),
    });
    const db = anonymous();
    await assertFails(getDoc(doc(db, "users", ALICE)));
    await assertFails(getDoc(doc(db, "circles", CIRCLE)));
    await assertFails(getDoc(doc(db, "circleInvites", CODE)));
    await assertFails(getDocs(collection(db, "circles")));
    await assertFails(getDocs(query(collection(db, "listings"), where("circleId", "==", CIRCLE))));
    await assertFails(getDocs(collection(db, "bookings")));
    await assertFails(setDoc(doc(db, "users", ALICE), profile(ALICE)));
  });
});

describe("users", () => {
  test("owner creates, reads and updates their own profile", async () => {
    const alice = as(ALICE);
    await assertSucceeds(setDoc(doc(alice, "users", ALICE), profile(ALICE)));
    await assertSucceeds(getDoc(doc(alice, "users", ALICE)));
    await assertSucceeds(
      updateDoc(doc(alice, "users", ALICE), touch({ proAlertsEnabled: true, alertRadiusMiles: 2.5 }))
    );
  });

  test("nobody else can read or write a profile, and profiles cannot be listed", async () => {
    const alice = as(ALICE);
    const bob = as(BOB);
    await setDoc(doc(alice, "users", ALICE), profile(ALICE));
    await assertFails(getDoc(doc(bob, "users", ALICE)));
    await assertFails(updateDoc(doc(bob, "users", ALICE), touch({ displayName: "Hacked" })));
    await assertFails(setDoc(doc(bob, "users", ALICE), profile(ALICE)));
    // ...including one that does not exist yet.
    await assertFails(setDoc(doc(bob, "users", CHARLIE), profile(CHARLIE)));
    await assertFails(getDocs(collection(alice, "users")));
  });

  test("create rejects unknown, missing, mismatched or back-dated fields", async () => {
    const alice = as(ALICE);
    const target = doc(alice, "users", ALICE);
    await assertFails(setDoc(target, profile(ALICE, { isAdmin: true })));
    await assertFails(setDoc(target, profile(BOB)));
    await assertFails(setDoc(target, profile(ALICE, { createdAt: longAgo() })));
    await assertFails(setDoc(target, profile(ALICE, { updatedAt: longAgo() })));
    await assertFails(setDoc(target, profile(ALICE, { trustScore: 9 })));
    await assertFails(setDoc(target, profile(ALICE, { displayName: "" })));
    const { alertCategories, ...withoutCategories } = profile(ALICE);
    await assertFails(setDoc(target, withoutCategories));
  });

  test("update cannot touch uid or createdAt, leave valid ranges, or back-date updatedAt", async () => {
    const alice = as(ALICE);
    const target = doc(alice, "users", ALICE);
    await setDoc(target, profile(ALICE));
    await assertFails(updateDoc(target, touch({ uid: BOB })));
    await assertFails(updateDoc(target, touch({ createdAt: serverTimestamp() })));
    await assertFails(updateDoc(target, touch({ alertRadiusMiles: 50 })));
    await assertFails(updateDoc(target, touch({ completedHandoffs: -1 })));
    await assertFails(updateDoc(target, touch({ isAdmin: true })));
    await assertFails(updateDoc(target, { proAlertsEnabled: true, updatedAt: longAgo() }));
    await assertFails(updateDoc(target, { proAlertsEnabled: true }));
  });
});

describe("circles and invite codes", () => {
  test("a circle and its invite are created together", async () => {
    const alice = as(ALICE);
    await assertSucceeds(
      createCircle(alice, { circleData: circle(ALICE), inviteData: invite(ALICE) })
    );
    await assertSucceeds(getDoc(doc(alice, "circles", CIRCLE)));
  });

  test("neither half can be created on its own", async () => {
    const alice = as(ALICE);
    await assertFails(setDoc(doc(alice, "circles", CIRCLE), circle(ALICE)));
    await assertFails(setDoc(doc(alice, "circleInvites", CODE), invite(ALICE)));
  });

  test("the pair must agree and belong to the caller", async () => {
    const alice = as(ALICE);
    const attempt = (circleData, inviteData) =>
      assertFails(createCircle(alice, { circleData, inviteData }));

    await attempt(circle(ALICE), invite(ALICE, { circleName: "Some Other Name" }));
    await attempt(circle(ALICE), invite(ALICE, { circleId: "another_circle" }));
    await attempt(circle(ALICE, { inviteCode: "PINE1234" }), invite(ALICE));
    await attempt(circle(ALICE), invite(ALICE, { inviteCode: "ZZZZ9999" }));
    await attempt(circle(BOB), invite(ALICE));
    await attempt(circle(ALICE), invite(BOB));
    await attempt(circle(ALICE, { memberUids: [ALICE, BOB] }), invite(ALICE));
    await attempt(circle(ALICE, { memberUids: [BOB] }), invite(ALICE));
    await attempt(circle(ALICE, { createdAt: longAgo() }), invite(ALICE));
    await attempt(circle(ALICE), invite(ALICE, { createdAt: longAgo() }));
    await attempt(circle(ALICE, { name: "X" }), invite(ALICE, { circleName: "X" }));
    await attempt(circle(ALICE, { radiusMiles: 500 }), invite(ALICE));
    await attempt(circle(ALICE, { owner: ALICE }), invite(ALICE));
    await attempt(circle(ALICE), invite(ALICE, { expiresAt: longAgo() }));
  });

  test("invite codes must be 4-12 upper-case letters or digits", async () => {
    const alice = as(ALICE);
    for (const code of ["oak482", "OAK", "OAK-482", "ABCDEFGHIJKLM"]) {
      await assertFails(
        createCircle(alice, {
          code,
          circleData: circle(ALICE, { inviteCode: code }),
          inviteData: invite(ALICE, { inviteCode: code }),
        })
      );
    }
  });

  test("an invite code that is already taken cannot be claimed by another circle", async () => {
    const alice = as(ALICE);
    const bob = as(BOB);
    await createCircle(alice, { circleData: circle(ALICE), inviteData: invite(ALICE) });

    // Bob's whole batch is refused, so no orphan circle is left behind.
    await assertFails(
      createCircle(bob, {
        circleId: "circle_bob",
        circleData: circle(BOB),
        inviteData: invite(BOB, { circleId: "circle_bob" }),
      })
    );
    const stillAlice = await getDoc(doc(alice, "circleInvites", CODE));
    assert.equal(stillAlice.data().circleId, CIRCLE);
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const orphan = await getDoc(doc(context.firestore(), "circles", "circle_bob"));
      assert.equal(orphan.exists(), false);
    });
  });

  test("one invite cannot serve two circles, and a circle has exactly one code", async () => {
    const alice = as(ALICE);

    // Two circles created in one batch cannot share a single invite document.
    const twins = writeBatch(alice);
    twins.set(doc(alice, "circles", CIRCLE), circle(ALICE));
    twins.set(doc(alice, "circles", "circle_twin"), circle(ALICE));
    twins.set(doc(alice, "circleInvites", CODE), invite(ALICE));
    await assertFails(twins.commit());

    await createCircle(alice, { circleData: circle(ALICE), inviteData: invite(ALICE) });
    await assertFails(
      setDoc(doc(alice, "circleInvites", "EXTRA123"), invite(ALICE, { inviteCode: "EXTRA123" }))
    );
  });

  test("a leftover invite cannot be adopted by recreating its circle id", async () => {
    await seed({ [`circleInvites/${CODE}`]: invite(ALICE, { createdAt: longAgo() }) });
    await assertFails(setDoc(doc(as(BOB), "circles", CIRCLE), circle(BOB)));
    await assertFails(setDoc(doc(as(ALICE), "circles", CIRCLE), circle(ALICE)));
  });

  test("invites are fetched by exact code, never listed, never edited", async () => {
    const alice = as(ALICE);
    const bob = as(BOB);
    await createCircle(alice, { circleData: circle(ALICE), inviteData: invite(ALICE) });

    await assertSucceeds(getDoc(doc(bob, "circleInvites", CODE)));
    await assertFails(getDocs(collection(bob, "circleInvites")));
    await assertFails(getDocs(query(collection(bob, "circleInvites"), where("circleId", "==", CIRCLE))));
    await assertFails(updateDoc(doc(alice, "circleInvites", CODE), { circleId: "elsewhere" }));
    await assertFails(setDoc(doc(bob, "circleInvites", CODE), invite(BOB)));
  });

  test("only members can read a circle; the membership query works, an open one does not", async () => {
    const alice = as(ALICE);
    const bob = as(BOB);
    await createCircle(alice, { circleData: circle(ALICE), inviteData: invite(ALICE) });

    await assertFails(getDoc(doc(bob, "circles", CIRCLE)));
    await assertFails(getDocs(collection(alice, "circles")));
    const mine = await assertSucceeds(
      getDocs(query(collection(alice, "circles"), where("memberUids", "array-contains", ALICE)))
    );
    assert.equal(mine.size, 1);
    await assertFails(
      getDocs(query(collection(bob, "circles"), where("memberUids", "array-contains", ALICE)))
    );
  });

  test("a neighbour joins by adding exactly themselves", async () => {
    const alice = as(ALICE);
    const bob = as(BOB);
    await createCircle(alice, { circleData: circle(ALICE), inviteData: invite(ALICE) });

    await assertFails(joinCircle(bob, CHARLIE));
    await assertFails(
      updateDoc(doc(bob, "circles", CIRCLE), touch({ memberUids: [ALICE, BOB, CHARLIE] }))
    );
    await assertFails(updateDoc(doc(bob, "circles", CIRCLE), touch({ memberUids: [BOB] })));
    await assertFails(
      updateDoc(doc(bob, "circles", CIRCLE), touch({ memberUids: arrayUnion(BOB), name: "Bob's" }))
    );
    await assertFails(updateDoc(doc(bob, "circles", CIRCLE), { memberUids: arrayUnion(BOB) }));

    await assertSucceeds(joinCircle(bob, BOB));
    await assertSucceeds(getDoc(doc(bob, "circles", CIRCLE)));
    // Joining twice is not a valid write; the app checks membership first instead.
    await assertFails(joinCircle(bob, BOB));
    await assertFails(updateDoc(doc(bob, "circles", CIRCLE), touch({ memberUids: [ALICE, BOB, BOB] })));
  });

  test("a circle holds at most 50 neighbours", async () => {
    const members = Array.from({ length: 50 }, (_, i) => `neighbour_${i}`);
    await seed({
      [`circles/${CIRCLE}`]: circle(members[0], {
        memberUids: members,
        createdAt: longAgo(),
        updatedAt: longAgo(),
      }),
    });
    await assertFails(joinCircle(as(BOB), BOB));
  });

  test("a member leaves by removing exactly themselves; the creator cannot leave", async () => {
    const { alice, bob } = await blockWithAliceAndBob({ listingData: null });
    const charlie = as(CHARLIE);
    await joinCircle(charlie, CHARLIE);

    await assertFails(leaveCircle(alice, ALICE));
    await assertFails(leaveCircle(bob, CHARLIE));
    await assertFails(updateDoc(doc(bob, "circles", CIRCLE), touch({ memberUids: [ALICE] })));
    await assertSucceeds(leaveCircle(bob, BOB));
    await assertFails(getDoc(doc(bob, "circles", CIRCLE)));
    const after = await getDoc(doc(alice, "circles", CIRCLE));
    assert.deepEqual(after.data().memberUids, [ALICE, CHARLIE]);
  });

  test("only the creator edits name and radius; identity fields never change", async () => {
    const { alice, bob } = await blockWithAliceAndBob({ listingData: null });
    const target = (db) => doc(db, "circles", CIRCLE);

    await assertSucceeds(updateDoc(target(alice), touch({ name: "Oak Street", radiusMiles: 2 })));
    await assertFails(updateDoc(target(bob), touch({ name: "Bob Street" })));
    await assertFails(updateDoc(target(alice), touch({ inviteCode: "NEWCODE1" })));
    await assertFails(updateDoc(target(alice), touch({ creatorUid: BOB })));
    await assertFails(updateDoc(target(alice), touch({ createdAt: serverTimestamp() })));
    await assertFails(updateDoc(target(alice), touch({ memberUids: [ALICE] })));
    await assertFails(updateDoc(target(alice), { name: "No timestamp" }));
  });

  test("an invite outlives nothing but its circle", async () => {
    const { alice, bob } = await blockWithAliceAndBob({ listingData: null });

    await assertFails(deleteDoc(doc(alice, "circleInvites", CODE)));
    await assertFails(deleteDoc(doc(bob, "circles", CIRCLE)));

    const batch = writeBatch(alice);
    batch.delete(doc(alice, "circles", CIRCLE));
    batch.delete(doc(alice, "circleInvites", CODE));
    await assertSucceeds(batch.commit());
  });
});

describe("listings", () => {
  test("members post; outsiders and impostors cannot", async () => {
    const { alice, bob } = await blockWithAliceAndBob({ listingData: null });
    const charlie = as(CHARLIE);

    await assertSucceeds(setDoc(doc(alice, "listings", LISTING), listing(ALICE)));
    await assertSucceeds(setDoc(doc(bob, "listings", "listing_ladder"), listing(BOB)));
    await assertFails(setDoc(doc(charlie, "listings", "listing_x"), listing(CHARLIE)));
    await assertFails(setDoc(doc(bob, "listings", "listing_y"), listing(ALICE)));
    await assertFails(
      setDoc(doc(bob, "listings", "listing_z"), listing(BOB, { circleId: "no_such_circle" }))
    );
  });

  test("create validates every field", async () => {
    const { alice } = await blockWithAliceAndBob({ listingData: null });
    const attempt = (overrides) =>
      assertFails(setDoc(doc(alice, "listings", LISTING), listing(ALICE, overrides)));

    await attempt({ status: "reserved" });
    await attempt({ status: "paused" });
    await attempt({ activeBookingId: "booking_x" });
    await attempt({ memberUids: [ALICE] });
    await attempt({ kind: "service" });
    await attempt({ title: "ab" });
    await attempt({ rateCents: 99 });
    await attempt({ rateCents: 18.5 });
    await attempt({ rateCents: 500001 });
    await attempt({ depositCents: -1 });
    await attempt({ distanceMiles: 0 });
    await attempt({ ownerTrustScore: 5.1 });
    await attempt({ createdAt: longAgo() });
    await attempt({ updatedAt: longAgo() });
    await attempt({ kind: "micro_job", depositCents: 500 });
    const { activeBookingId, ...withoutPointer } = listing(ALICE);
    await assertFails(setDoc(doc(alice, "listings", LISTING), withoutPointer));

    await assertSucceeds(
      setDoc(doc(alice, "listings", LISTING), listing(ALICE, { kind: "micro_job", depositCents: 0 }))
    );
  });

  test("photos are inline JPEG thumbnails, never links", async () => {
    const { alice } = await blockWithAliceAndBob({ listingData: null });
    const post = (id, photoUri) => setDoc(doc(alice, "listings", id), listing(ALICE, { photoUri }));

    await assertSucceeds(post("with_photo", PHOTO));
    await assertFails(post("tracker", "https://attacker.example/pixel.jpg"));
    await assertFails(post("local", "content://media/picker/0/com.android.providers.media.photopicker/media/33"));
    await assertFails(post("svg", "data:image/svg+xml;base64,PHN2Zz48L3N2Zz4="));
    await assertFails(post("not_base64", "data:image/jpeg;base64,<script>"));
    await assertFails(post("huge", "data:image/jpeg;base64," + "A".repeat(60000)));

    await assertSucceeds(updateDoc(doc(alice, "listings", "with_photo"), touch({ photoUri: "" })));
    await assertFails(
      updateDoc(doc(alice, "listings", "with_photo"), touch({ photoUri: "https://attacker.example/p.jpg" }))
    );
  });

  test("the feed is readable by current members only, and only per circle", async () => {
    const { alice, bob } = await blockWithAliceAndBob();
    const charlie = as(CHARLIE);
    const feed = (db, circleId = CIRCLE) =>
      getDocs(query(collection(db, "listings"), where("circleId", "==", circleId)));

    assert.equal((await assertSucceeds(feed(alice))).size, 1);
    assert.equal((await assertSucceeds(feed(bob))).size, 1);
    await assertSucceeds(getDoc(doc(bob, "listings", LISTING)));

    await assertFails(feed(charlie));
    await assertFails(getDoc(doc(charlie, "listings", LISTING)));
    // A listing that does not exist reads as "not found" for anyone signed in, not as "denied".
    const missing = await assertSucceeds(getDoc(doc(charlie, "listings", "no_such_listing")));
    assert.equal(missing.exists(), false);
    await assertFails(getDoc(doc(anonymous(), "listings", "no_such_listing")));
    await assertFails(getDocs(collection(alice, "listings")));
    await assertFails(getDocs(query(collection(alice, "listings"), where("ownerUid", "==", ALICE))));
    await assertFails(feed(alice, "some_other_circle"));
  });

  test("a new member sees existing listings at once; a member who leaves loses them at once", async () => {
    const alice = as(ALICE);
    const bob = as(BOB);
    await createCircle(alice, { circleData: circle(ALICE), inviteData: invite(ALICE) });
    await setDoc(doc(alice, "listings", LISTING), listing(ALICE));
    const feed = () => getDocs(query(collection(bob, "listings"), where("circleId", "==", CIRCLE)));

    await assertFails(feed());
    await joinCircle(bob, BOB);
    assert.equal((await assertSucceeds(feed())).size, 1);
    await leaveCircle(bob, BOB);
    await assertFails(feed());
    await assertFails(getDoc(doc(bob, "listings", LISTING)));
  });

  test("the owner edits details and pauses / resumes", async () => {
    const { alice } = await blockWithAliceAndBob();
    const target = doc(alice, "listings", LISTING);

    await assertSucceeds(
      updateDoc(target, touch({ title: "DeWalt Pressure Washer 2500 PSI", rateCents: 2200, distanceMiles: 0.8 }))
    );
    await assertSucceeds(updateDoc(target, touch({ status: "paused" })));
    await assertSucceeds(updateDoc(target, touch({ status: "available" })));
    await assertSucceeds(updateDoc(target, touch({ photoUri: PHOTO })));
  });

  test("the owner cannot move, re-own, retype, re-date or self-reserve a listing", async () => {
    const { alice } = await blockWithAliceAndBob();
    const target = doc(alice, "listings", LISTING);

    await assertFails(updateDoc(target, touch({ circleId: "another_circle" })));
    await assertFails(updateDoc(target, touch({ ownerUid: BOB })));
    await assertFails(updateDoc(target, touch({ kind: "micro_job", depositCents: 0 })));
    await assertFails(updateDoc(target, touch({ createdAt: serverTimestamp() })));
    await assertFails(updateDoc(target, touch({ status: "reserved" })));
    await assertFails(updateDoc(target, touch({ activeBookingId: "booking_x" })));
    await assertFails(updateDoc(target, touch({ rateCents: 5 })));
    await assertFails(updateDoc(target, { title: "No timestamp bump" }));
    await assertFails(updateDoc(target, { title: "Back-dated", updatedAt: longAgo() }));
  });

  test("other members cannot edit, pause, un-pause, reserve or delete someone else's listing", async () => {
    const { alice, bob } = await blockWithAliceAndBob();
    const target = doc(bob, "listings", LISTING);

    await assertFails(updateDoc(target, touch({ title: "Tampered Title" })));
    await assertFails(updateDoc(target, touch({ status: "paused" })));
    await assertFails(updateDoc(target, touch({ status: "reserved" })));
    await assertFails(updateDoc(target, touch({ status: "reserved", activeBookingId: "ghost_booking" })));
    await assertFails(deleteDoc(target));

    await updateDoc(doc(alice, "listings", LISTING), touch({ status: "paused" }));
    await assertFails(updateDoc(target, touch({ status: "available" })));
  });

  test("the owner deletes a listing unless a booking is in flight", async () => {
    const { alice, bob } = await blockWithAliceAndBob();
    await book(bob, toolBooking());

    await assertFails(deleteDoc(doc(alice, "listings", LISTING)));
    await assertFails(updateDoc(doc(alice, "listings", LISTING), touch({ status: "paused" })));
    await assertFails(updateDoc(doc(alice, "listings", LISTING), touch({ status: "available" })));
    // Details stay editable while reserved; the booking keeps its own copy of the terms.
    await assertSucceeds(updateDoc(doc(alice, "listings", LISTING), touch({ description: "Back by 8pm" })));

    await transition(bob, "cancelled", { release: true });
    await assertSucceeds(deleteDoc(doc(alice, "listings", LISTING)));
  });
});

describe("bookings", () => {
  test("tool rental: request, approve, hand off, complete — and the listing is free again", async () => {
    const { alice, bob } = await blockWithAliceAndBob();

    await assertSucceeds(book(bob, toolBooking()));
    let reserved = await getDoc(doc(alice, "listings", LISTING));
    assert.equal(reserved.data().status, "reserved");
    assert.equal(reserved.data().activeBookingId, BOOKING);

    await assertSucceeds(transition(alice, "escrow_held", { notes: "Side gate code 4812" }));
    await assertSucceeds(transition(bob, "in_progress"));
    await assertSucceeds(transition(alice, "completed", { release: true }));

    const freed = await getDoc(doc(alice, "listings", LISTING));
    assert.equal(freed.data().status, "available");
    assert.equal(freed.data().activeBookingId, "");
    const done = await getDoc(doc(bob, "bookings", BOOKING));
    assert.equal(done.data().status, "completed");
    assert.equal(done.data().providerNetCents, 1710);

    await assertSucceeds(book(bob, toolBooking(), { bookingId: "booking_2" }));
  });

  test("micro-job: the worker claims, the poster approves and confirms", async () => {
    const job = listing(ALICE, {
      kind: "micro_job",
      title: "Fix Backyard Gate Latch",
      rateCents: 2500,
      depositCents: 0,
    });
    const { alice, bob } = await blockWithAliceAndBob({ listingData: job });
    const claim = toolBooking({
      listingKind: "micro_job",
      listingTitle: "Fix Backyard Gate Latch",
      requesterUid: ALICE,
      requesterName: "Alice Miller",
      providerUid: BOB,
      providerName: "Bob Carter",
      participantUids: [ALICE, BOB],
      grossAmountCents: 2500,
      depositCents: 0,
      platformFeeCents: 125,
      providerNetCents: 2375,
    });

    // Bob cannot sign another neighbour up for the work...
    await joinCircle(as(CHARLIE), CHARLIE);
    await assertFails(
      book(bob, { ...claim, providerUid: CHARLIE, providerName: "Charlie", participantUids: [ALICE, CHARLIE] })
    );
    // ...only himself, and only the poster can then approve him.
    await assertSucceeds(book(bob, claim));
    await assertFails(transition(bob, "escrow_held"));
    await assertSucceeds(transition(alice, "escrow_held"));
    await assertSucceeds(transition(alice, "in_progress"));
    await assertFails(transition(bob, "completed", { release: true }));
    await assertSucceeds(transition(alice, "completed", { release: true }));
  });

  test("a booking and its reservation only exist together", async () => {
    const { bob } = await blockWithAliceAndBob();

    await assertFails(setDoc(doc(bob, "bookings", BOOKING), toolBooking()));
    await assertFails(
      updateDoc(doc(bob, "listings", LISTING), touch({ status: "reserved", activeBookingId: BOOKING }))
    );
    // The reservation may not carry any other change to the listing with it.
    const vandal = writeBatch(bob);
    vandal.set(doc(bob, "bookings", BOOKING), toolBooking());
    vandal.update(
      doc(bob, "listings", LISTING),
      touch({ status: "reserved", activeBookingId: BOOKING, description: "Edited by the borrower" })
    );
    await assertFails(vandal.commit());
    // Reserving for a different booking id than the one being created is refused too.
    const batch = writeBatch(bob);
    batch.set(doc(bob, "bookings", BOOKING), toolBooking());
    batch.update(doc(bob, "listings", LISTING), touch({ status: "reserved", activeBookingId: "other" }));
    await assertFails(batch.commit());
  });

  test("a listing cannot be double-booked", async () => {
    const { bob } = await blockWithAliceAndBob();
    const charlie = as(CHARLIE);
    await joinCircle(charlie, CHARLIE);
    await book(bob, toolBooking());

    const second = toolBooking({
      requesterUid: CHARLIE,
      requesterName: "Charlie",
      participantUids: [CHARLIE, ALICE],
    });
    await assertFails(book(charlie, second, { bookingId: "booking_2" }));
  });

  test("one write cannot create two bookings for the same listing", async () => {
    const { bob } = await blockWithAliceAndBob();
    const batch = writeBatch(bob);
    batch.set(doc(bob, "bookings", BOOKING), toolBooking());
    batch.set(doc(bob, "bookings", "booking_2"), toolBooking());
    batch.update(doc(bob, "listings", LISTING), touch({ status: "reserved", activeBookingId: BOOKING }));
    await assertFails(batch.commit());
  });

  test("paused listings cannot be booked", async () => {
    const { alice, bob } = await blockWithAliceAndBob();
    await updateDoc(doc(alice, "listings", LISTING), touch({ status: "paused" }));
    await assertFails(book(bob, toolBooking()));
  });

  test("the counterparty is always the listing owner, never a third party or oneself", async () => {
    const { alice, bob } = await blockWithAliceAndBob();
    // Charlie is a genuine member, so only the role checks stand in the way below.
    await joinCircle(as(CHARLIE), CHARLIE);

    // Naming another neighbour as the lender...
    await assertFails(
      book(bob, toolBooking({ providerUid: CHARLIE, participantUids: [BOB, CHARLIE] }))
    );
    // ...or booking on another neighbour's behalf.
    await assertFails(
      book(bob, toolBooking({ requesterUid: CHARLIE, participantUids: [CHARLIE, ALICE] }))
    );
    // Roles swapped: for a tool the booker pays, they cannot name themselves the earner.
    await assertFails(
      book(bob, toolBooking({ requesterUid: ALICE, providerUid: BOB, participantUids: [ALICE, BOB] }))
    );
    // The owner cannot book their own listing on a neighbour's behalf.
    await assertFails(book(alice, toolBooking()));
    await assertFails(
      book(alice, toolBooking({ requesterUid: ALICE, participantUids: [ALICE, ALICE] }))
    );
    await assertFails(book(bob, toolBooking({ participantUids: [ALICE, BOB] })));
  });

  test("money terms and labels must match the listing exactly", async () => {
    const { bob } = await blockWithAliceAndBob();
    const attempt = (overrides) => assertFails(book(bob, toolBooking(overrides)));

    await attempt({ grossAmountCents: 100, platformFeeCents: 5, providerNetCents: 95 });
    await attempt({ depositCents: 0 });
    await attempt({ platformFeeCents: 0, providerNetCents: 1800 });
    await attempt({ platformFeeCents: 89, providerNetCents: 1711 });
    await attempt({ providerNetCents: 1800 });
    await attempt({ listingTitle: "Something else entirely" });
    await attempt({ listingKind: "micro_job" });
    await attempt({ providerName: "Not Alice" });
    await attempt({ circleId: "another_circle" });
    await attempt({ listingId: "another_listing" });
    await attempt({ status: "escrow_held" });
    await attempt({ status: "completed" });
    await attempt({ createdAt: longAgo() });
    await attempt({ notes: "x".repeat(251) });
    await attempt({ tip: 500 });
  });

  test("a booking belongs to its listing's circle", async () => {
    const { alice, bob } = await blockWithAliceAndBob();
    // A second block that both neighbours are in.
    await createCircle(alice, {
      circleId: "circle_pine",
      code: "PINE4X9Q",
      circleData: circle(ALICE, { name: "Pine Block", inviteCode: "PINE4X9Q" }),
      inviteData: invite(ALICE, { inviteCode: "PINE4X9Q", circleId: "circle_pine", circleName: "Pine Block" }),
    });
    await joinCircle(bob, BOB, "circle_pine");

    await assertFails(book(bob, toolBooking({ circleId: "circle_pine" })));
    await assertSucceeds(book(bob, toolBooking()));
  });

  test("the 5% fee is rounded half up in whole cents", async () => {
    const { bob } = await blockWithAliceAndBob({ listingData: listing(ALICE, { rateCents: 1010 }) });
    const terms = (platformFeeCents) =>
      toolBooking({ grossAmountCents: 1010, platformFeeCents, providerNetCents: 1010 - platformFeeCents });

    await assertFails(book(bob, terms(50)));
    await assertSucceeds(book(bob, terms(51)));
  });

  test("only current members of the circle can book", async () => {
    const { bob } = await blockWithAliceAndBob();
    const charlie = as(CHARLIE);
    const asCharlie = toolBooking({
      requesterUid: CHARLIE,
      requesterName: "Charlie",
      participantUids: [CHARLIE, ALICE],
    });

    await assertFails(book(charlie, asCharlie));
    await leaveCircle(bob, BOB);
    await assertFails(book(bob, toolBooking()));
  });

  test("only the two participants can see or change a booking", async () => {
    const { alice, bob } = await blockWithAliceAndBob();
    const charlie = as(CHARLIE);
    await joinCircle(charlie, CHARLIE);
    await book(bob, toolBooking());
    const mine = (db, uid) =>
      getDocs(query(collection(db, "bookings"), where("participantUids", "array-contains", uid)));

    assert.equal((await assertSucceeds(mine(alice, ALICE))).size, 1);
    assert.equal((await assertSucceeds(mine(bob, BOB))).size, 1);
    assert.equal((await assertSucceeds(mine(charlie, CHARLIE))).size, 0);
    await assertFails(getDoc(doc(charlie, "bookings", BOOKING)));
    await assertFails(mine(charlie, BOB));
    await assertFails(getDocs(collection(alice, "bookings")));

    // ...or change it, with or without touching the listing.
    await assertFails(transition(charlie, "cancelled", { release: true }));
    await assertFails(transition(charlie, "cancelled"));
    await transition(alice, "escrow_held");
    await assertFails(transition(charlie, "in_progress"));
  });

  test("only the listing owner approves; steps cannot be skipped", async () => {
    const { alice, bob } = await blockWithAliceAndBob();
    await book(bob, toolBooking());

    await assertFails(transition(bob, "escrow_held"));
    await assertFails(transition(alice, "in_progress"));
    await assertFails(transition(alice, "completed", { release: true }));
    await assertFails(transition(alice, "requested"));
    await assertSucceeds(transition(alice, "escrow_held"));
    await assertFails(transition(alice, "requested"));
    await assertFails(transition(alice, "completed", { release: true }));
  });

  test("only the listing owner completes, and completing frees the listing", async () => {
    const { alice, bob } = await blockWithAliceAndBob();
    await book(bob, toolBooking());
    await transition(alice, "escrow_held");
    await transition(bob, "in_progress");

    await assertFails(transition(bob, "completed", { release: true }));
    await assertFails(transition(alice, "completed"));
    await assertFails(transition(alice, "cancelled", { release: true }));
    await assertSucceeds(transition(alice, "completed", { release: true }));
  });

  test("either neighbour cancels before the handoff, which frees the listing", async () => {
    const { alice, bob } = await blockWithAliceAndBob();

    await book(bob, toolBooking());
    await assertFails(transition(bob, "cancelled"));
    await assertSucceeds(transition(bob, "cancelled", { release: true }));

    await book(bob, toolBooking(), { bookingId: "booking_2" });
    await transition(alice, "escrow_held", { bookingId: "booking_2" });
    await assertSucceeds(transition(alice, "cancelled", { bookingId: "booking_2", release: true }));
    const freed = await getDoc(doc(alice, "listings", LISTING));
    assert.equal(freed.data().status, "available");
  });

  test("a reservation is released only when its booking is settled, and only by a participant", async () => {
    const { alice, bob } = await blockWithAliceAndBob();
    const charlie = as(CHARLIE);
    await joinCircle(charlie, CHARLIE);
    await book(bob, toolBooking());
    const release = (db) =>
      updateDoc(doc(db, "listings", LISTING), touch({ status: "available", activeBookingId: "" }));

    await assertFails(release(bob));
    await assertFails(release(alice));
    await assertFails(release(charlie));

    // If a settled booking ever left its listing reserved, a participant can still free it.
    await seed({ [`bookings/${BOOKING}`]: toolBooking({ status: "cancelled", createdAt: longAgo(), updatedAt: longAgo() }) });
    await assertFails(release(charlie));
    // Freeing it means clearing the pointer as well, and nothing else.
    await assertFails(updateDoc(doc(bob, "listings", LISTING), touch({ status: "available" })));
    await assertFails(
      updateDoc(doc(bob, "listings", LISTING), touch({ status: "available", activeBookingId: "", rateCents: 100 }))
    );
    await assertSucceeds(release(bob));
  });

  test("settled bookings are final and the ledger cannot be rewritten or deleted", async () => {
    const { alice, bob } = await blockWithAliceAndBob();
    await book(bob, toolBooking());
    const target = doc(alice, "bookings", BOOKING);

    await assertFails(updateDoc(target, touch({ status: "escrow_held", platformFeeCents: 0 })));
    await assertFails(updateDoc(target, touch({ status: "escrow_held", providerNetCents: 500000 })));
    await assertFails(updateDoc(target, touch({ status: "escrow_held", providerUid: CHARLIE })));
    await assertFails(updateDoc(target, touch({ status: "escrow_held", participantUids: [BOB, CHARLIE] })));
    await assertFails(updateDoc(target, touch({ status: "escrow_held", listingTitle: "Other" })));
    await assertFails(updateDoc(target, touch({ status: "escrow_held", notes: "x".repeat(251) })));
    await assertFails(updateDoc(target, { status: "escrow_held", updatedAt: longAgo() }));
    await assertFails(updateDoc(target, touch({ notes: "notes without a transition" })));
    await assertFails(deleteDoc(target));

    await transition(alice, "escrow_held");
    await transition(alice, "in_progress");
    await transition(alice, "completed", { release: true });

    await assertFails(transition(alice, "cancelled"));
    await assertFails(transition(alice, "in_progress"));
    await assertFails(updateDoc(target, touch({ notes: "rewriting history" })));
    await assertFails(deleteDoc(target));
    await assertFails(deleteDoc(doc(bob, "bookings", BOOKING)));
  });
});

describe("data written by earlier versions of the app", () => {
  // Before reservations were tracked, listings carried a denormalised memberUids array, had no
  // activeBookingId, and could hold a content:// photo URI.
  const legacyListing = (overrides = {}) => {
    const { activeBookingId, ...rest } = listing(ALICE, {
      memberUids: [ALICE, BOB],
      photoUri: "content://media/picker/0/com.android.providers.media.photopicker/media/33",
      createdAt: longAgo(),
      updatedAt: longAgo(),
      ...overrides,
    });
    return rest;
  };

  async function legacyBlock(listingOverrides) {
    const { alice, bob } = await blockWithAliceAndBob({ listingData: null });
    await seed({ [`listings/${LISTING}`]: legacyListing(listingOverrides) });
    return { alice, bob };
  }

  test("old listings stay readable, editable and bookable", async () => {
    const { alice, bob } = await legacyBlock();

    await assertSucceeds(getDoc(doc(bob, "listings", LISTING)));
    await assertSucceeds(updateDoc(doc(alice, "listings", LISTING), touch({ rateCents: 2000 })));
    await assertFails(
      updateDoc(doc(alice, "listings", LISTING), touch({ photoUri: "content://media/another/1" }))
    );
    await assertSucceeds(
      book(bob, toolBooking({ grossAmountCents: 2000, platformFeeCents: 100, providerNetCents: 1900 }))
    );
  });

  test("an old reservation with nothing pointing at it can be cleared by the owner only", async () => {
    const { alice, bob } = await legacyBlock({ status: "reserved" });

    await assertFails(updateDoc(doc(bob, "listings", LISTING), touch({ status: "available" })));
    await assertFails(deleteDoc(doc(alice, "listings", LISTING)));
    await assertSucceeds(updateDoc(doc(alice, "listings", LISTING), touch({ status: "available" })));
    await assertSucceeds(deleteDoc(doc(alice, "listings", LISTING)));
  });

  test("an old booking can still be settled although its listing has no pointer to release", async () => {
    const { alice, bob } = await legacyBlock({ status: "reserved" });
    await seed({ [`bookings/${BOOKING}`]: toolBooking({ createdAt: longAgo(), updatedAt: longAgo() }) });

    // The app first tries to free the listing in the same batch, which these listings refuse...
    await assertFails(transition(bob, "cancelled", { release: true }));
    // ...and then settles the booking on its own.
    await assertSucceeds(transition(bob, "cancelled"));
    await assertSucceeds(updateDoc(doc(alice, "listings", LISTING), touch({ status: "available" })));
  });

  test("a circle left without its invite can get one from its creator only", async () => {
    // Earlier versions wrote the circle first and the invite second; the second write could fail.
    await seed({ [`circles/${CIRCLE}`]: circle(ALICE, { createdAt: longAgo(), updatedAt: longAgo() }) });

    await assertFails(setDoc(doc(as(BOB), "circleInvites", CODE), invite(BOB)));
    await assertSucceeds(setDoc(doc(as(ALICE), "circleInvites", CODE), invite(ALICE)));
  });

  test("a booking whose listing was deleted can still be settled", async () => {
    const { bob } = await blockWithAliceAndBob({ listingData: null });
    await seed({ [`bookings/${BOOKING}`]: toolBooking({ createdAt: longAgo(), updatedAt: longAgo() }) });

    await assertFails(transition(bob, "cancelled", { release: true }));
    await assertSucceeds(transition(bob, "cancelled"));
  });
});
