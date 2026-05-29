@file:Suppress("unused")

package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList

/**
 * Stub mirror of the host CatalogueSource. Extensions/themes override [supportsLatest] and
 * [getFilterList]; HttpSource implements this interface.
 */
interface CatalogueSource : MangaSource {
    override val lang: String
    val supportsLatest: Boolean
    fun getFilterList(): FilterList
}
