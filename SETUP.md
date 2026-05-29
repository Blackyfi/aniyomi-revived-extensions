# Setup & Build

This repo is a **scaffold**. Three things must be wired before `./gradlew assembleRelease` succeeds.
None require touching the extension source — they are environment/coordinate wiring.

## 1. Android SDK + JDK 17

- JDK 17 (Temurin) — already installed if you followed the app setup.
- Android SDK with `platform-android-35` and `build-tools;35.0.0`. Set `ANDROID_HOME` and create
  `local.properties` with `sdk.dir=<path>` (gitignored). `aapt2` from build-tools is also used by
  `tools/create_repo.py`.

## 2. The `source-api` dependency (the load-bearing step)

Extensions compile `compileOnly` against the app's `source-api`; the host app supplies the real
classes at runtime. Pick ONE wiring and update `gradle/libs.versions.toml`:

**Option A — JitPack from your app fork (simplest, fully FOSS).**
Push `aniyomi-revived` to GitHub, then in `libs.versions.toml`:
```toml
source-api = { module = "com.github.YOUR_GH_USER.aniyomi-revived:source-api-android", version = "<commit-sha-or-tag>" }
```
JitPack builds the module on first request. Verify the exact published artifact id
(`source-api-android` vs `source-api`) on jitpack.io after the first build.

**Option B — Local Maven (offline / fastest iteration).**
In the app tree, publish `:source-api` to mavenLocal (`./gradlew :source-api:publishToMavenLocal`),
add `mavenLocal()` to `settings.gradle.kts` repositories, and point the catalog at that coordinate.

**Option C — Composite build (monorepo-style).**
`includeBuild("../aniyomi-revived")` in `settings.gradle.kts` and depend on the project. Tightest
coupling; good if you keep app + extensions in lockstep.

> Tachiyomi/keiyoushi historically used a tiny hand-written `extensions-lib` stub instead of the
> full `source-api` so extensions don't drag in Compose/RxJava at compile time. If JitPack pulls too
> much, create a minimal stub module exposing just the interfaces used here.

## 3. Signing key

```bash
keytool -genkeypair -v -keystore ci.keystore -alias arext -keyalg RSA -keysize 2048 -validity 10000
# Get the fingerprint that must go into repo.json:
keytool -list -v -keystore ci.keystore -alias arext | grep -A1 SHA256:
```
Store the keystore (base64) and passwords as GitHub Actions secrets:
`SIGNING_KEYSTORE_B64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
**Never commit the keystore** (it is gitignored).

## 4. Build

```bash
./gradlew :src:all:mangadex:assembleRelease   # one source
./gradlew assembleRelease                      # everything
python3 tools/create_repo.py repo/apk repo/index.json repo/index.min.json
```

## 5. Verify end-to-end

Add `https://raw.githubusercontent.com/YOUR_GH_USER/aniyomi-revived-extensions/repo/index.min.json`
in the app. Install MangaDex; confirm it loads as **trusted** (not "Untrusted") — that proves the
`repo.json` fingerprint == your signing cert SHA-256, end to end.

## Known scaffold gaps (see tasks.md)

- `tools/create_repo.py` emits `sources: []` — an **Inspector** step (loads the dex to read each
  source's id/lang/name/baseUrl) is still needed for pre-install source listings.
- Launcher icons are not yet extracted to `repo/icon/<pkg>.png`.
- `src/en/examplemadara` is a placeholder pointing at `example.invalid` — replace or delete.
