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

In root `settings.gradle.kts`:

```kotlin
include(":jpeg-compressor")
```

In app `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":jpeg-compressor"))
}
```

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

Common parameters: `quality`, `targetWidth`/`targetHeight`, `scale`, `cropX/Y/W/H`, `autoRotate`, `fallbackToOriginalOnError`.  
Result: `JPEGCompressor.CompressResult` (`success`, `compressionRatio`, `fallbackUsed`, `errorMessage`, etc.).

---

## Log viewing

The library logs compression parameters, duration, and memory usage. Filter by tag in Logcat:

- **Tag**: `JPEGCompressor`
- **Command line**: `adb logcat -s JPEGCompressor`
- **Android Studio Logcat**: enter `tag:JPEGCompressor` in the filter box, or filter by tag `JPEGCompressor`

---

## Docs and samples

- **Library details** (build, integration, examples, FAQ, CompressResult, version requirements): [jpeg-compressor/README.md](jpeg-compressor/README.md) (中文) | [jpeg-compressor/README.en.md](jpeg-compressor/README.en.md) (English).
- **app** in this repo is a sample; run it to see compression, pick image, save to gallery.
- **libjpeg-turbo modifications & patching steps**: see [jpeg-compressor/THIRD_PARTY.md](jpeg-compressor/THIRD_PARTY.md) for how to apply files under `libjpeg-turbo-patches` to the upstream libjpeg-turbo source.

---

## Version requirements

- **minSdk**: 21
- **Kotlin**: 1.9+
- **NDK**: 21+

---

## License

**Apache License 2.0**. See [LICENSE](LICENSE).
