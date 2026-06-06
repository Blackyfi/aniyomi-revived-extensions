plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "eu.kanade.tachiyomi.multisrc.madara"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // ---- JVM unit tests (Task D1) ----
    // Let Android-stubbed calls return defaults instead of throwing; jsoup selector mapping and
    // SManga/SChapter field assertions need no real device.
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
    // AGP's `NullSafeMutableLiveData` detector crashes (AIOOBE) while analyzing the `:lib-stub`
    // Kotlin sources during lint. Disable that single buggy detector.
    lint {
        disable += "NullSafeMutableLiveData"
    }
}

dependencies {
    // The host app provides the source API + network helpers at runtime; jsoup/okhttp too.
    // Theme classes are bundled into each derived extension APK that depends on this module.
    // `:lib-stub` mirrors the host source-api / core:common API surface so this compiles on CI
    // without the app artifacts in mavenLocal; it is `compileOnly` so it is never bundled.
    compileOnly(project(":lib-stub"))
    compileOnly(libs.okhttp)
    compileOnly(libs.jsoup)

    // Unit tests: the production deps above are `compileOnly`, so re-declare them on the test
    // runtime classpath. ParsedHttpSource construction only lazily touches injekt (never during the
    // selector/fromElement parse paths exercised here), so these run on the plain host JVM.
    testImplementation(libs.source.api)
    testImplementation(libs.core.common)
    testImplementation(libs.okhttp)
    testImplementation(libs.jsoup)
    testImplementation("junit:junit:4.13.2")
}

// core:common (pulled directly and transitively via source-api-android) declares a dependency on
// Aniyomi:i18n — a host-only generated module that is NOT published to mavenLocal. The unit tests
// never touch i18n, so drop it from the test classpaths to keep resolution fully offline.
configurations.matching { it.name.contains("UnitTest", ignoreCase = true) }.configureEach {
    exclude(group = "Aniyomi", module = "i18n")
}
