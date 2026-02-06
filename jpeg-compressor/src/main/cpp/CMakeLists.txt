# libjpeg-turbo 3.x with NEON SIMD acceleration for ARM
cmake_minimum_required(VERSION 4.1.2)
project("jpegcompressor")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LIBJPEG_TURBO_DIR ${CMAKE_CURRENT_SOURCE_DIR}/libjpeg-turbo)
set(LIBJPEG_TURBO_SRC ${LIBJPEG_TURBO_DIR}/src)
set(WRAPPER_DIR ${LIBJPEG_TURBO_SRC}/wrapper)
set(SIMD_DIR ${LIBJPEG_TURBO_DIR}/simd)

include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${LIBJPEG_TURBO_SRC}
        ${SIMD_DIR}
)

# libjpeg-turbo 3.x 完整源文件列表
set(JPEG_SOURCES
        ${LIBJPEG_TURBO_SRC}/jcapimin.c
        ${WRAPPER_DIR}/jcapistd-8.c ${WRAPPER_DIR}/jcapistd-12.c ${WRAPPER_DIR}/jcapistd-16.c
        ${WRAPPER_DIR}/jccoefct-8.c ${WRAPPER_DIR}/jccoefct-12.c
        ${WRAPPER_DIR}/jccolor-8.c ${WRAPPER_DIR}/jccolor-12.c ${WRAPPER_DIR}/jccolor-16.c
        ${WRAPPER_DIR}/jcdctmgr-8.c ${WRAPPER_DIR}/jcdctmgr-12.c
        ${WRAPPER_DIR}/jcdiffct-8.c ${WRAPPER_DIR}/jcdiffct-12.c ${WRAPPER_DIR}/jcdiffct-16.c
        ${LIBJPEG_TURBO_SRC}/jchuff.c ${LIBJPEG_TURBO_SRC}/jcicc.c ${LIBJPEG_TURBO_SRC}/jcinit.c
        ${LIBJPEG_TURBO_SRC}/jclhuff.c
        ${WRAPPER_DIR}/jclossls-8.c ${WRAPPER_DIR}/jclossls-12.c ${WRAPPER_DIR}/jclossls-16.c
        ${WRAPPER_DIR}/jcmainct-8.c ${WRAPPER_DIR}/jcmainct-12.c ${WRAPPER_DIR}/jcmainct-16.c
        ${LIBJPEG_TURBO_SRC}/jcmarker.c ${LIBJPEG_TURBO_SRC}/jcmaster.c ${LIBJPEG_TURBO_SRC}/jcomapi.c
        ${LIBJPEG_TURBO_SRC}/jcparam.c ${LIBJPEG_TURBO_SRC}/jcphuff.c
        ${WRAPPER_DIR}/jcprepct-8.c ${WRAPPER_DIR}/jcprepct-12.c ${WRAPPER_DIR}/jcprepct-16.c
        ${WRAPPER_DIR}/jcsample-8.c ${WRAPPER_DIR}/jcsample-12.c ${WRAPPER_DIR}/jcsample-16.c
        ${LIBJPEG_TURBO_SRC}/jctrans.c ${LIBJPEG_TURBO_SRC}/jdapimin.c
        ${WRAPPER_DIR}/jdapistd-8.c ${WRAPPER_DIR}/jdapistd-12.c ${WRAPPER_DIR}/jdapistd-16.c
        ${LIBJPEG_TURBO_SRC}/jdatadst.c ${LIBJPEG_TURBO_SRC}/jdatasrc.c
        ${WRAPPER_DIR}/jdcoefct-8.c ${WRAPPER_DIR}/jdcoefct-12.c
        ${WRAPPER_DIR}/jdcolor-8.c ${WRAPPER_DIR}/jdcolor-12.c ${WRAPPER_DIR}/jdcolor-16.c
        ${WRAPPER_DIR}/jddctmgr-8.c ${WRAPPER_DIR}/jddctmgr-12.c
        ${WRAPPER_DIR}/jddiffct-8.c ${WRAPPER_DIR}/jddiffct-12.c ${WRAPPER_DIR}/jddiffct-16.c
        ${LIBJPEG_TURBO_SRC}/jdhuff.c ${LIBJPEG_TURBO_SRC}/jdicc.c ${LIBJPEG_TURBO_SRC}/jdinput.c
        ${LIBJPEG_TURBO_SRC}/jdlhuff.c
        ${WRAPPER_DIR}/jdlossls-8.c ${WRAPPER_DIR}/jdlossls-12.c ${WRAPPER_DIR}/jdlossls-16.c
        ${WRAPPER_DIR}/jdmainct-8.c ${WRAPPER_DIR}/jdmainct-12.c ${WRAPPER_DIR}/jdmainct-16.c
        ${LIBJPEG_TURBO_SRC}/jdmarker.c ${LIBJPEG_TURBO_SRC}/jdmaster.c
        ${WRAPPER_DIR}/jdmerge-8.c ${WRAPPER_DIR}/jdmerge-12.c
        ${LIBJPEG_TURBO_SRC}/jdphuff.c
        ${WRAPPER_DIR}/jdpostct-8.c ${WRAPPER_DIR}/jdpostct-12.c ${WRAPPER_DIR}/jdpostct-16.c
        ${WRAPPER_DIR}/jdsample-8.c ${WRAPPER_DIR}/jdsample-12.c ${WRAPPER_DIR}/jdsample-16.c
        ${LIBJPEG_TURBO_SRC}/jdtrans.c ${LIBJPEG_TURBO_SRC}/jerror.c ${LIBJPEG_TURBO_SRC}/jfdctflt.c
        ${WRAPPER_DIR}/jfdctfst-8.c ${WRAPPER_DIR}/jfdctfst-12.c
        ${WRAPPER_DIR}/jfdctint-8.c ${WRAPPER_DIR}/jfdctint-12.c
        ${WRAPPER_DIR}/jidctflt-8.c ${WRAPPER_DIR}/jidctflt-12.c
        ${WRAPPER_DIR}/jidctfst-8.c ${WRAPPER_DIR}/jidctfst-12.c
        ${WRAPPER_DIR}/jidctint-8.c ${WRAPPER_DIR}/jidctint-12.c
        ${WRAPPER_DIR}/jidctred-8.c ${WRAPPER_DIR}/jidctred-12.c
        ${LIBJPEG_TURBO_SRC}/jmemmgr.c ${LIBJPEG_TURBO_SRC}/jmemnobs.c
        ${LIBJPEG_TURBO_SRC}/jpeg_nbits.c
        ${WRAPPER_DIR}/jquant1-8.c ${WRAPPER_DIR}/jquant1-12.c
        ${WRAPPER_DIR}/jquant2-8.c ${WRAPPER_DIR}/jquant2-12.c
        ${WRAPPER_DIR}/jutils-8.c ${WRAPPER_DIR}/jutils-12.c ${WRAPPER_DIR}/jutils-16.c
        ${LIBJPEG_TURBO_SRC}/jaricom.c
        ${LIBJPEG_TURBO_SRC}/jcarith.c
        ${LIBJPEG_TURBO_SRC}/jdarith.c
)

# TurboJPEG 源文件
set(TURBOJPEG_SOURCES
        ${JPEG_SOURCES}
        ${LIBJPEG_TURBO_SRC}/turbojpeg.c
        ${LIBJPEG_TURBO_SRC}/transupp.c
        ${LIBJPEG_TURBO_SRC}/jdatadst-tj.c
        ${LIBJPEG_TURBO_SRC}/jdatasrc-tj.c
        ${LIBJPEG_TURBO_SRC}/rdbmp.c
        ${WRAPPER_DIR}/rdppm-8.c ${WRAPPER_DIR}/rdppm-12.c ${WRAPPER_DIR}/rdppm-16.c
        ${LIBJPEG_TURBO_SRC}/wrbmp.c
        ${WRAPPER_DIR}/wrppm-8.c ${WRAPPER_DIR}/wrppm-12.c ${WRAPPER_DIR}/wrppm-16.c
)

# NEON SIMD 源文件（ARM 通用）
set(SIMD_ARM_COMMON
        ${SIMD_DIR}/arm/jccolor-neon.c
        ${SIMD_DIR}/arm/jcgray-neon.c
        ${SIMD_DIR}/arm/jcphuff-neon.c
        ${SIMD_DIR}/arm/jcsample-neon.c
        ${SIMD_DIR}/arm/jdcolor-neon.c
        ${SIMD_DIR}/arm/jdmerge-neon.c
        ${SIMD_DIR}/arm/jdsample-neon.c
        ${SIMD_DIR}/arm/jfdctfst-neon.c
        ${SIMD_DIR}/arm/jfdctint-neon.c
        ${SIMD_DIR}/arm/jidctfst-neon.c
        ${SIMD_DIR}/arm/jidctint-neon.c
        ${SIMD_DIR}/arm/jidctred-neon.c
        ${SIMD_DIR}/arm/jquanti-neon.c
)

# 根据目标架构选择 SIMD 源文件
if(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    message(STATUS "Building for arm64-v8a with NEON SIMD")
    set(SIMD_SOURCES
            ${SIMD_ARM_COMMON}
            ${SIMD_DIR}/arm/aarch64/jsimd.c
            ${SIMD_DIR}/arm/aarch64/jsimd_neon.S
            ${SIMD_DIR}/arm/aarch64/jchuff-neon.c
    )
    # ARM64 NEON 编译标志
    set(SIMD_FLAGS "-DNEON_INTRINSICS")
    include_directories(${SIMD_DIR}/arm ${SIMD_DIR}/arm/aarch64)

elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
    message(STATUS "Building for armeabi-v7a with NEON SIMD")
    set(SIMD_SOURCES
            ${SIMD_ARM_COMMON}
            ${SIMD_DIR}/arm/aarch32/jsimd.c
            ${SIMD_DIR}/arm/aarch32/jsimd_neon.S
            ${SIMD_DIR}/arm/aarch32/jchuff-neon.c
    )
    # ARM32 NEON 编译标志
    set(SIMD_FLAGS "-DNEON_INTRINSICS -mfpu=neon")
    include_directories(${SIMD_DIR}/arm ${SIMD_DIR}/arm/aarch32)

elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "x86_64" OR CMAKE_ANDROID_ARCH_ABI STREQUAL "x86")
    # x86/x86_64 暂不启用 SIMD（需要 NASM）
    message(STATUS "Building for ${CMAKE_ANDROID_ARCH_ABI} without SIMD (requires NASM)")
    set(SIMD_SOURCES ${LIBJPEG_TURBO_SRC}/jsimd_none.c)
    set(SIMD_FLAGS "")

else()
    message(STATUS "Building for ${CMAKE_ANDROID_ARCH_ABI} without SIMD")
    set(SIMD_SOURCES ${LIBJPEG_TURBO_SRC}/jsimd_none.c)
    set(SIMD_FLAGS "")
endif()

add_library(jpegcompressor SHARED
        jpeg_compressor_jni.cpp
        jpeg_compressor.cpp
        ${TURBOJPEG_SOURCES}
        ${SIMD_SOURCES}
)

target_compile_definitions(jpegcompressor PRIVATE BMP_SUPPORTED PPM_SUPPORTED)

# 不把警告当错误，并抑制 libjpeg-turbo（如 jchuff.c）的 shift-count-overflow 警告，避免构建失败
target_compile_options(jpegcompressor PRIVATE -Wno-error -Wno-shift-count-overflow)

# NDK r29 / CMake 4.1：Release 使用 -O3 以充分利用新版 Clang 的优化
if(CMAKE_BUILD_TYPE STREQUAL "Release" OR CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo")
    target_compile_options(jpegcompressor PRIVATE -O3)
endif()

if(SIMD_FLAGS)
    # 启用 SIMD 时需要定义 WITH_SIMD，否则 SIMD 文件无法正确编译
    target_compile_definitions(jpegcompressor PRIVATE WITH_SIMD)
    target_compile_options(jpegcompressor PRIVATE ${SIMD_FLAGS})
endif()

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(jpegcompressor ${log-lib} ${android-lib})