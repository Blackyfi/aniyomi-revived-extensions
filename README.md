# Aniyomi Revived — Extensions

Open-source content **extensions** for the [aniyomi-revived](https://github.com/YOUR_GH_USER/aniyomi-revived) app.
Each extension is an Android APK whose classes implement the app's `source-api`. Extensions are
**not** bundled in the app — users add this repository and install the sources they want.

> **Status:** scaffold / work-in-progress. MangaDex (manga) is implemented as the reference source;
> a Madara theme base is included for cheap, durable manhwa volume. See the roadmap below.

## Add this repo to the app

In the app: **Settings → Browse → Extension repos → Add**, then paste:

```
https://raw.githubusercontent.com/YOUR_GH_USER/aniyomi-revived-extensions/repo/index.min.json
```

(The app validates the URL ends in `/index.min.json`, fetches `repo.json` for the signing
fingerprint, then lists extensions from `index.min.json`.)

## ⚠️ Trust model — read this

Extensions run **inside the app process with the app's full permissions — there is no sandbox.**
A trusted extension can read app data, use the shared network/cookie session, and run arbitrary
code on your device. The app's only enforced guarantees are:

1. **Unsigned APKs are rejected.**
2. An extension is **trusted** iff it was signed by **this repo's single signing key**, whose
   SHA-256 fingerprint is published in [`repo.json`](repo.json.template) and below.

**Therefore the human review of every line we ship _is_ the security boundary.** We commit to:
- Mandatory review of **every network call** — requests may only target domains that are literals
  in the source (or the user-overridable `baseUrl`). No URLs assembled from decoded/decrypted data.
- **Zero obfuscation**: no base64/hex/xor decode-then-run, no reflection, no dynamic dex/code download.
- **Build-from-source-only CI**: the published `repo` branch is a deterministic function of audited
  `main` source. We never accept prebuilt APKs.
- **One signing key**, pinned via `repo.json`; rotation is a rare, announced event.

**Our signing key fingerprint (verify before trusting):** `PUBLISH_AFTER_FIRST_CI_RUN`

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full review policy and [SETUP.md](SETUP.md) to build.

## Repository structure

Two branches:
- **`main`** — human-reviewed source (this tree).
- **`repo`** — CI-generated artifacts the app consumes: `index.min.json`, `repo.json`, `apk/`, `icon/`.

```
lib-multisrc/<theme>/   shared theme base classes (one fix repairs many sites) — e.g. madara
src/<lang>/<name>/      one Gradle module per source — e.g. src/all/mangadex
common.gradle           convention script: ext{} → loader-valid signed APK
tools/create_repo.py    generates the index from built APKs
.github/workflows/      build → sign → index → publish to the repo branch
```

## Roadmap (durability-first)

Prioritizes API-backed and theme-backed sources that rarely break, per the maintenance analysis
in the app's `PROFILE_REPORT.md`:

1. **MangaDex** — first-party JSON API ✅ (implemented)
2. **Madara theme base** + curated stable instances (manhwa volume) — base ✅, add sites
3. **MangaThemesia theme base** — second-largest cluster
4. **Self-hosted** (Komga / Suwayomi) — zero external breakage risk
5. **WeebCentral** — most popular reliable EN scraper
6. **AllAnime** (anime) — API-based

Deliberately **deferred/excluded**: domain-hopping aggregators (Manganato/Mangakakalot family),
heavy anti-bot/Cloudflare sources (MangaFire, GogoAnime), and dead sites (Comick). These break
constantly regardless of who maintains them.

## License

[Apache License 2.0](LICENSE) — same as the app. Individual sources are unaffiliated with the
content providers; this repository hosts no content.
