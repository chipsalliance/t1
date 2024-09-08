{ lib
, rustPlatform
, libspike
, libspike_interfaces
, rtlDesignMetadata
}:

{ outputName
, emuType ? ""
, moduleType
, enableTrace ? false
}:

assert let
  available = [ "dpi_t1" "dpi_t1rocket" "offline_t1" "offline_t1rocket" ];
in
lib.assertMsg (lib.elem moduleType available) "emuType is not in ${lib.concatStringsSep ", " available}";
# if emuType is empty, then moduleType must be offline-*, or user should give valid emuType
assert lib.assertMsg ((emuType == "" && lib.hasPrefix "offline" moduleType) || (lib.elem emuType [ "verilator" "vcs" ])) "emuType is either 'vcs' nor 'verilator'";

rustPlatform.buildRustPackage {
  name = outputName;
  src = with lib.fileset; toSource {
    root = ./.;
    fileset = unions [
      ./spike_rs
      ./offline_t1
      ./offline_t1rocket
      ./dpi_common
      ./dpi_t1
      ./dpi_t1rocket
      ./test_common
      ./Cargo.lock
      ./Cargo.toml
    ];
  };

  buildFeatures = [ ] ++ lib.optionals (lib.hasPrefix "dpi" moduleType) [ "dpi_common/${emuType}" ] ++ lib.optionals enableTrace [ "dpi_common/trace" ];
  buildAndTestSubdir = "./${moduleType}";

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
    dpiLibPath = "/lib/libdpi_${moduleType}.a";
    inherit enableTrace;
  };
}
