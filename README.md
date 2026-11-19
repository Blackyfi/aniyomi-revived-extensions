# Aniyomi Revived Extensions

Manga extensions for [Aniyomi Revived](https://github.com/Blackyfi/aniyomi-revived). Each extension is a small APK that adds one source to the app. Nothing here is bundled with the app: you add this repo once, then install only the sources you want.

Looking for anime? Those live in [aniyomi-revived-anime-extensions](https://github.com/Blackyfi/aniyomi-revived-anime-extensions).

## Adding the repo

In the app, go to **Settings → Browse → Extension repos → Add** and paste:

```
https://raw.githubusercontent.com/Blackyfi/aniyomi-revived-extensions/repo/index.min.json
```

The extensions then show up under **Browse → Extensions**.

## Sources

| Source | Language | Notes |
|---|---|---|
| MangaDex | Multi | Uses the official MangaDex API |
| Komga | Multi | For your own Komga server |
| MangaVault | Multi | For your own MangaVault server |
| WeebCentral | English | |
| Komik Cast | Indonesian | |
| Manhwa Indo | Indonesian | |

The repo prefers sources that rarely break: sites with a real API, self-hosted servers, and sites built on shared themes like Madara or MangaThemesia, where one fix repairs many sites at once. Aggregators that keep changing domains and sites behind heavy Cloudflare checks are left out on purpose.

## Is it safe?

Extensions run inside the app with the same permissions as the app itself. Android doesn't sandbox them, so an extension can do anything the app can. That's why every change here is reviewed by hand before it ships:

- Sources only talk to the site they're for (or the server address you set yourself).
- No obfuscated code, no reflection and no code downloaded at runtime.
- APKs are built by GitHub Actions straight from this repository, never uploaded by hand.

Every extension is signed with the same key. The app reads its fingerprint from [`repo.json`](https://raw.githubusercontent.com/Blackyfi/aniyomi-revived-extensions/repo/repo.json) and only trusts extensions that match it:

```
e4bbc0829bf2b1ef674b4772407c93898253620c5bebca3b3ddb372b6863ca9b
```

## How it's organised

- `main` has the source code, `repo` has what the app downloads (`index.min.json`, `repo.json`, the APKs and icons). CI rebuilds `repo` on every push to `main`.
- `src/<lang>/<name>/` holds one source per folder.
- `lib-multisrc/<theme>/` holds the shared theme code (Madara, MangaThemesia).
- `lib-stub/` is a compile-only copy of the app's source API, so extensions build without the whole app.
- `tools/` has the scripts that build the index and extract icons.

To build it yourself, see [SETUP.md](SETUP.md). To add or fix a source, see [CONTRIBUTING.md](CONTRIBUTING.md).

## Disclaimer

This repository isn't affiliated with any of the content providers above, and it hosts no content.

## License

[Apache License 2.0](LICENSE), same as the app.
