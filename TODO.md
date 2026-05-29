# Campzone Android - Build TODO

> Derived from `docs/` (read 01–09). Build phases mirror `08-feature-parity.md`.
> Cross-cutting rules (full CRUD, RBAC gates, data contract) apply to every task.
> Done bar: build + unit tests + lint clean; every write path passes the `07` pre-write checklist.

---

## Phase A - Foundation

### A1. Project & Gradle Setup ✅

- [x] Finalize application ID - `fr.ziyon.campzone` (already set, matches Firebase Android app)
- [x] Add Gradle version catalog (`gradle/libs.versions.toml`) entries:
  - Firebase BoM 33.7.0 + `firebase-auth-ktx`, `firebase-firestore-ktx`, `firebase-messaging-ktx`, `firebase-analytics-ktx`
  - Navigation-Compose 2.8.5
  - Hilt 2.59.2 (KSP-based, AGP 9.x compatible)
  - kotlinx-coroutines 1.9.0 + coroutines-play-services
  - Coil 2.7.0
  - Stripe Android SDK 21.3.0
  - KSP 2.2.10-2.0.2
- [x] Apply Google Services, KSP, Hilt Gradle plugins in both build files
- [x] `google-services.json` already present in `app/`
- [x] `compileSdk 36 / minSdk 24 / targetSdk 36` confirmed
- [x] Backend base URL wired as `BuildConfig.BACKEND_BASE_URL` in debug + release build types
- [x] `android.disallowKotlinSourceSets=false` added to gradle.properties (KSP/AGP 9.x compatibility shim)

### A2. Firebase & Firestore Init ✅

- [x] Initialize Firebase in `CampzoneApp` (Application class); Hilt `@HiltAndroidApp`
- [x] Enable Firestore **disk persistence** (`setLocalCacheSettings(persistentLocalCache(...))`) for offline parity - songbook/schedule/camp program prioritized
- [x] Configure Firebase Auth providers: **Google** (Credential Manager / Google Identity) + **Apple** (`OAuthProvider("apple.com")` web flow)
- [x] Register SHA-1/SHA-256 signing certs for Google Sign-In in the Firebase console

### A3. Design System (`core/designsystem/`) ✅

- [x] `CzColors` object - all semantic light/dark token pairs from `06-design-tokens.md` (ember palette, pine greens, cream neutrals)
- [x] `CzSpacing` object - xs=4·sm=8·md=12·base=16·lg=20·xl=24·xxl=32·xxxl=48 (all dp)
- [x] `CzRadius` object - xs=4·sm=8·md=12·lg=16·xl=20·xxl=24·full=999 (all dp)
- [x] Rounded `Typography` (Nunito or Varela Round) matching 10 iOS type scales in `sp`
- [x] `CampzoneTheme` composable wiring Material3 `ColorScheme`, typography, shapes; swap light/dark via `isSystemInDarkTheme()`
- [x] Core reusable components (mirror iOS `Cz*` set): `CzButton`, `CzCard`, `CzTextField`, `CzBadge`, `CzEmptyState`, `CzErrorState`, `CzLoadingView`, `CzAvatar`, `CzSectionHeader`
- [x] Touch targets ≥48dp, content descriptions on all interactive elements, font scale via `sp`

### A4. Navigation Shell (`core/navigation/`) ✅

- [x] `AppRoute` sealed hierarchy (typed routes, no raw strings) - 4 tabs: Home, Campings, Announcements, Profile
- [x] Single-Activity `MainActivity` with Compose `NavHost`
- [x] Intent filter for `campzone://` deep-link scheme; park cold-start links until auth/nav is ready (see `05-deep-linking.md`)
- [x] Bottom navigation with 4 tabs; push feature screens via typed routes

### A5. Permission Evaluator (`core/permissions/`) ✅

- [x] `AppPermission` sealed class / evaluator mirroring the `03-rbac-and-security.md` permission matrix exactly
- [x] Church-scope rule: non-admin leadership gates apply only when `camping.organizerLevel.type == "church"` AND `value == user.church` (case-insensitive)
- [x] Unit-test every permission against the matrix in `03` (all 9 roles × all permissions)

### A6. Auth & Session (`ui/auth/`) ✅

- [x] Sign-in screen: Google + Apple buttons ( follow iOS design)
- [x] On first sign-in: create `users/{uid}` doc with `role: "guest"`, `createdAt: serverTimestamp()`, `onboardingCompleted: false` - **merge: true**; do not overwrite existing `email`/`displayName`/`photoURL`
- [x] Session state: `StateFlow<AuthState>` (signed-out / onboarding-incomplete / signed-in)
- [x] Sign-out clears local session; navigates to auth screen

### A7. Onboarding (`ui/onboarding/`) ✅

- [x] Collect: age (derive `ageGroup`), church, preferred language, gender
- [x] Write `users/{uid}` with `onboardingCompleted: true`, `languages: [preferredLanguage]` (single element) - **merge: true**
- [x] Apply `07` pre-write checklist: `age`/`ageGroup`/`gender` are **delete-when-nil** if blank; no extra keys
- [x] Gate: show onboarding when `onboardingCompleted == false`; request FCM permission **after** onboarding completes (not at launch)

### A8. Profile View/Edit + Account Deletion (`ui/profile/`) ✅

- [x] Profile screen: display all `users/{uid}` fields
- [x] Edit screen: update self-profile field allowlist only (`displayName`, `age`, `ageGroup`, `gender`, `church`, `skills`, `profession`, `education`, `pathfinderRank`, `phone`, `preferredLanguage`, `languages`, `role` ∈ {guest/user/adult} only)
- [x] **Denormalization fan-out on save** (`02` §10): update `registrations` (CG `userID==uid`), `teams.members[]` (CG), `checkIns` (CG `userID==uid`), `chat` (CG `senderID==uid`), `announcements` (`authorID==uid`), `polls` (CG `createdByID==uid`)
- [x] Profile photo upload: call `POST /cloudinary/sign` → upload multipart → persist `photoURL` + `photoPublicID` (delete-when-nil on clear)
- [x] Account deletion: set `pendingDeletionAt: serverTimestamp()` + `deletionRequestedBy: uid`; cancel = `FieldValue.delete()` on both fields; purge is server-side (30-day grace)

### A9. Family Participants / Children (`ui/family/`) ✅

- [x] CRUD for `users/{uid}/children/{childId}` - role `adult`/`admin` gate
- [x] Doc ID: client UUID (stable, mirrored in `id` field)
- [x] Required fields: `id`, `guardianID`, `displayName`, `age`, `gender`, `church`, `preferredLanguage`, `emergencyContactName`, `emergencyContactPhone`
- [x] `ageGroup` derived from `age`; `guardianConsentAt` delete-when-nil
- [x] `photoURL`/`photoPublicID` delete-when-nil; child photo upload via Cloudinary
- [x] Hard delete on remove

### A10. Data Models (`data/model/`) ✅

- [x] Kotlin `data class`es for every entity in `02-firestore-schema.md` - exact wire field names, enum raw strings, nullability
- [x] Enum classes with explicit raw string values (copy **exactly** from `02` §8; case-sensitive)
- [x] **No POJO auto-mapping** (`toObject<T>()` forbidden); all (de)serialization via `Map<String, Any?>` in the service layer
- [x] Unit-test: serialize → deserialize round-trip against Firestore fixtures for every model

---

## Phase B - Core Content

### B1. Campings List & Detail (`ui/campings/`) ✅

- [x] List screen: read `campings` collection, ordered by `startDate` desc; group by year/month
- [x] Filter: by church, age group, language; search by title
- [x] Detail screen: display all camping fields; `registrationStatus` badge; capacity/waitlist indicator
- [x] Camp guidelines tab: render `campings/{id}/guidelines` markdown
- [x] `UiState` sealed: Loading / Loaded / Empty / Error

### B2. Admin: Create / Edit / Cancel Camping (`ui/campings/admin/`) ✅

- [x] Gate: `canCreateCamping` / `canEditCamping` / `canCancelCamping` from permission evaluator
- [x] Write camping via hand-built `Map<String, Any?>` (never auto-encode) - exact fields from `02` §3
- [x] Required: `title`, `description`, `startDate`, `endDate`, `organizerLevel ({type,value})`, `location`, `registrationStatus`
- [x] Optional delete-when-nil: `locationLatitude`, `locationLongitude`, `participantCapacity`, `logoURL`, `logoPublicID`, `registrationFeeCents`, `feeCurrency`
- [x] Always write (may be empty `[]`): `priceItems`, `agePrices`, `transportationOptions`
- [x] Stamp `createdByUID` + `createdByName` on create only (authorizes delete)
- [x] Logo upload: `POST /cloudinary/sign` → multipart → stable public_id `campzone/campings/{campingID}`
- [x] Cancel: write only `{ registrationStatus: "cancelled", updatedAt: serverTimestamp() }`
- [x] Delete: only if `canDeleteCamping` or `createdByUID == auth.uid`
- [x] Do NOT write `guidelines` or `winnerRevealPolicy` in the normal edit path
- [x] iOS parity: location search picker (Geocoder + nearby + recents), registration status all 3 states with color dots, timing amber dividers, transport 2-line cap, `CampingsScreen` admin card + StatusBadge + capsule capacity bar

### B3. Registration Flow (`ui/campings/register/`) ✅

- [x] Self-registration: doc `registrations/{uid}` with `userID==uid`, `uid==uid`
- [x] Child registration: doc `registrations/{child.id}` with `guardianID`, `participantKind: "child"`
- [x] Transportation choice: `transportationChoice` (`own_car` / `provided_bus`); if `provided_bus` → create `transportationBookings/{participant.id}-bus`
- [x] Set `registrationStatus: "pending"` on create; **never** write `paymentStatus` (backend settles)
- [x] Required fields per `02` §3.6; `gender` omit-when-nil
- [x] Dispatch `POST /notifications/dispatch/registration` after submit
- [x] Waitlist: show when `participantCapacity` reached and status would be `waitlisted`
- [x] Paid camp: initiate Stripe payment flow (see Phase D)

### B4. Registration Review (Admin/Leadership) (`ui/campings/registrations/`) ✅

- [x] Gate: `canApproveRegistrations`; list all `registrations` subcollection
- [x] Approve/Reject: write only `{ registrationStatus, updatedAt }` (RBAC enforced)
- [x] Delete attendee: hard-delete registration doc → cascade: delete `checkIns/{attendeeId}`, `transportationBookings` where `participantID==attendeeId`, remove from team `members[]`
- [x] Attendee list visible only to registered+approved participants or leadership

### B5. Schedule (`ui/schedule/`) ✅

- [x] Read `campings/{id}/schedule/config` + days + programs; merge-sorted by `startDate`
- [x] Display: grouped by day (`CampDay`); program cards with type icon, time, location
- [x] Admin editor: create/edit/delete programs and days
  - Program save: upsert `schedule/config`, **delete** old day doc if day changed, upsert day, upsert program; prune empty untitled days
  - **Always recompute `campDayID`** from `startDate` - never trust inbound value
  - Day doc ID: `"<campingID>-day-<yyyy-MM-dd>"` (gregorian, en_US_POSIX, local TZ)
  - `venuePointID` delete-when-empty
- [x] Reminder timing: write `schedule/config.reminderTiming` (`ScheduleReminderTiming` raw); call `POST /notifications/reminders`
- [x] Gate: `canManageSchedule` for writes

### B6. Food Menu + Menu↔Program Sync (`ui/schedule/food/`) ✅

- [x] CRUD for `campings/{id}/foodMenu/{entryId}` - doc ID: `"<yyyy-MM-dd>-<meal>"` (gregorian, en_US_POSIX, local TZ)
- [x] Gate: `canManageFoodMenu` (== schedule manager)
- [x] **Menu↔Program two-way sync** (`02` §4.5): on every save, write **both** the food menu doc and the generated program (`"menu-<menuId>"`)
  - Entry→Program: `title`/`type`/`description` always regenerated from menu; preserve existing `startDate`/`endDate`/`location` if program already exists
  - Default meal times: breakfast 08:00 (45m), snack 10:30 (30m), lunch 12:30 (60m), dinner 18:30 (60m); default location `"Dining hall"`
  - Description format: each dish on `"- <dish>"` line; notes on blank line then `"Notes: <notes>"`

### B7. Announcements (`ui/announcements/`) ✅

- [x] List screen: read `announcements` ordered `createdAt` desc, limit 100
- [x] Detail screen: render markdown `body`; show attachments (image/PDF)
- [x] Admin composer: create/edit - gate `canEditAnnouncements`; delete gate `admin` only
- [x] Image/PDF attachment upload: `POST /cloudinary/sign` → upload; write `storagePath` (Cloudinary public_id) + `downloadURL` (secure_url); empty string `""` when nil (not omitted)
- [x] `notificationTargetRoleRawValue`: write as `""` when none (not omitted)
- [x] After save: `POST /notifications/dispatch/announcement`
- [x] `authorPhotoURL` omit-when-nil

### B8. Songbook (`ui/songbook/`) ✅

- [x] List screen: read `campings/{id}/songs`, ordered by `orderIndex`; highlight `isPinnedTheme`
- [x] Detail screen: lyrics + chords render (ChordPro-ish); audio player; YouTube link; PDF link
- [x] Favorites: `arrayUnion`/`arrayRemove` on `favoriteUserIDs`
- [x] Admin writes only: `canManageSongs` (admin role only per `03`); create/edit/delete songs
- [x] Reorder: update `orderIndex` on drag
- [x] Pin theme: set `isPinnedTheme: true` on selected song, batch-clear others
- [x] Audio upload: `POST /cloudinary/sign` with `resourceType: "video"` → upload → write `audio` + `audioFiles[]`; `audio` delete-when-empty
- [x] Chord sheet: respect lossy `originalKey` decode (only specific keys map back; others → C major)

### B9. Camp Guidelines (`ui/campings/guidelines/`) ✅

- [x] Display: render markdown from `campings/{id}/guidelines`
- [x] Edit: write only via guidelines update path (`updateData(["guidelines": ...])`); gate `canManageGuidelines`

---

## Phase C - Engagement

### C1. Teams (`ui/teams/`) ✅

- [x] List + ranking: read `campings/{id}/teams`; compute `totalScore = points + Σmembers[].personalScore − Σpenalties[].points` on read (never stored)
- [x] Detail: members list, captain/vice-captain roles, scores, penalties
- [x] Admin: create/edit/delete teams - gate `canManageTeams`
- [x] Every team write **rewrites full doc** + derives `memberUserIDs = members[].userID` (RBAC-critical for team chat)
- [x] `photoURL`/`photoPublicID` delete-when-empty; upload via Cloudinary
- [x] Captain/vice-captain: at most one each (client-enforced via `normalizeCaptaincy()`); per-member `role` field
- [ ] After team mutation: `POST /notifications/dispatch/team` (deferred to notification phase)
- [ ] Auto-balance members (deferred)

### C2. Games & Points (`ui/games/`) ✅

- [x] CRUD for `campings/{id}/games/{gameId}` - gate `canManageGames`
- [x] Game `updatedAt` is client `Date()` (not serverTimestamp)
- [x] Award points: write `activities/{activityId}` first, then mutate team doc
  - Activity is immutable (`update: false`); `createdBy == auth.uid`; `campingID == path` (RBAC checked)
  - Negative team award → append positive-magnitude `penalties[]` entry (never decrement `points`)
  - User award → add delta to `members[i].personalScore`; rewrite full team doc + `memberUserIDs`
- [x] `activities` list: gate `canManageGames` or `canRevealWinners` or (approved participant + `visibility=="immediate"`)

### C3. Winner Reveal (`ui/games/reveal/`) ✅

- [x] Gate: `canRevealWinners`
- [x] Write only `winnerRevealPolicy` via dedicated update path - forbidden in normal camp edit
- [x] `winnerRevealPolicy.isRevealed` required if map is present

### C4. Chat (`ui/chat/`) ✅

- [x] Camping-wide chat: `campings/{id}/chat` - read gate: approved participant or moderator
- [x] Team chat: `campings/{id}/teams/{teamId}/chat` - read gate: team member or `canModerateTeamChat`
- [x] Send: full `set` (no merge); `senderID == auth.uid`; `campingID == path`; text cap 500 chars (server cap 2000)
- [x] `teamID` written only in team chat
- [x] Soft delete: write `{ isDeleted: true, deletedByID, deletedAt: serverTimestamp() }`; display `"Message removed"`; keep doc
- [x] Pin: `updateData({ pinned: true })`; gate moderator
- [x] Report: create `contentReports` doc (see C8)
- [x] Block user: write `users/{uid}/blockedUsers/{blockedUid}` (`blockedAt` Timestamp only - no Date fallback on read)
- [x] After send: `POST /notifications/dispatch/chat` — either/or with the mention dispatch
- [x] iOS parity extras: @mentions (picker, highlight, `@everyone`; `mentions`+`mentionedUserIDs` wire), image attachments (Cloudinary + fullscreen), voice notes (MediaRecorder → Cloudinary `video` → player), inline edit (`editedAt`, 15-min window). Schema extended beyond `02` §6.1 to match iOS (iOS authoritative)

### C5. Polls (`ui/polls/`) ✅

- [x] List/create: gate `canManagePolls`; participants see list when approved
- [x] Vote: transactional - decrement old option `voteCount`, increment new, write `votes/{auth.uid}`, update poll `options`
- [x] `poll.createdAt` / `closesAt` are **client `Date()`** (not serverTimestamp); `closesAt` written as explicit Firestore `null` when none
- [x] `votes/{voterId}` doc ID == voter uid (one per voter; re-vote overwrites)
- [x] After create/close/reopen: `POST /notifications/dispatch/poll`
- [x] iOS parity: list (Live/Closed sections, PollCard), detail (vote rows, results bars, change-vote, admin close/reopen/delete), editor (2–8 unique options, settings, auto-close date/time). Single `PollViewModel` shared across list/detail/editor via the polls back-stack entry

### C6. QR Check-In (`ui/checkin/`) ✅

- [x] Scanner: decode QR payload `campzone://checkin?v=1&c=<campingID>&a=<attendeeID>&u=<userID>&iat=<unixSeconds>`
- [x] Write `checkIns/{attendeeId}` (doc ID == attendeeId); required: `campingID`, `attendeeID`, `userID`, `displayName`, `method`, `checkedInBy == auth.uid`; gate `canManageCheckIns`
- [x] `checkedInAt: serverTimestamp()`; gender/ageGroup/photoURL omit-when-nil
- [x] Manual check-in fallback: search registrations list
- [x] Guardian can read their child's single check-in doc (list denied)

### C7. Badges / Achievements (`ui/profile/badges/`) ✅

- [x] Read-only display of `users/{uid}/badges/{achievementId}`
- [x] Filter by `AchievementCatalog` in-code (shipped iOS catalog; unknown ids filtered out)
- [x] Catalog rarity/awardKind embedded in app - not in Firestore
- [x] `campingID` and `note` are explicit Firestore `null` when absent (not omitted)
- [x] Manual award: gate `canAwardAchievements`; write to `users/{targetUid}/badges/{achievementId}` (RBAC asserts `request.auth.uid != uid`)

### C8. Album Media (`ui/media/`)

- [x] Read gate: approved participant or album-manager
- [x] Upload: check `albumSettings/default.allowedUploadRoles` contains user's role; `POST /cloudinary/sign` → upload image/video; write `media/{mediaId}` (full set)
- [x] Admin delete/edit: gate `canManageAlbumMedia` or uploader
- [x] Settings: `albumSettings/default.allowedUploadRoles` sorted role raws, manager-only writes
- [x] Load with Coil from Cloudinary URLs; thumbnail via `thumbnailURL`

### C9. Content Moderation (`ui/admin/moderation/`)

- [x] Report: create `contentReports/{uuid}` (full set, no merge); all required fields must be present - **brittle reader** (admin list aborts on missing field or unknown enum)
- [x] `target` enum: `announcement`/`camping`/`chatMessage` (**camelCase**)
- [x] Admin queue: read + update status (`dismissed`/`resolved`); gate `canModerateContent`

### C10. Notifications (`core/notifications/`) ✅

- [x] `FirebaseMessagingService` (`CampzoneMessagingService`): on token refresh, write to **both**:
  1. Firestore `users/{uid}/notificationTokens/{sha256hex}` - doc ID = lowercase hex SHA-256 of the raw token; `platform: "android"`
  2. `POST /notifications/devices` with `appID: "campzone"`, `platform: "android"` + user role + locale (`NotificationDeviceRegistrar`; also registered once per user on sign-in via `NotificationBootstrapViewModel`)
- [x] Notification settings screen: read/write `users/{uid}/notificationSettings/default` **and** call `POST /notifications/settings` (both required - API call drives FCM topic subscription)
- [x] `subscribedRoleRawValues` (not `subscribedRoles`) stored in Firestore; trimmed, deduped, sorted (reused existing `NotificationPrefsPayload`; API sends both keys for backend tolerance)
- [x] In-app notification feed: read `ziyon_notifications` filtered by `appID == "campzone"` + visible topics; sorted `sentAt` desc; `sentAt` is ISO-8601 string (also accepts Timestamp/Date) — Home bell opens the feed; rows deep-link
- [x] Deep link handler in `MainActivity`: parse FCM data payload → `campzone://` route per `05-deep-linking.md`; park until auth/nav ready (pre-existing `IntentDeepLinks`/`DeepLinkInbox`; FCM tap PendingIntent forwards the data map)
- [x] Notification channels keyed by type: `announcement`, `chat_message`, `poll`, `schedule_reminder`, `team_update`, `registration`, `general`
- [x] iOS parity: full notification settings (master + 5 categories + role audiences + camping/team channel pickers); feed rows with audience/kind, `concerns()` role + `@mention` scoping; topics/`visibleTopics` ported verbatim

### C11. Analytics

- [x] Firebase Analytics; mirror iOS event set: `viewCamping`, `registerForCamping`, `cancelCamping`, `viewSchedule`, `viewSongbook`, `viewTeams`, `playSong`, `favoriteSong`, `searchCampings`, `signIn`, `signOut`

---

## Phase D - Operations & Growth

### D1. Transportation Tickets (`ui/transportation/`)

- [ ] Read bookings: merge `userID==uid` + `guardianID==uid` queries
- [ ] RBAC on create: `paymentStatus == "unpaid"`, `boardingStatus == "not_boarded"` literals
- [ ] QR payload: `campzone://transport?v=1&c=<campingID>&b=<bookingID>&r=<registrationID>&p=<participantID>&t=<ticketToken>`
- [ ] Admin scanner: decode QR → validate `ticketToken`; update `boardingStatus: "boarded"` + `boardedBy`/`boardedAt`; gate `canManageTransportation`
- [ ] `validFrom`/`validUntil`: raw Date → Timestamp (not serverTimestamp)

### D2. Stripe Payments (`ui/payments/`)

- [ ] Call `POST /payments/intent` with Firebase ID token; receive `paymentIntentClientSecret` + `ephemeralKeySecret` + `customerId` + `publishableKey`
- [ ] Present Stripe Android **PaymentSheet** with the returned params
- [ ] On PaymentSheet success → call `POST /payments/confirm`; backend auto-approves paid camps (`registrationStatus: "approved"`, `paymentStatus: "paid"`)
- [ ] Kinds: `registration` / `transportation` / `priceItem`; `referenceID` = attendee/booking/price-item id
- [ ] Amount in integer cents; currency lowercase (e.g. `"eur"`)
- [ ] Never embed `STRIPE_SECRET_KEY`; payment audit doc is backend-only

### D3. Lodging (`ui/lodging/`)

- [ ] CRUD for `campings/{id}/lodging/{unitId}` - gate `canManageTeams`
- [ ] Read: signed-in users
- [ ] Occupancy denormalized into `occupantIDs[]` on the unit doc (no separate assignment collection)
- [ ] Gender policy filter: `any`/`male`/`female`/`family`

### D4. Post-Camp Feedback (`ui/feedback/`)

- [ ] Submit: `campings/{id}/feedback/{uid}` (doc ID == auth.uid); RBAC enforces `overallRating` 1–5 and `feedbackId == auth.uid`
- [ ] `submittedAt` overridden to `serverTimestamp()`; `isAnonymous` hides `displayName` in UI (still stored)
- [ ] Admin results view: gate `canViewParticipantProfiles`

### D5. Venue Map (`ui/venuemap/`)

- [ ] Read/write `campings/{id}/venueMap/config` (single doc, ID `config`)
- [ ] Display: Cloudinary map image overlay + pins at `imageX`/`imageY` positions; optional real lat/lon
- [ ] Admin: upload map image (delete-when-empty on clear); add/edit/delete pins; gate `canManageTeams` or `canManageSchedule`
- [ ] Program↔venue link: write `program.venuePointID` (delete-when-empty) and keep pin `name` in `program.location`

### D6. "Family at Camp" View (`ui/family/camp/`)

- [ ] Guardian read-only aggregate: compose `registrations` + `checkIns` + `teams` + schedule for own children
- [ ] No new Firestore collection; guardian `checkIns` single-get allowed by RBAC

### D7. Admin Hub (`ui/admin/`)

- [ ] Admin tools: user management (read/update `users` docs; admin can set any role)
- [ ] Onboarding checklist UI; moderation queue (reuse C9)
- [ ] Role assignment: admin → any; own-church `youth_director`/`pastor` → only `guest`/`user`/`adult`
- [ ] `id` field written on `users` doc for admin list decoder compatibility

---

## Cross-Cutting (All Phases)

### Data Contract Enforcement

- [ ] Every write path passes the `07` pre-write checklist (doc ID, required fields, enum case, null/delete/omit encoding, timestamps, integer cents, RBAC fields, denormalization, dual notification stores)
- [ ] `FieldValue.delete()` for delete-when-nil fields; explicit Firestore `null` for null-when-absent fields; omit for omit-when-nil fields
- [ ] `createdAt: serverTimestamp()` on first create only; `updatedAt: serverTimestamp()` on every write
- [ ] Money always integer cents; `priceItems[].currency` uppercase; payments API currency lowercase
- [ ] Deterministic doc IDs used exactly: `registrations/{attendeeId}`, `checkIns/{attendeeId}`, `feedback/{uid}`, `votes/{voterId}`, `schedule/config`, `venueMap/config`, `albumSettings/default`, `notificationSettings/default`

### Architecture

- [ ] Each feature: `*Screen.kt` (Composable, no business logic) + `*ViewModel.kt` (`StateFlow<UiState>` sealed: Loading/Loaded/Empty/Error) + service interface + real impl + fake impl
- [ ] Constructor DI via Hilt; no global mutable state
- [ ] Firestore listeners registered in ViewModel init/`onStart`, removed in `onCleared()`; no stacking on recomposition
- [ ] `callbackFlow` to bridge Firestore listeners to `Flow`
- [ ] `@Preview` on every screen using fake service

### Localization

- [ ] String resources in English keys; add `values-pt` (Portuguese) and `values-fr` (French) qualifiers
- [ ] No hardcoded user-facing strings in Kotlin/Composable code

### Accessibility

- [ ] `contentDescription` on all images and icon buttons
- [ ] Touch targets ≥48dp; dynamic font scale (`sp` only)
- [ ] TalkBack traversal order; contrast ratios per WCAG AA
- [ ] `LocalReducedMotion` check before animations

### Offline

- [ ] Firestore disk persistence enabled (Phase A2)
- [ ] Songbook, schedule, and camp program data prioritized for local cache
- [ ] UI handles offline gracefully (show cached data; surface offline indicator on write failure)

### Testing

- [ ] Model round-trip unit tests (serialize → deserialize against fixtures) for every entity in `02`
- [ ] ViewModel state-transition tests (coroutine test + fake service)
- [ ] Permission evaluator unit tests (all 9 roles × all permissions)
- [ ] Firestore emulator rule tests for RBAC-sensitive write paths
- [ ] Compose UI tests for navigation, onboarding, and registration happy paths
- [ ] `./gradlew assembleDebug && ./gradlew testDebugUnitTest && ./gradlew lint` must be green before any PR

---

## Build Order Summary

```
Phase A  →  Phase B  →  Phase C  →  Phase D
(Foundation)  (Core content)  (Engagement)  (Ops & growth)

A1 Gradle setup
A2 Firebase init
A3 Design system
A4 Navigation shell
A5 Permission evaluator      ← gate logic for every feature
A6 Auth + session
A7 Onboarding
A8 Profile + denorm
A9 Family/children
A10 Data models + tests

B1 Campings list/detail
B2 Admin camping CRUD
B3 Registration flow
B4 Registration review
B5 Schedule
B6 Food menu + sync
B7 Announcements
B8 Songbook
B9 Guidelines

C1 Teams ✅
C2 Games + points ✅
C3 Winner reveal ✅
C4 Chat (camping + team)
C5 Polls
C6 QR check-in
C7 Badges
C8 Album media
C9 Content moderation ✅
C10 Notifications (FCM + in-app feed) ✅
C11 Analytics

D1 Transportation tickets
D2 Stripe payments
D3 Lodging
D4 Post-camp feedback
D5 Venue map
D6 Family at camp
D7 Admin hub
```
