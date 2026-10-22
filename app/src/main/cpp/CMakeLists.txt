# On-device speech-to-text for the WAEX app process (never loaded into WhatsApp).
# whisper.cpp is fetched at a pinned commit instead of being vendored.
cmake_minimum_required(VERSION 3.22.1)
project(waexwhisper C CXX)

include(FetchContent)
FetchContent_Declare(
    whisper
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG        v1.9.5 # commit d1be6fde11ac6e0407606b4e42fe72d34add8037
    GIT_SHALLOW    TRUE
)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
# Phones since ~2018 (Cortex-A55/A75 and newer) have dot-product and fp16 arithmetic,
# which make quantized models 2-3x faster. The app checks /proc/cpuinfo before loading this.
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
FetchContent_MakeAvailable(whisper)

add_library(waexwhisper SHARED waex_whisper.c)
target_link_libraries(waexwhisper PRIVATE whisper log)
target_compile_options(waexwhisper PRIVATE -O3)
