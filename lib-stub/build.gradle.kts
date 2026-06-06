// Compile-only stub of the host Aniyomi API surface.
//
// WHY THIS EXISTS: extensions compile `compileOnly` against the host app's `source-api` /
// `core:common` (the REAL classes are injected by the app at runtime). On CI there is no
// mavenLocal publish of those artifacts and building the whole app is too heavy. This module
// mirrors EXACTLY the public symbols the extensions reference, under the SAME fully-qualified
// names/packages, with throwing/`TODO()` bodies. It is consumed `compileOnly` by every module,
// so it is NEVER bundled into an APK (the host provides the real implementation at runtime).
//
// It is a `com.android.library` (not a plain kotlin lib) so android framework types referenced in
// the API surface — `Context`, `Application`, `SharedPreferences`, `PreferenceScreen` — resolve.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "eu.kanade.tachiyomi.stub"
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
        // `parseAs` is declared `context(Json)` in the host's core:common; mirror that here.
        freeCompilerArgs += listOf("-Xcontext-receivers")
    }
    // AGP's `NullSafeMutableLiveData` detector crashes (AIOOBE) while analyzing these stub
    // sources; it is irrelevant to a compile-only API stub.
    lint {
        disable += "NullSafeMutableLiveData"
    }
}

dependencies {
    // The stub only DECLARES signatures referencing these libs (Response, Json, jsoup types).
    // They are real mavenCentral artifacts and are themselves compileOnly here.
    compileOnly(libs.okhttp)
    compileOnly(libs.kotlinx.serialization.json)
    compileOnly(libs.jsoup)
    // androidx.preference provides PreferenceScreen, referenced by ConfigurableSource.
    compileOnly("androidx.preference:preference:1.2.1")
}
