{ lib
, cmake
, ninja
, rv32-stdenv
, iree
, fetchFromGitHub
}:
let
  version = "3.4.0rc20250331";
  iree-googletest-version = "c8393f8554419dc27b688c535b8fa4afb82146a4";
  iree-googletest-src = fetchFromGitHub {
    owner = "google";
    repo = "googletest";
    rev = iree-googletest-version;
    hash = "sha256-K48kVGMyoWWcDHXhHM98NORZerBm6wbnYf50GkK4B2Y=";
  };
  iree-flatcc-version = "9362cd00f0007d8cbee7bff86e90fb4b6b227ff3";
  iree-flatcc-src = fetchFromGitHub {
    owner = "dvidelabs";
    repo = "flatcc";
    rev = iree-flatcc-version;
    hash = "sha256-umZ9TvNYDZtF/mNwQUGuhAGve0kPw7uXkaaQX0EzkBY=";
  };
in
rv32-stdenv.mkDerivation {
  pname = "iree";
  version = version;

  src = fetchFromGitHub {
    owner = "iree-org";
    repo = "iree";
    tag = "iree-${version}";
    hash = "sha256-L21iWAyl4qj6w8tElrnf05Vt6eEY3A+VX4yfH7bUciA=";
  };

  postUnpack = ''
    cp -r ${iree-googletest-src}/* $sourceRoot/third_party/googletest/
    cp -r ${iree-flatcc-src}/* $sourceRoot/third_party/flatcc/
    chmod -R u+w $sourceRoot/third_party/
  '';

  nativeBuildInputs = [ cmake ninja iree ];

  doCheck = false;

  CXXFLAGS = toString [
    "-march=rv32gcv"
    "-mabi=ilp32f"
    "-DIREE_PLATFORM_GENERIC=1"
    "-DIREE_SYNCHRONIZATION_DISABLE_UNSAFE=1"
    "-DIREE_FILE_IO_ENABLE=0"
    ''-DIREE_TIME_NOW_FN="{ return 0; }"''
    ''-D"IREE_WAIT_UNTIL_FN(ns)= (true)"''
    "-DIREE_DEVICE_SIZE_T=uint32_t"
    "-DPRIdsz=PRIu32"
  ];
  CFLAGS = toString [
    "-march=rv32gcv"
    "-mabi=ilp32f"
    "-DIREE_PLATFORM_GENERIC=1"
    "-DIREE_SYNCHRONIZATION_DISABLE_UNSAFE=1"
    "-DIREE_FILE_IO_ENABLE=0"
    ''-DIREE_TIME_NOW_FN="{ return 0; }"''
    ''-D"IREE_WAIT_UNTIL_FN(ns)= (true)"''
    "-DIREE_DEVICE_SIZE_T=uint32_t"
    "-DPRIdsz=PRIu32"
  ];

  cmakeFlags = [
    "-DIREE_BUILD_TESTS=OFF"
    "-DIREE_BUILD_SAMPLES=OFF"
    "-DIREE_BUILD_COMPILER=OFF"
    "-DCMAKE_CROSSCOMPILING=ON"
    "-DCMAKE_SYSTEM_NAME=Generic"
    "-DCMAKE_SYSTEM_PROCESSOR=riscv32"
    "-DCMAKE_C_COMPILER_TARGET=riscv32"
    "-DCMAKE_CXX_COMPILER_TARGET=riscv32"
    "-DIREE_ENABLE_THREADING=OFF"
    "-DIREE_HAL_DRIVER_DEFAULTS=OFF"
    "-DIREE_HAL_DRIVER_LOCAL_SYNC=ON"
    "-DIREE_HAL_EXECUTABLE_LOADER_DEFAULTS=OFF"
    "-DIREE_HAL_EXECUTABLE_LOADER_EMBEDDED_ELF=ON"
    "-DIREE_HAL_EXECUTABLE_PLUGIN_DEFAULTS=OFF"
    "-DIREE_HAL_EXECUTABLE_PLUGIN_EMBEDDED_ELF=ON"
    "-DIREE_BUILD_BINDINGS_TFLITE=OFF"
    "-DIREE_BUILD_BINDINGS_TFLITE_JAVA=OFF"
    "-DIREE_HOST_BIN_DIR=${iree}/bin"
  ];

  # postBuild = ''
  #   exit 1
  # '';

  installPhase = ''
    # iree::base
    mkdir -p $prefix/lib/iree/base/
    cp runtime/src/iree/base/libiree_base_base.a $prefix/lib/
    cp runtime/src/iree/base/internal/libiree_base_internal_synchronization.a $prefix/lib/
    cp runtime/src/iree/base/internal/libiree_base_internal_time.a $prefix/lib/
    cp runtime/src/iree/base/internal/libiree_base_internal_arena.a $prefix/lib/
    cp runtime/src/iree/base/internal/libiree_base_internal_atomic_slist.a $prefix/lib/
    cp runtime/src/iree/base/internal/libiree_base_internal_cpu.a $prefix/lib/
    cp runtime/src/iree/base/internal/libiree_base_internal_fpu_state.a $prefix/lib/
    cp runtime/src/iree/base/internal/libiree_base_internal_memory.a $prefix/lib/
    mkdir -p $prefix/include/iree/base/
    cp $src/runtime/src/iree/base/api.h $prefix/include/iree/base/
    cp $src/runtime/src/iree/base/alignment.h $prefix/include/iree/base/
    cp $src/runtime/src/iree/base/attributes.h $prefix/include/iree/base/
    cp $src/runtime/src/iree/base/target_platform.h $prefix/include/iree/base/
    cp $src/runtime/src/iree/base/config.h $prefix/include/iree/base/
    cp $src/runtime/src/iree/base/allocator.h $prefix/include/iree/base/
    cp $src/runtime/src/iree/base/status.h $prefix/include/iree/base/
    cp $src/runtime/src/iree/base/string_view.h $prefix/include/iree/base/
    cp $src/runtime/src/iree/base/assert.h $prefix/include/iree/base/
    cp $src/runtime/src/iree/base/bitfield.h $prefix/include/iree/base/
    cp $src/runtime/src/iree/base/string_builder.h $prefix/include/iree/base/
    cp $src/runtime/src/iree/base/loop.h $prefix/include/iree/base/
    cp $src/runtime/src/iree/base/time.h $prefix/include/iree/base/
    cp $src/runtime/src/iree/base/wait_source.h $prefix/include/iree/base/
    cp $src/runtime/src/iree/base/loop_inline.h $prefix/include/iree/base/
    cp $src/runtime/src/iree/base/tracing.h $prefix/include/iree/base/

    mkdir -p $prefix/include/iree/base/internal/
    cp $src/runtime/src/iree/base/internal/atomics.h $prefix/include/iree/base/internal/
    cp $src/runtime/src/iree/base/internal/atomics_clang.h $prefix/include/iree/base/internal/

    # iree::hal
    mkdir -p $prefix/lib/iree/hal/
    cp runtime/src/iree/hal/libiree_hal_hal.a $prefix/lib/
    cp runtime/src/iree/hal/utils/libiree_hal_utils_deferred_command_buffer.a $prefix/lib/
    cp runtime/src/iree/hal/utils/libiree_hal_utils_files.a $prefix/lib/
    cp runtime/src/iree/hal/utils/libiree_hal_utils_file_transfer.a $prefix/lib/
    cp runtime/src/iree/hal/utils/libiree_hal_utils_semaphore_base.a $prefix/lib/
    cp runtime/src/iree/hal/utils/libiree_hal_utils_resource_set.a $prefix/lib/
    cp runtime/src/iree/hal/local/libiree_hal_local_local.a $prefix/lib/
    mkdir -p $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/api.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/allocator.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/buffer.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/queue.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/resource.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/buffer_transfer.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/device.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/channel.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/channel_provider.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/command_buffer.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/event.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/executable.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/executable_cache.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/fence.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/semaphore.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/file.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/buffer_view.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/buffer_view_util.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/driver.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/driver_registry.h $prefix/include/iree/hal/
    cp $src/runtime/src/iree/hal/string_util.h $prefix/include/iree/hal/

    mkdir -p $prefix/include/iree/io/
    cp runtime/src/iree/io/libiree_io_file_handle.a $prefix/lib/
    cp $src/runtime/src/iree/io/file_handle.h $prefix/include/iree/io/

    # iree::hal::drivers::local_sync::sync_driver
    mkdir -p $prefix/lib/iree/hal/drivers/local_sync/
    cp runtime/src/iree/hal/drivers/local_sync/libiree_hal_drivers_local_sync_sync_driver.a $prefix/lib/
    mkdir -p $prefix/include/iree/hal/drivers/local_sync/
    cp $src/runtime/src/iree/hal/drivers/local_sync/sync_device.h $prefix/include/iree/hal/drivers/local_sync/
    
    # iree::hal::local::executable_loader
    mkdir -p $prefix/lib/iree/hal/local/
    cp runtime/src/iree/hal/local/libiree_hal_local_executable_loader.a $prefix/lib/
    cp runtime/src/iree/hal/local/libiree_hal_local_executable_library_util.a $prefix/lib/
    cp runtime/src/iree/hal/local/libiree_hal_local_executable_plugin_manager.a $prefix/lib/
    cp runtime/src/iree/hal/local/libiree_hal_local_executable_environment.a $prefix/lib/
    mkdir -p $prefix/include/iree/hal/local/
    cp $src/runtime/src/iree/hal/local/executable_loader.h $prefix/include/iree/hal/local/

    # iree::hal::local::loaders::embedded_elf_loader
    mkdir -p $prefix/lib/iree/hal/local/loaders/
    cp runtime/src/iree/hal/local/loaders/libiree_hal_local_loaders_embedded_elf_loader.a $prefix/lib/
    cp runtime/src/iree/hal/local/elf/libiree_hal_local_elf_elf_module.a $prefix/lib/
    cp runtime/src/iree/hal/local/elf/libiree_hal_local_elf_arch.a $prefix/lib/
    cp runtime/src/iree/hal/local/elf/libiree_hal_local_elf_platform.a $prefix/lib/
    mkdir -p $prefix/include/iree/hal/local/loaders/
    cp $src/runtime/src/iree/hal/local/loaders/embedded_elf_loader.h $prefix/include/iree/hal/local/loaders/
    
    # iree::modules::hal
    mkdir -p $prefix/lib/iree/modules/hal/
    cp runtime/src/iree/modules/hal/libiree_modules_hal_hal.a $prefix/lib/
    cp runtime/src/iree/modules/hal/libiree_modules_hal_types.a $prefix/lib/
    cp runtime/src/iree/modules/hal/libiree_modules_hal_debugging.a $prefix/lib/
    cp runtime/src/iree/modules/hal/utils/libiree_modules_hal_utils_buffer_diagnostics.a $prefix/lib/
    mkdir -p $prefix/include/iree/modules/hal/
    cp $src/runtime/src/iree/modules/hal/module.h $prefix/include/iree/modules/hal/
    cp $src/runtime/src/iree/modules/hal/debugging.h $prefix/include/iree/modules/hal/
    cp $src/runtime/src/iree/modules/hal/types.h $prefix/include/iree/modules/hal/

    # iree::vm
    mkdir -p $prefix/lib/iree/vm/
    cp runtime/src/iree/vm/libiree_vm_impl.a $prefix/lib/
    mkdir -p $prefix/include/iree/vm/
    cp $src/runtime/src/iree/vm/api.h $prefix/include/iree/vm/
    cp $src/runtime/src/iree/vm/buffer.h $prefix/include/iree/vm/
    cp $src/runtime/src/iree/vm/ref.h $prefix/include/iree/vm/
    cp $src/runtime/src/iree/vm/context.h $prefix/include/iree/vm/
    cp $src/runtime/src/iree/vm/instance.h $prefix/include/iree/vm/
    cp $src/runtime/src/iree/vm/module.h $prefix/include/iree/vm/
    cp $src/runtime/src/iree/vm/stack.h $prefix/include/iree/vm/
    cp $src/runtime/src/iree/vm/invocation.h $prefix/include/iree/vm/
    cp $src/runtime/src/iree/vm/list.h $prefix/include/iree/vm/
    cp $src/runtime/src/iree/vm/type_def.h $prefix/include/iree/vm/
    cp $src/runtime/src/iree/vm/value.h $prefix/include/iree/vm/
    cp $src/runtime/src/iree/vm/variant.h $prefix/include/iree/vm/
    cp $src/runtime/src/iree/vm/native_module.h $prefix/include/iree/vm/
    cp $src/runtime/src/iree/vm/shims.h $prefix/include/iree/vm/

    # iree::vm::bytecode::module
    mkdir -p $prefix/lib/iree/vm/bytecode/
    cp runtime/src/iree/vm/bytecode/libiree_vm_bytecode_module.a $prefix/lib/
    cp runtime/src/iree/vm/bytecode/utils/libiree_vm_bytecode_utils_utils.a $prefix/lib/
    mkdir -p $prefix/include/iree/vm/bytecode/
    cp $src/runtime/src/iree/vm/bytecode/module.h $prefix/include/iree/vm/bytecode/

    cp build_tools/third_party/flatcc/libflatcc_parsing.a $prefix/lib/
    # cp build_tools/third_party/flatcc/libflatcc_runtime.a $prefix/lib/
  '';

  patches = [
    ../patches/iree/0001-Do-not-unconditionally-build-runtime-demo.patch
  ];
}
