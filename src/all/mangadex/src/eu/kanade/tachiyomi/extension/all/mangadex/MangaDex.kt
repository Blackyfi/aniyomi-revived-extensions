package eu.kanade.tachiyomi.extension.all.mangadex

import android.app.Application
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AtHomeDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ChapterListDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaDataDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaListDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaWrapperDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * MangaDex — a JSON-API source. Reference implementation: extend [HttpSource] (NOT the deprecated
 * ParsedHttpSource) and parse with the host's `Response.parseAs<T>()`. No CSS selectors to break.
 *
 * One APK, many language variants — see [MangaDexFactory]. Implements [ConfigurableSource] so each
 * per-language source carries its own cover-quality preference (the preference store is keyed on the
 * source id via [getSourcePreferences], and ids are per-language; see HttpSource.kt:57,87-91).
 */
open class MangaDex(override val lang: String, private val dexLang: String) :
    HttpSource(), ConfigurableSource {

    override val name = "MangaDex"

    // Website host (used for stored URLs); the API and CDN are separate hosts. Each is one edit if it moves.
    override val baseUrl = "https://mangadex.org"
    private val apiUrl = "https://api.mangadex.org"
    private val cdnUrl = "https://uploads.mangadex.org"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    // Per-source preference store (keyed on the source id, which differs per language).
    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", android.content.Context.MODE_PRIVATE)
    }

    // MangaDex publishes a ~5 req/s limit; honor it via the host rate-limit interceptor.
    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(permits = 5)
        .build()

    // ---- Preferences ----
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val coverPref = ListPreference(screen.context).apply {
            key = COVER_QUALITY_PREF
            title = "Cover quality"
            // Entry order maps 1:1 to entryValues; values are CDN filename suffixes (literals).
            entries = arrayOf("Original", "Medium (512px)", "Low (256px)")
            entryValues = arrayOf(COVER_ORIGINAL, COVER_512, COVER_256)
            setDefaultValue(COVER_512)
            summary = "%s"
        }
        screen.addPreference(coverPref)
    }

    // Returns one of the literal CDN suffixes: "" / ".512.jpg" / ".256.jpg".
    private fun coverQualitySuffix(): String =
        preferences.getString(COVER_QUALITY_PREF, COVER_512) ?: COVER_512

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
        val builder = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("offset", offset(page))
            .addQueryParameter("includes[]", "cover_art")

        if (query.isNotBlank()) {
            builder.addQueryParameter("title", query)
        }

        // Default order, overridden only if a Sort filter is present and set.
        var orderApplied = false

        filters.forEach { filter ->
            when (filter) {
                is ContentRatingFilter -> filter.state
                    .filter { it.state }
                    // it.value is a literal from CONTENT_RATINGS.
                    .forEach { builder.addQueryParameter("contentRating[]", it.value) }

                is StatusFilter -> filter.state
                    .filter { it.state }
                    // it.value is a literal from STATUSES.
                    .forEach { builder.addQueryParameter("status[]", it.value) }

                is TagFilter -> filter.state.forEach { tag ->
                    when (tag.state) {
                        // tag.uuid is a literal from TAGS.
                        Filter.TriState.STATE_INCLUDE ->
                            builder.addQueryParameter("includedTags[]", tag.uuid)
                        Filter.TriState.STATE_EXCLUDE ->
                            builder.addQueryParameter("excludedTags[]", tag.uuid)
                    }
                }

                is SortFilter -> {
                    val selection = filter.state
                    if (selection != null) {
                        // SORT_KEYS is a literal array; direction is a literal asc/desc.
                        val key = SORT_KEYS[selection.index]
                        val direction = if (selection.ascending) "asc" else "desc"
                        builder.addQueryParameter("order[$key]", direction)
                        orderApplied = true
                    }
                }

                else -> { /* Header / Separator: no-op */ }
            }
        }

        if (!orderApplied) {
            builder.addQueryParameter("order[followedCount]", "desc")
        }

        return GET(builder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ---- Filters ----
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Filters are ignored if a text search is combined with sort by relevance."),
        SortFilter(),
        ContentRatingFilter(),
        StatusFilter(),
        Filter.Header("Tags (tap to include, again to exclude)"),
        TagFilter(),
    )

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
        // Suffix is a literal ("" / ".512.jpg" / ".256.jpg") chosen by the cover-quality preference.
        thumbnail_url = coverFile?.let { "$cdnUrl/covers/${data.id}/$it${coverQualitySuffix()}" }
    }

    private fun offset(page: Int) = ((page - 1) * LIMIT).toString()

    private fun parseDate(date: String): Long =
        runCatching { dateFormat.parse(date)?.time }.getOrNull() ?: 0L

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'+00:00'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    // ---- Filter types ----

    /** A content-rating checkbox; [value] is a literal MangaDex `contentRating[]` value. */
    private class ContentRating(name: String, val value: String) : Filter.CheckBox(name)

    private class ContentRatingFilter : Filter.Group<ContentRating>(
        "Content rating",
        CONTENT_RATINGS.map { ContentRating(it.first, it.second) },
    )

    /** A publication-status checkbox; [value] is a literal MangaDex `status[]` value. */
    private class Status(name: String, val value: String) : Filter.CheckBox(name)

    private class StatusFilter : Filter.Group<Status>(
        "Publication status",
        STATUSES.map { Status(it.first, it.second) },
    )

    /** A tristate tag; [uuid] is a literal MangaDex tag UUID (see [TAGS]). */
    private class Tag(name: String, val uuid: String) : Filter.TriState(name)

    private class TagFilter : Filter.Group<Tag>(
        "Tags",
        TAGS.map { Tag(it.first, it.second) },
    )

    private class SortFilter : Filter.Sort(
        "Sort by",
        SORT_LABELS,
        Selection(0, ascending = false),
    )

    companion object {
        private const val LIMIT = 20

        private const val COVER_QUALITY_PREF = "cover_quality"
        private const val COVER_ORIGINAL = ""
        private const val COVER_512 = ".512.jpg"
        private const val COVER_256 = ".256.jpg"

        // label -> literal `contentRating[]` API value
        private val CONTENT_RATINGS = listOf(
            "Safe" to "safe",
            "Suggestive" to "suggestive",
            "Erotica" to "erotica",
            "Pornographic" to "pornographic",
        )

        // label -> literal `status[]` API value
        private val STATUSES = listOf(
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
            "Cancelled" to "cancelled",
        )

        // Sort labels shown to the user; index maps 1:1 to SORT_KEYS below.
        private val SORT_LABELS = arrayOf(
            "Best match (relevance)",
            "Followed count",
            "Latest chapter",
            "Title",
            "Rating",
        )

        // Literal MangaDex `order[<key>]` keys, index-aligned with SORT_LABELS.
        private val SORT_KEYS = arrayOf(
            "relevance",
            "followedCount",
            "latestUploadedChapter",
            "title",
            "rating",
        )

        // Curated set of well-known MangaDex tag UUIDs (stable ids from the public /manga/tag endpoint).
        // label -> literal tag UUID, applied as includedTags[]/excludedTags[].
        private val TAGS = listOf(
            "Action" to "391b0423-d847-456f-aff0-8b0cfc03066b",
            "Adventure" to "87cc87cd-a395-47af-b27a-93258283bbc6",
            "Comedy" to "4d32cc48-9f00-4cca-9b5a-a839f0764984",
            "Drama" to "b9af3a63-f058-46de-a9a0-e0c13906197a",
            "Fantasy" to "cdc58593-87dd-415e-bbc0-2ec27bf404cc",
            "Horror" to "cdad7e68-1419-41dd-bdce-27753074a640",
            "Isekai" to "ace04997-f6bd-436e-b261-779182193d3d",
            "Mystery" to "07251805-a27e-4d59-b488-f0bfbec15168",
            "Romance" to "423e2eae-a7a2-4a8b-ac03-a8351462d71d",
            "Sci-Fi" to "256c8bd9-4904-4360-bf4f-508a76d67183",
            "Slice of Life" to "e5301a23-ebd9-49dd-a0cb-2add944c7fe9",
            "Sports" to "69964a64-2f90-4d33-beeb-f3ed2875eb4c",
            "Supernatural" to "eabc5b4c-6aff-42f3-b657-3e90cbd00b75",
            "Tragedy" to "f8f62932-27da-4fe4-8ee1-6779a8c5edba",
        )
    }
}
