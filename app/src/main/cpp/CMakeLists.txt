# JPEG XL repacking for backups: libjxl, built from the pinned source that tools/fetch-deps.sh
# places in app/third_party/libjxl, statically linked into one small JNI library. The pins and
# checksums live in fetch-deps.sh; nothing here downloads anything.
cmake_minimum_required(VERSION 3.22)
project(limenjxl C CXX)
set(CMAKE_CXX_STANDARD 17)

set(LIBJXL_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../third_party/libjxl)
if(NOT EXISTS ${LIBJXL_DIR}/lib/include/jxl/encode.h)
    message(FATAL_ERROR "libjxl source is missing. Run: bash tools/fetch-deps.sh")
endif()

# FORCE, because libjxl declares each of these as a cache option of its own.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
foreach(off JPEGXL_ENABLE_TOOLS JPEGXL_ENABLE_DEVTOOLS JPEGXL_ENABLE_DOXYGEN JPEGXL_ENABLE_MANPAGES
        JPEGXL_ENABLE_BENCHMARK JPEGXL_ENABLE_EXAMPLES JPEGXL_ENABLE_JNI JPEGXL_ENABLE_SJPEG
        JPEGXL_ENABLE_OPENEXR JPEGXL_ENABLE_PLUGINS JPEGXL_ENABLE_VIEWERS JPEGXL_BUNDLE_LIBPNG
        JPEGXL_ENABLE_FUZZERS JPEGXL_ENABLE_COVERAGE JPEGXL_STATIC)
    set(${off} OFF CACHE BOOL "" FORCE)
endforeach()
foreach(on JPEGXL_ENABLE_SKCMS JPEGXL_ENABLE_TRANSCODE_JPEG JPEGXL_ENABLE_BOXES)
    set(${on} ON CACHE BOOL "" FORCE)
endforeach()

# One SIMD variant, the baseline every device of the ABI runs. The full set made the arm64
# library 2.6 times larger and repacked no faster on a Pixel 10. All four ABIs are built
# because the app already ships native code for all four, through a library it uses, and a
# device left without this one could not restore a backup. Highway's NEON on 32-bit ARM needs
# VFPv4, which every 32-bit ARM core able to run Android 10 has; libjxl's own
# JPEGXL_FORCE_NEON also asks for the hard-float ABI, which Android does not use.
foreach(target AVX2 AVX3 AVX3_DL AVX3_SPR AVX3_ZEN4 EMU128 NEON NEON_BF16 NEON_WITHOUT_AES PPC10 PPC8
        PPC9 RVV SCALAR SSE2 SSE4 SSSE3 SVE SVE_256 SVE2 SVE2_128 WASM WASM_EMU256 Z14 Z15)
    set(JPEGXL_ENABLE_HWY_${target} OFF CACHE BOOL "" FORCE)
endforeach()
if(ANDROID_ABI MATCHES "^x86")
    set(JPEGXL_ENABLE_HWY_SSE2 ON CACHE BOOL "" FORCE)
else()
    set(JPEGXL_ENABLE_HWY_NEON_WITHOUT_AES ON CACHE BOOL "" FORCE)
endif()
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    add_compile_options(-mfpu=neon-vfpv4)
endif()

add_subdirectory(${LIBJXL_DIR} libjxl EXCLUDE_FROM_ALL)

add_library(limenjxl SHARED jpeg_xl.cpp)
target_link_libraries(limenjxl PRIVATE jxl)
