@file:Suppress("unused")

package eu.kanade.tachiyomi.source

/**
 * Stub mirror of the host base `Source` contract. The fork's concrete base is `MangaSource`
 * (which extends this); upstream-style sources reference `Source` directly (e.g. a
 * `SourceFactory` returning `List<Source>`), so it must exist under this name too.
 */
interface Source {
    val id: Long
    val name: String
    val lang: String
        get() = ""
}
