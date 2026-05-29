# Project State - C6 QR Check-In + C7 Badges Parity

Updated: 2026-05-28T16:18:00Z

## Current Architecture

- Branch/worktree: `c6-qr-check-in` with C6 check-in, C7 badges, Home announcement wiring, and older dirty work preserved.
- Android still follows the layered Compose MVVM shape: composable route -> Hilt `ViewModel` -> service interface -> manual Firestore maps.
- Check-in records use the canonical Firestore path `campings/{campingId}/checkIns/{attendeeId}`. The doc id is the attendee id, and a successful re-check-in overwrites that one doc with a full `set`.
- `CheckInRecord` and `CheckInQrPayload` exist in `data/model/`, with manual decoding/encoding for `campzone://checkin?v=1&c=<campingID>&a=<attendeeID>&u=<userID>&iat=<unixSeconds>`.
- `CheckInService` exists with `FirestoreCheckInService` and `FakeCheckInService`. It loads all records for managers, loads a single attendee record for guardian-style reads, and writes `checkedInAt: FieldValue.serverTimestamp()`.
- `CheckInViewModel` exists and validates scanned QR codes against the loaded camping attendees before writing. It rejects wrong-camp, unknown/forged attendee, unapproved, malformed, and duplicate scans, and supports manual check-in.
- `QrCameraPreview` exists with CameraX + ML Kit QR analysis.

## iOS Parity Reference Read

- `Features/CheckIn/Model/CheckInRecord.swift`
- `Features/CheckIn/Service/CheckInService.swift`
- `Features/CheckIn/Observer/CheckInObserver.swift`
- `Features/CheckIn/View/CheckInScannerView.swift`
- `Features/CheckIn/View/CheckInRecordsView.swift`
- `Features/CheckIn/View/CheckInQRView.swift`
- `Features/CheckIn/Components/CheckInRow.swift`
- `Features/CheckIn/Components/QRCodeImage.swift`
- `Features/Campings/View/CampingDetailView.swift` operations/resources wiring

## Completed C6 Surface

- QR payload round-trip and malformed-code unit tests exist.
- Check-in payload tests cover required fields, `qr`/`manual` method wire values, omit-when-nil optionals, blank photo omission, and tolerant decode.
- ViewModel tests cover QR success, wrong camp, unknown/forged attendee, unapproved attendee, already checked-in, malformed code, duplicate-frame protection, manual fallback, restricted users, and record search.
- Permission plumbing already exposes `canManageCheckIns` in `CampingDetailViewModel`.
- Camera/ML Kit dependencies are present in Gradle, and `AndroidManifest.xml` has check-in-related camera changes in this worktree.
- C6 now has typed Android routes for scanner, records, and QR passes: `CheckInScanner`, `CheckInRecords`, and `CheckInQrPasses`.
- `CampzoneNavigationShell` registers all three C6 destinations and wires them from `CampingDetailScreen`.
- `CampingDetailScreen` now shows "My QR Passes" for users with any managed registration and an Operations group with scanner/records for users with `canManageCheckIns`.
- `CheckInScannerRoute` shows the CameraX/ML Kit preview, camera-permission prompt, saving chip, scan-result status card, and checked-in count summary.
- `CheckInRecordsRoute` shows iOS-style summary stats, search, pending manual check-in rows, checked-in rows, and scanner navigation.
- `CheckInQrPassesRoute` generates local QR bitmaps for approved self/child registrations, sorts self first then children alphabetically, and shows pending/not-registered states.
- C6 strings are filled across default/PT/FR resources.
- `TODO.md` C6 checklist is marked complete.
- Focused verification passed: `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests 'fr.ziyon.campzone.ui.checkin.*' --tests 'fr.ziyon.campzone.data.model.CheckIn*'`.
- Build verification passed: `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew assembleDebug`.
- `lintDebug` is still blocked by pre-existing Compose preview ViewModel-constructor errors, first at `app/src/main/java/fr/ziyon/campzone/ui/polls/CampingPollsScreen.kt:196`. The lint report does not show C6 check-in errors, MissingTranslation, ExtraTranslation, or stale MyView hits.

## Remaining C6 Gaps

- No known C6 implementation gaps remain in the Android code after the current pass.
- Bus tickets, lodging cards, and transportation boarding remain Phase D surfaces even though the iOS "My QR Passes" page already combines them with check-in passes.
- Device/emulator camera QA is still needed because unit tests cannot validate real camera permission/session behavior.

## Completed C7 Surface

- `AchievementCatalog` is embedded in code from the shipped iOS catalog. The iOS source currently contains 45 achievement ids despite the old TODO/docs wording saying 50; Android tests assert the shipped tier counts.
- `EarnedBadge` manually decodes `users/{uid}/badges/{achievementId}` and filters unknown ids out through the service layer.
- Manual award writes use deterministic doc id `achievementId`, `earnedAt: FieldValue.serverTimestamp()`, and explicit Firestore `null` for absent `campingID` / `note`.
- `AchievementService` exists with Firestore and fake implementations.
- `AchievementsRoute` replaces the profile placeholder with a read-only badge display for the signed-in user.
- `CampingBadgeAwardRoute` is wired from camping operations when `canAwardAchievements` is true and supports team or individual approved-participant awards while blocking self-award.
- C7 checklist is marked complete with wording adjusted to "shipped iOS catalog" rather than the stale "50 badges" count.

## Home Follow-up

- Home now loads app-wide, non-role-targeted announcement previews through `AnnouncementService`, shows up to two rows, and routes taps to announcement detail.
- Home no longer hides announcements when there is no featured camping.

## Current Verification

- Passed: `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests 'fr.ziyon.campzone.ui.checkin.*' --tests 'fr.ziyon.campzone.data.model.CheckIn*' --tests 'fr.ziyon.campzone.data.model.AchievementPayloadTest' --tests 'fr.ziyon.campzone.ui.profile.badges.AchievementViewModelTest'`.
- Passed: `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest assembleDebug`.
- Passed: `git diff --check`.
- Still blocked: `lintDebug` fails on pre-existing Compose preview ViewModel constructor errors in polls/games, first at `app/src/main/java/fr/ziyon/campzone/ui/polls/CampingPollsScreen.kt:196`. No lint errors were reported in C6/C7 files.

## Next Steps

- Real-device QA: open a registered participant's QR pass, scan it from the manager scanner, verify the record appears once, and verify duplicate scan messaging.
- Real-device/admin QA: award a manual badge to a team and one approved participant, verify the target user's badge appears and self-award is denied by rules.

## Worktree Notes

- Pre-existing dirty changes include `.claude/settings.local.json`, Gradle/build files, `AndroidManifest.xml`, C1 Teams reveal edits, generated MyView cleanup/deletions, localization changes, and Home announcement wiring. Preserve unrelated work and stage only the requested scope if a commit is requested.
