@file:Suppress("unused")

package eu.kanade.tachiyomi.network

import kotlinx.serialization.json.Json
import okhttp3.Response

/**
 * Stub mirror of the host `parseAs`. Declared `context(Json)` exactly like core:common, so
 * extensions call it as `with(json) { response.parseAs<T>() }`. Requires `-Xcontext-receivers`.
 */
context(Json)
inline fun <reified T> Response.parseAs(): T = throw RuntimeException("stub")
