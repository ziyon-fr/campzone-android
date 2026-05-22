# RBAC, Roles & Firestore Security Rules (Canonical)

> Permissions are **server-enforced** by deployed Firestore Security
> Rules (`firestore-rbac.rules`). Client UI gates must **match** the
> rules - if the client allows an action the rules forbid, the write
> fails at runtime; if the client hides an action the rules allow, the
> feature is just unreachable. This document mirrors the deployed rules
> and the iOS `AppPermissionEvaluator` so web/Android gate identically.
>
> Source of truth: `Campzone/Core/Firebase/firestore-rbac.rules` +
> `Campzone/Core/Permissions/AppPermission.swift` (iOS repo).

---

## 1. Roles (`UserRole`)

Stored on `users/{uid}.role` as these **exact** strings:

`guest`, `user`, `adult`, `youth_director`, `pastor`, `game_master`,
`leader`, `photographer`, `admin`.

Legacy values `senior` / `youth` are read as `user` (never written).

- **Self-assignable** (a user may set their own role only within this
  set): `guest`, `user`, `adult`. Anything else must be granted by an
  authorized leader/admin (`validChurchRoleAssignment` /
  `isAdmin` update rules).
- **Leadership roles**: `youth_director`, `pastor`, `game_master`,
  `leader`, `photographer`, `admin`.
- Leadership (non-admin) permissions are **church-scoped**: they apply
  only to campings whose `organizerLevel` is
  `{ type: "church", value: <the user’s `church`> }` (case-insensitive
  compare). Admin is global/unrestricted.

### Church-scope rule (critical)

A non-admin leadership action on a camping is allowed **only when**
`camping.organizerLevel.type == "church"` **and**
`camping.organizerLevel.value` equals the acting user’s
`users/{uid}.church`. Regional/international/custom campings are
**admin-only** until a future staff-assignment model exists. Web/Android
must replicate this exact gate before showing management UI.

---

## 2. Permission → role matrix

`✓` = full/global, `C` = church-scoped (own-church campings only),
blank = denied. Admin has **every** permission globally.

| Permission | guest | user | adult | youth_director | pastor | game_master | leader | photographer | admin |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| View campings / announcements / songbook | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Register for campings |  | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Manage family registrations |  |  | ✓ |  |  |  |  |  | ✓ |
| Create/edit/cancel campings (global) |  |  |  |  |  |  |  |  | ✓ |
| Create own-church campings |  |  |  | C | C |  |  |  | ✓ |
| Edit own-church campings |  |  |  | C | C |  |  |  | ✓ |
| Cancel own-church campings |  |  |  | C | C |  |  |  | ✓ |
| Approve registrations |  |  |  | C |  |  | C |  | ✓ |
| Manage schedule / food menu |  |  |  | C | C |  | C |  | ✓ |
| Manage teams |  |  |  | C |  | C¹ | C |  | ✓ |
| Manage games |  |  |  | C | C | C¹ | C |  | ✓ |
| Assign points |  |  |  | C |  | C¹ | C |  | ✓ |
| Reveal winners |  |  |  |  |  | C¹ |  |  | ✓ |
| Manage album media |  |  |  |  |  |  |  | C | ✓ |
| Manage check-ins (own church) |  |  |  | C | C |  | C |  | ✓ |
| Manage transportation (own church) |  |  |  | C | C |  | C |  | ✓ |
| Award achievements |  |  |  | C | C | C¹ | C |  | ✓ |
| Revoke achievements |  |  |  |  |  |  |  |  | ✓ |
| View participant profiles |  |  |  | C | C | C¹ | C |  | ✓ |
| Create/edit announcements |  |  |  | ✓ | ✓ |  | ✓ |  | ✓ |
| Delete announcements |  |  |  |  |  |  |  |  | ✓ |
| Moderate content |  |  |  | ✓ | ✓ |  | ✓ |  | ✓ |
| Edit guidelines (own church) |  |  |  | C | C |  | C |  | ✓ |
| Assign own-church roles |  |  |  | C | C |  |  |  | ✓ |
| Assign leadership roles |  |  |  |  |  |  |  |  | ✓ |
| View admin tools |  |  |  |  |  |  |  |  | ✓ |

¹ `game_master` is **not** church-scoped by profile church in the iOS
evaluator the same way (it has no church requirement in some paths) - it
is granted these for campings it operates; treat as church-scoped for
parity and rely on the server rule as the final authority.

`canManagePolls` / `canModerateCampingChat` reuse the announcement-editor
gate (`canEditCamping` OR `editAnnouncements` scope). `canModerateTeamChat`
= `canEditCamping` OR `canManageTeams` OR `canManageGames`.

Assignable roles when granting: admin → any role; own-church role
assigners → only the self-assignable set (`guest`/`user`/`adult`), so a
non-admin can never escalate someone to leadership.

---

## 3. Security-rule digest (per collection)

Exact helper logic lives in `firestore-rbac.rules`. Summary of the
**deployed** access control (replicate the client gate to match):

- **Legacy collection-group** `{path=**}/programs/{id}`: world-readable
  (schedule widgets). `{path=**}/children/{id}`: `read` for
  `adult`/`admin` only (cross-guardian duplicate detection; needs the
  `(displayName, age)` composite index).
- **`users/{uid}`**: `read` self OR admin OR same-church
  youth_director/pastor. `create`/`update` self only within the
  self-profile field allowlist and self-assignable role set; admin may
  update anything; same-church youth_director/pastor may change `role`
  (to a self-assignable value) + `updatedAt` only. `delete` admin only.
  - `notificationTokens`, `notificationSettings`, `blockedUsers`: self
    only (RWCD).
  - `badges`: `read` self / content-moderator / camp achievement-awarder.
    `create`/`update` only by admin or camp achievement-awarder **and
    `request.auth.uid != uid`** (no self-award). `delete` admin only.
  - `children`: self **and** role `adult`/`admin`.
- **`ziyon_notifications`**: `read` if signed-in and the doc’s `topic`
  is `campzone_announcements`, the user’s own role topic, or (admin) any
  role topic. `create/update/delete: false` (backend-only).
- **`campings/{id}`**: `read` public (`true`). `create` by admin or
  own-church youth_director/pastor whose proposed `organizerLevel`
  matches their church. `update`: admin any; own-church editor **except**
  `winnerRevealPolicy`; canceller may change `registrationStatus`+
  `updatedAt`; winner-revealer may change `winnerRevealPolicy` only.
  `delete`: admin OR `resource.data.createdByUID == auth.uid` (the
  creator signature) OR own-church canceller.
  - `schedule/**`, `days/**`, `programs/**`: `read` public; write
    `canManageSchedule`.
  - `songs`: `read` public; write **admin only**.
  - `guidelines`: `read` public; write `canManageGuidelines`.
  - `lodging`: `read` signed-in; write `canManageTeams`.
  - `venueMap`: `read` signed-in; write `canManageTeams` OR
    `canManageSchedule` (inline pin creation from the program editor).
  - `feedback/{uid}`: `read` own doc OR `canViewParticipantProfiles`.
    `create` only if `feedbackId == auth.uid`, `userID == auth.uid`,
    `campingID == path`, `overallRating` int in 1..5. `update` own doc
    (userID unchanged). `delete` admin.
  - `teams`: `read` signed-in; write `canManageTeams`.
    - `teams/{id}/chat`: `read` team member OR `canModerateTeamChat`.
      `create` team member, `senderID==auth.uid`, `campingID==path`,
      `teamID==path`, `text` string ≤2000. `update` own non-deleted msg
      OR moderator. `delete` admin.
  - `games`: `read` signed-in; write `canManageGames`.
  - `activities`: `read` `canManageGames` OR `canRevealWinners` OR
    (approved participant AND `visibility=="immediate"`). `create`
    `canAssignPoints` AND `campingID==path` AND `createdBy==auth.uid`.
    `update: false` (immutable). `delete` `canManageGames`.
  - `registrations`: `read` if signed-in AND
    (`canViewParticipantProfiles` OR own (`uid`/`userID`) OR
    `guardianID==auth.uid` OR approved participant). `create` if
    `campingID==path` AND (self-registration OR adult-family
    registration). `update` `canApproveRegistrations` and only
    `registrationStatus`+`updatedAt`. `delete` admin.
  - `transportationBookings`: `read` manager OR own
    (`userID`/`guardianID`). `create` self/guardian, `campingID==path`,
    `paymentStatus=="unpaid"`, `boardingStatus=="not_boarded"`.
    `update` `canManageTransportation` and the immutable keys
    (`ticketToken`,`campingID`,`registrationID`,`participantID`,`userID`)
    unchanged. `delete` admin.
  - `checkIns`: `read` `canManageCheckIns` OR own (`userID==auth.uid`)
    OR a guardian whose sibling `registrations/{sameId}.guardianID ==
    auth.uid` (single `get()`). `create` `canManageCheckIns`,
    `checkedInBy==auth.uid`, `campingID==path`. `update`/`delete` admin.
  - `chat`: `read` camping-chat moderator OR approved participant.
    `create` approved participant, `senderID==auth.uid`,
    `campingID==path`, `text` string ≤2000. `update` own non-deleted msg
    OR moderator. `delete` admin.
  - `media`: `read` album-manager OR approved participant. `create`
    album-manager OR (approved participant AND `uploaderID==auth.uid`
    AND `albumSettings/default.allowedUploadRoles` contains the user’s
    role). `update`/`delete` album-manager OR uploader.
  - `albumSettings`: `read` signed-in; `write` `canManageAlbumMedia`.
  - `foodMenu`: `read` signed-in; write `canManageFoodMenu`
    (== schedule manager).
  - `polls`: `read` poll-manager OR approved participant. write
    `canManagePolls`.
    - `polls/{id}/votes/{voterId}`: `read` poll-manager OR own
      (`voterId==auth.uid`). `create`/`update` self, approved
      participant, **poll `isOpen==true`**. `delete` admin.
- **`contentReports`**: `create` signed-in `reporterID==auth.uid`.
  `read`/`update` content-moderator. `delete` admin.
- **`announcements`**: `read` public. `create`/`update`
  announcement-editor roles. `delete` admin.
- **`schedules`, `teams`** (legacy top-level): `read` public/signed-in;
  write **admin only**. Clients should **not** write these - use the
  camping-scoped subcollections.
- **`churches/**`**: `read` public; `write: false` (managed out-of-band).
- Everything else: `read, write: false` (default deny).

---

## 4. Implications for web/Android clients

1. **Never write a field a rule forbids.** E.g. a registrant cannot set
   `registrationStatus`/`paymentStatus` - those are settled by the
   approver UI or the backend Admin SDK. A team write must include
   `memberUserIDs`. A camp creator must stamp `createdByUID` to be able
   to delete later.
2. **Approved-participant gating**: chat/media/polls reads require an
   `approved` `registrations/{auth.uid}` doc in that camping. Build the
   UI so these surfaces are hidden until approved.
3. **Field allowlists on `users` self-update**: only the documented
   self-profile fields may change, and `role` only within
   `guest/user/adult`. Sending an extra/unknown key fails the update.
4. **Deterministic IDs are security-relevant**: `registrations/{id}`,
   `checkIns/{id}`, `feedback/{uid}`, poll `votes/{voterId}` doc IDs are
   asserted by rules. Use the exact ID conventions from
   `02-firestore-schema.md`.
5. **Cross-platform parity is mandatory.** If you add a feature, add the
   matching Security Rule (in the iOS repo’s `firestore-rbac.rules`,
   redeploy via `firebase deploy --only firestore:rules`) **and** the
   client gate, for **all three** apps. Rule changes are owner-deployed -
   coordinate; see `04-backend-api.md` → “Owner deploy actions”.
6. **Auth**: Firebase Authentication with **Apple** and **Google**
   providers only (same project). A user doc is created on first
   sign-in; onboarding completes the profile (`onboardingCompleted`).
   The web app uses Firebase JS SDK Auth; Android uses Firebase Auth +
   Credential Manager / Google + Sign in with Apple via OAuth provider.
