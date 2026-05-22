# Campzone Android — Documentation

This `docs/` set is the build base for the Campzone **Android app**
(Jetpack Compose). It is paired with the shipped iOS app and a parallel
Next.js web app, all on **one** Firebase project + one Vercel
notification backend.

> **Read in order. The data contract is non-negotiable.** Campzone iOS
> is in production; Android and web write into the *same* Firestore
> documents. `02`–`08` describe that contract extracted verbatim from
> the iOS code/rules/backend — replicate it exactly, do not redesign.
> Getting an enum case or a `FieldValue.delete()` vs `null` wrong
> silently corrupts shared data or trips a Security Rule.

| # | Doc | Scope |
|---|---|---|
| 00 | [`00-project-overview.md`](./00-project-overview.md) | Product, principles, stack — **shared** |
| 01 | [`01-architecture.md`](./01-architecture.md) | Compose/MVVM architecture — Android-specific |
| 02 | [`02-firestore-schema.md`](./02-firestore-schema.md) | **Exact Firestore wire schema — shared, the crown jewel** |
| 03 | [`03-rbac-and-security.md`](./03-rbac-and-security.md) | Roles, permission matrix, Security Rules — **shared** |
| 04 | [`04-backend-api.md`](./04-backend-api.md) | Notification backend (push/feed/Cloudinary/Stripe/badges) — **shared** |
| 05 | [`05-deep-linking.md`](./05-deep-linking.md) | `campzone://` + push routing — **shared** |
| 06 | [`06-design-tokens.md`](./06-design-tokens.md) | Colors/type/spacing/radius — **shared** |
| 07 | [`07-data-contract-rules.md`](./07-data-contract-rules.md) | Cross-platform sync gotchas — **shared, read before writing** |
| 08 | [`08-feature-parity.md`](./08-feature-parity.md) | Feature matrix + build order — **shared** |
| 09 | [`09-setup.md`](./09-setup.md) | Gradle/Firebase/Stripe wiring, build — Android-specific |

`00` and `02`–`08` are **byte-identical** to the web repo’s `docs/`.
Only `01` and `09` differ per platform. If the contract changes: change
iOS first (schema + `firestore-rbac.rules`, owner-deployed), then update
`02/03/04` and copy to the web repo. Never let the platforms drift.

**Suggested path:** `00` → `07` (gotchas) → `02` (schema) → `03` (RBAC)
→ `04` (backend) → `01` (architecture) → `09` (setup) → `08` (what to
build) → `05`/`06` as needed.
