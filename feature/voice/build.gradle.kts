// 語音模組：錄音、STT、LLM 潤飾、AI 改寫、語音面板。
// 只依賴 :core:contracts 與 Android 框架（HttpURLConnection、org.json），沒有其他第三方函式庫，穩定優先。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "tw.hoonsor.ime.voice"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
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
    testImplementation(libs.junit)
}
