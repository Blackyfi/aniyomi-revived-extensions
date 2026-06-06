package eu.kanade.tachiyomi.source.model

/**
 * Stub mirror of the host FilterList. Implements `List<Filter<*>>` so extensions can `forEach`
 * over it, and exposes the vararg constructor they build it with.
 */
data class FilterList(val list: List<Filter<*>>) : List<Filter<*>> by list {
    constructor(vararg fs: Filter<*>) : this(if (fs.isNotEmpty()) fs.asList() else emptyList())
}
