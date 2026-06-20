package eu.kanade.tachiyomi.extension.all.mangavault

import android.content.SharedPreferences
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.all.mangavault.dto.ChapterDto
import eu.kanade.tachiyomi.extension.all.mangavault.dto.MangaDto
import eu.kanade.tachiyomi.extension.all.mangavault.dto.MangaListResponse
import eu.kanade.tachiyomi.extension.all.mangavault.dto.PageDto
import eu.kanade.tachiyomi.extension.all.mangavault.dto.SourceDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.MangaSourceInfo
import eu.kanade.tachiyomi.source.MultiSourceCatalogSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * MangaVault — a personal self-hosted manga library (FastAPI) exposing an
 * Aniyomi-friendly JSON API at `/extension/...`, reached over Tailscale.
 *
 * There is NO hardcoded server: the server URL and API key both come from user
 * preferences (the host is a private Tailscale name and this APK is public, so
 * baking it in would leak it). With nothing configured, [baseUrl] is empty and
 * every request short-circuits to an empty result instead of crashing.
 *
 * Auth is a single long-lived API key sent as the `X-Extension-Key` header on
 * every request (including image/download fetches) via an OkHttp interceptor.
 */
class MangaVault : HttpSource(), ConfigurableSource, MultiSourceCatalogSource {

    override val name = "MangaVault"

    override val lang = "all"

    override val supportsLatest = true

    // Construct Json directly. Do NOT use injectLazy()/parseAs() from the lib-stub:
    // those are `inline` stubs whose `throw RuntimeException("stub")` body gets inlined
    // into this APK at compile time and actually runs at runtime. kotlinx-serialization's
    // decodeFromString is the real (host-provided) deserializer.
    private val json = Json { ignoreUnknownKeys = true }

    private val preferences: SharedPreferences by lazy { getSourcePreferences() }

    /** User-configured server root, normalized without a trailing slash. Empty when unconfigured. */
    override val baseUrl: String
        get() = preferences.getString(PREF_ADDRESS, "")!!.trim().trimEnd('/')

    private val apiKey: String
        get() = preferences.getString(PREF_API_KEY, "")!!.trim()

    /** Attach the API key to every outgoing request when it is set. */
    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::authInterceptor)
        .build()

    private fun authInterceptor(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val key = apiKey
        return if (key.isNotEmpty()) {
            chain.proceed(original.newBuilder().header("X-Extension-Key", key).build())
        } else {
            chain.proceed(original)
        }
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", "application/json")

    // ---- Popular ----
    override fun popularMangaRequest(page: Int): Request = mangaListRequest(page, "added_at")

    override fun popularMangaParse(response: Response): MangasPage = mangaListParse(response)

    // ---- Latest ----
    override fun latestUpdatesRequest(page: Int): Request = mangaListRequest(page, "last_updated")

    override fun latestUpdatesParse(response: Response): MangasPage = mangaListParse(response)

    // ---- Search ----
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (baseUrl.isEmpty()) return GET(NO_CONFIG_URL, headers)
        val url = "$baseUrl/extension/manga/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PAGE_SIZE.toString())
        if (query.isNotBlank()) {
            url.addQueryParameter("search", query.trim())
        } else {
            url.addQueryParameter("sort_by", "added_at")
            url.addQueryParameter("sort_order", "desc")
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = mangaListParse(response)

    private fun mangaListRequest(page: Int, sortBy: String): Request {
        if (baseUrl.isEmpty()) return GET(NO_CONFIG_URL, headers)
        val url = "$baseUrl/extension/manga/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PAGE_SIZE.toString())
            .addQueryParameter("sort_by", sortBy)
            .addQueryParameter("sort_order", "desc")
            .build()
        return GET(url, headers)
    }

    private fun mangaListParse(response: Response): MangasPage {
        if (baseUrl.isEmpty()) return MangasPage(emptyList(), false)
        val result = json.decodeFromString<MangaListResponse>(response.body!!.string())
        return MangasPage(result.mangas.map(::mangaToSManga), result.hasNextPage)
    }

    // ---- Details ----
    override fun mangaDetailsRequest(manga: SManga): Request {
        if (baseUrl.isEmpty()) return GET(NO_CONFIG_URL, headers)
        return GET("$baseUrl${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga =
        mangaToSManga(json.decodeFromString<MangaDto>(response.body!!.string()))

    // ---- Chapters ----
    override fun chapterListRequest(manga: SManga): Request {
        if (baseUrl.isEmpty()) return GET(NO_CONFIG_URL, headers)
        // manga.url is "/extension/manga/{id}" → "{base}/extension/manga/{id}/chapters"
        return GET("$baseUrl${manga.url}/chapters", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        if (baseUrl.isEmpty()) return emptyList()
        val chapters = json.decodeFromString<List<ChapterDto>>(response.body!!.string())
        // Server returns ascending by chapter_number; Aniyomi shows newest-first.
        return chapters.map(::chapterToSChapter).sortedByDescending { it.chapter_number }
    }

    // Abstract on this fork's HttpSource even for JSON sources; the chapter list is
    // built from JSON, so this is never invoked.
    override fun chapterPageParse(response: Response): SChapter = throw UnsupportedOperationException()

    // ---- Pages ----
    override fun pageListRequest(chapter: SChapter): Request {
        if (baseUrl.isEmpty()) return GET(NO_CONFIG_URL, headers)
        // chapter.url is "/extension/chapter/{id}/pages"
        return GET("$baseUrl${chapter.url}", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        if (baseUrl.isEmpty()) return emptyList()
        val pages = json.decodeFromString<List<PageDto>>(response.body!!.string())
        // image_url is already an absolute backend URL; the interceptor adds the key.
        return pages.map { Page(it.index, imageUrl = it.imageUrl) }
    }

    // Pages already carry an absolute imageUrl, so imageUrlParse is never called.
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ---- No filters (personal library; search is enough) ----
    override fun getFilterList(): FilterList = FilterList()

    // ---- Mappers ----
    private fun mangaToSManga(dto: MangaDto): SManga = SManga.create().apply {
        setUrlWithoutDomain("$baseUrl/extension/manga/${dto.id}")
        title = dto.title
        thumbnail_url = dto.thumbnailUrl.ifBlank { "$baseUrl/extension/cover/${dto.id}" }
        author = dto.authors.joinToString().ifBlank { null }
        genre = dto.genres.joinToString().ifBlank { null }
        description = dto.description
        status = when (dto.status.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "on_hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }

    private fun chapterToSChapter(dto: ChapterDto): SChapter = SChapter.create().apply {
        // Source-INDEPENDENT URL keyed by (manga, chapter_number). The backend serves
        // pages from the manga's effective source, so switching sources keeps the URL
        // identical for shared chapter numbers — Aniyomi then preserves read/bookmark
        // state (it matches chapters by URL). The legacy /extension/chapter/{id}/pages
        // route still works for any older library entries.
        setUrlWithoutDomain(
            "$baseUrl/extension/manga/${dto.mangaId}/chapter/${chapterNumberPath(dto.chapterNumber)}/pages",
        )
        chapter_number = dto.chapterNumber
        date_upload = dto.dateUpload
        scanlator = dto.scanlator
        name = buildChapterName(dto)
    }

    /** Canonical chapter-number path segment: "12" for whole numbers, "12.5" otherwise. */
    private fun chapterNumberPath(num: Float): String =
        if (num % 1f == 0f) num.toInt().toString() else num.toString()

    // ---- Multi-source ----
    override fun getMangaSources(manga: SManga): List<MangaSourceInfo> {
        if (baseUrl.isEmpty()) return emptyList()
        val response = client.newCall(GET("$baseUrl${manga.url}", headers)).execute()
        val dto = response.use { json.decodeFromString<MangaDto>(it.body!!.string()) }
        return dto.sources.map(::sourceToInfo)
    }

    override fun setMangaSource(manga: SManga, sourceKey: String): List<MangaSourceInfo> {
        if (baseUrl.isEmpty()) return emptyList()
        val body = """{"source":"$sourceKey"}""".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl${manga.url}/default-source")
            .headers(headers)
            .put(body)
            .build()
        val response = client.newCall(request).execute()
        val sources = response.use { json.decodeFromString<List<SourceDto>>(it.body!!.string()) }
        return sources.map(::sourceToInfo)
    }

    private fun sourceToInfo(dto: SourceDto): MangaSourceInfo = MangaSourceInfo(
        key = dto.key,
        name = dto.name,
        totalChapters = dto.totalChapters,
        latestChapter = dto.latestChapterNumber,
        isPrimary = dto.isPrimary,
        isDefault = dto.isDefault,
        isMostUpToDate = dto.isMostUpToDate,
        isEffective = dto.isEffective,
    )

    /** Stable display name. Kept independent of upscale state so chapter identity never shifts. */
    private fun buildChapterName(dto: ChapterDto): String {
        val num = if (dto.chapterNumber % 1f == 0f) {
            dto.chapterNumber.toInt().toString()
        } else {
            dto.chapterNumber.toString()
        }
        val label = "Chapter $num"
        val title = dto.title?.trim()?.trimEnd(':')?.trim().orEmpty()
        return when {
            title.isEmpty() -> label
            title.equals(label, ignoreCase = true) -> label
            title.startsWith(label, ignoreCase = true) -> title
            else -> "$label: $title"
        }
    }

    // ---- Preferences ----
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context

        screen.addPreference(
            EditTextPreference(context).apply {
                key = PREF_ADDRESS
                title = "Server URL"
                summary = "Your MangaVault server over Tailscale, e.g. http://your-host:8000 (no trailing slash)"
                dialogTitle = "Server URL"
                setDefaultValue("")
            },
        )
        screen.addPreference(
            EditTextPreference(context).apply {
                key = PREF_API_KEY
                title = "API Key"
                summary = "Extension API key from MangaVault (Admin → generate). Stored on-device only."
                dialogTitle = "API Key"
                setDefaultValue("")
                setOnBindEditTextListener { editText ->
                    editText.inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
            },
        )
    }

    companion object {
        private const val PREF_ADDRESS = "address"
        private const val PREF_API_KEY = "api_key"

        private const val PAGE_SIZE = 20

        // Syntactically-valid but inert URL used only when no server is configured. It is never
        // actually fetched because every parse() short-circuits on an empty baseUrl.
        private const val NO_CONFIG_URL = "http://localhost/"
    }
}
