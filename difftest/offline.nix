{ lib
, elaborateConfig
, rustPlatform
, libspike
, libspike_interfaces
}:

rustPlatform.buildRustPackage {
  name = "offline";
  src = with lib.fileset; toSource {
    root = ./.;
    fileset = unions [
      ./spike_rs
      ./offline
      ./dpi_common
      ./dpi_t1
      ./dpi_t1rocket
      ./test_common
      ./Cargo.lock
      ./Cargo.toml
    ];
  };

  buildFeatures = [];
  buildAndTestSubdir = "./offline";

  env = {
    SPIKE_LIB_DIR = "${libspike}/lib";
    SPIKE_INTERFACES_LIB_DIR = "${libspike_interfaces}/lib";
    DESIGN_VLEN = elaborateConfig.parameter.vLen;
    DESIGN_DLEN = elaborateConfig.parameter.dLen;
    SPIKE_ISA_STRING =
      "rv32gc_" +
      (builtins.concatStringsSep "_" elaborateConfig.parameter.extensions)
      + "_Zvl${toString elaborateConfig.parameter.vLen}b";
  };

  cargoLock = {
    lockFile = ./Cargo.lock;
  };
}
