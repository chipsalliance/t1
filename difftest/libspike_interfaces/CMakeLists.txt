cmake_minimum_required(VERSION 3.20)
project(libspike_interfaces)
set(CMAKE_CXX_STANDARD 17)

find_package(libspike REQUIRED)

add_library(spike_interfaces SHARED spike_interfaces.cc)

target_link_libraries(spike_interfaces PUBLIC libspike)
target_include_directories(spike_interfaces PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

install(TARGETS spike_interfaces)
