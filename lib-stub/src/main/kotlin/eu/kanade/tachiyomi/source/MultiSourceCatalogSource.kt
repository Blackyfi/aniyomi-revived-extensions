@file:Suppress("unused")

package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

/**
 * Stub mirror of the host MultiSourceCatalogSource.
 *
 * A source implements this when one library entry is backed by several
 * interchangeable upstream sources (e.g. a self-hosted server that scrapes the
 * same series from multiple sites). The host shows a source picker on the manga
 * screen and lets the user switch which source's chapters are displayed.
 *
 * All methods do blocking network I/O — the host calls them off the main
 * thread. Extensions are compiled without kotlinx-coroutines, so these are
 * intentionally NOT `suspend`.
 */
interface MultiSourceCatalogSource : MangaSource {

    /** All sources available for [manga] (primary + alternates). */
    fun getMangaSources(manga: SManga): List<MangaSourceInfo>

    /**
     * Switch the active/default source for [manga].
     *
     * @param sourceKey one of the [MangaSourceInfo.key] values, or "auto".
     * @return the refreshed source list.
     */
    fun setMangaSource(manga: SManga, sourceKey: String): List<MangaSourceInfo>

    /**
     * Chapters for a specific [sourceKey] WITHOUT changing the active source. Lets the host prefetch
     * alternate sources and switch between them instantly. Default returns an empty list (the host
     * then falls back to [setMangaSource] + [getChapterList]).
     *
     * @param sourceKey one of the [MangaSourceInfo.key] values, or "auto".
     */
    fun getChapterListForSource(manga: SManga, sourceKey: String): List<SChapter> = emptyList()
}

/** One selectable source for a manga. Mirrors the host data class field-for-field. */
data class MangaSourceInfo(
    val key: String,
    val name: String,
    val totalChapters: Int = 0,
    val latestChapter: Float = 0f,
    val isPrimary: Boolean = false,
    val isDefault: Boolean = false,
    val isMostUpToDate: Boolean = false,
    val isEffective: Boolean = false,
)
