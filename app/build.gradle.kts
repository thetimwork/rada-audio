plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "africa.myrada.audio"
    compileSdk = 34

    defaultConfig {
        applicationId = "africa.myrada.audio"
        minSdk = 28 // DynamicsProcessing arrived in Android 9 (API 28)
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.0-alpha" + (System.getenv("GITHUB_RUN_NUMBER") ?: "1")
    }

    signingConfigs {
        // Shared alpha key, committed on purpose so every CI build can update the last one.
        // Not for Play Store releases: create a private upload key for that.
        create("alpha") {
            storeFile = rootProject.file("keystore/rada-alpha.jks")
            storePassword = "rada-alpha"
            keyAlias = "rada-alpha"
            keyPassword = "rada-alpha"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("alpha")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("alpha")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
}
