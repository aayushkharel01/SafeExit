import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget()
    js(IR) { browser(); binaries.executable() }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.10.1")
                implementation("androidx.core:core-ktx:1.15.0")
                implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
                implementation("com.google.firebase:firebase-auth-ktx")
                implementation("com.google.firebase:firebase-firestore-ktx")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1")
            }
        }
    }
}

android {
    namespace = "com.safeexit.app"
    compileSdk = 35
    defaultConfig { applicationId = "exit.android"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" }
}

compose.experimental { web.application { } }

if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")
