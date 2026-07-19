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
- [x] On first sign-in: create `users/{uid}` doc with `role: "user"`, `createdAt: serverTimestamp()`, `onboardingCompleted: false` - **merge: true**; do not overwrite existing `email`/`displayName`/`photoURL`
- [x] Session state: `StateFlow<AuthState>` (signed-out / onboarding-incomplete / signed-in)
- [x] Sign-out clears local session; navigates to auth screen

### A7. Onboarding (`ui/onboarding/`) ✅

- [x] Collect: age (derive `ageGroup`), church, preferred language, gender
- [x] Write `users/{uid}` with `onboardingCompleted: true`, `languages: [preferredLanguage]` (single element), preserving the first-sign-in role - **merge: true**
- [x] Apply `07` pre-write checklist: `age`/`ageGroup`/`gender` are **delete-when-nil** if blank; role stays within the self-assignable set
- [x] Gate: show onboarding when `onboardingCompleted == false`; request FCM permission **after** onboarding completes (not at launch)

### A8. Profile View/Edit + Account Deletion (`ui/profile/`) ✅

- [x] Profile screen: display all `users/{uid}` fields
- [x] Edit screen: update self-profile field allowlist only (`displayName`, `age`, `ageGroup`, `gender`, `church`, `skills`, `profession`, `education`, `pathfinderRank`, `phone`, `preferredLanguage`, `languages`, `role` in {user/adult} only)
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
- [x] Dispatch `POST /notifications/dispatch/registration` after submit — self-only registration body names the user once as the registrant ("{requester} registered for {camp}") instead of the redundant "{requester} requested to register {requester}"; family registrations still name the participant(s) (diverges from current iOS copy — mirror to iOS later)
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
- [x] Scoped writes: `canManageSongbook` (admin, camp creator, or own-church
  youth director/leader with the songbook permission); create/edit/delete songs
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
- [x] After team mutation: `POST /notifications/dispatch/team` (`TeamNotificationDispatcher`, dispatched from `TeamViewModel`/`GameViewModel`)
- [x] Auto-balance members (`TeamBalancer` + `previewAutoBalance`/`applyAutoBalance`, `TeamAutoBalanceSheet`)
- [x] Real-time: teams stream via `TeamService.observeTeams`; the camping doc (winner-reveal policy/score visibility) streams via `CampingService.observeCamping` so scores/ranking update live across devices

### C2. Games & Points (`ui/games/`) ✅

- [x] CRUD for `campings/{id}/games/{gameId}` - gate `canManageGames`
- [x] Game `updatedAt` is client `Date()` (not serverTimestamp)
- [x] Award points: write `activities/{activityId}` first, then mutate team doc
  - Activity is immutable (`update: false`); `createdBy == auth.uid`; `campingID == path` (RBAC checked)
  - Negative team award → append positive-magnitude `penalties[]` entry (never decrement `points`)
  - User award → add delta to `members[i].personalScore`; rewrite full team doc + `memberUserIDs`
- [x] `activities` list: gate `canManageGames` or `canRevealWinners` or (approved participant + `visibility=="immediate"`)
- [x] Real-time: `GameService.observeGames`/`observeActivities` snapshot listeners; `GameViewModel` streams them (single owned job, removed in `onCleared`) so awarded points/new games surface on every device without a manual reload

### C3. Winner Reveal (`ui/games/reveal/`) ✅

- [x] Gate: `canRevealWinners`
- [x] Write only `winnerRevealPolicy` via dedicated update path - forbidden in normal camp edit
- [x] `winnerRevealPolicy.isRevealed` required if map is present
- [x] Synchronized reveal: the ceremony countdown anchors on the shared `revealedAt` (`revealCountdownSeconds`), so once an admin reveals, every device streaming the live camping doc converges on the same trophy moment (`revealedAt + 10s`); late joiners skip straight to the trophy

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
- [x] Read top-level `badges/{achievementId}` catalog display data with the shipped in-code `AchievementCatalog` as fallback; unknown earned ids are filtered out
- [x] Decode Firestore catalog `localizations.en/fr/pt-BR` display copy, with legacy flat-field fallback
- [x] Catalog rarity/awardKind come from Firestore when present, falling back to the in-code mirror
- [x] `campingID` and `note` are explicit Firestore `null` when absent (not omitted)
- [x] Manual award: gate `canAwardAchievements`; write to `users/{targetUid}/badges/{achievementId}` (RBAC asserts `request.auth.uid != uid`)

### C8. Album Media (`ui/media/`) ✅

- [x] Read gate: approved participant or album-manager
- [x] Upload: check `albumSettings/default.allowedUploadRoles` contains user's role; `POST /cloudinary/sign` → upload image/video; write `media/{mediaId}` (full set)
- [x] Admin delete/edit: gate `canManageAlbumMedia` or uploader
- [x] Settings: `albumSettings/default.allowedUploadRoles` sorted role raws, manager-only writes
- [x] Load with Coil from Cloudinary URLs; thumbnail via `thumbnailURL`

### C9. Content Moderation (`ui/admin/moderation/`) ✅

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

### C11. Analytics ✅

- [x] Firebase Analytics; mirror iOS event set: `viewCamping`, `registerForCamping`, `cancelCamping`, `viewSchedule`, `viewSongbook`, `viewTeams`, `playSong`, `favoriteSong`, `searchCampings`, `signIn`, `signOut`

---

## Phase D - Operations & Growth

### D1. Transportation Tickets (`ui/transportation/`) ✅

- [x] Read bookings: merge `userID==uid` + `guardianID==uid` queries
- [x] RBAC on create: `paymentStatus == "unpaid"`, `boardingStatus == "not_boarded"` literals
- [x] QR payload: `campzone://transport?v=1&c=<campingID>&b=<bookingID>&r=<registrationID>&p=<participantID>&t=<ticketToken>`
- [x] Admin scanner: decode QR → validate `ticketToken`; update `boardingStatus: "boarded"` + `boardedBy`/`boardedAt`; gate `canManageTransportation`
- [x] `validFrom`/`validUntil`: raw Date → Timestamp (not serverTimestamp)
- [x] iOS parity: round-trip model (`coversReturn`) with outbound/return legs × departure/arrival checkpoints + append-only `scanHistory` audit log (legacy `boardedAt`/`arrivedAt` back-filled); `isActive`/`arrived*`/`canceled*` fields. Schema extended beyond `02` §7.2 to match iOS (iOS authoritative)
- [x] iOS parity: marshal **Dashboard** (per-leg progress, bookings grouped by state, payment segmented control → `updatePaymentStatus`, cancel → `cancelBooking`, add-voyager sheet → `createBooking`); **Scan History** audit feed with leg filter; round-trip **BusTicketCard** (leg cards + per-checkpoint timeline + QR); **Scanner** leg/checkpoint mode picker + arrival scan + per-leg live tally
- [x] Service: leg-aware `markBoarded`/`markArrived` (arrayUnion scan events + legacy outbound mirror), `updatePaymentStatus`, `cancelBooking` (`cancelReason` delete-when-empty); add-voyager free option settles `waived` via the manager update path (create stays `unpaid` per RBAC)
- [x] Passenger fare CTA presents the Stripe PaymentSheet (D2) — per-booking fare CTA under each `BusTicketCard` (`transportation` kind, `referenceID = booking.id`); paid/waived keep the header status pill

### D2. Stripe Payments (`ui/payments/`) ✅

- [x] Call `POST /payments/intent` with Firebase ID token; receive `paymentIntentClientSecret` + `ephemeralKeySecret` + `customerId` + `publishableKey`
- [x] Present Stripe Android **PaymentSheet** with the returned params (reusable `CzPaymentButton` + `PaymentButtonViewModel`)
- [x] On PaymentSheet success → call `POST /payments/confirm`; backend auto-approves paid camps (`registrationStatus: "approved"`, `paymentStatus: "paid"`)
- [x] Kinds: `registration` / `transportation` / `priceItem`; `referenceID` = attendee/booking/price-item id — registration (registration step-2 screen + Fees hub), transportation (tickets fare CTA), priceItem (Fees hub: card / 3-installment / IBAN bank transfer)
- [x] Amount in integer cents; currency lowercase (e.g. `"eur"`) — `PaymentRequest.normalizedCurrency`
- [x] Never embed `STRIPE_SECRET_KEY`; payment audit doc is backend-only
- [x] iOS parity: reusable inline pay button (`CzPaymentButton`, mirrors iOS `PaymentButton`); "Fees & Payments" hub (`CampingPricingScreen`) folds price items (mirrors iOS `CampingPricingView`) + pending registration fees; camp-detail entry gated on `hasPendingRegistrationPayment || hasPayablePriceItems`
- [x] PDF invoice receipts: `PaymentReceiptPdf` renders a one-page A4 receipt (invoice number, camp, date, line items, total) to cache and shares it via the existing `${applicationId}.fileprovider`
- [x] Payment-proof/history list: `PaymentProofService` (interface + Firestore + fake + bindings) reads `campings/{id}/payments` (backend audit) + `campings/{id}/invoices` (client receipt), merged into `PaymentProof`; surfaced as a "Receipts" section in the Fees hub (`CampingPricingScreen`) with per-row PDF share. Invoice write gated by the deployed `isOwnPaidPayment` + `invoiceMatchesPaidPayment` rules (`amount`/`currency`/`paymentID` must match the paid payment doc)
- [x] Mixed registration+transport single-charge bundling: `PaymentRequest` carries `lineItems` (per-kind) + `summary` + `referenceIDs`; the registration payment step folds an unpaid bus fare into the registration charge. After the primary confirm, `PaymentButtonViewModel`/`RegistrationPaymentViewModel` run a follow-up `confirm()` per extra kind (`subrequest(kind)`) so each Firestore sub-collection flips off the same charge, then `recordInvoice` persists the full line-item set

### D3. Lodging (`ui/lodging/`) ✅

- [x] CRUD for `campings/{id}/lodging/{unitId}` - gate `canManageTeams` (`LodgingViewModel` + `LodgingScreen`/editor sheet; manager-only surface, `Restricted` state otherwise)
- [x] Read: signed-in users (`MyLodgingViewModel` observe; `LodgingService` Firestore listener, read open per `03`)
- [x] Occupancy denormalized into `occupantIDs[]` on the unit doc (no separate assignment collection) — `setOccupants` (merge `occupantIDs` + `updatedAt`), assign/remove via the unit's list
- [x] Gender policy filter: `any`/`male`/`female`/`family` (filter chips); assignment also enforces `genderPolicy.accepts(gender)` + capacity
- [x] iOS parity: reused the existing `LodgingUnit` model + `LodgingPayload`/`toLodgingUnitOrNull` (added `accepts`, `occupancy`/`availableSpots`/`occupancyText`/`contains`); admin summary + units + "Needs a bed" sections, editor + assignment `ModalBottomSheet`s; **My Lodging** card embedded in My QR Passes (mirrors iOS `CheckInQRView` → `MyLodgingCard`)
- [x] One-tap **auto-allocate** (capacity + gender + family rules): `LodgingAllocator` (port of iOS, deterministic constrained bin-pack; never exceeds capacity, honours male/female policy, keeps a guardian + their children together, soft tight-pack + age coherence) → `LodgingViewModel.autoAllocate()` persists via `LodgingService.applyAllocation` (one batched write); "Auto-allocate beds" button + result snackbar (`LodgingAllocatorTest`)

### D4. Post-Camp Feedback (`ui/feedback/`) ✅

- [x] Submit: `campings/{id}/feedback/{uid}` (doc ID == auth.uid); RBAC enforces `overallRating` 1–5 and `feedbackId == auth.uid` — `FeedbackService` (interface + Firestore + fake) hand-builds the payload via `CampFeedbackPayload`, `set(merge)`, `overallRating` coerced 1–5
- [x] `submittedAt` overridden to `serverTimestamp()` (also `updatedAt`); `isAnonymous` hides `displayName` in the results UI (still stored)
- [x] Admin results view: gate `canManageAnyCamping` (iOS `CampFeedbackResultsView` is authoritative; rules also allow read for `canViewParticipantProfiles`) — average overall, would-return %, per-program averages, comment stream
- [x] iOS parity: `CampFeedbackSurveyScreen` (availability window opens on `endDate`, closes 60 days later; not-available/closed/thanks/form states; 1–5 star picker, per-session ratings excluding meals, highlights/improvements, would-return + anonymous toggles) and detail-screen "Share your feedback" card + "Feedback Results" management entry, both gated like iOS `CampingDetailView`
- [x] Two VMs (`FeedbackSurveyViewModel` / `FeedbackResultsViewModel`, sealed `UiState`), typed routes `CampFeedbackSurvey`/`CampFeedbackResults`, EN/FR/PT strings; build + unit tests (`FeedbackViewModelTest`) + lint green

### D5. Venue Map (`ui/venuemap/`) ✅

- [x] Read/write `campings/{id}/venueMap/config` (single doc, ID `config`) — `VenueMapService` (interface + Firestore live listener + fake), `VenueMapPayload` hand-built map (`imageURL`/`imagePublicID` **delete-when-empty**, embedded `points[]` with omit-when-nil `imageX`/`imageY`/`latitude`/`longitude`), `updatedAt` serverTimestamp
- [x] Display: **Illustration / Map** segmented control (mirrors iOS `VenueMapMode`). Illustration = Coil site-image overlay (4:3) + pins at `imageX`/`imageY` + "Locations" legend; Map = **in-app osmdroid (OpenStreetMap) map** with camp + pin markers (key-free), tappable to the shared detail card; coordinate pins also offer **Open in Maps** (`geo:` intent)
- [x] Admin: upload/replace/remove site image via the shared backend-signed Cloudinary path (delete-when-empty on clear); tap-to-place / reposition / edit / delete pins; set a pin's coordinate on an **interactive osmdroid crosshair map** (pan so the marker sits on the spot) or clear it; gate `canManageTeams` OR `canManageSchedule` (else `Restricted`)
- [x] Program↔venue link: program editor shows the camp's pins as quick-pick chips → sets `venuePointID` + fills `location` with the pin `name`; typing a custom location clears `venuePointID` (write stays delete-when-empty)
- [x] iOS parity: `VenueMapScreen` (viewer, edit affordance for managers) + `VenueMapEditorScreen` (site image section, interactive canvas, points list, editor/coordinate `ModalBottomSheet`s) + self-silencing `VenueMapEntryCard` in the camp detail **and** the header location chip both deep-link to the map (mirrors iOS `CampingDetailView`). `VenueMapViewModel` (sealed `UiState`, single owned listener), reusable osmdroid `VenueOsmMap`/`VenueOsmPicker` (lifecycle-managed `MapView`), typed routes, EN/FR/PT strings, `VenueMapViewModelTest`; build + tests + lint green
- [x] Live user-location + in-app directions (osmdroid, key-free): the Map tab shows the user-location dot (runtime `ACCESS_FINE_LOCATION`, graceful denied pill), a recenter FAB, and a "Directions to <pin/camp>" control that draws the road `Polyline` + an ETA pill (min · distance) using the public OSRM endpoint — mirrors the iOS `VenueMapKitCanvas`. (Production note: OSRM `router.project-osrm.org` is a demo server; self-host for production. The `geo:` "Open in Maps" intent remains as a fallback.)

### D6. "Family at Camp" View (`ui/guardian/`) ✅

- [x] Guardian read-only aggregate: composes per-child `registrations` (per-doc get) + `checkIns` (per-doc get) + `teams` (live) + schedule for own children — `GuardianUpdatesService` owns no query logic of its own, it reuses the existing CheckIn/Team/Schedule services (mirrors iOS `Features/Guardian`); `GuardianUpdate` model derives `GuardianChildUpdate` snapshots (check-in status, team + score gated by `winnerRevealPolicy.areScoresHidden`)
- [x] No new Firestore collection; guardian `checkIns`/`registrations` read by single-doc id (the blanket queries are denied by RBAC, so per-child gets only); team read signed-in, schedule public
- [x] iOS parity: `GuardianUpdatesScreen` (per-child detail cards: avatar + age/church, check-in row with method, team row with score/"scores hidden"/"not on a team"; + "Today at camp" schedule card with happening-now / up-next) and a self-silencing pine-gradient `GuardianUpdatesCard` in the camp detail (shown only when the viewer registered a child here; taps → the read-only screen). `GuardianUpdatesViewModel` (sealed `UiState`, single owned team listener), typed route `CampingGuardianUpdates`, EN/FR/PT strings, `GuardianUpdatesViewModelTest`; build + tests + lint green
- Note: implemented under `ui/guardian/` + `data/guardian/` (matching the iOS `Features/Guardian` naming) rather than `ui/family/camp/`

### D7. Admin Hub (`ui/admin/`) ✅

- [x] Admin tools: user management (read/update `users` docs; admin can set any role) — `RoleManagementScreen` + `RoleAssignmentService` (interface + Firestore + fake) + `RoleManagementViewModel`; `ManagedUser` decoded manually from the raw map (no POJO), `id`→`uid`→docId fallback
- [x] Onboarding checklist UI (`AdminOnboardingScreen`, SharedPreferences-backed `AdminOnboardingViewModel`, 5 steps mirroring iOS — camping/announcement navigate, roles/rules/notifications toggle, progress ring + bar + reset); moderation queue reused (C9)
- [x] Role assignment: admin -> any (`assignableRoles` = all wire roles); own-church `youth_director`/`pastor` -> only `user`/`adult` (`selfAssignableRoles`); list church-filtered for non-admin assigners to match the RBAC read rule; church-scoped write stays `{role, updatedAt}` only
- [x] `id` field written on `users` doc for admin list decoder compatibility — stamped on the **admin** role-update only (`writeIdField`); the self-profile allowlist and `validChurchRoleAssignment` (affectedOnly role+updatedAt) forbid `id` on every other write path, so this is the only RBAC-compliant place
- [x] Hub expanded (`AdminToolsScreen`): Operations (Setup Guide / Registration Review / Role Assignment) + Moderation + Infrastructure; restricted unless moderator OR admin-tools OR any-role-assigner; typed routes `RoleManagement`/`AdminOnboarding`; EN/FR/PT strings; build + unit tests (7 `RoleManagementViewModelTest`) + lint green

### D8. F3 Recurring Camps + Program Attendance (`ui/camping/template/`, `ui/attendance/`) ✅

- [x] Recurring camps/templates: camp-detail management exposes "Create recurring camp" for users who can create the target camp and copy at least one scoped section. The clone form defaults title/dates one year forward, registration status `closed`, validates title/date/content, and writes a fresh camping with live data reset.
- [x] Template clone service: `CampingService.cloneCampingTemplate` writes the new camping doc with a client UUID, clears attendees/featured state/creator timestamps, shifts registration deadline, optionally copies guidelines, schedule config/days/programs, team shells, and songbook order/content. Schedule dates shift by day offset; venue/game links are cleared; team members/scores/penalties and song favorites are reset.
- [x] Per-program attendance: program detail exposes attendance for `canManageCheckIns`; records live under `campings/{campingID}/programAttendance/{programID}/records/{attendeeID}` with doc ID = attendee ID. Scanner reuses the canonical check-in QR payload, validates camp, attendee/user id, approval status, duplicate state, and reports save failures distinctly.
- [x] Attendance management UI: records screen shows present/missing counts, searchable present records, approved-but-missing attendee rows, manual mark-present, correction timestamp, and removal. Scanner route uses the shared camera preview and links back to records.
- [x] RBAC parity: songbook writes now use the scoped iOS rule (`canManageSongbook`) so recurring templates can offer songbook copy only to admin, creator, or own-church youth director/leader. Localized EN/FR/PT/PT-BR strings added.
- [x] Tests: `CampingTemplateCloneTest`, `CampingTemplateCloneViewModelTest`, `ProgramAttendanceViewModelTest`, and updated `AppPermissionEvaluatorTest` cover clone defaults/validation/live-state reset, scoped clone creation, QR success/duplicate/reject/save-failure retry, restricted users, and scoped songbook writes.

---

## Cross-Cutting (All Phases)

### Data Contract Enforcement

- [x] Every write path passes the `07` pre-write checklist (doc ID, required fields, enum case, null/delete/omit encoding, timestamps, integer cents, RBAC fields, denormalization, dual notification stores)
- [x] `FieldValue.delete()` for delete-when-nil fields; explicit Firestore `null` for null-when-absent fields; omit for omit-when-nil fields
- [x] `createdAt: serverTimestamp()` on first create only; `updatedAt: serverTimestamp()` on every write
- [x] Money always integer cents; `priceItems[].currency` uppercase; payments API currency lowercase
- [x] Deterministic doc IDs used exactly: `registrations/{attendeeId}`, `checkIns/{attendeeId}`, `feedback/{uid}`, `votes/{voterId}`, `schedule/config`, `venueMap/config`, `albumSettings/default`, `notificationSettings/default`

### Architecture

- [x] Each feature: `*Screen.kt` (Composable, no business logic) + `*ViewModel.kt` (`StateFlow<UiState>` sealed: Loading/Loaded/Empty/Error) + service interface + real impl + fake impl
- [x] Constructor DI via Hilt; no global mutable state
- [x] Firestore listeners registered in ViewModel init/`onStart`, removed in `onCleared()`; no stacking on recomposition
- [x] `callbackFlow` to bridge Firestore listeners to `Flow`
- [x] `@Preview` on every screen using fake service

### Localization

- [x] String resources in English keys; add `values-pt` (Portuguese) and `values-fr` (French) qualifiers
- [x] No hardcoded user-facing strings in Kotlin/Composable code

### Accessibility

- [x] `contentDescription` on all images and icon buttons (enforced via the `Cz*` components + `czContentDescription`)
- [x] Touch targets ≥48dp; dynamic font scale (`sp` only) (`CzSpacing.minTouchTarget` + `sp` typography)
- [ ] TalkBack traversal order; contrast ratios per WCAG AA — not formally audited
- [ ] `LocalReducedMotion` check before animations — not implemented (animations always run)

### Offline

- [x] Firestore disk persistence enabled (Phase A2)
- [x] Songbook, schedule, and camp program data prioritized for local cache (covered by the persistent disk cache)
- [ ] UI handles offline gracefully — cached reads work via persistence and write failures surface as errors, but there is no dedicated offline indicator yet

### Testing

- [x] Model round-trip unit tests (serialize → deserialize against fixtures) for every entity in `02`
- [x] ViewModel state-transition tests (coroutine test + fake service)
- [x] Permission evaluator unit tests (all 9 roles × all permissions) (`AppPermissionEvaluatorTest`)
- [ ] Firestore emulator rule tests for RBAC-sensitive write paths — only partial (`NotificationSettingsRulesTest`); not every write path is covered
- [ ] Compose UI tests for navigation, onboarding, and registration happy paths — not written (only the boilerplate `ExampleInstrumentedTest`)
- [x] `./gradlew assembleDebug && ./gradlew testDebugUnitTest && ./gradlew lint` must be green before any PR (standing done-bar, met)

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
C4 Chat (camping + team + operations team) ✅
C5 Polls ✅
C6 QR check-in ✅
C7 Badges ✅
C8 Album media ✅
C9 Content moderation ✅
C10 Notifications (FCM + in-app feed) ✅
C11 Analytics ✅
C12 Organizer-defined operations teams ✅

D1 Transportation tickets ✅
D2 Stripe payments ✅
D3 Lodging ✅
D4 Post-camp feedback ✅
D5 Venue map ✅
D6 Family at camp ✅
D7 Admin hub ✅
D8 F3 recurring camps + program attendance ✅
```
