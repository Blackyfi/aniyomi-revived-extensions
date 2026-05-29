@file:Suppress("unused", "UNUSED_PARAMETER")

package eu.kanade.tachiyomi.network.interceptor

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Stub mirrors of the host `OkHttpClient.Builder.rateLimit` extensions. Extensions call
 * `newBuilder().rateLimit(permits = 5).build()`.
 */
@Deprecated("Use the version with kotlin.time APIs instead.")
fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS,
): OkHttpClient.Builder = throw RuntimeException("stub")

fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Duration = 1.seconds,
): OkHttpClient.Builder = throw RuntimeException("stub")
