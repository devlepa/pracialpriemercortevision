cmake_minimum_required(VERSION 3.22.1)
project("parcial")

set(OPENCV_NATIVE_DIR "C:/Users/Devlepa/OneDrive/Escritorio/OpenCV-android-sdk/sdk/native")

add_library(imageproc SHARED native-lib.cpp)

include_directories(${OPENCV_NATIVE_DIR}/jni/include)

add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES
        IMPORTED_LOCATION
        ${OPENCV_NATIVE_DIR}/libs/${ANDROID_ABI}/libopencv_java4.so
        # Si en tu zip está en staticlibs: cambia 'libs' por 'staticlibs'
)

find_library(log-lib log)

target_link_libraries(imageproc
        opencv_java4
        android
        ${log-lib})

add_link_options(-Wl,-z,max-page-size=16384)