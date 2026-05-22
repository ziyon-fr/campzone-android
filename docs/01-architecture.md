# Android Architecture — Jetpack Compose (Platform-Specific)

> How the **Android** client implements the shared Campzone
> architecture. The data contract is fixed elsewhere — see `02`–`08`.
> This doc covers the app’s structure, MVVM layering in
> Compose/Kotlin, Firebase wiring, and conventions. Mirror the iOS
> feature-folder layout and the layered contract.

Stack (from `build.gradle.kts` / `libs.versions.toml`): **Kotlin
2.2.10**, **Jetpack Compose** (BOM 2026.02.01) + **Material 3**,
`compileSdk 36`, `minSdk 24`, `targetSdk 36`, AGP 9.2.1, single-Activity.
Package currently `com.example.campzone` (finalize the real application
id before any release — keep consistent with the Firebase Android app’s
package name).

Dependencies to add (not yet in the scaffold): Firebase BoM
(`firebase-auth`, `firebase-firestore`, `firebase-messaging`,
`firebase-analytics`), Google Identity / Credential Manager + Sign in
with Apple (Firebase `OAuthProvider`), Coil (Cloudinary image/video
thumbs), Navigation-Compose, a DI framework (Hilt recommended),
kotlinx-coroutines, and the Stripe Android SDK (PaymentSheet). See
`09-setup.md`.

---

## 1. Layering (MVVM — matches the iOS contract)

iOS **View → Observer → Service → Model** maps to Android
**Composable → ViewModel → Repository/Service → Model**:

- **Model** (`data/model/`): Kotlin `data class`es that encode the
  **exact Firestore wire shape** of `02-firestore-schema.md` — enum
  classes with explicit wire strings, nullability, and the
  delete/null/omit distinctions. **Do not rely on Firestore POJO
  auto-mapping** (`toObject<T>()` / `@DocumentId`): it cannot express
  `FieldValue.delete()` vs explicit `null` vs omit, the per-field
  timestamp variants, the legacy alias reads, or the deterministic id
  rules. Map manually via `Map<String, Any?>` in the service layer,
  exactly like the iOS `payload(for:)` / `init(from:)`.
- **Service/Repository** (`data/service/`, `data/repository/`): the
  only layer touching Firestore/Cloudinary/the backend. Interface +
  real impl + fake/mock impl (for tests/previews), like iOS protocol
  services. Owns (de)serialization, `FieldValue.serverTimestamp()` /
  `FieldValue.delete()` / explicit `null`, deterministic doc ids, the
  denormalization fan-out (`02` §10), and the Menu↔Program sync
  (`02` §4.5).
- **ViewModel** (`ui/<feature>/`): `androidx.lifecycle.ViewModel`
  exposing a single **`StateFlow<UiState>`** where `UiState` is a
  sealed interface (`Loading`/`Loaded(data)`/`Empty`/`Error(msg)`) —
  not scattered booleans. Owns coroutine scope, realtime listener
  registrations and their removal, error→message mapping. Inject
  services via constructor (Hilt).
- **View** (`ui/<feature>/*Screen.kt`): `@Composable` only,
  presentational, stateless where possible (hoist state), driven by the
  `UiState`. No business logic, no direct Firestore calls. Every screen
  has a `@Preview` using the fake service (mirror the iOS preview rule).

## 2. Suggested module/package structure

```
app/src/main/java/com/<final-package>/campzone/
  CampzoneApp.kt                 # Application: Firebase init, DI
  MainActivity.kt                # single Activity, Compose host, deep links
  core/
    designsystem/                # tokens (06): Color, Type, Spacing, Radius, Cz* components
    firebase/                    # Firestore/Auth/Messaging providers
    permissions/                 # AppPermission evaluator (mirror 03 + church scope)
    navigation/                  # typed AppRoute + NavHost + campzone:// resolver (05)
    i18n/                        # PT/FR + English keys (string resources)
  data/
    model/                       # wire-schema data classes + enums (02)
    service/                     # interface + Firestore/Cloudinary/backend impls + fakes
    repository/                  # orchestration where useful
  ui/
    home/ campings/ schedule/ announcements/ songbook/ teams/ games/
    chat/ polls/ checkin/ transportation/ lodging/ feedback/ venuemap/
    profile/ family/ notifications/ admin/      # one package per feature
```

One package per feature with `{Screen, ViewModel, components}`,
mirroring iOS `Features/<Domain>/{Model,View,Observer,Service,Components}`.

## 3. Firebase wiring

- `google-services.json` for the **same** Firebase project as
  iOS/web (see `09-setup.md`); Google services Gradle plugin.
- **Auth**: Firebase Auth with **Google** (Credential Manager /
  Google Identity) and **Apple** (Firebase
  `OAuthProvider("apple.com")` web flow). First sign-in creates
  `users/{uid}` (role `guest`); onboarding sets `onboardingCompleted`.
- **Firestore**: enable disk persistence
  (`FirebaseFirestoreSettings` / `setLocalCacheSettings` persistent) for
  offline parity (songbook/schedule/camp program prioritized).
- **Realtime**: services expose listeners returning a
  `ListenerRegistration`; the ViewModel registers in an init/`onStart`
  and **removes** it in `onCleared()` — single owned registration, no
  stacking on recomposition/config-change (mirror the iOS listener
  discipline).
- **FCM**: `FirebaseMessagingService` for token + push; on token
  refresh, register in **both** Firestore
  (`users/{uid}/notificationTokens/{sha256}`, `platform:"android"`) and
  `POST /notifications/devices` (`07` §9). Notification channels keyed by
  type (`announcement`, `chat_message`, …) to match the backend FCM
  `channel_id`. Handle the data payload → deep link (`05`).
- Use the **client SDK** for authed reads/writes so Firestore Security
  Rules apply (same rules as iOS). No Admin SDK on device.

## 4. State, concurrency, errors

- Coroutines + `Flow`; `viewModelScope`; `callbackFlow` to bridge
  Firestore listeners; cancel on `onCleared`.
- One `StateFlow<UiState>` sealed-interface per screen (no
  `isLoading`+`hasError`).
- Map Firebase exceptions to friendly copy; never surface raw errors;
  structured logging. Silent failures forbidden.

## 5. Design system & accessibility

Implement `06-design-tokens.md` as a `CampzoneTheme` (Material3
`ColorScheme` light/dark, rounded `Typography`, `CzSpacing`/`CzRadius`
`Dp` objects) and a `core/designsystem` component set mirroring the iOS
`Cz*` components. No hardcoded colors/sizes in feature code. A11y:
`contentDescription`, touch target ≥48dp, dynamic font scale,
TalkBack, contrast, reduced-motion.

## 6. Payments, media, notifications (Android specifics)

- **Payments**: Stripe Android **PaymentSheet**. Call `/payments/intent`
  with the Firebase ID token, present PaymentSheet with the returned
  client secret + ephemeral key + customer + publishable key, then call
  `/payments/confirm` so the backend settles status server-side
  (`04` §5). Never embed the Stripe secret.
- **Media**: `POST /cloudinary/sign`, upload `multipart/form-data`
  directly to Cloudinary, persist `secure_url`/`public_id` per `02`.
  Load images/video thumbs with Coil from the Cloudinary URLs.
- **Notifications**: see §3; in-app feed reads `ziyon_notifications`
  (client read-only), filtered `appID=="campzone"` + visible topics.

## 7. Navigation & deep links

Single Activity + Navigation-Compose. A strongly-typed `AppRoute`
sealed hierarchy mirroring the iOS routes (no raw-string routes).
Register an `intent-filter` for the `campzone` scheme on `MainActivity`
and resolve FCM data payloads + `campzone://` links per `05`
(park cold-start links until auth/nav is ready).

## 8. Testing

Unit-test models (wire round-trip vs fixtures), services (Firestore
emulator + fakes), ViewModels (state transitions, error mapping),
permission evaluator. Add emulator rule tests where you change
behavior. Compose UI tests for navigation/onboarding/registration.
“Builds + emulator green” is the done bar; never claim an unrun green.

## 9. Conventions

`async`-style via coroutines only (no callback spaghetti); no global
mutable state/singletons (DI); small composables; hoisted state; typed
routes; no monolithic files; latest stable AndroidX/Compose; full CRUD
for anything that can Create; previews on every screen with fakes.
