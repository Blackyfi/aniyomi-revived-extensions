package eu.kanade.tachiyomi.extension.all.mangadex

import eu.kanade.tachiyomi.extension.all.mangadex.dto.AtHomeDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaListDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the MangaDex DTOs (Task D1).
 *
 * These decode realistic MangaDex API JSON fixtures straight into the @Serializable DTOs from
 * [eu.kanade.tachiyomi.extension.all.mangadex.dto]. They run on the host JVM with no device.
 *
 * The host's `Response.parseAs<T>()` uses an injected `Json` configured with
 * `ignoreUnknownKeys = true` (so an added/missing API field never bricks the source). We mirror
 * that here because the fixtures carry real-world fields (result/response/year/contentRating/...)
 * that the lean DTOs deliberately do not model.
 */
class MangaDexDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!
            .bufferedReader().use { it.readText() }

    @Test
    fun `parses manga list response into MangaListDto`() {
        val result = json.decodeFromString<MangaListDto>(fixture("manga_list.json"))

        assertEquals(2, result.data.size)
        assertEquals(20, result.limit)
        assertEquals(0, result.offset)
        assertEquals(2, result.total)
    }

    @Test
    fun `parses manga id, title map and status`() {
        val result = json.decodeFromString<MangaListDto>(fixture("manga_list.json"))
        val first = result.data.first()

        assertEquals("32d76d19-8a05-4db0-9fc2-e0b0648fe9d0", first.id)
        assertEquals("Solo Leveling", first.attributes.title["en"])
        assertEquals("俺だけレベルアップ", first.attributes.title["ja"])
        assertEquals("completed", first.attributes.status)

        val second = result.data[1]
        assertEquals("Komi Can't Communicate", second.attributes.title["en"])
        assertEquals("ongoing", second.attributes.status)
    }

    @Test
    fun `parses manga tags`() {
        val first = json.decodeFromString<MangaListDto>(fixture("manga_list.json")).data.first()
        val tagNames = first.attributes.tags.mapNotNull { it.attributes.name["en"] }

        assertEquals(listOf("Action", "Fantasy"), tagNames)
    }

    @Test
    fun `extracts cover_art relationship fileName`() {
        val first = json.decodeFromString<MangaListDto>(fixture("manga_list.json")).data.first()

        val cover = first.relationships.firstOrNull { it.type == "cover_art" }
        assertNotNull(cover)
        assertEquals("cover-solo-leveling.jpg", cover!!.attributes?.fileName)

        val author = first.relationships.firstOrNull { it.type == "author" }
        assertNotNull(author)
        assertEquals("Chugong", author!!.attributes?.name)
    }

    @Test
    fun `manga with missing optional fields uses DTO defaults`() {
        val second = json.decodeFromString<MangaListDto>(fixture("manga_list.json")).data[1]

        // empty description map + empty tag list should not throw and fall back to defaults
        assertTrue(second.attributes.description.isEmpty())
        assertTrue(second.attributes.tags.isEmpty())
    }

    @Test
    fun `parses at-home server response into AtHomeDto`() {
        val atHome = json.decodeFromString<AtHomeDto>(fixture("at_home_server.json"))

        assertEquals("https://uploads.mangadex.org", atHome.baseUrl)
        assertEquals("abc123def456hash", atHome.chapter.hash)
        assertEquals(3, atHome.chapter.data.size)
        assertEquals("1-page-aaaa.png", atHome.chapter.data.first())
        assertEquals("3-page-cccc.png", atHome.chapter.data.last())
    }
}
