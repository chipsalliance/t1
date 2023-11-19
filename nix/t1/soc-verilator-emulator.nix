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

, soc-elaborate
, config-name
, do-trace ? false
}:

stdenv.mkDerivation {
  name = "t1-soc-${config-name}-verilator-emulator" + lib.optionalString do-trace "-trace";

  src = ../../subsystememu/csrc;

  # CMakeLists.txt will read the environment
  env.VERILATE_SRC_DIR = toString soc-elaborate;

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
    libspike
    nlohmann_json
  ];

  meta.mainProgram = "emulator";
}
