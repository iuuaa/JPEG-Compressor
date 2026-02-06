package com.iuuaa.jpegcompressor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.File
import java.io.FileOutputStream

private const val LOG_TAG = "JPEGCompressor"

/**
 * JPEG 图片压缩器（基于 libjpeg-turbo / TurboJPEG API）
 *
 * 支持同步、异步回调和 RxJava3 三种调用方式，支持路径、Bitmap、Uri 三种输入类型。
 *
 * ## 功能特性
 * - **压缩**：使用 TurboJPEG API 高效压缩 JPEG
 * - **缩放**：支持按比例缩放、按目标宽高缩放、按长边限制缩放
 * - **裁剪**：支持按比例裁剪（在解码时进行，效率高）
 * - **裁剪+缩放**：支持先裁剪再缩放（如 1:1 裁剪后限制长边 1920）
 * - **容错**：支持压缩失败时返回原图（fallbackToOriginalOnError）
 *
 * ## 再次压缩已压缩过的 JPEG
 * 对已压缩过的照片再次压缩时，压缩率可能为**负数**（体积反而增大）。原因：首次压缩已做
 * 量化与熵编码，再次解码为 RGB 后重编码会二次量化，熵编码效率通常更差，故体积可能增加。
 * 建议对"原图"做一次压缩；若必须二次压缩，可适当降低 quality 以强制缩小体积（画质会进一步下降）。
 *
 * ## 超大图与 OOM
 * 压缩过程会分配 width×height×3 的 RGB 缓冲区，单边超过 [MAX_DIMENSION]（默认 8192）
 * 或总像素数超过 [MAX_PIXELS] 会拒绝压缩并返回 [ERROR_IMAGE_TOO_LARGE]。
 * 建议单边不超过 8192，否则存在 OOM 风险。
 *
 * ## 压缩优化说明
 * 当前使用：TurboJPEG API、TJSAMP_420 色度子采样、TJFLAG_FASTDCT（速度优先）。
 * optimize_coding 需使用 libjpeg 标准 API，TurboJPEG 未暴露；SIMD 可在构建时启用以提升速度。
 */
class JPEGCompressor private constructor() {

    companion object {
        /**
         * Native 压缩方法（基础版，不缩放不裁剪）
         */
        @JvmStatic
        private external fun nativeCompress(
            inputPath: String,
            outputPath: String,
            quality: Int,
        ): Int

        /**
         * Native 压缩方法（扩展版，支持缩放、裁剪、容错）
         *
         * @param inputPath 输入路径
         * @param outputPath 输出路径
         * @param quality 压缩质量 1-100
         * @param targetWidth 目标宽度（0 表示不限）
         * @param targetHeight 目标高度（0 表示不限）
         * @param scale 缩放比例（0 表示不按比例缩放）
         * @param cropX 裁剪区域左上角 X
         * @param cropY 裁剪区域左上角 Y
         * @param cropW 裁剪区域宽度
         * @param cropH 裁剪区域高度
         * @param fallbackToOriginal 压缩失败时是否复制原图
         * @return 0 成功，1 fallback 成功，-1 失败，-2 图片过大
         */
        @JvmStatic
        private external fun nativeCompressEx(
            inputPath: String,
            outputPath: String,
            quality: Int,
            targetWidth: Int,
            targetHeight: Int,
            scale: Float,
            cropX: Int,
            cropY: Int,
            cropW: Int,
            cropH: Int,
            fallbackToOriginal: Boolean,
        ): Int

        /**
         * Native 获取图片信息方法
         *
         * @param imagePath 图片路径
         * @return LongArray [width, height, fileSize]，失败返回 null
         */
        @JvmStatic
        private external fun nativeGetImageInfo(imagePath: String): LongArray?

        init {
            System.loadLibrary("jpegcompressor")
        }

        /** 单例实例 */
        @JvmStatic
        val instance: JPEGCompressor by lazy { JPEGCompressor() }

        /** Native 层单边最大像素，超过会返回错误码 -2 以避免 OOM */
        const val MAX_DIMENSION = 8192

        /** Native 层最大总像素数（约 67MP），超过会返回错误码 -2 */
        const val MAX_PIXELS = MAX_DIMENSION.toLong() * MAX_DIMENSION

        /** 错误码：图片尺寸过大，存在 OOM 风险 */
        const val ERROR_IMAGE_TOO_LARGE = -2

        /** 返回码：压缩成功 */
        const val RESULT_SUCCESS = 0

        /** 返回码：使用了 fallback（复制原图） */
        const val RESULT_FALLBACK = 1
    }

    // ==================== 数据类与接口 ====================

    /**
     * 压缩结果数据类
     *
     * @property success 是否成功（包括 fallback 成功）
     * @property inputPath 输入路径（Bitmap 输入时为 "bitmap:宽x高"）
     * @property outputPath 输出路径
     * @property inputSize 输入文件大小（字节）
     * @property outputSize 输出文件大小（字节）
     * @property inputWidth 输入图片宽度
     * @property inputHeight 输入图片高度
     * @property outputWidth 输出图片宽度
     * @property outputHeight 输出图片高度
     * @property compressionRatio 压缩率（%），负数表示体积增大
     * @property fallbackUsed 是否使用了 fallback（复制原图）
     * @property errorMessage 错误信息（失败时）
     */
    data class CompressResult(
        val success: Boolean,
        val inputPath: String,
        val outputPath: String,
        val inputSize: Long,
        val outputSize: Long,
        val inputWidth: Int,
        val inputHeight: Int,
        val outputWidth: Int,
        val outputHeight: Int,
        val compressionRatio: Float,
        val fallbackUsed: Boolean = false,
        val errorMessage: String? = null,
    )

    /**
     * 图片信息数据类
     *
     * @property width 图片宽度（像素）
     * @property height 图片高度（像素）
     * @property fileSize 文件大小（字节），Bitmap 输入时为 0
     * @property path 文件路径，Bitmap/Uri 输入时可能为空
     */
    data class ImageInfo(
        val width: Int,
        val height: Int,
        val fileSize: Long,
        val path: String,
    )

    /**
     * 裁剪区域数据类
     *
     * @property x 裁剪区域左上角 X 坐标
     * @property y 裁剪区域左上角 Y 坐标
     * @property w 裁剪区域宽度，0 表示无需裁剪
     * @property h 裁剪区域高度，0 表示无需裁剪
     */
    data class CropRegion(val x: Int, val y: Int, val w: Int, val h: Int)

    /**
     * 压缩回调接口
     */
    interface CompressCallback {
        /** 压缩成功回调（主线程） */
        fun onSuccess(result: CompressResult)

        /** 压缩失败回调（主线程） */
        fun onError(error: Throwable)
    }

    // ==================== 路径输入的压缩方法 ====================

    /**
     * 同步压缩图片（路径输入），支持缩放、裁剪和容错。
     *
     * ## 用法示例
     * ```kotlin
     * // 1. 基础压缩
     * val result = compressor.compressSync("/sdcard/photo.jpg", "/sdcard/out.jpg", quality = 80)
     *
     * // 2. 缩放 50%
     * compressor.compressSync(inputPath, outputPath, quality = 85, scale = 0.5f)
     *
     * // 3. 限制长边 1920（保持比例）
     * val info = compressor.getImageInfo(inputPath)
     * val (tw, th) = if (info.width >= info.height) {
     *     1920 to (1920 * info.height / info.width)
     * } else {
     *     (1920 * info.width / info.height) to 1920
     * }
     * compressor.compressSync(inputPath, outputPath, quality = 85, targetWidth = tw, targetHeight = th)
     *
     * // 4. 按 1:1 比例裁剪（居中）
     * val crop = compressor.calculateCropRegionForAspectRatio(info.width, info.height, 1, 1)
     * compressor.compressSync(inputPath, outputPath, quality = 85,
     *     cropX = crop.x, cropY = crop.y, cropW = crop.w, cropH = crop.h)
     *
     * // 5. 裁剪 + 限制长边（1:1 裁剪后限制长边 1920）
     * val crop = compressor.calculateCropRegionForAspectRatio(info.width, info.height, 1, 1)
     * compressor.compressSync(inputPath, outputPath, quality = 85,
     *     targetWidth = 1920, targetHeight = 1920,
     *     cropX = crop.x, cropY = crop.y, cropW = crop.w, cropH = crop.h)
     *
     * // 6. 启用容错（压缩失败时返回原图）
     * compressor.compressSync(inputPath, outputPath, fallbackToOriginalOnError = true)
     * if (result.fallbackUsed) println("使用了原图")
     * ```
     *
     * ## 注意事项
     * - 裁剪在解码时进行（使用 TurboJPEG tj3SetCroppingRegion），效率高于压缩后裁剪
     * - 裁剪区域必须完全在图片范围内，否则返回失败
     * - scale 非 0 时优先于 targetWidth/targetHeight
     * - 输出尺寸会取 TurboJPEG 支持的缩放因子下不超过目标的最大尺寸（仅非裁剪时）
     * - 裁剪+缩放时，缩放基于裁剪后的尺寸计算
     * - 压缩过程会记录耗时和内存使用到 Log（tag: JPEGCompressor）
     *
     * @param inputPath 输入图片路径（必须存在且为有效 JPEG）
     * @param outputPath 输出图片路径
     * @param quality 压缩质量 (1-100)，默认 85，值越小文件越小但画质越差
     * @param targetWidth 目标宽度（像素），0 表示不按宽度限制
     * @param targetHeight 目标高度（像素），0 表示不按高度限制
     * @param scale 缩放比例 (0 < scale <= 1)，非 0 时优先于 targetWidth/targetHeight
     * @param cropX 裁剪区域左上角 X 坐标（相对于原图），0 表示不裁剪
     * @param cropY 裁剪区域左上角 Y 坐标（相对于原图）
     * @param cropW 裁剪区域宽度，0 表示不裁剪
     * @param cropH 裁剪区域高度，0 表示不裁剪
     * @param fallbackToOriginalOnError 压缩失败时是否复制原图到输出路径，默认 false
     * @return CompressResult 压缩结果，通过 success 判断是否成功，fallbackUsed 判断是否使用了原图
     */
    @JvmOverloads
    fun compressSync(
        inputPath: String,
        outputPath: String,
        quality: Int = 85,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        scale: Float = 0f,
        cropX: Int = 0,
        cropY: Int = 0,
        cropW: Int = 0,
        cropH: Int = 0,
        fallbackToOriginalOnError: Boolean = false,
    ): CompressResult {
        return try {
            val inputFile = File(inputPath)
            if (!inputFile.exists()) {
                return CompressResult(
                    success = false,
                    inputPath = inputPath,
                    outputPath = outputPath,
                    inputSize = 0,
                    outputSize = 0,
                    inputWidth = 0,
                    inputHeight = 0,
                    outputWidth = 0,
                    outputHeight = 0,
                    compressionRatio = 0f,
                    fallbackUsed = false,
                    errorMessage = "输入文件不存在"
                )
            }

            val inputInfo = getImageInfo(inputPath)
            val inputSize = inputFile.length()

            val runtime = Runtime.getRuntime()
            val memBeforeKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024
            val startNanos = System.nanoTime()

            val nativeResult = if (targetWidth != 0 || targetHeight != 0 || scale > 0f
                || cropW > 0 || cropH > 0 || fallbackToOriginalOnError
            ) {
                nativeCompressEx(
                    inputPath, outputPath, quality,
                    targetWidth, targetHeight, scale,
                    cropX, cropY, cropW, cropH,
                    fallbackToOriginalOnError
                )
            } else {
                nativeCompress(inputPath, outputPath, quality)
            }

            val durationMs = (System.nanoTime() - startNanos) / 1_000_000
            val memAfterKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024
            Log.i(
                LOG_TAG,
                "compress finished: durationMs=$durationMs, memoryBefore=${memBeforeKb}KB, " +
                        "memoryAfter=${memAfterKb}KB, memoryDelta=${memAfterKb - memBeforeKb}KB, " +
                        "nativeResult=$nativeResult"
            )

            val fallbackUsed = nativeResult == RESULT_FALLBACK

            if (nativeResult != RESULT_SUCCESS && nativeResult != RESULT_FALLBACK) {
                val errorMsg = when (nativeResult) {
                    ERROR_IMAGE_TOO_LARGE ->
                        "图片尺寸过大，存在 OOM 风险（单边不超过 $MAX_DIMENSION 像素）"

                    else ->
                        "压缩失败，错误码: $nativeResult"
                }
                return CompressResult(
                    success = false,
                    inputPath = inputPath,
                    outputPath = outputPath,
                    inputSize = inputSize,
                    outputSize = 0,
                    inputWidth = inputInfo.width,
                    inputHeight = inputInfo.height,
                    outputWidth = 0,
                    outputHeight = 0,
                    compressionRatio = 0f,
                    fallbackUsed = false,
                    errorMessage = errorMsg
                )
            }

            val outputFile = File(outputPath)
            val outputSize = if (outputFile.exists()) outputFile.length() else 0
            // 延迟获取输出信息，仅在成功时调用（性能优化）
            val outputInfo =
                if (outputFile.exists()) getImageInfo(outputPath) else ImageInfo(0, 0, 0, "")

            val compressionRatio = if (inputSize > 0 && outputSize > 0) {
                (1 - outputSize.toFloat() / inputSize.toFloat()) * 100
            } else {
                0f
            }

            CompressResult(
                success = true,
                inputPath = inputPath,
                outputPath = outputPath,
                inputSize = inputSize,
                outputSize = outputSize,
                inputWidth = inputInfo.width,
                inputHeight = inputInfo.height,
                outputWidth = outputInfo.width,
                outputHeight = outputInfo.height,
                compressionRatio = compressionRatio,
                fallbackUsed = fallbackUsed
            )
        } catch (e: Exception) {
            CompressResult(
                success = false,
                inputPath = inputPath,
                outputPath = outputPath,
                inputSize = 0,
                outputSize = 0,
                inputWidth = 0,
                inputHeight = 0,
                outputWidth = 0,
                outputHeight = 0,
                compressionRatio = 0f,
                fallbackUsed = false,
                errorMessage = e.message
            )
        }
    }

    /**
     * 异步压缩图片（路径输入），回调在主线程执行。
     *
     * ## 用法示例
     * ```kotlin
     * val disposable = compressor.compressAsync(inputPath, outputPath, quality = 80,
     *     callback = object : JPEGCompressor.CompressCallback {
     *         override fun onSuccess(result: JPEGCompressor.CompressResult) {
     *             // 主线程回调，可直接更新 UI
     *             if (result.fallbackUsed) showToast("使用了原图")
     *         }
     *         override fun onError(error: Throwable) {
     *             showToast("压缩失败: ${error.message}")
     *         }
     *     }
     * )
     * // Fragment/Activity 销毁时取消
     * compositeDisposable.add(disposable)
     * ```
     *
     * ## 注意事项
     * - 回调在主线程执行，可直接更新 UI
     * - 返回的 Disposable 应在 Fragment/Activity 销毁时 dispose 以避免内存泄漏
     * - 其他参数说明同 [compressSync]
     *
     * @param inputPath 输入图片路径
     * @param outputPath 输出图片路径
     * @param quality 压缩质量 (1-100)，默认 85
     * @param targetWidth 目标宽度，0 表示不限
     * @param targetHeight 目标高度，0 表示不限
     * @param scale 缩放比例 (0 < scale <= 1)
     * @param cropX 裁剪区域左上角 X
     * @param cropY 裁剪区域左上角 Y
     * @param cropW 裁剪区域宽度
     * @param cropH 裁剪区域高度
     * @param fallbackToOriginalOnError 压缩失败时是否使用原图
     * @param callback 压缩回调
     * @return Disposable 可用于取消任务
     */
    @JvmOverloads
    fun compressAsync(
        inputPath: String,
        outputPath: String,
        quality: Int = 85,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        scale: Float = 0f,
        cropX: Int = 0,
        cropY: Int = 0,
        cropW: Int = 0,
        cropH: Int = 0,
        fallbackToOriginalOnError: Boolean = false,
        callback: CompressCallback,
    ): Disposable {
        return Single.fromCallable {
            compressSync(
                inputPath, outputPath, quality,
                targetWidth, targetHeight, scale,
                cropX, cropY, cropW, cropH,
                fallbackToOriginalOnError
            )
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    if (result.success) {
                        callback.onSuccess(result)
                    } else {
                        callback.onError(Exception(result.errorMessage ?: "压缩失败"))
                    }
                },
                { error ->
                    callback.onError(error)
                }
            )
    }

    /**
     * RxJava3 方式压缩图片（路径输入）。
     *
     * ## 用法示例
     * ```kotlin
     * compressor.compressRx(inputPath, outputPath, quality = 80)
     *     .observeOn(AndroidSchedulers.mainThread())
     *     .subscribe(
     *         { result ->
     *             if (result.success) updateUI(result)
     *             else showError(result.errorMessage)
     *         },
     *         { error -> showError(error.message) }
     *     )
     * ```
     *
     * ## 注意事项
     * - 默认在 IO 线程执行，需手动切换到主线程观察结果
     * - 其他参数说明同 [compressSync]
     *
     * @return Single<CompressResult> 可订阅的压缩结果
     */
    @JvmOverloads
    fun compressRx(
        inputPath: String,
        outputPath: String,
        quality: Int = 85,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        scale: Float = 0f,
        cropX: Int = 0,
        cropY: Int = 0,
        cropW: Int = 0,
        cropH: Int = 0,
        fallbackToOriginalOnError: Boolean = false,
    ): Single<CompressResult> {
        return Single.fromCallable {
            compressSync(
                inputPath, outputPath, quality,
                targetWidth, targetHeight, scale,
                cropX, cropY, cropW, cropH,
                fallbackToOriginalOnError
            )
        }.subscribeOn(Schedulers.io())
    }

    // ==================== 获取图片信息 ====================

    /**
     * 获取图片信息（路径输入）。
     *
     * ## 用法示例
     * ```kotlin
     * val info = compressor.getImageInfo("/sdcard/photo.jpg")
     * println("尺寸: ${info.width}x${info.height}, 大小: ${info.fileSize} 字节")
     * ```
     *
     * ## 注意事项
     * - 优先使用 libjpeg-turbo 读取 JPEG 头（高效），失败时回退到 BitmapFactory
     * - fileSize 来自文件系统，尺寸来自 JPEG 头或 BitmapFactory
     *
     * @param imagePath 图片文件路径
     * @return ImageInfo 图片信息，失败时 width/height 为 0
     */
    fun getImageInfo(imagePath: String): ImageInfo {
        return try {
            val file = File(imagePath)
            val fileSize = if (file.exists()) file.length() else 0

            val infoArray = nativeGetImageInfo(imagePath)
            if (infoArray != null && infoArray.size >= 3) {
                ImageInfo(
                    width = infoArray[0].toInt(),
                    height = infoArray[1].toInt(),
                    fileSize = infoArray[2].coerceAtLeast(0),
                    path = imagePath
                )
            } else {
                // Native 失败，回退到 BitmapFactory
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(imagePath, options)
                ImageInfo(
                    width = options.outWidth,
                    height = options.outHeight,
                    fileSize = fileSize,
                    path = imagePath
                )
            }
        } catch (_: Exception) {
            ImageInfo(0, 0, 0, imagePath)
        }
    }

    /**
     * 获取图片信息（Bitmap 输入）。
     *
     * ## 用法示例
     * ```kotlin
     * val bitmap = BitmapFactory.decodeResource(resources, R.drawable.image)
     * val info = compressor.getImageInfo(bitmap)
     * println("尺寸: ${info.width}x${info.height}")
     * ```
     *
     * ## 注意事项
     * - fileSize 返回 0（Bitmap 无文件大小概念）
     * - path 返回空字符串
     *
     * @param bitmap Bitmap 对象
     * @return ImageInfo 图片信息
     */
    fun getImageInfo(bitmap: Bitmap): ImageInfo {
        return ImageInfo(
            width = bitmap.width,
            height = bitmap.height,
            fileSize = 0L,
            path = ""
        )
    }

    /**
     * 获取图片信息（Uri 输入）。
     *
     * ## 用法示例
     * ```kotlin
     * val uri = Uri.parse("content://media/external/images/media/123")
     * val info = compressor.getImageInfo(context, uri)
     * ```
     *
     * ## 注意事项
     * - 需要 Context 打开 ContentResolver 流
     * - 会复制到临时文件后读取，读取后自动删除临时文件
     * - 失败时返回 ImageInfo(0, 0, 0, "")
     *
     * @param context Android Context
     * @param uri 图片 Uri
     * @return ImageInfo 图片信息
     */
    fun getImageInfo(context: Context, uri: Uri): ImageInfo {
        val tempPath = copyUriToTempFile(context, uri, context.cacheDir)
            ?: return ImageInfo(0, 0, 0, "")
        return try {
            getImageInfo(tempPath)
        } finally {
            try {
                File(tempPath).delete()
            } catch (_: Exception) {
            }
        }
    }

    // ==================== Bitmap 输入的压缩方法 ====================

    /**
     * 同步压缩图片（Bitmap 输入）。
     *
     * ## 用法示例
     * ```kotlin
     * val bitmap = BitmapFactory.decodeResource(resources, R.drawable.image)
     * val result = compressor.compressSync(bitmap, "/sdcard/output.jpg", quality = 80)
     * ```
     *
     * ## 注意事项
     * - Bitmap 会先保存为临时 JPEG（质量 100），再调用路径压缩
     * - 临时文件会在压缩后自动删除
     * - CompressResult.inputPath 为 "bitmap:宽x高" 格式
     * - 其他参数说明同 [compressSync]（路径版本）
     *
     * @param inputBitmap 输入 Bitmap 对象
     * @param outputPath 输出图片路径
     * @param quality 压缩质量 (1-100)
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度
     * @param scale 缩放比例
     * @param cropX 裁剪区域左上角 X
     * @param cropY 裁剪区域左上角 Y
     * @param cropW 裁剪区域宽度
     * @param cropH 裁剪区域高度
     * @param fallbackToOriginalOnError 压缩失败时是否使用原图
     * @return CompressResult 压缩结果
     */
    @JvmOverloads
    fun compressSync(
        inputBitmap: Bitmap,
        outputPath: String,
        quality: Int = 85,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        scale: Float = 0f,
        cropX: Int = 0,
        cropY: Int = 0,
        cropW: Int = 0,
        cropH: Int = 0,
        fallbackToOriginalOnError: Boolean = false,
    ): CompressResult {
        val tempFile = File(File(outputPath).parentFile, "tmp_in_${System.currentTimeMillis()}.jpg")
        return try {
            FileOutputStream(tempFile).use { out ->
                if (!inputBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)) {
                    return CompressResult(
                        success = false,
                        inputPath = "",
                        outputPath = outputPath,
                        inputSize = 0,
                        outputSize = 0,
                        inputWidth = inputBitmap.width,
                        inputHeight = inputBitmap.height,
                        outputWidth = 0,
                        outputHeight = 0,
                        compressionRatio = 0f,
                        fallbackUsed = false,
                        errorMessage = "Bitmap 写入临时文件失败"
                    )
                }
            }
            val result = compressSync(
                tempFile.absolutePath, outputPath, quality,
                targetWidth, targetHeight, scale,
                cropX, cropY, cropW, cropH,
                fallbackToOriginalOnError
            )
            result.copy(inputPath = "bitmap:${inputBitmap.width}x${inputBitmap.height}")
        } finally {
            try {
                tempFile.delete()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 异步压缩图片（Bitmap 输入），回调在主线程执行。
     *
     * ## 注意事项
     * - 回调在主线程执行
     * - 返回的 Disposable 应在 Fragment/Activity 销毁时 dispose
     * - 其他参数说明同 [compressSync]（Bitmap 版本）
     *
     * @param inputBitmap 输入 Bitmap 对象
     * @param outputPath 输出图片路径
     * @param quality 压缩质量 (1-100)
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度
     * @param scale 缩放比例
     * @param cropX 裁剪区域左上角 X
     * @param cropY 裁剪区域左上角 Y
     * @param cropW 裁剪区域宽度
     * @param cropH 裁剪区域高度
     * @param fallbackToOriginalOnError 压缩失败时是否使用原图
     * @param callback 压缩回调
     * @return Disposable 可用于取消任务
     */
    @JvmOverloads
    fun compressAsync(
        inputBitmap: Bitmap,
        outputPath: String,
        quality: Int = 85,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        scale: Float = 0f,
        cropX: Int = 0,
        cropY: Int = 0,
        cropW: Int = 0,
        cropH: Int = 0,
        fallbackToOriginalOnError: Boolean = false,
        callback: CompressCallback,
    ): Disposable {
        return Single.fromCallable {
            compressSync(
                inputBitmap, outputPath, quality,
                targetWidth, targetHeight, scale,
                cropX, cropY, cropW, cropH,
                fallbackToOriginalOnError
            )
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { r -> if (r.success) callback.onSuccess(r) else callback.onError(Exception(r.errorMessage)) },
                { e -> callback.onError(e) }
            )
    }

    /**
     * RxJava3 方式压缩图片（Bitmap 输入）。
     *
     * ## 注意事项
     * - 默认在 IO 线程执行，需手动切换到主线程观察结果
     * - 其他参数说明同 [compressSync]（Bitmap 版本）
     *
     * @return Single<CompressResult> 可订阅的压缩结果
     */
    @JvmOverloads
    fun compressRx(
        inputBitmap: Bitmap,
        outputPath: String,
        quality: Int = 85,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        scale: Float = 0f,
        cropX: Int = 0,
        cropY: Int = 0,
        cropW: Int = 0,
        cropH: Int = 0,
        fallbackToOriginalOnError: Boolean = false,
    ): Single<CompressResult> {
        return Single.fromCallable {
            compressSync(
                inputBitmap, outputPath, quality,
                targetWidth, targetHeight, scale,
                cropX, cropY, cropW, cropH,
                fallbackToOriginalOnError
            )
        }.subscribeOn(Schedulers.io())
    }

    // ==================== Uri 输入的压缩方法 ====================

    /**
     * 同步压缩图片（Uri 输入）。
     *
     * ## 用法示例
     * ```kotlin
     * val uri = Uri.parse("content://media/external/images/media/123")
     * val result = compressor.compressSync(context, uri, "/sdcard/output.jpg", quality = 80)
     * ```
     *
     * ## 注意事项
     * - 需要 Context 打开 ContentResolver 流
     * - Uri 会先复制到临时文件，再调用路径压缩
     * - 临时文件会在压缩后自动删除
     * - 其他参数说明同 [compressSync]（路径版本）
     *
     * @param context Android Context
     * @param inputUri 输入图片 Uri
     * @param outputPath 输出图片路径
     * @param quality 压缩质量 (1-100)
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度
     * @param scale 缩放比例
     * @param cropX 裁剪区域左上角 X
     * @param cropY 裁剪区域左上角 Y
     * @param cropW 裁剪区域宽度
     * @param cropH 裁剪区域高度
     * @param fallbackToOriginalOnError 压缩失败时是否使用原图
     * @return CompressResult 压缩结果
     */
    @JvmOverloads
    fun compressSync(
        context: Context,
        inputUri: Uri,
        outputPath: String,
        quality: Int = 85,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        scale: Float = 0f,
        cropX: Int = 0,
        cropY: Int = 0,
        cropW: Int = 0,
        cropH: Int = 0,
        fallbackToOriginalOnError: Boolean = false,
    ): CompressResult {
        val tempPath =
            copyUriToTempFile(context, inputUri, File(outputPath).parentFile ?: context.cacheDir)
                ?: return CompressResult(
                    success = false,
                    inputPath = "",
                    outputPath = outputPath,
                    inputSize = 0,
                    outputSize = 0,
                    inputWidth = 0,
                    inputHeight = 0,
                    outputWidth = 0,
                    outputHeight = 0,
                    compressionRatio = 0f,
                    fallbackUsed = false,
                    errorMessage = "无法从 Uri 读取到临时文件"
                )
        return try {
            compressSync(
                tempPath, outputPath, quality,
                targetWidth, targetHeight, scale,
                cropX, cropY, cropW, cropH,
                fallbackToOriginalOnError
            )
        } finally {
            try {
                File(tempPath).delete()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 异步压缩图片（Uri 输入），回调在主线程执行。
     *
     * ## 注意事项
     * - 回调在主线程执行
     * - 返回的 Disposable 应在 Fragment/Activity 销毁时 dispose
     * - 其他参数说明同 [compressSync]（Uri 版本）
     *
     * @param context Android Context
     * @param inputUri 输入图片 Uri
     * @param outputPath 输出图片路径
     * @param quality 压缩质量 (1-100)
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度
     * @param scale 缩放比例
     * @param cropX 裁剪区域左上角 X
     * @param cropY 裁剪区域左上角 Y
     * @param cropW 裁剪区域宽度
     * @param cropH 裁剪区域高度
     * @param fallbackToOriginalOnError 压缩失败时是否使用原图
     * @param callback 压缩回调
     * @return Disposable 可用于取消任务
     */
    @JvmOverloads
    fun compressAsync(
        context: Context,
        inputUri: Uri,
        outputPath: String,
        quality: Int = 85,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        scale: Float = 0f,
        cropX: Int = 0,
        cropY: Int = 0,
        cropW: Int = 0,
        cropH: Int = 0,
        fallbackToOriginalOnError: Boolean = false,
        callback: CompressCallback,
    ): Disposable {
        return Single.fromCallable {
            compressSync(
                context, inputUri, outputPath, quality,
                targetWidth, targetHeight, scale,
                cropX, cropY, cropW, cropH,
                fallbackToOriginalOnError
            )
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { r -> if (r.success) callback.onSuccess(r) else callback.onError(Exception(r.errorMessage)) },
                { e -> callback.onError(e) }
            )
    }

    /**
     * RxJava3 方式压缩图片（Uri 输入）。
     *
     * ## 注意事项
     * - 默认在 IO 线程执行，需手动切换到主线程观察结果
     * - 其他参数说明同 [compressSync]（Uri 版本）
     *
     * @return Single<CompressResult> 可订阅的压缩结果
     */
    @JvmOverloads
    fun compressRx(
        context: Context,
        inputUri: Uri,
        outputPath: String,
        quality: Int = 85,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        scale: Float = 0f,
        cropX: Int = 0,
        cropY: Int = 0,
        cropW: Int = 0,
        cropH: Int = 0,
        fallbackToOriginalOnError: Boolean = false,
    ): Single<CompressResult> {
        return Single.fromCallable {
            compressSync(
                context, inputUri, outputPath, quality,
                targetWidth, targetHeight, scale,
                cropX, cropY, cropW, cropH,
                fallbackToOriginalOnError
            )
        }.subscribeOn(Schedulers.io())
    }

    // ==================== 裁剪区域计算 ====================

    /**
     * 计算按指定宽高比裁剪的区域（居中裁剪）。
     *
     * ## 用法示例
     * ```kotlin
     * val info = compressor.getImageInfo(imagePath)
     *
     * // 1:1 正方形裁剪
     * val crop1x1 = compressor.calculateCropRegionForAspectRatio(info.width, info.height, 1, 1)
     * compressor.compressSync(imagePath, outputPath,
     *     cropX = crop1x1.x, cropY = crop1x1.y, cropW = crop1x1.w, cropH = crop1x1.h)
     *
     * // 3:4 竖图裁剪
     * val crop3x4 = compressor.calculateCropRegionForAspectRatio(info.width, info.height, 3, 4)
     *
     * // 16:9 横图裁剪
     * val crop16x9 = compressor.calculateCropRegionForAspectRatio(info.width, info.height, 16, 9)
     * ```
     *
     * ## 注意事项
     * - 返回的区域会居中裁剪，保证输出比例为 aspectW:aspectH
     * - 如果原图比例已等于目标比例，返回 CropRegion(0, 0, 0, 0) 表示无需裁剪
     * - 裁剪区域完全在图片范围内
     *
     * @param imageWidth 原图宽度（像素）
     * @param imageHeight 原图高度（像素）
     * @param aspectW 目标比例宽度（如 1、3、16）
     * @param aspectH 目标比例高度（如 1、4、9）
     * @return CropRegion 裁剪区域，w/h=0 表示无需裁剪
     */
    fun calculateCropRegionForAspectRatio(
        imageWidth: Int,
        imageHeight: Int,
        aspectW: Int,
        aspectH: Int,
    ): CropRegion {
        if (aspectW <= 0 || aspectH <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            return CropRegion(0, 0, 0, 0)
        }
        val targetAspect = aspectW.toFloat() / aspectH.toFloat()
        val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
        var cropW = imageWidth
        var cropH = imageHeight
        var cropX = 0
        var cropY = 0
        if (imageAspect > targetAspect) {
            // 原图更宽，需要裁剪宽度
            cropW = (imageHeight * targetAspect).toInt()
            cropX = (imageWidth - cropW) / 2
        } else if (imageAspect < targetAspect) {
            // 原图更高，需要裁剪高度
            cropH = (imageWidth / targetAspect).toInt()
            cropY = (imageHeight - cropH) / 2
        }
        if (cropW == imageWidth && cropH == imageHeight) {
            return CropRegion(0, 0, 0, 0)
        }
        return CropRegion(cropX, cropY, cropW, cropH)
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 将 Uri 复制到临时文件
     */
    private fun copyUriToTempFile(context: Context, uri: Uri, dir: File): String? {
        return try {
            val temp = File(dir, "tmp_uri_${System.currentTimeMillis()}.jpg")
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { inp ->
                FileOutputStream(temp).use { out -> inp.copyTo(out) }
            }
            temp.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
