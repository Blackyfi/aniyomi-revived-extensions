@file:Suppress("FunctionName", "unused", "UNUSED_PARAMETER")

package eu.kanade.tachiyomi.network

import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody

/**
 * Stub mirrors of the host request builders. Extensions call `GET(url, headers)` /
 * `POST(url, headers, body)`; default args mirror the host so call sites resolve.
 */
private val DEFAULT_HEADERS = Headers.Builder().build()
private val DEFAULT_CACHE_CONTROL = CacheControl.Builder().build()
private val DEFAULT_BODY: RequestBody = throw RuntimeException("stub")

fun GET(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = throw RuntimeException("stub")

fun GET(
    url: HttpUrl,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = throw RuntimeException("stub")

fun POST(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = throw RuntimeException("stub")
