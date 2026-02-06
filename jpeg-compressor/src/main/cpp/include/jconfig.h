/* Minimal jconfig.h for Android build - SIMD disabled */
#define JPEG_LIB_VERSION 80
#define LIBJPEG_TURBO_VERSION "3.1.3"
#define LIBJPEG_TURBO_VERSION_NUMBER 3010003
#define MEM_SRCDST_SUPPORTED 1
/* WITH_SIMD intentionally not defined - use C fallbacks */
#ifndef BITS_IN_JSAMPLE
#define BITS_IN_JSAMPLE 8
#endif
