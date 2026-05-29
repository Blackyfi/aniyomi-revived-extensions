@file:Suppress("unused", "UNUSED_PARAMETER")

package uy.kohesive.injekt.api

import uy.kohesive.injekt.InjektScope

/**
 * Stub mirror of the injekt `get` scope extension, imported as `uy.kohesive.injekt.api.get`
 * and called as `Injekt.get<Application>()`.
 */
inline fun <reified T : Any> InjektScope.get(): T = throw RuntimeException("stub")
