@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka)
    `maven-publish`
    signing
}

val libraryVersion: String = project.libs.versions.versionName.get()

val publishGroupId: String = project.findProperty("PUBLISH_GROUP_ID") as String? ?: "com.iuuaa"
val publishArtifactId: String = project.findProperty("PUBLISH_ARTIFACT_ID") as String? ?: "jpeg-compressor"
val publishVersion: String = libraryVersion

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
    publishing {
        singleVariant("release")
    }
}

dependencies {
    compileOnly(libs.rxjava3.rxandroid)
    compileOnly(libs.androidx.exifinterface)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}

// ---------- Dokka：仅用于生成 Javadoc（Maven Central 要求 + IDE 文档） ----------
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
                remoteUrl.set(uri("https://github.com/your-username/JpegCompressor/tree/main/jpeg-compressor/src/main/java"))
                remoteLineSuffix.set("#L")
            }
        }
    }
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(android.sourceSets["main"].java.srcDirs)
}

val javadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.named("dokkaGeneratePublicationHtml"))
    archiveClassifier.set("javadoc")
    from(layout.buildDirectory.dir("dokka/html"))
}

// ---------- Maven Publish + Signing（发布至 Maven Central） ----------
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = publishGroupId
                artifactId = publishArtifactId
                version = publishVersion
                from(components["release"])
                artifact(sourcesJar)
                artifact(javadocJar)
                pom {
                    name.set("JPEG Compressor")
                    description.set("Android JPEG image compression library based on libjpeg-turbo. Sync/async/RxJava3, path/Bitmap/Uri input, scale/crop/EXIF rotation.")
                    url.set("https://github.com/your-username/JpegCompressor")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("iuuaa")
                            name.set("Developer")
                            email.set("dev@example.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/your-username/JpegCompressor.git")
                        developerConnection.set("scm:git:ssh://github.com/your-username/JpegCompressor.git")
                        url.set("https://github.com/your-username/JpegCompressor")
                    }
                }
            }
        }
        repositories {
            maven {
                name = "Sonatype"
                url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                credentials {
                    username = project.findProperty("sonatypeUsername") as String? ?: ""
                    password = project.findProperty("sonatypePassword") as String? ?: ""
                }
            }
        }
    }
    signing {
        val signingRequired = (project.findProperty("signing.keyId") as String?).isNullOrBlank().not()
        if (signingRequired) {
            sign(publishing.publications["release"])
        }
    }
}