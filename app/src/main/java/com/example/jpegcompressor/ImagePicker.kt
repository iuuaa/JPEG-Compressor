package com.example.jpegcompressor

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
 * Image picker & gallery helper used only by the sample app.
 */
class ImagePicker private constructor() {

    companion object {
        @JvmStatic
        val instance: ImagePicker by lazy { ImagePicker() }

        @JvmStatic
        fun createPickImageIntent(): Intent {
            return Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        }
    }

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

    fun saveImageToGallery(
        context: Context,
        imagePath: String,
        displayName: String? = null
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val file = File(imagePath)
        if (!file.exists()) return null
        val name = displayName ?: "IMG_${System.currentTimeMillis()}"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/JPEGCompressorSample")
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
