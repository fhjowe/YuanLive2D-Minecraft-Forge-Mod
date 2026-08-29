cmake_minimum_required(VERSION 3.16)
project(yuan_live2d LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>DLL")

find_package(JNI REQUIRED)
find_package(OpenGL REQUIRED)

set(CORE_PATH "${CUBISM_SDK_ROOT}/Core")
set(FRAMEWORK_PATH "${CUBISM_SDK_ROOT}/Framework")
set(PATCHED_FRAMEWORK_PATH "${CMAKE_BINARY_DIR}/cubism-sdk/Framework")
set(GLEW_PATH "${GLEW_ROOT}")

execute_process(
    COMMAND pwsh -NoProfile -File "${CMAKE_CURRENT_SOURCE_DIR}/patch-framework.ps1"
            -Source "${FRAMEWORK_PATH}" -Destination "${PATCHED_FRAMEWORK_PATH}"
    RESULT_VARIABLE PATCH_FRAMEWORK_RESULT)
if(NOT PATCH_FRAMEWORK_RESULT EQUAL 0)
    message(FATAL_ERROR "Failed to generate core-profile Cubism Framework sources")
endif()

add_library(Live2DCubismCore SHARED IMPORTED)
set_target_properties(Live2DCubismCore PROPERTIES
    IMPORTED_IMPLIB "${CORE_PATH}/dll/windows/x86_64/Live2DCubismCore.lib"
    IMPORTED_LOCATION "${CORE_PATH}/dll/windows/x86_64/Live2DCubismCore.dll"
    INTERFACE_INCLUDE_DIRECTORIES "${CORE_PATH}/include")

set(FRAMEWORK_SOURCE OpenGL)
add_subdirectory("${GLEW_PATH}/build/cmake" "${CMAKE_BINARY_DIR}/glew")
add_subdirectory("${PATCHED_FRAMEWORK_PATH}" "${CMAKE_BINARY_DIR}/Framework")
target_compile_definitions(Framework PUBLIC CSM_TARGET_WIN_GL GLEW_STATIC)
target_compile_options(Framework PRIVATE /utf-8)
target_include_directories(Framework PUBLIC "${GLEW_PATH}/include")
target_link_libraries(Framework PUBLIC Live2DCubismCore glew_s)

add_library(yuan_live2d SHARED
    src/YuanLive2DBridge.cpp
    src/YuanLive2DGlState.cpp
    src/YuanLive2DModel.cpp
    src/YuanLive2DNativeSupport.cpp
    src/stb_image_impl.cpp)
target_include_directories(yuan_live2d PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/src"
    "${CUBISM_SDK_ROOT}/Samples/OpenGL/thirdParty/stb")
target_link_libraries(yuan_live2d PRIVATE JNI::JNI Framework OpenGL::GL)
target_compile_options(yuan_live2d PRIVATE /MP /utf-8)

add_executable(yuan_live2d_smoke
    tests/YuanLive2DNativeSmoke.cpp
    src/YuanLive2DGlState.cpp
    src/YuanLive2DModel.cpp
    src/YuanLive2DNativeSupport.cpp
    src/stb_image_impl.cpp)
target_include_directories(yuan_live2d_smoke PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/src"
    "${PATCHED_FRAMEWORK_PATH}/src"
    "${CORE_PATH}/include"
    "${CUBISM_SDK_ROOT}/Samples/OpenGL/thirdParty/stb")
target_link_libraries(yuan_live2d_smoke PRIVATE Framework OpenGL::GL)
target_compile_options(yuan_live2d_smoke PRIVATE /utf-8)
add_custom_command(TARGET yuan_live2d_smoke POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
            "$<TARGET_FILE:Live2DCubismCore>" "$<TARGET_FILE_DIR:yuan_live2d_smoke>")
enable_testing()
add_test(NAME yuan_live2d_smoke COMMAND yuan_live2d_smoke "${CUBISM_SDK_ROOT}")
