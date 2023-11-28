plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.guhao.opensource.cutme.android"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.guhao.opensource.cutme.android"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    // For media playback using ExoPlayer
    implementation(libs.androidx.media3.exoplayer)

    // For DASH playback support with ExoPlayer
    implementation(libs.androidx.media3.exoplayer.dash)
    // For HLS playback support with ExoPlayer
    implementation(libs.androidx.media3.exoplayer.hls)
    // For RTSP playback support with ExoPlayer
    implementation(libs.androidx.media3.exoplayer.rtsp)
    // For ad insertion using the Interactive Media Ads SDK with ExoPlayer
    implementation(libs.androidx.media3.exoplayer.ima)

    // For loading data using the Cronet network stack
    implementation(libs.androidx.media3.datasource.cronet)
    // For loading data using the OkHttp network stack
    implementation(libs.androidx.media3.datasource.okhttp)
    // For loading data using librtmp
    implementation(libs.androidx.media3.datasource.rtmp)

    // For building media playback UIs
    implementation(libs.androidx.media3.ui)
    // For building media playback UIs for Android TV using the Jetpack Leanback library
    implementation(libs.androidx.media3.ui.leanback)

    // For exposing and controlling media sessions
    implementation(libs.androidx.media3.session)

    // For extracting data from media containers
    implementation(libs.androidx.media3.extractor)

    // For integrating with Cast
    implementation(libs.androidx.media3.cast)

    // For scheduling background operations using Jetpack Work's WorkManager with ExoPlayer
    implementation(libs.androidx.media3.exoplayer.workmanager)

    // For transforming media files
    implementation(libs.androidx.media3.transformer)

    // Utilities for testing media components (including ExoPlayer components)
    implementation(libs.androidx.media3.test.utils)
    // Utilities for testing media components (including ExoPlayer components) via Robolectric
    implementation(libs.androidx.media3.test.utils.robolectric)

    // Common functionality for media database components
    implementation(libs.androidx.media3.database)
    // Common functionality for media decoders
    implementation(libs.androidx.media3.decoder)
    // Common functionality for loading data
    implementation(libs.androidx.media3.datasource)
    // Common functionality used across multiple media libraries
    implementation(libs.androidx.media3.common)

    implementation(libs.androidx.runtime.livedata)

    implementation(libs.glide.compose)
}