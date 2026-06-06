@file:Suppress("unused")

package uy.kohesive.injekt

/**
 * Stub mirrors of the injekt symbols the extensions consume:
 *   - `by injectLazy()` for lazy DI (e.g. `private val json: Json by injectLazy()`)
 *   - the `Injekt` scope object, used with the `uy.kohesive.injekt.api.get` extension
 *     (e.g. `Injekt.get<Application>()`).
 *
 * The host app provides the real injekt at runtime; this only satisfies the compiler.
 */
inline fun <reified T : Any> injectLazy(): Lazy<T> = lazy { throw RuntimeException("stub") }

object InjektScope

val Injekt: InjektScope get() = throw RuntimeException("stub")
