@file:Suppress("PropertyName", "unused")

package eu.kanade.tachiyomi.source.model

import java.io.Serializable

/** Stub mirror of the host SChapter interface. */
interface SChapter : Serializable {

    var url: String
    var name: String
    var date_upload: Long
    var chapter_number: Float
    var scanlator: String?

    fun copyFrom(other: SChapter): Unit = throw RuntimeException("stub")

    companion object {
        fun create(): SChapter = throw RuntimeException("stub")
    }
}
