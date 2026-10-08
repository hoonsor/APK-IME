// 純 Kotlin 模組：共用介面與資料模型。改動規則見 docs/CONVENTIONS.md「契約變更流程」。
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    api(libs.coroutines.core)
}
