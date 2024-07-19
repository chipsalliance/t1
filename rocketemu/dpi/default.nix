{ lib
, verilator
, stdenv
, cmake
, ninja
, rocketv-verilated-csrc
}:
stdenv.mkDerivation {
  name = "rocketv-emulator";

  src = ./.;

  nativeBuildInputs = [
    cmake
    ninja
    verilator
  ];

  cmakeFlags = lib.optionals rocketv-verilated-csrc.enable-trace [
    "-DVM_TRACE=ON"
  ];

  env = {
    VERILATED_INC_DIR = "${rocketv-verilated-csrc}/include";
    VERILATED_LIB_DIR = "${rocketv-verilated-csrc}/lib";
  };
}
