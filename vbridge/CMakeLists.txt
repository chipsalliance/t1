cmake_minimum_required(VERSION 3.20)
project(emulator)
set(SPIKE_DIR ../dependencies/spike)
set(SPIKE_BUILD_DIR "${SPIKE_DIR}/build")
find_package(verilator)
set(CMAKE_C_COMPILER "clang")
set(CMAKE_CXX_COMPILER "clang++")
find_package(Threads)

add_executable(vbridge vbridge.cc "${SPIKE_BUILD_DIR}/spike_main.o")
target_include_directories(vbridge PRIVATE
    "${SPIKE_DIR}/riscv"
    "${SPIKE_DIR}/fesvr"
    "${SPIKE_DIR}/softfloat"
    "${SPIKE_BUILD_DIR}"
)

verilate(vbridge
  SOURCES ../builds/V.v
)
