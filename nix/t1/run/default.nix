{ lib
, callPackage
, runCommand
, verilator-emu
, verilator-emu-trace
, vcs-emu
, vcs-emu-trace
, cases
, configName
}:
let
  runVerilatorEmu = callPackage ./run-verilator-emu.nix { };
  runVCSEmu = callPackage ./run-vcs-emu.nix { };

  # cases is now { mlir = { hello = ...; ...  }; ... }
  emuAttrs = lib.pipe cases [
    (lib.filterAttrs (_: category: builtins.typeOf category == "set"))
    (lib.mapAttrs
      # Now we have { category = "mlir", caseSet = { hello = ... } }
      (category: caseSet:
        let
          cleanCaseSet = lib.filterAttrs
            (_: drv:
              lib.isDerivation drv
              && drv ? pname
              && (builtins.length (lib.splitString "." drv.pname) == 2)
            )
            caseSet;
          innerMapper = caseName: case: {
            verilator-emu = runVerilatorEmu verilator-emu case;
            verilator-emu-trace = runVerilatorEmu verilator-emu-trace case;
            vcs-emu = runVCSEmu vcs-emu case;
            vcs-emu-trace = runVCSEmu vcs-emu-trace case;
          };
        in
        # Now we have { caseName = "hello", case = <derivation> }
        (lib.mapAttrs innerMapper cleanCaseSet)))
  ];
  # cases is now { mlir = { hello = <verilator-emu-result>, ... = <verilator-emu-result> }; ... }

  _getAllResult = emuType:
    let
      testPlan = builtins.fromJSON
        (lib.readFile ../../../.github/${configName}/default.json);
      # flattern the attr set to a list of test case derivations
      # AttrSet (AttrSet Derivation) -> List Derivation
      allCasesResult = lib.pipe emuAttrs [
        # For { case = { name = { vcs-emu = <drv> } } }
        # Drop the outer scope, get the [ { name = { vcs-emu = <drv> } }, ... ]
        (lib.attrValues)
        # Drop the inner scope, get the [ [{ vcs-emu = <drv> }, ...], ... ]
        (map lib.attrValues)
        # Concat list, get the [ { vcs-emu = <drv> }, ... ]
        (lib.concatLists)
        (map (attr: lib.filterAttrs (n: _: n == emuType) attr))
        # Drop the emu scope, get the [ [<drv>, ...], ... ]
        (map lib.attrValues)
        # Concat list, get the [ <drv>, ... ]
        (lib.concatLists)
        # Filter lists, get only CI specify tests run result
        (lib.filter (val: lib.isDerivation val && lib.hasAttr val.caseName testPlan))
      ];
      script = ''
        mkdir -p $out
      '' + (lib.concatMapStringsSep "\n"
        (caseDrv: ''
          _caseOutDir=$out/${caseDrv.caseName}
          mkdir -p "$_caseOutDir"

          if [ -r ${caseDrv}/perf.json ]; then
            cp -v ${caseDrv}/perf.json "$_caseOutDir"/
          fi

          cp -v ${caseDrv}/offline-check-* "$_caseOutDir"/

          if [ -d ${caseDrv}/cm.vdb ]; then
            cp -vr ${caseDrv}/cm.vdb "$_caseOutDir"/
          fi
        '')
        allCasesResult);
    in
    runCommand "catch-${configName}-all-emu-result-for-ci" { } script;

  _vcsEmuResult = runCommand "get-vcs-emu-result" { __noChroot = true; emuOutput = _getAllResult "vcs-emu"; } ''
    cp -vr $emuOutput $out
    chmod -R u+w $out

    ${vcs-emu.vcs-fhs-env}/bin/vcs-fhs-env -c "urg -dir $emuOutput/*/cm.vdb -format text"
    cp -vr urgReport $out/
  '';
in
emuAttrs // { inherit _vcsEmuResult; }
