plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ms.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ms.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "2.2"
    }

    // The permanent signing key is NOT in this public repo. GitHub Actions decodes it
    // from the KEYSTORE_B64 / KEYSTORE_PASSWORD secrets into KEYSTORE_FILE at build time.
    val keystoreFile = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "mslauncher"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    // Two editions:
    //  standard - no accessibility service at all (recommended)
    //  lock     - adds the optional double-tap-to-lock accessibility service
    flavorDimensions += "edition"
    productFlavors {
        create("standard") {
            dimension = "edition"
            isDefault = true
            buildConfigField("boolean", "LOCK_FEATURE", "false")
        }
        create("lock") {
            dimension = "edition"
            versionNameSuffix = "-lock"
            buildConfigField("boolean", "LOCK_FEATURE", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without the secret, fall back to a temporary key so the build still works
            signingConfig = if (keystoreFile != null) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
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
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
}
