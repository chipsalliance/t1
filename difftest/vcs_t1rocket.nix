{ lib
, libspike
, libspike_interfaces
, rustPlatform
, rtlDesignMetadata
, enable-trace ? false
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
  buildAndTestSubdir = "./dpi_t1rocket";

  env = {
    SPIKE_LIB_DIR = "${libspike}/lib";
    SPIKE_INTERFACES_LIB_DIR = "${libspike_interfaces}/lib";
    DESIGN_VLEN = rtlDesignMetadata.vlen;
    DESIGN_DLEN = rtlDesignMetadata.dlen;
    SPIKE_ISA_STRING = rtlDesignMetadata.march;
  };

  cargoLock = {
    lockFile = ./Cargo.lock;
  };

  passthru = {
    inherit enable-trace;
  };
}
