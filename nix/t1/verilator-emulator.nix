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

, elaborate

, do-trace ? false
}:

stdenv.mkDerivation {
  name = "t1-verilator-emulator";

  src = ../../emulator;

  # CMakeLists.txt will read the environment
  env.VERILATE_SRC_DIR = toString elaborate;

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
