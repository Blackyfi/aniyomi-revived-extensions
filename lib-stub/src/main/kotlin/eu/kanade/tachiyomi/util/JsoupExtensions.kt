@file:Suppress("unused", "UNUSED_PARAMETER")

package eu.kanade.tachiyomi.util

import okhttp3.Response
import org.jsoup.nodes.Document

/** Stub mirror of the host `Response.asJsoup` extension. */
fun Response.asJsoup(html: String? = null): Document = throw RuntimeException("stub")
