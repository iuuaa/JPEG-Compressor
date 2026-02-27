package com.example.jpegcompressor

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.jpegcompressor.databinding.ActivityCameraBinding
import com.otaliastudios.cameraview.CameraException
import com.otaliastudios.cameraview.PictureResult
import com.otaliastudios.cameraview.controls.Facing
import java.io.File

/**
 * 自定义相机页面（基于 [CameraView](https://github.com/natario1/CameraView)）
 * - 横竖屏通过「横/竖屏」按钮手动切换（cameraUseDeviceOrientation=false）
 * - 「前后摄像头」按钮切换前置/后置
 *
 * @author anonymous
 * @since 2026/2/25 星期三
 * @version 1.0.0
 */
class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
                setResult(RESULT_CANCELED)
                finish()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }

        binding.cameraView.setLifecycleOwner(this)

        binding.btnRotate.setOnClickListener {
            val current = requestedOrientation
            requestedOrientation = when {
                current == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }

        binding.btnSwitchCamera.setOnClickListener {
            val next = if (binding.cameraView.facing == Facing.BACK) Facing.FRONT else Facing.BACK
            binding.cameraView.facing = next
        }

        binding.cameraView.addCameraListener(object :
            com.otaliastudios.cameraview.CameraListener() {
            override fun onCameraError(exception: CameraException) {
                binding.btnCapture.isEnabled = true
                Toast.makeText(
                    this@CameraActivity,
                    "相机错误: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onPictureTaken(result: PictureResult) {
                binding.btnCapture.isEnabled = true
                val outputFile = File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                result.toFile(outputFile) { file ->
                    runOnUiThread {
                        if (file != null) {
                            setResult(
                                RESULT_OK,
                                Intent().apply { putExtra(EXTRA_OUTPUT_PATH, file.absolutePath) })
                        } else {
                            setResult(RESULT_CANCELED)
                        }
                        finish()
                    }
                }
            }
        })

        binding.btnCapture.setOnClickListener {
            binding.btnCapture.isEnabled = false
            binding.cameraView.takePicture()
        }
    }

    override fun onDestroy() {
        binding.cameraView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OUTPUT_PATH = "extra_output_path"
    }
}
