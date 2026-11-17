# BlockBench

Hyper-local tool sharing and micro-jobs for a neighbourhood block, with an escrow-style ledger.
Neighbours form a **block** by invite code, lend tools or post 20-minute jobs to its feed, and
walk each booking through a small escrow workflow.

Android app in Kotlin and Jetpack Compose, backed by Firebase Authentication (Google sign-in) and
Cloud Firestore. The project started as a Google AI Studio export.

> **Prototype status.** No money moves anywhere: "escrow" is a ledger of who agreed to what, and
> "Pro Alerts" is a setting without billing behind its price tag. The on-time rate is always 100%
> because returns are not timed, and a listing's distance is whatever its owner entered.

## How it fits together

```
Compose screens ──▶ BlockBenchViewModel ──▶ BlockBenchRepository ──▶ Firestore
   ui/screens          ui/ (one per             data/repository        firestore.rules
   ui/components       signed-in session)       (every write is a
                                                transaction)
```

There is no backend. `firestore.rules` is the only thing between a modified client and the data,
so the rules validate every write in full and the repository mirrors them: change one, change the
other, and run both test suites.

| Collection | What it holds | Who can read it |
| --- | --- | --- |
| `users/{uid}` | Private profile, active block, Pro Alert rules | Its owner |
| `circles/{id}` | A block: name, radius, invite code, members (max 50) | Its members |
| `circleInvites/{code}` | Invite code → block lookup | Anyone signed in, by exact code only |
| `listings/{id}` | A tool or micro-job in one block | Current members of that block |
| `bookings/{id}` | One escrow ledger entry | Its two participants |

The field-level schema is in [`firebase-blueprint.json`](firebase-blueprint.json).

### The escrow workflow

```
requested ──owner──▶ escrow_held ──either──▶ in_progress ──owner──▶ completed
    └──────either──────┴───────────▶ cancelled
```

"Owner" is the neighbour who posted the listing: the lender of a tool, or the poster of a job.
A booking is created by the *other* neighbour, copies the listing's price and deposit, and
reserves the listing in the same write. The listing stays reserved until the booking is completed
or cancelled, so it cannot be double-booked, paused or deleted meanwhile. The platform fee is 5%
of the rate, rounded half up, taken from the provider's payout. The same table lives in
`EscrowWorkflow` (app) and in the booking `update` rule.

### Things worth knowing

- **Invite codes** are the only secret protecting a block: three letters from its name plus five
  random characters (for example `OAK7K2MQ`).
- **Listing photos** are stored inside the listing document as a ~320 px JPEG thumbnail
  (`data:` URI). There is no file storage, and a photo-picker URI is only readable on the phone
  that picked it. Moving to Cloud Storage would be the next step for larger images.
- **Writes need a connection.** Reads work offline from Firestore's cache; every change is a
  transaction and fails with a message when offline instead of being queued.

## Building

Requirements: JDK 21, the Android SDK (`ANDROID_HOME` or `local.properties`), and for the tests
Node.js 20 or newer.

```bash
./gradlew :app:assembleDebug
```

This works on a clean checkout, but the resulting app only shows a "Firebase isn't configured"
screen. To run it for real:

1. Download `google-services.json` for the Android app `com.aistudio.blockbench.mrvkqz` from the
   Firebase console (Project settings → Your apps) into `app/`. The file is git-ignored.
2. Check that `app/src/main/res/values/firebase_applet_config.xml` names your Firestore database,
   and that Google is enabled as a sign-in provider.
3. Deploy the rules (see below) and rebuild.

Signing: debug builds use the repo-root `debug.keystore` when AI Studio provides one and the
standard Android debug keystore otherwise. Release builds need `KEYSTORE_PATH`, `STORE_PASSWORD`
and `KEY_PASSWORD`.

## Testing

```bash
./gradlew :app:testDebugUnitTest   # unit and screen tests; emulator tests are skipped
npm ci                             # once: Firebase emulators and the rules test library
npm test                           # security-rules tests against the Firestore emulator
npm run test:android               # everything in the first line, plus the repository
                                   # tests against the Firestore and Auth emulators
```

- `firestore.test.js` exercises every allow path of `firestore.rules` and the attacks they exist
  to stop.
- `BlockBenchRepositoryTest` drives the real Firebase SDK through those rules, so it catches the
  app and the rules drifting apart.
- Screen tests render the Compose screens under Robolectric.

`npm run emulators` starts the emulators for manual use (`npm run test:rules` then runs the rules
tests against them). After editing `firestore.rules`, restart them before running the Android
tests: the emulator only hot-reloads rules for the default database, and the app uses a named one.

CI (`.github/workflows/ci.yml`) runs both suites, a clean-checkout debug build and Android lint.

## Deploying the rules

```bash
npx firebase deploy --only firestore:rules --project <your-firebase-project-id>
```

Ship rules and app together. An app build older than the rules cannot post or book, and the
current app expects the current rules.

## Repository layout

```
app/src/main/java/com/example/
  auth/             Google sign-in via Credential Manager
  data/             Repository (all Firestore access) and error handling
  model/            Documents, the escrow workflow, fee maths, invite codes
  ui/               ViewModel, navigation shell, screens, components, theme
app/src/test/       Unit, screen and emulator-backed tests
firestore.rules     Security rules
firestore.test.js   Their tests
firebase.json       Emulator ports and the rules ↔ database mapping
```

`metadata.json`, `.env.example` and the signing blocks in `app/build.gradle.kts` belong to the AI
Studio tooling.
