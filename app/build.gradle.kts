plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.weatherappphfinal"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.weatherappphfinal"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("androidx.work:work-runtime:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    testImplementation("org.mockito:mockito-core:3.12.4")
}