{ lib
, rustPlatform
, libspike
, libspike_interfaces
, rtlDesignMetadata
}:

{ outputName
, emuType ? ""
, moduleType
}:

assert let
  available = [ "dpi_t1emu" "dpi_t1rocketemu" "offline_t1emu" "offline_t1rocketemu" ];
in
lib.assertMsg (lib.elem moduleType available) "moduleType is not in ${lib.concatStringsSep ", " available}";
# if emuType is empty, then moduleType must be offline-*, or user should give valid emuType
assert lib.assertMsg ((emuType == "" && lib.hasPrefix "offline" moduleType) || (lib.elem emuType [ "verilator" "vcs" ])) "emuType is either 'vcs' nor 'verilator'";

rustPlatform.buildRustPackage {
  name = outputName;
  src = with lib.fileset; toSource {
    root = ./.;
    fileset = unions [
      ./spike_rs
      ./offline_t1emu
      ./offline_t1rocketemu
      ./dpi_common
      ./dpi_t1emu
      ./dpi_t1rocketemu
      ./Cargo.lock
      ./Cargo.toml
      ./.rustfmt.toml
    ];
  };

  buildFeatures = [ ] ++ lib.optionals (lib.hasPrefix "dpi" moduleType) [ "dpi_common/${emuType}" ];
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

  postInstall = lib.optionalString (lib.hasPrefix "offline" moduleType) ''
    exe=$(find $out/bin -type f -name 'offline_*')
    ln -s "$exe" $out/bin/offline
  '';

  passthru = {
    dpiLibPath = "/lib/libdpi_${moduleType}.a";
  };
}
