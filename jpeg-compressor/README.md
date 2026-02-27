[English](README.en.md) | 中文

# JPEG Compressor Library

基于 [libjpeg-turbo](https://github.com/libjpeg-turbo/libjpeg-turbo) 的 Android 图片压缩库，支持 JPEG 无损/有损压缩。对 libjpeg-turbo 的修改与新增文件说明见 [THIRD_PARTY.md](THIRD_PARTY.md)。

---

## 功能特性

- ✅ 基于 libjpeg-turbo 3.x 高性能压缩
- ✅ 支持同步、异步回调、RxJava3 三种调用方式
- ✅ 支持多架构：armeabi-v7a、arm64-v8a、x86、x86_64
- ✅ 获取图片信息（尺寸、文件大小）
- ✅ 压缩结果统计（压缩率、文件大小对比）

---

## 集成方式

### 方式一：作为 Module 依赖

在 `settings.gradle.kts` 中：

```kotlin
include(":jpeg-compressor")
```

在 app 的 `build.gradle.kts` 中：

```kotlin
dependencies {
    implementation(project(":jpeg-compressor"))
}
```

### 方式二：Maven Central

在宿主工程根目录 `settings.gradle.kts` 的 `dependencyResolutionManagement.repositories` 中确保包含：

```kotlin
repositories {
    mavenCentral()
    // ...
}
```

在 app 的 `build.gradle.kts` 中：

```kotlin
dependencies {
    implementation("io.github.iuuaa:jpeg-compressor:1.0.5")
    // 若使用异步 / Rx / EXIF 自动旋转，需同时添加（库内为 compileOnly）：
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
}
```

---

## 使用方法

### 1. 同步压缩

```kotlin
val compressor = JPEGCompressor.instance

val result = compressor.compressSync(
    inputPath = "/path/to/input.jpg",
    outputPath = "/path/to/output.jpg",
    quality = 85  // 1-100，默认 85
)

if (result.success) {
    println("压缩成功！压缩率: ${result.compressionRatio}%")
} else {
    println("压缩失败: ${result.errorMessage}")
}
```

### 2. 异步压缩（回调）

```kotlin
compressor.compressAsync(
    inputPath = inputPath,
    outputPath = outputPath,
    quality = 85,
    callback = object : JPEGCompressor.CompressCallback {
        override fun onSuccess(result: JPEGCompressor.CompressResult) {
            // 主线程回调
        }
        override fun onError(error: Throwable) {
            // 错误处理
        }
    }
)
```

### 3. RxJava3 方式

```kotlin
compressor.compressRx(inputPath, outputPath, 85)
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
        { result -> /* 处理结果 */ },
        { error -> /* 处理错误 */ }
    )
```

### 4. 压缩至指定宽高或比例

可选参数（默认保持原图尺寸）：

- **targetWidth** / **targetHeight**：目标宽高（0 表示不限制）；仅设一边时按比例缩放。
- **scale**：缩放比例 (0 &lt; scale &lt;= 1)，优先于 target 宽高。

实际输出尺寸会取 TurboJPEG 支持的缩放因子下不超过目标的**最大**尺寸。

```kotlin
// 缩放到一半
compressor.compressSync(inputPath, outputPath, 85, scale = 0.5f)

// 限制最长边 1920
val info = compressor.getImageInfo(inputPath)
val (tw, th) = if (info.width >= info.height) 1920 to (1920 * info.height / info.width) else (1920 * info.width / info.height) to 1920
compressor.compressSync(inputPath, outputPath, 85, targetWidth = tw, targetHeight = th)
```

### 5. 获取图片信息

```kotlin
val info = compressor.getImageInfo("/path/to/image.jpg")
// info.width, info.height, info.fileSize, info.path（尺寸与 fileSize 由 BitmapFactory 仅读头/文件系统得到）
```

### 6. 日志查看

压缩过程会输出参数与耗时等日志，tag 为 `JPEGCompressor`。命令行查看：`adb logcat -s JPEGCompressor`；Android Studio Logcat 过滤：`tag:JPEGCompressor`。

## 完整示例

```kotlin
// 同步示例
val result = JPEGCompressor.instance.compressSync(
    inputPath = "/sdcard/DCIM/photo.jpg",
    outputPath = "/sdcard/DCIM/photo_compressed.jpg",
    quality = 80
)

// 异步示例
JPEGCompressor.instance.compressAsync(
    inputPath = inputPath,
    outputPath = outputPath,
    quality = 80,
    callback = object : JPEGCompressor.CompressCallback {
        override fun onSuccess(result: JPEGCompressor.CompressResult) {
            Log.d("Compress", "成功: ${result.compressionRatio}% 压缩率")
        }
        override fun onError(error: Throwable) {
            Log.e("Compress", "失败", error)
        }
    }
)

// RxJava3 示例
JPEGCompressor.instance.compressRx(inputPath, outputPath, 80)
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
        { result -> updateUI(result) },
        { it.printStackTrace() }
    )
```

---

## CompressResult 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| success | Boolean | 是否成功（含 fallback 成功） |
| inputPath | String | 输入路径（Bitmap 输入时为 "bitmap:宽x高"） |
| outputPath | String | 输出路径 |
| inputSize | Long | 原始大小（字节） |
| outputSize | Long | 压缩后大小（字节） |
| compressionRatio | Float | 压缩率（%），再次压缩已压缩图可能为负数表示体积增大 |
| inputWidth/Height | Int | 原始尺寸 |
| outputWidth/Height | Int | 压缩后尺寸 |
| fallbackUsed | Boolean | 是否使用了 fallback（压缩失败时复制原图） |
| rotationApplied | Int | 若启用自动旋转且 EXIF 有旋转信息，则为实际应用的角度（90/180/270），否则为 0 |
| errorMessage | String? | 失败时错误信息 |

---

## 版本要求

| 项目 | 要求 | 说明 |
|------|------|------|
| **Android minSdk** | 21 | 与主工程保持一致即可 |
| **Android compileSdk** | 36 | 本库当前配置，主工程 ≥ 即可 |
| **Kotlin** | 1.9+ | 与 AGP 兼容的 Kotlin 版本 |
| **JDK** | 17 | 本库 `compileOptions` 为 17 |
| **Gradle** | 8.x / 9.x | 与 AGP 9.x 兼容 |
| **Android Gradle Plugin** | 9.x | 见根项目 `libs.versions.toml` 的 `agp` |
| **NDK** | 21+（推荐 29.x） | 编译 native 所需，本库 `ndkVersion` 为 29.0.x |

- 若以 **Module 依赖**方式集成，主工程需满足上述 Gradle / AGP / JDK 要求。
- 若仅使用 **AAR**，仅需主工程 minSdk ≥ 21 及 Kotlin 运行时即可。

---

## License

本库采用 **Apache License 2.0**。详见项目根目录 [LICENSE](../LICENSE)。
