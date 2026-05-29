@file:Suppress("unused")

package eu.kanade.tachiyomi.source

/**
 * Stub mirror of the host MangaSource interface — the fork's concrete source base.
 * Extends [Source] so upstream-style code referencing `Source` resolves. `CatalogueSource`
 * extends this; `HttpSource` ultimately implements it.
 */
interface MangaSource : Source
