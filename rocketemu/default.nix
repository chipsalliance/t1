{ lib
, newScope
, rustPlatform
, libspike
, zlib
, rocketv-verilated-csrc
}:
lib.makeScope newScope (scope: rec {
  c-dpi-lib = scope.callPackage ./dpi { };

  # FIXME: merge with difftest and put it under the nix/pkgs
  spike_interfaces = scope.callPackage ../difftest/spike_interfaces { };

  emu = rustPlatform.buildRustPackage {
    name = "rocketemu";

    src = with lib.fileset; toSource {
      root = ./.;
      fileset = unions [
        ./driver
        ./offline
        ./spike_rs
        ./test_common
        ./Cargo.lock
        ./Cargo.toml
      ];
    };

    buildInputs = [
      zlib
      spike_interfaces
    ];

    # FIXME: can we hack this into derivations, so that we don't need to specify library dir explicitly?
    env =
      let
        toLib = drv: "${drv}/lib";
      in
      {
        ROCKET_DPI_DIR = toLib c-dpi-lib;
        TESTBENCH_LIB_DIR = toLib rocketv-verilated-csrc;
        SPIKE_LIB_DIR = toLib libspike;
        SPIKE_INTERFACES_LIB_DIR = toLib spike_interfaces;
      };

    cargoLock = {
      lockFile = ./Cargo.lock;
    };

    outputs = [ "out" "driver" "offline" ];

    postInstall = ''
      mkdir -p $driver/bin $offline/bin
      ln -s $out/bin/driver $driver/bin/driver
      ln -s $out/bin/offline $driver/bin/offline
    '';
  };
})
