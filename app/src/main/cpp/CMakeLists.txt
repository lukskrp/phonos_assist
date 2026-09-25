cmake_minimum_required(VERSION 3.14)

project(phonosassist_tts LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_POSITION_INDEPENDENT_CODE ON CACHE BOOL "" FORCE)

# 16 KB page-size support (Android 15+): align ELF LOAD segments of the shared
# library and the espeak-ng sub-builds so they load on both 4 KB and 16 KB kernels.
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

# ── espeak-ng (Piper TTS phonemization) ───────────────────────────────────
# espeak-ng's own CMake compiles espeak-ng-data by RUNNING the host binary, which
# is impossible when cross-compiling to Android. We build only the library and
# point it at the pre-generated espeak-ng-data bundled in assets at runtime
# (see tts_jni.cpp -> espeak_Initialize(dataPath)).

set(USE_MBROLA OFF CACHE BOOL "" FORCE)
set(USE_LIBSONIC OFF CACHE BOOL "" FORCE)
set(USE_LIBPCAUDIO OFF CACHE BOOL "" FORCE)
set(USE_KLATT OFF CACHE BOOL "" FORCE)
set(USE_SPEECHPLAYER OFF CACHE BOOL "" FORCE)
set(USE_ASYNC OFF CACHE BOOL "" FORCE)

# espeak-ng's sub-builds expect this interface target for shared includes.
add_library(espeak-include INTERFACE)
target_include_directories(espeak-include INTERFACE
    espeak-ng/src/include
    espeak-ng/src/include/compat)

add_subdirectory(espeak-ng/src/ucd-tools)
add_subdirectory(espeak-ng/src/libespeak-ng)

add_library(phonosassist_tts SHARED tts_jni.cpp)

target_include_directories(phonosassist_tts PRIVATE
    espeak-ng/src/include
    espeak-ng/src/include/compat)

target_link_libraries(phonosassist_tts PRIVATE espeak-ng espeak-ng-config ucd android log)

target_compile_features(phonosassist_tts PRIVATE cxx_std_17)
