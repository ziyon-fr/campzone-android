# Campzone Firestore Wire Schema (Canonical)

> **This document is the single source of truth for the Firestore data
> contract.** iOS (shipped), Next.js web, and Android all read/write the
> **same Firebase project**. Every field name, type, enum raw value,
> document-ID formula, and timestamp format below is extracted verbatim
> from the shipped iOS Swift serializers (`*Service.swift` `payload(for:)`
> / `init(from:)`), the deployed `firestore-rbac.rules`, and the
> notification backend. **Do not “improve”, rename, or re-case anything
> here.** A single drift (e.g. writing `"providedBus"` instead of
> `"provided_bus"`, or a `null` instead of a deleted field) silently
> corrupts cross-platform data or trips a Security Rule.
>
> Read `07-data-contract-rules.md` alongside this - it lists the
> non-obvious gotchas (timestamp inconsistencies, `FieldValue.delete()`,
> deterministic IDs, the two notification stores).

Generated 2026-05-18 from the iOS codebase on branch
`phase-15-tech-debt-followups`.

---

## 0. Conventions used in this document

- **Firestore type**: `string`, `int`, `double`, `bool`, `timestamp`
  (Firestore native `Timestamp`), `iso-string` (ISO-8601 text - only
  `ziyon_notifications`), `map`, `array<T>`, `null` (explicit Firestore
  null), `geo` (lat/lon pair stored as two doubles, **not** GeoPoint).
- **Req/Opt**: whether the iOS reader requires the key. “Required” means
  the document is **dropped/!throws** on decode if absent → web/Android
  **must** write it.
- **Decode default**: value the iOS reader substitutes when the key is
  missing. Write the key anyway unless noted (“omit when nil”).
- **delete-when-nil**: iOS writes `FieldValue.delete()` (the field is
  **removed**, not set to `null`) when the value is empty. Replicate
  exactly - `firestore.FieldValue.delete()` (web) /
  `FieldValue.delete()` (Android). Writing `null` is **wrong**.
- **omit-when-nil**: the key is simply absent from the write payload
  when nil (not deleted, not null).
- Swift `enum: String` raw value == the case name **unless** an explicit
  `= "..."` mapping is shown. Enum raw strings are **case-sensitive**.
- All writes are `set(..., { merge: true })` **unless** the entry says
  “full set (overwrite)”.
- `createdAt` = `serverTimestamp()` written **only on first create**
  (doc didn’t exist). `updatedAt` = `serverTimestamp()` on **every**
  write. Exceptions are called out.

---

## 1. Collection map

```js
users/{uid}                                         User profile
  users/{uid}/children/{childId}                    ChildParticipant (family)
  users/{uid}/notificationTokens/{sha256hex}        FCM token (client-direct)
  users/{uid}/notificationSettings/default          Notification settings
  users/{uid}/blockedUsers/{blockedUid}             Chat block list (UGC 1.2)
  users/{uid}/badges/{achievementId}                EarnedBadge (backend-written)

campings/{campingId}                                Camping (event)
  campings/{id}/registrations/{attendeeId}          CampingAttendee
  campings/{id}/schedule/config                     Schedule config (single doc)
  campings/{id}/schedule/config/days/{dayId}        CampDay
  campings/{id}/schedule/config/days/{dayId}/programs/{programId}  Program
  campings/{id}/foodMenu/{entryId}                  FoodMenuEntry
  campings/{id}/teams/{teamId}                      Team
  campings/{id}/teams/{teamId}/chat/{messageId}     ChatMessage (team)
  campings/{id}/games/{gameId}                      Game
  campings/{id}/activities/{activityId}             Activity (immutable audit)
  campings/{id}/chat/{messageId}                    ChatMessage (camping-wide)
  campings/{id}/polls/{pollId}                      Poll
  campings/{id}/polls/{pollId}/votes/{voterId}      PollVote
  campings/{id}/checkIns/{attendeeId}               CheckInRecord
  campings/{id}/transportationBookings/{bookingId}  TransportationBooking
  campings/{id}/lodging/{unitId}                    LodgingUnit
  campings/{id}/venueMap/config                     VenueMap (single doc)
  campings/{id}/feedback/{uid}                       CampFeedback
  campings/{id}/media/{mediaId}                      MediaItem (album)
  campings/{id}/albumSettings/default                AlbumSettings (single doc)
  campings/{id}/songs/{songId}                       Song
  campings/{id}/guidelines/{guidelineId}             Markdown guidelines
  campings/{id}/payments/{paymentIntentId}           Payment audit (BACKEND-ONLY)

announcements/{announcementId}                       Announcement
contentReports/{reportId}                            ContentReport
churches/**                                          SDA church DB (read-only)
ziyon_notifications/{id}                             In-app notification feed
                                                     (BACKEND-WRITTEN, shared)
schedules/{id}, teams/{id}                           LEGACY top-level (admin only,
                                                     unused by clients - do NOT write)
notification_apps/{appID}/users/{uid}/...            Backend FCM topic store
                                                     (written by the API, not clients)
```

`campings.attendees` is **NOT** a field on the camping document.
Attendees live exclusively in the `registrations` subcollection.

---

## 2. `users/{uid}` - User profile

- **Doc ID**: the Firebase Auth `uid` (deterministic). Mirrored in field `uid`.
- Written by: sign-in (Apple/Google), onboarding, profile edit, account-deletion flags, admin role change. All `merge: true` (partial).

| Wire key | Type | Req/Opt | Default | Notes |
| --- | --- | --- | --- | --- |
| `uid` | string | opt | doc ID | mirror of doc ID |
| `displayName` | string | opt | `""` | trimmed; denormalized everywhere (§ Denormalization) |
| `email` | string | opt | `""` | trimmed; on sign-in only written if existing blank |
| `age` | int | opt | `nil` | **delete-when-nil**; drives `ageGroup` |
| `ageGroup` | string | opt | `nil` | **derived from `age`**, **delete-when-nil**. `CampingAgeGroup`: `kids`/`youth`/`adult` |
| `gender` | string | opt | `nil` | **delete-when-nil**. `UserGender`: `female`/`male`/`prefer_not_to_say` |
| `church` | string | opt | `""` | trimmed; RBAC scope key for leadership |
| `skills` | array\<string> | opt | `[]` | trimmed, empties dropped |
| `profession` | string | opt | `""` | |
| `education` | string | opt | `""` | |
| `pathfinderRank` | string | opt | `""` | |
| `phone` | string | opt | `""` | |
| `preferredLanguage` | string | opt | `preferredLanguage ?? languages.first ?? ""` | ISO-639-1-ish code |
| `languages` | array\<string> | opt | `[]` | language codes |
| `role` | string | opt | `guest` | `UserRole` raw (§ Enums). Legacy `senior`/`youth` read as `user`. First create writes literal `"guest"` |
| `photoURL` | string | opt | `nil` | **absolute URL string**; **delete-when-nil**; on sign-in only written if no existing value |
| `photoPublicID` | string | opt | `nil` | Cloudinary public id; **delete-when-nil** |
| `onboardingCompleted` | bool | opt | `false` | **wire key for the Swift `isProfileComplete` property** - there is no `isProfileComplete` field |
| `providerIDs` | array\<string> | opt | - | e.g. `apple.com`, `google.com`; write-only |
| `lastAuthProvider` | string | opt | - | `apple` / `google`; write-only |
| `stripeCustomerID` | string | opt | - | written by the **backend** on first payment |
| `pendingDeletionAt` | timestamp | opt | `nil` | set on deletion request; **delete-when-nil** on cancel. 30-day grace; **purge is server-side** |
| `deletionRequestedBy` | string | opt | - | requesting uid; **delete-when-nil** on cancel |
| `createdAt` | timestamp | opt | - | `serverTimestamp()`, first create only |
| `updatedAt` | timestamp | opt | - | `serverTimestamp()`, every write |

Sign-in is first-write-wins for `email`/`displayName`/`photoURL` (auth
data never overwrites a user-edited value). Onboarding writes
`languages = [preferredLanguage]` (single element) - the multi-language
array only appears via full profile edit.

The admin user-management list decoder reads the doc ID from an **`id`**
key (not `uid`) and requires `updatedAt`. When you write a user doc that
admins will manage, also set `id == uid`.

### 2.1 `users/{uid}/children/{childId}` - ChildParticipant

- **Doc ID**: client UUID (`UUID().uuidString`, lowercase-hyphen), stable across edits, mirrored in `id`.
- Hard delete on remove. Collection-group query `children` filtered by `displayName == X && age == Y` (needs composite index).

| Wire key | Type | Req/Opt | Default | Notes |
| --- | --- | --- | --- | --- |
| `id` | string | **req** | - | UUID, == doc ID |
| `guardianID` | string | **req** | - | owning user uid |
| `displayName` | string | **req** | - | trimmed |
| `age` | int | **req** | - | UI range 0–17 |
| `ageGroup` | string | write-only | - | derived from `age` |
| `gender` | string | **req** | - | `UserGender` raw |
| `church` | string | **req** | - | |
| `preferredLanguage` | string | **req** | - | |
| `languages` | array\<string> | write-only | - | `[preferredLanguage]` or `[]` |
| `emergencyContactName` | string | **req** | - | |
| `emergencyContactPhone` | string | **req** | - | |
| `medicalNotes` | string | opt | `""` | |
| `relationship` | string | opt | `parent` | `FamilyRelationship` (§ Enums) |
| `customRelationshipLabel` | string | opt | `""` | only when `relationship == other` |
| `guardianConsentAt` | timestamp | opt | `nil` | presence == consent; **delete-when-nil** |
| `photoURL` | string | opt | `nil` | absolute URL; **delete-when-nil** |
| `photoPublicID` | string | opt | `nil` | **delete-when-nil** |
| `createdAt` | timestamp | opt | `nil` | first create only |
| `updatedAt` | timestamp | opt | `nil` | every save |

### 2.2 `users/{uid}/notificationTokens/{docId}`

- **Doc ID**: lowercase hex **SHA-256 of the FCM token string** (64 chars). Idempotent upsert.
- This is the **client-direct** Firestore copy (RBAC self-only). The backend API ALSO stores a copy under `notification_apps/...` (see `04-backend-api.md`). Web/Android write **both** (Firestore here + call `POST /notifications/devices`).

| Wire key | Type | Notes |
| --- | --- | --- |
| `token` | string | raw FCM token |
| `platform` | string | iOS writes `"ios"`. Web → `"web"`, Android → `"android"` |
| `provider` | string | `"fcm"` |
| `role` | string | `UserRole` raw at registration time |
| `localeIdentifier` | string | e.g. `en_US`, `fr_FR` |
| `appVersion` | string | or `"unknown"` |
| `createdAt` | timestamp | first create only |
| `updatedAt` | timestamp | every write |

No `appID` field here (that lives only in the backend API payload).

### 2.3 `users/{uid}/notificationSettings/default`

- **Doc ID**: literal **`default`** (one doc per user).

| Wire key | Type | Default | Notes |
| --- | --- | --- | --- |
| `isEnabled` | bool | `true` | |
| `authorizationState` | string | `notDetermined` (unknown→`unknown`) | `notDetermined`/`denied`/`authorized`/`provisional`/`ephemeral`/`unknown` |
| `announcementsEnabled` | bool | `true` | |
| `chatMessagesEnabled` | bool | `true` | |
| `scheduleRemindersEnabled` | bool | `true` | |
| `roleMessagesEnabled` | bool | `true` | |
| `teamUpdatesEnabled` | bool | `true` | |
| `subscribedCampingIDs` | array\<string> | `[]` | trimmed, deduped, sorted on write |
| `subscribedRoleRawValues` | array\<string> | `[]`→`[user.role]` | `UserRole` raws. **Stored field is `subscribedRoleRawValues`** (the Swift `subscribedRoles` is computed, not stored) |
| `subscribedTeamIDs` | array\<string> | `[]` | trimmed, deduped, sorted |
| `updatedAt` | timestamp | - | every write. **No client `createdAt`** here |

`appID`/`userID` are NOT stored in this doc (path encodes user; appID constant).

### 2.4 `users/{uid}/blockedUsers/{blockedUid}`

- **Doc ID**: the blocked user’s uid. Block = `merge:true`; unblock = hard delete.

| Wire key | Type | Notes |
| --- | --- | --- |
| `blockedUserID` | string | mirror of doc ID |
| `displayName` | string | denormalized at block time; read falls back to doc ID |
| `blockedAt` | timestamp | `serverTimestamp()`; read **strictly as Timestamp** (no Date fallback) |

### 2.5 `users/{uid}/badges/{achievementId}` - EarnedBadge

- **Doc ID**: the catalog `achievementId` (deterministic, one per achievement).
- **Written by the backend** (`badge-evaluator` for objective badges; admin/leader award flow). iOS/web/Android are **readers**; clients must not self-award (RBAC enforces - `request.auth.uid != uid`).

| Wire key | Type | Default | Notes |
| --- | --- | --- | --- |
| `id` | string | `UUID` | == achievementId == doc ID |
| `userID` | string | `""` | target uid |
| `earnedAt` | timestamp | `now` | `serverTimestamp()` |
| `campingID` | string \| **null** | `nil` | written as explicit **`null`** (`NSNull`) when none - **not** omitted, **not** deleted |
| `note` | string \| **null** | `nil` | explicit **`null`** when empty; backend auto-award writes `"Auto-awarded"` |

The full static `AchievementCatalog` (50 ids, rarities, award kind) is in
`08-feature-parity.md` → it is in-code, **not** in Firestore; only ids in
the catalog are kept on read.

---

## 3. `campings/{campingId}` - Camping

- **Doc ID**: client-supplied `camping.id` (human slug or UUID, **not** Firestore auto-id).
- **Write path bypasses Codable** - the spec is the manual `payload(for:)`. Fields the Swift model has but the payload does **not** write: `attendees` (never on wire), `guidelines` (only via the guidelines update path), `winnerRevealPolicy` (only via the reveal path).

| Wire key | Type | Req/Opt | Default | Notes |
| --- | --- | --- | --- | --- |
| `id` | string | req(write) | `UUID` | == doc ID |
| `title` | string | **req** | throws | |
| `description` | string | **req** | throws | |
| `startDate` | timestamp | **req** | throws | collection ordered by this |
| `endDate` | timestamp | **req** | throws | |
| `organizerLevel` | map | **req** | throws | `{ type, value }` - see § 3.1. **Not a string** |
| `location` | string | **req** | throws | flat string (NOT a `CampingLocation` map) |
| `locationLatitude` | double | opt | `nil` | **delete-when-nil** |
| `locationLongitude` | double | opt | `nil` | **delete-when-nil** |
| `registrationStatus` | string | **req** | throws | `open`/`closed`/`cancelled` |
| `participantCapacity` | int | opt | `nil` | **delete-when-nil**. Triggers waitlist when reached |
| `winnerRevealPolicy` | map | opt | `nil` | § 3.2. Written only by the reveal flow; RBAC-gated separately |
| `logoURL` | string | opt | `nil` | **delete-when-empty** |
| `logoPublicID` | string | opt | `nil` | **delete-when-empty** |
| `guidelines` | string | opt | `""` | markdown; written only by the guidelines update path |
| `registrationFeeCents` | int | opt | `nil` | **cents/minor units**. delete-when-`<=0` |
| `feeCurrency` | string | opt | `nil` | e.g. `usd`/`eur`. delete-when-blank |
| `priceItems` | array\<map> | opt | `[]` | always written (may be `[]`). § 3.3 |
| `agePrices` | array\<map> | opt | `[]` | always written. § 3.4 |
| `transportationOptions` | array\<map> | opt | `[]` | always written. § 3.5 |
| `createdByUID` | string | opt | `nil` | creator signature; stamped once on create; omit-when-empty. Authorizes delete |
| `createdByName` | string | opt | `nil` | omit-when-empty |
| `createdAt` | timestamp | server | - | `serverTimestamp()`, create only |
| `updatedAt` | timestamp | server | - | `serverTimestamp()`, every save/cancel/guidelines write |

### 3.1 `organizerLevel` map (required)

```jsonc
{ "type": "church" | "regional" | "international" | "custom",
  "value": "<free-text org name>" }
```

Both keys required (decode throws otherwise). RBAC uses
`organizerLevel.type == "church"` + `organizerLevel.value == <user church>`
for own-church scoping.

### 3.2 `winnerRevealPolicy` map (optional)

| key | type | default | notes |
| --- | --- | --- | --- |
| `hideDate` | timestamp | `nil` | effective default = `endDate − 24h` (computed client-side) |
| `revealDate` | timestamp | `nil` | optional auto-reveal |
| `isRevealed` | bool | `false` | **required if the map is present** (non-optional) |
| `revealedBy` | string | `nil` | omit-when-nil |
| `revealedByName` | string | `nil` | omit-when-nil |
| `revealedAt` | timestamp | `nil` | omit-when-nil |

Written via `updateData(["winnerRevealPolicy": ...])`. Own-church camp
edits are **forbidden** from touching this key; only `canRevealWinners`.

### 3.3 `priceItems[]` element (CampingPriceItem)

| key | type | notes |
| --- | --- | --- |
| `id` | string | UUID |
| `name` | string | |
| `details` | string | always present (empty string if none) |
| `amountCents` | int | **cents**, `>= 0` |
| `currency` | string | ISO-4217, **uppercase** (e.g. `EUR`) |
| `paymentOptions` | array\<string> | `cardOneTime`/`cardInstallments`/`bankTransfer` (camelCase) |
| `iban` | string | omit-when-nil |
| `ibanHolder` | string | omit-when-nil |
| `isMandatory` | bool | |

### 3.4 `agePrices[]` element (CampingAgePrice)

| key | type | notes |
| --- | --- | --- |
| `id` | string | UUID |
| `label` | string | always present |
| `minAge` | int | `>= 0` |
| `maxAge` | int | **omit-when-nil** (nil = no upper bound) |
| `amountCents` | int | **cents** |

Band selection: lowest matching `minAge` wins; flat `registrationFeeCents`
is the fallback for uncovered ages.

### 3.5 `transportationOptions[]` element (CampingTransportationOption)

| key | type | notes |
| --- | --- | --- |
| `id` | string | UUID |
| `name` | string | always present |
| `mode` | string | `TransportationMode` raw - **camelCase** (`bus`,`coach`,`minibus`,`shuttle`,`train`,`carpool`,`ownCar`,`plane`,`boat`,`bike`,`onFoot`,`other`) |
| `details` | string | always present |
| `requiresTicket` | bool | when true → registration maps to `provided_bus` + a TransportationBooking |
| `capacity` | int | omit-when-nil |
| `feeCents` | int | **cents**, omit-when-nil |
| `currency` | string | **uppercase**, default `EUR` |

### 3.6 `campings/{id}/registrations/{attendeeId}` - CampingAttendee

- **Doc ID**: `attendee.id` == `participant.id`. For self-registration this **equals the user uid**; for a child it equals `child.id`.
- Both `userID` and a duplicate `uid` field are written (same value). Decode accepts either.

| Wire key | Type | Req/Opt | Default | Notes |
| --- | --- | --- | --- | --- |
| `id` | string | req(write) | userID/uid/UUID | == doc ID |
| `userID` | string | req(write) | uid → id | |
| `uid` | string | req(write) | - | duplicate of `userID` (legacy/rule compat) |
| `displayName` | string | **req** | throws | denormalized; subcollection ordered by this |
| `church` | string | **req** | throws | |
| `age` | int | **req** | throws | |
| `ageGroup` | string | opt | `nil`→from age | `CampingAgeGroup` raw |
| `gender` | string | opt | `nil` | omit-when-nil. `UserGender` raw |
| `preferredLanguage` | string | opt | `""` | always written |
| `languages` | array\<string> | **req** | throws | always written |
| `participantKind` | string | opt | `self` | `self` / `child` (note `self` is the explicit raw for `selfParticipant`) |
| `guardianID` | string | opt | `nil` | omit-when-nil; guardian fan-out query key |
| `emergencyContactName` | string | opt | `""` | |
| `emergencyContactPhone` | string | opt | `""` | |
| `medicalNotes` | string | opt | `""` | |
| `guardianConsentAt` | timestamp | opt | `nil` | omit-when-nil |
| `transportationChoice` | string | opt | `own_car` | `own_car` / `provided_bus` |
| `transportationBookingID` | string | opt | `nil` | omit-when-nil; form `"{participant.id}-bus"` |
| `transportationOptionID` | string | opt | `nil` | omit-when-nil; refs `transportationOptions[].id` |
| `transportationOptionName` | string | opt | `nil` | omit-when-nil; denormalized |
| `registrationStatus` | string | **req** | throws | `pending`/`approved`/`rejected`/`waitlisted` |
| `paymentStatus` | string | opt | `unpaid` | NOT written on create. `unpaid`/`paid`/`waived`. **Backend** flips to `paid` |
| `photoURL` | string | opt | `nil` | absolute URL string |
| `createdAt` | timestamp | server | - | injected `serverTimestamp()` on create |
| `updatedAt` | timestamp | server | - | `serverTimestamp()` on create + status update |

Status update writes ONLY `{ registrationStatus, updatedAt }`. Waitlist
auto-promote writes `{ registrationStatus: "pending", updatedAt }`.
`deleteAttendee` hard-deletes the doc then cascades into
`checkIns/{attendeeId}`, `transportationBookings` (where
`participantID == attendeeId`), and team `members[]`.
Backend payment confirm additionally writes `paymentReference`,
`paymentUpdatedAt`, and for `registration` kind `registrationStatus:
"approved"` + `approvedVia: "payment"` + `approvedAt`.

---

## 4. Schedule

### 4.1 `campings/{id}/schedule/config` (single doc, ID literal `config`)

| key | type | default | notes |
| --- | --- | --- | --- |
| `campingID` | string | - | always written |
| `reminderTiming` | string | `none` | `ScheduleReminderTiming` (§ Enums). Only the reminder-save path writes it |
| `createdAt` | timestamp | - | first create only |
| `updatedAt` | timestamp | - | every save / every program save |

### 4.2 `.../schedule/config/days/{dayId}` - CampDay

- **Doc ID** (deterministic): `"<campingID>-day-<yyyy-MM-dd>"` where the
  date is the program’s **start date** at local-calendar day, formatted
  with gregorian calendar + `en_US_POSIX` locale + local time zone.

| key | type | default | notes |
| --- | --- | --- | --- |
| `campingID` | string | `""` | |
| `date` | timestamp | `now` | **local-midnight** (`startOfDay`) of the day. Decoder accepts Timestamp or Date |
| `title` | string | `"Camp Day"` (read) | written `""` **only on first create**; never overwritten by a program save (preserves a curated title). In-memory normalizer shows `"Day N"` by position - not persisted |
| `createdAt` | timestamp | - | first create only |
| `updatedAt` | timestamp | - | every upsert |

### 4.3 `.../days/{dayId}/programs/{programId}` - Program

- **Doc ID**: free `program.id`. Menu-generated programs use `"menu-<foodMenuEntry.id>"` (see § 4.5).
- **Invariant**: `campDayID` is **always recomputed on write** from
  `CampDayKey.id(campingID, startDate)` - never trust an inbound value.
  The program is physically nested under that day and `campDayID`
  duplicates it.

| key | type | default | notes |
| --- | --- | --- | --- |
| `campingID` | string | `""` | |
| `campDayID` | string | `""` | == parent day id; recomputed from `startDate` |
| `title` | string | `""` | |
| `type` | string | `other` | `ProgramType` (§ Enums) |
| `startDate` | timestamp | `now` | full datetime instant |
| `endDate` | timestamp | `now` | may be a later calendar day |
| `location` | string | `""` | kept in sync with the venue pin name when linked |
| `description` | string | `""` | meal programs: rendered dish list (§ 4.5) |
| `venuePointID` | string | `nil` | optional `VenuePoint` link. **delete-when-empty** (merge-safe clear) |
| `createdAt` | timestamp | - | first create only |
| `updatedAt` | timestamp | - | every save |

`saveProgram` is a multi-step move: upsert `schedule/config`; **delete**
the program doc under any other day (a program lives under exactly one
day); upsert the day; upsert the program; **prune** empty untitled day
docs. `normalizeDays` re-files mis-keyed programs idempotently.

### 4.4 `campings/{id}/foodMenu/{entryId}` - FoodMenuEntry

- **Doc ID** (deterministic): `"<yyyy-MM-dd>-<meal>"` (date key gregorian
  - `en_US_POSIX`, local zone). `campingID` is intentionally **not** in
  the id (collection is already camp-scoped).
- Not Codable - manual map. Query ordered by `date` asc.

| key | type | req | default | notes |
| --- | --- | --- | --- | --- |
| `campingID` | string | - | path arg | |
| `date` | timestamp | **req** | drop | entry dropped if neither Timestamp nor Date |
| `meal` | string | **req** | drop | `FoodMealKind`: `breakfast`/`lunch`/`dinner`/`snack` |
| `dishes` | array\<string> | opt | `[]` | |
| `notes` | string | opt | `""` | |

### 4.5 Menu ↔ Program sync (application-level - no Firestore trigger)

`FoodMenuProgramSync` keeps a `foodMenu` entry and a generated `Program`
in sync **on the client**. Web/Android **must reimplement this** and
write **both** docs whenever either side changes. Correlation is by
deterministic id only:

- menu id `"<yyyy-MM-dd>-<meal>"`; program id `"menu-<menu id>"`;
  program `campDayID = "<campingID>-day-<yyyy-MM-dd>"`.
- Entry→Program: `title`/`type`/`description` are **menu-owned** (always
  regenerated). `startDate`/`endDate`/`location`/`campDayID`/`id` are
  **preserved** if a leader-edited program already exists.
- Default meal times: breakfast 08:00 (45m), snack 10:30 (30m), lunch
  12:30 (60m), dinner 18:30 (60m). Default location `"Dining hall"`.
- `description` format: each dish on `"- <dish>"` line; if notes, a blank
  line then `"Notes: <notes>"`; joined with `\n`. Parsing splits on
  newline **and** comma, strips `-`/`*`/`Menu:` prefixes, drops `Notes:`.

---

## 5. Teams · Games · Activities

### 5.1 `campings/{id}/teams/{teamId}` - Team

- **Doc ID**: client UUID. `Team.id` = doc ID (no `id` field).
- Every write **rewrites the whole doc** (no `increment`) and **must**
  re-derive `memberUserIDs = members[].userID` - the RBAC `isTeamMember`
  check (team chat) reads `memberUserIDs`.

| key | type | default | notes |
| --- | --- | --- | --- |
| `campingID` | string | path arg | |
| `name` | string | `""` | |
| `slogan` | string | `""` | always written |
| `symbolName` | string | `"shield.lefthalf.filled"` | SF Symbol id (map to your own icon set) |
| `colorHex` | string | `"#2E7D32"` | incl. leading `#` |
| `points` | int | `0` | base points (excludes member points & penalties) |
| `penalties` | array\<map> | `[]` | § 5.2 |
| `members` | array\<map> | `[]` | § 5.2 |
| `memberUserIDs` | array\<string> | `[]` | **derived**, always written, RBAC-critical |
| `photoURL` | string | `nil` | string (not URL); **delete-when-empty** |
| `photoPublicID` | string | `nil` | **delete-when-empty** |
| `createdAt` | timestamp | `now` | create only |
| `updatedAt` | timestamp | `now` | `serverTimestamp()` every write |

`totalScore = points + Σ members[].personalScore − Σ penalties[].points`
is **computed on read** - never stored.

### 5.2 Embedded `members[]` (TeamMember) / `penalties[]` (TeamPenalty)

TeamMember: `id` (req, →userID→UUID), `userID` (req), `displayName`
(req,→`"Participant"`), `notificationUserID` (opt, omit-when-empty),
`church` (req), `ageGroup` (opt, `CampingAgeGroup`), `gender` (opt,
`UserGender`), `preferredLanguage` (opt `""`), `languages` (opt `[]`),
`role` (req, `TeamMemberRole` = `member`/`captain`/`viceCaptain`),
`personalScore` (req int `0`), `photoURL` (opt, absolute string,
omit-when-nil). Captaincy is per-member `role`; **no** team-level captain
field. At most one `captain` + one `viceCaptain` (client-enforced on
every mutation path).

TeamPenalty: `id` (req UUID), `reason` (req `""`), `points` (req int,
stored **positive**, subtracted in total), `createdAt` (req, written as a
raw Date → Timestamp; **not** serverTimestamp).

### 5.3 `campings/{id}/games/{gameId}` - Game

Pure Swift Codable (wire keys == property names; `Date`→Timestamp;
`updatedAt` is a **client clock** `Date()`, not serverTimestamp).

| key | type | notes |
| --- | --- | --- |
| `id` | string | **stored in body** + doc ID |
| `campingID` | string | |
| `name` | string | |
| `rules` | string | `""` default |
| `pointRules` | array\<map> | § 5.4 |
| `createdBy` | string | creator uid |
| `createdAt` | timestamp | model init `Date()`; ordered by this |
| `updatedAt` | timestamp | client `Date()` every save |

### 5.4 Embedded `pointRules[]` (PointRule)

`id` (UUID), `name`, `points` (int, may be negative), `reason` (`""`),
`ruleBrokenPenalty` (int, omit-when-nil), `maxUses` (int, omit-when-nil),
`category` (string free-text, omit-when-nil), `appliesTo`
(`team`/`user`/`any`), `visibility` (`immediate`/`afterReveal`).

### 5.5 `campings/{id}/activities/{activityId}` - Activity (immutable audit)

Full-set write (`merge:false`); `update` is **forbidden** by RBAC.
Create requires `campingID == path` and `createdBy == auth.uid`.

| key | type | notes |
| --- | --- | --- |
| `id` | string | body + doc ID |
| `campingID` | string | **must equal path** |
| `gameID` | string | indexed (`whereField`) |
| `pointRuleID` | string | omit-when-nil |
| `name` | string | |
| `points` | int | **signed** (negative = penalty/correction) |
| `reason` | string | `""` |
| `targetTeamID` / `targetTeamName` | string | omit-when-nil |
| `targetUserID` / `targetUserName` | string | omit-when-nil |
| `visibility` | string | `immediate`/`afterReveal` - RBAC: participants only read `immediate` |
| `previousScore` | int | snapshot before delta |
| `newScore` | int | `previousScore + points` |
| `createdBy` | string | **must equal auth.uid** |
| `createdByName` | string | |
| `createdAt` | timestamp | client `Date()`; ordered desc; capped 500 |

Awarding contract: write the Activity first, then mutate the team doc.
A **negative team award** is recorded as an appended positive-magnitude
`penalties[]` entry - **never** a decrement of `points`. A user award
adds the delta to that member’s `personalScore`. Every team write
rewrites the full doc + `memberUserIDs` + `updatedAt`.

---

## 6. Communication

### 6.1 `campings/{id}/chat/{messageId}` and `campings/{id}/teams/{teamId}/chat/{messageId}` - ChatMessage

- **Doc ID**: client UUID. `senderID == auth.uid`. Send = full `set` (no merge); pin/soft-delete = `updateData`. Read ordered by `createdAt` asc, `limit(toLast: 200)`.
- Decode drops the message if any of `campingID`, `senderID`, `senderName`, `text` is missing/non-string.

| key | type | req | default | notes |
| --- | --- | --- | --- | --- |
| `campingID` | string | **req** | drop | |
| `teamID` | string | opt | `nil` | written **only** in team chat; not read back |
| `senderID` | string | **req** | drop | == auth.uid |
| `senderName` | string | **req** | drop | denormalized |
| `senderChurch` | string | opt | `""` | denormalized |
| `senderPreferredLanguage` | string | opt | `""` | denormalized |
| `senderGender` | string | opt | `nil` | `UserGender`; omit-when-nil |
| `senderPhotoURL` | string | opt | `nil` | absolute URL; omit-when-nil |
| `text` | string | **req** | drop | client cap **500** chars; RBAC cap **≤2000** |
| `createdAt` | timestamp | opt | `now` | `serverTimestamp()` |
| `pinned` | bool | opt | `false` | |
| `isDeleted` | bool | opt | `false` | soft delete keeps the doc; display → `"Message removed"` |
| `deletedByID` | string | opt | `nil` | on soft delete |
| `deletedAt` | timestamp | opt | `nil` | `serverTimestamp()` on soft delete |

### 6.2 `announcements/{announcementId}` - Announcement

- **Doc ID**: client slug/UUID. Top-level. Read ordered `createdAt` desc, `limit(100)`.

| key | type | default | notes |
| --- | --- | --- | --- |
| `id` | string | doc ID | injected on read |
| `title` | string | `""` | |
| `body` | string | `""` | markdown. Decode also accepts legacy `description` |
| `notificationTargetRoleRawValue` | string | `nil` | written as **`""`** when none (not omitted). `UserRole` raw |
| `authorID` | string | `""` | |
| `authorName` | string | `"Campzone Team"` | |
| `authorPhotoURL` | string | `nil` | absolute URL; **omit-when-nil** |
| `createdAt` | timestamp | `now` | `serverTimestamp()`, create only |
| `updatedAt` | timestamp | `=createdAt` | `serverTimestamp()`, every write |
| `attachments` | array\<map> | `[]` | embedded |

Attachment element: `id`, `kind` (`image`/`pdf`), `fileName`,
`contentType` (MIME), `storagePath` (**holds the Cloudinary public_id**;
`""` when empty), `downloadURL` (Cloudinary secure_url; written **`""`**
when nil).

### 6.3 `campings/{id}/polls/{pollId}` - Poll + `.../votes/{voterId}` - PollVote

Poll (`campingID` NOT stored - from path):

| key | type | default | notes |
| --- | --- | --- | --- |
| `question` | string | `""` | |
| `description` | string | `""` | |
| `options` | array\<map> | `[]` | element `{ id (req), label (req), voteCount (int, 0) }`; `voteCount` mutated inside the vote transaction |
| `allowsMultiple` | bool | `false` | |
| `showsResultsBeforeClose` | bool | **`true`** | |
| `isOpen` | bool | `false` | |
| `createdByID` | string | `""` | |
| `createdByName` | string | `""` | |
| `createdAt` | timestamp | `now` | **client `Date()`**, not serverTimestamp |
| `closesAt` | timestamp \| **null** | `nil` | written as explicit Firestore **`null`** when no close date (not omitted) |

PollVote - **doc ID == `voterID`** (one per voter; re-vote overwrites):
`voterID` (req, ==doc ID), `selectedOptionIDs` (array\<string>, `[]`),
`votedAt` (timestamp, `serverTimestamp()`). Cast inside a transaction:
decrement old options, increment new, set vote doc + poll `options`.

### 6.4 `contentReports/{reportId}` - ContentReport

- **Doc ID**: client UUID. Submit = full `set` (no merge). **Brittle read**: the admin list aborts entirely if any doc is missing a required field or has an unknown enum.

| key | type | req | notes |
| --- | --- | --- | --- |
| `id` | string | **req** | == doc ID (injected) |
| `target` | string | **req** | `announcement`/`camping`/`chatMessage` (**camelCase**) |
| `contentID` | string | **req** | id of reported content |
| `reporterID` | string | **req** | == auth.uid (RBAC) |
| `reason` | string | **req** | `inappropriate`/`spam`/`misinformation`/`harassment`/`other` |
| `note` | string | **req** | may be `""` (written, not omitted) |
| `createdAt` | timestamp | **req** | `serverTimestamp()` |
| `status` | string | **req** | `pending` on submit; `dismissed`/`resolved` |
| `reviewedByID` | string | opt | on status update |
| `reviewedAt` | timestamp | opt | `serverTimestamp()` on status update |

### 6.5 `ziyon_notifications/{id}` - in-app feed (BACKEND-WRITTEN, shared multi-app)

Clients are **readers only** (RBAC: `create/update/delete: false`).
Read filtered `whereField("topic", isEqualTo: <topic>).limit(200)` per
visible topic, merged & deduped by `id`, sorted `sentAt` desc. Tolerant
decoder. **`appID` MUST be `"campzone"`** or the doc is ignored client-side.

| key | type | default | notes |
| --- | --- | --- | --- |
| `id` | string | doc ID | injected; `UUID` fallback |
| `appID` | string | `"campzone"` | non-campzone docs filtered out |
| `kind` | string | inferred | backend writes **`kind`**; decoder also accepts `type` |
| `title` | string | `"Notification"` | |
| `body` | string | `""` | |
| `topic` | string | `""` | audience routing (§ 04-backend-api topics) |
| `sentAt` | **iso-string** | `.distantPast` | backend writes `new Date().toISOString()` → `2026-05-16T09:00:00.000Z`. Decoder also accepts Timestamp/Date |
| `createdAt` | iso-string | - | fallback for `sentAt` |
| `announcementID` | string | `nil` | kind inference: → `announcement` |
| `campingID` | string | `nil` | → `chatMessage` if no other |
| `pollID` | string | `nil` | → `poll` |
| `teamID` | string | `nil` | team-scoped |
| `role` | string | from topic | derived from topic prefix `campzone_role_` |
| `senderId` | string | - | backend writes `senderId` (note casing) |
| `messageId` | string | - | FCM message id |

`AppNotificationKind` raw: `announcement`, `chat_message` (also accepts
`chatmessage`), `poll`, `schedule_reminder` (also `schedulereminder`),
`unknown`. Kind inference order: explicit kind → `type` →
`announcementID`→announcement → `pollID`→poll → `campingID`→chatMessage →
`unknown`.

---

## 7. Camp operations

### 7.1 `campings/{id}/checkIns/{attendeeId}` - CheckInRecord

- **Doc ID == attendeeId** (one per attendee; re-check-in overwrites - full `set`). Guardians may read a single child doc by id (list is denied).

| key | type | req | default | notes |
| --- | --- | --- | --- | --- |
| `campingID` | string | **req** | drop | |
| `attendeeID` | string | **req** | drop | == doc ID |
| `userID` | string | **req** | drop | attendee/child uid |
| `displayName` | string | **req** | drop | |
| `church` | string | opt | `""` | |
| `preferredLanguage` | string | opt | `""` | |
| `method` | string | **req** | drop | `qr` / `manual` |
| `checkedInBy` | string | **req** | drop | scanner uid (RBAC: == auth.uid) |
| `checkedInAt` | timestamp | server | `now` | `serverTimestamp()` |
| `ageGroup` | string | opt | `nil` | `CampingAgeGroup`; omit-when-nil |
| `gender` | string | opt | `nil` | `UserGender`; omit-when-nil |
| `photoURL` | string | opt | `nil` | absolute URL; omit-when-nil |

QR payload (not a doc): `campzone://checkin?v=1&c=<campingID>&a=<attendeeID>&u=<userID>&iat=<unixSeconds>`.

### 7.2 `campings/{id}/transportationBookings/{bookingId}` - TransportationBooking

- **Doc ID**: `bookingId`. Read merges `userID==uid` + `guardianID==uid` queries.
- RBAC checks literal `paymentStatus == "unpaid"` and `boardingStatus == "not_boarded"` on create.

| key | type | req | default | notes |
| --- | --- | --- | --- | --- |
| `id` | string | opt | doc ID | |
| `campingID` | string | **req** | drop | |
| `registrationID` | string | **req** | drop | |
| `participantID` | string | **req** | drop | |
| `participantKind` | string | **req** | drop | `self` / `child` |
| `participantName` | string | **req** | drop | |
| `userID` | string | **req** | drop | |
| `paymentStatus` | string | **req** | drop | `unpaid`/`paid`/`waived` |
| `boardingStatus` | string | **req** | drop | `not_boarded`/`boarded` |
| `validFrom` | timestamp | **req** | `now` | raw Date → Timestamp |
| `validUntil` | timestamp | **req** | `now` | |
| `ticketToken` | string | **req** | drop | opaque server-issued secret |
| `guardianID` | string | opt | `nil` | omit-when-nil |
| `boardedBy` / `boardedAt` | string / ts | opt | `nil` | on `markBoarded` |
| `paymentUpdatedBy` / `paymentUpdatedAt` | string / ts | opt | `nil` | on payment update |
| `paymentReference` | string | opt | - | **backend** writes the Stripe intent id on confirm |
| `createdAt` / `updatedAt` | timestamp | opt | `nil` | created by booking creator; `updatedAt` serverTimestamp on every mutation |

`canBoard` = `paymentStatus ∈ {paid,waived}` AND `boardingStatus == not_boarded`.
QR payload: `campzone://transport?v=1&c=<campingID>&b=<bookingID>&r=<registrationID>&p=<participantID>&t=<ticketToken>`.

### 7.3 `campings/{id}/lodging/{unitId}` - LodgingUnit

| key | type | default | notes |
| --- | --- | --- | --- |
| `campingID` | string | path | |
| `name` | string | `""` | |
| `kind` | string | `tent` | `tent`/`cabin`/`room`/`dorm` |
| `capacity` | int | `4` | clamped `>= 1` |
| `genderPolicy` | string | `any` | `any`/`male`/`female`/`family` |
| `notes` | string | `""` | |
| `occupantIDs` | array\<string> | `[]` | attendee ids; occupancy is denormalized **into the unit** (no assignment collection) |
| `createdAt` | timestamp | `now` | first create only |
| `updatedAt` | timestamp | `now` | every write |

### 7.4 `campings/{id}/venueMap/config` (single doc, ID literal `config`)

| key | type | default | notes |
| --- | --- | --- | --- |
| `campingID` | string | path | |
| `imageURL` | string | `nil` | Cloudinary secure_url; **delete-when-empty** |
| `imagePublicID` | string | `nil` | **delete-when-empty** |
| `points` | array\<map> | `[]` | embedded pin set |
| `updatedAt` | timestamp | `now` | `serverTimestamp()` |

`points[]` element: `id` (UUID), `name` (req - element dropped if
missing), `category` (`tent`/`stage`/`dining`/`firstAid`/`restroom`/
`parking`/`water`/`program`/`info`/`other`), `note` (`""`), `imageX`/
`imageY` (double 0…1, omit-when-nil), `latitude`/`longitude` (double,
omit-when-nil). A pin may carry image position, real coordinate, both,
or neither.

### 7.5 `campings/{id}/feedback/{uid}` - CampFeedback

- **Doc ID == submitting uid** (one per person; RBAC enforces `overallRating` int 1–5). Codable; all fields effectively required.

| key | type | notes |
| --- | --- | --- |
| `id` | string | == doc ID (uid) |
| `campingID` | string | |
| `userID` | string | uid (or child id) |
| `displayName` | string | |
| `submittedAt` | timestamp | overridden to `serverTimestamp()` |
| `overallRating` | int | **1–5, RBAC-enforced** |
| `programFeedback` | array\<map> | element `{ id (==Program.id), programTitle, rating (int; 0 = skipped), comment }` |
| `highlights` | string | may be `""` |
| `improvements` | string | may be `""` |
| `wouldReturn` | bool | |
| `isAnonymous` | bool | UI hides displayName when true (still stored) |
| `updatedAt` | timestamp | `serverTimestamp()` (metadata) |

### 7.6 `campings/{id}/media/{mediaId}` - MediaItem + `albumSettings/default`

MediaItem (full `set`, ordered `uploadedAt` desc): `campingID`, `kind`
(`photo`/`video`, req), `secureURL` (req, Cloudinary), `publicID` (req),
`uploaderID` (req), `uploaderName` (req), `uploadedAt` (req,
`serverTimestamp()`), `caption` (`""`), `thumbnailURL` (opt),
`width`/`height` (int, opt), `durationSeconds` (double, opt).

`albumSettings/default` (single doc): `allowedUploadRoles`
(array\<string> of `UserRole` raws, sorted; default
`["admin","leader","pastor","photographer","youth_director"]`).

### 7.7 `campings/{id}/songs/{songId}` - Song

| key | type | default | notes |
| --- | --- | --- | --- |
| `title` | string | `""` | |
| `artist` / `composer` | string | `""` | |
| `lyrics` | string | `""` | plain blob |
| `chords` | string | `""` | legacy ChordPro-ish text |
| `lyricsParts` | array\<map> | `[]` | element `{ id, kind (`intro`/`verse`/`preChorus`/`chorus`/`bridge`/`instrumental`/`outro`/`custom`), number (int ≥1), title, text }` |
| `chordSheet` | map | parsed/empty | see below |
| `audio` | map | `nil` | primary take; **delete-when-empty** |
| `audioFiles` | array\<map> | `[]` | all takes; falls back to `[audio]` |
| `youtubeLink` / `pdfLink` | string | `""` | |
| `orderIndex` | int | `0` | list order |
| `isPinnedTheme` | bool | `false` | one per camp (others batch-cleared) |
| `favoriteUserIDs` | array\<string> | `[]` | `arrayUnion`/`arrayRemove` |
| `createdAt` | timestamp | `now` | first create only |
| `updatedAt` | timestamp | `now` | every write |

`chordSheet` map: `id`, `originalKey` (string - **lossy**: only
`G/D/A/F/Bb/B♭/Am/A minor/Em/E minor` decode back, everything else → C
major), `tempo` (int, omit-when-nil), `timeSignature` (string e.g.
`"4/4"`, omit-when-nil), `capo` (int, omit-when-nil), `updatedAt`
(**raw Date**, not serverTimestamp), `lines` (array\<map>). `lines[]`:
`id` (UUID string), `text`, `isSectionHeader` (bool), `chords`
(array\<map>). `lines[].chords[]`: `id`, `chord` (**req - display
string** e.g. `"Am"`, `"D/F♯"`, `"Cmaj7(9)"`; re-parsed on read, dropped
if unparseable - **not** a structured object), `position` (int),
`lane` (int), `freeX` (double, omit-when-nil).

`audio`/`audioFiles[]` element (SongAudio): `id`, `fileName`
(`"Song audio"`), `contentType` (`"audio/mpeg"`), `storagePath`
(Cloudinary public_id, video resource type), `downloadURL` (secure_url;
`""` when nil), `kind` (`mp3`/`m4a`/`wav`/`aac`/`other`), `duration`
(double sec), `fileSize` (int64 bytes), `voiceType` (string).

### 7.8 `campings/{id}/payments/{paymentIntentId}` - Payment audit (BACKEND-ONLY)

**No client reads or writes this.** Written by the notification backend
(`POST /payments/intent` seeds it, `POST /payments/confirm` finalizes).
Documented in `04-backend-api.md`. Fields: `uid`, `kind`
(`registration`/`transportation`/`priceItem`), `campingID`,
`referenceID`, `amount` (int cents), `currency` (string), `status`
(Stripe status; seeded `"created"`), `paid` (bool), `createdAt`,
`updatedAt` (serverTimestamps). Falls back to top-level
`payments/{paymentIntentId}` if no `campingID`.

---

## 8. Enum raw-value reference (case-sensitive - copy exactly)

| Enum | Wire field(s) | Raw strings |
| --- | --- | --- |
| `UserRole` | `role` | `guest`, `user`, `youth_director`, `pastor`, `game_master`, `leader`, `photographer`, `adult`, `admin`. Legacy read-only: `senior`,`youth` → `user` |
| `UserGender` | `gender` | `female`, `male`, `prefer_not_to_say` |
| `CampingAgeGroup` | `ageGroup` | `kids`, `youth`, `adult` (age <13 / 13–35 / ≥36) |
| `Language` | `preferredLanguage`,`languages[]` | ISO-639-1-ish: `en zh hi es fr ar bn pt ru ur id de ja sw mr te tr ta vi ko it th gu fa pl uk ms kn om ro` (stored as free strings - not validated) |
| `FamilyRelationship` | `relationship` | `parent`, `step_parent`, `legal_guardian`, `grandparent`, `sibling`, `aunt`, `uncle`, `cousin`, `friend`, `other` |
| `CampingRegistrationStatus` | camping `registrationStatus` | `open`, `closed`, `cancelled` |
| `CampingRegistrationApprovalStatus` | attendee `registrationStatus` | `pending`, `approved`, `rejected`, `waitlisted` |
| organizer `type` | `organizerLevel.type` | `church`, `regional`, `international`, `custom` |
| `RegistrationParticipantKind` | `participantKind` | `self` (← `selfParticipant`), `child` |
| `TransportationChoice` | `transportationChoice` | `own_car`, `provided_bus` |
| `TransportationPaymentStatus` | `paymentStatus` | `unpaid`, `paid`, `waived` |
| `TransportationBoardingStatus` | `boardingStatus` | `not_boarded`, `boarded` |
| `TransportationMode` | `transportationOptions[].mode` | **camelCase**: `bus`,`coach`,`minibus`,`shuttle`,`train`,`carpool`,`ownCar`,`plane`,`boat`,`bike`,`onFoot`,`other` |
| `CampingPaymentOption` | `priceItems[].paymentOptions[]` | **camelCase**: `cardOneTime`,`cardInstallments`,`bankTransfer` |
| `ProgramType` | program `type` | `reception`,`games`,`preaching`,`prayer`,`breakfast`,`lunch`,`dinner`,`snack`,`other`,`rest`,`break`,`custom` |
| `FoodMealKind` | foodMenu `meal` + id | `breakfast`,`lunch`,`dinner`,`snack` |
| `ScheduleReminderTiming` | `reminderTiming` | `none`,`atStart`,`fiveMinutes`,`fifteenMinutes`,`thirtyMinutes`,`oneHour` |
| `TeamMemberRole` | `members[].role` | `member`,`captain`,`viceCaptain` |
| `PointRuleTarget` | `pointRules[].appliesTo` | `team`,`user`,`any` |
| `PointRuleVisibility` / Activity `visibility` | `visibility` | `immediate`,`afterReveal` (RBAC literal-checks `immediate`) |
| `AnnouncementAttachmentKind` | attachment `kind` | `image`,`pdf` |
| `ContentReportTarget` | `target` | `announcement`,`camping`,`chatMessage` |
| `ContentReportReason` | `reason` | `inappropriate`,`spam`,`misinformation`,`harassment`,`other` |
| `ContentReportStatus` | `status` | `pending`,`dismissed`,`resolved` |
| `AppNotificationKind` | `kind`/`type` | `announcement`,`chat_message`,`poll`,`schedule_reminder`,`unknown` |
| `CheckInMethod` | `method` | `qr`,`manual` |
| `LodgingKind` | lodging `kind` | `tent`,`cabin`,`room`,`dorm` |
| `LodgingGenderPolicy` | `genderPolicy` | `any`,`male`,`female`,`family` |
| `VenueCategory` | `points[].category` | `tent`,`stage`,`dining`,`firstAid`,`restroom`,`parking`,`water`,`program`,`info`,`other` |
| `MediaKind` | media `kind` | `photo`,`video` |
| `SongLyricsPartKind` | `lyricsParts[].kind` | `intro`,`verse`,`preChorus`,`chorus`,`bridge`,`instrumental`,`outro`,`custom` |
| `SongAudioKind` | audio `kind` | `mp3`,`m4a`,`wav`,`aac`,`other` |
| `PaymentKind` | backend `kind` | `registration`,`transportation`,`priceItem` |
| Achievement `rarity` | catalog (in-code) | `common`,`uncommon`,`rare`,`epic`,`legendary` |
| Achievement `awardKind` | catalog (in-code) | `manual`,`automatic` |

`MedalKind` (gold/silver/bronze/participant) is **UI-only**, never persisted.

---

## 9. Timestamp format per collection (do NOT normalize)

| Collection / field | Wire form |
| --- | --- |
| camping `startDate`/`endDate`, `createdAt`/`updatedAt`; registration `createdAt`/`updatedAt`/`guardianConsentAt`; `winnerRevealPolicy.*`; schedule day/program/config `*At`; checkIns `checkedInAt`; media `uploadedAt`; contentReports `createdAt`/`reviewedAt`; chat `createdAt`/`deletedAt`; poll `votes.votedAt`; feedback `submittedAt`/`updatedAt`; lodging/venueMap/song `*At`; user/child `*At` | Firestore **`Timestamp`** (serverTimestamp for `createdAt`/`updatedAt`; raw Date for explicit ones) |
| `poll.createdAt`, `poll.closesAt` | **client `Date()`** Timestamp; `closesAt` is explicit **`null`** when absent |
| `team.penalties[].createdAt`, `chordSheet.updatedAt`, `team.createdAt/updatedAt` (model init default) | raw `Date` → Timestamp |
| `ziyon_notifications.sentAt`/`createdAt` | **ISO-8601 string** `YYYY-MM-DDTHH:mm:ss.SSSZ` (also accepts Timestamp/Date on read) |
| `users.blockedUsers.blockedAt` | Timestamp **only** (no Date fallback on read) |

No field uses a Unix epoch number or an `HH:mm` string. Program times
are full datetime Timestamps; day calendar identity is encoded only in
the deterministic doc-ID `yyyy-MM-dd` substring.

---

## 10. Denormalization (must fan-out on profile edit)

On profile save the iOS app fans the new profile into denormalized
copies. Web/Android must do the same or data drifts:

| Target (query) | Match | Keys written |
| --- | --- | --- |
| `registrations` (CG, `userID==uid`) | `userID` | `displayName`,`church`,`photoURL`,`preferredLanguage`,`languages`,`age`,`ageGroup`,`gender`,`updatedAt` |
| parent `campings/{id}` | - | `updatedAt` bump |
| `teams` (CG, `members[].userID==uid`) | member | member `displayName`,`church`,`preferredLanguage`,`languages`,`age`,`ageGroup`,`gender`,`photoURL`; doc `members` rewrite + `updatedAt` |
| `checkIns` (CG, `userID==uid`) | `userID` | `displayName`,`church`,`preferredLanguage`,`photoURL`,`updatedAt` |
| `chat` (CG, `senderID==uid`) | `senderID` | `senderName`,`senderChurch`,`senderPreferredLanguage`,`senderGender`,`senderPhotoURL`,`updatedAt` (note `sender*` rename) |
| `announcements` (`authorID==uid`) | `authorID` | `authorName`,`authorPhotoURL`,`updatedAt` |
| `polls` (CG, `createdByID==uid`) | `createdByID` | `createdByName`,`updatedAt` |

(CG = collection-group query.) Onboarding does a narrower
`registrations`/`checkIns` snapshot sync.
