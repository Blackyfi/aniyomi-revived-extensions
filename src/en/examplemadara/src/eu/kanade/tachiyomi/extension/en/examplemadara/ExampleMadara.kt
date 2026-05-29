package eu.kanade.tachiyomi.extension.en.examplemadara

import eu.kanade.tachiyomi.multisrc.madara.Madara

/**
 * Demonstration of the theme pattern: a whole Madara source in a handful of lines.
 * Replace name/baseUrl with a real site and override selectors ONLY where that site deviates.
 *
 * NOTE: this points at a placeholder domain and is a template, not a working source. Delete it
 * (and its module dir) once you have real Madara sites, or repoint it.
 */
class ExampleMadara : Madara(
    name = "Example Madara Site",
    baseUrl = "https://example.invalid",
    lang = "en",
) {
    // Example deviation: this hypothetical site serves its catalogue under /series instead of /manga.
    override val mangaSubString = "series"
}
