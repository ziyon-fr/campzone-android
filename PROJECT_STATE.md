# Project State - C11 Analytics

Updated: 2026-05-29

## Current position

- Branch: `c11-analytics` (branched from clean `c10-notifications` state).
- Phase C is complete through C11. Remaining: Phase D.
- Verification green on this branch: `./gradlew :app:testDebugUnitTest`,
  `:app:assembleDebug`, `:app:lintDebug` all SUCCESSFUL
  (JAVA_HOME = Android Studio JBR).

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
