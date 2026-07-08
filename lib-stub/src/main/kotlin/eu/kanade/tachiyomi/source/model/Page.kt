@file:Suppress("unused")

package eu.kanade.tachiyomi.source.model

import android.net.Uri

/**
 * Stub mirror of the host Page class. Extensions construct it as
 * `Page(index, imageUrl = url)` and read [index] / [imageUrl] / [url].
 *
 * ABI-CRITICAL: the parameter list MUST match the host's real
 * `eu.kanade.tachiyomi.source.model.Page(index, url, imageUrl, uri)` exactly — including
 * the 4th `uri` param. Extensions call `Page(index, imageUrl = ...)` with the rest defaulted,
 * which the compiler lowers to the synthetic default-args constructor
 * `<init>(I, String, String, Uri, I, DefaultConstructorMarker)`. If this stub omits `uri`, the
 * compiler emits `<init>(I, String, String, I, DefaultConstructorMarker)` instead, which does
 * not exist in the host and throws NoSuchMethodError at runtime in every extension that builds
 * a Page with default args. Keep in sync with source-api's Page.kt.
 */
open class Page(
    val index: Int,
    val url: String = "",
    var imageUrl: String? = null,
    var uri: Uri? = null,
) {
    val number: Int
        get() = index + 1
}
