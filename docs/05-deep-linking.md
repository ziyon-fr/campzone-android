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

```
announcement(id)
campingChat(campingID)
teamChat(campingID, teamID)
poll(campingID, pollID?)
registrationReview(campingID)   // leadership: pending registrations
camping(id)
```

## 2. FCM push payload → destination

A tapped push carries a `data` map (see `04-backend-api.md` §3). Resolve
**case-insensitively**:

1. Read `type` (or fall back to `kind`), lowercased; plus `campingID`,
   `teamID`.
2. Switch:
   - `announcement` → requires `announcementID` → `announcement(id)`.
   - `chat_message` / `chatmessage` → requires `campingID`;
     `teamChat(campingID, teamID)` if `teamID` present, else
     `campingChat(campingID)`.
   - `poll` → requires `campingID` → `poll(campingID, pollID?)`.
   - `registration` / `registration_request` → requires `campingID` →
     `registrationReview(campingID)`.
   - **no/unknown type** → infer: `announcementID` → announcement;
     else `messageID` + `campingID` → (team)chat; else `pollID` +
     `campingID` → poll; else `campingID` → `camping(id)`; else ignore.

Backend dispatch `data` keys you can rely on: `type`, `appID`,
`campingID`, `teamID`, `pollID`, `announcementID`, `messageID`,
`senderID`, `senderUid`, `event`, `role`, `participantName`,
`requestedByName`, `participantCount`. (All values are strings - the
backend stringifies the FCM `data` map.)

Cold-start: park the link until the router/auth is ready, then consume
once (iOS uses a `DeepLinkInbox`; web = a query param / `sessionStorage`
handoff after auth; Android = an `Intent` extra processed post-auth).

## 3. `campzone://` URL scheme (shared links)

Scheme = `campzone`. Only **camping** and **announcement** are
meaningfully shareable; chat/poll/registrationReview have **no**
shareable URL (return nothing).

| Link | Inbound parse | Canonical share URL |
|---|---|---|
| camping | host `camping`/`campings`, id = first path component or `?id`/`?c`/`?campingID` | `campzone://camping/<id>` |
| announcement | host `announcement`/`announcements`, id = first path component or `?id`/`?announcementID` | `campzone://announcement/<id>` |

iOS registers the `campzone` scheme in `Info.plist` and handles
`onOpenURL`. Android must register an `intent-filter` with
`<data android:scheme="campzone"/>` on the main activity. Web cannot
register a custom scheme - use HTTPS routes (next section) and, when
generating a share link for cross-platform recipients, prefer the
HTTPS URL (it can include a smart-banner / app-open fallback).

## 4. Canonical web routes (Next.js App Router)

Map every destination to a real route so shared links and the web app
work in a browser. Recommended structure:

| Destination | Web route |
|---|---|
| `camping(id)` | `/campings/[id]` |
| `announcement(id)` | `/announcements/[id]` |
| `campingChat(campingID)` | `/campings/[id]/chat` |
| `teamChat(campingID, teamID)` | `/campings/[id]/teams/[teamId]/chat` |
| `poll(campingID, pollID)` | `/campings/[id]/polls/[pollId]` (or `/campings/[id]/polls` when no id) |
| `registrationReview(campingID)` | `/campings/[id]` (admin section shows pending) |
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
- `poll(id?)` → `[campingDetail, pollDetail]` or
  `[campingDetail, campingPolls]`
- `registrationReview` → `[campingDetail]` (admin section)

Tabs: **Home, Campings, Announcements, Profile/Settings** (same four on
every platform).
