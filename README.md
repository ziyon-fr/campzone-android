# Campzone Android

The Android client (Jetpack Compose) for **Campzone** - a church camping
management app for the Seventh-day Adventist community (registration,
schedules, announcements, songbook, teams & gamification, check-in,
transportation, lodging, polls, chat, feedback, venue map,
notifications, payments).

The shipped **iOS app is the reference implementation**. This app and a
parallel Next.js web app sync into the **same Firebase project** and the
same Vercel notification backend. The Firestore data contract is fixed
by iOS - **replicate it exactly, do not redesign it**.

## Start here

All implementation context lives in **[`docs/`](./docs/README.md)** -
read it before writing code:

- `docs/00-project-overview.md` - product & principles
- `docs/07-data-contract-rules.md` - sync gotchas (read before any write)
- `docs/02-firestore-schema.md` - **exact Firestore wire schema**
- `docs/03-rbac-and-security.md` - roles & Security Rules
- `docs/04-backend-api.md` - notification/Cloudinary/Stripe/badges API
- `docs/01-architecture.md` - Compose/MVVM architecture
- `docs/09-setup.md` - Gradle, Firebase, Stripe wiring
- `docs/05`/`06`/`08` - deep links, design tokens, feature parity

`docs/00` + `docs/02`-`08` are byte-identical to the web repo's `docs/`
(the shared contract). `01`/`09` are Android-specific. Agent guidance:
`CLAUDE.md`.

## Build

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

Requires the shared Firebase project's `google-services.json` in
`app/` and the Firebase/Stripe/Coil dependencies added (the scaffold is
bare - see `docs/09-setup.md`). Stripe/Cloudinary secrets stay in the
backend - never in this app.

## Stack

Kotlin 2.2.10 - Jetpack Compose + Material 3 - `compileSdk 36` /
`minSdk 24` - Firebase Android SDK - Stripe Android SDK - Coil.
