# Minimal CMake for native "Hello from C++" JNI

cmake_minimum_required(VERSION 3.22.1)

project("edgeviewer")

# Build native-lib.so from native-lib.cpp
add_library(
        native-lib
        SHARED
        native-lib.cpp
)

# Find Android log library
find_library(
        log-lib
        log
)

# Link libraries
target_link_libraries(
        native-lib
        ${log-lib}
        android
)
