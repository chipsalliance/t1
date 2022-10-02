cmake_minimum_required(VERSION 3.20)
project(emulator)

set(VECTOR_ROOT ../../..)
cmake_path(ABSOLUTE_PATH VECTOR_ROOT)
set(SPIKE_DIR "${VECTOR_ROOT}/dependencies/spike")
set(SPIKE_BUILD_DIR "${SPIKE_DIR}/build")

#add_compile_options(-fsanitize=address)
#add_link_options(-fsanitize=address)

find_package(verilator)
find_package(Threads)

add_executable(vbridge vbridge.cc main.cc)
target_include_directories(vbridge PRIVATE
    "${SPIKE_DIR}/riscv"
    "${SPIKE_DIR}/fesvr"
    "${SPIKE_DIR}/softfloat"
    "${SPIKE_BUILD_DIR}"
)

include(FetchContent)
FetchContent_Declare(fmt GIT_REPOSITORY https://github.com/fmtlib/fmt GIT_TAG 8.0.1)
FetchContent_Declare(glog GIT_REPOSITORY https://github.com/google/glog GIT_TAG v0.5.0)
FetchContent_MakeAvailable(fmt glog)
target_link_libraries(vbridge fmt glog)

verilate(vbridge
    SOURCES "${VECTOR_ROOT}/builds/V.v"
    TRACE
)

# importing spike libraries
set(spike_ar_name fdt fesvr softfloat disasm riscv)
foreach(spike_ar ${spike_ar_name})
    list(APPEND spike_files "${SPIKE_BUILD_DIR}/lib${spike_ar}.a")
endforeach()
add_custom_command(
    OUTPUT libspike.o
    COMMAND ld -r -o libspike.o --whole-archive ${spike_files}
    DEPENDS ${spike_files}
)
add_custom_target(libspike_target DEPENDS libspike.o)
add_library(libspike STATIC IMPORTED GLOBAL)
set_target_properties(libspike
    PROPERTIES
    IMPORTED_LOCATION libspike.o
)
target_link_libraries(vbridge libspike)
