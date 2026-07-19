# Feature Parity Matrix & Build Order (Canonical)

> The shipped iOS app is the feature reference. This is the full surface
> web/Android must reach for parity, in a sensible build order, each
> mapped to the Firestore collection(s) and backend endpoint(s) it
> touches. Source: iOS `TODO.md` (Phases 0–14, P0–P5, F1–F3) +
> `Features/` slices. Treat anything not started here as “net-new on
> this platform - must match the iOS data contract”.

Legend: collections referenced are defined in `02-firestore-schema.md`;
endpoints in `04-backend-api.md`; gates in `03-rbac-and-security.md`.

---

## Phase A - Foundation (build first)

| Capability | Data / API | Notes |
| --- | --- | --- |
| Firebase init (Auth, Firestore w/ offline persistence) | - | Apple + Google providers, same project. Enable Firestore local persistence/cache (web: `persistentLocalCache`; Android: default + `setPersistenceEnabled`) |
| Auth + session | `users/{uid}` | first sign-in creates the doc (role `user`); see §2 schema. iOS requests notif permission **after** onboarding, not at launch |
| Onboarding | `users/{uid}` | collect age, church, preferred language, gender; derive `ageGroup`; set `onboardingCompleted` |
| Profile view/edit + account-deletion flags | `users/{uid}` (+ denormalization fan-out §10) | 30-day deletion grace flag; purge is server-side |
| Family participants (CRUD) | `users/{uid}/children/{id}` | role `adult`/`admin`; full CRUD mandatory |
| Design system + routing shell | - | tokens (`06`), 4 tabs, typed routes, loading/empty/error states |
| Permission evaluator (client gate) | - | mirror `03` matrix + church-scope exactly |

## Phase B - Core content

| Capability | Data / API |
| --- | --- |
| Campings list (active/upcoming plus History grouped by organizer/year) + detail | `campings/{id}` (+ `registrations` for counts) |
| Camp filters (church/age group/language), search | `campings` |
| Admin create/edit/cancel camping + logo + fees/age-prices/price-items/transportation options | `campings/{id}` (+ Cloudinary sign for logo) |
| Registration flow (self + family) + transportation choice + waitlist | `registrations/{attendeeId}`, `transportationBookings`; dispatch `registration` |
| Registration review/approve/reject + delete-attendee cascade | `registrations` (status), cascades to `checkIns`/`transportationBookings`/team `members` |
| Schedule (days/programs) + admin editor + reminder timing | `schedule/config`, `.../days`, `.../programs` |
| Food menu with structured dishes, per-dish allergen warnings, and Menu↔Program two-way sync | `foodMenu.items` + legacy `dishes`, generated `programs` (`02` §4.4–4.5) |
| Announcements timeline + detail + admin composer + image/PDF attachments | `announcements`; Cloudinary; dispatch `announcement` |
| Songbook (list/detail, lyrics+chords render, audio, favorites, reorder, pinned theme) | `campings/{id}/songs` (admin writes) |
| Camp guidelines (markdown) | `campings/{id}/guidelines` |

## Phase C - Engagement

| Capability | Data / API |
| --- | --- |
| Teams (list/ranking/detail), members, captain/vice, scores, penalties, auto-balance | `campings/{id}/teams` (rewrite full doc + `memberUserIDs`) |
| Games + point rules + award points + immutable activity audit | `games`, `activities` |
| Winner reveal policy + ceremony | camping `winnerRevealPolicy` (reveal gate) |
| Camping chat + team chat + staff-role private chat (pin/report/soft-delete) + per-user block | `chat`, `teams/{id}/chat`, `staffRoles/{id}/chat`, `users/{uid}/blockedUsers`; dispatch `chat` |
| Organizer-defined camping staff roles (games, kitchen, cleaning, reception, worship, logistics, media, safety, prayer, custom) with self-service CRUD and private chat | `campings/{id}/staffRoles` (rewrite full doc + `memberUserIDs`) |
| Live polls (create/vote/results, transactional vote) | `polls`, `polls/{id}/votes/{voterId}`; dispatch `poll` |
| QR check-in (scanner + records) | `checkIns/{attendeeId}`; QR payload in `05` |
| Badges/achievements (read-only display) | `users/{uid}/badges` (backend-awarded) |
| Album media (photo/video upload by role) | `media`, `albumSettings/default`; Cloudinary |
| Content moderation (report + admin queue) | `contentReports` (brittle read - validate) |
| Notifications (FCM token, settings, in-app feed, deep links) | `02` §2.2/2.3/6.5, `04` §3, `05` |
| Analytics events | Firebase Analytics (web: GA4; Android: Firebase Analytics) - events: viewCamping, registerForCamping, cancelCamping, viewSchedule, viewSongbook, viewTeams, playSong, favoriteSong, searchCampings, signIn, signOut |

## Phase D - Operations & growth (F-series)

| Capability | Data / API |
| --- | --- |
| Transportation tickets + admin scanner/boarding | `transportationBookings`; QR in `05`; manager gate |
| Stripe payments (registration/bus/price-item) + auto-approve | `/payments/intent`+`/payments/confirm`; `02` §7.8 |
| Lodging / tent assignment + “My Lodging” | `campings/{id}/lodging/{unitId}` |
| Post-camp feedback survey + admin results | `campings/{id}/feedback/{uid}` |
| Venue map (illustration + MapKit/Map overlay, pins) + program↔venue link | `campings/{id}/venueMap/config`; program `venuePointID` |
| Personal packing checklist + leadership template editor + shared-list import | `campings/{id}/packingChecklistTemplate/config`, `packingChecklists/{uid}`, `packingShares/{shareId}` |
| App/camp support hub + sponsor acknowledgements | `support/appDevelopment`, `campings/{id}/support/config` |
| Guardian “Family at Camp” live updates (read-only aggregate) | composes `registrations`+`checkIns`+`teams`+schedule (no new collection) |
| Admin tools hub / onboarding checklist / moderation queue | - (UI over the above) |
| F3 shipped / backlog | Shipped on iOS + Android: recurring camp templates, per-program attendance, analytics dashboard, emergency hub, packing checklist, and support/sponsor surface. Remaining backlog: offline write queue, calendar export (EventKit→ICS on web), GDPR export |

## Cross-cutting (every phase)

- **Full CRUD is mandatory**: anything that can Create must ship working
  Read/Update/Delete with real UI + matching Security Rule parity. A
  create-only feature is incomplete (iOS `CLAUDE.md` rule 8).
- Offline: Firestore persistent cache is the offline strategy
  (songbook, schedule, camp program prioritized). No SwiftData
  equivalent needed; web = IndexedDB persistence, Android = Firestore
  disk persistence.
- Localization: PT + FR (English keys). Web: `next-intl`/ICU; Android:
  string resources + locale qualifiers.
- Accessibility: dynamic type/font scale, labels/content descriptions,
  contrast, large targets, reduced motion.
- Feedback: pair haptics (where available) + sound on success/error/
  warning, like the iOS `SoundFeedback`/`HapticFeedback` rule.

---

## Achievement badge catalog (Firestore display source + in-code fallback)

`users/{uid}/badges/{id}` only stores earned badges (backend-written);
the display catalog now lives in top-level `badges/{id}` and includes
localized copy at `localizations.{en,fr,pt-BR}.{title,summary,detail}`.
The in-app `AchievementCatalog` remains the required fallback mirror and
unknown-ID filter, and must stay aligned with iOS + backend
`badge-evaluator`. `rarity` in `common,uncommon,rare,epic,legendary`;
`awardKind` in `manual,automatic`.

**Automatic** (backend `badge-evaluator`, see `04` §6): `first-adventure`
(common), `camp-check-in` (common), `team-roster` (common),
`trail-veteran` (uncommon), `score-spark` (uncommon), `team-captain`
(rare), `perfect-attendance` (legendary).

**Common (manual)**: `tent-ready`, `morning-circle`, `meal-line-helper`,
`song-circle`, `good-neighbor`, `trail-cleanup`, `memory-maker`.
**Uncommon (manual)**: `team-player`, `prayer-partner`, `kitchen-helper`,
`clean-camp-champion`, `flag-circle`, `night-watch-helper`,
`workshop-learner`, `welcome-crew`.
**Rare (manual)**: `camp-mentor`, `points-builder`, `service-squad`,
`activity-leader`, `peacemaker`, `language-bridge`, `camp-storyteller`,
`steady-servant`, `voice-of-praise`.
**Epic (manual)**: `early-riser`, `shepherd`, `perfect-day`,
`team-builder`, `challenge-champion`, `worship-lead`, `check-in-hero`,
`mission-maker`, `legacy-helper`, `all-camp-spirit`.
**Legendary (manual)**: `grand-camp-champion`, `servant-leader`,
`season-shepherd`, `campfire-legend`.

(45 total: 10 common, 10 uncommon, 10 rare, 10 epic, 5 legendary -
`first-adventure`/`camp-check-in`/`team-roster` common,
`trail-veteran`/`score-spark` uncommon, `team-captain` rare,
`perfect-attendance` legendary are the automatic ones.) `BadgeTint`
raws: `ember,amber,pine,sky,rose,gold` (`sky`&`pine`→pine color,
`rose`→error, `gold`→warning).
