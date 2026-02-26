plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.jpegcompressor"
    buildFeatures.viewBinding = true
    compileSdk {
        version = release(project.libs.versions.compileSdk.get().toInt())
    }

    defaultConfig {
        applicationId = "com.example.jpegcompressor"
        minSdk = project.libs.versions.minSdk.get().toInt()
        targetSdk = project.libs.versions.compileSdk.get().toInt()
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.material)

    implementation(libs.glide)
    implementation(libs.cameraview)
    implementation(libs.rxjava3.rxandroid)

    // implementation(project(":jpeg-compressor"))
    implementation(files("libs/jpeg-compressor-1.0.5-release.aar"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}