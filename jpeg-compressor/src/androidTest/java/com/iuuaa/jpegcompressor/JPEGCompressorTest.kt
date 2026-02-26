package com.iuuaa.jpegcompressor

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * 核心功能单元测试：裁剪区域计算、MCU 对齐等纯逻辑（不依赖真实图片文件）。
 * 运行：./gradlew :jpeg-compressor:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class JPEGCompressorTest {

    private val compressor: JPEGCompressor
        get() = JPEGCompressor.instance

    // ---------- calculateCropRegionForAspectRatio ----------

    @Test
    fun calculateCropRegion_1x1_fromWideImage_returnsCenteredCrop() {
        // 横图 400x200，目标 1:1 -> 裁剪为 200x200，居中
        val crop = compressor.calculateCropRegionForAspectRatio(400, 200, 1, 1)
        assertEquals(100, crop.x)
        assertEquals(0, crop.y)
        assertEquals(200, crop.w)
        assertEquals(200, crop.h)
    }

    @Test
    fun calculateCropRegion_1x1_fromTallImage_returnsCenteredCrop() {
        // 竖图 200x400，目标 1:1 -> 裁剪为 200x200，居中
        val crop = compressor.calculateCropRegionForAspectRatio(200, 400, 1, 1)
        assertEquals(0, crop.x)
        assertEquals(100, crop.y)
        assertEquals(200, crop.w)
        assertEquals(200, crop.h)
    }

    @Test
    fun calculateCropRegion_1x1_alreadySquare_returnsNoCrop() {
        val crop = compressor.calculateCropRegionForAspectRatio(300, 300, 1, 1)
        assertEquals(0, crop.x)
        assertEquals(0, crop.y)
        assertEquals(0, crop.w)
        assertEquals(0, crop.h)
    }

    @Test
    fun calculateCropRegion_16x9_fromWideImage_returnsCorrectCrop() {
        // 1920x1080 已是 16:9，无需裁剪
        val crop = compressor.calculateCropRegionForAspectRatio(1920, 1080, 16, 9)
        assertEquals(0, crop.x)
        assertEquals(0, crop.y)
        assertEquals(0, crop.w)
        assertEquals(0, crop.h)
    }

    @Test
    fun calculateCropRegion_16x9_fromTallImage_returnsCenteredCrop() {
        // 1080x1920，目标 16:9 -> 裁剪高度 1080/(16/9)=607.5 -> 607，居中
        val crop = compressor.calculateCropRegionForAspectRatio(1080, 1920, 16, 9)
        assertEquals(0, crop.x)
        assertEquals(656, crop.y)  // (1920-607)/2
        assertEquals(1080, crop.w)
        assertEquals(607, crop.h)
    }

    @Test
    fun calculateCropRegion_invalidInput_returnsNoCrop() {
        assertEquals(JPEGCompressor.CropRegion(0, 0, 0, 0), compressor.calculateCropRegionForAspectRatio(0, 100, 1, 1))
        assertEquals(JPEGCompressor.CropRegion(0, 0, 0, 0), compressor.calculateCropRegionForAspectRatio(100, 0, 1, 1))
        assertEquals(JPEGCompressor.CropRegion(0, 0, 0, 0), compressor.calculateCropRegionForAspectRatio(100, 100, 0, 1))
        assertEquals(JPEGCompressor.CropRegion(0, 0, 0, 0), compressor.calculateCropRegionForAspectRatio(100, 100, 1, 0))
    }

    // ---------- alignCropRegionToMCU ----------

    @Test
    fun alignCropRegionToMCU_alignedInput_unchanged() {
        val crop = JPEGCompressor.CropRegion(0, 0, 64, 48)
        val aligned = compressor.alignCropRegionToMCU(crop, 100, 100, 16)
        assertEquals(0, aligned.x)
        assertEquals(0, aligned.y)
        assertEquals(64, aligned.w)
        assertEquals(48, aligned.h)
    }

    @Test
    fun alignCropRegionToMCU_unalignedInput_alignedTo16() {
        val crop = JPEGCompressor.CropRegion(10, 20, 50, 40)
        val aligned = compressor.alignCropRegionToMCU(crop, 100, 100, 16)
        assertEquals(0, aligned.x)
        assertEquals(16, aligned.y)
        assertEquals(48, aligned.w)   // 50 -> 48 (16*3)
        assertEquals(32, aligned.h)   // 40 -> 32 (16*2)
    }

    @Test
    fun alignCropRegionToMCU_noCrop_returnsOriginal() {
        val crop = JPEGCompressor.CropRegion(0, 0, 0, 0)
        val aligned = compressor.alignCropRegionToMCU(crop, 100, 100, 16)
        assertEquals(crop, aligned)
    }

    @Test
    fun alignCropRegionToMCU_smallCrop_clampedToAlign() {
        val crop = JPEGCompressor.CropRegion(0, 0, 8, 8)
        val aligned = compressor.alignCropRegionToMCU(crop, 100, 100, 16)
        assertEquals(0, aligned.x)
        assertEquals(0, aligned.y)
        assertEquals(16, aligned.w)
        assertEquals(16, aligned.h)
    }

    // ---------- Constants ----------

    @Test
    fun constants_areExpected() {
        assertEquals(8192, JPEGCompressor.MAX_DIMENSION)
        assertEquals(8192L * 8192L, JPEGCompressor.MAX_PIXELS)
        assertEquals((-2), JPEGCompressor.ERROR_IMAGE_TOO_LARGE)
        assertEquals(0, JPEGCompressor.RESULT_SUCCESS)
        assertEquals(1, JPEGCompressor.RESULT_FALLBACK)
    }
}
