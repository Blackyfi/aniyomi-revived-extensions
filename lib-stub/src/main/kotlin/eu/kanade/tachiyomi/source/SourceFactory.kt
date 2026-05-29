package eu.kanade.tachiyomi.source

/**
 * Stub mirror of the host SourceFactory. Returns `List<Source>` so both upstream-style
 * (`List<Source>`) and fork-style (`List<MangaSource>`, a covariant subtype) overrides compile;
 * generics erase to the same runtime signature either way.
 */
interface SourceFactory {
    fun createSources(): List<Source>
}
