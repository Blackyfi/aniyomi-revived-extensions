package eu.kanade.tachiyomi.extension.all.mangavault.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Every field is defaulted / nullable so a single added or removed backend field
// never bricks the source. Mirrors backend/src/schemas/extension.py.

@Serializable
data class MangaListResponse(
    val mangas: List<MangaDto> = emptyList(),
    @SerialName("has_next_page") val hasNextPage: Boolean = false,
)

@Serializable
data class MangaDto(
    val id: Int = 0,
    val title: String = "",
    @SerialName("thumbnail_url") val thumbnailUrl: String = "",
    val status: String = "unknown",
    val authors: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val description: String? = null,
    // Only populated on the detail endpoint; empty in list responses.
    val sources: List<SourceDto> = emptyList(),
)

@Serializable
data class SourceDto(
    val key: String = "primary",
    val name: String = "",
    val site: String = "",
    @SerialName("total_chapters") val totalChapters: Int = 0,
    @SerialName("latest_chapter_number") val latestChapterNumber: Float = 0f,
    @SerialName("is_primary") val isPrimary: Boolean = false,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("is_most_up_to_date") val isMostUpToDate: Boolean = false,
    @SerialName("is_effective") val isEffective: Boolean = false,
)

@Serializable
data class ChapterDto(
    val id: Int = 0,
    @SerialName("manga_id") val mangaId: Int = 0,
    @SerialName("chapter_number") val chapterNumber: Float = 0f,
    val title: String? = null,
    @SerialName("date_upload") val dateUpload: Long = 0L,
    val scanlator: String? = null,
    @SerialName("is_upscaled") val isUpscaled: Boolean = false,
)

@Serializable
data class PageDto(
    val index: Int = 0,
    @SerialName("image_url") val imageUrl: String = "",
)
