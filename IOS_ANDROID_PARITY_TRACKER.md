# iOS → Android Parity Tracker

Last updated: 2026-07-02

Android repository: `/Users/leon/AndroidStudioProjects/Campzone`
iOS source of truth: `/Users/leon/Desktop/Business Projects/Campzone/Campzone`
Android branch at baseline: `venue-map-games-locations-instructions`

## Purpose

This is the persistent execution ledger for iOS changes that must be copied or
adapted to Android. Update it whenever:

- a new iOS behavior, model field, route, permission, localization key, or
  visual state is added;
- an Android implementation starts, changes status, or is verified;
- a comparison finds a new gap or proves an apparent gap is platform-specific;
- verification succeeds or fails.

The shipped iOS behavior is authoritative for structure, interaction, copy, and
Firestore semantics. Android platform conventions may differ, but a difference
must be intentional and recorded here.

## Status legend

- `OPEN`: confirmed Android gap.
- `IN PROGRESS`: implementation is actively being changed.
- `PARTIAL`: Android has some of the behavior but not the complete iOS contract.
- `VERIFY`: code appears present; device/UI or end-to-end verification remains.
- `DONE`: implementation and appropriate automated verification passed.
- `N/A`: iOS-only technology or an intentional Android adaptation.
- `BLOCKED`: needs backend deployment, product decision, or external authority.

## Progress summary

| Bucket | Count | Meaning |
| --- | ---: | --- |
| Remaining implementation gaps | 0 | No source-confirmed implementation gap remains in the current iOS working-tree audit; device findings can reopen this count |
| Partial / verification queue | 20 | Includes the broader audits plus the latest high-visibility polish awaiting device/UI or external-app acceptance |
| Automated-verified implementations | 34 | Baseline slices plus the completed correctness, Songbook, resource-gate, album-permission, family-query, stale-link, structured-menu, Camping-management, team-scoring, auto-balance, Songbook-row, checklist-share, vehicle-offer, and vehicle self-removal closures |
| Intentional platform adaptations | 5 | Do not port literally |

## Execution order

1. Data-contract and notification correctness.
2. Missing user-facing behavior.
3. High-visibility Campings/Home visual parity.
4. Feature-by-feature device verification.
5. Localization, accessibility, full tests, lint, and debug assembly.

---

## Parity updates from the 2026-07-02 iOS working-tree audit

iOS source reviewed: the current dirty tree for `CampingVehicle`,
`VehicleObserver`, `VehicleService`, `VehicleFormView`, `FoodMenuView`,
`ParticipantAllergyListView`, `AppNotification`, `CampzoneDeepLink`,
`PackingShareImportView`, team reassignment helpers, organizer-level validation,
and the updated Firestore RBAC rules/tests.

| ID | Status | iOS source / behavior | Android evidence | Required Android result | Verification |
| --- | --- | --- | --- | --- | --- |
| TRANSPORT-003 | DONE | `VehicleService.swift`, `VehicleObserver.swift`, `MyTransportationView.swift`, and RBAC tests let assigned passengers remove themselves, keep driver/manager passenger removal, and reject second active vehicle claims across driver, passenger, and pending-request states. | Android mirrors the contract in `Vehicle.kt`, `VehicleService.kt`, `VehicleViewModel.kt`, and `VehicleScreens.kt`: passenger state exposes a localized leave action, driver add/invite candidates exclude already-claimed registrations, request/approve/add mutations guard against duplicate active claims, and narrow passenger payloads restore seats. | A registration must be claimed by at most one active vehicle at a time; moving cars requires leaving/removal first, and passenger self-removal must only remove the signed-in passenger's own approved seat. | Focused Android `VehicleModelTest`/`VehicleUiStateTest`, Firebase RBAC vehicle tests, targeted iOS vehicle tests, Android XML validation, iOS localization JSON validation, and scoped Android/iOS `git diff --check` passed 2026-07-02. |
| TRANSPORT-002 | DONE | `CampingVehicle.swift`, `VehicleObserver.swift`, `VehicleService.swift`, `VehicleFormView.swift`, and RBAC tests add `offeredSeats` as the driver-controlled public carpool offer cap, with legacy fallback to available seats and passenger mutations consuming/restoring the explicit offer. | Android now decodes, derives, mutates, filters, creates, updates, cancels, and documents `offeredSeats`; dashboard/open-seat counts and the vehicle form use the offered-seat cap while preserving legacy vehicles. | Keep `availableSeats` as physical capacity and `offeredSeats` as the currently offered public seats; non-drivers must not mutate the field. | Focused `VehicleModelTest`/`VehicleUiStateTest`, full `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`, Android `git diff --check`, iOS app build, targeted iOS vehicle tests, iOS `git diff --check`, and Firebase RBAC vehicle tests passed 2026-07-02. |
| FOOD-002 | VERIFY | `FoodMenuView.swift` opens `ParticipantAllergyListView` for managers so leaders can see attendees with food-relevant allergy/restriction tokens and jump into attendee context. | Android Food Menu now exposes a manager allergy action, inline restriction count, attendee allergy dialog, allergy chips, and attendee-profile navigation from each row. | Device-check toolbar placement, empty/non-empty dialog states, TalkBack labels, and attendee navigation against iOS. | Full Android `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`, Android `git diff --check`, iOS app build, targeted iOS Food/notification-adjacent tests, and iOS `git diff --check` passed 2026-07-02. |
| PACK-002 | DONE | `AppNotification.swift`, `CampzoneDeepLink.swift`, and `PackingShareImportView.swift` carry checklist-share notification/deep-link context with an optional subject registration ID fallback. | Android already has the checklist notification family, `CampzoneDeepLink.PackingShare`, share/import route query parsing, `actionSubjectRegistrationID`, and subject-registration fallback for packing-share imports. | Preserve the optional `registrationID`/`subjectRegistrationID` semantics so family checklist shares open under the intended registration when present and fall back safely when absent. | Existing notification/deep-link tests passed inside full `:app:testDebugUnitTest` on 2026-07-02; targeted iOS packing/notification tests and Firebase checklist-share rules tests also passed. |

Reviewed with no new Android implementation gap: pending team reassignment/add-member
UI, organizer-level normalization and validation, allergy multi-select state
handling, and Songbook parser/performance changes already tracked in earlier
2026-07-01 entries.

---

## Parity updates from the 2026-07-01 Songbook source audit

iOS source reviewed: the updated Campzone Songbook parser/performance files and
the WorshipPlus chord parser source used by the iOS port.

| ID | Status | iOS source / behavior | Android evidence | Required Android result | Verification |
| --- | --- | --- | --- | --- | --- |
| SONG-003 | VERIFY | `ChordParser.swift`, `ChordSymbolParser`, `SmoothScrollView.swift`, and `ChordsPerformanceView.swift`: strict WorshipPlus-style chord parsing preserves slash basses, short numeric adds, alterations, `alt`/`no`/`omit`, `ø`/`º`/`Δ` forms, low-speed auto-scroll accumulates fractional movement, and performance mode can open full-screen with close chrome. | Android now has `ChordSymbolParser`, routes `ChordProParser` through semantic tokenization, transposes complex symbols without treating `6/9` as slash bass, rejects lyric fragments like `D/Amazing`, drives auto-scroll from frame timestamps with fractional accumulation, opens a full-screen performance dialog with localized accessibility labels, hides/restores system bars in full-screen, reserves inset-aware space for the close/auto-scroll controls, and uses a compact custom speed slider. | Device-compare full-screen performance layout and very-low-speed auto-scroll against the current iOS behavior; keep the Firestore chord string wire shape unchanged. | `ChordProParserTest`, `SongbookViewModelTest`, full `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `git diff --check` passed 2026-07-01 for the parser/performance port. Follow-up `compileDebugKotlin`, `lintDebug`, `assembleDebug`, and `git diff --check` passed after the layout/compact-slider fix. |

---

## Parity updates from the 2026-06-29 source audit

iOS source reviewed: commit `24d4361` (`Surface member-level point activity
in team views + polish`) plus the 2026-06-29 working-tree extraction of
`TeamListSectionHeader`.

| ID | Status | iOS source / behavior | Android evidence | Required Android result | Verification |
| --- | --- | --- | --- | --- | --- |
| TEAM-002 | DONE | `TeamDetailView.swift`, `PointsHistorySection.swift`, `PenaltiesSection.swift`, `PointHistoryView.swift`, and `RouteDestinationView.swift`: team detail/history include member-targeted positive awards, penalties merge stored team penalties with member-level negative activities, point history resolves team members, and pull-to-refresh reloads teams plus the scoped ledger. | Android now shares member-aware activity filters, includes positive member awards in Team detail and team-scoped Point History, merges member deductions into the Penalties section, passes `TeamViewModel` into team point history, and adds pull-to-refresh to Team detail. | Keep team-targeted negative activities out of the merged penalty list because the stored `TeamPenalty` already represents them. | `TeamActivityFiltersTest`, focused team/Songbook tests, full unit suite, `lintDebug`, `assembleDebug`, locale-key parity, and `git diff --check` passed 2026-06-29. |
| TEAM-003 | DONE | `TeamBalancer.swift`, `TeamAutoBalanceSheet.swift`, and `TeamObserver.swift`: auto-balance is weighted, cancellable, large-camp safe, stale-preview guarded, applies only the previewed selected teams, and caps preview member chips. | Android `TeamBalancer`, `TeamViewModel`, and `TeamsScreen` now use cancellable weighted balancing, async preview state, stale-signature checks, selected-team apply order, capped preview chips, and a localized processing overlay. | Keep preview/apply coupled so operators cannot apply an out-of-date assignment after roster or team selection changes. | Focused balancer/ViewModel tests, full unit suite, `lintDebug`, `assembleDebug`, locale-key parity, and `git diff --check` passed 2026-06-29. |
| SONG-002 | DONE | `SongbookView.swift`: each Songbook row menu exposes Voice Kits tracks and switches to the selected alternative track from the list, not only from detail. | Android `SongbookScreen` now passes active audio state into rows and adds the Voice Kits track group with selected checkmark and per-track playback action. | Keep main-song default playback while allowing any typed alternative track to start directly from the list row. | Focused Songbook ViewModel tests, full unit suite, `lintDebug`, `assembleDebug`, and `git diff --check` passed 2026-06-29. |
| CAMP-005 | VERIFY | `CampingEventSheet.swift` and `FeaturedCampingCard.swift`: about/info card dividers and the featured registration CTA are visually polished. | Android event sheet about/info cards received matching divider/card treatment and the featured CTA is centered without a trailing chevron. | Compare light/dark phone-width Home and event-sheet screenshots against current iOS. | Automated gates passed 2026-06-29; device visual comparison remains. |
| IOS-REF-001 | N/A | iOS-only SwiftUI extraction/polish: `CampzoneTextFieldStyle`, `FlowLayout`, `TeamListSectionHeader`, preview wiring, Xcode project version, SwiftUI state plumbing, comment-only background edits, and the unreferenced `worship` asset. | Android already has Compose/design-system equivalents or no product counterpart; no Firestore, navigation, copy, or user-visible behavior changed. | Record as reviewed so future audits do not reopen it as a missing Android feature. | Source reviewed 2026-06-29. |

---

## Parity gaps from the 2026-06-23 source audit

### P0 — data and behavior correctness

| ID | Status | iOS source / behavior | Android evidence | Required Android result | Verification |
| --- | --- | --- | --- | --- | --- |
| ACT-001 | DONE | `Features/Games/Model/Activity.swift`: `gameID` is optional so manual team/member adjustments can create ledger rows with no game. | Android now models `gameId` as nullable, decodes missing values, and omits absent values from payloads. | Keep game-linked rows unchanged while accepting game-independent manual audit rows. | Payload/decode tests and full unit suite passed 2026-06-23. |
| TEAM-001 | DONE | `TeamMemberScoreSheet.swift`, `TeamDetailView.swift`, `TeamObserver.swift`: signed team/member changes require a non-empty reason and append an immutable manual `Activity` with previous/new score. Penalties also append ledger entries. | Team/member/penalty dialogs now require a reason and write best-effort audit rows after the score mutation. | Keep ledger failure from misreporting an already-persisted score mutation as failed. | Team ViewModel/activity tests and full unit suite passed 2026-06-23. |
| ANN-001 | VERIFY | `AnnouncementObserver.swift` owns one stream task; reload/retry cancels the previous infinite Firestore stream and waits only for its first batch. | `AnnouncementViewModel` now owns one load job, cancels it before restart, and cancels it in `onCleared`. | Device-check retry/re-entry listener ownership; add a direct ViewModel regression test when its Android `Context` dependency is isolated. | Debug compile and full unit suite passed 2026-06-23. |
| ANN-002 | VERIFY | `AnnouncementObserver.saveAnnouncement` dispatches announcement notifications only when creating; edits show “saved” and do not look like a new announcement. | Save captures create-vs-edit before launching and dispatches only for a create. | Device-check create versus edit notification behavior; add a direct dispatcher-count test with the Context isolation work. | Debug compile and full unit suite passed 2026-06-23. |
| HOME-002 | DONE | iOS observes guardian/self registration ownership without requiring a compound `registrationStatus` query and filters approval in app state. | Android Home previously combined ownership and approval in a collection-group query, which failed live without a composite index. It now queries only `userID`/`guardianID`, then filters approved rows locally. | Family pass discovery must work with the deployed indexes and continue excluding pending registrations. | Focused Home tests, all 525 unit tests, lint, debug assembly, and a clean live Home reload passed 2026-06-24. |
| DEEP-001 | DONE | A removed or stale camping link resolves to a terminal not-found state rather than an indefinite loader. | Android's live document observer previously emitted nothing for a confirmed-missing document. It now closes with a typed not-found result and the detail screen renders localized EN/FR/PT/PT-BR recovery copy. | Preserve retry/back navigation and do not classify an unconfirmed cache miss as a missing document. | ViewModel regression test, full suite, and a live `campzone://camping/definitely-missing-camping` emulator check passed 2026-06-24. |
| FOOD-001 | DONE | Current `FoodMenu.swift`, `FoodMenuService.swift`, `FoodMenuItemEditorRow.swift`, and `MealMenuCard.swift` use structured dishes with stable IDs, descriptions, notes, allergen tokens, legacy `dishes[]` fallback, and per-user allergy warnings. | Android previously rendered only dish-name strings. It now prefers and writes additive `items[]` maps while retaining denormalized `dishes[]`, preserves structured items through schedule sync, exposes identity-stable dish editor cards and food-allergen multi-select, and highlights profile matches. | Keep older documents readable and keep newer documents usable by older clients. | Legacy fallback, structured payload, matching, schedule-sync, and ViewModel save/validation tests passed; all 529 tests, lint, debug assembly, and locale-key parity passed 2026-06-24. |
| SONG-001 | DONE | Current `Song.swift`, `SongbookObserver.swift`, `SongEditorView.swift`, and `SongDetailView.swift` support typed multi-track “Voice Kits”: one main song plus playback/instrumental/voice-part/custom tracks, legacy normalization, relabeling, alternative playback, and natural-end state reset. | Android now persists typed `voiceType` plus `displayName`, normalizes legacy tracks, keeps `audio` on the main take, supports local and remote audio, opens the editor label picker, prevents duplicate named kits, promotes a replacement main track, and switches tracks from detail. | Keep the wire contract additive and default playback on the main take. | Model/payload and Songbook ViewModel tests passed; focused Compose compile passed 2026-06-23. |
| ATT-001 | VERIFY | Current `CampingAttendee.swift`, registration mapping, and `AttendeeProfileDetailView.swift` denormalize child `relationship`/`customRelationshipLabel` and show the attendee's guardian, siblings, and dependents as a navigable “Family at camp” section. | Android now carries both fields through participant/attendee payloads and renders navigable guardian, sibling, and dependent rows. | Device-check chained profile navigation and the self/child/fallback relationship captions. | Payload/decode and relationship derivation tests passed 2026-06-23. |

### P1 — missing product behavior and high-visibility parity

| ID | Status | iOS source / behavior | Android evidence | Required Android result | Verification |
| --- | --- | --- | --- | --- | --- |
| CAL-001 | VERIFY | `Features/Schedule/Service/CalendarExportService.swift`, `CampingScheduleView.swift`, and `ProgramDetailView.swift`: export an entire schedule or one program to the system calendar with title, dates, location, and camp context. | Program detail now opens Android's native calendar insert contract; schedule exports a secure multi-event `.ics` handoff without calendar permissions. | Device-check a Calendar-capable phone and a phone with no calendar handler. | Planner, intent-contract, multi-event/escaping tests and full unit suite passed 2026-06-23. |
| CAMP-001 | DONE | `CampingCard.swift` and `CampingEventSheet.swift` calculate inclusive calendar days using start-of-day and `dayDiff + 1`. | Android now normalizes both dates to local midnight and counts inclusively. | Preserve 1-day same-day behavior and DST-safe date iteration. | Same-day, multi-day, Europe/Paris DST tests and full unit suite passed 2026-06-23. |
| HOME-001 | VERIFY | Current `FeaturedCampingCard.swift` renders the featured marker as the arrow icon only. | Android's marker is now the icon-only arrow treatment with a TalkBack label. | Compare dimensions and placement on a phone against current iOS. | Debug compile and preview coverage passed; device comparison remains. |
| FEEDBACK-001 | VERIFY | Current `CampFeedbackResultsView.swift` adds the camping image/avatar to the summary card. | Feedback results now load the camp logo and show a circular identity image with an initial fallback. | Compare loaded-image and fallback states against iOS. | Feedback ViewModel tests and full unit suite passed 2026-06-23; device comparison remains. |
| CAMP-002 | VERIFY | Current `CampingCard.swift` uses `FeaturedMountainBackground` as the no-logo fallback and revised content padding. | Android no-logo cards now use `FeaturedMountainBackground`; inner content padding lets the divider span like iOS. | Compare light/dark phone-width rendering and contrast. | Debug compile and full unit suite passed; device comparison remains. |
| ATT-002 | VERIFY | Current `CampingAttendeesListView.swift` exposes age-group, church, and language filters with localized active-filter chips and clear-all. | Android now exposes church/language selectors sourced from loaded attendees, retains age chips, localizes language names, and keeps clear-all behavior. | Device-check narrow-width chip/dropdown layout and TalkBack labels. | ViewModel source/filter tests and Compose compile passed 2026-06-23. |
| CAMP-003 | DONE | Current `CampingResourceGroup.swift` hides songbook without `viewSongbook`, shows safety/support only to approved participants or camp managers, limits chat/polls/album/menu to approved participants, and limits pricing to managers or approved/pending registrations. | Android now applies each per-resource gate independently, requires approved registration for QR/resources, and gates pricing to managers or approved/pending registrations. | Retain destination-level guards as defense in depth. | Permission/state tests and focused Compose compile passed 2026-06-23. |
| CAMP-004 | DONE | Current `CampingAdminSection.swift` keeps camp management together: pin, recurring camp, lodging, venue map, packing, feedback, cancel, delete, and creator attribution. | Android detail now uses the same two-column action set/order, direct editor routes, destructive confirmations, creator attribution, and ViewModel/service permission guards. | Preserve permission gates and the compact two-column layout. | ViewModel mutation tests, Compose compile, live 1080×2400 odd-count layout, and non-destructive cancel-confirmation QA passed 2026-06-24. |
| ALBUM-002 | DONE | iOS separates `manageAlbumMedia` from `manageAlbumSettings`: photographers moderate media; scoped youth directors, pastors, and leaders edit upload-role policy. | Android previously reused media moderation for settings. It now has the separate permission, role matrix, camping state, and route/UI gate. | Keep upload-role writes unavailable to photographers while retaining their media tools. | Permission matrix, camping state tests, and Compose compile passed 2026-06-23. |

---

## Partial or verification queue

These are not accepted as complete merely because corresponding files exist.

| ID | Status | Surface | Current Android state | Acceptance work still required |
| --- | --- | --- | --- | --- |
| AUD-CAMP-001 | VERIFY | Campings list, card, event sheet, history | Android now carries iOS `publicationStatus`, creates/clones drafts, exposes the explicit publish path, filters drafts with the same `canEditCamping` visibility predicate, groups leadership lists into Drafts/Published/Archived, shows unpublished badges on cards, lets editors open details from list cards, shows admin actions across loading/empty/error/loaded states, uses iOS-like card banner chrome/border weight, and restyles History with organizer count pills, year separators, location rows, and chevrons. The signed-in leadership/light-mode emulator check confirmed the earlier centered header, search, admin actions, month card, event sheet, hero/capacity, Edit access, and CTA. | Complete dark-mode, updated publication-group, History, no-logo, and alternate registration-role screenshots against iOS. |
| AUD-DETAIL-001 | VERIFY | Camping detail/menu | Android now has Overview/Schedule/Teams tabs, grouped resources, management actions, compact approved registration state, cancelled readability, packing/support/safety entries. The leadership emulator pass confirmed real camp loading, grouped Camp Life/Operations rows, family-at-camp, venue/packing cards, management ordering, odd action count, and cancel confirmation. | Complete non-leadership empty/locked states, cancellation rendering, and dark-mode comparison. |
| AUD-HOME-001 | VERIFY | Home dashboard | Adaptive hero, personalized/pinned actions, schedule timeline, announcement carousel, family QR pass deck, safe-area handling, and current icon-only featured marker exist. A 1080×2400 light-mode emulator check confirmed the event hero, QR/checklist top-right pin badges, quick actions, schedule, and clean Home reload after the family-query fix. | Complete alternate registration states, a multi-person QR pager account, pin picker, dark mode, and small-screen layout. |
| AUD-FOOD-001 | VERIFY | Food menu | Structured `items[]` plus legacy `dishes[]`, per-dish details/notes/allergens, attendee allergy awareness, matching warnings, one-day selector, identity-stable editor CRUD, manager participant-allergy dialog, attendee-profile navigation from restriction rows, and schedule sync now match iOS. The existing legacy menu and allergy summary rendered on the emulator before the structured editor patch. | Device-check the newly built structured dish editor, manager allergy dialog, attendee navigation, and a persisted matching-allergen meal once ADB is available again; automated round-trip/sync/localization acceptance is complete. |
| AUD-CHAT-001 | VERIFY | Camping/team chat | Replies, reactions, mentions, media, voice notes, URL taps, edit/report/block/pin/remove, jump/highlight, compact composer, iOS-style non-overlay composer layout, bottom-aware auto-scroll/unseen jump button, swipe-to-reply, optimistic send rollback, anchored long-press action overlay with exit-content retention, springy swipe feedback, animated message placement, and compact expanded-reaction grid are present. Full unit suite, lint, debug assembly, focused compile, and diff check passed on 2026-06-29. | Device-test keyboard/insets, long-press overlay visual geometry, swipe-to-reply feel, attachment upload, reply jump, reaction mutation, and team/camping permission differences. |
| AUD-SONG-001 | VERIFY | Songbook | ChordPro parsing, transposition, local/remote typed Voice Kits, list-row track switching, favorites, pinned theme, editor, detail track switching, iOS-style gradient-stroke artwork, a draggable spring-settling global player with side-peek hide/restore plus next/play/stop controls, shared playback position/duration state, the collapsed chevron anchored to the visible 38dp peek handle with animated collapsed/expanded content, iOS-style bottom dock/side-inset/tucked-edge placement, vertically centered mini-player contents, and the animated iOS-style cream seek bar on the active Song detail hero card. Pixel 8 emulator smoke verified Songbook list/detail rendering, centered mini-player contents, hero-card seek bar, right-edge hide peek arrow, and tap restore. | Device-test audio picker/upload, remote URL playback, natural-end replay, alternative switching, deletion/promotion, row-menu switching, remaining left/right drag matrix, and live hero seek gestures against real media. |
| AUD-TEAM-001 | VERIFY | Teams/games/point history | Team CRUD, roles, reason-required score/penalty mutations, manual audit activities, reveal, scoped activity listeners, member-aware team history, merged member deductions, and Team detail pull-to-refresh exist. | Device-verify all manual adjustments appear once in Team detail and point history with correct visibility, no duplicate deduction, and a successful pull-to-refresh reload. |
| AUD-TRANSPORT-001 | VERIFY | Vehicles/transportation | Vehicle QR, family/manager direct assignment, invitations/requests, driver `offeredSeats` carpool cap, passenger self-removal, one-active-vehicle assignment guards, registration transport step, My Vehicles, scanner flows, and route/workflow localization in EN/FR/PT/PT-BR exist. | Device-test driver/family creation, offered-seat edits, invitation accept/decline, request approval, passenger self-removal, driver removal, duplicate-assignment rejection, transport-intent sync, QR arrival, and all localized entry points. |
| AUD-SCHEDULE-001 | VERIFY | Schedule/program detail | Current-day selection, vertical timeline, editable day titles, custom program types, reminders, food sync, attendance links, calendar actions, iOS-like editor top add action/day-chip rename affordance, all-day overview counts, responsive compact time picker sizing, animated selected-day scroll, and animated chip color/stroke transitions exist. | Verify custom type propagation, reminder replacement, day normalization, selected-day persistence, editor layout against iOS, and both calendar handoffs. |
| AUD-LOC-001 | VERIFY | Localization/accessibility | Base EN plus FR/PT/PT-BR resources cover current implemented slices; family allergy/default checklist localization, Vehicle/Songbook/Schedule strings, Vehicle runtime feedback, and Safety hub validation/broadcast feedback are localized. Translatable EN/FR/PT/PT-BR key parity is clean as of 2026-06-29. | Inspect runtime fallbacks, remove any remaining hardcoded user-visible strings outside the touched slices, audit TalkBack traversal/labels and reduced-motion behavior. |

---

## Implemented Android slices retained in regression scope

These map to current iOS work and have Android implementations. A future iOS
change may move them back to `PARTIAL` or `OPEN`.

| ID | Status | Capability | Android evidence |
| --- | --- | --- | --- |
| REG-001 | DONE | First-time registration uses rule-provable ownership queries and registration-count fallback. | `data/camping/CampingService.kt`; registration tests. |
| FAMILY-001 | DONE | Family participant CRUD, duplicate guard, stable allergy tokens, multi-select/remove, English fallback. | `data/family/*`, `ui/family/*`, `ui/common/Allergies.kt`. |
| PIN-001 | DONE | Only admins can select the app-wide featured camp. | Permission evaluator + camping detail/ViewModel guard. |
| QUICK-001 | DONE | Quick actions rank by use, support up to two role-safe pins, and show the pin badge at tile top-right. | `ui/home/QuickActionUsageStore.kt`, `QuickActionPinStore.kt`, `HomeScreen.kt`. |
| PASS-001 | DONE | Live Home pass supports approved self + family QR pages, team/lodging/photo identity, brightness, and localized pager copy. | `HomeViewModel.kt`, `HomeScreen.kt`, `HomeViewModelTest.kt`. |
| PACK-001 | DONE | Packing template/editor, private progress, custom items, notes, share/import/revoke, and localized starter catalog. | `data/packing/*`, `ui/packing/*`. |
| SUPPORT-001 | DONE | App and camp support/sponsor hubs with role-safe edit flow. | `data/support/*`, `ui/support/*`. |
| SAFETY-001 | DONE | Camp emergency/safety hub, contacts, editable instructions, and urgent broadcast entry. | `data/safety/*`, `ui/safety/*`. |
| ADMIN-001 | DONE | Admin analytics dashboard and Admin Tools entry. | `data/admin/AdminAnalyticsService.kt`, `ui/admin/AdminAnalyticsScreen.kt`. |
| ATTEND-001 | DONE | Per-program attendance records, manual corrections/removal, missing list, and QR scanner. | `data/attendance/*`, `ui/attendance/*`. |
| TEMPLATE-001 | DONE | Recurring camp cloning with shifted schedule, reset teams, copied songbook/guidelines, and no live data. | `data/model/CampingTemplateClone.kt`, `ui/camping/template/*`. |
| DATA-001 | DONE | GDPR/self-service data export and profile entry. | `data/profile/UserDataExportRepository.kt`, `ui/profile/UserDataExportScreen.kt`. |
| ALBUM-001 | DONE | Swipeable full-screen gallery, media metadata, delete/caption actions. | `ui/album/CampingAlbumScreen.kt`, `data/model/MediaItem.kt`. |
| AUTH-001 | DONE | Adaptive sign-in/onboarding/splash presentation and profile-field parity. | `ui/auth/AuthGate.kt`, `ui/onboarding/OnboardingScreen.kt`. |
| NOTIF-001 | DONE | Deep links, scoped feed queries, camping/team channel visibility, and badge/registration/transport actions. | `data/notifications/*`, `ui/notifications/*`, navigation tests. |
| GAME-SCOPE-001 | DONE | Activity listeners issue no query for unauthorized/guardian-only viewers and constrain participants to immediate visibility before reveal. | `data/games/ActivityReadScope.kt`, `GameService.kt`, games tests. |
| HISTORY-001 | DONE | Past/cancelled campings moved to organizer → descending-year History sheet. | `CampingsViewModel.kt`, `CampingsScreen.kt`. |
| PAYMENT-001 | DONE | Stripe PaymentSheet registration/transport/price-item flows, confirmation, and proof/receipt support. | `data/payments/*`, `ui/payments/*`. |

---

## Intentional Android adaptations / non-portable iOS changes

| ID | Status | iOS change | Android treatment |
| --- | --- | --- | --- |
| NATIVE-001 | N/A | SF Symbols browser and `SFSymbolPickerRow`. | Use the curated Material icon catalog and Android picker semantics; preserve stored wire symbols only where shared data requires them. |
| NAV-001 | N/A | `.restoresSwipeBack()` and scoped UIKit navigation-controller behavior. | Use Navigation Compose back handling and system predictive-back behavior. |
| IMAGE-001 | N/A | Kingfisher migration for cached remote images. | Coil already supplies Android memory/disk caching; verify sizing/error placeholders, not library parity. |
| APPLE-001 | N/A | App Store metadata, iOS entitlements, EventKit APIs, UIKit/AVKit implementation details. | Port product behavior only; use Android equivalents and Android release metadata. CAL-001 remains required because calendar export is product behavior. |
| SWIFTUI-001 | N/A | SwiftUI-only extraction of reusable view helpers, preview scaffolding, Xcode project metadata, and comment-only source edits. | Use existing Compose/design-system helpers; port only product behavior, visible styling, data contracts, and navigation changes. |

---

## Known source-of-truth cautions

- Do not blindly port obvious malformed formatting changes. Preserve the product
  meaning and localization (for example, percentage APIs differ between Swift
  and Kotlin and may expect a fraction rather than an integer).
- Firestore optional/omit/null behavior must match the shared schema, not a UI
  helper's convenience type.
- A green compile is not UI parity. High-visibility screens remain `VERIFY`
  until both light/dark and relevant role/state combinations are compared.
- The Android worktree was already heavily modified when this ledger was
  created. Do not overwrite or revert unrelated changes.

## Verification log

| Date | Scope | Result |
| --- | --- | --- |
| 2026-06-23 | Baseline source audit | Created tracker; confirmed ACT-001, TEAM-001, ANN-001, ANN-002, CAL-001, CAMP-001, HOME-001, FEEDBACK-001, and CAMP-002. |
| 2026-06-23 | Existing Home family-pass slice | Focused Home tests, lint, and debug assembly passed before this tracker was created. |
| 2026-06-23 | Correctness + visible-parity implementation slice | Activity/team, announcements, inclusive durations, Home marker, feedback identity, camping no-logo banner, and calendar export implemented. Focused tests and full `testDebugUnitTest` passed. |
| 2026-06-23 | Expanded source-gap closure | Added attendee relationships/family navigation and filters, Camping resource/management parity, typed Songbook Voice Kits plus remote audio, and the separate album-settings permission. All 525 unit tests, lint, debug assembly, resource-name parity, and diff checks passed. |
| 2026-06-24 | Live emulator and final regression pass | Verified Home, quick-action pin placement, Campings list/card/event sheet, and leadership controls on a 1080×2400 English/light emulator. Removed the composite-index dependency from family pass discovery and replaced the stale-camping infinite loader with localized not-found recovery. No app fatal, Firestore index, or permission failure remained. All 525 unit tests, lint, debug assembly, EN/FR/PT/PT-BR resource-name parity, and `git diff --check` passed. |
| 2026-06-24 | Structured Camp Menu closure | A deeper iOS model/UI comparison reopened the menu contract: Android still used string-only dishes. Added the additive structured dish model, legacy fallback, per-dish editor/allergens/profile warnings, schedule-sync preservation, schema docs, and localized copy. Focused tests and APK assembly passed; final verification passed all 529 unit tests, lint, debug assembly, locale-key parity, and `git diff --check`. ADB daemon startup became unavailable after the permission-profile change, so the newly built editor remains explicitly device-VERIFY. |
| 2026-06-29 | iOS `24d4361` team scoring + polish parity | Ported member-aware team points/history, merged member deductions, Team detail pull-to-refresh, cancellable auto-balance preview/apply guards, Songbook row Voice Kits picker, event-sheet/card CTA polish, and reviewed late SwiftUI-only component extraction. Focused tests, full `testDebugUnitTest`, `lintDebug`, `assembleDebug`, EN/FR/PT/PT-BR locale-key parity, and `git diff --check` passed. |
| 2026-06-29 | Songbook/Schedule/Vehicle/Splash spot parity | Ported iOS gradient-stroke Songbook artwork, draggable side-peek floating player with next control, Schedule editor day-chip/top-action polish, responsive time picker sizing, broad Vehicle screen localization, and richer iOS-style splash scene/tagline. `git diff --check`, `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` passed with Android Studio JBR; device visual/gesture comparison remains VERIFY. |
| 2026-06-29 | Songbook playback hero follow-up | Added Songbook playback position/duration state, iOS-aligned collapsed floating-player chevron placement on the visible side peek, corrected the scrubber placement to the active Song detail hero card with the iOS cream capsule, knob, and time labels, and fixed floating-player dock/edge placement so the card no longer centers or tucks against a padded content edge. Removed stale bottom padding so mini-player artwork/text/buttons are vertically centered. Scoped `git diff --check`, translatable locale-key parity, `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` passed after the seek correction; focused `:app:compileDebugKotlin` passed after the floating-player position and content-centering corrections. |
| 2026-06-29 | Animation/localization parity follow-up | Added spring/peek continuity and collapsed-content animation to the Songbook floating player, animated the Song detail hero seek-bar reveal, added Schedule editor selected-day scroll and chip color/stroke animation, retained Chat overlay content during exit with spring swipe feedback and animated message placement, added iOS-style Splash pulse/twinkle/fire motion, and localized Vehicle plus Safety runtime feedback across EN/FR/PT/PT-BR. `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, translatable locale-key parity, full `git diff --check`, debug install/launch on Pixel 8 emulator, Songbook list/detail screenshots, and right-edge mini-player hide/restore passed with Android Studio JBR. |
| 2026-07-01 | Songbook parser/performance parity | Ported the iOS/WorshipPlus chord parser behavior into Android Songbook parsing and transposition, fixed sub-0.4x auto-scroll by accumulating frame-time fractional movement, and added full-screen performance mode. Follow-up layout parity reserves bottom space for auto-scroll controls, keeps the full-screen close button clear of transpose controls, hides/restores Android system bars, and replaces the oversized Material speed slider with a compact custom control. Focused parser and Songbook tests, full unit suite, `lintDebug`, `assembleDebug`, and `git diff --check` passed for the parser port; `compileDebugKotlin`, `lintDebug`, `assembleDebug`, and `git diff --check` passed after the layout fix. |
| 2026-07-02 | iOS working-tree parity implementation pass | Ported the iOS vehicle `offeredSeats` carpool cap into Android model/service/ViewModel/form UI, exposed the iOS participant-allergy manager list in Android Food Menu, documented the vehicle schema/RBAC contract, and confirmed checklist-share deep links/notifications already matched Android. Fixed the Android lint locale issue in the new allergy row and cleaned iOS whitespace plus a Food Menu accessibility typo. Full Android `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`, Android `git diff --check`, iOS app build, targeted iOS unit slices, iOS string-catalog JSON parse, iOS `git diff --check`, and Firebase RBAC tests passed. |

## Change log

### 2026-06-23 — initial baseline

- Inventoried the current 177-file iOS working-tree diff by feature.
- Compared it with the Android dirty tree and existing project status.
- Separated confirmed gaps from already-implemented slices and iOS-only changes.
- Established stable IDs and acceptance criteria so future work can update this
  file instead of restarting the audit.

### 2026-06-23 — first implementation closure

- Closed every concrete implementation gap in the baseline source audit.
- Added optional game-independent activity payloads and reason-required manual
  team/member/penalty audit rows.
- Stopped announcement listener stacking and edit-time notification dispatch.
- Matched inclusive camp-day arithmetic, the current Home featured marker,
  feedback camp identity, and camping-card no-logo treatment.
- Added permission-free Android calendar export: native insert for one program
  and a secure multi-event calendar file for the full schedule.
- Kept UI/device acceptance items as `VERIFY`; source implementation is not
  treated as proof of visual or external-app behavior.

### 2026-06-23 — expanded audit reopened

- Reopened every broad `VERIFY` row after the owner confirmed that the first
  source-diff pass was incomplete.
- Confirmed five additional implementation gaps: typed Songbook voice kits,
  attendee relationship/family data, attendee church/language filter controls,
  Camping resource visibility, and the Camping detail management action set.
- The summary's open count now reflects confirmed work only; it is intentionally
  updated upward when deeper comparisons find more instead of pretending a
  green build means full parity.

### 2026-06-23 — expanded implementation closure

- Closed the five reopened source gaps and the subsequently discovered album
  settings permission split.
- Added typed Songbook Voice Kits end to end, including legacy normalization,
  main-track denormalization, local/remote audio labeling, duplicate prevention,
  alternative playback, and natural-end replay state.
- Added attendee relationship payloads, family-at-camp navigation, and reachable
  church/language roster filters.
- Matched Camping resource visibility and the full iOS management action grid,
  including destructive confirmations and direct lodging/venue/checklist routes.
- Completed EN/FR/PT/PT-BR strings for the new slices and filled previously
  missing European-Portuguese chat/reminder resources.
- Kept visual/device acceptance rows at `VERIFY`; zero open source gaps is not a
  claim that external Calendar apps or every physical-device layout was tested.

### 2026-06-24 — live runtime closure

- Re-ran the current iOS working-tree inventory; no iOS source file changed
  after the audited 2026-06-23 snapshot.
- Verified the English/light Home and Campings/event-sheet surfaces on the
  signed-in 1080×2400 emulator, including iOS-style quick-action pin placement.
- Fixed family pass discovery to avoid an undeployed composite Firestore index
  while retaining approved-only self and guardian registrations.
- Fixed stale/removed camping links so a server-confirmed missing document
  reaches localized not-found recovery instead of loading indefinitely.
- Re-ran the complete Android unit, lint, assembly, localization-key, and diff
  integrity gates after both runtime fixes.
- Reopened the food-menu comparison after live UI inspection exposed that the
  Android model still flattened iOS structured dishes to names. Ported the
  complete additive wire contract, identity-stable editor, per-dish allergen
  picker and warnings, legacy fallback, schedule preservation, and four-locale
  copy. The final suite now contains 529 passing tests.

### 2026-06-29 — iOS team scoring and polish parity

- Audited iOS commit `24d4361` and the same-day `TeamListSectionHeader`
  extraction before closing the Android task.
- Ported member-aware team scoring visibility: positive member awards now
  appear in Team detail and team-scoped Point History, and negative member
  deductions merge into the Penalties section without duplicating team-level
  penalty ledger rows.
- Added Team detail pull-to-refresh for the team roster/score plus the scoped
  game activity ledger.
- Ported the auto-balance preview refinements, stale-preview guard, selected
  apply order, capped preview chips, and processing overlay.
- Added the Songbook list-row Voice Kits picker and the latest Campings/Home
  visual polish. SwiftUI-only extraction, preview, project, and comment changes
  were reviewed as N/A for Android.

### 2026-06-29 — Campings publication and history parity

- Ported the iOS camping publication lifecycle into Android: missing
  `publicationStatus` decodes as published, new/recurring campings save as
  drafts, normal saves preserve visibility, and detail exposes the explicit
  draft publish action.
- Updated Campings list parity: managers see Drafts / Published / Archived
  sections from the same filtered visible campings as search, unpublished cards
  show publication badges, archived groups are de-emphasized, and editors open
  camp detail directly from cards like iOS.
- Updated Camping History to mirror iOS organizer/year grouping more closely:
  organizer count pill, year divider pill, grouped rounded rows, location line,
  registration status badge, and chevron.
- Verification: focused camping/publication/dashboard/admin tests passed, full
  `:app:testDebugUnitTest` passed, and `git diff --check` passed. Visual/device
  screenshot parity remains VERIFY.

### 2026-06-29 — Chat overlay parity continuation

- Re-audited iOS `ChatTimelineView`, `ChatMessageRow`, `ChatComposer`, and
  `MessageActionOverlay` against Android `ChatScreen`/`ChatComposer`.
- Updated Android long-press actions to carry the selected bubble's root
  position into the overlay, then clamp the reaction/action cluster near that
  message instead of always centering it.
- Wrapped expanded reactions into a compact two-row grid to match the iOS
  reaction picker behavior and avoid wide-row overflow.
- Verification: `git diff --check`, strings-only EN/FR/PT/PT-BR resource parity,
  `:app:compileDebugKotlin`, and `:app:testDebugUnitTest`
  `:app:lintDebug` `:app:assembleDebug` passed after this continuation.

### 2026-06-29 — current iOS working-tree parity sweep

- Audited the current iOS dirty tree feature by feature against Android instead
  of trusting the existing tracker labels.
- Venue Map: ported the 120-location cap helpers, editor capacity copy/disabled
  add actions, viewer search/category filtering, no-match state, illustration
  zoom/reset behavior, selected-pin-only labels, GPX capacity truncation, and
  the iOS-style GPX preview sheet with category assignment and selectable
  waypoints.
- Schedule: matched Program Detail's generic navigation title, title-in-content
  header, break/rest details order, hidden empty location row, meal venue label,
  dark/light card treatment, and accent-colored info icons.
- Teams/Games: matched iOS activity/penalty row treatment, moved Team detail
  manual score and penalty controls into focused bottom sheets with mandatory
  reasons and quick amount chips, and confirmed Android already had the
  awaiting-games podium placeholder and corrected reveal confetti condition.
- Camping/Packing polish: mirrored the iOS light-mode white card treatment for
  Camping detail description/event/resource/attendee cards, Registration Review
  cards, Packing progress/category/notes cards, and the My Packing Checklist
  overview card. SwiftUI-only `.buttonStyle(.appStore)` changes were reviewed
  as already covered by Android Material press feedback.
- Incidental compile fix: corrected the dirty Songbook floating player
  `.align(alignment = .center)` Swift-ism to `Alignment.Center` so verification
  could complete.
- Verification: focused
  `./gradlew testDebugUnitTest --tests 'fr.ziyon.campzone.ui.teams.*' --tests 'fr.ziyon.campzone.ui.games.*' --tests 'fr.ziyon.campzone.ui.schedule.*' --tests 'fr.ziyon.campzone.ui.venuemap.*' --console=plain`
  passed with Android Studio JBR; `git diff --check`, full
  `./gradlew testDebugUnitTest --console=plain`, `./gradlew lintDebug
  assembleDebug --console=plain`, and EN/FR/PT/PT-BR translatable string-key
  parity also passed.
