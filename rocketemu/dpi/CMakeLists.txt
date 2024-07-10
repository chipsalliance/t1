cmake_minimum_required(VERSION 3.20)
project(rocket_dpi_c)
set(CMAKE_CXX_STANDARD 17)

message(STATUS "Project '${PROJECT_NAME}' build type: ${CMAKE_BUILD_TYPE}")

set(THREADS_PREFER_PTHREAD_FLAG ON)

add_library(dpi
  STATIC
  dpi.cc
)

add_library(dpi_pre_link
  STATIC
  dpi_pre_link.cc
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
  message("Using VERILATED_INC_DIR ${VERILATED_INC_DIR}")
endif()

# include verilated headers
target_include_directories(dpi PUBLIC ${VERILATED_INC_DIR})
target_include_directories(dpi PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_include_directories(dpi_pre_link PUBLIC ${VERILATED_INC_DIR})
target_include_directories(dpi_pre_link PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

# include verilator headers
find_package(verilator REQUIRED)
message(STATUS "Found verilator: ${verilator_DIR}")
target_include_directories(dpi PUBLIC ${verilator_DIR}/include)
target_include_directories(dpi PUBLIC ${verilator_DIR}/include/vltstd)
target_include_directories(dpi_pre_link PUBLIC ${verilator_DIR}/include)
target_include_directories(dpi_pre_link PUBLIC ${verilator_DIR}/include/vltstd)

install(TARGETS dpi dpi_pre_link ARCHIVE)
