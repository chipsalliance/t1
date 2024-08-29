{ lib
, elaborateConfig
, rustPlatform
, libspike
, libspike_interfaces
, enable-trace ? false
, vcStaticHome
}:

rustPlatform.buildRustPackage {
  name = "vcs-dpi-lib";
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

  buildFeatures = ["dpi_common/vcs"] ++ lib.optionals enable-trace [ "dpi_common/trace" ];
  buildAndTestSubdir = "./dpi_t1";

  env = {
    VCS_LIB_DIR = "${vcStaticHome}/vcs-mx/linux64/lib";
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

  passthru = {
    inherit enable-trace;
  };
}
