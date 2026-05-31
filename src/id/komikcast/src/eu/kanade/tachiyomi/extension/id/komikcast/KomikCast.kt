package eu.kanade.tachiyomi.extension.id.komikcast

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request

/**
 * Komik Cast — a long-running Indonesian MangaThemesia site.
 *
 * Deviations from the theme defaults: the catalogue lives under `/daftar-komik` (not `/manga`),
 * popular sorting uses `?sortby=popular`, and search uses the same listing path with a `?title=`
 * query rather than the generic WordPress `?s=` search.
 */
class KomikCast : MangaThemesia(
    name = "Komik Cast",
    baseUrl = "https://komikcast.cz",
    lang = "id",
    mangaUrlDirectory = "/daftar-komik",
) {
    override val seriesOrderByPopular = "?sortby=popular"
    override val seriesOrderByLatest = "?sortby=update"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl$mangaUrlDirectory/${pagePath(page)}?title=$query", headers)
}
