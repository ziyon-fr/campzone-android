# Data-Contract Rules - Cross-Platform Sync Gotchas (READ BEFORE WRITING)

> iOS is the shipped reference implementation. Web and Android write into
> the **same** Firestore documents iOS reads. These are the
> non-obvious rules that silently corrupt sync or trip Security Rules if
> you get them wrong. Treat this as a pre-write checklist. Full detail is
> in `02-firestore-schema.md`; this is the “what bites you” summary.

---

## 1. `FieldValue.delete()` vs `null` vs omit - they are NOT interchangeable

iOS uses three distinct “no value” encodings. Reproduce **each exactly**:

- **delete-when-nil**: write `FieldValue.delete()` so the key is
  **removed** from the doc. Fields: user/child `age`, `ageGroup`,
  `gender`, `photoURL`, `photoPublicID`; user `pendingDeletionAt`,
  `deletionRequestedBy`; child `guardianConsentAt`; camping
  `locationLatitude`, `locationLongitude`, `participantCapacity`,
  `logoURL`, `logoPublicID`, `registrationFeeCents`, `feeCurrency`;
  program `venuePointID`; team `photoURL`, `photoPublicID`; venueMap
  `imageURL`, `imagePublicID`; song `audio`.
- **explicit `null`** (`NSNull` / `null` literal): `poll.closesAt` when
  no close date; `users/{uid}/badges/*` `campingID` and `note` when
  absent. Writing these as “omitted” or “deleted” is **wrong**.
- **omit-when-nil**: just don’t include the key (e.g. attendee
  `gender`/`guardianID`/`transportation*`, announcement
  `authorPhotoURL`, chat `senderGender`/`senderPhotoURL`/`teamID`,
  most embedded-object optionals).
- **empty string `""`** (written, not omitted): announcement
  `notificationTargetRoleRawValue`, attachment `downloadURL`/
  `storagePath`, contentReport `note`, song audio `downloadURL`.

Web: `import { deleteField } from "firebase/firestore"` →
`deleteField()`. Android: `FieldValue.delete()`.

## 2. Timestamps are NOT uniform - do not normalize

Per `02` §9. Most `*At` are Firestore `Timestamp`
(`serverTimestamp()` for `createdAt`/`updatedAt`), **but**:
`poll.createdAt`/`closesAt` are a **client `Date`** (and `closesAt` is
explicit `null` when none); `team.penalties[].createdAt` and
`chordSheet.updatedAt` are a **raw client Date**;
`ziyon_notifications.sentAt`/`createdAt` are an **ISO-8601 string**
`YYYY-MM-DDTHH:mm:ss.SSSZ`; `users/{uid}/blockedUsers.blockedAt` is read
**Timestamp-only** (no Date fallback). Write the exact form iOS expects
or the iOS reader mis-parses (e.g. notifications all collapse to
“distant past”, sort wrong).

`createdAt` is written **only on first create**; `updatedAt` on **every**
write. Don’t overwrite `createdAt` on update.

## 3. Enum raw strings are case-sensitive - copy from `02` §8

Common traps: `provided_bus`/`own_car` (snake), `prefer_not_to_say`,
`youth_director`/`game_master` (snake), but `TransportationMode`
(`ownCar`,`onFoot`) and `CampingPaymentOption` (`cardOneTime`) are
**camelCase**, and `ContentReportTarget.chatMessage` is camelCase
(not `chat_message`, unlike `AppNotificationKind.chat_message`).
`participantKind` `self` (← `selfParticipant`). Unknown enum strings
either default (most) or **throw and drop the doc** (`contentReports`
aborts the whole admin list). RBAC literally compares `"unpaid"`,
`"not_boarded"`, `"immediate"`, `"approved"`, `"church"` - a mismatch =
denied write or invisible data.

## 4. Deterministic document IDs are load-bearing (RBAC asserts them)

- `users/{uid}` doc id = Auth uid.
- `registrations/{attendeeId}`: attendee id; for **self** == the user
  uid; for a child == child id. Both `userID` and a duplicate `uid`
  field are written.
- `checkIns/{attendeeId}` == the attendee/registration doc id (the
  guardian read rule joins them by equal id).
- `feedback/{uid}` == submitting uid (rule asserts
  `feedbackId == auth.uid`).
- poll `votes/{voterId}` == voter uid.
- `users/{uid}/notificationTokens/{id}` id = lowercase hex SHA-256 of
  the FCM token; `users/{uid}/blockedUsers/{id}` id = blocked uid;
  `users/{uid}/badges/{id}` id = achievement id.
- Single-doc collections: `schedule/config`, `venueMap/config`,
  `albumSettings/default`, `notificationSettings/default`.
- Schedule day id = `"<campingID>-day-<yyyy-MM-dd>"`; food menu id =
  `"<yyyy-MM-dd>-<meal>"`; generated meal program id =
  `"menu-<menu id>"`. Date key: gregorian calendar, `en_US_POSIX`
  locale, local time zone. **Program `campDayID` must be recomputed
  from `startDate` on every write** (never trust inbound).

Using a Firestore auto-id where a deterministic id is required = a
duplicate/ghost record and (often) a denied read.

## 5. Don’t write what a rule forbids (it fails at runtime)

- Registrants can’t set `registrationStatus`/`paymentStatus` - the
  approver UI sets status (`canApproveRegistrations`, only
  `registrationStatus`+`updatedAt`); the **backend** sets payment.
- `users` self-update only the documented self-profile fields, `role`
  only within `user/adult`; extra/unknown keys fail the write.
- `activities` are immutable (`update:false`) and create requires
  `campingID==path` + `createdBy==auth.uid`.
- camping `winnerRevealPolicy` is writable only via the reveal gate;
  own-church editors must NOT include it in a normal camp edit.
- camp `delete` needs `createdByUID==auth.uid` (or admin/own-church
  canceller) - **stamp `createdByUID` on create** or the creator can’t
  delete later.
- Team and staff-role writes must include a correct `memberUserIDs` (RBAC
  private-chat membership reads it).
- songs are **admin-only** writes (camping-scoped subcollection); legacy
  top-level `teams`/`schedules` are admin-only - never write them from a
  client; use `campings/{id}/teams` / `campings/{id}/schedule`.

## 6. The camping doc has no `attendees`; bypass-Codable write path

`campings/{id}` is written via a hand-built payload, not the model
encoder. It never contains `attendees` (use the `registrations`
subcollection), and `guidelines`/`winnerRevealPolicy`/`isFeatured` are
written only by their dedicated paths. `priceItems`/`agePrices`/
`transportationOptions` are **always** written (empty array if none);
`organizerLevel` is a `{type,value}` **map** (not a string);
`location` is a flat **string** (not a `CampingLocation` map).

## 7. Money is always integer cents (minor units)

`registrationFeeCents`, `priceItems[].amountCents`,
`agePrices[].amountCents`, `transportationOptions[].feeCents`,
`/payments/intent` `amount`. Currency codes: camp/options
`priceItems`/`options` store **uppercase** (`EUR`); camping
`feeCurrency` and the payments API use **lowercase** (`eur`). Never
store a float amount.

## 8. Denormalization fan-out on profile edit (or data drifts)

Editing a profile must fan the new values, including `allergies`, into `registrations`,
`teams.members[]`, `staffRoles.members[]`, `checkIns`, `chat` (as `sender*`), `announcements`
(as `author*`), `polls` (as `createdBy*`) via collection-group queries -
see `02` §10. Skipping this leaves stale names/photos across the app.
The Menu↔Program sync (`02` §4.5) is likewise application-level: write
**both** docs.

## 9. Two notification stores - do both

Notification token + settings go **both** to client-direct Firestore
(`users/{uid}/notificationTokens|notificationSettings`, RBAC self-only)
**and** the backend API (`/notifications/devices`,
`/notifications/settings`) which performs the FCM topic
subscription. Skipping the API = no pushes. The in-app feed
(`ziyon_notifications`) is **backend-written, client read-only**; filter
by `appID == "campzone"` + visible topics. Scoped feed queries must also
carry the authorization metadata predicates: `campingID`, `role`, and/or
`teamID`/`staffRoleID` as appropriate. Firestore Rules are not post-query
filters.

## 10. Tolerant vs brittle reads

Most list reads silently drop a malformed doc (`compactMap`/`try?`) - a
single bad write hides one record. **`contentReports` is brittle**: a
missing required field or unknown enum throws and the whole admin list
fails. Validate writes to `contentReports` especially strictly.

---

### Pre-write checklist

1. Right collection path + **deterministic doc id**? (§4)
2. Every required field present, exact enum raw string? (§3)
3. Optionals encoded with the right no-value form? (§1)
4. Timestamps in the exact per-field form? (§2)
5. Money in integer cents, currency case correct? (§7)
6. Not writing a rule-forbidden field; writing rule-required ones
   (`memberUserIDs`, `createdByUID`, `campingID==path`, `staffRoleID==path`,
   …)? (§5)
7. Denormalized copies + paired docs fanned out? (§8, §4.5)
8. Notifications: Firestore **and** backend API? (§9)
