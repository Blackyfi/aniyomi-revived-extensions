package eu.kanade.tachiyomi.multisrc.mangathemesia

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Shared base class for the WordPress "MangaThemesia" manga theme (a.k.a. WP Manga Stream),
 * which powers a large family of sites (e.g. KomikCast, Kiryuu, Manhwaindo, etc.).
 *
 * Like [eu.kanade.tachiyomi.multisrc.madara.Madara], every CSS selector and request shape lives
 * here ONCE as an `open` member so a derived site is typically ~30 lines (name/baseUrl/lang +
 * the rare override). MangaThemesia markup differs from Madara: listings use `.listupd .bs .bsx`,
 * details live under `.seriestucontent .infox`, and reader pages are emitted as an inline
 * `ts_reader.run({...})` JSON blob rather than as plain <img> tags.
 *
 * Extends [ParsedHttpSource], which already implements the *Parse(Response) methods in terms of
 * the selector + fromElement members below (see source-api ParsedHttpSource.kt:23-200).
 */
abstract class MangaThemesia(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    protected open val mangaUrlDirectory: String = "/manga",
) : ParsedHttpSource() {

    override val supportsLatest = true

    protected val json: Json = Json { ignoreUnknownKeys = true }

    // ---- Centralized, overridable selectors / fragments (override only when a site deviates) ----
    protected open val seriesOrderByPopular = "?order=popular"
    protected open val seriesOrderByLatest = "?order=update"

    // Listings: MangaThemesia themes use either the `.utao` carousel layout or the `.listupd` grid.
    protected open val mangaEntrySelector = ".utao .uta .imgu, .listupd .bs .bsx"
    protected open val mangaUrlSelector = "a"
    protected open val mangaTitleSelector = "a"
    protected open val mangaThumbnailSelector = "img"
    protected open val nextPageSelector = "div.pagination .next, div.hpage .r"

    // Details: the series info card.
    protected open val seriesDetailsSelector = ".seriestucontent, .seriestucontant, .bigcontent, .animefull, .main-info"
    protected open val seriesTitleSelector = "h1.entry-title, .seriestuheader h1, .infox h1"
    protected open val seriesDescriptionSelector = ".desc, .entry-content[itemprop=description], .seriestucontent .entry-content"
    protected open val seriesGenreSelector = ".seriestugenre a, .mgen a, .infox .genre-info a, span.mgen a"
    protected open val seriesAuthorSelector = ".infotable tr:contains(Author) td:last-child, .tsinfo .imptdt:contains(Author) i, .fmed b:contains(Author)+span"
    protected open val seriesArtistSelector = ".infotable tr:contains(Artist) td:last-child, .tsinfo .imptdt:contains(Artist) i, .fmed b:contains(Artist)+span"
    protected open val seriesThumbnailSelector = ".infomanga > div[itemprop=image] img, .thumb img, .seriestucontent .thumb img, .infox img"

    // Chapters.
    protected open val chapterListSelector = "#chapterlist li, .cl li, .clstyle li"
    protected open val chapterUrlSelector = "a"
    protected open val chapterNameSelector = ".eph-num a, .chapternum, a"

    // Reader: the inline JSON blob `ts_reader.run({...})`.
    protected open val pageScriptSelector = "script:containsData(ts_reader.run)"

    // ---- Requests ----
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl$mangaUrlDirectory/${pagePath(page)}$seriesOrderByPopular", headers)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl$mangaUrlDirectory/${pagePath(page)}$seriesOrderByLatest", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/page/$page/?s=$query", headers)

    protected open fun pagePath(page: Int): String = if (page > 1) "page/$page/" else ""

    // ---- Listing selectors (the three listings share one selector) ----
    override fun popularMangaSelector() = mangaEntrySelector
    override fun searchMangaSelector() = mangaEntrySelector
    override fun latestUpdatesSelector() = mangaEntrySelector

    override fun popularMangaNextPageSelector(): String? = nextPageSelector
    override fun searchMangaNextPageSelector(): String? = nextPageSelector
    override fun latestUpdatesNextPageSelector(): String? = nextPageSelector

    // ---- Element mappers (shared once) ----
    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst(mangaUrlSelector)!!
        setUrlWithoutDomain(link.attr("href")) // domain-change safe (HttpSource.setUrlWithoutDomain)
        title = element.selectFirst(mangaTitleSelector)?.attr("title")?.ifEmpty { null }
            ?: element.selectFirst(mangaTitleSelector)?.text().orEmpty()
        thumbnail_url = element.selectFirst(mangaThumbnailSelector)
            ?.let { it.absUrl("data-src").ifEmpty { it.absUrl("src") } }
    }

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)

    // ---- Details ----
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val info = document.selectFirst(seriesDetailsSelector) ?: document
        title = info.selectFirst(seriesTitleSelector)?.text().orEmpty()
        description = info.selectFirst(seriesDescriptionSelector)?.text()
        genre = info.select(seriesGenreSelector).joinToString { it.text() }
        author = info.selectFirst(seriesAuthorSelector)?.text()
        artist = info.selectFirst(seriesArtistSelector)?.text()
        thumbnail_url = info.selectFirst(seriesThumbnailSelector)
            ?.let { it.absUrl("data-src").ifEmpty { it.absUrl("src") } }
    }

    // ---- Chapters ----
    override fun chapterListSelector() = chapterListSelector

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val link = element.selectFirst(chapterUrlSelector)!!
        setUrlWithoutDomain(link.attr("href"))
        name = element.selectFirst(chapterNameSelector)?.text()?.ifEmpty { null }
            ?: link.text()
    }

    // ---- Pages ----
    /**
     * MangaThemesia reader pages are emitted by an inline script:
     *   `ts_reader.run({ "sources": [ { "images": [ "url1", "url2", ... ] } ] });`
     * We extract the JSON object passed to `ts_reader.run(...)` and read every `images` array.
     * A regex isolates the balanced `{...}` so a trailing `;` or extra script content can't break it,
     * with a defensive fallback to scanning raw `https?://...` image-like tokens if parsing fails.
     */
    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst(pageScriptSelector)?.data().orEmpty()
        val jsonText = TS_READER_REGEX.find(script)?.groupValues?.get(1)

        if (jsonText != null) {
            runCatching {
                val root = json.parseToJsonElement(jsonText).jsonObject
                val sources = root["sources"]?.jsonArray ?: JsonArray(emptyList())
                val images = sources.flatMap { source ->
                    (source as? JsonObject)?.get("images")?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?: emptyList()
                }
                if (images.isNotEmpty()) {
                    return images.mapIndexed { i, url -> Page(i, imageUrl = url) }
                }
            }
        }

        // Fallback: scrape image-like URLs straight out of the script payload.
        return IMAGE_URL_REGEX.findAll(script)
            .map { it.value.trim('"', '\\', ' ') }
            .toList()
            .mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    // Pages already carry absolute imageUrls, so imageUrlParse is never invoked.
    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException()

    // chapterPageParse is abstract on this fork's HttpSource (source-api HttpSource.kt:287) but
    // MangaThemesia sites list every chapter on the series page — there is no per-chapter page.
    override fun chapterPageParse(response: Response): SChapter =
        throw UnsupportedOperationException()

    companion object {
        private val TS_READER_REGEX = Regex("""ts_reader\.run\((\{.*\})\)\s*;?""", RegexOption.DOT_MATCHES_ALL)
        private val IMAGE_URL_REGEX = Regex("""https?://[^"'\\\s]+\.(?:jpe?g|png|webp|gif)""", RegexOption.IGNORE_CASE)
    }
}
