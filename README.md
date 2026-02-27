[English](README.en.md) | 中文

# JPEG Compressor

基于 [libjpeg-turbo](https://github.com/libjpeg-turbo/libjpeg-turbo) 的 Android JPEG 图片压缩库，支持同步/异步/RxJava3、路径/Bitmap/Uri 输入、缩放裁剪与 EXIF 自动旋转。

---

## 引用方式

### 1. Maven Central（推荐）

在工程根目录 `settings.gradle.kts` 的 `dependencyResolutionManagement.repositories` 中确保包含 `mavenCentral()`，然后在 app 的 `build.gradle.kts` 中：

```kotlin
dependencies {
    implementation("io.github.iuuaa:jpeg-compressor:1.0.5")
    // 若使用异步 / Rx / EXIF 自动旋转，需同时添加：
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
}
```

### 2. 本地 Module 依赖

在工程根目录 `settings.gradle.kts` 中包含：

```kotlin
include(":jpeg-compressor")
```

在 app 的 `build.gradle.kts` 中：

```kotlin
dependencies {
    implementation(project(":jpeg-compressor"))
}
```

---

## 快速开始

```kotlin
val compressor = JPEGCompressor.instance

// 同步压缩
val result = compressor.compressSync(
    inputPath = "/path/to/input.jpg",
    outputPath = "/path/to/output.jpg",
    quality = 85  // 1–100
)
if (result.success) {
    println("压缩率: ${result.compressionRatio}%")
} else {
    println("失败: ${result.errorMessage}")
}
```

---

## 核心 API 概览

| API | 说明 |
|-----|------|
| `JPEGCompressor.instance` | 单例入口 |
| `compressSync(path, path, quality, …)` | 同步压缩（路径） |
| `compressSync(bitmap, path, quality, …)` | 同步压缩（Bitmap） |
| `compressSync(context, uri, path, quality, …)` | 同步压缩（Uri） |
| `compressAsync(…, callback)` | 异步压缩，主线程回调 |
| `compressRx(…)` | RxJava3 `Single<CompressResult>` |
| `getImageInfo(path)` / `getImageInfo(bitmap)` / `getImageInfo(context, uri)` | 获取宽高、文件大小等 |
| `calculateCropRegionForAspectRatio(w, h, aspectW, aspectH)` | 按比例居中裁剪区域 |
| `alignCropRegionToMCU(crop, …)` | 将裁剪区域对齐到 MCU |
| `getExifRotation(path)` | 读取 EXIF 旋转角度 |

常用参数（各 `compress*` 方法均有）：`quality`、`targetWidth`/`targetHeight`、`scale`、`cropX/Y/W/H`、`autoRotate`、`fallbackToOriginalOnError`。  
返回结果见 `JPEGCompressor.CompressResult`（含 `success`、`compressionRatio`、`fallbackUsed`、`errorMessage` 等）。

---

## 日志查看

压缩过程会输出耗时、内存和参数等日志，便于调试。在 Logcat 中按 tag 过滤可只看库的日志：

- **Tag**：`JPEGCompressor`
- **命令行**：`adb logcat -s JPEGCompressor`
- **Android Studio Logcat**：在过滤框输入 `tag:JPEGCompressor` 或选择 tag 为 `JPEGCompressor`

---

## 文档与示例

- **库模块详细说明**（编译命令、集成方式、完整示例、常见问题、CompressResult 字段说明、版本要求）：见 [jpeg-compressor/README.md](jpeg-compressor/README.md)。
- **集成到已有 App**：见 [jpeg-compressor/INTEGRATION_GUIDE.md](jpeg-compressor/INTEGRATION_GUIDE.md)（若存在）。
- 本仓库中的 **app** 为示例应用，可直接运行查看压缩、选择图片、保存相册等用法。
- **libjpeg-turbo 修改与覆盖步骤**：见 [jpeg-compressor/THIRD_PARTY.md](jpeg-compressor/THIRD_PARTY.md)，其中说明了如何将 `libjpeg-turbo-patches` 目录下的文件覆盖到上游 libjpeg-turbo 源码。

---

## 版本要求

- **minSdk**：21
- **Kotlin**：1.9+
- **NDK**：21+

---

## License

本项目采用 **Apache License 2.0** 开源协议。  
详见 [LICENSE](LICENSE) 文件。您在使用、复制、修改及分发本软件时须遵守该协议。
