#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/bitmap.h>
#include <cstdio>
#include <cstdlib>
#include <csetjmp>
#include <memory>
#include "jpeg_compressor.h"

#define LOG_TAG "JPEGCompressor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// 压缩JPEG图片
JNIEXPORT jint JNICALL
Java_com_iuuaa_jpegcompressor_JPEGCompressor_nativeCompress(
        JNIEnv *env, jclass clazz,
        jstring inputPath, jstring outputPath, jint quality) {

    const char *input = env->GetStringUTFChars(inputPath, nullptr);
    const char *output = env->GetStringUTFChars(outputPath, nullptr);

    if (!input || !output) {
        LOGE("Failed to get string chars");
        if (input) env->ReleaseStringUTFChars(inputPath, input);
        if (output) env->ReleaseStringUTFChars(outputPath, output);
        return -1;
    }

    LOGI("Compressing: %s -> %s (quality: %d)", input, output, quality);

    int result = compress_jpeg(input, output, quality);

    env->ReleaseStringUTFChars(inputPath, input);
    env->ReleaseStringUTFChars(outputPath, output);

    return result;
}

// 压缩并可选缩放和裁剪
// targetWidth/targetHeight/scale 为 0 表示不缩放，crop_* 全为 0 表示不裁剪
// rotation 旋转角度（0=不旋转, 90=顺时针90度, 180=180度, 270=顺时针270度）
// fallbackToOriginal 非 0 时，压缩失败则复制原图到输出路径（返回 1 表示使用了 fallback）
JNIEXPORT jint JNICALL
Java_com_iuuaa_jpegcompressor_JPEGCompressor_nativeCompressEx(
        JNIEnv *env, jclass clazz,
        jstring inputPath, jstring outputPath, jint quality,
        jint targetWidth, jint targetHeight, jfloat scale,
        jint cropX, jint cropY, jint cropW, jint cropH,
        jint rotation, jboolean fallbackToOriginal) {

    const char *input = env->GetStringUTFChars(inputPath, nullptr);
    const char *output = env->GetStringUTFChars(outputPath, nullptr);

    if (!input || !output) {
        LOGE("Failed to get string chars");
        if (input) env->ReleaseStringUTFChars(inputPath, input);
        if (output) env->ReleaseStringUTFChars(outputPath, output);
        return -1;
    }

    int result = compress_jpeg_ex(input, output, quality,
                                  (int) targetWidth, (int) targetHeight, (float) scale,
                                  (int) cropX, (int) cropY, (int) cropW, (int) cropH,
                                  (int) rotation, fallbackToOriginal ? 1 : 0);

    env->ReleaseStringUTFChars(inputPath, input);
    env->ReleaseStringUTFChars(outputPath, output);

    return result;
}

} // extern "C"
