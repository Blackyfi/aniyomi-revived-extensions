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
}

dependencies {
    // The host app provides the source API at runtime; jsoup is also host-provided.
    // Theme classes are bundled into each derived extension APK that depends on this module.
    compileOnly(libs.source.api)
    compileOnly(libs.jsoup)
}
