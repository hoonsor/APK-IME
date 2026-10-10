// 純 Kotlin 模組：共用介面與資料模型。改動規則見 docs/CONVENTIONS.md「契約變更流程」。
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    // 用 Android Studio 內建的 JDK 編譯，輸出相容 Java 17（不要求電腦另外安裝 JDK 17）
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(libs.coroutines.core)
}
