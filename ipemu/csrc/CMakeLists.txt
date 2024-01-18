cmake_minimum_required(VERSION 3.20)
project(emulator)
set(CMAKE_CXX_STANDARD 17)

message(STATUS "Project '${PROJECT_NAME}' build type: ${CMAKE_BUILD_TYPE}")

find_package(args REQUIRED)
find_package(spdlog REQUIRED)
find_package(fmt REQUIRED)
find_package(libspike REQUIRED)
find_package(verilator REQUIRED)
find_package(nlohmann_json REQUIRED)
find_package(Threads REQUIRED)
set(THREADS_PREFER_PTHREAD_FLAG ON)

file(GLOB DRAMSIM3_SRCS ${CMAKE_SOURCE_DIR}/dramsim3/src/*.c)

add_executable(emulator
  spike_event.cc
  spdlog-ext.cc
  vbridge_impl.cc
  dpi.cc
  elf.cc
  rtl_config.cc
  ${DRAMSIM3_SRCS}
)

option(VERILATE_TRACE "enable verilator trace" OFF)

target_include_directories(emulator PRIVATE ${CMAKE_SOURCE_DIR}/dramsim3/src)

target_link_libraries(emulator PUBLIC ${CMAKE_THREAD_LIBS_INIT})
target_link_libraries(emulator PUBLIC libspike fmt::fmt spdlog::spdlog nlohmann_json::nlohmann_json)
target_compile_definitions(emulator PRIVATE COSIM_VERILATOR)

# read VERILATE_SRC_DIR from args, else read from environment
if (NOT DEFINED VERILATE_SRC_DIR)
  set(VERILATE_SRC_DIR "$ENV{VERILATE_SRC_DIR}")
  if (VERILATE_SRC_DIR STREQUAL "")
    message(FATAL_ERROR "You should specify rtl directory via -DVERILATE_SRC_DIR or environment variable EMULATOR_RTL_DIR, but it seems not")
  endif()
endif()

# parse filelist.f into VERILATE_SRC_LIST
file(READ "${VERILATE_SRC_DIR}/filelist.f" filelist_content)
string(REPLACE "\n" ";" VERILATE_SRC_LIST "${filelist_content}")  # transform lines into a list
list(FILTER VERILATE_SRC_LIST EXCLUDE REGEX "^$") # filter out empty lines
list(TRANSFORM VERILATE_SRC_LIST PREPEND "${VERILATE_SRC_DIR}/") # add base directory prefix

set(VERILATE_ARGS
  TOP_MODULE TestBench
  PREFIX VTestBench
  OPT_FAST
  THREADS 8
  VERILATOR_ARGS --x-initial unique --output-split 2048 --max-num-width 1048576 --threads-max-mtasks 100000 --main --timing --coverage-user --assert
)

if (${VERILATE_TRACE})
  list(PREPEND VERILATE_ARGS "TRACE_FST")
endif()

verilate(emulator
  SOURCES ${VERILATE_SRC_LIST}
  ${VERILATE_ARGS}
)

install(TARGETS emulator RUNTIME)

