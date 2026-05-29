# Signing Key + CI Publish Runbook

This is a **runbook you execute manually** — nothing here generates a key, commits a secret, or
pushes. It produces one signing key, pins its SHA-256 fingerprint once in `repo.json`, and wires CI
to build → inspect → sign → index → publish to the `repo` branch.

## The trust model in one sentence

The app trusts an installed extension APK **iff** the SHA-256 of its signing certificate (lowercase,
no colons) equals the `meta.signingKeyFingerprint` in your repo's `repo.json` — or the user has
manually trusted the exact `pkg:versionCode:fingerprint` tuple. See
`MangaExtensionLoader.kt:408-422` (extracts the cert SHA-256 fingerprints) and
`TrustMangaExtension.kt:14-18` (trusted iff a fingerprint matches a configured repo's
`signingKeyFingerprint`), as cited in `EXTENSIONS_REPO_STARTER.md` §"How the app finds, trusts, and
loads your repo" and §6. **One key, generated once, pinned once, signs everything.**

The keystore lives **only** in CI secrets. The fingerprint (a public value) lives in `repo.json` and
your README so users can verify it.

---

## 1. Generate the keystore and extract the fingerprint

> Run these on your own machine. Keep `ci.keystore` **off** version control — it is already
> gitignored (see SETUP.md §3). Never commit it.

### 1a. Create the keystore (RSA 2048, 10000-day validity, alias `arext`)

```bash
keytool -genkeypair -v \
  -keystore ci.keystore \
  -alias arext \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

`keytool` will prompt for a keystore password, your name/org (the cert DN), and a key password. Use a
strong, distinct keystore password and key password and record them in your password manager — you
will paste them into GitHub secrets in step 2 and they can never be recovered if lost. (The
convention plugin `common.gradle:46-52` reads `CI_KEYSTORE_PASSWORD` / `CI_KEY_ALIAS` /
`CI_KEY_PASSWORD`, and the alias here must equal `CI_KEY_ALIAS` = `arext`.)

### 1b. Read the SHA-256 fingerprint

```bash
keytool -list -v -keystore ci.keystore -alias arext
```

In the output, find the line under `Certificate fingerprints:` that reads:

```
SHA256: AB:CD:EF:01:...:23   (32 colon-separated hex bytes, UPPERCASE)
```

### 1c. Convert to the form the loader compares (lowercase, no colons)

The loader compares `Hash.sha256` of the DER-encoded signing cert — a **64-char lowercase hex string
with no colons** (`MangaExtensionLoader.kt:408-422`; trust check `TrustMangaExtension.kt:14-18`).
`keytool` prints UPPERCASE with colons, so strip the colons and lowercase it.

**macOS / Linux (or Git Bash on Windows):**

```bash
keytool -list -v -keystore ci.keystore -alias arext \
  | grep -A1 'SHA256:' \
  | grep -oE '([0-9A-F]{2}:){31}[0-9A-F]{2}' \
  | tr -d ':' | tr 'A-F' 'a-f' | head -n1
```

**Windows PowerShell** (the user's platform):

```powershell
# Paste the SHA256 value keytool printed (with colons) and normalize it:
$raw = (keytool -list -v -keystore ci.keystore -alias arext | Select-String 'SHA256:').Line
$fp  = ($raw -replace '.*SHA256:\s*', '') -replace ':', ''
$fp.ToLower()
# -> 64 lowercase hex chars, no colons. THIS is the repo.json value.
```

Save that 64-char string — call it `<FINGERPRINT>`. It is what goes into `repo.json` (step 3) and is
what CI recomputes from the keystore (step 4) so the two always match.

---

## 2. Store the key + passwords as GitHub Actions secrets

CI reconstructs the keystore from a base64 secret, then signs. Set four repo-level secrets.

### 2a. Base64-encode the keystore

**Windows PowerShell** (the user's platform):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('ci.keystore')) | Set-Content -NoNewline ci.keystore.b64.txt
```

(macOS/Linux equivalent: `base64 -w0 ci.keystore > ci.keystore.b64.txt`.)

### 2b. `gh secret set` (run in the EXT repo working dir)

```bash
# The base64 of the keystore. --body @file avoids shell length/escaping issues with the long blob.
gh secret set SIGNING_KEYSTORE_B64    --body @ci.keystore.b64.txt

# The three credentials (you will be prompted to paste each value):
gh secret set SIGNING_STORE_PASSWORD       # the keystore password from step 1a
gh secret set SIGNING_KEY_ALIAS            # exactly: arext
gh secret set SIGNING_KEY_PASSWORD         # the key password from step 1a
```

PowerShell note: `gh secret set ... --body @file` works the same in PowerShell. To pipe a value
without a temp file: `Get-Content -Raw ci.keystore.b64.txt | gh secret set SIGNING_KEYSTORE_B64`.

After setting them, **delete the local plaintext artifacts**:

```powershell
Remove-Item ci.keystore.b64.txt
```

Keep `ci.keystore` itself in a safe, backed-up, offline location (it is the only copy of your
identity). Never commit it.

> Naming note: the GitHub **secret** names (`SIGNING_*`) differ from the Gradle **env var** names
> (`CI_*`) the build reads (`common.gradle:48-51`). The workflow in step 4 maps `SIGNING_*` secrets →
> `CI_*` env at the build step. Keep both naming sets exactly as shown.

---

## 3. Put the fingerprint into `repo.json`

The published `repo.json` shape is fixed by `ExtensionRepoDto.kt:6-17` (per EXTENSIONS_REPO_STARTER.md
§"repo.json shape"). The repo already ships `repo.json.template`:

```json
{
  "meta": {
    "name": "Aniyomi Revived Extensions",
    "shortName": "arext",
    "website": "https://github.com/YOUR_GH_USER/aniyomi-revived-extensions",
    "signingKeyFingerprint": "REPLACE_WITH_LOWERCASE_SHA256_OF_YOUR_SIGNING_CERT"
  }
}
```

Two fields must be filled:

1. `website` — replace `YOUR_GH_USER` with your GitHub username.
2. `signingKeyFingerprint` — replace `REPLACE_WITH_LOWERCASE_SHA256_OF_YOUR_SIGNING_CERT` with the
   `<FINGERPRINT>` from step 1c (64 lowercase hex chars, no colons).

The CI workflow in step 4 fills these automatically: it recomputes the fingerprint from the keystore
and substitutes it into the template, so the committed-by-CI `repo.json` on the `repo` branch always
carries the live cert's fingerprint. You only hand-edit `repo.json.template` if you want the value
visible in source (recommended for README cross-checking) — but never hand-edit the generated
`repo.json` on the `repo` branch.

---

## 4. Audited, SHA-pinned reference CI workflow

> **This is reference material only.** The live workflow file
> `.github/workflows/build_push.yml` is owned by a separate task (B3). Do **not** create or edit that
> file from this runbook. Use the SHAs below when authoring/reviewing it.
>
> Every action is pinned to a **commit SHA** (not a tag) per EXTENSIONS_REPO_STARTER.md §6.6 ("Pin
> every GitHub Action to a commit SHA, not a tag"). SHAs were resolved against github.com on
> 2026-05-29 by dereferencing each action's latest release tag to its commit. Re-verify before use;
> tags can be re-pointed but a pinned commit SHA cannot.

| Action | Tag | Commit SHA |
|---|---|---|
| `actions/checkout` | v6.0.2 | `de0fac2e4500dabe0009e67214ff5f5447ce83dd` |
| `actions/setup-java` | v5.2.0 | `be666c2fcd27ec809703dec50e508c2fdc7f6654` |
| `gradle/actions/setup-gradle` | v6.1.0 | `50e97c2cd7a37755bbfafc9c5b7cafaece252f6e` |
| `peaceiris/actions-gh-pages` | v4.1.0 | `84c30a85c19949d7eee79c4ff27748b70285e453` |

```yaml
name: Build & publish extensions repo

on:
  push:
    branches: [main]

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: write   # least privilege: only what's needed to push the repo branch

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout source (main)
        uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd  # v6.0.2

      - name: Set up JDK 17
        uses: actions/setup-java@be666c2fcd27ec809703dec50e508c2fdc7f6654  # v5.2.0
        with:
          distribution: temurin
          java-version: 17

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@50e97c2cd7a37755bbfafc9c5b7cafaece252f6e  # v6.1.0

      # ---- Reconstruct the signing keystore from the base64 CI secret ----
      - name: Decode signing keystore
        env:
          KEYSTORE_B64: ${{ secrets.SIGNING_KEYSTORE_B64 }}
        run: echo "$KEYSTORE_B64" | base64 -d > "$RUNNER_TEMP/ci.keystore"

      # ---- Build & sign all source APKs (build-from-source ONLY; EXTENSIONS_REPO_STARTER.md §6.4) --
      # Maps the SIGNING_* secrets to the CI_* env vars common.gradle:48-51 reads.
      - name: Assemble release APKs
        env:
          CI_KEYSTORE_PATH: ${{ runner.temp }}/ci.keystore
          CI_KEYSTORE_PASSWORD: ${{ secrets.SIGNING_STORE_PASSWORD }}
          CI_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
          CI_KEY_PASSWORD: ${{ secrets.SIGNING_KEY_PASSWORD }}
        run: ./gradlew assembleRelease

      # ---- Inspector: extract each APK's Source classes (id/lang/name/baseUrl) for sources[] ----
      - name: Run Inspector
        run: |
          mkdir -p repo/apk repo/icon
          for apk in $(find . -path '*/build/outputs/apk/release/*-release.apk'); do
            cp "$apk" "repo/apk/$(basename "$apk")"
            # tools/inspector.* — fills sources[]; see SETUP.md "Known scaffold gaps".
          done

      # ---- Compute the signing-cert SHA-256 fingerprint (== repo.json value) ----
      - name: Compute signing fingerprint
        id: fp
        env:
          CI_KEYSTORE_PATH: ${{ runner.temp }}/ci.keystore
          CI_KEYSTORE_PASSWORD: ${{ secrets.SIGNING_STORE_PASSWORD }}
          CI_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
        run: |
          # lowercase, no colons — matches the loader's Hash.sha256 of the signing cert
          # (MangaExtensionLoader.kt:408-422; trust check TrustMangaExtension.kt:14-18).
          FP=$(keytool -list -v -keystore "$CI_KEYSTORE_PATH" \
                 -alias "$CI_KEY_ALIAS" -storepass "$CI_KEYSTORE_PASSWORD" \
               | grep -A1 'SHA256:' | grep -oE '([0-9A-F]{2}:){31}[0-9A-F]{2}' \
               | tr -d ':' | tr 'A-F' 'a-f' | head -n1)
          echo "fingerprint=$FP" >> "$GITHUB_OUTPUT"

      # ---- Generate index.min.json (+ index.json) and repo.json ----
      - name: Generate index + repo metadata
        env:
          SIGNING_FINGERPRINT: ${{ steps.fp.outputs.fingerprint }}
        run: |
          python3 tools/create_repo.py repo/apk repo/index.json repo/index.min.json
          # Fill repo.json.template with the live fingerprint (shape: ExtensionRepoDto.kt:6-17).
          sed "s/REPLACE_WITH_LOWERCASE_SHA256_OF_YOUR_SIGNING_CERT/${SIGNING_FINGERPRINT}/" \
            repo.json.template > repo/repo.json

      # ---- Publish artifacts to the orphan 'repo' branch ----
      - name: Publish to repo branch
        uses: peaceiris/actions-gh-pages@84c30a85c19949d7eee79c4ff27748b70285e453  # v4.1.0
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: ./repo
          publish_branch: repo
          force_orphan: true
```

---

## 5. Verify the chain end-to-end ("full circle")

This closes the loop described in EXTENSIONS_REPO_STARTER.md §5 ("How the app consumes it / full
circle"). The same cert SHA-256 must appear in three places:

1. **keytool** (step 1c) — `keytool -list -v ... | (strip colons, lowercase)` → `<FINGERPRINT>`.
2. **`repo.json`** (steps 3 & 4) — `meta.signingKeyFingerprint` on the published `repo` branch.
3. **The APK's actual signature** — what the loader extracts at install
   (`MangaExtensionLoader.kt:408-422`) and compares (`TrustMangaExtension.kt:14-18`).

### 5a. Confirm `keytool == repo.json` (CI-internal)

In the CI run, `steps.fp.outputs.fingerprint` is computed from the **same keystore** that signed the
APKs and is then substituted into `repo.json`. So by construction (1) == (2). To eyeball it: open the
published `repo/repo.json` and confirm `signingKeyFingerprint` equals your saved `<FINGERPRINT>`.

```powershell
# After CI publishes, fetch the live repo.json and compare to your saved fingerprint:
$expected = '<FINGERPRINT>'   # from step 1c
$live = (Invoke-RestMethod 'https://raw.githubusercontent.com/YOUR_GH_USER/aniyomi-revived-extensions/repo/repo.json').meta.signingKeyFingerprint
if ($live -eq $expected) { 'MATCH' } else { "MISMATCH: live=$live expected=$expected" }
```

### 5b. Confirm a published APK actually carries that cert (optional, strongest check)

Download one published APK and read its signer cert SHA-256 with `apksigner` (Android build-tools);
it must equal `<FINGERPRINT>`:

```bash
apksigner verify --print-certs repo/apk/<some>-release.apk | grep -i 'SHA-256'
# Strip colons + lowercase the printed digest; it must equal <FINGERPRINT>.
```

### 5c. Confirm in the app (the definitive proof)

Add `https://raw.githubusercontent.com/YOUR_GH_USER/aniyomi-revived-extensions/repo/index.min.json`
as a repo in the app (regex-validated, `/index.min.json` stripped to `baseUrl`;
`CreateMangaExtensionRepo.kt:15,23`). Install an extension (e.g. MangaDex). If it loads as **trusted**
(not "Untrusted"), then (1) == (2) == (3) and the fingerprint chain is correct end to end. An
"Untrusted" prompt means `repo.json.signingKeyFingerprint` does not equal the cert that actually
signed the APK — re-run steps 1c → 3 → 4.
