// 簡拼引擎：純 Kotlin，可直接在電腦上跑單元測試（./gradlew :engine:zhuyin:test）。
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
    api(project(":core:contracts"))
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
