# JPEG Compressor Library

基于 [libjpeg-turbo](https://github.com/libjpeg-turbo/libjpeg-turbo) 的 Android 图片压缩库，支持 JPEG 无损/有损压缩。

---

## 编译命令

### 编译 jpeg-compressor 库（Release AAR）

```bash
./gradlew :jpeg-compressor:assembleRelease
```

### 编译整个项目（含 app）

```bash
./gradlew assembleRelease
```

### 编译 Debug 版本（调试用）

```bash
./gradlew assembleDebug
```

### Windows（PowerShell / CMD）

```powershell
.\gradlew.bat :jpeg-compressor:assembleRelease
```

### 不使用 Gradle Daemon

```bash
./gradlew assembleRelease --no-daemon
```

### 输出文件位置

- **jpeg-compressor AAR**：`jpeg-compressor/build/outputs/aar/jpeg-compressor-release.aar`（或带版本号如 `jpeg-compressor-1.0.0.aar`）

### 说明

- 使用 `implementation(project(":jpeg-compressor"))` 时，**每次运行 app 都会从源码重新编译 jpeg-compressor**，不会使用预构建的 AAR。修改 jpeg-compressor 源码后，直接运行 app 即可生效，无需单独执行 `assembleRelease`。
- 预构建的 AAR 仅在使用 `implementation(files("libs/xxx.aar"))` 等方式引用时才会被使用。
- 需配置 Android SDK（`ANDROID_HOME` 环境变量或 `local.properties` 中的 `sdk.dir`）。

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
include(":app")
include(":jpeg-compressor")
```

在 app 的 `build.gradle.kts` 中：

```kotlin
dependencies {
    implementation(project(":jpeg-compressor"))
}
```

### 方式二：使用 AAR

1. **生成 AAR**：
   ```bash
   ./gradlew :jpeg-compressor:assembleRelease
   ```

2. **AAR 输出路径**：
   ```
   jpeg-compressor/build/outputs/aar/jpeg-compressor-release.aar
   ```

3. **集成到项目**：
   - 将 AAR 复制到目标项目的 `app/libs/` 目录
   - 在 `build.gradle.kts` 中：
   ```kotlin
   dependencies {
       implementation(files("libs/jpeg-compressor-release.aar"))
       // 或使用 flatDir
       implementation(name: "jpeg-compressor-release", ext: "aar")
   }
   ```

4. **依赖说明**：AAR 已包含 RxJava3，若主项目已有 RxJava3 可排除传递依赖：
   ```kotlin
   implementation("com.xxx:jpeg-compressor:1.0.0") {
       exclude(group = "io.reactivex.rxjava3", module = "rxjava")
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
// info.width, info.height, info.fileSize, info.path（fileSize 由 libjpeg-turbo 读头得到）
```

### 6. 图片选择（需配合 registerForActivityResult）

**重要**：`registerForActivityResult` 必须在 Fragment/Activity **创建前**调用（init 块或 onCreate 中），不能放在点击事件里。

```kotlin
// 在 Fragment 中
class MyFragment : Fragment() {

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val path = ImagePicker.instance.getFilePathFromUri(requireActivity(), uri)
                // 处理 path
            }
        }
    }

    // 点击时启动
    binding.btnSelect.setOnClickListener {
        pickImageLauncher.launch(ImagePicker.instance.createPickImageIntent())
    }
}
```

### 7. 系统相册预览（需 FileProvider）

```kotlin
// 传入应用 FileProvider 的 authority（与 AndroidManifest 中一致）
ImagePicker.instance.openImageInGallery(
    context,
    "/path/to/image.jpg",
    "${context.packageName}.fileprovider"
)
```

---

## 常见问题与说明

### 1. 为什么“再次压缩”后压缩率会变成负数？体积是变大了吗？

**是的，负压缩率表示输出文件比输入更大。**

- **原因**：第一次压缩时，原图已被量化并做了 Huffman 熵编码，文件已经较优。再次压缩时流程是：**解码 JPEG → 得到 RGB → 再按新 quality 编码**。第二次编码面对的是“已损失过”的像素，量化+熵编码效率通常不如第一次，所以**体积常会变大**，压缩率就会是负数。
- **结论**：对**已经压缩过的照片**再压一遍，往往既损画质又增体积，不推荐。建议只对**原图**做一次压缩；若必须二次压缩，可**降低 quality**（如 70、60）强制缩小体积，但画质会进一步下降。
- **界面**：当压缩率为负时，示例 App 会显示为「体积增大: X%」，并提示「再次压缩已压缩的图片常会变大」。

### 2. 压缩效果还能再优化吗？optimize_coding、SIMD、TurboJPEG 等

**当前实现：**

- 已使用 **TurboJPEG API**（`tjCompress2` / `tjDecompress2`）。
- 已使用 **TJSAMP_420** 色度子采样（在观感可接受下明显减小体积）。
- 已使用 **TJFLAG_ACCURATEDCT**（更精确的 DCT，画质与体积平衡较好）。

**可进一步考虑的方向：**

| 方向 | 说明 |
|------|------|
| **optimize_coding** | 可优化 Huffman 表，同画质下略减小体积。需使用 libjpeg **标准 API**（如 `jpeg_set_quality` + `optimize_coding`），**TurboJPEG 未暴露该选项**，若要使用需改为基于 libjpeg 的压缩流程。 |
| **SIMD** | 用于**加速**编解码，不直接减小体积。当前构建中 SIMD 已关闭（`jconfig.h` 未定义 `WITH_SIMD`）。若在 CMake 中为各 ABI 加入对应 simd 源文件并开启 SIMD，可显著提升压缩/解压速度。 |
| **质量/子采样** | 在现有 TurboJPEG 上，可尝试略降 quality（如 80）或保持 420，在体积与画质间做权衡。 |

总结：在**不改为 libjpeg 标准 API** 的前提下，当前 TurboJPEG + 420 + ACCURATEDCT 已是较优组合；要进一步减体积可考虑接入 optimize_coding（需改实现），要提速可开启 SIMD。

### 3. 是否支持超大图？最大支持多大？会不会 OOM？

- **支持上限**：单边像素不超过 **8192**（宽或高任一超过即拒绝），且总像素数不超过 8192×8192。与 C++ 中 `MAX_DIMENSION` 一致，Kotlin 可通过 `JPEGCompressor.MAX_DIMENSION` 获取。
- **OOM 风险**：压缩时会分配 **width×height×3** 的 RGB 缓冲区（约 8192×8192×3 ≈ 192MB）。若放宽上限，大图在低内存设备上易 OOM，因此超过上述尺寸会**直接拒绝压缩**并返回错误。
- **错误码**：尺寸过大时 native 返回 **-2**，Kotlin 层会得到 `success == false` 且 `errorMessage` 为「图片尺寸过大，存在 OOM 风险（单边不超过 8192 像素）」；常量 `JPEGCompressor.ERROR_IMAGE_TOO_LARGE == -2`。
- **建议**：单边尽量不超过 8192；若需处理更大图，需自行先缩放或分块处理后再压缩。

---

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
| success | Boolean | 是否成功 |
| inputPath | String | 输入路径 |
| outputPath | String | 输出路径 |
| inputSize | Long | 原始大小（字节） |
| outputSize | Long | 压缩后大小（字节） |
| compressionRatio | Float | 压缩率（%），再次压缩已压缩图可能为负数表示体积增大 |
| inputWidth/Height | Int | 原始尺寸 |
| outputWidth/Height | Int | 压缩后尺寸 |
| errorMessage | String? | 失败时错误信息 |

---

## 版本要求

- **minSdk**：库本身可支持 **21**（NDK、Context、File、BitmapFactory 等均可用）；与主工程 `minSdk` 一致即可（如 26）。
- Kotlin 1.9+
- NDK 21+