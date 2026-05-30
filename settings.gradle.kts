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

// Shared theme base classes (lib-multisrc/<theme>)
include(":lib-multisrc:madara")

// Per-source modules, auto-discovered: src/<lang>/<name>
rootDir.resolve("src").listFiles { f -> f.isDirectory }?.sorted()?.forEach { langDir ->
    langDir.listFiles { f -> f.isDirectory }?.sorted()?.forEach { srcDir ->
        include(":src:${langDir.name}:${srcDir.name}")
    }
}
