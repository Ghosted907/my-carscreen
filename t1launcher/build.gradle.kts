import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ghosted907.t1launcher"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.ghosted907.t1launcher"
        minSdk = 30
        targetSdk = 30
        versionCode = 33
        versionName = "1.14.0"
    }

    val releaseKeystoreB64 = System.getenv("RELEASE_KEYSTORE_BASE64")
    signingConfigs {
        if (!releaseKeystoreB64.isNullOrBlank()) {
            create("release") {
                val store = layout.buildDirectory.file("release-keystore.jks").get().asFile
                store.parentFile.mkdirs()
                store.writeBytes(Base64.getDecoder().decode(releaseKeystoreB64.trim()))
                storeFile = store
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    // Phone remote (T1 Remote) wire protocol, shared with the :t1remote module.
    sourceSets["main"].java.srcDir("../remote-protocol/src/main/kotlin")

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // This is a sideloaded Android 11/12 vehicle app, not a Google Play submission.
        // Retain the established target-30 compatibility; still surface this as a warning.
        warning += "ExpiredTargetSdkVersion"
        checkReleaseBuilds = false
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
