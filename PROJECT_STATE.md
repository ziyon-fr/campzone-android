# Project State - C10 Notifications (FCM + in-app feed)

Updated: 2026-05-29

## Current position

- Branch: `c10-notifications` (off committed C9 `c9-content-moderation`).
- Phase C is complete through C10. Remaining: C11 Analytics, then Phase D.
- Verification green on this branch: `./gradlew :app:assembleDebug`,
  `:app:testDebugUnitTest`, `:app:lintDebug` all SUCCESSFUL
  (JAVA_HOME = Android Studio JBR).

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
- C11 Analytics; Phase D.
- Backend `dispatch/*` hookups were already wired in earlier phases.
- Owner-side: ensure the CG single-field index for `registrations` supports
  the channel-picker queries (same pattern as profile denormalization).
