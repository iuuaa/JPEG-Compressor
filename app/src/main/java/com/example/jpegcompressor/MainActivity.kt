package com.example.jpegcompressor

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.example.jpegcompressor.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.jpegcompressor.ImagePicker
import com.iuuaa.jpegcompressor.JPEGCompressor
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var originalImagePath: String? = null
    private var originalImageUri: Uri? = null
    private var compressedImagePath: String? = null

    /** 系统相机拍照时使用的输出文件，用于 result 回调中取路径 */
    private var systemCameraOutputFile: File? = null

    private val compressor = JPEGCompressor.instance
    private val imagePicker = ImagePicker.instance
    private val compositeDisposable = CompositeDisposable()

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                originalImageUri = uri
                val path = imagePicker.getFilePathFromUri(this, uri)
                path?.let { imagePath ->
                    originalImagePath = imagePath
                    loadOriginalImage(imagePath, uri)
                    binding.btnCompress.isEnabled = true
                } ?: run {
                    Toast.makeText(this, "无法获取图片路径", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra(CameraActivity.EXTRA_OUTPUT_PATH)
            path?.let { imagePath ->
                originalImagePath = imagePath
                originalImageUri = null
                loadOriginalImage(imagePath, null)
                binding.btnCompress.isEnabled = true
                // 相机拍摄的照片建议开启自动旋转，横屏拍摄时可修正方向
                if (!binding.switchAutoRotate.isChecked) {
                    binding.switchAutoRotate.isChecked = true
                }
            }
        }
    }

    private val systemCameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            systemCameraOutputFile?.absolutePath?.let { imagePath ->
                originalImagePath = imagePath
                originalImageUri = null
                loadOriginalImage(imagePath, null)
                binding.btnCompress.isEnabled = true
                if (!binding.switchAutoRotate.isChecked) {
                    binding.switchAutoRotate.isChecked = true
                }
            } ?: Toast.makeText(this, "无法获取照片路径", Toast.LENGTH_SHORT).show()
        }
        systemCameraOutputFile = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupScaleOptionsVisibility()
        binding.rgScaleMode.setOnCheckedChangeListener { _, _ -> setupScaleOptionsVisibility() }
        setupClickListeners()
    }

    private fun setupScaleOptionsVisibility() {
        val isLongEdge = binding.rgScaleMode.checkedRadioButtonId == R.id.rbScaleLongEdge
        val isScalePercent = binding.rgScaleMode.checkedRadioButtonId == R.id.rbScaleHalf
        binding.tilLongEdge.visibility = if (isLongEdge) View.VISIBLE else View.GONE
        binding.tilScalePercent.visibility = if (isScalePercent) View.VISIBLE else View.GONE
    }

    /** 使用系统相机拍照，照片写入缓存文件后可用于压缩 */
    private fun launchSystemCamera() {
        val outputFile = File(cacheDir, "system_camera_${System.currentTimeMillis()}.jpg")
        val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", outputFile)
        systemCameraOutputFile = outputFile
        systemCameraLauncher.launch(uri)
    }

    private fun setupClickListeners() {
        binding.btnSelectImage.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("选择图片")
                .setItems(arrayOf("从相册选择", "自定义相机拍摄", "系统相机拍照")) { _, which ->
                    when (which) {
                        0 -> pickImageLauncher.launch(ImagePicker.createPickImageIntent())
                        1 -> cameraLauncher.launch(
                            Intent(
                                this,
                                CameraActivity::class.java
                            )
                        )
                        2 -> launchSystemCamera()
                    }
                }
                .show()
        }

        binding.btnCompress.setOnClickListener {
            originalImagePath?.let { compressImage(it) }
                ?: Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show()
        }

        binding.ivOriginal.setOnClickListener {
            originalImagePath?.let { path ->
                openImageInGallery(path)
            }
        }

        binding.ivCompressed.setOnClickListener {
            compressedImagePath?.let { path ->
                openImageInGallery(path)
            }
        }

        binding.btnSaveToGallery.setOnClickListener {
            compressedImagePath?.let { path ->
                val uri = imagePicker.saveImageToGallery(this, path, null)
                if (uri != null) {
                    Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show()
                } else {
                    val msg = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        "保存到相册需要 Android 10 及以上"
                    } else {
                        "保存失败"
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadOriginalImage(imagePath: String, uri: Uri?) {
        Glide.with(this).load(uri ?: imagePath).into(binding.ivOriginal)
        val info = getImageInfoForDisplay(imagePath, uri)
        binding.tvOriginalInfo.text = buildImageInfoText("原始", info)
    }

    private fun compressImage(inputPath: String) {
        val quality = try {
            binding.etQuality.text.toString().toInt().coerceIn(1, 100)
        } catch (_: Exception) {
            85
        }

        val info = getImageInfoForCompress(inputPath)
        // 开启自动旋转时，Native 先旋转再裁剪，裁剪区域必须在「旋转后的逻辑尺寸」下计算
        val rotation =
            if (binding.switchAutoRotate.isChecked) compressor.getExifRotation(inputPath) else 0
        val logW = if (rotation == 90 || rotation == 270) info.height else info.width
        val logH = if (rotation == 90 || rotation == 270) info.width else info.height

        val cropX: Int
        val cropY: Int
        val cropW: Int
        val cropH: Int
        when (binding.rgCropMode.checkedRadioButtonId) {
            R.id.rbCropNone -> {
                // 不裁剪：传 0 给库，库内 cropW/cropH 为 0 时仅做质量压缩
                cropX = 0
                cropY = 0
                cropW = 0
                cropH = 0
            }
            R.id.rbCrop1x1 -> {
                val crop =
                    compressor.alignCropRegionToMCU(
                        compressor.calculateCropRegionForAspectRatio(logW, logH, 1, 1),
                        logW, logH
                    )
                cropX = crop.x
                cropY = crop.y
                cropW = crop.w
                cropH = crop.h
            }

            R.id.rbCrop3x4 -> {
                val crop =
                    compressor.alignCropRegionToMCU(
                        compressor.calculateCropRegionForAspectRatio(logW, logH, 3, 4),
                        logW, logH
                    )
                cropX = crop.x
                cropY = crop.y
                cropW = crop.w
                cropH = crop.h
            }

            R.id.rbCrop4x3 -> {
                val crop =
                    compressor.alignCropRegionToMCU(
                        compressor.calculateCropRegionForAspectRatio(logW, logH, 4, 3),
                        logW, logH
                    )
                cropX = crop.x
                cropY = crop.y
                cropW = crop.w
                cropH = crop.h
            }

            else -> {
                cropX = 0
                cropY = 0
                cropW = 0
                cropH = 0
            }
        }

        val longEdgeLimit = try {
            binding.etLongEdge.text.toString().toInt().coerceIn(64, 8192)
        } catch (_: Exception) {
            1920
        }

        val scale: Float
        val targetWidth: Int
        val targetHeight: Int
        when (binding.rgScaleMode.checkedRadioButtonId) {
            R.id.rbScaleHalf -> {
                val percent = try {
                    binding.etScalePercent.text.toString().toInt().coerceIn(1, 100)
                } catch (_: Exception) {
                    50
                }
                scale = percent / 100f
                targetWidth = 0
                targetHeight = 0
            }

            R.id.rbScaleLongEdge -> {
                scale = 0f
                val w: Int
                val h: Int
                if (cropW > 0 && cropH > 0) {
                    w = cropW
                    h = cropH
                } else {
                    w = logW
                    h = logH
                }
                targetWidth =
                    if (w >= h) longEdgeLimit else (longEdgeLimit * w / h).coerceAtLeast(1)
                targetHeight =
                    if (h >= w) longEdgeLimit else (longEdgeLimit * h / w).coerceAtLeast(1)
            }

            else -> {
                scale = 0f
                targetWidth = 0
                targetHeight = 0
            }
        }

        val outputPath = File(
            this.cacheDir,
            "compressed_${System.currentTimeMillis()}.jpg"
        ).absolutePath

        binding.btnCompress.isEnabled = false
        binding.tvCompressResult.text = "正在压缩..."

        val progressDialog = MaterialAlertDialogBuilder(this)
            .setView(R.layout.dialog_compress_progress)
            .setCancelable(false)
            .create()
        progressDialog.show()

        val disposable = compressor.compressAsync(
            inputPath = inputPath,
            outputPath = outputPath,
            quality = quality,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            scale = scale,
            cropX = cropX,
            cropY = cropY,
            cropW = cropW,
            cropH = cropH,
            autoRotate = binding.switchAutoRotate.isChecked,
            callback = object : JPEGCompressor.CompressCallback {
                override fun onSuccess(result: JPEGCompressor.CompressResult) {
                    progressDialog.dismiss()
                    binding.btnCompress.isEnabled = true
                    if (result.success) {
                        compressedImagePath = result.outputPath
                        loadCompressedImage(result)
                        showCompressResult(result)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "压缩失败: ${result.errorMessage}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onError(error: Throwable) {
                    progressDialog.dismiss()
                    binding.btnCompress.isEnabled = true
                    Toast.makeText(
                        this@MainActivity,
                        "压缩失败: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        compositeDisposable.add(disposable)
    }

    private fun loadCompressedImage(result: JPEGCompressor.CompressResult) {
        Glide.with(this).load(result.outputPath).into(binding.ivCompressed)
        val info = compressor.getImageInfo(result.outputPath)
        val infoWithRatio =
            if (info.width > 0 && info.height > 0) info else JPEGCompressor.ImageInfo(
                result.outputWidth, result.outputHeight, result.outputSize, result.outputPath
            )
        binding.tvCompressedInfo.text = buildImageInfoText("压缩后", infoWithRatio)
        binding.btnSaveToGallery.isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    /** 获取图片信息：先 path，失败且为相册选图时再 Uri（内部含流兜底），避免重复实现 */
    private fun getImageInfoForDisplay(imagePath: String, uri: Uri?): JPEGCompressor.ImageInfo {
        var info = compressor.getImageInfo(imagePath)
        if (info.width > 0 && info.height > 0) return info
        if (uri != null) info = compressor.getImageInfo(this, uri)
        return info
    }

    /** 压缩用图片信息：与 getImageInfoForDisplay 一致，保证尺寸来源统一 */
    private fun getImageInfoForCompress(inputPath: String): JPEGCompressor.ImageInfo {
        var info = compressor.getImageInfo(inputPath)
        if (info.width > 0 && info.height > 0) return info
        originalImageUri?.let { info = compressor.getImageInfo(this, it) }
        return info
    }

    private fun buildImageInfoText(label: String, info: JPEGCompressor.ImageInfo): String {
        val validSize = info.width > 0 && info.height > 0
        val sizeStr = if (validSize) {
            "尺寸: ${info.width} × ${info.height}"
        } else {
            "尺寸: 无法读取"
        }
        val ratioStr = if (validSize) {
            val g = gcd(info.width, info.height)
            ", 比例: ${info.width / g}:${info.height / g}"
        } else ""
        val sizeMBStr = if (info.fileSize > 0) {
            String.format(Locale.CHINESE, "%.2f", info.fileSize / 1024.0 / 1024.0) + " MB"
        } else {
            "—"
        }
        return "$label - $sizeStr$ratioStr, 大小: $sizeMBStr"
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = kotlin.math.abs(a).coerceAtLeast(1)
        var y = kotlin.math.abs(b).coerceAtLeast(1)
        while (y != 0) {
            val t = y
            y = x % y
            x = t
        }
        return x
    }

    @SuppressLint("SetTextI18n")
    private fun showCompressResult(result: JPEGCompressor.CompressResult) {
        val inputSizeMB = result.inputSize / 1024.0 / 1024.0
        val outputSizeMB = result.outputSize / 1024.0 / 1024.0
        val ratioText = if (result.compressionRatio < 0) {
            "体积增大: ${
                String.format(
                    Locale.CHINESE,
                    "%.2f",
                    -result.compressionRatio
                )
            }%（再次压缩已压缩的图片常会变大）"
        } else {
            "压缩率: ${String.format(Locale.CHINESE, "%.2f", result.compressionRatio)}%"
        }
        val rotationLine = if (result.rotationApplied != 0) {
            "\n已自动旋转: ${result.rotationApplied}°（根据 EXIF 方向修正）"
        } else {
            ""
        }
        val outRatioStr = if (result.outputWidth > 0 && result.outputHeight > 0) {
            val g = gcd(result.outputWidth, result.outputHeight)
            ", 比例 ${result.outputWidth / g}:${result.outputHeight / g}"
        } else ""
        val inRatioStr = if (result.inputWidth > 0 && result.inputHeight > 0) {
            val g = gcd(result.inputWidth, result.inputHeight)
            ", 比例 ${result.inputWidth / g}:${result.inputHeight / g}"
        } else ""
        binding.tvCompressResult.text = """
            压缩成功！
            原始大小: ${String.format(Locale.CHINESE, "%.2f", inputSizeMB)} MB
            压缩后大小: ${String.format(Locale.CHINESE, "%.2f", outputSizeMB)} MB
            $ratioText
            原始尺寸: ${result.inputWidth} × ${result.inputHeight}$inRatioStr
            压缩后尺寸: ${result.outputWidth} × ${result.outputHeight}$outRatioStr$rotationLine
        """.trimIndent()
    }

    /**
     * 使用 FileProvider 打开系统预览查看图片，避免 FileUriExposedException。
     *
     * @param imagePath 图片本地路径
     */
    fun openImageInGallery(imagePath: String) {
        try {
            val file = File(imagePath)
            if (!file.exists()) return
            val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()
    }
}
