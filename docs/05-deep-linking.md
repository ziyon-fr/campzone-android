# Deep Linking & Routing (Canonical)

> The `campzone://` custom scheme and FCM push payload → destination
> mapping are a shared contract: a shared/announced link or a tapped
> push must resolve to the **same content** on iOS, web, and Android.
> Web additionally needs real HTTPS routes (a browser can’t open
> `campzone://`), so this doc defines the canonical web URL mapping too.
>
> Source: `Campzone/Core/Route/CampzoneDeepLink.swift`,
> `RouteSystem.swift`, FCM payloads from `ziyon-notification-service.js`.

---

## 1. `CampzoneDeepLink` cases

```js
announcement(id)
campingChat(campingID)
teamChat(campingID, teamID)
poll(campingID, pollID?)
teamUpdate(campingID, teamID)
teamPoints(campingID, teamID?)
registrationReview(campingID)   // leadership: pending registrations
achievements(userID, displayName?, photoURLString?, campingID?)
achievement(userID, achievementID, ...)
scheduleProgram(campingID, programID)
transportation(campingID)
transportationJoin(campingID, invitationCode)
transportationInvitation(campingID, vehicleID, registrationID)
transportationRequest(campingID, vehicleID, registrationID)
camping(id)
```

## 2. FCM push payload → destination

A tapped push carries a `data` map (see `04-backend-api.md` §3). Resolve
**case-insensitively**:

1. First, accept a direct URL from any of `deepLink`, `deeplink`,
   `deep_link`, `deepLinkURL`, `deepLinkUrl`, `url`, or `link`. Parse it
   with the same URL rules in §3.
2. Read `type` (or fall back to `kind`), lowercased; plus `campingID`,
   `teamID`.
3. Switch:
   - `announcement` → requires `announcementID` → `announcement(id)`.
   - `chat_message` / `chatmessage` → requires `campingID`;
     `teamChat(campingID, teamID)` if `teamID` present, else
     `campingChat(campingID)`.
   - `chat_mention` / `chatmention` → same as chat message.
   - `poll` → requires `campingID` → `poll(campingID, pollID?)`.
   - `registration` / `registration_request` → requires `campingID` →
     `registrationReview(campingID)` unless `event == "approved"`, which
     routes to `camping(campingID)`.
   - `badge` / `achievement` / `achievement_badge` → requires
     `recipientUserID` (or `userID`/`uid`) →
     `achievement(...)` when `achievementID`/`badgeID` is present,
     otherwise `achievements(...)`.
   - `schedule_reminder` → `scheduleProgram(campingID, programID)` when
     both ids exist, otherwise `camping(campingID)`.
   - `transportation` → invitation events open the passenger
     accept/decline sheet; join requests open the driver's
     approve/decline sheet; other events open My Transportation.
   - `team_update` / `teamupdate` → requires `campingID`; if `event` is
     `scoreChanged`, `memberScoreChanged`, or `penaltyApplied`, route to
     `teamPoints(campingID, teamID?)`; otherwise route to
     `teamUpdate(campingID, teamID)` when `teamID` exists, or
     `camping(id)` when it does not.
   - **no/unknown type** → infer: `announcementID` → announcement;
     else `messageID` + `campingID` → (team)chat; else `pollID` +
     `campingID` → poll; else `campingID` → `camping(id)`; else ignore.

Backend dispatch `data` keys you can rely on: `type`, `appID`,
`campingID`, `teamID`, `pollID`, `announcementID`, `messageID`,
`senderID`, `senderUid`, `event`, `role`, `participantName`,
`requestedByName`, `participantCount`, `achievementID`,
`achievementTitle`, `recipientUserID`, `recipientDisplayName`,
`recipientPhotoURLString`, and optional direct deep-link URL keys listed
above. (All values are strings - the backend stringifies the FCM `data`
map.)

Cold-start: park the link until the router/auth is ready, then consume
once (iOS uses a `DeepLinkInbox`; web = a query param / `sessionStorage`
handoff after auth; Android = an `Intent` extra processed post-auth).

## 3. URL parsing + share links

The legacy custom scheme is `campzone`. iOS also parses HTTPS Universal
Links on `https://campzone-web.vercel.app/...`. Camping, announcement,
achievement, and transportation invitation-code links are shareable; chat, polls,
team updates, point history, and registration review links
are deep-link destinations only and have **no** canonical share URL.

| Link | Inbound parse | Canonical share URL |
| --- | --- | --- |
| camping | route/host `camping`/`campings`, id = first path component or `?id`/`?c`/`?campingID` | `https://campzone-web.vercel.app/campings/<id>` |
| announcement | route/host `announcement`/`announcements`, id = first path component or `?id`/`?announcementID` | `https://campzone-web.vercel.app/announcements/<id>` |
| camping chat | route/host `chat`/`camping-chat`, camping id = first path component or `?id`/`?c`/`?campingID` | none |
| team chat | route/host `team-chat`, `teamID` = first path component or `?teamID`/`?t`, camping id = `?c`/`?campingID` | none |
| team update | route/host `team`/`teams`, `teamID` = first path component or `?teamID`/`?t`, camping id = `?c`/`?campingID` | none |
| team points | route/host `points`/`point-history`, camping id = first path component or `?id`/`?c`/`?campingID`, optional `?teamID`/`?t` | none |
| poll | route/host `poll`/`polls`, camping id = `?c`/`?campingID` or first path component, optional `?pollID`/`?p` | none |
| registration review | route/host `registration`/`registration-review`, camping id = first path component or `?id`/`?c`/`?campingID` | none |
| achievements/badge | route/host `achievement`/`achievements`/`badge`/`badges`, user id = first path component, optional `?achievementID`/`?badgeID`/`?a` | `https://campzone-web.vercel.app/badges/<userID>?achievementID=<id>` |
| transportation join | route/host `transportation-join`, camping id = first path component, code = `?code`/`?invitationCode`/`?i` | `https://campzone-web.vercel.app/transportation-join/<campingID>?code=<code>` |
| transportation decision | route/host `transportation-invitation` or `transportation-request`, vehicle id = first path component, camping/registration ids in query | none |
| packing share | route/host `packing-share`, share id = first path component or `?shareID`/`?s`, camping id = `?c`/`?campingID` | `https://campzone-web.vercel.app/packing-share/<shareID>?c=<campingID>` |

iOS registers both the `campzone` scheme in `Info.plist` and the
`applinks:campzone-web.vercel.app` Associated Domain entitlement.
Android must register an `intent-filter` with
`<data android:scheme="campzone"/>` and HTTPS app links for the same
host. Web cannot register a custom scheme, so cross-platform share links
must be HTTPS URLs.

## 4. Canonical web routes (Next.js App Router)

Map every destination to a real route so shared links and the web app
work in a browser. Recommended structure:

| Destination | Web route |
| --- | --- |
| `camping(id)` | `/campings/[id]` |
| `announcement(id)` | `/announcements/[id]` |
| `campingChat(campingID)` | `/campings/[id]/chat` |
| `teamChat(campingID, teamID)` | `/campings/[id]/teams/[teamId]/chat` |
| `teamUpdate(campingID, teamID)` | `/campings/[id]/teams/[teamId]` |
| `teamPoints(campingID, teamID?)` | `/campings/[id]/teams/[teamId]` or `/campings/[id]` |
| `poll(campingID, pollID)` | `/campings/[id]/polls/[pollId]` (or `/campings/[id]/polls` when no id) |
| `registrationReview(campingID)` | `/campings/[id]` (admin section shows pending) |
| `achievements(userID, ...)` | `/badges/[userId]` |
| `scheduleProgram(campingID, programID)` | `/campings/[id]?programID=...` |
| `transportation(campingID)` | `/campings/[id]?transportation=true` |
| `packingShare(campingID, shareID)` | `/packing-share/[shareId]?c=<campingID>` |
| Home / Campings / Announcements / Profile | `/`, `/campings`, `/announcements`, `/profile` |

Android: a single-Activity Compose nav graph mirroring the same logical
routes (sealed `AppRoute` like iOS). Keep route enums strongly typed on
every platform (no raw strings) - parity with the iOS `AppRoute`.

## 5. iOS route stack reference (for navigation parity)

When a deep link resolves, iOS pushes a **stack** so the back gesture
lands sensibly. Mirror this:

- `announcement(id)` → tab Announcements → `[announcementDetail(id)]`
- `camping(id)` → tab Campings → `[campingDetail(id)]`
- `campingChat` → `[campingDetail, campingChat]`
- `teamChat` → `[campingDetail, teamDetail, teamChat]`
- `teamUpdate` → `[campingDetail, teamDetail]`
- `teamPoints` → `[campingDetail, pointHistory]`
- `poll(id?)` → `[campingDetail, pollDetail]` or
  `[campingDetail, campingPolls]`
- `registrationReview` → focused registration review for that camping
- `achievements` → tab Profile → `[achievements]`; exact badge links open its detail sheet
- `scheduleProgram` → `[campingDetail, scheduleProgram]`
- `transportation` → `[campingDetail, myTransportation]`; invitation and
  request links additionally open the relevant decision bottom sheet
- `packingShare` → `[campingDetail, packingChecklistImport]`

Tabs: **Home, Campings, Announcements, Profile/Settings** (same four on
every platform).
