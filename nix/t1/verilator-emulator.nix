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
, elaborate-config
}:

let
  doTrace = (builtins.fromJSON (builtins.readFile elaborate-config)).trace;
in
stdenv.mkDerivation {
  name = "t1-verilator-emulator";

  src = ../../emulator/src;

  # CMakeLists.txt will read the environment
  env.VERILATE_SRC_DIR = toString elaborate;

  cmakeFlags = lib.optionals doTrace [
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
