cmake_minimum_required(VERSION 3.6)
project("edgeviewer")

set(OpenCV_DIR "${CMAKE_SOURCE_DIR}/../opencv/sdk/native/jni" CACHE PATH "OpenCV dir") # change if necessary
find_package(OpenCV REQUIRED)

add_library(native-lib SHARED native-lib.cpp opencv_processing.cpp)
include_directories(${OpenCV_INCLUDE_DIRS})
target_link_libraries(native-lib ${OpenCV_LIBS} log jnigraphics)
