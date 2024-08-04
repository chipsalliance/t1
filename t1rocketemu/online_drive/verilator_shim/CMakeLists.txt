cmake_minimum_required(VERSION 3.20)
project(verilator_shim)
set(CMAKE_CXX_STANDARD 17)

message(STATUS "Project '${PROJECT_NAME}' build type: ${CMAKE_BUILD_TYPE}")

set(THREADS_PREFER_PTHREAD_FLAG ON)

add_library(verilator_shim
  STATIC
  verilator_shim.cc
)

if (NOT DEFINED VERILATED_LIB_DIR)
  set(VERILATED_LIB_DIR "$ENV{VERILATED_LIB_DIR}")
  if (VERILATED_LIB_DIR STREQUAL "")
    message(FATAL_ERROR "You should specify verilated libs via -DVERILATE_LIB_DIR or environment variable VERILATED_LIB_DIR, but it seems not")
  endif()
endif()

if (NOT DEFINED VERILATED_INC_DIR)
  set(VERILATED_INC_DIR "$ENV{VERILATED_INC_DIR}")
  if (VERILATED_INC_DIR STREQUAL "")
    message(FATAL_ERROR "You should specify verilated libs via -DVERILATED_INC_DIR or environment variable VERILATED_INC_DIR, but it seems not")
  endif()
endif()

# include verilator headers
find_package(verilator REQUIRED)
message(STATUS "Found verilator: ${verilator_DIR}")
target_include_directories(verilator_shim PUBLIC ${verilator_DIR}/include)
target_include_directories(verilator_shim PUBLIC ${verilator_DIR}/include/vltstd)

if(DEFINED VM_TRACE)
  target_compile_definitions(verilator_shim PRIVATE VM_TRACE=1)
endif()

install(TARGETS verilator_shim ARCHIVE)
