plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    // Resolved here so the modules can apply them by id without a version — AGP 9
    // already puts Kotlin on the build classpath, and re-declaring a version in a
    // submodule fails with "already on the classpath with an unknown version".
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
