{ lib
, verilator
, stdenv
, cmake
, rocketv-verilated-csrc
}:
stdenv.mkDerivation {
  name = "rocketv-emulator";

  src = ./.;

  nativeBuildInputs = [
    cmake
    verilator
  ];

  env = {
    VERILATED_INC_DIR = "${rocketv-verilated-csrc}/include";
    VERILATED_LIB_DIR = "${rocketv-verilated-csrc}/lib";
  };
}
