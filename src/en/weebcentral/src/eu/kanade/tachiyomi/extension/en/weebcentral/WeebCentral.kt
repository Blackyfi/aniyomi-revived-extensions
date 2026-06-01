package eu.kanade.tachiyomi.extension.en.weebcentral

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Standalone English scraper for WeebCentral (https://weebcentral.com).
 *
 * The site is server-rendered with HTMX fragments. Catalogue browsing and search both go through
 * the same `/search/data` endpoint (a GET form). Series metadata lives on the `/series/{id}/{slug}`
 * page; the full chapter list and the page images are served as separate HTMX fragments
 * (`/series/{id}/full-chapter-list` and `/chapters/{id}/images`).
 *
 * Every CSS selector is centralized as a named `private val` below so a markup change is a
 * one-line fix.
 */
class WeebCentral : HttpSource() {

    override val name = "WeebCentral"

    override val baseUrl = "https://weebcentral.com"

    override val lang = "en"

    override val supportsLatest = true

    // ============================== Selectors ===============================
    // Centralized so a site markup change is a single-line edit. Flagged assumptions, where any,
    // are noted inline; these were derived from live markup on 2026-05-29.

    // --- /search/data list items ---
    private val searchMangaSelector = "article:has(> section a[href*=\"/series/\"])"
    private val searchMangaLinkSelector = "a[href*=\"/series/\"]"
    private val searchMangaTitleSelector = "section:last-child a[href*=\"/series/\"]"
    private val searchMangaThumbnailSelector = "section:first-child img"
    // "View More Results..." button carries an hx-get; its presence signals another page exists.
    private val searchNextPageSelector = "button[hx-get*=\"/search/data\"]"

    // --- /series/{id} details page ---
    private val detailsTitleSelector = "h1"
    private val detailsThumbnailSelector = "section img[alt\$=\"cover\"], main img[alt\$=\"cover\"]"
    private val detailsInfoItemSelector = "ul > li"
    private val detailsAuthorLabel = "Author"
    private val detailsTagLabel = "Tag"
    private val detailsStatusLabel = "Status"
    private val detailsDescriptionSelector = "li:has(strong:matchesOwn((?i)^Description) ) p"

    // --- /series/{id}/full-chapter-list fragment ---
    private val chapterSelector = "a[href*=\"/chapters/\"]"
    private val chapterNameSelector = "span.grow > span:first-child"
    private val chapterDateSelector = "time[datetime]"

    // --- /chapters/{id}/images fragment ---
    private val pageImageSelector = "section img[src], img[src]"

    // ============================== Endpoints ===============================

    private fun searchDataUrl(
        page: Int,
        query: String = "",
        sort: String = "Popularity",
    ): String {
        val offset = (page - 1) * SEARCH_LIMIT
        return baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addPathSegment("data")
            addQueryParameter("limit", SEARCH_LIMIT.toString())
            addQueryParameter("offset", offset.toString())
            if (query.isNotBlank()) {
                addQueryParameter("text", query)
            }
            addQueryParameter("sort", sort)
            addQueryParameter("order", "Descending")
            addQueryParameter("official", "Any")
            addQueryParameter("display_mode", "Full Display")
        }.build().toString()
    }

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        return GET(searchDataUrl(page, sort = "Popularity"), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = searchDataParse(response)

    // ============================== Latest ==============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(searchDataUrl(page, sort = "Latest Updates"), headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = searchDataParse(response)

    // ============================== Search ==============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sort = if (query.isBlank()) "Popularity" else "Best Match"
        return GET(searchDataUrl(page, query = query, sort = sort), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = searchDataParse(response)

    private fun searchDataParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(searchMangaSelector).map { element ->
            SManga.create().apply {
                val link = element.selectFirst(searchMangaLinkSelector)!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = element.selectFirst(searchMangaTitleSelector)?.text()
                    ?: link.attr("href").substringAfterLast('/').replace('-', ' ').trim()
                thumbnail_url = element.selectFirst(searchMangaThumbnailSelector)?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst(searchNextPageSelector) != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(detailsTitleSelector)?.text().orEmpty()
            thumbnail_url = document.selectFirst(detailsThumbnailSelector)?.absUrl("src")
            description = document.selectFirst(detailsDescriptionSelector)?.text()

            val infoItems = document.select(detailsInfoItemSelector)

            author = infoItems.infoValue(detailsAuthorLabel)
            genre = infoItems.firstOrNull { it.label().startsWith(detailsTagLabel, ignoreCase = true) }
                ?.select("span a, a")
                ?.joinToString { it.text() }
                ?.ifBlank { null }
            status = infoItems.infoValue(detailsStatusLabel).toStatus()
        }
    }

    private fun Elements.infoValue(label: String): String? {
        return firstOrNull { it.label().startsWith(label, ignoreCase = true) }
            ?.let { item ->
                item.select("span, a").firstOrNull { it.text().isNotBlank() }?.text()
                    ?: item.ownText().substringAfter(':').trim().ifBlank { null }
            }
    }

    private fun Element.label(): String =
        selectFirst("strong")?.text()?.trim().orEmpty()

    private fun String?.toStatus(): Int = when (this?.trim()?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "complete", "completed" -> SManga.COMPLETED
        "hiatus", "on hiatus" -> SManga.ON_HIATUS
        "canceled", "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    /** WeebCentral serves the chapter list as a separate HTMX fragment keyed by series id. */
    override fun chapterListRequest(manga: SManga): Request {
        val seriesId = manga.url.seriesId()
        return GET("$baseUrl/series/$seriesId/full-chapter-list", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(chapterSelector).map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst(chapterNameSelector)?.text()
                    ?: element.text().trim()
                date_upload = element.selectFirst(chapterDateSelector)
                    ?.attr("datetime")
                    .parseDate()
            }
        }
    }

    // ============================== Pages ==============================

    /** Page images are an HTMX fragment keyed by chapter id. */
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.chapterId()
        val url = "$baseUrl/chapters/$chapterId/images".toHttpUrl().newBuilder()
            .addQueryParameter("is_prev", "False")
            .addQueryParameter("reading_style", "long_strip")
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select(pageImageSelector)
            .mapNotNull { it.absUrl("src").ifBlank { null } }
            .distinct()
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()

    override fun chapterPageParse(response: Response): SChapter =
        throw UnsupportedOperationException()

    // ============================== Helpers ==============================

    /** Stored manga url looks like `/series/{id}/{slug}`. */
    private fun String.seriesId(): String =
        substringAfter("/series/").substringBefore('/')

    /** Stored chapter url looks like `/chapters/{id}`. */
    private fun String.chapterId(): String =
        substringAfter("/chapters/").substringBefore('/').substringBefore('?')

    private fun String?.parseDate(): Long {
        if (this.isNullOrBlank()) return 0L
        return try {
            DATE_FORMAT.parse(substringBefore('.').removeSuffix("Z") + "Z")?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    companion object {
        private const val SEARCH_LIMIT = 32

        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
