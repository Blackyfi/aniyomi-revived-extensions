@file:Suppress("unused")

package eu.kanade.tachiyomi.source

/**
 * Stub mirror of the host MangaSource interface — the base source contract. `SourceFactory`
 * returns `List<MangaSource>`; `CatalogueSource` extends it.
 */
interface MangaSource {
    val id: Long
    val name: String
    val lang: String
        get() = ""
}
