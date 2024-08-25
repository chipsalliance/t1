{ lib
, newScope
}:
{
  ip = lib.makeScope newScope (scope: {
    mlirbc = scope.callPackage ./nix/mlirbc.nix { };

    rtl = scope.callPackage ./nix/rtl.nix { };

    verilated-c-lib = scope.callPackage ./nix/verilated-c-lib.nix { enable-trace = false; };
    verilated-c-lib-trace = scope.callPackage ./nix/verilated-c-lib.nix { };

    verilator-emu = scope.callPackage ./nix/verilator.nix { };
    verilator-emu-trace = scope.callPackage ./nix/verilator.nix { verilated-c-lib = scope.verilated-c-lib-trace; };

    vcs-dpi-lib = scope.callPackage ./online_vcs { };
    vcs-dpi-lib-trace = scope.vcs-dpi-lib.override { enable-trace = true; };

    vcs-emu = scope.callPackage ./nix/vcs.nix { };
    vcs-emu-trace = scope.callPackage ./nix/vcs.nix { vcs-dpi-lib = scope.vcs-dpi-lib-trace; };

    getVLen = ext:
      let
        val = builtins.tryEval
          (lib.toInt
            (lib.removeSuffix "b"
              (lib.removePrefix "zvl"
                (lib.toLower ext))));
      in
      if val.success then
        val.value
      else
        throw "Invalid vlen extension `${ext}` specify, expect Zvl{N}b";

    # TODO: designConfig should be read from OM
    designConfig = with builtins; (fromJSON (readFile ./configs/default.json)).parameter;

    # TODO: We should have a type define, to keep t1 and t1rocket feeds same `rtlDesignMetadata` data structure.
    rtlDesignMetadata = rec {
      # TODO: `march` and `dlen` should be read from OM
      #
      # Although the string is already hard-coded in lower case, the toLower function call here is to remind developer that,
      # when we switch OM, we should always ensure the march input is lower case.
      march = lib.toLower "rv32imafcv_zve32x_zvl1024b";
      dlen = scope.designConfig.dLen;
      xlen = if (lib.hasPrefix "rv32" march) then 32 else 64;

      # Find "Zvl{N}b" string in march and parse it to vlen.
      # Extract earlier so that downstream derivation that relies on this value doesn't have to parse the string multiple times.
      vlen = lib.pipe (march) [
        (lib.splitString "_")
        (lib.filter (x: lib.hasPrefix "zvl" x))
        (lib.last)
        (lib.removePrefix "zvl")
        (lib.removeSuffix "b")
        (lib.toInt)
      ];
    };
  });
}
