{ lib
, rustPlatform
, libspike
, libspike_interfaces
, rtlDesignMetadata
}:

rustPlatform.buildRustPackage {
  name = "offline-checker-t1rocket";
  src = with lib.fileset; toSource {
    root = ./.;
    fileset = unions [
      ./spike_rs
      ./offline_t1rocket
      ./test_common
      ./Cargo.lock
      ./Cargo.toml
    ];
  };

  buildFeatures = [ ];
  buildAndTestSubdir = "./offline_t1rocket";

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
}
