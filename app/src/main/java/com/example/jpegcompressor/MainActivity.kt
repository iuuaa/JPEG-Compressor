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
import com.iuuaa.jpegcompressor.ImagePicker
import com.iuuaa.jpegcompressor.JPEGCompressor
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var originalImagePath: String? = null
    private var compressedImagePath: String? = null

    private val compressor = JPEGCompressor.instance
    private val imagePicker = ImagePicker.instance
    private val compositeDisposable = CompositeDisposable()

    // 必须在 Fragment 创建前（init 或 onCreate）注册
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val path = imagePicker.getFilePathFromUri(this, uri)
                path?.let { imagePath ->
                    originalImagePath = imagePath
                    loadOriginalImage(imagePath)
                    binding.btnCompress.isEnabled = true
                } ?: run {
                    Toast.makeText(this, "无法获取图片路径", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLongEdgeVisibility()
        binding.rgScaleMode.setOnCheckedChangeListener { _, _ -> setupLongEdgeVisibility() }
        setupClickListeners()
    }

    private fun setupLongEdgeVisibility() {
        binding.tilLongEdge.visibility =
            if (binding.rgScaleMode.checkedRadioButtonId == R.id.rbScaleLongEdge) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun setupClickListeners() {
        binding.btnSelectImage.setOnClickListener {
            pickImageLauncher.launch(ImagePicker.createPickImageIntent())
        }

        binding.btnCompress.setOnClickListener {
            originalImagePath?.let { compressImage(it) }
                ?: Toast.makeText(this@MainActivity, "请先选择图片", Toast.LENGTH_SHORT).show()
        }

        binding.btnPreviewOriginal.setOnClickListener {
            originalImagePath?.let {
                openImageInGallery(
                    it,
                    "${this@MainActivity.packageName}.fileprovider"
                )
            }
        }

        binding.btnPreviewCompressed.setOnClickListener {
            compressedImagePath?.let {
                openImageInGallery(
                    it,
                    "${this@MainActivity.packageName}.fileprovider"
                )
            }
        }

        binding.btnSaveToGallery.setOnClickListener {
            compressedImagePath?.let { path ->
                val uri = imagePicker.saveImageToGallery(this@MainActivity, path, null)
                if (uri != null) {
                    Toast.makeText(this@MainActivity, "已保存到相册", Toast.LENGTH_SHORT).show()
                } else {
                    val msg = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        "保存到相册需要 Android 10 及以上"
                    } else {
                        "保存失败"
                    }
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadOriginalImage(imagePath: String) {
        Glide.with(this).load(imagePath).into(binding.ivOriginal)
        val info = compressor.getImageInfo(imagePath)
        binding.tvOriginalInfo.text = buildImageInfoText("原始", info)
        binding.btnPreviewOriginal.isEnabled = true
    }

    private fun compressImage(inputPath: String) {
        val quality = try {
            binding.etQuality.text.toString().toInt().coerceIn(1, 100)
        } catch (_: Exception) {
            85
        }

        val info = compressor.getImageInfo(inputPath)
        val cropX: Int
        val cropY: Int
        val cropW: Int
        val cropH: Int
        when (binding.rgCropMode.checkedRadioButtonId) {
            R.id.rbCrop1x1 -> {
                val crop =
                    compressor.calculateCropRegionForAspectRatio(info.width, info.height, 1, 1)
                cropX = crop.x
                cropY = crop.y
                cropW = crop.w
                cropH = crop.h
            }

            R.id.rbCrop3x4 -> {
                val crop =
                    compressor.calculateCropRegionForAspectRatio(info.width, info.height, 3, 4)
                cropX = crop.x
                cropY = crop.y
                cropW = crop.w
                cropH = crop.h
            }

            R.id.rbCrop4x3 -> {
                val crop =
                    compressor.calculateCropRegionForAspectRatio(info.width, info.height, 4, 3)
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
                scale = 0.5f
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
                    w = info.width
                    h = info.height
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
            this@MainActivity.cacheDir,
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
        binding.tvCompressedInfo.text = buildImageInfoText("压缩后", info)
        binding.btnPreviewCompressed.isEnabled = true
        binding.btnSaveToGallery.isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    private fun buildImageInfoText(label: String, info: JPEGCompressor.ImageInfo): String {
        val sizeMB = info.fileSize / 1024.0 / 1024.0
        return "$label - 尺寸: ${info.width} x ${info.height}, 大小: ${
            String.format(
                Locale.CHINESE,
                "%.2f",
                sizeMB
            )
        } MB"
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
        binding.tvCompressResult.text = """
            压缩成功！
            原始大小: ${String.format(Locale.CHINESE, "%.2f", inputSizeMB)} MB
            压缩后大小: ${String.format(Locale.CHINESE, "%.2f", outputSizeMB)} MB
            $ratioText
            原始尺寸: ${result.inputWidth} x ${result.inputHeight}
            压缩后尺寸: ${result.outputWidth} x ${result.outputHeight}
        """.trimIndent()
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()
    }

    /**
     * 使用 FileProvider 打开系统预览查看图片，避免 FileUriExposedException。
     *
     * @param imagePath 图片本地路径
     * @param fileProviderAuthority 应用 AndroidManifest 中 FileProvider 的 android:authorities 值
     */
    fun openImageInGallery(imagePath: String, fileProviderAuthority: String) {
        try {
            val file = File(imagePath)
            if (!file.exists()) return
            val uri: Uri = FileProvider.getUriForFile(this, fileProviderAuthority, file)
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
}