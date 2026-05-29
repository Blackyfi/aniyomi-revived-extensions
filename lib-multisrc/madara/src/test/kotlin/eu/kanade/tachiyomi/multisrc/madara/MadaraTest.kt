package eu.kanade.tachiyomi.multisrc.madara

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for the shared Madara theme base class (Task D1).
 *
 * We instantiate a minimal concrete subclass and feed it jsoup-parsed HTML fixtures representative
 * of WordPress "Madara" markup, then assert the mapped SManga / SChapter fields. These exercise the
 * pure selector-mapping paths (popularMangaFromElement / mangaDetailsParse / chapterFromElement) and
 * never hit the network or injekt, so they run on the plain host JVM.
 */
class MadaraTest {

    /**
     * Minimal concrete Madara source. The base parse methods are `protected`; this subclass
     * re-exposes them publicly so the tests can drive them directly with a jsoup Element/Document.
     */
    private class TestMadara : Madara("Test", BASE_URL, "en") {
        public override fun popularMangaSelector(): String = super.popularMangaSelector()
        public override fun chapterListSelector(): String = super.chapterListSelector()

        public override fun popularMangaFromElement(element: Element): SManga =
            super.popularMangaFromElement(element)

        public override fun mangaDetailsParse(document: Document): SManga =
            super.mangaDetailsParse(document)

        public override fun chapterFromElement(element: Element): SChapter =
            super.chapterFromElement(element)

        // Abstract in HttpSource; never exercised by these tests (no page-comment chapter flow).
        override fun chapterPageParse(response: Response): SChapter =
            throw UnsupportedOperationException()
    }

    private val source = TestMadara()

    private fun fixtureDoc(name: String): Document {
        val html = javaClass.classLoader!!.getResourceAsStream(name)!!
            .bufferedReader().use { it.readText() }
        return Jsoup.parse(html, BASE_URL)
    }

    @Test
    fun `popularMangaFromElement maps title, url and lazy-loaded thumbnail`() {
        val doc = fixtureDoc("popular.html")
        val elements = doc.select(source.popularMangaSelector())
        assertEquals(2, elements.size)

        val first = source.popularMangaFromElement(elements[0])
        assertEquals("One Piece", first.title)
        // setUrlWithoutDomain strips the domain to a path
        assertEquals("/manga/one-piece/", first.url)
        // data-src wins over src when present
        assertEquals(
            "https://example.test/wp-content/uploads/one-piece-lazy.jpg",
            first.thumbnail_url,
        )
    }

    @Test
    fun `popularMangaFromElement falls back to src when no data-src`() {
        val doc = fixtureDoc("popular.html")
        val second = source.popularMangaFromElement(doc.select(source.popularMangaSelector())[1])

        assertEquals("Naruto", second.title)
        assertEquals("/manga/naruto/", second.url)
        assertEquals(
            "https://example.test/wp-content/uploads/naruto-thumb.jpg",
            second.thumbnail_url,
        )
    }

    @Test
    fun `mangaDetailsParse maps title, author, artist, genre, description and thumbnail`() {
        val manga = source.mangaDetailsParse(fixtureDoc("details.html"))

        assertEquals("One Piece", manga.title)
        assertEquals("Eiichiro Oda", manga.author)
        assertEquals("Eiichiro Oda", manga.artist)
        assertEquals("Action, Adventure, Comedy", manga.genre)
        assertEquals(
            "Gol D. Roger was known as the Pirate King, the strongest and most infamous being to have sailed the Grand Line.",
            manga.description,
        )
        assertEquals(
            "https://example.test/wp-content/uploads/one-piece-cover-hd.jpg",
            manga.thumbnail_url,
        )
    }

    @Test
    fun `chapterFromElement maps name and url`() {
        val doc = fixtureDoc("chapters.html")
        val elements = doc.select(source.chapterListSelector())
        assertEquals(2, elements.size)

        val first = source.chapterFromElement(elements[0])
        assertEquals("Chapter 1095", first.name)
        assertEquals("/manga/one-piece/chapter-1095/", first.url)

        val second = source.chapterFromElement(elements[1])
        assertEquals("Chapter 1094", second.name)
        assertEquals("/manga/one-piece/chapter-1094/", second.url)
    }

    @Test
    fun `source basic metadata is wired through the constructor`() {
        assertEquals("Test", source.name)
        assertEquals(BASE_URL, source.baseUrl)
        assertEquals("en", source.lang)
    }

    private companion object {
        const val BASE_URL = "https://example.test"
    }
}
