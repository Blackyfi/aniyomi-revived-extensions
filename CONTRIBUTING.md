# Contributing

Extensions run inside the app with no sandbox, so reviewing what goes in is the only real protection users have. Please keep that in mind when you open a pull request.

## Rules

A pull request that breaks one of these won't be merged.

1. **Requests go to the source's own site.** Every `GET`, `POST` or `client.newCall(...)` must target a domain written in the source, or the `baseUrl` the user can change. Don't build request URLs from decoded, decrypted or downloaded strings.
2. **No obfuscation.** No base64/hex/xor decoding that gets executed, no reflection or `Class.forName`, no `DexClassLoader`, no code downloaded at runtime. If a reviewer can't read it, it doesn't go in.
3. **No extra permissions.** Don't add Android permissions, services or activities, unless a `UrlActivity` for deep links is really needed. Use the app's `NetworkHelper` client, not raw sockets.
4. **Source only.** Never add a prebuilt APK. CI builds everything on the `repo` branch from `main`.
5. **Bump `extVersionCode` with every change**, so users get the update. Only bump `versionId` when a site changes its URLs in a way that breaks existing entries, since it creates a new source id.

## Before you open a PR

- [ ] Every request URL comes from a domain in the source or from `baseUrl`.
- [ ] No reflection, dynamic loading or decoded strings that get run.
- [ ] DTO fields have defaults or are nullable, so a field added or removed by the site doesn't break parsing.
- [ ] Manga and chapter URLs are stored with `setUrlWithoutDomain(...)`, so a domain change doesn't break libraries.
- [ ] `extName`, `extClass`, `extVersionCode` and `isNsfw` are correct.
- [ ] No new permissions, activities or services.

## Style

- Official Kotlin style. Prefer a JSON API (`HttpSource` with `parseAs`) over scraping HTML when the site has one.
- For HTML sites, extend a theme from `lib-multisrc` if one fits. Add a new theme only when at least two sites share it.
- `ParsedHttpSource` is fine for simple sites, but `HttpSource` is preferred.
