import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = rootProject.file("local.properties")
    .takeIf { it.exists() }
    ?.reader()
    ?.use { reader -> Properties().apply { load(reader) } }
    ?: Properties()

android {
    namespace = "bobko.stitch_screenshots"
    compileSdk = 37

    defaultConfig {
        applicationId = "bobko.stitch_screenshots"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("release.jks")
            storePassword = localProps["storePassword"] as String?
            keyAlias = localProps["keyAlias"] as String? ?: "mykey"
            keyPassword = localProps["keyPassword"] as String? ?: storePassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    buildFeatures {
        compose = true
    }

    sourceSets["main"].apply {
        manifest.srcFile("AndroidManifest.xml")
        kotlin.directories.clear()
        kotlin.directories.add("src")
        res.setSrcDirs(listOf("res"))
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
