cmake_minimum_required(VERSION 3.22.1)
project(hextrak CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(hextrak SHARED
        jni_bridge.cpp
        engine.cpp
        voice.cpp
        dsp.cpp)

target_compile_options(hextrak PRIVATE -O3 -ffast-math -Wall -Wno-unused-parameter)
target_link_libraries(hextrak aaudio log)
