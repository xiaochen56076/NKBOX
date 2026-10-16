// UtilityBox · 根 build.gradle.kts
// AGP 9.0+ 内置 Kotlin 支持 —— 官方要求不要再加 org.jetbrains.kotlin.android。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
