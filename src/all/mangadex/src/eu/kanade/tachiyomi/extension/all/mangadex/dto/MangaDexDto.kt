package eu.kanade.tachiyomi.extension.all.mangadex.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Every field has a default / nullable type so a single added/missing API field never bricks the source.

@Serializable
data class MangaListDto(
    val data: List<MangaDataDto> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
    val total: Int = 0,
)

@Serializable
data class MangaWrapperDto(val data: MangaDataDto)

@Serializable
data class MangaDataDto(
    val id: String,
    val attributes: MangaAttributesDto = MangaAttributesDto(),
    val relationships: List<RelationshipDto> = emptyList(),
)

@Serializable
data class MangaAttributesDto(
    val title: Map<String, String> = emptyMap(),
    val description: Map<String, String> = emptyMap(),
    val status: String? = null,
    val tags: List<TagDto> = emptyList(),
)

@Serializable
data class TagDto(val attributes: TagAttributesDto = TagAttributesDto())

@Serializable
data class TagAttributesDto(val name: Map<String, String> = emptyMap())

@Serializable
data class RelationshipDto(
    val id: String,
    val type: String,
    val attributes: AttributesDto? = null,
)

@Serializable
data class AttributesDto(
    val name: String? = null,
    val fileName: String? = null,
)

@Serializable
data class ChapterListDto(
    val data: List<ChapterDataDto> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
    val total: Int = 0,
)

@Serializable
data class ChapterDataDto(
    val id: String,
    val attributes: ChapterAttributesDto = ChapterAttributesDto(),
    val relationships: List<RelationshipDto> = emptyList(),
)

@Serializable
data class ChapterAttributesDto(
    val title: String? = null,
    val chapter: String? = null,
    val volume: String? = null,
    @SerialName("translatedLanguage") val translatedLanguage: String? = null,
    @SerialName("publishAt") val publishAt: String? = null,
)

// "at-home" page-server response
@Serializable
data class AtHomeDto(
    val baseUrl: String,
    val chapter: AtHomeChapterDto = AtHomeChapterDto(),
)

@Serializable
data class AtHomeChapterDto(
    val hash: String = "",
    val data: List<String> = emptyList(),
)
