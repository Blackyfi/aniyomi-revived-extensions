package eu.kanade.tachiyomi.extension.id.manhwaindo

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

/**
 * Manhwa Indo — an Indonesian MangaThemesia site focused on manhwa.
 *
 * Deviations from the theme defaults: the catalogue lives under `/series` (not `/manga`).
 * Everything else (popular/latest sorting, the `.listupd .bs .bsx` grid, the `#chapterlist`
 * chapter list and the `ts_reader.run({...})` reader payload) matches the centralized base,
 * so no further overrides are required.
 */
class ManhwaIndo : MangaThemesia(
    name = "Manhwa Indo",
    baseUrl = "https://manhwaindo.app",
    lang = "id",
    mangaUrlDirectory = "/series",
)
