cmake_minimum_required(VERSION 3.20)
project(dpi_c)
set(CMAKE_CXX_STANDARD 17)

message(STATUS "Project '${PROJECT_NAME}' build type: ${CMAKE_BUILD_TYPE}")

set(THREADS_PREFER_PTHREAD_FLAG ON)

add_library(dpi_c
  STATIC
  verilator_main.cpp
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

# link against verilated libs
target_include_directories(dpi_c PUBLIC ${VERILATED_INC_DIR})
target_include_directories(dpi_c PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
file(GLOB_RECURSE VERILATED_LIBS "${VERILATED_LIB_DIR}/*.a")
target_link_libraries(dpi_c PUBLIC ${VERILATED_LIBS})

find_package(verilator REQUIRED)
message(STATUS "Found verilator: ${verilator_DIR}, link with libs ${VERILATED_LIBS}")
target_include_directories(dpi_c PUBLIC ${verilator_DIR}/include)
target_include_directories(dpi_c PUBLIC ${verilator_DIR}/include/vltstd)

target_link_libraries(dpi_c PUBLIC ${CMAKE_THREAD_LIBS_INIT})

target_compile_definitions(dpi_c PRIVATE COSIM_VERILATOR)

install(TARGETS dpi_c ARCHIVE)
