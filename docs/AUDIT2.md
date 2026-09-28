# Audit (part 2) — Tasks 3–7

## Task 3 — Score honesty (the failing baseline test)

**Finding.** Baseline failure `expected:<60> but was:<100>`
(`ScheduleEngineTest.kt:394`). The *engine* was correct (score 60); the
*test* double-counted the base: `breakdown` already contains the base row
(+100) plus deductions (−40), so `100 + sum(deltas)` over-counts.
`ScoreBreakdownSheet` renders the base row by design.

**Change.** Test now asserts the true invariant —
`breakdown.sumOf { delta } == score` (60), exactly one positive (base) row,
plus the gap row. No production change; `ScoredSchedule.breakdown` KDoc
documents the shape.

## Task 4 — ViewModel decomposition (no rewrite)

`CoursePlannerViewModel` kept its public API (no screen changes) while
shedding separable responsibilities:
- Error plumbing → `showInfo/showError/launchDbWrite` (Task 6).
- Courses-screen aggregates → derived `StateFlow`s (Task 1).
- Generator grouping → single `groupBy`.

## Task 5 — Courses-screen decomposition (no rewrite)

`CoursesScreen.kt`: 1550 → 900 lines. Extracted, behavior-identical:
- `ui/screens/courses/CourseCard.kt` — `CourseCard` + `SectionItem`
  (precomputed `sections`/`docCount` params, no whole-list scans);
- `ui/screens/courses/CatalogQuickAdd.kt` — quick-add block + card
  (lookup via `sectionsByCourse` map);
- `ui/screens/courses/CoursesEmptyStates.kt` — `CoursesEmptyStates` +
  `CoursesNothingAtAll` (explicit params, no ViewModel capture).

`CoursesScreen` keeps: state collection, search bar, filter wiring, lazy
list, dialogs. Contracts (testTags, callbacks) unchanged.

## Task 6 — Error handling: no silent failures

**Finding.** Every `viewModelScope.launch { repository.* }` that threw (disk
full, IO, constraint race) died silently; `userMessage` existed but nothing
displayed it — `MainActivity` never collected it.

**Change.**
- `launchDbWrite { ... }` wraps all repository writes: exceptions → Persian
  error snackbar + `Log.e`; `CancellationException` rethrown.
- `MainActivity` hosts a global `SnackbarHost` (info = inverseSurface,
  error = errorContainer); messages auto-dismiss via `dismissUserMessage()`.
- Import DB failures → error snackbar (new); `init` cleanup logs instead of
  crashing.

**Verified by:** grep shows no bare `viewModelScope.launch { repository.`
write remains; manual error matrix in §Verification log below.
