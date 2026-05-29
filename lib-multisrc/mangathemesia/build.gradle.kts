plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "eu.kanade.tachiyomi.multisrc.mangathemesia"
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
    // MangaThemesia reader pages live in an inline `ts_reader.run({...})` JSON blob.
    compileOnly(libs.kotlinx.serialization.json)
}
