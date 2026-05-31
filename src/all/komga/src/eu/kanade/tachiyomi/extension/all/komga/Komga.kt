package eu.kanade.tachiyomi.extension.all.komga

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.all.komga.dto.BookDto
import eu.kanade.tachiyomi.extension.all.komga.dto.BookPageDto
import eu.kanade.tachiyomi.extension.all.komga.dto.LibraryDto
import eu.kanade.tachiyomi.extension.all.komga.dto.PageDto
import eu.kanade.tachiyomi.extension.all.komga.dto.SeriesDto
import eu.kanade.tachiyomi.extension.all.komga.dto.SeriesPageDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Komga — a self-hosted manga server with a JSON REST API at `/api/v1/...`.
 *
 * There is NO hardcoded server. The base URL, username and password all come from user
 * preferences. With no server configured, [baseUrl] is empty and every request no-ops gracefully
 * (returns an empty [MangasPage] / empty list) instead of crashing.
 *
 * Extends [HttpSource] (the host JSON-API pattern) and implements [ConfigurableSource] so the user
 * can enter their server address and HTTP Basic credentials.
 */
class Komga : HttpSource(), ConfigurableSource {

    override val name = "Komga"

    override val lang = "all"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val preferences: SharedPreferences by lazy { getSourcePreferences() }

    /**
     * User-configured server root, normalized without a trailing slash. Empty when unconfigured —
     * every request path is a literal appended to this value, so an empty base means no real host.
     */
    override val baseUrl: String
        get() = preferences.getString(PREF_ADDRESS, "")!!.trim().trimEnd('/')

    private val username: String
        get() = preferences.getString(PREF_USERNAME, "")!!.trim()

    private val password: String
        get() = preferences.getString(PREF_PASSWORD, "")!!

    /**
     * Inject HTTP Basic auth into every outgoing request when credentials are set. The header is
     * built from the user-entered username/password only (never hardcoded).
     */
    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::authInterceptor)
        .build()

    private fun authInterceptor(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val user = username
        return if (user.isNotEmpty()) {
            val authed = original.newBuilder()
                .header("Authorization", Credentials.basic(user, password))
                .build()
            chain.proceed(authed)
        } else {
            chain.proceed(original)
        }
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", "application/json")

    // ---- Popular ----
    override fun popularMangaRequest(page: Int): Request {
        return seriesRequest(page) {
            addQueryParameter("sort", "metadata.titleSort,asc")
        }
    }

    override fun popularMangaParse(response: Response): MangasPage = seriesPageParse(response)

    // ---- Latest ----
    override fun latestUpdatesRequest(page: Int): Request {
        return seriesRequest(page) {
            addQueryParameter("sort", "lastModified,desc")
        }
    }

    override fun latestUpdatesParse(response: Response): MangasPage = seriesPageParse(response)

    // ---- Search ----
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return seriesRequest(page) {
            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }
            filters.forEach { filter ->
                when (filter) {
                    is LibraryFilter -> {
                        filter.selectedId()?.let { addQueryParameter("library_id", it) }
                    }
                    is StatusFilter -> {
                        filter.selectedValue()?.let { addQueryParameter("status", it) }
                    }
                    else -> {}
                }
            }
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = seriesPageParse(response)

    /**
     * Builds a `/api/v1/series` request. Returns a no-op request (still a literal path on baseUrl,
     * but parsing short-circuits) when no server is configured so the catalogue stays empty.
     */
    private fun seriesRequest(page: Int, block: okhttp3.HttpUrl.Builder.() -> Unit): Request {
        if (baseUrl.isEmpty()) return GET(NO_CONFIG_URL, headers)
        val url = "$baseUrl/api/v1/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("size", PAGE_SIZE.toString())
            .apply(block)
            .build()
        return GET(url, headers)
    }

    private fun seriesPageParse(response: Response): MangasPage {
        if (baseUrl.isEmpty()) return MangasPage(emptyList(), false)
        val result = with(json) { response.parseAs<SeriesPageDto>() }
        val mangas = result.content.map(::seriesToSManga)
        return MangasPage(mangas, !result.last)
    }

    // ---- Details ----
    override fun mangaDetailsRequest(manga: SManga): Request {
        if (baseUrl.isEmpty()) return GET(NO_CONFIG_URL, headers)
        return GET("$baseUrl${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val series = with(json) { response.parseAs<SeriesDto>() }
        return seriesToSManga(series)
    }

    // ---- Chapters ----
    override fun chapterListRequest(manga: SManga): Request {
        if (baseUrl.isEmpty()) return GET(NO_CONFIG_URL, headers)
        val id = manga.url.substringAfterLast("/")
        val url = "$baseUrl/api/v1/series/$id/books".toHttpUrl().newBuilder()
            .addQueryParameter("size", BOOKS_SIZE.toString())
            .addQueryParameter("sort", "metadata.numberSort,asc")
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        if (baseUrl.isEmpty()) return emptyList()
        val result = with(json) { response.parseAs<BookPageDto>() }
        return result.content
            .map(::bookToSChapter)
            .sortedByDescending { it.chapter_number }
    }

    // chapterPageParse is abstract on HttpSource even for JSON sources; the chapter list is built
    // from the books JSON, so this is never invoked.
    override fun chapterPageParse(response: Response): SChapter = throw UnsupportedOperationException()

    // ---- Pages ----
    override fun pageListRequest(chapter: SChapter): Request {
        if (baseUrl.isEmpty()) return GET(NO_CONFIG_URL, headers)
        val id = chapter.url.substringAfterLast("/")
        return GET("$baseUrl/api/v1/books/$id/pages", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        if (baseUrl.isEmpty()) return emptyList()
        // The book id is on the request URL: /api/v1/books/{id}/pages
        val bookId = response.request.url.pathSegments
            .let { it.getOrNull(it.indexOf("pages") - 1) }
            .orEmpty()
        val pages = with(json) { response.parseAs<List<PageDto>>() }
        return pages.map { p ->
            Page(p.number - 1, imageUrl = "$baseUrl/api/v1/books/$bookId/pages/${p.number}")
        }
    }

    // Pages already carry an absolute imageUrl, so imageUrlParse is never called.
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ---- Mappers ----
    private fun seriesToSManga(series: SeriesDto): SManga = SManga.create().apply {
        setUrlWithoutDomain("$baseUrl/api/v1/series/${series.id}")
        title = series.metadata.title?.takeIf(String::isNotBlank) ?: series.name
        description = series.metadata.summary?.takeIf(String::isNotBlank)
            ?: series.booksMetadata.summary
        genre = (series.metadata.genres + series.metadata.tags).distinct().joinToString()
        author = series.booksMetadata.authors
            .firstOrNull { it.role.equals("writer", ignoreCase = true) }?.name
            ?: series.booksMetadata.authors.firstOrNull()?.name
        artist = series.booksMetadata.authors
            .firstOrNull { it.role.equals("penciller", ignoreCase = true) }?.name
        thumbnail_url = "$baseUrl/api/v1/series/${series.id}/thumbnail"
        status = when (series.metadata.status?.uppercase()) {
            "ONGOING" -> SManga.ONGOING
            "ENDED" -> SManga.COMPLETED
            "ABANDONED" -> SManga.CANCELLED
            "HIATUS" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun bookToSChapter(book: BookDto): SChapter = SChapter.create().apply {
        setUrlWithoutDomain("$baseUrl/api/v1/books/${book.id}")
        val numberLabel = book.metadata.number?.takeIf(String::isNotBlank) ?: book.number.toString()
        val title = book.metadata.title?.takeIf(String::isNotBlank) ?: book.name
        name = "$numberLabel - $title".trim().trimStart('-').trim()
        chapter_number = book.metadata.numberSort ?: book.number
        scanlator = book.metadata.authors.firstOrNull { it.role.equals("writer", ignoreCase = true) }?.name
        date_upload = parseDate(book.metadata.releaseDate)
            ?: parseDate(book.fileLastModified)
            ?: parseDate(book.created)
            ?: 0L
    }

    private fun parseDate(date: String?): Long? {
        if (date.isNullOrBlank()) return null
        return DATE_FORMATS.firstNotNullOfOrNull { fmt ->
            runCatching { fmt.parse(date)?.time }.getOrNull()
        }
    }

    // ---- Filters ----
    override fun getFilterList(): FilterList {
        return FilterList(
            StatusFilter(),
            LibraryFilter(loadLibraries()),
        )
    }

    /**
     * Best-effort synchronous fetch of the user's libraries to populate the library filter. Any
     * failure (no server configured, network error) degrades to an empty list rather than crashing.
     */
    private fun loadLibraries(): List<LibraryDto> {
        if (baseUrl.isEmpty()) return emptyList()
        return runCatching {
            client.newCall(GET("$baseUrl/api/v1/libraries", headers)).execute().use { resp ->
                with(json) { resp.parseAs<List<LibraryDto>>() }
            }
        }.getOrDefault(emptyList())
    }

    private class StatusFilter : Filter.Select<String>(
        "Status",
        arrayOf("Any", "Ongoing", "Ended", "Abandoned", "Hiatus"),
    ) {
        fun selectedValue(): String? = when (state) {
            1 -> "ONGOING"
            2 -> "ENDED"
            3 -> "ABANDONED"
            4 -> "HIATUS"
            else -> null
        }
    }

    private class LibraryFilter(private val libraries: List<LibraryDto>) : Filter.Select<String>(
        "Library",
        (listOf("Any") + libraries.map { it.name }).toTypedArray(),
    ) {
        fun selectedId(): String? = libraries.getOrNull(state - 1)?.id
    }

    // ---- Preferences ----
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context

        screen.addPreference(
            EditTextPreference(context).apply {
                key = PREF_ADDRESS
                title = "Server address"
                summary = "The base URL of your Komga server, e.g. https://komga.example.org"
                dialogTitle = "Server address"
                setDefaultValue("")
            },
        )
        screen.addPreference(
            EditTextPreference(context).apply {
                key = PREF_USERNAME
                title = "Username"
                summary = "The email / username used to log in to your Komga server"
                dialogTitle = "Username"
                setDefaultValue("")
            },
        )
        screen.addPreference(
            EditTextPreference(context).apply {
                key = PREF_PASSWORD
                title = "Password"
                summary = "The password used to log in to your Komga server"
                dialogTitle = "Password"
                setDefaultValue("")
                setOnBindEditTextListener { editText ->
                    editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
            },
        )
    }

    companion object {
        private const val PREF_ADDRESS = "address"
        private const val PREF_USERNAME = "username"
        private const val PREF_PASSWORD = "password"

        private const val PAGE_SIZE = 20
        private const val BOOKS_SIZE = 1000

        // A syntactically-valid but inert URL used only when no server is configured. It is never
        // actually fetched because every parse() short-circuits on an empty baseUrl.
        private const val NO_CONFIG_URL = "http://localhost/"

        private val DATE_FORMATS = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        )
    }
}
