# disp.lvgl

This is an example of running lvgl on T1.

## Build & Run instructions

```
# Create a directory for compiling
cd /workspace/examples/disp.lvgl # Should be the directory where this README lives
mkdir build && cd build

# Generate build files using cmake
cmake .. \
    -DLV_CONF_BUILD_DISABLE_DEMOS=TRUE \
    -DLV_CONF_BUILD_DISABLE_EXAMPLES=TRUE \
    -DLV_CONF_BUILD_DISABLE_THORVG_INTERNAL=TRUE \
    -DT1_PLATFORM=TRUE \
    -DCMAKE_TOOLCHAIN_FILE=../toolchain.cmake

# Build using make
make -j$(nproc)

# Since T1's elf need a special entry point, manually link a new elf here.
# (This should be done in cmake, though I haven't invest in time to figure it out)
t1-cc -T /workspace/share/t1.ld /workspace/share/main.S \
    ../main.c \
    -DLV_CONF_INCLUDE_SIMPLE -DLV_LVGL_H_INCLUDE_SIMPLE \
    -isystem /workspace/examples/disp.lvgl/lvgl \
    -isystem /workspace/examples/disp.lvgl/build/lvgl \
    CMakeFiles/lvgl_port_gl.dir/porting/lv_port_disp.c.obj \
    CMakeFiles/lvgl_port_gl.dir/porting/lv_port_fs.c.obj \
    CMakeFiles/lvgl_port_gl.dir/porting/lv_port_indev.c.obj \
    lvgl/lib/liblvgl.a \
    -o lvgl.elf

# Run the elf binary through emulator
t1rocketemu-verilated-simulator +t1_elf_file=lvgl.elf
```