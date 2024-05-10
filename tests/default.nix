{ lib
, newScope
, rv32-stdenv
, runCommand
}:

let
  scope = lib.recurseIntoAttrs (lib.makeScope newScope (casesSelf: {
    makeBuilder = casesSelf.callPackage ./builder.nix { };

    findAndBuild = dir: build:
      lib.recurseIntoAttrs (lib.pipe (builtins.readDir dir) [
        # filter out all non-directory entrires and underscore-prefixed directories
        (lib.filterAttrs (name: type: type == "directory" && ! lib.hasPrefix "_" name))
        # prepend path with base directory
        (lib.mapAttrs (subDirName: _: (lib.path.append dir subDirName)))
        # build
        (lib.mapAttrs (caseName: sourcePath:
          if builtins.pathExists "${sourcePath}/default.nix" then
            casesSelf.callPackage sourcePath { }
          else
            build {
              inherit caseName sourcePath;
            })
        )
      ]);
    t1main = ./t1_main.S;
    linkerScript = ./t1.ld;

    stdenv = rv32-stdenv;
    xLen = 32;
    vLen = 1024;
    fp = false;

    mlir = casesSelf.callPackage ./mlir { };
    intrinsic = casesSelf.callPackage ./intrinsic { };
    asm = casesSelf.callPackage ./asm { };
    perf = casesSelf.callPackage ./perf { };
    codegen = casesSelf.callPackage ./codegen { };
  }));

  # remove non-case attributes in scope
  scopeStripped = {
    inherit (scope) mlir intrinsic asm perf codegen;
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
