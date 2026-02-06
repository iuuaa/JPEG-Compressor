#include "jpeg_compressor.h"
#include "turbojpeg.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cstddef>
#include <android/log.h>
/* posix_memalign: Android 用 malloc.h，其他用 stdlib.h */
#if defined(__ANDROID__)

#include <malloc.h>

#else
#ifndef _POSIX_C_SOURCE
#define _POSIX_C_SOURCE 200809L
#endif
#include <stdlib.h>
#endif

#ifndef TJINIT_DECOMPRESS
#define TJINIT_DECOMPRESS 1
#endif

#define LOG_TAG "JPEGCompressor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* 单边最大像素，超过则拒绝压缩以避免 OOM */
#define MAX_DIMENSION 8192
/* 最大总像素数：约 67MP，RGB 缓冲区约 width*height*3 ≈ 200MB */
#define MAX_PIXELS ((unsigned long)(MAX_DIMENSION) * (unsigned long)(MAX_DIMENSION))

/* ==================== 文件操作 ==================== */

static unsigned char *read_file(const char *path, unsigned long *size) {
    FILE *file = fopen(path, "rb");
    if (!file) {
        LOGE("Cannot open file: %s", path);
        return nullptr;
    }
    fseek(file, 0, SEEK_END);
    *size = ftell(file);
    fseek(file, 0, SEEK_SET);
    auto *buffer = (unsigned char *) malloc(*size);
    if (!buffer) {
        LOGE("Cannot allocate memory for file: %s", path);
        fclose(file);
        return nullptr;
    }
    size_t read_size = fread(buffer, 1, *size, file);
    fclose(file);
    if (read_size != *size) {
        LOGE("Failed to read entire file: %s", path);
        free(buffer);
        return nullptr;
    }
    return buffer;
}

static int write_file(const char *path, unsigned char *buffer, unsigned long size) {
    FILE *file = fopen(path, "wb");
    if (!file) {
        LOGE("Cannot open file for writing: %s", path);
        return -1;
    }
    size_t written = fwrite(buffer, 1, size, file);
    fclose(file);
    if (written != size) {
        LOGE("Failed to write entire file: %s", path);
        return -1;
    }
    return 0;
}

/* 复制文件（用于 fallback 时直接复制原图）
 * 使用分块复制，避免大文件占用过多内存
 */
static int copy_file(const char *src_path, const char *dst_path) {
    FILE *src = fopen(src_path, "rb");
    if (!src) {
        LOGE("Cannot open source file: %s", src_path);
        return -1;
    }
    FILE *dst = fopen(dst_path, "wb");
    if (!dst) {
        LOGE("Cannot open destination file: %s", dst_path);
        fclose(src);
        return -1;
    }

    const size_t COPY_BUF_SIZE = 64 * 1024; /* 64KB */
    auto *buffer = (unsigned char *) malloc(COPY_BUF_SIZE);
    if (!buffer) {
        LOGE("Cannot allocate copy buffer");
        fclose(src);
        fclose(dst);
        return -1;
    }

    size_t bytes_read;
    int result = 0;
    while ((bytes_read = fread(buffer, 1, COPY_BUF_SIZE, src)) > 0) {
        if (fwrite(buffer, 1, bytes_read, dst) != bytes_read) {
            LOGE("Failed to write during copy");
            result = -1;
            break;
        }
    }

    free(buffer);
    fclose(src);
    fclose(dst);
    return result;
}

/* ==================== 图像缩放 ==================== */

/*
 * 优化版最近邻缩放 RGB 图像
 * 使用预计算行偏移，减少重复计算
 */
static void resize_rgb_nn(const unsigned char *src, int srcW, int srcH,
                          unsigned char *dst, int dstW, int dstH) {
    /* 预计算 X 方向源索引 */
    int *sx_table = (int *) malloc(dstW * sizeof(int));
    if (!sx_table) {
        /* 回退到逐像素计算 */
        for (int y = 0; y < dstH; y++) {
            int sy = (int) ((long) y * srcH / dstH);
            if (sy >= srcH) sy = srcH - 1;
            const unsigned char *srcRow = src + sy * srcW * 3;
            unsigned char *dstRow = dst + y * dstW * 3;
            for (int x = 0; x < dstW; x++) {
                int sx = (int) ((long) x * srcW / dstW);
                if (sx >= srcW) sx = srcW - 1;
                const unsigned char *s = srcRow + sx * 3;
                dstRow[0] = s[0];
                dstRow[1] = s[1];
                dstRow[2] = s[2];
                dstRow += 3;
            }
        }
        return;
    }

    for (int x = 0; x < dstW; x++) {
        int sx = (int) ((long) x * srcW / dstW);
        if (sx >= srcW) sx = srcW - 1;
        sx_table[x] = sx * 3;
    }

    /* 循环展开优化：每次处理 4 个像素，减少循环开销 */
    for (int y = 0; y < dstH; y++) {
        int sy = (int) ((long) y * srcH / dstH);
        if (sy >= srcH) sy = srcH - 1;
        const unsigned char *srcRow = src + sy * srcW * 3;
        unsigned char *dstRow = dst + y * dstW * 3;

        int x = 0;
        /* 处理 4 的倍数像素 */
        for (; x < dstW - 3; x += 4) {
            const unsigned char *s0 = srcRow + sx_table[x];
            const unsigned char *s1 = srcRow + sx_table[x + 1];
            const unsigned char *s2 = srcRow + sx_table[x + 2];
            const unsigned char *s3 = srcRow + sx_table[x + 3];

            dstRow[0] = s0[0];
            dstRow[1] = s0[1];
            dstRow[2] = s0[2];
            dstRow[3] = s1[0];
            dstRow[4] = s1[1];
            dstRow[5] = s1[2];
            dstRow[6] = s2[0];
            dstRow[7] = s2[1];
            dstRow[8] = s2[2];
            dstRow[9] = s3[0];
            dstRow[10] = s3[1];
            dstRow[11] = s3[2];
            dstRow += 12;
        }
        /* 处理剩余像素 */
        for (; x < dstW; x++) {
            const unsigned char *s = srcRow + sx_table[x];
            dstRow[0] = s[0];
            dstRow[1] = s[1];
            dstRow[2] = s[2];
            dstRow += 3;
        }
    }

    free(sx_table);
}

/* ==================== 压缩实现（接收已读取的 buffer） ==================== */

/*
 * 内部压缩实现，接收已读取的 JPEG buffer 和预解析的图像信息
 * 避免重复读取文件和解析头部
 *
 * srcBuf/srcSize: 已读取的 JPEG 数据
 * img_width/img_height: 原图尺寸（已从头部解析）
 * output_path: 输出路径
 * quality: 压缩质量 1-100
 * out_width/out_height: 输出尺寸
 * crop_x/crop_y/crop_w/crop_h: 裁剪区域（crop_w=0 表示不裁剪）
 */
static int compress_jpeg_from_buffer(
        const unsigned char *srcBuf, unsigned long srcSize,
        int img_width, int img_height,
        const char *output_path, int quality,
        int out_width, int out_height,
        int crop_x, int crop_y, int crop_w, int crop_h) {

    tjhandle handle = nullptr;
    unsigned char *jpegBuf = nullptr;
    unsigned long jpegSize = 0;
    unsigned char *rgbBuf = nullptr;
    unsigned char *cropBuf = nullptr;
    int compressQuality = quality;
    int result = -1;
    int actual_crop_w = crop_w;
    int actual_crop_h = crop_h;
    int use_tj3 = 0;

    if (compressQuality < 1) compressQuality = 1;
    if (compressQuality > 100) compressQuality = 100;

    /* 验证裁剪区域并确定基准尺寸 */
    if (crop_w > 0 && crop_h > 0) {
        if (crop_x < 0 || crop_y < 0 || crop_x + crop_w > img_width ||
            crop_y + crop_h > img_height) {
            LOGE("Invalid crop region: %d,%d %dx%d (image: %dx%d)", crop_x, crop_y, crop_w, crop_h,
                 img_width, img_height);
            return -1;
        }
    } else {
        actual_crop_w = img_width;
        actual_crop_h = img_height;
    }

    if (out_width <= 0) out_width = actual_crop_w;
    if (out_height <= 0) out_height = actual_crop_h;

    /* 分配输出 RGB 缓冲区，16 字节对齐以利于 SIMD */
    size_t rgbBufSize = (size_t) out_width * (size_t) out_height * 3;
    if (posix_memalign((void **) &rgbBuf, 16, rgbBufSize) != 0) {
        /* 回退到普通 malloc */
        rgbBuf = (unsigned char *) malloc(rgbBufSize);
        if (!rgbBuf) {
            LOGE("Cannot allocate RGB buffer (%dx%d)", out_width, out_height);
            return -1;
        }
    }

    /* 解码 */
    if (crop_w > 0 && crop_h > 0) {
        /* 裁剪时使用 TJ3 API */
        handle = tj3Init(TJINIT_DECOMPRESS);
        use_tj3 = 1;
        if (!handle) {
            LOGE("tj3Init failed");
            goto cleanup;
        }
        /* 设置快速 DCT 模式 */
        tj3Set(handle, TJPARAM_FASTDCT, 1);

        if (tj3DecompressHeader(handle, srcBuf, srcSize) != 0) {
            LOGE("tj3DecompressHeader failed: %s", tj3GetErrorStr(handle));
            goto cleanup;
        }
        tjregion cropRegion = {crop_x, crop_y, crop_w, crop_h};
        if (tj3SetCroppingRegion(handle, cropRegion) != 0) {
            LOGE("tj3SetCroppingRegion failed: %s", tj3GetErrorStr(handle));
            goto cleanup;
        }

        if (out_width == actual_crop_w && out_height == actual_crop_h) {
            /* 不需要缩放 */
            if (tj3Decompress8(handle, srcBuf, srcSize, rgbBuf, out_width * 3, TJPF_RGB) != 0) {
                LOGE("tj3Decompress8 failed: %s", tj3GetErrorStr(handle));
                goto cleanup;
            }
        } else {
            /* 需要缩放 */
            size_t cropBufSize = (size_t) actual_crop_w * (size_t) actual_crop_h * 3;
            if (posix_memalign((void **) &cropBuf, 16, cropBufSize) != 0) {
                cropBuf = (unsigned char *) malloc(cropBufSize);
                if (!cropBuf) {
                    LOGE("Cannot allocate crop buffer");
                    goto cleanup;
                }
            }
            if (tj3Decompress8(handle, srcBuf, srcSize, cropBuf, actual_crop_w * 3, TJPF_RGB) !=
                0) {
                LOGE("tj3Decompress8 failed: %s", tj3GetErrorStr(handle));
                goto cleanup;
            }
            resize_rgb_nn(cropBuf, actual_crop_w, actual_crop_h, rgbBuf, out_width, out_height);
            free(cropBuf);
            cropBuf = nullptr;
        }
        tj3Destroy(handle);
        handle = nullptr;
    } else {
        /* 不裁剪，使用 TJ2 API（支持解码时缩放，更高效） */
        handle = tjInitDecompress();
        if (!handle) {
            LOGE("tjInitDecompress failed");
            goto cleanup;
        }
        if (tjDecompress2(handle, srcBuf, srcSize, rgbBuf, out_width, 0, out_height,
                          TJPF_RGB, TJFLAG_FASTDCT) != 0) {
            LOGE("tjDecompress2 failed: %s", tjGetErrorStr());
            goto cleanup;
        }
        tjDestroy(handle);
        handle = nullptr;
    }

    /* 压缩 */
    handle = tjInitCompress();
    if (!handle) {
        LOGE("tjInitCompress failed");
        goto cleanup;
    }

    /*
     * 压缩优化设置（速度优先，兼顾压缩率）：
     * - TJSAMP_420: 强制使用 4:2:0 色度子采样（最高压缩率，肉眼几乎无差别）
     * - TJFLAG_FASTDCT: 使用快速 DCT（比 ACCURATEDCT 快 2-3 倍，压缩率略降 2-5%）
     *
     * 注意：
     * - 忽略原图的 subsamp 参数，统一使用 4:2:0 以获得最佳压缩率
     * - 已移除 TJFLAG_PROGRESSIVE（渐进式 JPEG 会增加压缩时间）
     * - 对于大图片（如 6000x8000），速度优化更重要
     */
    if (tjCompress2(handle, rgbBuf, out_width, 0, out_height, TJPF_RGB,
                    &jpegBuf, &jpegSize, TJSAMP_420, compressQuality,
                    TJFLAG_FASTDCT) != 0) {
        LOGE("tjCompress2 failed: %s", tjGetErrorStr());
        goto cleanup;
    }

    /* 释放 RGB 缓冲区 */
    free(rgbBuf);
    rgbBuf = nullptr;

    /* 写入输出文件 */
    if (write_file(output_path, jpegBuf, jpegSize) != 0) goto cleanup;

    LOGI("Compression completed: %lu -> %lu bytes (q=%d, %dx%d)",
         srcSize, jpegSize, compressQuality, out_width, out_height);
    result = 0;

    cleanup:
    if (handle) {
        if (use_tj3) tj3Destroy(handle);
        else tjDestroy(handle);
    }
    if (jpegBuf) tjFree(jpegBuf);
    if (rgbBuf) free(rgbBuf);
    if (cropBuf) free(cropBuf);
    return result;
}

/* ==================== 公开 API ==================== */

int compress_jpeg(const char *input_path, const char *output_path, int quality) {
    return compress_jpeg_ex(input_path, output_path, quality, 0, 0, 0.0f, 0, 0, 0, 0, 0);
}

int compress_jpeg_ex(const char *input_path, const char *output_path, int quality,
                     int target_width, int target_height, float scale,
                     int crop_x, int crop_y, int crop_w, int crop_h,
                     int fallback_to_original) {

    unsigned char *srcBuf = nullptr;
    unsigned long srcSize = 0;
    tjhandle handle = nullptr;
    int width = 0, height = 0;
    int result = -1;
    int out_width = 0, out_height = 0;
    int jpegSubsamp = 0, jpegColorspace = 0;
    unsigned long pixels = 0;

    if (quality < 1) quality = 1;
    if (quality > 100) quality = 100;

    /* 只读取一次文件 */
    srcBuf = read_file(input_path, &srcSize);
    if (!srcBuf) {
        LOGE("Failed to read input file: %s", input_path);
        goto cleanup_ex;
    }

    /* 读取 JPEG 头获取尺寸和子采样 */
    handle = tjInitDecompress();
    if (!handle) {
        LOGE("tjInitDecompress() failed: %s", tjGetErrorStr());
        goto cleanup_ex;
    }

    if (tjDecompressHeader3(handle, srcBuf, srcSize, &width, &height,
                            &jpegSubsamp, &jpegColorspace) != 0) {
        LOGE("tjDecompressHeader3() failed: %s", tjGetErrorStr());
        goto cleanup_ex;
    }

    tjDestroy(handle);
    handle = nullptr;

    /* 尺寸与 OOM 检查 */
    pixels = (unsigned long) width * (unsigned long) height;
    if (pixels > MAX_PIXELS || width > MAX_DIMENSION || height > MAX_DIMENSION) {
        LOGE("Image too large: %dx%d", width, height);
        result = -2;
        goto cleanup_ex;
    }

    /* 计算输出尺寸 */
    {
        int base_w = width;
        int base_h = height;
        if (crop_w > 0 && crop_h > 0) {
            base_w = crop_w;
            base_h = crop_h;
        }

        if (scale > 0.0f && scale <= 1.0f) {
            out_width = static_cast<int>(base_w * scale);
            out_height = static_cast<int>(base_h * scale);
            if (out_width < 1) out_width = 1;
            if (out_height < 1) out_height = 1;
        } else if (target_width > 0 || target_height > 0) {
            if (target_width > 0 && target_height > 0) {
                out_width = target_width;
                out_height = target_height;
            } else if (target_width > 0) {
                out_width = target_width;
                out_height = (int) ((long) base_h * target_width / base_w);
                if (out_height < 1) out_height = 1;
            } else {
                out_height = target_height;
                out_width = (int) ((long) base_w * target_height / base_h);
                if (out_width < 1) out_width = 1;
            }
        } else {
            out_width = base_w;
            out_height = base_h;
        }
    }

    /* 非裁剪时使用 TurboJPEG 支持的缩放因子 */
    if (crop_w <= 0 || crop_h <= 0) {
        int nsf = 0;
        const tjscalingfactor *factors = tjGetScalingFactors(&nsf);
        if (factors && nsf > 0) {
            int best_w = 0, best_h = 0;
            for (int i = 0; i < nsf; i++) {
                int sw = TJSCALED(width, factors[i]);
                int sh = TJSCALED(height, factors[i]);
                if (sw <= out_width && sh <= out_height && sw >= best_w) {
                    best_w = sw;
                    best_h = sh;
                }
            }
            if (best_w > 0 && best_h > 0) {
                out_width = best_w;
                out_height = best_h;
            }
        }
    }

    /* 调用内部实现（传递已解析的信息，避免重复解析头部） */
    result = compress_jpeg_from_buffer(srcBuf, srcSize, width, height,
                                       output_path, quality,
                                       out_width, out_height,
                                       crop_x, crop_y, crop_w, crop_h);

    /* 压缩失败且启用 fallback 时，复制原图 */
    if (result != 0 && fallback_to_original) {
        LOGI("Compression failed (code=%d), fallback to copy original file", result);
        if (copy_file(input_path, output_path) == 0) {
            result = 1; /* 1 表示 fallback 成功 */
        }
    }

    cleanup_ex:
    if (srcBuf) free(srcBuf);
    if (handle) tjDestroy(handle);
    return result;
}

/*
 * 只读取 JPEG 头部获取尺寸（性能优化：不读取整个文件）
 * JPEG 头部信息通常在前 64KB 内
 */
int get_jpeg_dimensions(const char *path, int *width, int *height, unsigned long *file_size) {
    FILE *file = nullptr;
    unsigned char *headerBuf = nullptr;
    tjhandle handle = nullptr;
    int jpegSubsamp, jpegColorspace;
    int result = -1;
    const size_t HEADER_SIZE = 65536; /* 64KB 足够读取 JPEG 头 */

    file = fopen(path, "rb");
    if (!file) {
        LOGE("Cannot open file: %s", path);
        return -1;
    }

    /* 获取文件大小 */
    fseek(file, 0, SEEK_END);
    unsigned long fileLen = ftell(file);
    fseek(file, 0, SEEK_SET);

    if (file_size) {
        *file_size = fileLen;
    }

    /* 只读取头部（最多 64KB 或文件大小） */
    size_t readSize = (fileLen < HEADER_SIZE) ? fileLen : HEADER_SIZE;
    headerBuf = (unsigned char *) malloc(readSize);
    if (!headerBuf) {
        LOGE("Cannot allocate header buffer");
        fclose(file);
        return -1;
    }

    if (fread(headerBuf, 1, readSize, file) != readSize) {
        LOGE("Failed to read header: %s", path);
        free(headerBuf);
        fclose(file);
        return -1;
    }
    fclose(file);

    handle = tjInitDecompress();
    if (!handle) {
        LOGE("tjInitDecompress() failed: %s", tjGetErrorStr());
        free(headerBuf);
        return -1;
    }

    if (tjDecompressHeader3(handle, headerBuf, readSize, width, height,
                            &jpegSubsamp, &jpegColorspace) != 0) {
        LOGE("tjDecompressHeader3() failed: %s", tjGetErrorStr());
    } else {
        result = 0;
    }

    tjDestroy(handle);
    free(headerBuf);
    return result;
}