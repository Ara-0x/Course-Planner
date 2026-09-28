# TermChin Course Planner — Code Quality, Correctness & Performance Audit

Date: 2026-09-28 · App version: 2.3.0 (versionCode 13) · Scope: full
`app/src` (Kotlin/Compose), Room schema, Gradle config, CI, docs.
Rule followed throughout: **no architectural rewrites** — targeted fixes only,
behavior-preserving unless the old behavior was a bug. Every fix is covered by
`testDebugUnitTest` (green) plus a clean `assembleDebug`.

## Method

1. Baseline `testDebugUnitTest` → compile OK, **1 failing test**
   (`ScheduleEngineTest.testRealScore_noFakeHundredAndExplainableBreakdown`).
2. Fix + refactor in small slices; verify with `testDebugUnitTest` and
   `assembleDebug`; diff-review each slice.
3. This file records finding → change → verification per task.

---

## Task 1 — Courses-screen performance (Lazy list scans)

**Finding.** `CoursesScreen.kt` recomputed full-collection scans on the render
path for every visible row:
- `items(...) { cws -> allDocs.count { ... } }` — O(docs) per row;
- `CourseCard`: `allSections.filter { it.course.id == ... }` per card;
- `CatalogQuickAddResults`: `allSections.filter { ... }` per match;
- `departments`, `selectedCount`, `catalogOnlyCount` recomputed with
  `remember(list)` inside composition instead of once per data change.

**Change.**
- `CoursePlannerViewModel` exposes precomputed, `flowOn(Default)` state:
  `sectionsByCourse: Map<Long, List<SectionWithDetails>>`,
  `documentCountByCourse: Map<Long, Int>`, `departmentNames`,
  `catalogOnlyCourseCount`.
- `CoursesScreen` uses O(1) lookups
  (`sectionsByCourse[id].orEmpty()`, `documentCountByCourse[id] ?: 0`).
- `runScheduleGenerator` groups sections once (`groupBy`) instead of
  `filter` per selected course.

**Verified by:** `testDebugUnitTest` green; unchanged ordering/cards/counts;
no `filter`/`count` remains in any item lambda (grep).

## Task 2 — Optimization-preference correctness

**Finding.** `setOptimizationPreference()` re-ranked only the **previous
Top-K** (K=12) retained in `generationState.rawCombinations`. A preference
change could never recover combinations the old weights had cut — wrong
winner possible.

**Change.**
- `setOptimizationPreference()` **re-runs the full search** with the new
  weights when results already exist (no-op when unchanged / not generated).
- `ScheduleEngine` gained deterministic `rankingComparator`
  (score → gaps → active days → early classes → section-id tie-break) used by
  the top-K insertion, final ordering, and `rankSchedules`.

**Verified by:** preference re-run keeps the true best in unit tests (Task 9).
