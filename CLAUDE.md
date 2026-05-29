# Campzone Android - Agent Handoff

This is the **Android client** (Jetpack Compose) for Campzone, a church
camping management app for the Seventh-day Adventist community. The
shipped **iOS app is the reference**; this app and a parallel Next.js
web app sync into the **same Firebase project** + one Vercel
notification backend.

## Required reading before any implementation/debug/review

Read **`docs/`** in this order - it is the build base and the data
contract:

1. `docs/00-project-overview.md` - product, principles, stack
2. `docs/07-data-contract-rules.md` - sync gotchas (read before writing
   anything to Firestore)
3. `docs/02-firestore-schema.md` - **exact Firestore wire schema**
   (replicate verbatim; do not redesign)
4. `docs/03-rbac-and-security.md` - roles, permission matrix, Security
   Rules (client gate must match the deployed rules)
5. `docs/04-backend-api.md` - notification/Cloudinary/Stripe/badges
   backend contract (secrets never leave the server)
6. `docs/01-architecture.md` - Compose/MVVM architecture & layering
7. `docs/09-setup.md` - Gradle, Firebase, Stripe wiring
8. `docs/05-deep-linking.md`, `docs/06-design-tokens.md`,
   `docs/08-feature-parity.md` - as needed
9. All written code MUST BE production ready, dont create enought for MVP code.

`docs/00` and `docs/02`-`08` are **byte-identical** to the web repo's
`docs/`. They are derived from the iOS source - if the contract must
change, change iOS first (schema + `firestore-rbac.rules`,
owner-deployed), then update `02/03/04` and copy to the web repo. Never
let the three platforms drift.

## Mandatory & Non-negotiables - iOS parity rule

- Every AI agent must read this file and the relevant `docs/` entries
above before making any project change. For any new or modified UI view,
also open the matching iOS SwiftUI source under:

`/Users/leon/Desktop/Business Projects/Campzone/Campzone`.

- **Required**
Always write in `PROJECT_STATUS` projects current state.

The shipped iOS app is the source of truth for view structure, copy,
visual style, interaction flow, navigation state, and Firestore payload
behavior. Android Compose views must try to match the iOS equivalent
first, then adapt only where Android platform conventions or missing
Android plumbing require it. When `docs/` and Android code disagree,
check the iOS implementation and align Android with iOS; if the docs
need updating, note that the iOS source remains authoritative.

## Task branch and merge rule

Every new task listed in `TODO.md` must be done on a new Git branch named
after that task. When the task is finished, commit the completed work,
push the branch, and merge it back to `main` only after explicit approval.

## Non-negotiables

- **Replicate the Firestore schema exactly** - field names, enum raw
  strings (case-sensitive), `FieldValue.delete()` vs explicit `null` vs
  omit, per-field timestamp form, deterministic doc IDs, money in
  integer cents. A single drift corrupts shared data. Map Firestore
  docs **manually** (`Map<String, Any?>`) - do **not** use POJO
  auto-mapping (it can't express delete/null/omit or the legacy
  aliases). Use the `07` pre-write checklist on every write path.
- **Match RBAC** - client gates must equal the deployed Security Rules
  (`03`); never write a rule-forbidden field; include rule-required
  ones (`memberUserIDs`, `createdByUID`, `campingID==path`, ...).
- **Secrets stay server-side** - Stripe/Cloudinary/cron secrets live
  only in the notification backend; the app calls its endpoints.
- **Full CRUD is mandatory** - anything that can Create ships working
  Read/Update/Delete with real UI + Security-Rule parity.
- **Layered MVVM** - Composable -> ViewModel (`StateFlow<UiState>`
  sealed) -> Service/Repository (interface + fake) -> Model; constructor
  DI (Hilt); typed `AppRoute`; no business logic in composables; no
  global mutable state; single owned Firestore listener removed in
  `onCleared`; previews on every screen with fakes.
- **Accessibility + localization** (PT/FR, English keys) + design
  tokens are mandatory, not optional.
- **Verification honesty** - never claim a build/test/emulator green
  you did not actually produce.
- **UI Design** - ALWAYS consult the IOS side at `/Users/leon/Desktop/Business Projects/Campzone`  for design reference.
  before building a View

## Stack

Kotlin 2.2.10 - Jetpack Compose (BOM 2026.02.01) + Material 3 -
`compileSdk 36` / `minSdk 24` - single-Activity - Firebase Android SDK
(add) - Stripe Android SDK (add) - Coil. Backend:
`https://notification-backend-chi.vercel.app`. Finalize the application
id (currently `com.example.campzone`) before registering the Firebase
Android app / any release.
