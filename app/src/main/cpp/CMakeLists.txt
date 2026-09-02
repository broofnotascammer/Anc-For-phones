cmake_minimum_required(VERSION 3.22.1)
project("oboe_anc_engine" CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(oboe_anc_engine SHARED
    jni_bridge.cpp
    oboe_engine.cpp
    dsp_fxlms.cpp
)

target_include_directories(oboe_anc_engine PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(oboe_anc_engine
    oboe::oboe
    log
    android
)
