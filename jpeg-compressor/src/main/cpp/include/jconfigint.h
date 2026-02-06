/* Minimal jconfigint.h for Android - no SIMD */
#define BUILD "1"
#define HIDDEN __attribute__((visibility("hidden")))
#undef inline
#define INLINE inline
#define THREAD_LOCAL __thread
#define PACKAGE_NAME "libjpeg-turbo"
#define VERSION "3.1.3"
#define SIZEOF_SIZE_T 8

#if defined(__has_attribute) && __has_attribute(fallthrough)
#define FALLTHROUGH __attribute__((fallthrough));
#else
#define FALLTHROUGH
#endif

#ifndef BITS_IN_JSAMPLE
#define BITS_IN_JSAMPLE 8
#endif

#define C_ARITH_CODING_SUPPORTED 1
#define D_ARITH_CODING_SUPPORTED 1
#undef WITH_SIMD
