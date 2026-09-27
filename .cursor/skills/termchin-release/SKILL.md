---
name: termchin-release
description: >-
  Build, test, verify, bump, tag, and publish releases for the TermChin (Course Planner) Android project.
  Use when preparing or executing a new release, running local offline tests, assembling debug APKs,
  verifying APK version metadata, updating README changelogs, pushing tags, and monitoring GitHub Actions releases.
---

# TermChin Android Build, Test & Release Workflow

This skill encapsulates the battle-tested, offline-capable verification and release pipeline for the **TermChin (Course Planner)** Android project.

---

## 1. Environment & Prerequisites

Gradle Wrapper (`gradlew`) is not checked into the repository; use the local standalone toolchain with JDK 17:

- **JDK 17:** `C:\Users\Amirreza\.buildtools\jdk-17`
- **Android SDK:** `C:\Users\Amirreza\.buildtools\android`
- **Standalone Gradle:** `C:\Users\Amirreza\.buildtools\gradle-9.3.1\bin\gradle.bat`
- **aapt tool:** `C:\Users\Amirreza\.buildtools\android\build-tools\36.0.0\aapt.exe`

### Setting up Process Environment
Whenever executing Gradle in PowerShell, always configure the process environment properly:
```powershell
$env:JAVA_HOME = "C:\Users\Amirreza\.buildtools\jdk-17"
$env:ANDROID_HOME = "C:\Users\Amirreza\.buildtools\android"
$env:PATH = "C:\Users\Amirreza\.buildtools\jdk-17\bin;$env:PATH"
```

---

## 2. Step 1: Local Test & Build Verification (Before Commit)

### A. Non-blocking Background Execution
Interactive terminal shells can hang or timeout on long-running Gradle operations. **Always launch Gradle via background PowerShell scripts redirected to log files**:
```powershell
@'
$env:JAVA_HOME = "C:\Users\Amirreza\.buildtools\jdk-17"
$env:ANDROID_HOME = "C:\Users\Amirreza\.buildtools\android"
$env:PATH = "C:\Users\Amirreza\.buildtools\jdk-17\bin;$env:PATH"
& "C:\Users\Amirreza\.buildtools\gradle-9.3.1\bin\gradle.bat" :app:testDebugUnitTest --offline --tests "ir.courseplanner.app.data.importer.PooyaHtmlParserTest" --tests "ir.courseplanner.app.ui.CourseFiltersTest" --tests "ir.courseplanner.app.util.*" --tests "ir.courseplanner.app.engine.ScheduleParityTest" --tests "ir.courseplanner.app.ExampleUnitTest" > test_run.log 2>&1
'@ | Set-Content -Path run_test.ps1; Start-Process -FilePath "powershell.exe" -ArgumentList "-ExecutionPolicy", "Bypass", "-File", "run_test.ps1"
```
Poll the log until `BUILD SUCCESSFUL` or failure is reported:
```powershell
Start-Sleep -Seconds 12; Get-Content test_run.log -Tail 15 -ErrorAction SilentlyContinue
```

### B. Pure JUnit vs Robolectric Tests
- **Local Unit Tests:** Run only pure JUnit suites (`PooyaHtmlParserTest`, `CourseFiltersTest`, `JalaliDateTest`, `TimetableExporterTest`, `ScheduleParityTest`, `ExampleUnitTest`).
- **Robolectric Tests:** Local environment has JDK 17, while target SDK 36 Robolectric tests may require JDK 21 or cause bytecode conflicts locally. Robolectric tests run automatically on GitHub Actions CI.

### C. Build Debug APK
Build the debug package offline:
```powershell
@'
$env:JAVA_HOME = "C:\Users\Amirreza\.buildtools\jdk-17"
$env:ANDROID_HOME = "C:\Users\Amirreza\.buildtools\android"
$env:PATH = "C:\Users\Amirreza\.buildtools\jdk-17\bin;$env:PATH"
& "C:\Users\Amirreza\.buildtools\gradle-9.3.1\bin\gradle.bat" :app:assembleDebug --offline > build_run.log 2>&1
'@ | Set-Content -Path run_build.ps1; Start-Process -FilePath "powershell.exe" -ArgumentList "-ExecutionPolicy", "Bypass", "-File", "run_build.ps1"
```

### D. Verify APK Version with AAPT
Android rejects APK installation if `versionCode` matches the existing installed version. Always verify badging:
```powershell
& "C:\Users\Amirreza\.buildtools\android\build-tools\36.0.0\aapt.exe" dump badging app/build/outputs/apk/debug/app-debug.apk | Select-String -Pattern "versionCode|versionName"
```


---

## 3. Step 2: Version Bumping & Documentation

### A. Bump Version in `app/build.gradle.kts`
- Increment `versionCode` (e.g. from `11` to `12`).
- Update `versionName` (e.g. `"2.2.1"`).

### B. Update `README.md`
- Update badge/current version info: `**نسخه فعلی: vX.Y.Z** (بیلد versionCode=N)`.
- Prepend the new release row to the version history table (`📋 تاریخچه نسخه‌ها`).

### C. Sync `.github/workflows/build-apk.yml`
- Ensure the aapt verification assertion expects the new versions:
  ```yaml
  "$ANDROID_HOME/build-tools/36.0.0/aapt" dump badging "$APK" | grep -E "versionCode='12'|versionName='2.2.1'"
  ```
- Ensure release name, tags, and asset copying are aligned (`CoursePlanner-vX.Y.Z.apk`).
- Update the release body template in the workflow with the Persian release notes.

---

## 4. Step 3: Git Commit, Push & Tagging

1. **Check Untracked & Sensitive Files:**
   Verify `git status` to ensure temporary log files, secrets, or keystores are not staged:
   ```powershell
   Remove-Item run_test.ps1, test_run.log, run_build.ps1, build_run.log -ErrorAction SilentlyContinue
   git status
   ```

2. **Stage and Commit:**
   Follow conventional commits:
   ```bash
   git add -A
   git commit -m "feat(ui): v2.2.1 - touch targets, typography mapping, RTL transitions, and release config"
   ```

3. **Push to Main:**
   ```bash
   git push origin main
   ```

4. **Tag and Push Release Tag:**
   ```bash
   git tag -a v2.2.1 -m "Release v2.2.1"
   git push origin v2.2.1
   ```

---

## 5. Step 4: GitHub Actions & Release Verification

1. **Watch the Workflow Run:**
   Pushing the tag `v*` triggers `.github/workflows/build-apk.yml`.
   ```bash
   gh run list --limit 3
   gh run view <RUN_ID>
   ```

2. **Verify Release Assets:**
   Once the workflow concludes with `success`, inspect the published release:
   ```bash
   gh release view v2.2.1
   ```
   Confirm both artifacts exist:
   - `CoursePlanner-vX.Y.Z.apk`
   - `CoursePlanner-vX.Y.Z-<COMMIT_SHA>.apk` (cache-busting artifact)
   - Release notes are formatted properly in Persian.
