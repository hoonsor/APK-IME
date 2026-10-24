// 主程式：IME Service、鍵盤 View、扇子、設定頁（對話 A 負責）。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 共用簽章 hoonsor-release.jks：由 CI 從 Secrets 解出；本機或沒有 Secrets 時退回 debug 簽章
val signingPath: String? = System.getenv("SIGNING_KEYSTORE_PATH")?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    namespace = "tw.hoonsor.ime.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "tw.hoonsor.ime"
        minSdk = 29
        targetSdk = 36
        // 發新版：改這兩行後 push vX.Y.Z tag（tag 必須與 versionName 相同，CI 會檢查）
        versionCode = 8
        versionName = "0.7.1"
    }

    signingConfigs {
        if (signingPath != null) {
            create("hoonsor") {
                storeFile = file(signingPath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (signingPath != null) signingConfigs.getByName("hoonsor") else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true // App 內更新用 BuildConfig.VERSION_NAME
    }
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
    implementation(project(":engine:zhuyin"))
    implementation(project(":core:dictionary"))
    implementation(project(":feature:voice"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.datastore.preferences)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.json) // ReleaseInfo 用 org.json；JVM 單元測試要真正的實作
}
