plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    android {
        namespace = "com.paregi.animetv.shared"
        compileSdk = 37
        minSdk = 23
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        androidResources { enable = true }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.animation)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("dev.chrisbanes.haze:haze:1.7.3")
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.engine.defaults)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.datasource)
            implementation(libs.media3.database)
            implementation(libs.media3.exoplayer.hls)
            implementation(libs.media3.exoplayer.dash)
            implementation(libs.media3.ui)
            implementation(libs.zxing.core)
        }
    }
}

