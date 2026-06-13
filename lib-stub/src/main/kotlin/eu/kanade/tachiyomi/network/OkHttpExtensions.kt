@file:Suppress("unused")

package eu.kanade.tachiyomi.network

import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Response
import rx.Observable

/**
 * Stub mirror of the host `parseAs`. Declared `context(Json)` exactly like core:common, so
 * extensions call it as `with(json) { response.parseAs<T>() }`. Requires `-Xcontext-receivers`.
 */
context(Json)
inline fun <reified T> Response.parseAs(): T = throw RuntimeException("stub")

/**
 * Stub mirrors of the host's deprecated Rx network extensions (host provides at runtime).
 * Many ported sources call `client.newCall(req).asObservableSuccess()`.
 */
fun Call.asObservable(): Observable<Response> = throw RuntimeException("stub")
fun Call.asObservableSuccess(): Observable<Response> = throw RuntimeException("stub")

/** Stub mirror of the host's suspend `Call.await()` (eu.kanade.tachiyomi.network.await). */
suspend fun Call.await(): Response = throw RuntimeException("stub")

/** Stub mirror of the host's suspend `Call.awaitSuccess()`. */
suspend fun Call.awaitSuccess(): Response = throw RuntimeException("stub")
