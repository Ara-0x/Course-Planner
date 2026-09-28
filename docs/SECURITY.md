# Security Notes — TermChin

> Status: 2026-09-28 · Applies to repository `Ara-0x/TermChin` (public).

## 1. Signing key material

### Verified facts (no secrets printed)

| Item | Value |
|---|---|
| Distribution key SHA-256 (public fingerprint) | `fcced2ea0574ba6c7b9536c44846c2b697e1f841507af9e4ed00ed910bbe10ff` |
| Owner | `CN=Android Debug, O=Android, C=US` (alias `androiddebugkey`) |
| Tracked today? | **No.** `git ls-files` contains no `*.jks` / `*.keystore` / `*.base64`. |
| Present in git history? | **Yes.** Added in commit `e0eb70e` ("ci: include debug keystore…"), deleted in `7b2b5a1` (v2.4.0 hygiene pass). The blob is still fetchable with `git show e0eb70e:debug.keystore.base64`. |
| Local untracked copy | `debug.keystore.base64` in the working tree (gitignored) — the maintainer's backup of the same key. |

The historical blob and the local backup were both decoded **offline** and their
fingerprints compared; both match the key that signs every released APK (the
same constant CI's signature gate checks). That is the strongest evidence
possible without publishing key material.

### Impact

The repository is **public**, so anyone can read the private key from history.
Released APKs are debug-signed with exactly this key, therefore anybody who has
it can produce an APK with:

- the same `applicationId` and the same signing certificate, and
- a higher `versionCode`,

which Android will accept as an **update over an installed TermChin**. This is a
real supply-chain risk for anyone who installed an official release.

### Mitigations already in place

1. The file is out of the working tree and gitignored (`*.jks`, `*.keystore`,
   `*.base64`, `*.b64`, `debug.keystore`, `.ci-secrets/`).
2. CI restores the key from the `DEBUG_KEYSTORE_BASE64` **repository secret**
   only (`.github/workflows/build-apk.yml`), never from the repo.
3. New CI gate `Secret-hygiene gate (no signing material tracked in git)` fails
   any build that tracks a keystore-like file again.
4. The `Signature gate` step pins the expected signer fingerprint, so a build
   with any other key cannot publish a release.

### Open decisions for the maintainer (NOT executed automatically)

Rewriting history or rotating the key both have user-visible cost, so they are
left as explicit choices:

- **History cleanup** (`git filter-repo --path debug.keystore.base64 --invert-paths`
  + force-push + GitHub support cache purge) removes the key from history but
  does **not** un-expose it if it was already fetched/forked.
- **Key rotation** to a fresh debug keystore stops future exposure, but every
  installed APK signed with the old key must then be **uninstalled and
  reinstalled** (sideloaded installs have no Play-style key-rotation flow), and
  the secret, the signature-gate fingerprint and this document must all change
  together.

Recommendation: rotate **after** publishing a final release from the current key
that tells users to reinstall, then purge history.

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
