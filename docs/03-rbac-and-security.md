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

`guest`, `user`, `youth_director`, `pastor`, `game_master`, `leader`,
`photographer`, `adult`, `admin`.

Legacy values `senior` / `youth` are read as `user` (never written).

- **Self-assignable** (a user may set their own role only within this
  set): `guest`, `user`, `adult`. Anything else must be granted by an
  authorized leader/admin (`validChurchRoleAssignment` /
  `isAdmin` update rules).
- **Leadership roles**: `youth_director`, `pastor`, `game_master`,
  `leader`, `photographer`, `admin`.
- Leadership (non-admin) permissions are **church-scoped**: they apply
  only to campings whose `organizerLevel` is
  `{ type: "church", value: <the user’s`church`> }` (case-insensitive
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

The client enum **must match iOS `AppPermission` exactly**. Android uses
PascalCase names (`ViewPublishedCampings`) for the Swift cases
(`viewPublishedCampings`), but the case set and order are identical.

`✓` = raw/global role permission. `C` = effective camping helper is
church-scoped to own-church campings. Blank = denied. Admin has **every**
permission globally. Several user-created camping helpers also allow the
creator even when the raw role permission is absent; see helper notes
below the table.

| AppPermission | guest | user | adult | youth_director | pastor | game_master | leader | photographer | admin |
| --- |:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| View published campings | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Register for campings |  | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Approve registrations |  |  |  | C |  |  | C |  | ✓ |
| Create campings |  |  |  |  |  |  |  |  | ✓ |
| Edit campings |  |  |  |  |  |  |  |  | ✓ |
| Cancel campings |  |  |  |  |  |  |  |  | ✓ |
| Create own-church campings |  |  |  | C | C |  |  |  | ✓ |
| Edit own-church campings |  |  |  | C | C |  |  |  | ✓ |
| Cancel own-church campings |  |  |  | C | C |  |  |  | ✓ |
| View announcements | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Create announcements |  |  |  | ✓¹ | ✓¹ |  | ✓¹ |  | ✓ |
| Edit announcements |  |  |  | ✓¹ | ✓¹ |  | ✓¹ |  | ✓ |
| Delete announcements |  |  |  |  |  |  |  |  | ✓ |
| View songbook | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Manage songbook |  |  |  |  |  |  |  |  | ✓ |
| Manage schedule |  |  |  | C | C |  | C |  | ✓ |
| Manage teams |  |  |  | C |  | C | C |  | ✓ |
| Manage games |  |  |  | C | C | C | C |  | ✓ |
| Assign points |  |  |  | C |  | C | C |  | ✓ |
| Reveal winners |  |  |  |  |  | C |  |  | ✓ |
| Manage album media |  |  |  |  |  |  |  | C | ✓ |
| Manage transportation |  |  |  |  |  |  |  |  | ✓ |
| Manage own-church transportation |  |  |  | C | C |  | C |  | ✓ |
| Award achievements |  |  |  | C | C | C | C |  | ✓ |
| Revoke achievements |  |  |  |  |  |  |  |  | ✓ |
| Manage check-ins |  |  |  |  |  |  |  |  | ✓ |
| Manage own-church check-ins |  |  |  | C | C |  | C |  | ✓ |
| View participant profiles |  |  |  | C | C | C | C |  | ✓ |
| Assign leadership roles |  |  |  |  |  |  |  |  | ✓ |
| Assign own-church roles |  |  |  | C | C |  |  |  | ✓ |
| View admin tools |  |  |  |  |  |  |  |  | ✓ |
| Manage family registrations |  | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Edit guidelines |  |  |  |  |  |  |  |  | ✓ |
| Edit own-church guidelines |  |  |  | C | C |  | C |  | ✓ |

¹ Raw announcement create/edit permissions are role-level. Camping-scoped
announcement management uses `canManageAnnouncements(for:)`, which scopes
non-admin leadership to own-church campings. Camping ownership alone does
not grant announcement publishing access.

Derived helpers are **not** enum cases:

- `canManageFoodMenu` follows `canManageSchedule`.
- `canManagePolls` / `canModerateCampingChat` =
  `canEditCamping` OR scoped `editAnnouncements`.
- `canModerateTeamChat` = `canEditCamping` OR `canManageTeams` OR
  `canManageGames`.
- `canSaveCamping` mirrors the iOS editor: creating requires
  `canCreateCamping` for the proposed organizer; editing requires
  `canEditCamping` for the current camp and, unless the user is the camp
  creator, `canCreateCamping` for the proposed organizer.
- `canEditCamping`, `canManageSchedule`, `canManageTeams`,
  `canApproveRegistrations`, and
  `canViewParticipantProfiles` allow `createdByUID == auth.uid`, matching
  iOS creator-owned camping behavior.

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
  - `children`: self **and** any onboarded role (every role except
    `guest`). The privileged collection-group `read` above (cross-guardian
    duplicate detection) stays `adult`/`admin`; for other roles that read is
    denied and the client skips cross-guardian duplicate detection gracefully.
- **`ziyon_notifications`**: `read` if signed-in and the doc’s `topic`
  is `campzone_announcements`, the user’s own role topic, or (admin) any
  role topic. `create/update/delete: false` (backend-only).
- **`campings/{id}`**: `read` public (`true`). `create` by admin or
  own-church youth_director/pastor whose proposed `organizerLevel`
  matches their church. `update`: admin any; own-church editor **except**
  `winnerRevealPolicy`; canceller may change `registrationStatus`+
  `updatedAt`; winner-revealer may change `winnerRevealPolicy` only.
  `delete`: admin OR `resource.data.createdByUID == auth.uid` (the
  creator signature) OR own-church canceller. Android camping detail/edit
  views use the same iOS gates: the edit affordance is
  `canEditCamping(current)`, while Save also validates the proposed
  organizer through `canSaveCamping`.
  - `schedule/**`, `days/**`, `programs/**`: `read` public; write
    `canManageSchedule`.
  - `songs`: `read` public; write **admin only**.
  - `guidelines`: `read` public; write `canEditGuidelines`.
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
    unchanged. `delete` admin. Manager updates cover the round-trip/scan
    fields added in `02` §7.2 (`scanHistory` append via `arrayUnion`,
    `boardingStatus`/`boardedBy/At`/`arrivedBy/At` mirrors, `paymentStatus`,
    `isActive`/`canceledBy/At`/`cancelReason`); the deployed rules already
    accept these (iOS writes them) since they touch none of the immutable
    keys.
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
