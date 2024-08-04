cmake_minimum_required(VERSION 3.20)
project(spike_interfaces LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)

find_package(libspike REQUIRED)

add_library(${CMAKE_PROJECT_NAME} STATIC spike_interfaces.cc)

target_link_libraries(${CMAKE_PROJECT_NAME} PUBLIC libspike)

target_include_directories(${CMAKE_PROJECT_NAME} INTERFACE
  $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}>
  $<INSTALL_INTERFACE:${CMAKE_INSTALL_INCLUDEDIR}>
)

# just playing with CMake export, maybe not necessary
target_sources(${CMAKE_PROJECT_NAME} PUBLIC
  FILE_SET HEADERS
  FILES spike_interfaces.h spike_interfaces_c.h)

install(
  TARGETS ${CMAKE_PROJECT_NAME}
  EXPORT ${CMAKE_PROJECT_NAME}-config
  PUBLIC_HEADER
    FILE_SET HEADERS
)

install(
  EXPORT ${CMAKE_PROJECT_NAME}-config
  NAMESPACE ${CMAKE_PROJECT_NAME}::
  DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/${CMAKE_PROJECT_NAME}
)
