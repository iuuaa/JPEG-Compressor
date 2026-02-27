English | [中文](README.md)

# JPEG Compressor

Android JPEG image compression library based on [libjpeg-turbo](https://github.com/libjpeg-turbo/libjpeg-turbo). Supports sync/async/RxJava3, path/Bitmap/Uri input, scale/crop, and EXIF auto-rotation.

---

## How to use

### 1. Maven Central (recommended)

Ensure `mavenCentral()` is in `dependencyResolutionManagement.repositories` in the root `settings.gradle.kts`, then in your app `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.iuuaa:jpeg-compressor:1.0.5")
    // If using async / Rx / EXIF rotation, add:
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
}
```

### 2. Local module

In app `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":jpeg-compressor"))
}
```

### 3. Local AAR

1. Build: `./gradlew :jpeg-compressor:assembleRelease`  
   Output: `jpeg-compressor/build/outputs/aar/jpeg-compressor-<version>-release.aar`
2. Copy the AAR to your app’s `libs/` (or custom directory).
3. In app `build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/jpeg-compressor-1.0.5-release.aar"))
}
```

When using the AAR, add RxAndroid and ExifInterface yourself if you use async/Rx or EXIF rotation.

---

## Quick start

```kotlin
val compressor = JPEGCompressor.instance

val result = compressor.compressSync(
    inputPath = "/path/to/input.jpg",
    outputPath = "/path/to/output.jpg",
    quality = 85  // 1–100
)
if (result.success) {
    println("Compression ratio: ${result.compressionRatio}%")
} else {
    println("Failed: ${result.errorMessage}")
}
```

---

## API overview

| API | Description |
|-----|-------------|
| `JPEGCompressor.instance` | Singleton entry |
| `compressSync(path, path, quality, …)` | Sync compress (path) |
| `compressSync(bitmap, path, quality, …)` | Sync compress (Bitmap) |
| `compressSync(context, uri, path, quality, …)` | Sync compress (Uri) |
| `compressAsync(…, callback)` | Async compress, main-thread callback |
| `compressRx(…)` | RxJava3 `Single<CompressResult>` |
| `getImageInfo(path)` / `getImageInfo(bitmap)` / `getImageInfo(context, uri)` | Width, height, file size |
| `calculateCropRegionForAspectRatio(w, h, aspectW, aspectH)` | Center crop by aspect ratio |
| `alignCropRegionToMCU(crop, …)` | Align crop to MCU |
| `getExifRotation(path)` | EXIF rotation angle |
| `ImagePicker.instance.createPickImageIntent()` | Pick image Intent |
| `ImagePicker.instance.getFilePathFromUri(activity, uri)` | Uri → file path |
| `ImagePicker.instance.saveImageToGallery(context, path, displayName)` | Save to gallery (Android 10+) |

Common parameters: `quality`, `targetWidth`/`targetHeight`, `scale`, `cropX/Y/W/H`, `autoRotate`, `fallbackToOriginalOnError`.  
Result: `JPEGCompressor.CompressResult` (`success`, `compressionRatio`, `fallbackUsed`, `errorMessage`, etc.).

---

## Docs and samples

- **Library details** (build, integration, examples, FAQ, CompressResult, version requirements): [jpeg-compressor/README.md](jpeg-compressor/README.md) (中文) | [jpeg-compressor/README.en.md](jpeg-compressor/README.en.md) (English).
- **app** in this repo is a sample; run it to see compression, pick image, save to gallery.

---

## Version requirements

- **minSdk**: 21 (match your app)
- **Kotlin**: 1.9+
- **NDK**: 21+

---

## License

**Apache License 2.0**. See [LICENSE](LICENSE).
