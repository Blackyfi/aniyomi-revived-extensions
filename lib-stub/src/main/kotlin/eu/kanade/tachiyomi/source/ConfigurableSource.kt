@file:Suppress("unused")

package eu.kanade.tachiyomi.source

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen

/**
 * Stub mirror of the host ConfigurableSource. Extensions implement it to add preferences;
 * they override [setupPreferenceScreen] and call [getSourcePreferences].
 */
interface ConfigurableSource : MangaSource {

    /** @since extensions-lib 1.5 */
    fun getSourcePreferences(): SharedPreferences = throw RuntimeException("stub")

    fun setupPreferenceScreen(screen: PreferenceScreen)
}

fun ConfigurableSource.preferenceKey(): String = throw RuntimeException("stub")

fun ConfigurableSource.sourcePreferences(): SharedPreferences = throw RuntimeException("stub")

fun sourcePreferences(key: String): SharedPreferences = throw RuntimeException("stub")
