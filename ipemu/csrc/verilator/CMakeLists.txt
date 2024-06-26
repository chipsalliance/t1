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
find_package(dramsim3 REQUIRED)
set(THREADS_PREFER_PTHREAD_FLAG ON)

add_executable(emulator
  dpi.cpp
  main.cpp
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

target_include_directories(emulator PUBLIC ${VERILATED_INC_DIR})
target_include_directories(emulator PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

message("Found verilator: ${verilator_DIR}")
target_include_directories(emulator PUBLIC ${verilator_DIR}/include)
target_include_directories(emulator PUBLIC ${verilator_DIR}/include/vltstd)

target_link_libraries(emulator PUBLIC ${CMAKE_THREAD_LIBS_INIT})
target_link_libraries(emulator PUBLIC libspike fmt::fmt spdlog::spdlog nlohmann_json::nlohmann_json dramsim3 taywee::args)

file(GLOB_RECURSE VERILATED_LIBS "${VERILATED_LIB_DIR}/*.a") 
target_link_libraries(emulator PUBLIC ${VERILATED_LIBS})

target_compile_definitions(emulator PRIVATE COSIM_VERILATOR)

install(TARGETS emulator RUNTIME)
