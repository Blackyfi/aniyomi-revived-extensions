plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "keiyoushi.lib.zipinterceptor"
    compileSdk = 35
    sourceSets { getByName("main") { java.srcDirs("src"); res.srcDirs("res"); assets.srcDirs("assets") } }
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    lint { disable += "NullSafeMutableLiveData" }
}
dependencies {
    compileOnly(project(":lib-stub"))
    compileOnly("androidx.preference:preference:1.2.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    compileOnly(libs.okhttp)
    compileOnly(libs.jsoup)
    compileOnly("com.github.tachiyomiorg:image-decoder:e08e9be535")
}
