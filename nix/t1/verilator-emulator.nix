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

  cmakeFlags = [
    "-DVERILATE_SRC_DIR=${elaborate}"
  ] ++ lib.optionals doTrace [
    "-DEMULATOR_TRACE=ON"
  ];

  nativeBuildInputs = [
    cmake

    verilator
    zlib

    libargs
    spdlog
    fmt
    libspike
    nlohmann_json
    ninja
  ];
}
