{ lib
, libspike
, rustPlatform
, rust-analyzer
, libspike_interfaces
}:

let
  self = rustPlatform.buildRustPackage {
    name = "t1-simulator";
    src = with lib.fileset; toSource {
      root = ./.;
      fileset = fileFilter (file: file.name != "default.nix") ./.;
    };
    passthru.devShell = self.overrideAttrs (old: {
      nativeBuildInputs = old.nativeBuildInputs ++ [
        rust-analyzer
      ];
    });
    buildInputs = [ libspike libspike_interfaces ];
    cargoLock = {
      lockFile = ./Cargo.lock;
    };
  };
in
self
