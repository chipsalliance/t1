{ lib
, newScope
, rustPlatform
, libspike
, zlib
}:
let
  configsDirectory = ../rocketv/configs;
  # allConfigs is a (configName -> configJsonPath) map
  allConfigs = lib.mapAttrs'
    (fileName: fileType:
      assert fileType == "regular" && lib.hasSuffix ".json" fileName;
      lib.nameValuePair
        (lib.removeSuffix ".json" fileName)
        (lib.path.append configsDirectory fileName))
    (builtins.readDir configsDirectory);
in
lib.mapAttrs
  (configName: configPath: (
    lib.makeScope newScope (scope: rec {
      rocket-config = configPath;
      mlirbc = scope.callPackage ./nix/mlirbc.nix { };
      rtl = scope.callPackage ./nix/rtl.nix { };
      verilated-csrc = scope.callPackage ./nix/verilated-csrc.nix { };

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

        env =
          let
            toLib = drv: "${drv}/lib";
          in
          {
            ROCKET_DPI_DIR = toLib c-dpi-lib;
            TESTBENCH_LIB_DIR = toLib verilated-csrc;
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
  )) # end of mapAttr
  allConfigs
