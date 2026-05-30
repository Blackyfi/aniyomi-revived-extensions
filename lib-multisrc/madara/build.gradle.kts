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
}

dependencies {
    // The host app provides the source API + network helpers at runtime; jsoup/okhttp too.
    // Theme classes are bundled into each derived extension APK that depends on this module.
    compileOnly(libs.source.api)
    compileOnly(libs.core.common)
    compileOnly(libs.okhttp)
    compileOnly(libs.jsoup)
}
