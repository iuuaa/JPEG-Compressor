English | [中文](README.md)

# JPEG Compressor Library

Android image compression library based on [libjpeg-turbo](https://github.com/libjpeg-turbo/libjpeg-turbo), supporting lossy/lossless JPEG compression. Modifications and added files relative to libjpeg-turbo are documented in [THIRD_PARTY.md](THIRD_PARTY.md).

---

## Features

- libjpeg-turbo 3.x high-performance compression
- Sync, async callback, and RxJava3 APIs
- ABIs: armeabi-v7a, arm64-v8a, x86, x86_64
- Image info (size, file size)
- Compression stats (ratio, size comparison)

---

## Integration

### Option 1: Module dependency

In `settings.gradle.kts`:

```kotlin
include(":jpeg-compressor")
```

In app `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":jpeg-compressor"))
}
```

### Option 2: Maven Central

In root `settings.gradle.kts` make sure:

```kotlin
repositories {
    mavenCentral()
    // ...
}
```

In the app `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.iuuaa:jpeg-compressor:1.0.5")
    // If you use async/Rx or EXIF rotation (library uses compileOnly):
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
}
```

---

## Usage

### 1. Sync compress

```kotlin
val compressor = JPEGCompressor.instance

val result = compressor.compressSync(
    inputPath = "/path/to/input.jpg",
    outputPath = "/path/to/output.jpg",
    quality = 85  // 1-100, default 85
)

if (result.success) {
    println("Success! Ratio: ${result.compressionRatio}%")
} else {
    println("Failed: ${result.errorMessage}")
}
```

### 2. Async (callback)

```kotlin
compressor.compressAsync(
    inputPath = inputPath,
    outputPath = outputPath,
    quality = 85,
    callback = object : JPEGCompressor.CompressCallback {
        override fun onSuccess(result: JPEGCompressor.CompressResult) { /* main thread */ }
        override fun onError(error: Throwable) { /* handle error */ }
    }
)
```

### 3. RxJava3

```kotlin
compressor.compressRx(inputPath, outputPath, 85)
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
        { result -> /* handle */ },
        { error -> /* handle */ }
    )
```

### 4. Scale / target size

- **targetWidth** / **targetHeight**: 0 = no limit; one side scales proportionally.
- **scale**: 0 < scale <= 1, takes precedence over target size.

Output size is the largest TurboJPEG scaling factor not exceeding the target.

```kotlin
compressor.compressSync(inputPath, outputPath, 85, scale = 0.5f)

val info = compressor.getImageInfo(inputPath)
val (tw, th) = if (info.width >= info.height) 1920 to (1920 * info.height / info.width) else (1920 * info.width / info.height) to 1920
compressor.compressSync(inputPath, outputPath, 85, targetWidth = tw, targetHeight = th)
```

### 5. Image info

```kotlin
val info = compressor.getImageInfo("/path/to/image.jpg")
// info.width, info.height, info.fileSize, info.path
```

### 6. Log viewing

Compression logs use tag `JPEGCompressor`. Command line: `adb logcat -s JPEGCompressor`; Android Studio Logcat filter: `tag:JPEGCompressor`.

## Full example

```kotlin
val result = JPEGCompressor.instance.compressSync(
    inputPath = "/sdcard/DCIM/photo.jpg",
    outputPath = "/sdcard/DCIM/photo_compressed.jpg",
    quality = 80
)

JPEGCompressor.instance.compressAsync(inputPath, outputPath, 80,
    callback = object : JPEGCompressor.CompressCallback {
        override fun onSuccess(result: JPEGCompressor.CompressResult) {
            Log.d("Compress", "Ratio: ${result.compressionRatio}%")
        }
        override fun onError(error: Throwable) { Log.e("Compress", "Failed", error) }
    }
)

JPEGCompressor.instance.compressRx(inputPath, outputPath, 80)
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe({ updateUI(it) }, { it.printStackTrace() })
```

---

## CompressResult fields

| Field | Type | Description |
|-------|------|-------------|
| success | Boolean | Success (including fallback) |
| inputPath | String | Input path ("bitmap:WxH" for Bitmap) |
| outputPath | String | Output path |
| inputSize | Long | Original size (bytes) |
| outputSize | Long | Compressed size (bytes) |
| compressionRatio | Float | Ratio (%); negative if output larger |
| inputWidth/Height | Int | Input dimensions |
| outputWidth/Height | Int | Output dimensions |
| fallbackUsed | Boolean | True if fallback (copy original) was used |
| rotationApplied | Int | EXIF rotation applied (90/180/270) or 0 |
| errorMessage | String? | Error message on failure |

---

## Version requirements

| Item | Requirement | Notes |
|------|--------------|-------|
| **Android minSdk** | 21 | Match your app |
| **Android compileSdk** | 36 | Library default; app can use ≥ |
| **Kotlin** | 1.9+ | Compatible with AGP |
| **JDK** | 17 | Library compileOptions |
| **Gradle** | 8.x / 9.x | Compatible with AGP 9.x |
| **Android Gradle Plugin** | 9.x | See root `libs.versions.toml` |
| **NDK** | 21+ (29.x recommended) | For native build; library uses 29.0.x |

- **Module dependency**: Host project must meet Gradle / AGP / JDK above.
- **AAR only**: minSdk ≥ 21 and Kotlin runtime are enough.

---

## License

**Apache License 2.0**. See root [LICENSE](../LICENSE).
