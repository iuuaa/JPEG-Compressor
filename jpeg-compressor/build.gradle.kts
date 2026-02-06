@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.library)
}

val libraryVersion = project.libs.versions.versionName.get()

android {
    namespace = "com.iuuaa.jpegcompressor"
    ndkVersion = "29.0.14206865"
    compileSdk {
        version = release(project.libs.versions.compileSdk.get().toInt())
    }

    defaultConfig {
        base.archivesName.set("jpeg-compressor-${libraryVersion}")
        minSdk = project.libs.versions.minSdk.get().toInt()

        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }

        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            pickFirsts += "**/libc++_shared.so"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(libs.rxjava3.rxandroid)
}