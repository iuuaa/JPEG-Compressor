#ifndef JPEG_COMPRESSOR_H
#define JPEG_COMPRESSOR_H

#ifdef __cplusplus
extern "C" {
#endif

// 压缩 JPEG 图片（不缩放，等价于 compress_jpeg_ex(..., 0, 0, 0)）
// 返回: 0 成功，-1 一般失败，-2 图片尺寸过大（存在 OOM 风险）
int compress_jpeg(const char *input_path, const char *output_path, int quality);

// 获取 JPEG 图片尺寸；可选返回文件大小（通过 libjpeg-turbo 读头 + 同次读入的文件长度）
// path: 图片路径
// width: 输出宽度
// height: 输出高度
// file_size: 可选，输出文件大小（字节），可为 NULL
// 返回: 0 成功，-1 失败
int get_jpeg_dimensions(const char *path, int *width, int *height, unsigned long *file_size);

// 压缩 JPEG，可选缩放到目标宽高或比例（0 表示保持原尺寸）
// target_width/target_height: 目标宽高，任一为 0 表示不按尺寸缩放
// scale: 缩放比例 (0 < scale <= 1)，非 0 时优先于 target_*，输出尺寸为 floor(width*scale) x floor(height*scale)，再取库支持的缩放因子
// crop_x/crop_y/crop_w/crop_h: 裁剪区域（相对于原始图片），全为 0 表示不裁剪；裁剪在解码时进行（高效）
// fallback_to_original: 非 0 时，压缩失败则直接复制原文件到输出路径（返回 1 表示使用了 fallback）
// 返回: 0 成功，1 fallback 成功（复制了原图），-1 失败，-2 图片过大
int compress_jpeg_ex(const char *input_path, const char *output_path, int quality,
                     int target_width, int target_height, float scale,
                     int crop_x, int crop_y, int crop_w, int crop_h,
                     int fallback_to_original);

#ifdef __cplusplus
}
#endif

#endif // JPEG_COMPRESSOR_H
