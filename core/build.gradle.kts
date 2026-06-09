plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "keiyoushi.core"
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
    lint {
        disable += "NullSafeMutableLiveData"
    }
}

dependencies {
    // Shared `keiyoushi.utils` helpers, BUNDLED into each source that depends on `:core`
    // (the host app does NOT provide these classes — unlike `:lib-stub`, which it does).
    // The host API surface + okhttp/jsoup/serialization are provided at runtime → compileOnly.
    compileOnly(project(":lib-stub"))
    compileOnly(libs.okhttp)
    compileOnly(libs.jsoup)
    compileOnly(libs.kotlinx.serialization.json)
}
