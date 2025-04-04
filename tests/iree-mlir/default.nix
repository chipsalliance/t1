{ linkerScript
, iree
, iree-runtime
, makeBuilder
, findAndBuild
, getTestRequiredFeatures
, t1main
}:

let
  builder = makeBuilder { casePrefix = "iree-mlir"; };
  build = { caseName, sourcePath }:
    builder {
      inherit caseName;

      src = sourcePath;

      passthru.featuresRequired = getTestRequiredFeatures sourcePath;

      nativeBuildInputs = [ iree ];
      buildInputs = [ iree-runtime ];

      ireeCFlags = toString [
        "-DIREE_PLATFORM_GENERIC=1"
      ];

      ireeCompileArgs = [
        "--output-format=vm-bytecode"
        "--iree-hal-target-backends=llvm-cpu"
        "--iree-llvmcpu-target-triple=riscv32-pc-none-elf"
        "--iree-llvmcpu-target-cpu=generic-rv32"
        "--iree-llvmcpu-target-abi=ilp32f"
        "--iree-llvmcpu-target-cpu-features=+m,+f"
        "--riscv-v-fixed-length-vector-lmul-max=8"
        "--iree-llvmcpu-debug-symbols=false"
      ];

      ireeCEmbedDataArgs = [
        "--output_header=mul_module_dylib_riscv_32.h"
        "--output_impl=mul_module_dylib_riscv_32.c"
        "--identifier=iree_mlir_test_mul_module_dylib_riscv_32"
        "--flatten"
      ];

      buildPhase = ''
        runHook preBuild

        iree-compile $ireeCompileArgs mul.mlir -o mul_module_dylib_riscv_32.vmfb

        iree-c-embed-data $ireeCEmbedDataArgs mul_module_dylib_riscv_32.vmfb

        $CC -c -fPIC mul_module_dylib_riscv_32.c -o mul_module_dylib.o

        $CC -c -fPIC $ireeCFlags init.c -o mul_init.o

        $CC -c -fPIC $ireeCFlags device_embedded_sync.c -o device_embedded_sync.o

        $CC -T${linkerScript} ${t1main} mul_init.o mul_module_dylib.o device_embedded_sync.o \
          -liree_vm_impl \
          -liree_base_base \
          -liree_base_internal_synchronization \
          -liree_base_internal_time \
          -liree_base_internal_arena \
          -liree_base_internal_atomic_slist \
          -liree_base_internal_cpu \
          -liree_base_internal_fpu_state \
          -liree_base_internal_memory \
          -liree_hal_hal \
          -liree_modules_hal_hal \
          -liree_modules_hal_types \
          -liree_modules_hal_debugging \
          -liree_modules_hal_utils_buffer_diagnostics \
          -liree_io_file_handle \
          -liree_hal_drivers_local_sync_sync_driver \
          -liree_hal_utils_deferred_command_buffer \
          -liree_hal_utils_files \
          -liree_hal_utils_file_transfer \
          -liree_hal_utils_resource_set \
          -liree_hal_local_local \
          -liree_hal_utils_semaphore_base \
          -liree_hal_local_executable_loader \
          -liree_hal_local_executable_library_util \
          -liree_hal_local_loaders_embedded_elf_loader \
          -liree_hal_local_elf_elf_module \
          -liree_hal_local_elf_arch \
          -liree_hal_local_elf_platform \
          -liree_hal_local_executable_plugin_manager \
          -liree_hal_local_executable_environment \
          -liree_vm_bytecode_module \
          -liree_vm_bytecode_utils_utils \
          -lflatcc_parsing \
          -o $pname.elf

        runHook postBuild
      '';

      meta.description = "testcase '${caseName}', written in MLIR";
    };
in
findAndBuild ./. build
