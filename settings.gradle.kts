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
        // The unit tests use source-api / core:common published to mavenLocal from the
        // app tree (see SETUP.md).
        mavenLocal()
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "aniyomi-revived-extensions"

// Compile-only stub of the host Aniyomi API surface (source-api / core:common). Consumed
// `compileOnly` so the EXT repo compiles on CI without publishing the app artifacts to mavenLocal.
include(":lib-stub")

// Shared theme base classes (lib-multisrc/<theme>)
include(":lib-multisrc:madara")
include(":lib-multisrc:mangathemesia")

// Per-source modules, auto-discovered: src/<lang>/<name>
rootDir.resolve("src").listFiles { f -> f.isDirectory }?.sorted()?.forEach { langDir ->
    langDir.listFiles { f -> f.isDirectory }?.sorted()?.forEach { srcDir ->
        include(":src:${langDir.name}:${srcDir.name}")
    }
}
