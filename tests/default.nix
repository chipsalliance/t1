{ lib
, newScope
, rv32-stdenv
, runCommand

, configName

, emulator
}:

assert lib.assertMsg (lib.isDerivation emulator) "emulator in malform, expect an derivation";
assert lib.assertMsg (emulator ? runEmulation) "emulator ${emulator.name} doesn't contains `runEmulation` hooks";
assert lib.assertMsg (builtins.typeOf emulator.runEmulation == "lambda") "emulator ${emulator.name} have wrong `runEmulation` hook";

let
  # TODO
  # In next PR, I would love to have `featureSet` became same as the `rtlDesignMetadata`,
  # which is populate from OM data.
  # Keep it here now, for compatibility so that we can do fast code iteraction.
  #
  # Assignee @Avimitin.
  featuresSet = {
    extensions = lib.splitString "_" emulator.rtlDesignMetadata.march;
    inherit (emulator.rtlDesignMetadata) march xlen vlen dlen;
  };

  # isSubSetOf m n: n is subset of m
  isSubsetOf = m: n: lib.all (x: lib.elem x m) n;

  # Return true if attribute in first argument exists in second argument, and the value is also equal.
  #
  # Example:
  #
  # hasIntersect { } { a = [1 2 3]; b = 4; }                 # true
  # hasIntersect { a = [1]; } { a = [1 2 3]; b = 4; }        # true
  # hasIntersect { a = [1]; b = 4; } { a = [1 2 3]; b = 4; } # true
  # hasIntersect { a = [4]; } { a = [1 2 3]; b = 4; }        # false
  # hasIntersect { c = 4; } { a = [1 2 3]; b = 4; }          # false
  #
  # hasIntersect :: AttrSet -> AttrSet -> Bool
  hasIntersect = ma: na: with builtins; let
    keysMa = attrNames ma;
    keysNa = attrNames na;
    intersectKeys = lib.filter (m: lib.elem m keysNa) keysMa;
    intersectValEquality = map
      (key:
        if typeOf (ma.${key}) == "list" then
          isSubsetOf na.${key} ma.${key}
        else ma.${key} == na.${key})
      intersectKeys;
  in
  (length keysMa == 0) ||
  ((length intersectKeys > 0) && all (isEqual: isEqual) intersectValEquality);

  scope = lib.recurseIntoAttrs (lib.makeScope newScope (casesSelf: {
    recurseForDerivations = true;

    inherit emulator featuresSet;

    makeBuilder = casesSelf.callPackage ./builder.nix { };

    # Read casePath/features-required.json to get extra feature information.
    # Like the requirement of zve32f, or requirement for higher vlen.
    # Empty list means no extra requirement for RTL design, then the baseline zve32x will be used.
    #
    # TODO: check user specified features are correct or not
    getTestRequiredFeatures = sourcePath:
      let
        extraFeatures = lib.path.append sourcePath "features-required.json";
      in
      if lib.pathExists extraFeatures then
        builtins.fromJSON (lib.fileContents extraFeatures)
      else { };

    filterByFeatures = caseName: caseDrv:
      assert lib.assertMsg (caseDrv ? featuresRequired) "${caseName} doesn't have features specified";
      # Test the case required extensions is supported by rtl design
      hasIntersect caseDrv.featuresRequired featuresSet;

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
        (lib.filterAttrs casesSelf.filterByFeatures)
      ]);
    t1main = ./t1_main.S;
    linkerScript = ./t1.ld;

    stdenv = rv32-stdenv;

    mlir = casesSelf.callPackage ./mlir { };
    intrinsic = casesSelf.callPackage ./intrinsic { };
    asm = casesSelf.callPackage ./asm { };
    perf = casesSelf.callPackage ./perf { };
    codegen = casesSelf.callPackage ./codegen { };
    rvv_bench = casesSelf.callPackage ./rvv_bench { };
    pytorch = casesSelf.callPackage ./pytorch { };
  }));

  # remove non-case attributes in scope
  scopeStripped = {
    inherit (scope) mlir intrinsic asm perf codegen rvv_bench pytorch;
  };

  # This derivation is for internal CI use only.
  # We have a large test suite used in CI, but resolving each test individually is too slow for production.
  # This "fake" derivation serves as a workaround, making all tests dependencies of this single derivation.
  # This allows Nix to resolve the path only once, while still pulling all tests into the local Nix store.
  _allEmuResult =
    let
      testPlan = builtins.fromJSON
        (lib.readFile ../.github/cases/${configName}/default.json);
      # flattern the attr set to a list of test case derivations
      # AttrSet (AttrSet Derivation) -> List Derivation
      allCases = lib.filter
        (val: lib.isDerivation val && lib.hasAttr val.pname testPlan)
        (lib.concatLists (map lib.attrValues (lib.attrValues scopeStripped)));
      script = ''
        mkdir -p $out
      '' + (lib.concatMapStringsSep "\n"
        (caseDrv: ''
          _caseOutDir=$out/${caseDrv.pname}
          mkdir -p "$_caseOutDir"
          cp ${caseDrv.emu-result}/perf.txt "$_caseOutDir"/
          cp ${caseDrv.emu-result}/offline-check-status "$_caseOutDir"/
          cp ${caseDrv.emu-result}/offline-check-journal "$_caseOutDir"/
        '')
        allCases);
    in
    runCommand
      "catch-${configName}-all-emu-result-for-ci"
      { }
      script;

  _allVCSEmuResult =
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
          cp ${caseDrv.emu-result}/offline-check-* "$_caseOutDir"/
        '')
        allCases);
    in
    runCommand "catch-${configName}-all-vcs-emu-result-for-ci" { } script;

  _all =
    let
      allCases = lib.filter
        lib.isDerivation
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
    runCommand
      "build-all-testcases"
      { }
      script;
in
lib.recurseIntoAttrs (scopeStripped // { inherit _all _allEmuResult _allVCSEmuResult; })
