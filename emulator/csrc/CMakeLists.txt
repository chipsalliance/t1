cmake_minimum_required(VERSION 3.20)
set(CMAKE_CXX_STANDARD 17)

option(IPEMU "configure to ip emulator(spike as scalar core)" OFF)
option(SUBSYSTEMEMU "configure to subsystem emulator(rocket as scalar core)" OFF)
option(VERILATE_TRACE "enable verilator trace" OFF)

if (${IPEMU})
  project(ipemu)
elseif (${SUBSYSTEMEMU})
  project(subsystememu)
else()
  message(FATAL_ERROR "either IPEMU or SUBSYSTEMEMU should be defined via -D")
endif()

# read VERILATE_SRC_DIR from args, else read from environment
if (NOT DEFINED VERILATE_SRC_DIR)
  set(VERILATE_SRC_DIR "$ENV{VERILATE_SRC_DIR}")
  if (VERILATE_SRC_DIR STREQUAL "")
    message(FATAL_ERROR "You should specify rtl directory via -DVERILATE_SRC_DIR or environment variable VERILATE_SRC_DIR, but it seems not")
  endif()
endif()
set(VERILATE_ARGS
# TODO: configurable top name?
  TOP_MODULE TestBench
  PREFIX VTestBench
  OPT_FAST
# TODO: configurable threads?
  THREADS 8
  VERILATOR_ARGS --x-initial unique --output-split 2048 --max-num-width 1048576 --threads-max-mtasks 100000 --main --timing --assert
)
if (${VERILATE_TRACE})
  list(PREPEND VERILATE_ARGS "TRACE_FST")
endif()

# parse filelist.f into VERILATE_SRC_LIST
file(READ "${VERILATE_SRC_DIR}/filelist.f" filelist_content)
string(REPLACE "\n" ";" VERILATE_SRC_LIST "${filelist_content}")  # transform lines into a list
list(FILTER VERILATE_SRC_LIST EXCLUDE REGEX "^$") # filter out empty lines
list(TRANSFORM VERILATE_SRC_LIST PREPEND "${VERILATE_SRC_DIR}/") # add base directory prefix

message(STATUS "Project '${PROJECT_NAME}' build type: ${CMAKE_BUILD_TYPE}")

find_package(args REQUIRED)
find_package(spdlog REQUIRED)
find_package(fmt REQUIRED)
find_package(verilator REQUIRED)
find_package(nlohmann_json REQUIRED)
find_package(Threads REQUIRED)
find_package(dramsim3 REQUIRED)
if (${IPEMU})
  find_package(libspike REQUIRED)
endif()
set(THREADS_PREFER_PTHREAD_FLAG ON)

if (${IPEMU})
add_executable(ipemu
  spike_event.cc
  spdlog_ext.cc
  ipemu.cc
  dpi.cc
  memory.cc
)
target_link_libraries(ipemu PUBLIC ${CMAKE_THREAD_LIBS_INIT})
target_link_libraries(ipemu PUBLIC libspike fmt::fmt spdlog::spdlog nlohmann_json::nlohmann_json dramsim3 taywee::args)
target_compile_definitions(ipemu PRIVATE COSIM_VERILATOR)
target_compile_definitions(ipemu PRIVATE IPEMU)
verilate(ipemu
  SOURCES ${VERILATE_SRC_LIST}
  ${VERILATE_ARGS}
)
install(TARGETS ipemu RUNTIME)
endif()

if (${SUBSYSTEMEMU})
add_executable(subsystememu
)
target_link_libraries(subsystememu PUBLIC ${CMAKE_THREAD_LIBS_INIT})
target_link_libraries(subsystememu PUBLIC fmt::fmt spdlog::spdlog nlohmann_json::nlohmann_json dramsim3 taywee::args)
target_compile_definitions(subsystememu PRIVATE COSIM_VERILATOR)
target_compile_definitions(subsystememu PRIVATE IPEMU)

verilate(subsystememu
  SOURCES ${VERILATE_SRC_LIST}
  ${VERILATE_ARGS}
)
install(TARGETS subsystememu RUNTIME)
endif()


