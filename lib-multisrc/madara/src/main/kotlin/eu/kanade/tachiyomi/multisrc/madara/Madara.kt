package eu.kanade.tachiyomi.multisrc.madara

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Shared base class for the (very common) WordPress "Madara" manga theme.
 *
 * The whole point of a theme base class: every CSS selector and request shape lives here, ONCE.
 * A derived site is typically ~10 lines (name/baseUrl/lang + the rare override). When Madara
 * sites change their markup, you fix one `open` member here and every derived source is repaired.
 *
 * Extends [ParsedHttpSource], which already implements the *Parse(Response) methods in terms of
 * the selector + fromElement members below (see source-api ParsedHttpSource.kt:23-200).
 */
abstract class Madara(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {

    override val supportsLatest = true

    // ---- Centralized, overridable selectors / fragments (override only when a site deviates) ----
    protected open val mangaSubString = "manga"
    protected open val mangaEntrySelector = "div.page-item-detail.manga"
    protected open val mangaUrlSelector = "div.post-title a"
    protected open val mangaThumbnailSelector = "img"
    protected open val nextPageSelector: String? = "div.nav-previous, a.nextpostslink"
    protected open val chapterEntrySelector = "li.wp-manga-chapter"
    protected open val pageImgSelector = "div.page-break img, li.blocks-gallery-item img"

    // ---- Requests ----
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/$mangaSubString/page/$page/?m_orderby=views", headers)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/$mangaSubString/page/$page/?m_orderby=latest", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/page/$page/?s=$query&post_type=wp-manga", headers)

    // ---- Selectors (the three listings share one selector; override `mangaEntrySelector` to change all) ----
    override fun popularMangaSelector() = mangaEntrySelector
    override fun searchMangaSelector() = mangaEntrySelector
    override fun latestUpdatesSelector() = mangaEntrySelector

    override fun popularMangaNextPageSelector() = nextPageSelector
    override fun searchMangaNextPageSelector() = nextPageSelector
    override fun latestUpdatesNextPageSelector() = nextPageSelector

    // ---- Element mappers (shared once) ----
    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst(mangaUrlSelector)!!
        setUrlWithoutDomain(link.attr("href")) // domain-change safe (HttpSource.setUrlWithoutDomain)
        title = link.text()
        thumbnail_url = element.selectFirst(mangaThumbnailSelector)
            ?.let { it.absUrl("data-src").ifEmpty { it.absUrl("src") } }
    }

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)

    // ---- Details ----
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("div.post-title h1, div.post-title h3")?.text().orEmpty()
        description = document.selectFirst("div.description-summary div.summary__content, div.summary_content div.post-content_item")?.text()
        genre = document.select("div.genres-content a").joinToString { it.text() }
        author = document.selectFirst("div.author-content a")?.text()
        artist = document.selectFirst("div.artist-content a")?.text()
        thumbnail_url = document.selectFirst("div.summary_image img")
            ?.let { it.absUrl("data-src").ifEmpty { it.absUrl("src") } }
    }

    // ---- Chapters ----
    override fun chapterListSelector() = chapterEntrySelector

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val link = element.selectFirst("a")!!
        setUrlWithoutDomain(link.attr("href"))
        name = link.text()
    }

    // ---- Pages ----
    override fun pageListParse(document: Document): List<Page> =
        document.select(pageImgSelector).mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("data-src").ifEmpty { img.absUrl("src") })
        }

    // Madara pages already carry absolute imageUrls, so imageUrlParse is never invoked.
    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException()

    // This fork's HttpSource declares chapterPageParse abstract (HttpSource.kt:287); page-based
    // Madara sources never use it, so provide a default so derived sources stay ~30 lines.
    override fun chapterPageParse(response: Response): SChapter =
        throw UnsupportedOperationException()
}
