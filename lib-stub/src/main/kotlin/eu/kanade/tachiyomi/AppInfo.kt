@file:Suppress("unused")

package eu.kanade.tachiyomi

/**
 * Stub mirror of the host `AppInfo` (eu.kanade.tachiyomi.AppInfo). Used by extensions for
 * User-Agent/version info. The host app supplies the real implementation at runtime.
 */
object AppInfo {
    fun getVersionCode(): Int = throw RuntimeException("stub")
    fun getVersionName(): String = throw RuntimeException("stub")
    fun getSupportedImageMimeTypes(): List<String> = throw RuntimeException("stub")
}
