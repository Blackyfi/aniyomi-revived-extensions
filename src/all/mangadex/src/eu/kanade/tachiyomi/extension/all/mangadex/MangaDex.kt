package eu.kanade.tachiyomi.extension.all.mangadex

import eu.kanade.tachiyomi.extension.all.mangadex.dto.AtHomeDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ChapterListDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaDataDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaListDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaWrapperDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * MangaDex — a JSON-API source. Reference implementation: extend [HttpSource] (NOT the deprecated
 * ParsedHttpSource) and parse with the host's `Response.parseAs<T>()`. No CSS selectors to break.
 *
 * One APK, many language variants — see [MangaDexFactory].
 */
open class MangaDex(override val lang: String, private val dexLang: String) : HttpSource() {

    override val name = "MangaDex"

    // Website host (used for stored URLs); the API and CDN are separate hosts. Each is one edit if it moves.
    override val baseUrl = "https://mangadex.org"
    private val apiUrl = "https://api.mangadex.org"
    private val cdnUrl = "https://uploads.mangadex.org"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    // MangaDex publishes a ~5 req/s limit; honor it via the host rate-limit interceptor.
    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(permits = 5)
        .build()

    // ---- Popular ----
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("order[followedCount]", "desc")
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("offset", offset(page))
            .addQueryParameter("includes[]", "cover_art")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = with(json) { response.parseAs<MangaListDto>() }
        val mangas = result.data.map(::mangaFromDto)
        val hasNext = result.offset + result.limit < result.total
        return MangasPage(mangas, hasNext)
    }

    // ---- Latest ----
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("order[latestUploadedChapter]", "desc")
            .addQueryParameter("availableTranslatedLanguage[]", dexLang)
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("offset", offset(page))
            .addQueryParameter("includes[]", "cover_art")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ---- Search ----
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("title", query)
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("offset", offset(page))
            .addQueryParameter("includes[]", "cover_art")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ---- Details ----
    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("/")
        val url = "$apiUrl/manga/$id".toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("includes[]", "artist")
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = with(json) { response.parseAs<MangaWrapperDto>() }.data
        return mangaFromDto(data)
    }

    // ---- Chapters ----
    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("/")
        val url = "$apiUrl/manga/$id/feed".toHttpUrl().newBuilder()
            .addQueryParameter("translatedLanguage[]", dexLang)
            .addQueryParameter("order[chapter]", "desc")
            .addQueryParameter("limit", "500")
            .addQueryParameter("includes[]", "scanlation_group")
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = with(json) { response.parseAs<ChapterListDto>() }
        return result.data.map { dto ->
            SChapter.create().apply {
                setUrlWithoutDomain("$baseUrl/chapter/${dto.id}")
                name = buildString {
                    dto.attributes.volume?.let { append("Vol. $it ") }
                    dto.attributes.chapter?.let { append("Ch. $it ") }
                    dto.attributes.title?.takeIf(String::isNotBlank)?.let { append("- $it") }
                }.trim().ifEmpty { "Oneshot" }
                chapter_number = dto.attributes.chapter?.toFloatOrNull() ?: -1f
                scanlator = dto.relationships
                    .firstOrNull { it.type == "scanlation_group" }?.attributes?.name
                date_upload = dto.attributes.publishAt?.let(::parseDate) ?: 0L
            }
        }
    }

    // ---- Pages ----
    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringAfterLast("/")
        return GET("$apiUrl/at-home/server/$id", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val atHome = with(json) { response.parseAs<AtHomeDto>() }
        return atHome.chapter.data.mapIndexed { index, file ->
            Page(index, imageUrl = "${atHome.baseUrl}/data/${atHome.chapter.hash}/$file")
        }
    }

    // chapterPageParse is abstract on HttpSource (source-api HttpSource.kt:287) even for JSON
    // sources; MangaDex builds its chapter list from the feed JSON, so this is never invoked.
    override fun chapterPageParse(response: Response): SChapter = throw UnsupportedOperationException()

    // Pages already carry an absolute imageUrl, so imageUrlParse is never called.
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ---- Helpers ----
    private fun mangaFromDto(data: MangaDataDto): SManga = SManga.create().apply {
        setUrlWithoutDomain("$baseUrl/manga/${data.id}")
        title = data.attributes.title.values.firstOrNull().orEmpty()
        description = data.attributes.description[dexLang] ?: data.attributes.description["en"]
        genre = data.attributes.tags.mapNotNull { it.attributes.name["en"] }.joinToString()
        author = data.relationships.firstOrNull { it.type == "author" }?.attributes?.name
        artist = data.relationships.firstOrNull { it.type == "artist" }?.attributes?.name
        status = when (data.attributes.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        val coverFile = data.relationships.firstOrNull { it.type == "cover_art" }?.attributes?.fileName
        thumbnail_url = coverFile?.let { "$cdnUrl/covers/${data.id}/$it.256.jpg" }
    }

    private fun offset(page: Int) = ((page - 1) * LIMIT).toString()

    private fun parseDate(date: String): Long =
        runCatching { dateFormat.parse(date)?.time }.getOrNull() ?: 0L

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'+00:00'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    companion object {
        private const val LIMIT = 20
    }
}
