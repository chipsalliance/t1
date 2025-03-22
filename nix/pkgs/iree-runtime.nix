{ lib
, cmake
, ninja
, rv32-stdenv
, iree-compiler
, fetchFromGitHub
, breakpointHook
}:
let
  version = "3.2.0";
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
    tag = "v${version}";
    hash = "sha256-gS65v/iVCmR5yr4SYZIPnUCndRPvdqa83ShoEKywXeo=";
  };

  postUnpack = ''
    cp -r ${iree-googletest-src}/* $sourceRoot/third_party/googletest/
    cp -r ${iree-flatcc-src}/* $sourceRoot/third_party/flatcc/
    chmod -R u+w $sourceRoot/third_party/
  '';

  nativeBuildInputs = [ cmake ninja iree-compiler ];

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
    "-DIREE_HOST_BIN_DIR=${lib.getBin iree-compiler}/bin"
  ];

  patches = [
    ../patches/iree/0001-Remove-flags-demo-test.patch
    ../patches/iree/0001-Remove-elf_module_test.patch
    ../patches/iree/0001-Remove-hello_world_file.patch
  ];
}
