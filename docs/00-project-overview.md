# Campzone - Project Overview (Canonical)

> Shared product context for every Campzone client (iOS shipped, Next.js
> web, Android). This file is **identical** in the web and Android repos.
> Read it first, then the platform `01-architecture.md` /
> `09-setup.md`, then the shared data-contract docs (`02`–`08`).

---

## What Campzone is

Campzone is a church camping management app for the **Seventh-day
Adventist** community. It helps organizers and participants run church
camp events end to end: registration & approval, family participants,
schedules, food menus, announcements, songbook, teams & gamification
(games/points/winner reveal), QR check-in, transportation tickets,
lodging, polls, chat, post-camp feedback, a venue map, notifications,
achievements, and payments.

- **iOS** is the shipped reference (Swift 6 / SwiftUI / MVVM /
  `@Observable` / Firebase). Production-ready through Phase 14.
- **Web** (this/that repo): Next.js (App Router) + Firebase JS SDK.
- **Android**: Jetpack Compose + MVVM + Firebase Android SDK.
- All three share **one Firebase project** and **one Vercel
  notification backend**. The data contract is fixed by iOS - see
  `02-firestore-schema.md` (the rule: replicate, do not redesign).

## Product principles

- **Role-based access** (public browsing -> user -> adult -> leadership -> admin),
  server-enforced by Firestore Security Rules; church-scoped for
  non-admin leadership. See `03-rbac-and-security.md`.
- **Registration requires admin/leadership approval** (paid camps
  auto-approve on successful payment, server-side).
- **Attendee lists are visible only to registered participants.**
- **Offline matters** (camps have poor connectivity): Firestore
  persistent cache; prioritize songbook, schedule, camp program.
- **Localization**: Portuguese + French first, English keys.
- **Accessibility is mandatory** (dynamic type/font scale, labels,
  contrast, large targets, reduced motion).
- **Full CRUD is mandatory**: any Create ships Read/Update/Delete with
  real UI and matching Security-Rule parity.
- **Secrets never reach a client.** Media signing, payments, badge
  auto-award, and notification fan-out all go through the backend.

## Architecture shape (all platforms)

Layered MVVM-equivalent: **View → Observer/ViewModel → Service →
Model**. Views are presentational only; state is explicit
(`loading/loaded/empty/error`), not scattered booleans; services are
protocol/interface-based with a production + mock implementation;
dependencies are injected; navigation is strongly typed (no raw-string
routes). Mirror the iOS feature-folder layout
(`Feature/{Model,View,Observer,Service,Components}`).

## Tabs / primary surfaces

**Home · Campings · Announcements · Profile/Settings** (same four
everywhere). Detail/management surfaces are pushed via typed routes;
deep links and shared `campzone://` links resolve to the same content on
every platform (`05-deep-linking.md`).

## Tech stack per platform

| | iOS (ref) | Web | Android |
| --- | --- | --- | --- |
| Lang/UI | Swift 6 / SwiftUI | TypeScript / React 19 / Next.js App Router | Kotlin / Jetpack Compose |
| State | `@Observable` MVVM | Server Components + client Observers/hooks | ViewModel + StateFlow MVVM |
| Backend | Firebase (Auth, Firestore) | `firebase` JS SDK | Firebase Android SDK |
| Media | Cloudinary (backend-signed) | same | same |
| Push | FCM | FCM (web push) | FCM |
| Payments | Stripe PaymentSheet | Stripe.js / Payment Element | Stripe Android PaymentSheet |
| Dispatch/badges | Vercel notification backend | same | same |

## Documentation map

| File | Scope |
| --- | --- |
| `00-project-overview.md` | this - shared product context |
| `01-architecture.md` | **platform-specific** architecture |
| `02-firestore-schema.md` | **shared** - exact Firestore wire schema |
| `03-rbac-and-security.md` | **shared** - roles, permission matrix, rules |
| `04-backend-api.md` | **shared** - notification backend contract |
| `05-deep-linking.md` | **shared** - `campzone://` + push routing |
| `06-design-tokens.md` | **shared** - colors/type/spacing/radius |
| `07-data-contract-rules.md` | **shared** - sync gotchas checklist |
| `08-feature-parity.md` | **shared** - feature matrix + build order |
| `09-setup.md` | **platform-specific** environment setup |

`02`–`08` and `00` are byte-identical across the web and Android repos.
If the data contract changes, change it on iOS first (schema + Security
Rule), redeploy rules, then update `02`/`03`/`04` here and copy to both
repos. Never let the three drift.
