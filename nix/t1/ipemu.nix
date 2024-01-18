{ stdenv
, lib

  # emulator deps
, cmake
, libargs
, spdlog
, fmt
, libspike
, dramsim3
, nlohmann_json
, ninja
, verilator
, zlib

, rtl

, do-trace ? false
}:

stdenv.mkDerivation {
  name = "t1-${rtl.elaborateConfig}-ipemu" + lib.optionalString do-trace "-trace";

  src = ../../ipemu/csrc;

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
    libspike
    dramsim3
    nlohmann_json
  ];

  meta = {
    mainProgram = "emulator";
    description = "IP emulator for config ${rtl.elaborateConfig}";
  };
}
