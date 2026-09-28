# Changelog

Notable changes to TermChin (Course Planner), newest first.
Versions are written as `versionName (versionCode)` exactly as they appear in
`app/build.gradle.kts` — the single source of truth also checked by CI.

## [2.5.0] — 2026-09-28 integrity pass

### Fixed (critical data safety)

- **Startup database wipe removed.** `CoursePlannerViewModel.init` used to run
  `repository.clearAllData()` whenever the DataStore marker
  `release_clean_courses_v1` was absent — and the marker defaults to *false*, so
  one missing flag (fresh/restored DataStore) erased courses, groups, sessions,
  enrollments and documents on the next launch. The whole mechanism (marker
  API + `init` block) is gone; nothing may delete user data automatically, and
  `StartupDataPreservationTest` now guards that invariant (existing data +
  missing marker + app start ⇒ data intact).
- **Incomplete schedules can no longer be applied.**
  `ScheduleSearchResult.isComplete` / `GenerationState.isComplete` /
  `canApplyCurrent` express the state; the Apply button is disabled with an
  explanatory note and `applyCurrentGeneratedSchedule()` refuses the write, while
  `CourseRepository.applySchedule` independently refuses any candidate that does
  not cover every course selected for generation (`ApplyScheduleResult.Incomplete`
  names the missing courses). The current enrollments are never destroyed by a
  partial plan. `skippedCourses` kept its role (informational report).
- **Duplicate course codes are impossible at the data layer.** Code uniqueness
  is now checked with one canonical normalization (`trim()` + upper-case, see
  `normalizeCode`) in `addManualCourse` **and** `updateCourseDetails`, so
  "MATH101", " math101 " and "Math101" are one course. Refusals return typed
  results the UI turns into messages (`AddCourseResult` /
  `UpdateCourseResult.BlankCode`).
- **Duplicate group codes are impossible inside a course.** Same normalized
  uniqueness per course for `addSectionToCourse` / `updateSectionDetails`; the
  same group number under a *different* course stays valid.
- **Overlapping sessions rejected before persistence.** New
  `engine/SectionSessionValidator` (single implementation shared by the add/edit
  dialogs and the repository) rejects same-day, same-parity, time-overlapping
  sessions of one group, honouring the existing parity rule
  (EVERY×EVERY/ODD/EVEN and ODD×ODD/EVEN×EVEN clash, ODD×EVEN does not) plus
  unparseable/reversed times. Imported catalog data is not run through it (the
  parsers own their format and the import path is unchanged).
- **No synthesized identifiers.** The `CRS-${timestamp%10000}` course code and
  the `ifBlank { "01" }` group code fallbacks are gone: a blank code is an
  explicit validation error in the dialogs and in the repository.
- **Import I/O failures are no longer fake-empty files.**
  `readImportText` now throws when the stream cannot be opened (it used to
  return `""`), and Settings reports four distinct outcomes: file read failed →
  "خواندن فایل ناموفق", file empty → "فایل خالی", read-but-no-courses → the
  parser's message, success → the parser's summary. JSON/CSV pickers got the
  same treatment. Parser behavior untouched.
- **Remaining raw `viewModelScope.launch` writes** (course/generator toggles,
  section enrollment, document bookmarks, manual course/group creation) now go
  through `launchDbWrite`, so a failed write always produces a logged, visible
  error instead of a UI that pretends the write succeeded.

### Changed (scheduler scoring)

- **Parity-aware scoring metrics.** `weeklyActiveDays` /
  `weeklyEarlyMorningCount` charge a biweekly-only day/session 0.5 (the class
  meets every other week) instead of 1.0; the integer `activeDaysCount` /
  `earlyMorningClassCount` stay the *union* view used for display ("days you
  must keep free"). Both rules are documented at their definition and covered
  by tests.
- **Comments now match the weights.** "each day above 3 drops 5 points" was
  wrong (the code always used 6); all weights are named constants with the
  table documented next to `evaluateSchedule`. Scores are unchanged except for
  the intentional parity averaging above.
- **Score breakdown always equals the displayed score.** `ScoredSchedule` gained
  `rawScore`, and when the 0..100 clamp bites, an explicit
  "محدودسازی امتیاز به بازهٔ ۰ تا ۱۰۰" row is added so the rows sum to the
  shown number (tests cover in-range, below-zero and the max case).

### Removed

- `feature/portal/PortalWebViewScreen.kt`: an unreferenced WebView screen with
  no `INTERNET` permission in the manifest (dead code from the abandoned
  in-app-browser idea). The official import workflow remains "save the page as
  HTML → import the file → process locally"; no scraping was added.

### Security / repository hygiene

- Verified (fingerprint compared offline, nothing printed): the distribution
  keystore blob was committed in `e0eb70e` and deleted in `7b2b5a1`, but it is
  still in the **public** repository's history and it is the very key that signs
  releases (`fcced2ea…`). Findings, impact and the two remediation options are
  documented in `docs/SECURITY.md`; a new CI **secret-hygiene gate** fails any
  build that tracks a keystore-like file again. History rewrite / key rotation
  are left as explicit maintainer decisions (both have user-visible cost).
- README rewritten for accuracy: TermChin repo/URLs, honest offline wording
  (no `INTERNET` permission at all), Top-K + `maxLeaves` truncation instead of
  "all combinations", the 4-step HTML-file import workflow (no automatic portal
  login/scraping), MVVM + Repository (no "Clean Architecture" claim), removed
  the deleted WebView entry, and documented the version history through 2.5.0.

## [2.4.0] — 2026-09-28 audit pass

### Added

- **Global error snackbar.** Repository/DB write failures no longer die
  silently in `viewModelScope`: every write goes through `launchDbWrite`,
  which logs the exception and shows a Persian error snackbar hosted in
  `MainActivity` (info vs. error styling, auto-dismiss).
- **Preference re-run.** Changing the optimization preference now re-runs the
  full schedule search instead of re-ranking only the previous top-12
  retained combinations — the previously "impossible to recover" better
  schedule can win again.
- **Deterministic ranking.** `ScheduleEngine.rankingComparator`
  (score → gaps → active days → early classes → section-id) used by top-K
  insertion, final ordering and `rankSchedules`; covered by the new
  `RankingDeterminismTest`.
- **Gradle wrapper** (`gradlew`, `gradlew.bat`,
  `gradle/wrapper/gradle-wrapper.jar`) pinned to Gradle 9.3.1, so any machine
  and CI build the same way without a preinstalled Gradle.
- **Audit trail:** `docs/AUDIT.md` (+ parts 2–3) and this changelog.

### Changed

- **Courses screen split & de-scanned.** `CoursesScreen.kt` 1550 → 900
  lines; behavior-identical extractions into
  `ui/screens/courses/{CourseCard,CatalogQuickAdd,CoursesEmptyStates}.kt`.
  O(docs) render-path scans replaced by ViewModel-computed maps
  (`sectionsByCourse`, `documentCountByCourse`, `departmentNames`,
  `catalogOnlyCourseCount`).
- **`CoursePlannerViewModel` decomposition** without public-API changes:
  derived `StateFlow`s for the Courses-screen aggregates, shared
  `showInfo/showError/launchDbWrite` error plumbing, single-pass grouping in
  the generator.
- **Score honesty.** `testRealScore_noFakeHundredAndExplainableBreakdown` now
  asserts the real invariant (`sum(deltas) == score`, one positive base row)
  — the engine always scored 60, the old test double-counted the +100 base.
- **Dependency hygiene.** `gradle/libs.versions.toml` dropped ~15 unused
  libraries/plugins (camera, location, Retrofit/Moshi/OkHttp, Coil,
  accompanist, navigation-compose, Firebase, credentials, secrets &
  google-services plugins) and `app/build.gradle.kts` dropped the
  commented-out stubs. The legacy web prototype was out of the Android build
  and is deleted in the "Removed" section below.

### Removed

- **Legacy web prototype deleted.** The pre-Android React/Vite prototype
  (`src/App.tsx`, `src/main.tsx`, `src/index.css`,
  `src/utils/{defaultData,parser}.ts`, `index.html`, `package.json`,
  `tsconfig.json`, `vite.config.ts`, `metadata.json` — ~130 KB) never
  participated in the Gradle build (`settings.gradle.kts` includes only
  `:app`, and no workflow, build script or source file referenced it), so it
  was pure dead weight that made every clone look like a mixed JS/Android
  project. It stays available in git history if it is ever needed again.

### Security / build

- **Keystore hygiene.** `debug.keystore.base64` untracked; `*.jks`,
  `*.keystore`, `*.base64` and `.ci-secrets/` gitignored. Local debug builds
  use AGP's standard auto-generated keystore (fresh clones build with zero
  setup). Release signing reads only non-secret values from
  `gradle.properties` (`KEYSTORE_PATH`, `KEY_ALIAS`) and passwords **only**
  from the environment (`STORE_PASSWORD`/`KEY_PASSWORD`) or `-P` — no
  defaults, no committed file; without them the release APK is unsigned by
  design. Because the released APKs are debug-signed, the debug key *is* the
  distribution key: CI restores it from the `DEBUG_KEYSTORE_BASE64`
  repository secret into `.ci-secrets/debug.keystore` and hands it to the
  build explicitly through `CI_KEYSTORE_PATH` (relying on AGP's implicit
  `~/.android/debug.keystore` silently produced a throwaway key and an APK
  that could not update installed builds).
- **Signature gate.** `build-apk.yml` verifies the built APK's signer
  certificate against the public SHA-256 fingerprint of the historical
  release key and fails the run on any mismatch, so a signature-breaking
  release can never be published again.
- **CI gates.** `build-apk.yml` now (1) runs `testDebugUnitTest` before
  assembling, (2) derives `versionCode`/`versionName` from
  `app/build.gradle.kts` instead of hardcoding them, (3) on `v*` tags fails
  the release when `versionName != tag` or `versionCode` is not strictly
  greater than the previous tag's code.

### Fixed

- Baseline unit-test failure (`expected:<60> but was:<100>`) — see
  "Score honesty" above; `testDebugUnitTest` is green (0 failures).
- `init` cleanup logs instead of crashing; import DB failures surface as an
  error snackbar instead of vanishing.