package eu.kanade.tachiyomi.extension.all.komga.dto

import kotlinx.serialization.Serializable

// Every field is defaulted / nullable so a single added or removed Komga API field never bricks the source.

// ---- /api/v1/series (and /api/v1/series/{id}) ----

@Serializable
data class SeriesPageDto(
    val content: List<SeriesDto> = emptyList(),
    val totalPages: Int = 0,
    val number: Int = 0,
    val last: Boolean = true,
)

@Serializable
data class SeriesDto(
    val id: String = "",
    val libraryId: String = "",
    val name: String = "",
    val metadata: SeriesMetadataDto = SeriesMetadataDto(),
    val booksMetadata: BooksMetadataDto = BooksMetadataDto(),
)

@Serializable
data class SeriesMetadataDto(
    val status: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val publisher: String? = null,
)

@Serializable
data class BooksMetadataDto(
    val summary: String? = null,
    val authors: List<AuthorDto> = emptyList(),
)

@Serializable
data class AuthorDto(
    val name: String? = null,
    val role: String? = null,
)

// ---- /api/v1/series/{id}/books ----

@Serializable
data class BookPageDto(
    val content: List<BookDto> = emptyList(),
    val totalPages: Int = 0,
    val number: Int = 0,
    val last: Boolean = true,
)

@Serializable
data class BookDto(
    val id: String = "",
    val seriesId: String = "",
    val name: String = "",
    val number: Float = 0f,
    val metadata: BookMetadataDto = BookMetadataDto(),
    val fileLastModified: String? = null,
    val created: String? = null,
)

@Serializable
data class BookMetadataDto(
    val title: String? = null,
    val number: String? = null,
    val numberSort: Float? = null,
    val releaseDate: String? = null,
    val authors: List<AuthorDto> = emptyList(),
)

// ---- /api/v1/books/{id}/pages ----

@Serializable
data class PageDto(
    val number: Int = 0,
    val fileName: String? = null,
    val mediaType: String? = null,
)

// ---- /api/v1/libraries ----

@Serializable
data class LibraryDto(
    val id: String = "",
    val name: String = "",
)
