{ lib
, newScope
}:
lib.makeScope newScope (scope: {
  mlirbc = scope.callPackage ./nix/mlirbc.nix { };
  rtl = scope.callPackage ./nix/rtl.nix { };
  verilated-c-lib = scope.callPackage ./nix/verilated-c-lib.nix { };
  emu = scope.callPackage ./emu.nix { };
  designConfig = with builtins; (fromJSON (readFile ./configs/default.json)).parameter;
  cases = scope.callPackage ../tests {
    configName = "t1rocket";
    t1rocket-emu = scope.emu;
    rtlDesignMetadata = {
      march = "rv32iafcv_zve32x_zvl1024b";
      dlen = scope.designConfig.dLen;
    };
  };
})
