{ stdenv

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


, configName
, verilated
}:

stdenv.mkDerivation {
  name = "t1-${configName}-pseudo-emu";

  src = ../../ipemu/csrc/verilator;

  cmakeFlags = [
    "-DVERILATED_INC_DIR=${verilated}/include"
    "-DVERILATED_LIB_DIR=${verilated}/lib"
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
    description = "IP emulator for config ${configName}";
  };
}
