@file:Suppress("unused")

package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable

/**
 * Stub mirror of the host HttpSource. Public/protected signatures and open/abstract modifiers
 * mirror the real API exactly so extension subclasses (and the lib-multisrc theme bases) compile
 * against it `compileOnly`. The host app supplies the real implementation at runtime.
 */
@Suppress("MemberVisibilityCanBePrivate")
abstract class HttpSource : CatalogueSource {

    protected val network: NetworkHelper
        get() = throw RuntimeException("stub")

    abstract val baseUrl: String

    open val versionId = 1

    override val id: Long
        get() = throw RuntimeException("stub")

    val headers: Headers
        get() = throw RuntimeException("stub")

    open val client: OkHttpClient
        get() = throw RuntimeException("stub")

    protected open fun headersBuilder(): Headers.Builder = throw RuntimeException("stub")

    // Deprecated Rx API surface (host retains it). Sources override these; stubs throw.
    open fun fetchPopularManga(page: Int): Observable<MangasPage> = throw RuntimeException("stub")
    open fun fetchLatestUpdates(page: Int): Observable<MangasPage> = throw RuntimeException("stub")
    open fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw RuntimeException("stub")
    open fun fetchMangaDetails(manga: SManga): Observable<SManga> = throw RuntimeException("stub")
    open fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = throw RuntimeException("stub")
    open fun fetchPageList(chapter: SChapter): Observable<List<Page>> = throw RuntimeException("stub")
    open fun fetchImageUrl(page: Page): Observable<String> = throw RuntimeException("stub")

    // Related-manga API (present in newer upstream HttpSource; host may not invoke it).
    open fun relatedMangaListRequest(manga: SManga): Request = throw RuntimeException("stub")
    open fun relatedMangaListParse(response: Response): List<SManga> = throw RuntimeException("stub")

    override fun toString(): String = throw RuntimeException("stub")

    protected abstract fun popularMangaRequest(page: Int): Request
    protected abstract fun popularMangaParse(response: Response): MangasPage

    protected abstract fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request
    protected abstract fun searchMangaParse(response: Response): MangasPage

    protected abstract fun latestUpdatesRequest(page: Int): Request
    protected abstract fun latestUpdatesParse(response: Response): MangasPage

    open fun mangaDetailsRequest(manga: SManga): Request = throw RuntimeException("stub")
    protected abstract fun mangaDetailsParse(response: Response): SManga

    protected open fun chapterListRequest(manga: SManga): Request = throw RuntimeException("stub")
    protected abstract fun chapterListParse(response: Response): List<SChapter>

    protected open fun chapterPageParse(response: Response): SChapter = throw UnsupportedOperationException()

    protected open fun pageListRequest(chapter: SChapter): Request = throw RuntimeException("stub")
    protected abstract fun pageListParse(response: Response): List<Page>

    protected open fun imageUrlRequest(page: Page): Request = throw RuntimeException("stub")
    protected abstract fun imageUrlParse(response: Response): String

    protected open fun imageRequest(page: Page): Request = throw RuntimeException("stub")

    /** Extension members on SChapter/SManga (matching the host); used as `setUrlWithoutDomain(...)`. */
    fun SChapter.setUrlWithoutDomain(url: String) {
        throw RuntimeException("stub")
    }

    fun SManga.setUrlWithoutDomain(url: String) {
        throw RuntimeException("stub")
    }

    open fun getMangaUrl(manga: SManga): String = throw RuntimeException("stub")

    open fun getChapterUrl(chapter: SChapter): String = throw RuntimeException("stub")

    open fun prepareNewChapter(chapter: SChapter, manga: SManga) {}

    override fun getFilterList(): FilterList = throw RuntimeException("stub")
}
