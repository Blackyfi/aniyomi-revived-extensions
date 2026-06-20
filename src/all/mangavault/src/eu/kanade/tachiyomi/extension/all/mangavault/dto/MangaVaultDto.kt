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
