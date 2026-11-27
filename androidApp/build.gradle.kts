plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val resolvedVersionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 666
val resolvedVersionName = System.getenv("APP_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "6.6.7-KMP"

android {
    namespace = "com.paregi.androidtv.animetvjmto"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.paregi.androidtv.animetvjmto"
        minSdk = 23
        targetSdk = 34
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
    }

    signingConfigs {
        getByName("debug") {
            val ks = rootProject.file("keystore/debug.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        create("release") {
            val envPath = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
            val ksFile = listOfNotNull(
                envPath?.let(::file),
                envPath?.let(rootProject::file),
                rootProject.file("keystore/release.keystore"),
                rootProject.file("keystore/debug.keystore")
            ).firstOrNull { it.exists() }
            if (ksFile != null) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "android"
                keyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
                    ?: if (ksFile.name.contains("release")) "animetv" else "androiddebugkey"
                keyPassword = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: "android"
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.tvprovider)
    implementation(libs.okhttp)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.play.services.cronet)
    implementation(libs.kotlin.stdlib.jdk7)
}
