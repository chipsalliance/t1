{ lib
, configName
, elaborateConfig
, newScope
, rv32-stdenv
, runCommand
, verilator-emu
, verilator-emu-trace
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

    inherit verilator-emu verilator-emu-trace;

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

  # This derivation is for internal CI use only.
  # We have a large test suite used in CI, but resolving each test individually is too slow for production.
  # This "fake" derivation serves as a workaround, making all tests dependencies of this single derivation.
  # This allows Nix to resolve the path only once, while still pulling all tests into the local Nix store.
  _allEmuResult =
    let
      testPlan = builtins.fromJSON (lib.readFile ../.github/cases/${configName}/default.json);
      # flattern the attr set to a list of test case derivations
      # AttrSet (AttrSet Derivation) -> List Derivation
      allCases = lib.filter (val: lib.isDerivation val && lib.hasAttr val.pname testPlan)
        (lib.concatLists (map lib.attrValues (lib.attrValues scopeStripped)));
      script = ''
        mkdir -p $out
      '' + (lib.concatMapStringsSep "\n"
        (caseDrv: ''
          _caseOutDir=$out/${caseDrv.pname}
          mkdir -p "$_caseOutDir"
          cp ${caseDrv.emu-result.with-offline}/perf.txt "$_caseOutDir"/
          cp ${caseDrv.emu-result.with-offline}/offline-check-status "$_caseOutDir"/
          cp ${caseDrv.emu-result.with-offline}/offline-check-journal "$_caseOutDir"/
        '')
        allCases);
    in
    runCommand "catch-${configName}-all-emu-result-for-ci" { } script;

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
lib.recurseIntoAttrs (scopeStripped // { inherit all _allEmuResult; })
