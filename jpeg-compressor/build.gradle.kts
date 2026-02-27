@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
}

val libraryVersion: String = project.libs.versions.versionName.get()

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
}

dependencies {
    compileOnly(libs.rxjava3.rxandroid)
    compileOnly(libs.androidx.exifinterface)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}

// ---------- Dokka (used for Javadoc jar and IDE docs) ----------
dokka {
    moduleName.set("jpeg-compressor")
    dokkaSourceSets {
        configureEach {
            perPackageOption {
                matchingRegex.set(".*\\.internal.*")
                suppress.set(true)
            }
            reportUndocumented.set(true)
            skipDeprecated.set(false)
            skipEmptyPackages.set(true)
            sourceLink {
                localDirectory.set(projectDir.resolve("src/main/java"))
                remoteUrl.set(uri("https://github.com/iuuaa/JPEG-Compressor/tree/main/jpeg-compressor/src/main/java"))
                remoteLineSuffix.set("#L")
            }
        }
    }
}

// ---------- Maven Central (vanniktech/gradle-maven-publish-plugin) ----------
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

// Configure signatory from gradle.properties (supports both naming styles)
afterEvaluate {
    val signingKey = project.findProperty("signing.key")?.toString()
    val signingPassword = project.findProperty("signing.password")?.toString()
    if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        extensions.findByType<SigningExtension>()?.useInMemoryPgpKeys(signingKey, signingPassword)
    }
}
