// 語音模組（對話 B 負責）。只能依賴 :core:contracts，不可依賴 :app 或 :core:dictionary。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "tw.hoonsor.ime.voice"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    // 用 Android Studio 內建的 JDK 編譯，輸出相容 Java 17（不要求電腦另外安裝 JDK 17）
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core:contracts"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.datastore.preferences)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
}
