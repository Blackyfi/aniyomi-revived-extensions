package eu.kanade.tachiyomi.source

/** Stub mirror of the host SourceFactory interface. */
interface SourceFactory {
    fun createSources(): List<MangaSource>
}
