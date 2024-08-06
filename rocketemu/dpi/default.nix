{ lib
, verilator
, stdenv
, cmake
, ninja
, verilated-csrc
}:
stdenv.mkDerivation {
  name = "rocketv-emulator";

  src = ./.;

  nativeBuildInputs = [
    cmake
    ninja
    verilator
  ];

  cmakeFlags = lib.optionals verilated-csrc.enable-trace [
    "-DVM_TRACE=ON"
  ];

  env = {
    VERILATED_INC_DIR = "${verilated-csrc}/include";
    VERILATED_LIB_DIR = "${verilated-csrc}/lib";
  };
}
