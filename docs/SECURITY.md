# Security Notes — TermChin

> Status: 2026-09-29 · Applies to repository `Ara-0x/TermChin` (public).

## 1. Signing-key migration (2026-09-29)

The historical distribution key was **compromised** — it was committed to this
public repository — and is now **retired**. TermChin has a new release-signing
identity.

### Old certificate (RETIRED) vs. current certificate

| Item | Old key — do not trust | Current release key |
|---|---|---|
| SHA-256 (public fingerprint) | `fcced2ea0574ba6c7b9536c44846c2b697e1f841507af9e4ed00ed910bbe10ff` | `0d38aa655105b0af6a0c0a1d26b37dbb872d20b6b4ab63fbeb9f88aa195adda0` |
| Owner / alias | `CN=Android Debug, O=Android, C=US` (`androiddebugkey`) | `CN=TermChin Release, O=TermChin, C=IR` (`termchin-release`) |
| Key | RSA 2048 (debug key) | RSA 4096, created 2026-09-29, valid until 2056 |
| Impact of leak | Anybody with it can forge an APK that Android accepts as an update over the releases it signed. | Signs every official APK from now on. |

### What happened

`debug.keystore.base64` — the old private key, base64-encoded — was committed in
`e0eb70e` and deleted in `7b2b5a1`. The file is untracked today, but the blob
stayed reachable from public history, and releases v2.0.0–v2.5.0 were signed with
exactly that certificate, so a forged APK could be installed as an update.

### What this migration changed

1. **New release key** generated on the maintainer's machine, outside the Git
   working tree. The old key must never sign anything again.
2. **Release builds always use the release key.** `assembleRelease` depends on
   the `verifyReleaseSigning` task, which fails the build with an explicit error
   when the keystore or a password is missing. There is deliberately no fallback
   to the debug keystore and no unsigned-artifact mode.
3. **Debug builds** keep AGP's local debug keystore
   (`~/.android/debug.keystore`), so a fresh clone still builds with zero setup.
   The debug key is never used for a release.
4. **Credentials live outside Git.** Only the non-secret keystore path and alias
   are in `gradle.properties`; passwords arrive from the environment
   (`RELEASE_KEYSTORE_PASSWORD` / `RELEASE_KEY_PASSWORD`) or `-P` flags.
5. **CI restores the key from GitHub Actions secrets only**
   (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
   `RELEASE_KEY_PASSWORD`) into the runner's temp directory, deletes it after the
   job, and fails the run if any of them is missing.
6. **Signature gate.** Each build verifies the APK signer's SHA-256 against the
   current fingerprint above and fails on any mismatch, so a debug-signed or
   otherwise wrong APK cannot be published.
7. **Secret-hygiene gate** (`git ls-files` check in CI) fails any build that
   tracks keystore/base64 material again.
8. **History cleanup.** The compromised blob was removed from every reachable
   branch and tag with `git filter-repo` plus a force-push, so the old commit is
   no longer reachable from repository refs.

### User impact — one-time reinstall

Because the signing identity changed, Android refuses to install a new release
over an app signed with the old key
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Anyone on v2.0.0–v2.5.0 must uninstall
the old app once and then install the new APK — export a backup from Settings
first if the data matters.

### Remaining risks (this is not "100% secure")

- The key was public for a long time: clones, forks, CI caches and mirrors that
  already fetched it keep their own copies, and rewriting history does not
  un-leak it.
- Whoever holds the old key can still forge an APK that installs **over** the old
  releases (v2.0.0–v2.5.0). Only replacing those installs removes that exposure.
- Previously published release assets signed with the old key remain
  downloadable. They are the genuine old builds, but they carry the compromised
  certificate, so users should move off them.
- The new private key exists on the maintainer's machine and in GitHub Actions
  secrets. If it is lost, a later release needs a new key (and another
  reinstall); if it leaks, it must be rotated the same way.

## 2. What must never be committed

- Keystores, their base64 dumps, keystore passwords, GitHub tokens.
- `local.properties`, `.env` (gitignored) — CI reads them from secrets/env.

## 3. Data safety rules

- The app must **never** delete user data automatically at startup (the old
  DataStore-marker wipe was removed in v2.5.0; see
  `StartupDataPreservationTest`).
- Wiping the database is only possible through the explicit "حذف همهٔ
  اطلاعات" action in Settings.
- All Room schema changes must bump `AppDatabase.version` and ship a
  `Migration`; destructive migrations are forbidden.
