plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.retrocable.box"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.retrocable.box"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "0.5"
    }

    // A fixed signing key, so each new build installs over the old one
    // instead of failing with "App not installed". Personal sideload use only.
    signingConfigs {
        create("sideload") {
            storeFile = file("cablebox.keystore")
            storePassword = "cablebox"
            keyAlias = "cablebox"
            keyPassword = "cablebox"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("sideload")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
}
