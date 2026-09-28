# Changelog

Notable changes to TermChin (Course Planner), newest first.
Versions are written as `versionName (versionCode)` exactly as they appear in
`app/build.gradle.kts` — the single source of truth also checked by CI.

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
  commented-out stubs. The legacy web prototype (`src/`, `package.json`) is
  untouched and out of the Android build.

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