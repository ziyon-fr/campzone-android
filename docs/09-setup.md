# Android Setup & Environment (Compose - Platform-Specific)

> Local dev, Firebase/Cloudinary/Stripe wiring, and the build/verify
> workflow for the **Android** client. Data contract is in `02`–`08`;
> backend ops/owner actions in `04` §7.

---

## 1. Prerequisites

- Android Studio (matching AGP 9.2.1 / Kotlin 2.2.10), JDK 17+.
- Access to the shared **Firebase project** (same one iOS/web use):
  an **Android app** registered with this module’s package name +
  `google-services.json`.
- Notification backend is already deployed at
  `https://notification-backend-chi.vercel.app` (no local backend for
  app dev). Cloudinary/Stripe are server-side only - the app calls the
  backend; you do **not** need their secrets.

## 2. Project state & first steps

The scaffold is a bare Compose app (`com.example.campzone`, single
`MainActivity`, Material3, no Firebase yet). To start:

1. **Finalize the application id / package** (e.g.
   `org.<org>.campzone`) - it must equal the Firebase Android app’s
   package name. Do this **before** registering the Firebase app and
   before any release; renaming later invalidates `google-services.json`
   and signing config.
2. Add the Google services Gradle plugin + Firebase BoM and the
   modules: `firebase-auth`, `firebase-firestore`,
   `firebase-messaging`, `firebase-analytics`. Add Navigation-Compose,
   Coil, coroutines, Hilt (DI), and the Stripe Android SDK
   (`com.stripe:stripe-android` / `payments-ui` for PaymentSheet).
   Wire them through `gradle/libs.versions.toml` (version catalog), not
   ad-hoc strings.
3. Drop `google-services.json` (the shared Firebase project’s Android
   app) into `app/`. **Do not commit** it if it carries project-private
   config - gitignore and document where to obtain it.
4. Apply `CampzoneTheme` (tokens from `06`) at the `setContent` root;
   enable Firestore disk persistence in the `Application`.
5. Implement Auth (Google via Credential Manager + Apple via Firebase
   `OAuthProvider`), the onboarding gate (`onboardingCompleted`), and
   the FCM service before feature work.

## 3. Secrets / configuration

Nothing Stripe/Cloudinary/cron lives in the app. The only “config” is
`google-services.json` (Firebase) and the notification API base URL
(default `https://notification-backend-chi.vercel.app`) - put the base
URL in a build config field / resource, overridable per build type
(debug vs release) if needed. FCM web push is N/A; Android uses native
FCM (no VAPID key).

## 4. Firebase project checklist

1. Use the **same** Firebase project as iOS/web (data must sync).
2. Auth providers **Apple** + **Google** enabled; add the Android app’s
   SHA-1/SHA-256 signing certs for Google sign-in; configure the Apple
   OAuth web redirect.
3. Firestore Security Rules + composite indexes are **owner-managed
   from the iOS repo** (`firestore-rbac.rules`) - do **not** keep a
   separate rules file here. If you add a `where + orderBy` query,
   request the index (`04` §7).
4. Enable Firestore persistent disk cache in the client.
5. FCM: notification channels per type; register the token in **both**
   Firestore (`users/{uid}/notificationTokens/{sha256}`,
   `platform:"android"`) and `POST /notifications/devices` (`07` §9).
6. Analytics: Firebase Analytics; mirror the iOS event set (`08`
   Phase C).

## 5. Build & verify

```bash
./gradlew assembleDebug      # build
./gradlew testDebugUnitTest  # unit tests
./gradlew lint               # Android lint
```

Definition of done per change:

- Build + unit tests + lint clean.
- Models match `02-firestore-schema.md` exactly (fixture round-trip
  test: serialize→deserialize).
- Every write path checked against the `07` pre-write checklist.
- Gated actions match `03` (and the iOS Security Rule - rule changes
  are owner actions, coordinate per `04` §7).
- Full CRUD for anything that can Create (`08` cross-cutting).
- Firestore emulator green for rule-sensitive paths. Never claim an
  unrun green.

## 6. Working with the shared docs

`00` and `02`–`08` are **byte-identical** to the web repo’s `docs/`. If
the data contract changes it must change on iOS first (schema +
Security Rule, owner-deployed), then be reflected in `02/03/04` and
copied to the web repo. Never let the platforms drift - a schema
mismatch silently corrupts shared Firestore data.
