cmake_minimum_required(VERSION 3.16)
project(ai_electronics_runtime_core LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(ai_electronics_runtime
    src/Manifest.cpp
    src/Expression.cpp
    src/RuntimeCore.cpp
    src/Protocol.cpp
    src/BlePacket.cpp
    src/BleRuntimeBridge.cpp
)

target_include_directories(ai_electronics_runtime PUBLIC include)

enable_testing()

add_executable(ai_electronics_runtime_tests
    tests/RuntimeCoreTests.cpp
)
target_link_libraries(ai_electronics_runtime_tests PRIVATE ai_electronics_runtime)

add_test(NAME ai_electronics_runtime_tests COMMAND ai_electronics_runtime_tests)
