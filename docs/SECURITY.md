# Security Policy — TermChin

> Applies to repository `Ara-0x/TermChin` and to APKs distributed through its
> GitHub Releases page.

## Supported Versions

| Version | Supported |
|---|---|
| 2.5.x (current release key) | ✅ |
| 2.0.0 – 2.5.0 (released with the retired key) | ⚠️ installable, but must be replaced — see [Migration impact](#migration-impact) |
| older | ❌ |

Only APKs downloaded from this repository's official Releases page are
supported. Builds from forks or third-party mirrors are not.

## Reporting a Vulnerability

Please **do not** open a public issue for a security problem.

- Use GitHub's private vulnerability reporting for this repository
  (Security → Report a vulnerability), or
- contact the maintainer directly.

Include: affected version, steps to reproduce, impact, and any suggested fix.
You should receive an acknowledgement within a few days. Once a fix is
released, the report can be discussed publicly.

Never include in a report: private keys, keystore files, passwords, tokens, or
any real user data.

## Release Signing

| Item | Value |
|---|---|
| Certificate owner | `CN=TermChin Release, O=TermChin, C=IR` |
| Algorithm | RSA 4096 |
| **SHA-256 fingerprint** | `0d38aa655105b0af6a0c0a1d26b37dbb872d20b6b4ab63fbeb9f88aa195adda0` |

Verify an APK before installing it:

```bash
apksigner verify --print-certs TermChin-vX.Y.Z.apk | grep 'SHA-256 digest'
```

The digest must equal the fingerprint above. Any other digest means the APK was
not produced by this project.

Rules enforced by the build:

1. **Four environment variables, no fallbacks.** Release signing reads exactly
   `RELEASE_KEYSTORE_PATH`, `RELEASE_KEY_ALIAS`, `RELEASE_KEYSTORE_PASSWORD`,
   `RELEASE_KEY_PASSWORD`. Legacy names and `-P` overrides are not honoured, and
   there is no default keystore.
2. **Fails closed when a value is missing.** `assembleRelease` depends on the
   `verifyReleaseSigning` Gradle task, which aborts with an explicit error if any
   of the four is unset. A release artifact can never be unsigned or
   debug-signed.
3. **Fails closed when a value is wrong.** The same task then proves the four
   values work together: it opens the keystore with `RELEASE_KEYSTORE_PASSWORD`,
   requires `RELEASE_KEY_ALIAS` to be a private-key entry in it, and requires
   `RELEASE_KEY_PASSWORD` to decrypt that entry. A wrong password, a wrong
   alias, or a keystore that is not really a keystore stops the build before
   packaging begins — with the failure named — instead of failing late or
   publishing something signed by an unexpected key.
4. **The keystore is never in Git.** It lives outside the working tree and is
   supplied to CI only as a repository secret, restored into the runner's temp
   directory for the job and deleted afterwards.
5. **CI verifies the certificate.** Every build compares the built APK's signer
   SHA-256 against the fingerprint above, and explicitly rejects the retired
   certificate. A mismatch fails the run.
6. **No signing material may be tracked.** A CI gate fails the build if any
   `*.jks`, `*.keystore`, `*.b64`, `*.base64` or `debug.keystore` file ever
   becomes a tracked file again.

Debug builds use the ordinary local Android debug keystore so a fresh clone
builds with zero setup. The debug key never signs a release.

## Historical Signing-Key Incident

TermChin's early releases were signed with a certificate that was later
**committed to this public repository**, which means the private key must be
treated as permanently compromised. That key is **retired**: it no longer signs
anything, and all history reachable from the repository's branches and tags has
been cleaned so the material is gone.

Two consequences remain, and neither can be undone retroactively:

- Copies may still exist in older clones, forks, forks' caches and mirrors.
  Rewriting this repository's history does not reach those.
- APKs signed with the retired certificate (v2.0.0–v2.5.0) remain installable
  from the Releases page, and whoever holds the retired key could still sign
  an APK that installs as an update over *those* builds only.

**Migration impact — one-time reinstall.** Because the signing identity
changed, Android refuses to install a current release over a build signed with
the retired key (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). The first upgrade
requires: export a backup from the app, uninstall, install the new APK, re-import
the backup. Builds signed with the *current* key update normally afterwards.

**This is not "100% secure".** Rotation removes the ongoing exposure for future
releases; it cannot un-publish a key that was already public.

## Data & Privacy

- **Offline by construction.** The app declares no `INTERNET` permission, so it
  cannot make network connections. A CI gate fails the build if `INTERNET` ever
  appears in the released APK.
- **OS backup is disabled.** The manifest sets `android:allowBackup="false"`,
  so Android Auto Backup and device-to-device transfer do not copy the app's
  data to a cloud account or another device. The only backup mechanism is the
  app's own **Settings → Export JSON**, saved wherever the user chooses.
- **What is stored locally:** course catalog and enrollments, weekly schedule,
  documents metadata and preferences (Room + DataStore, both app-private
  internal storage).
- **Attached files are not copied by TermChin.** A document attachment points
  at a file the user selected through Android's storage picker; the file stays
  where the user put it.
- **No data is deleted automatically.** The database is only cleared through
  the explicit "حذف همهٔ اطلاعات" action in Settings. Room schema changes always
  ship a migration; destructive migrations are forbidden.

## What must never be committed

- Keystores, keystore dumps, passwords, tokens, API keys.
- `local.properties`, `.env`, or anything under `.ci-secrets/`.
