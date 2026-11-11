import java.util.Base64

plugins {
    id("com.android.application")
}

android {
    namespace = "com.ghosted907.clusterdiag"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.ghosted907.clusterdiag"
        minSdk = 30
        targetSdk = 30
        versionCode = 8
        versionName = "1.7.0"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // Sideload target is Android 11. Play's target-SDK lint is not a release blocker.
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

tasks.register<Copy>("copyDebugApk") {
    dependsOn("packageDebug")
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(rootProject.layout.projectDirectory.dir("artifacts"))
    rename { "clusterdiag-debug.apk" }
}

afterEvaluate {
    tasks.named("assembleDebug").configure {
        finalizedBy("copyDebugApk")
    }
}
