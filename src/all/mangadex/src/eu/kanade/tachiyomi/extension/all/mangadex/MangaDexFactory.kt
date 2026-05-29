package eu.kanade.tachiyomi.extension.all.mangadex

import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.SourceFactory

/**
 * One APK, many language variants. The loader instantiates the `tachiyomi.extension.class` entry;
 * because this is a [SourceFactory], it calls createSources() (MangaExtensionLoader.kt:317).
 * Each variant gets a distinct source id derived from name/lang/versionId (HttpSource.kt:57,87-91).
 */
class MangaDexFactory : SourceFactory {
    override fun createSources(): List<MangaSource> = listOf(
        MangaDex("en", "en"),
        MangaDex("es", "es"),
        MangaDex("es-419", "es-la"),
        MangaDex("fr", "fr"),
        MangaDex("de", "de"),
        MangaDex("pt-BR", "pt-br"),
        MangaDex("ja", "ja"),
        MangaDex("ko", "ko"),
        MangaDex("zh-Hans", "zh"),
        // Add languages here; each becomes its own selectable source.
    )
}
