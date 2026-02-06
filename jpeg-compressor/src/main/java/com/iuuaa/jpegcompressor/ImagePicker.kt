package com.iuuaa.jpegcompressor

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * 图片选择器工具类。
 *
 * 使用方式（解决 Fragment registerForActivityResult 时机问题）：
 * 1. 注册：在 Fragment 属性初始化或 onAttach/onCreate 中调用
 *    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> ... }
 * 2. 发起选择：在点击等时机调用
 *    launcher.launch(ImagePicker.createPickImageIntent())
 */
class ImagePicker private constructor() {

    companion object {
        @JvmStatic
        val instance: ImagePicker by lazy { ImagePicker() }

        /**
         * 创建选择图片的 Intent，供 [ActivityResultLauncher.launch][androidx.activity.result.ActivityResultLauncher.launch] 使用。
         * 调用方需先通过 registerForActivityResult(StartActivityForResult()) 注册，再在合适时机 launch 此 Intent。
         */
        @JvmStatic
        fun createPickImageIntent(): Intent {
            return Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        }
    }

    /**
     * 将 Uri 转换为本地文件路径（会复制到 app cache 目录）
     */
    fun getFilePathFromUri(activity: Activity, uri: Uri): String? {
        return try {
            val inputStream = activity.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(activity.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 将图片保存到系统相册（Android 10+ 使用 MediaStore，无需存储权限）。
     * 仅当 API 29+ 时使用 RELATIVE_PATH 方案；以下版本返回 null。
     *
     * @param context Context
     * @param imagePath 本地图片文件路径
     * @param displayName 显示名称（不含扩展名），为 null 时使用时间戳
     * @return 保存后的 MediaStore Uri，失败或 API&lt;29 返回 null
     */
    fun saveImageToGallery(
        context: Context,
        imagePath: String,
        displayName: String? = null,
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        val file = File(imagePath)
        if (!file.exists()) return null
        val name = displayName ?: "IMG_${System.currentTimeMillis()}"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/CursorAI")
        }
        return try {
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { out: OutputStream ->
                file.inputStream().use { it.copyTo(out) }
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
