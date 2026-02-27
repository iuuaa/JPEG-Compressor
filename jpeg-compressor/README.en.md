English | [中文](README.md)

# JPEG Compressor Library

Android image compression library based on [libjpeg-turbo](https://github.com/libjpeg-turbo/libjpeg-turbo), supporting lossy/lossless JPEG compression. Modifications and added files relative to libjpeg-turbo are documented in [THIRD_PARTY.md](THIRD_PARTY.md).

---

## Build commands

### Build library (Release AAR)

```bash
./gradlew :jpeg-compressor:assembleRelease
```

### Build full project (with app)

```bash
./gradlew assembleRelease
```

### Debug build

```bash
./gradlew assembleDebug
```

### Windows (PowerShell / CMD)

```powershell
.\gradlew.bat :jpeg-compressor:assembleRelease
```

### Without Gradle Daemon

```bash
./gradlew assembleRelease --no-daemon
```

### Output locations

- **AAR**: `jpeg-compressor/build/outputs/aar/jpeg-compressor-<version>-release.aar` (e.g. `jpeg-compressor-1.0.5-release.aar`)
- **Sources / Javadoc**: Run `sourcesJar` and `javadocJar` to get `-sources.jar` and `-javadoc.jar` in `build/libs/` for IDE attachment.

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

### Option 2: AAR

1. **Build AAR**:
   ```bash
   ./gradlew :jpeg-compressor:assembleRelease
   ```

2. **Output path**: `jpeg-compressor/build/outputs/aar/jpeg-compressor-<version>-release.aar` (version from `gradle/libs.versions.toml`).

3. **Add to project**: Copy AAR to `app/libs/`, then:
   ```kotlin
   dependencies {
       implementation(files("libs/jpeg-compressor-1.0.5-release.aar"))
   }
   ```

4. **Optional dependencies**: The library uses `compileOnly` for RxJava3 and ExifInterface. If you use async/Rx or EXIF rotation, add:
   ```kotlin
   implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
   implementation("androidx.exifinterface:exifinterface:1.4.2")
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

## FAQ

### 1. Why does compression ratio go negative when re-compressing?

**Negative ratio means the output is larger than the input.** First compression already quantized and entropy-coded the image. Re-compressing decodes to RGB and re-encodes; the second pass is usually less efficient, so size can increase. Prefer compressing originals once; if you must re-compress, lower quality to force smaller size (with more quality loss).

### 2. Can compression be improved further?

**Current**: TurboJPEG API, TJSAMP_420, TJFLAG_FASTDCT (speed-focused). **Possible**: optimize_coding (needs libjpeg standard API, not exposed by TurboJPEG), SIMD for speed, or tweak quality/subsampling.

### 3. Max image size? OOM?

- **Limit**: No side above **8192** pixels, total pixels ≤ 8192×8192. See `JPEGCompressor.MAX_DIMENSION`.
- **OOM**: Compression allocates width×height×3 RGB buffer (~192MB at 8192×8192). Over limit returns error -2 (`JPEGCompressor.ERROR_IMAGE_TOO_LARGE`). For larger images, scale or process in chunks first.

---

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
