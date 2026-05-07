# Specify the cross compiler
set(CMAKE_SYSTEM_NAME Generic)
set(CMAKE_C_COMPILER t1-cc)
#set(CMAKE_CXX_COMPILER /bin/t1-c++)

#set(CMAKE_TRY_COMPILE_TARGET_TYPE "STATIC_LIBRARY")
#set(CMAKE_CXX_COMPILER_FORCED TRUE)

# Specify additional flags if necessary
#set(CMAKE_C_FLAGS "-T /workspace/share/t1.ld")

# Include directories for system files
#include_directories(/workspace/share)

# Set the output format
set(CMAKE_OUTPUT_FORMAT ELF)

set(CMAKE_AR /bin/riscv32-none-elf-ar)

#set(CMAKE_C_COMPILE_OBJECT
#    "<CMAKE_C_COMPILER> ${CMAKE_C_FLAGS} <DEFINES> <INCLUDES> <SOURCE> -o <OBJECT>")
