plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.flirttime"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.flirttime"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        buildConfigField("String", "BASE_URL", "\"https://flirttimeapi.walit.in/\"")
        // Web client ID from google-services.json (client_type 3) — used for Google ID token requests
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"828544365224-4f1m6hemudd6c3b7r1eucs58ga3orm4s.apps.googleusercontent.com\""
        )
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)

    // Navigation Component
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Coroutines & Lifecycle
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Scalable dp & sp
    implementation(libs.sdp.android)
    implementation(libs.ssp.android)

    // Lottie Animation
    implementation(libs.lottie)

    // Fresco Image Loading
    implementation(libs.fresco)

    // Google Play Billing (Premium subscriptions - iOS StoreKit equivalent)
    implementation(libs.play.billing)

    // Shimmer
    implementation(libs.shimmer)

    // Image Cropper
    implementation(libs.android.image.cropper)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    // Socket.IO (chat realtime)
    implementation(libs.socket.io.client)

    // Firebase (FCM)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Google Sign-In via Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}