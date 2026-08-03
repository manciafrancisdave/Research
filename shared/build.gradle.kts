// AGP 9 no longer allows com.android.application alongside the Kotlin Multiplatform
// plugin, so the shared Compose UI lives here as a KMP *library* and :app is a thin
// Android host. The same library is exported to iOS as the ComposeApp framework.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.siren.mobile.resources"
}

kotlin {
    androidLibrary {
        namespace = "com.siren.mobile.shared"
        compileSdk = 37
        minSdk = 24
    }

    // iosX64 (Intel-Mac simulator) is deliberately omitted — some dependencies no
    // longer publish that variant, and Apple Silicon uses iosSimulatorArm64.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            // Discontinued for Multiplatform after 1.7.3 — pinned on purpose.
            implementation(libs.compose.icons.extended)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            // Multiplatform Firebase wrappers: one Kotlin implementation over the
            // native Android and iOS SDKs.
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.firestore)
        }
    }
}
