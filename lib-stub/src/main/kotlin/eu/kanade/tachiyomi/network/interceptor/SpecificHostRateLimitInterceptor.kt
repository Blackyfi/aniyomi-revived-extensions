@file:Suppress("unused")

package eu.kanade.tachiyomi.network.interceptor

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Stub mirrors of the host per-host rate-limit builder extensions
 * (core:common SpecificHostRateLimitInterceptor). Host provides the real impl at runtime.
 */
fun OkHttpClient.Builder.rateLimitHost(
    httpUrl: HttpUrl,
    permits: Int,
    period: Duration = 1.seconds,
): OkHttpClient.Builder = throw RuntimeException("stub")

fun OkHttpClient.Builder.rateLimitHost(
    url: String,
    permits: Int,
    period: Duration = 1.seconds,
): OkHttpClient.Builder = throw RuntimeException("stub")
