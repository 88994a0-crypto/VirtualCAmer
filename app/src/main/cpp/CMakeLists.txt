cmake_minimum_required(VERSION 3.22.1)

project("virtualcamera")

add_library(
    virtualcamera
    SHARED
    virtualcamera.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    virtualcamera
    ${log-lib}
)
