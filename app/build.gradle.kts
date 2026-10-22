import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Version : une seule ligne dans version.properties (comme __init__.py de l'appli PC). Le numéro interne exigé par
// Android (versionCode, toujours croissant) en est déduit : 1.2.3 -> 10203.
val appVersion: String = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
    .getProperty("version").trim()
val appVersionCode: Int = appVersion.split(".").map { it.toInt() }.let { (major, minor, patch) ->
    major * 10000 + minor * 100 + patch
}

// Clé de signature des versions publiées : hors du dépôt (voir README, « Clé de signature »)
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.github.pauljean445.iptvtv"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.pauljean445.iptvtv"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode  // version.properties
        versionName = appVersion
        // Lecteur VLC embarqué en ARM 32 bits seulement : c'est le processeur de la Bravia, et les TV / boîtiers
        // 64 bits lancent aussi les applis 32 bits. APK deux fois plus léger qu'avec les deux versions.
        ndk { abiFilters += listOf("armeabi-v7a") }
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Version publiée : code optimisé (R8), nettement plus fluide qu'une version de développement
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core:1.13.1")  // FileProvider : APK de mise à jour confié à l'installeur
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.videolan.android:libvlc-all:3.6.0")
    implementation("com.google.zxing:core:3.5.3")
}
