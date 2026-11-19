# Building

You don't need any of this to use the extensions. It's only for building them yourself.

## Requirements

- JDK 17
- Android SDK with `platforms;android-35` and `build-tools;35.0.0`. Point `ANDROID_HOME` at it, or create a `local.properties` file with `sdk.dir=<path to the SDK>` (it's gitignored).
- Python 3, for the scripts in `tools/`. Icon extraction also uses `aapt2` from build-tools, and Pillow if an icon is WEBP only.

Extensions compile against `lib-stub`, a small copy of the app's source API, so you don't need the app's code to build them. The app provides the real classes at runtime.

## Build

```bash
./gradlew :src:all:mangadex:assembleRelease   # one source
./gradlew assembleRelease                      # everything
```

The APKs end up in `src/<lang>/<name>/build/outputs/apk/release/`.

To generate the repo files the way CI does:

```bash
mkdir -p repo/apk repo/icon
find . -path '*/build/outputs/apk/release/*.apk' -exec cp {} repo/apk/ \;
python3 tools/extract_icons.py repo/apk repo/icon
python3 tools/create_repo.py repo/apk repo/index.json repo/index.min.json --src-root .
```

## Tests

The MangaDex and Madara unit tests run against the real `source-api` and `core:common` instead of the stub. Publish them from the app checkout first:

```bash
# in aniyomi-revived
./gradlew --init-script publish-source-api.init.gradle.kts \
  :source-api:publishToMavenLocal :core:common:publishToMavenLocal
```

Then run `./gradlew test` here.

## Signing

Release APKs are signed in CI with one key, stored as GitHub Actions secrets. Never commit a keystore (`*.keystore` and `*.jks` are gitignored).

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_B64` | The keystore, base64 encoded |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias (`arext`) |
| `SIGNING_KEY_PASSWORD` | Key password |

CI computes the certificate's SHA-256 fingerprint (lowercase, no colons) and writes it into `repo.json`. The app trusts an extension only when its signing certificate matches that fingerprint, so changing the key means every user has to trust the repo again.

To check the fingerprint of a local keystore:

```bash
keytool -list -v -keystore ci.keystore -alias arext | awk '/SHA256:/ {print $2}' | tr -d ':' | tr 'A-F' 'a-f'
```

To check a published APK:

```bash
apksigner verify --print-certs some-extension.apk
```
