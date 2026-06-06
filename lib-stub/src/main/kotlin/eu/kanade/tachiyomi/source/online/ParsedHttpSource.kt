@file:Suppress("unused")

package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Stub mirror of the host ParsedHttpSource (jsoup-based). The lib-multisrc theme bases (Madara,
 * MangaThemesia) extend this and override the selector / *FromElement / *Parse(Document) members.
 */
@Suppress("MemberVisibilityCanBePrivate")
abstract class ParsedHttpSource : HttpSource() {

    override fun popularMangaParse(response: Response): MangasPage = throw RuntimeException("stub")
    protected abstract fun popularMangaSelector(): String
    protected abstract fun popularMangaFromElement(element: Element): SManga
    protected abstract fun popularMangaNextPageSelector(): String?

    override fun searchMangaParse(response: Response): MangasPage = throw RuntimeException("stub")
    protected abstract fun searchMangaSelector(): String
    protected abstract fun searchMangaFromElement(element: Element): SManga
    protected abstract fun searchMangaNextPageSelector(): String?

    override fun latestUpdatesParse(response: Response): MangasPage = throw RuntimeException("stub")
    protected abstract fun latestUpdatesSelector(): String
    protected abstract fun latestUpdatesFromElement(element: Element): SManga
    protected abstract fun latestUpdatesNextPageSelector(): String?

    override fun mangaDetailsParse(response: Response): SManga = throw RuntimeException("stub")
    protected abstract fun mangaDetailsParse(document: Document): SManga

    override fun chapterListParse(response: Response): List<SChapter> = throw RuntimeException("stub")
    protected abstract fun chapterListSelector(): String
    protected abstract fun chapterFromElement(element: Element): SChapter

    override fun pageListParse(response: Response): List<Page> = throw RuntimeException("stub")
    protected abstract fun pageListParse(document: Document): List<Page>

    override fun imageUrlParse(response: Response): String = throw RuntimeException("stub")
    protected abstract fun imageUrlParse(document: Document): String
}
