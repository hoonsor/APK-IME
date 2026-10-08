// 簡拼引擎：純 Kotlin，可直接在電腦上跑單元測試（./gradlew :engine:zhuyin:test）。
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:contracts"))
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
