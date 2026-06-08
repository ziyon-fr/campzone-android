# Project State - D1 Transportation Tickets

Updated: 2026-05-29

## Current position

- Branch: `d1-transportation-tickets` (branched from updated `main` after
  C11 merge).
- Phase C is complete through C11. Phase D is complete through D1. Next:
  D2 Stripe Payments.
- Verification on this branch:
  - `./gradlew :app:testDebugUnitTest` SUCCESSFUL
  - `./gradlew :app:assembleDebug :app:lintDebug` SUCCESSFUL
  - JAVA_HOME = Android Studio JBR

## D1 surface shipped

Transportation model contract (`data/model/TransportationBooking.kt`):

- Added the canonical D1 QR payload
  `campzone://transport?v=1&c=<campingID>&b=<bookingID>&r=<registrationID>&p=<participantID>&t=<ticketToken>`
  via `TransportationTicketPayload`.
- Added `TransportationScanResult` states for marshal scanning:
  success, already-boarded, unpaid, wrong camp, unknown booking, token
  mismatch, not approved, expired, malformed.
- `TransportationBookingPayload.createPayload` keeps the RBAC-required
  `paymentStatus = "unpaid"` and `boardingStatus = "not_boarded"` literals,
  and writes `validFrom`/`validUntil` as raw `Date` values.

Transportation service (`data/transportation/`):

- `FirestoreTransportationService` reads `campings/{id}/transportationBookings`.
- User ticket reads merge both `userID == uid` and `guardianID == uid`,
  dedupe by booking id, and sort by participant name.
- `createBooking` uses deterministic `{participant.id}-bus` booking ids and
  the exact hand-built payload.
- `markBoarded` writes only `boardingStatus: "boarded"`, `boardedBy`,
  `boardedAt`, and `updatedAt` with `serverTimestamp()`.

UI + navigation (`ui/transportation/`):

- Added passenger tickets screen showing the user's/guardian's transport
  bookings with generated QR bitmaps and payment/boarding status.
- Added marshal scanner screen using the shared camera QR preview. It gates
  with `canManageTransportation`, decodes D1 payloads, validates camping id,
  token, registration/participant ids, approved registration, validity window,
  payment status, and already-boarded state before boarding.
- Added typed routes:
  `AppRoute.TransportationTickets(campingID)` and
  `AppRoute.TransportationScanner(campingID)`.
- Camping detail now exposes Transportation under registered participant camp
  life resources, and Transportation scanner under manager operations.

Localization:

- Added English, French, and Portuguese strings for tickets, scanner, QR,
  statuses, and scan-result copy.

Tests added:

- `TransportationTicketPayloadTest` covers canonical encoding/decoding,
  case-insensitive scheme/host, rejected malformed/foreign payloads, and
  create payload defaults/raw dates.
- `TransportationViewModelTest` covers self+guardian ticket reads,
  successful boarding, wrong camp, token mismatch, unapproved registration,
  unpaid, expired, already-boarded, and restricted scanner access.

## C11 surface shipped

Firebase Analytics wrapper (`data/analytics/`):

- `AnalyticsService` mirrors the iOS event API:
  `viewCamping`, `registerForCamping`, `cancelCamping`, `viewSchedule`,
  `viewSongbook`, `viewTeams`, `playSong`, `favoriteSong`,
  `searchCampings`, `signIn`, `signOut`.
- Event names and params match iOS `AnalyticsService.swift`:
  `view_item` with `item_id`/`item_name`/`content_type=camping`,
  custom camping events (`register_camping`, `cancel_camping`,
  `view_schedule`, `view_songbook`, `view_teams`), song events
  (`play_song`, `favorite_song`), and Firebase-standard `search`/`login`.
- `FirebaseAnalyticsLogger` is bound through Hilt; `FirebaseModule` provides
  `Firebase.analytics`.

Wired call sites:

- Camping detail load logs `viewCamping`.
- Detail resource taps log `viewSchedule`, `viewSongbook`, and `viewTeams`
  before navigating.
- Registration success logs `registerForCamping`.
- Admin cancellation success logs `cancelCamping`.
- Campings search logs non-empty `searchCampings` terms.
- Auth success/sign-out log `signIn` and `signOut`.
- Song audio start logs `playSong`; adding a favorite logs `favoriteSong`.

Tests added:

- `CampzoneAnalyticsServiceTest` covers the iOS event names/parameters,
  blank-search suppression, and auth/song/camping mappings.

## C10 surface shipped

Push + token plumbing (`core/notifications/`):

- `CampzoneMessagingService : FirebaseMessagingService` (`@AndroidEntryPoint`)
  - `onNewToken` → `NotificationDeviceRegistrar.storeToken` (role read from
    `users/{uid}`).
  - `onMessageReceived` → channel notification keyed by `data.type`; tap
    `PendingIntent` re-opens `MainActivity` with the FCM data map as extras
    (existing `IntentDeepLinks`/`CampzoneDeepLink.fromPayload` resolves it).
- `NotificationChannels` (announcement/chat_message/poll/schedule_reminder/
  team_update/registration/general) created in `CampzoneApp.onCreate`;
  manifest `<service>` + `default_notification_channel_id=general`.
- `NotificationDeviceRegistrar`: token doc `users/{uid}/notificationTokens/
  {sha256hex}` (reuses `NotificationPrefsPayload.tokenPayload`) **and**
  `POST /notifications/devices`. Run once per uid on sign-in via
  `NotificationBootstrapViewModel` (LaunchedEffect in `AuthGate` SignedIn).
- `FirebaseModule` now provides `FirebaseMessaging`.

Backend client (`data/notifications/`):

- `NotificationApi` / `BackendNotificationApi` (HttpURLConnection + Firebase
  ID token, mirrors `RegistrationNotificationDispatcher`): `registerDevice`,
  `syncSettings` (sends both `subscribedRoleRawValues` + `subscribedRoles`).

Settings (`data/notifications/` + `ui/notifications/`):

- `NotificationSettingsRules` (pure): defaults / `normalizedFor` /
  `roleAudienceOptions` / `sanitized` — ported from iOS.
- `NotificationSettingsService` (`Firestore…` + `Fake…`): read/write
  `users/{uid}/notificationSettings/default` (merge + `updatedAt`) then
  `api.syncSettings`.
- `NotificationSettingsViewModel` (shared via the settings nav back-stack
  entry) + `NotificationSettingsScreen`: master, 5 categories, role audiences
  (admins all roles, others own), channel nav rows. Optimistic apply +
  revert-on-failure with typed `NotificationOpMessage`.
- Full-parity channel pickers: `NotificationCampingChannelsScreen` /
  `NotificationTeamChannelsScreen`, backed by `NotificationChannelsLoader`
  (interface + `Firestore…` impl): attended campings via CG `registrations`
  (`userID`/`guardianID`) ∩ camping list; personal team via
  `memberUserIds.contains(uid)`.

In-app feed (`data/model/` + `data/notifications/` + `ui/notifications/`):

- `AppNotificationKind` extended (+`chat_mention`, `registration`,
  `team_update`); `AppNotification` gained `event`, `mentionedUserIds`,
  `concerns()`, `deepLink()`.
- `NotificationTopics` (topic builder + `visibleTopics` + role gating) ported
  verbatim from iOS.
- `AppNotificationFeedService` (`Firestore…` per-topic snapshot listeners,
  merge/dedupe/sort, `awaitClose` removes all; `Fake…`) +
  `AppNotificationFeedViewModel` (single owned stream, cancelled in
  `onCleared`) + `AppNotificationFeedScreen` (tappable rows → deep link).

Navigation: `AppRoute.NotificationFeed` (Home bell) + `…CampingChannels` /
`…TeamChannels` (under settings). Home bell now opens the **feed**; Profile →
settings (iOS parity).

Localization: all new strings in `values` + `values-fr` + `values-pt-rBR`.

## Tests added

- `NotificationSettingsRulesTest`, `NotificationTopicsTest`,
  `AppNotificationDeepLinkTest` (deepLink + concerns + extended `fromWire`),
  `NotificationSettingsViewModelTest`, `AppNotificationFeedViewModelTest`.

## iOS parity reference read

- `Features/Notifications/` Service (`NotificationService`,
  `ZiyonNotificationAPIClient`, `AppNotificationFeedService`), Model
  (`AppNotification`, `NotificationModels`), View
  (`NotificationSettingsView`, `AppNotificationFeedView`), Observer/*.

## Not done / deferred

- Phase D.
- Backend `dispatch/*` hookups were already wired in earlier phases.
- Owner-side: ensure the CG single-field index for `registrations` supports
  the channel-picker queries (same pattern as profile denormalization).

---

## Vehicle QR code check-in handoff

- Implemented the Android vehicle QR check-in core against the shipped iOS
  Firestore contract: manual vehicle/user-vehicle/check-in decoding, narrow
  payload writers, Firestore services + fakes, `VehicleViewModel`, participant
  transport hub, vehicle wizard, QR pass, manager dashboard, scanner, arrival
  confirmation, saved profile vehicle CRUD/default, and `MyVehicleCard` inside
  My QR Passes.
- Converted camping registration into the iOS-style progressive Who ->
  Transport -> Review wizard. The Transport step now offers inline own-car
  capture when the self participant is selected and no ticketed organizer
  transport is chosen; it can reuse saved vehicles or capture plate, vehicle
  details, seats, availability, and notes inline.
- On submit, the flow still writes registrations first, then creates the
  camping vehicle QR, links the attendee with `transportationMode=ownCar`,
  `vehicleID`, `isDriver=true`, and best-effort saves typed vehicle details to
  `users/{uid}/vehicles`.
- Entry points now exist at Camping Detail -> Vehicles, My QR Passes -> My
  Transportation, and Profile -> My Vehicles.
- Registration transport writes use the additive fields
  `transportationMode`, `vehicleID`, `isDriver`, `needsTransportHelp`,
  `transportationNotes`, `updatedAt`. Vehicle edit writes use the driver/manager
  allowlist only; immutable create fields and `qrToken` are not sent on edit.
- Verified with Android Studio JBR:
  - `./gradlew :app:compileDebugKotlin`
  - `./gradlew :app:testDebugUnitTest --tests fr.ziyon.campzone.data.model.VehicleModelTest`
  - `./gradlew :app:testDebugUnitTest --tests fr.ziyon.campzone.ui.camping.register.CampingRegistrationViewModelTest --tests fr.ziyon.campzone.data.model.VehicleModelTest`
- Still needs device QA for CameraX/ML Kit scanning and live Firestore rules.
  Standalone vehicle screens still need a dedicated string-resource pass.
