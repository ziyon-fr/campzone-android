# Notification Backend API Contract (Canonical)

> The Campzone backend is a **Vercel serverless** project
> (`notification-backend`, base URL
> `https://notification-backend-chi.vercel.app`) powered by the Firebase
> Admin SDK. It is the **single source of truth** for: FCM push +
> in-app feed dispatch, Cloudinary signed uploads, Stripe payments, and
> auto-awarded achievement badges. **Secrets never leave the server** -
> web/Android must use these endpoints exactly like iOS does; do not put
> Stripe/Cloudinary secrets in any client.
>
> Source: `notification-backend` repo (`api/`, `lib/`, `vercel.json`,
> `README.md`).

---

## 1. Conventions

- **Base URL**: `https://notification-backend-chi.vercel.app`
- Most write endpoints require:
  - `Authorization: Bearer <Firebase ID token>`
  - `Content-Type: application/json`
  - The ID token is the signed-in user’s Firebase Auth token
    (`getIdToken()` web / `getIdToken()` Android). The backend resolves
    `req.user = { uid, role, name, email, church }` from it.
- **Response envelope** (`ziyonRoute`): success →
  `{ "success": true, "data": <payload>, "meta": { "api":
  "ziyon-notification", "timestamp": "<iso>" } }`. Error → handled by
  `errorHandler` with `{ "success": false, "error": { "code", "message"
  [, "details"] } }` and an appropriate status. `OPTIONS` → 200 (CORS
  preflight). Non-allowed method → 405.
- `appID` is **always `"campzone"`** in every request body that takes
  one. The backend validates it (`^[A-Za-z0-9-_.~%]{1,80}$`).
- Vercel Hobby caps **12 serverless functions** - do not add functions
  casually; the dispatch endpoints are intentionally consolidated.

---

## 2. Endpoint index (`vercel.json` routes)

| Method | Path (and `/api`-prefixed alias) | Purpose |
| --- | --- | --- |
| GET | `/`, `/api` | API root / endpoint list |
| GET | `/api/health` | health (firebase/firestore/fcm) |
| POST | `/notifications/devices` | register/refresh FCM token + topic subscriptions |
| POST | `/notifications/settings` | save notification settings + re-sync topics |
| POST | `/notifications/reminders` | schedule a camp reminder (leadership) |
| GET | `/api/notifications/reminders` | cron: dispatch due reminders (`CRON_SECRET`) |
| POST | `/notifications/dispatch/announcement` | push + feed: announcement |
| POST | `/notifications/dispatch/chat` | push + feed: chat message |
| POST | `/notifications/dispatch/poll` | push + feed: poll event (leadership) |
| POST | `/notifications/dispatch/team` | push + feed: team update (leadership) |
| POST | `/notifications/dispatch/registration` | push + feed: registration request (any registrant) |
| POST | `/cloudinary/sign` | signed Cloudinary upload descriptor |
| POST | `/cloudinary/destroy` | delete a Cloudinary asset |
| GET | `/api/badges/evaluate` | cron: bounded badge sweep (`CRON_SECRET`) |
| POST | `/api/badges/evaluate` | evaluate one user’s badges |
| POST | `/payments/intent` | create Stripe PaymentSheet params |
| POST | `/payments/confirm` | verify Stripe payment + settle Firestore |

(`/notifications/dispatch/*` and `/payments/*` are consolidated into one
function each via `?type=`/`?action=` rewrites - the public paths above
are stable.)

---

## 3. Notifications

### 3.1 Two storage locations (important)

The notification system writes to **two** places. Web/Android must do
**both**, exactly like iOS:

1. **Client-direct Firestore** (RBAC self-only):
   `users/{uid}/notificationTokens/{sha256hex}` and
   `users/{uid}/notificationSettings/default` - see
   `02-firestore-schema.md` §2.2/§2.3. This is what your own settings UI
   reads/writes.
2. **Backend API** (`/notifications/devices`, `/notifications/settings`)
   - the backend stores its own copy under
   `notification_apps/{appID}/users/{uid}/{tokens|settings}` and, more
   importantly, **subscribes/unsubscribes the FCM token to topics**.
   Without the API call the device gets **no pushes**.

### 3.2 `POST /notifications/devices`

Body:

```jsonc
{ "appID": "campzone", "token": "<FCM token>",
  "platform": "web" | "android" | "ios",
  "provider": "fcm",
  "role": "<UserRole raw>",            // optional, defaults to req.user.role
  "localeIdentifier": "en_US",          // optional
  "appVersion": "1.0.0" }               // optional
```

The backend upserts the token (doc id = `base64url(token).slice(0,120)`),
computes the user’s topic set from saved settings (or default
`announcements` + `role_<role>`), and subscribes the token via FCM.
Returns `{ appID, userID, platform, role, updatedAt }`.

### 3.3 `POST /notifications/settings`

Body mirrors the settings doc (`02` §2.3): `appID`, `isEnabled`,
`authorizationState`, `announcementsEnabled`, `chatMessagesEnabled`,
`scheduleRemindersEnabled`, `roleMessagesEnabled`, `teamUpdatesEnabled`,
`subscribedCampingIDs[]`, `subscribedRoles[]` **or**
`subscribedRoleRawValues[]`, `subscribedTeamIDs[]`. Empty
`subscribedRoleRawValues` defaults to `[req.user.role]`. The backend
re-derives topics for every token of the user and re-subscribes.

### 3.4 Dispatch endpoints (push + `ziyon_notifications` feed record)

All take `{ "appID": "campzone", ... }`. The backend sends an FCM **topic**
message **and** appends a record to the `ziyon_notifications` collection
(read by the in-app feed - see `02` §6.5). `dispatch/poll` and
`dispatch/team` require a **privileged** caller
(`admin`/`youth_director`/`pastor`/`leader`); `dispatch/chat`,
`dispatch/announcement` likewise except `dispatch/registration` is open
to any signed-in registrant.

- `dispatch/announcement` - `{ announcementID, title, body,
  target?: { campingID?, role?, teamID? } }`. Topic =
  target-specific or global `campzone_announcements`.
- `dispatch/chat` - `{ campingID, messageID, teamID? }`. The backend
  loads the chat message, verifies `senderID == caller` and not deleted,
  pushes to `campzone_camping_chat_<campingID>` (or
  `campzone_team_chat_<teamID>`).
- `dispatch/poll` - `{ campingID, pollID, title, body, event:
  "created"|"closed"|"reopened" }`. Topic `campzone_camping_<campingID>`.
- `dispatch/team` - `{ campingID, teamID, teamName, title, body,
  event, memberID?, memberName?, pointsDelta?, reason? }`. `event` ∈
  `created,updated,memberAssigned,memberRemoved,memberRoleUpdated,
  scoreChanged,memberScoreChanged,penaltyApplied`. Topic
  `campzone_team_<teamID>`.
- `dispatch/registration` - `{ campingID, title, body,
  participantName?, requestedByName?, participantCount? }`. Fans out to
  **leadership role topics** `campzone_role_{leader,pastor,
  youth_director,admin}` so leadership sees the pending request.

### 3.5 Topic naming

`_topic(appID, scope, value)` = `[appID, scope, value]` filtered, each
part sanitized (`[^A-Za-z0-9-_.~%] → "_"`), joined by `_`. Resulting
topics the in-app feed filters on:

- Global announcements: **`campzone_announcements`**
- Role: **`campzone_role_<roleRaw>`** (e.g. `campzone_role_admin`).
  Admins effectively see all role topics; non-admins only their own.
- Camping: `campzone_camping_<id>`; camping chat
  `campzone_camping_chat_<id>`; camping reminders
  `campzone_camping_reminders_<id>`; team `campzone_team_<id>`; team chat
  `campzone_team_chat_<id>`.

The **feed** (`ziyon_notifications`) is filtered client-side by
`appID == "campzone"` and visible topics (global + the user’s role
topics). FCM topic subscription (what actually delivers a push) is
driven by the saved notification settings via `/notifications/devices`
and `/notifications/settings`.

### 3.6 In-app feed records

Backend writes to `ziyon_notifications` with: `appID`, `kind`
(`announcement`/`chat_message`/`poll`/`team_update`/`registration`),
ids (`announcementID`/`campingID`/`pollID`/`teamID` as relevant),
`topic`, `messageId`, `title`, `body`, `role?`, `senderId`, `sentAt`
(**ISO-8601 string** `…SSSZ`). Clients are **readers only** (RBAC
forbids client writes). See `02` §6.5 for the read schema + tolerant
decoder requirements.

---

## 4. Cloudinary (signed uploads - no client secret)

Media (profile/team/camp logos, album photos/videos, announcement
attachments, song audio, venue-map images) is stored on **Cloudinary**,
not Firebase Storage. The client never holds the API secret.

### 4.1 `POST /cloudinary/sign`

Body:

```jsonc
{ "paramsToSign": { "public_id": "...", "folder": "...",
                     "tags": "a,b", "overwrite": true, ... },
  "resourceType": "image" | "video" | "raw" }   // optional, default image
```

Returns:

```jsonc
{ "signature": "<sha1>", "apiKey": "<key>", "timestamp": "1737045600",
  "resourceType": "image", "cloudName": "<cloud>",
  "uploadURL": "https://api.cloudinary.com/v1_1/<cloud>/<resource>/upload",
  "signedParams": { ...normalized echoed params... } }
```

Then the client POSTs `multipart/form-data` directly to `uploadURL` with
the file + every signed param + `api_key` + `signature` + `timestamp`.
Persist the returned `secure_url` and `public_id` into the relevant
Firestore field (e.g. `photoURL`/`photoPublicID`, `logoURL`/
`logoPublicID`, media `secureURL`/`publicID`, attachment `downloadURL`/
`storagePath`). Audio uses `resourceType: "video"` on Cloudinary.

### 4.2 `POST /cloudinary/destroy`

Body `{ "publicID": "...", "resourceType": "image"|"video"|"raw",
"invalidate": true }`. Use for replace/cleanup. Both endpoints require
the Firebase ID token.

Conventions: camp logo public id is stable
`campzone/campings/{campingID}` (overwrite-in-place). Keep the
`publicID` everywhere so backend cleanup jobs can remove orphans.

---

## 5. Stripe payments (PaymentSheet pattern)

Server-signed; the client never holds `STRIPE_SECRET_KEY`. Both routes
require the Firebase ID token. Web should use Stripe.js / Payment
Element; Android uses the Stripe Android SDK PaymentSheet. The
**contract is identical to iOS**.

### 5.1 `POST /payments/intent`

Body:

```jsonc
{ "amount": <int cents>, "currency": "eur",   // lowercased server-side
  "kind": "registration" | "transportation" | "priceItem",
  "campingID": "<id>", "referenceID": "<id>",
  "stripeVersion": "2024-06-20" }              // optional
```

- `amount` must be a positive integer (**cents**), max 5,000,00.
- `referenceID` = the registration attendee id (`registration`), the
  booking id (`transportation`), or the price-item id (`priceItem`).
- Backend gets-or-creates the user’s Stripe Customer (persists
  `users/{uid}.stripeCustomerID`), mints an Ephemeral Key + a
  PaymentIntent (`metadata: { uid, kind, campingID, referenceID, app:
  "campzone" }`), and **seeds an audit doc**
  `campings/{campingID}/payments/{paymentIntentId}` with
  `{ uid, kind, campingID, referenceID, amount, currency,
  status:"created", paid:false, createdAt }` (falls back to top-level
  `payments/{id}` if no campingID).
- Returns `data`:
  `{ paymentIntentId, paymentIntentClientSecret, ephemeralKeySecret,
  customerId, publishableKey, amount, currency }`.

### 5.2 `POST /payments/confirm`

Body `{ "paymentIntentId": "...", "kind"?, "campingID"?,
"referenceID"? }` (the backend prefers the values from the verified
intent metadata).

- Re-fetches the PaymentIntent from Stripe (**never trusts the
  client**), checks `status == "succeeded"` and `metadata.uid ==
  caller`.
- Merges the audit doc to the final Stripe status (`paid`, `status`,
  `amount`, `currency`, `updatedAt`).
- If succeeded and `kind == "registration"`: Admin-SDK writes the
  attendee `registrations/{referenceID}` `{ paymentStatus:"paid",
  paymentReference, paymentUpdatedAt, registrationStatus:"approved",
  approvedVia:"payment", approvedAt }` - **paid camps auto-approve**.
- If `kind == "transportation"`: sets the booking `paymentStatus:"paid"`
  (+ `paymentReference`, `paymentUpdatedAt`).
- `kind == "priceItem"`: audit doc only (no status flip).
- Returns `{ paid, status, kind, campingID, referenceID }`.

**Flow**: register → submit → (paid camp) PaymentSheet via
`/payments/intent` → on success call `/payments/confirm` → backend
auto-approves. Free camps skip payment (manual admin approval). The
client cannot write `registrationStatus`/`paymentStatus` (RBAC) - only
the backend settles them.

---

## 6. Achievement badge auto-award

`/api/badges/evaluate` is the **single source of truth** for objective
badges (clients only read `users/{uid}/badges/{id}`):

| Badge id | Unlocked when |
| --- | --- |
| `first-adventure` | ≥1 approved registration |
| `trail-veteran` | ≥3 approved registrations |
| `camp-check-in` | any check-in record |
| `team-roster` | member of any team |
| `team-captain` | captain of any team |
| `score-spark` | personal team score > 0 |

- `GET /api/badges/evaluate` - cron-secured bounded sweep (header/secret
  `CRON_SECRET`); runs hourly via GitHub Actions.
- `POST /api/badges/evaluate` `{ "userID": "..." }` - system/cron;
  an authenticated end user may only (re)evaluate **their own** badges.
- Writes are idempotent (never overwrite/revoke), Admin-SDK only, with
  `earnedAt` server Timestamp, `campingID:null`, `note:"Auto-awarded"`.
  Subjective/leadership badges stay manual. Badge ids mirror the in-code
  `AchievementCatalog` (see `08-feature-parity.md`).

---

## 7. Environment & deploy (owner actions)

Backend env (Vercel project settings - **never** in any client bundle):
`FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL`, `FIREBASE_PRIVATE_KEY`
(escaped `\n`), `CRON_SECRET`, `API_VERSION`, `CLOUDINARY_CLOUD_NAME`,
`CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `STRIPE_SECRET_KEY`,
`STRIPE_PUBLISHABLE_KEY`.

Owner-only operations the web/Android teams depend on (coordinate
before relying on a new field/rule):

- `firebase deploy --only firestore:rules` - after **any** RBAC change
  (must stay in parity across all 3 clients).
- `firebase deploy --only firestore:indexes` - composite indexes (e.g.
  collection-group `children (displayName, age)`; any new `where+order`).
- `npm run deploy` (in `notification-backend`) - after backend changes;
  `npm test` must pass first.
- Stripe SPM/SDK + `STRIPE_*` env must be live for payments to settle.

Client base URL is fixed (`https://notification-backend-chi.vercel.app`)
unless overridden by build config. Health check: `GET /api/health`.
