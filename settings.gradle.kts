pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // Version catalog auto-loaded from gradle/libs.versions.toml as `libs`.
    repositories {
        google()
        mavenCentral()
        // Local Maven (SETUP.md Option B): consumes :source-api published via
        // `./gradlew :source-api:publishToMavenLocal` from the app tree.
        mavenLocal()
        // For consuming your own published source-api (see SETUP.md).
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "aniyomi-revived-extensions"

// Compile-only stub of the host Aniyomi API surface (source-api / core:common). Consumed
// `compileOnly` so the EXT repo compiles on CI without publishing the app artifacts to mavenLocal.
include(":lib-stub")

// Shared `keiyoushi.utils` helpers, bundled into sources that depend on it (vendored from keiyoushi).
include(":core")

// Shared helper libs (lib/<name>) and theme bases (lib-multisrc/<theme>) — auto-discovered so
// vendoring a new one only requires dropping its module dir + build.gradle.kts.
rootDir.resolve("lib").listFiles { f -> f.isDirectory }?.sorted()?.forEach { include(":lib:${it.name}") }
rootDir.resolve("lib-multisrc").listFiles { f -> f.isDirectory }?.sorted()?.forEach { include(":lib-multisrc:${it.name}") }

// Per-source modules, auto-discovered: src/<lang>/<name>
rootDir.resolve("src").listFiles { f -> f.isDirectory }?.sorted()?.forEach { langDir ->
    langDir.listFiles { f -> f.isDirectory }?.sorted()?.forEach { srcDir ->
        include(":src:${langDir.name}:${srcDir.name}")
    }
}
