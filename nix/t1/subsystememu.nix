{ stdenv
, lib

  # emulator deps
, cmake
, libargs
, spdlog
, fmt
, libspike
, nlohmann_json
, ninja
, verilator
, zlib
, glog

, rtl
, config-name
, do-trace ? false
, enableDebugging
}:

stdenv.mkDerivation {
  name = "t1-${config-name}-subsystememu" + lib.optionalString do-trace "-trace";

  src = ../../subsystememu/csrc;

  # CMakeLists.txt will read the environment
  env.VERILATE_SRC_DIR = toString rtl;

  cmakeFlags = lib.optionals do-trace [
    "-DVERILATE_TRACE=ON"
  ];

  nativeBuildInputs = [
    cmake
    verilator
    ninja
  ];

  buildInputs = [
    zlib
    libargs
    spdlog
    fmt
    (enableDebugging libspike)
    nlohmann_json
    glog
  ];

  meta.mainProgram = "emulator";
}
