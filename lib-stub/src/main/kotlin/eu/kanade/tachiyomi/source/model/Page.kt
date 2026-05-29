@file:Suppress("unused")

package eu.kanade.tachiyomi.source.model

/**
 * Stub mirror of the host Page class. Extensions construct it as
 * `Page(index, imageUrl = url)` and read [index] / [imageUrl] / [url].
 */
open class Page(
    val index: Int,
    val url: String = "",
    var imageUrl: String? = null,
    var uri: android.net.Uri? = null,
) {
    val number: Int
        get() = index + 1
}
