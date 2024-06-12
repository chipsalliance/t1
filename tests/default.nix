{ lib
, elaborateConfig
, newScope
, rv32-stdenv
, runCommand
, ip-emu
}:

let
  extension = lib.head elaborateConfig.parameter.extensions;
  xLen = if lib.hasInfix "ve32" extension then 32 else 64;
  isFp = lib.hasInfix "f" extension;
  vLen = let vLen = elaborateConfig.parameter.vLen; in
    assert builtins.bitAnd vLen (vLen - 1) == 0;  # vLen should be power of 2
    assert vLen >= 32;
    vLen;

  scope = lib.recurseIntoAttrs (lib.makeScope newScope (casesSelf: {
    recurseForDerivations = true;

    inherit ip-emu;

    makeEmuResult = casesSelf.callPackage ./make-emu-result.nix { };

    makeBuilder = casesSelf.callPackage ./builder.nix { };

    findAndBuild = dir: build:
      lib.recurseIntoAttrs (lib.pipe (builtins.readDir dir) [
        # filter out all non-directory entrires and underscore-prefixed directories
        (lib.filterAttrs (name: type: type == "directory" && ! lib.hasPrefix "_" name))
        # prepend path with base directory
        (lib.mapAttrs (subDirName: _: (lib.path.append dir subDirName)))
        # build. If {sourcePath}/default.nix exists, call it. Otherwise call the generic builder
        (lib.mapAttrs (caseName: sourcePath:
          if builtins.pathExists "${sourcePath}/default.nix" then
            casesSelf.callPackage sourcePath { }
          else
            build {
              inherit caseName sourcePath;
            })
        )
        (lib.filterAttrs (caseName: caseDrv: assert caseDrv ? isFp; caseDrv.isFp -> isFp))
      ]);
    t1main = ./t1_main.S;
    linkerScript = ./t1.ld;

    stdenv = rv32-stdenv;

    inherit xLen vLen isFp;

    mlir = casesSelf.callPackage ./mlir { };
    intrinsic = casesSelf.callPackage ./intrinsic { };
    asm = casesSelf.callPackage ./asm { };
    perf = casesSelf.callPackage ./perf { };
    codegen = casesSelf.callPackage ./codegen { };
    rvv_bench = casesSelf.callPackage ./rvv_bench { };
  }));

  # remove non-case attributes in scope
  scopeStripped = {
    inherit (scope) mlir intrinsic asm perf codegen rvv_bench;
  };

  all =
    let
      allCases = lib.filter lib.isDerivation
        (lib.concatLists (map lib.attrValues (lib.attrValues scopeStripped)));
      script = ''
        mkdir -p $out/configs
      '' + (lib.concatMapStringsSep "\n"
        (caseDrv: ''
          mkdir -p $out/cases/${caseDrv.pname}
          cp ${caseDrv}/bin/${caseDrv.pname}.elf $out/cases/${caseDrv.pname}/
          cp ${caseDrv}/${caseDrv.pname}.json $out/configs/
        '')
        allCases);
    in
    runCommand "build-all-testcases" { } script;
in
lib.recurseIntoAttrs (scopeStripped // { inherit all; })
