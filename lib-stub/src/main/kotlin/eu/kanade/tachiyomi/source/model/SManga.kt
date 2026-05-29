@file:Suppress("PropertyName", "unused")

package eu.kanade.tachiyomi.source.model

import java.io.Serializable

/** Stub mirror of the host SManga interface (the host provides the real impl at runtime). */
interface SManga : Serializable {

    var url: String
    var title: String
    var artist: String?
    var author: String?
    var description: String?
    var genre: String?
    var status: Int
    var thumbnail_url: String?
    var update_strategy: UpdateStrategy
    var initialized: Boolean

    fun getGenres(): List<String>? = throw RuntimeException("stub")

    fun copy(): SManga = throw RuntimeException("stub")

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6

        fun create(): SManga = throw RuntimeException("stub")
    }
}
