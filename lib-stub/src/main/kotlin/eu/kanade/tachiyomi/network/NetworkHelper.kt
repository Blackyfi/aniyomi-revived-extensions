@file:Suppress("unused")

package eu.kanade.tachiyomi.network

import okhttp3.OkHttpClient

/**
 * Stub mirror of the host NetworkHelper. Extensions read `network.client` (to derive their own
 * OkHttpClient via `newBuilder()`) and call `network.defaultUserAgentProvider()`.
 */
class NetworkHelper {
    val client: OkHttpClient
        get() = throw RuntimeException("stub")

    val cloudflareClient: OkHttpClient
        get() = throw RuntimeException("stub")

    fun defaultUserAgentProvider(): String = throw RuntimeException("stub")
}
