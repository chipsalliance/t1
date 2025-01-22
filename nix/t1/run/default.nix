{ lib
, callPackage
, runCommand
, snps-fhs-env
, verilator-emu
, verilator-emu-trace
, vcs-emu
, vcs-emu-rtlink
, vcs-emu-cover
, vcs-emu-trace
, vcs-dpi-lib
, cases
, configName
, topName
}:
let
  runEmu = callPackage ./run-emulator.nix { };
  runFsdb2vcd = callPackage ./run-fsdb2vcd.nix { };

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
          innerMapper = caseName: testCase: {
            verilator-emu = runEmu {
              emulator = verilator-emu;
              emuDriverArgs = [ "+t1_elf_file=${testCase}/bin/${testCase.pname}.elf" ];
              inherit testCase;
            };
            verilator-emu-trace = runEmu {
              emulator = verilator-emu-trace;
              emuDriverArgs = [
                "+t1_elf_file=${testCase}/bin/${testCase.pname}.elf"
                "+t1_wave_path=$out/wave.fst"
              ];
              inherit testCase;
            };

            vcs-emu = runEmu {
              emulator = vcs-emu-rtlink;
              dpilib = vcs-dpi-lib;
              emuDriverArgs = [
                "-exitstatus"
                "-assert"
                "global_finish_maxfail=10000"
                "+t1_elf_file=${testCase}/bin/${testCase.pname}.elf"
                "-sv_root"
                "${vcs-dpi-lib}/lib"
                "-sv_lib"
                "${vcs-dpi-lib.svLibName}"
              ];
              inherit testCase;
            };

            vcs-emu-cover = runEmu {
              emulator = vcs-emu-cover;
              emuDriverArgs = [
                "-exitstatus"
                "-assert"
                "global_finish_maxfail=10000"
                "+t1_elf_file=${testCase}/bin/${testCase.pname}.elf"
                "-cm"
                "assert"
                "-assert"
                "hier=${testCase}/${testCase.pname}.cover"
              ];
              inherit testCase;

              postInstall = ''
                ${snps-fhs-env}/bin/snps-fhs-env -c "urg -dir cm.vdb -format text -metric assert -show summary"
                # TODO: add a flag to specify 'vdb only generated in ci mode'
                cp -vr cm.vdb $out/
                cp -vr urgReport $out/
              '';
            };

            vcs-emu-trace = runEmu {
              emulator = vcs-emu-trace;
              emuDriverArgs = [
                "-exitstatus"
                "-assert"
                "global_finish_maxfail=10000"
                "+t1_elf_file=${testCase}/bin/${testCase.pname}.elf"
                "+t1_wave_path=${testCase.pname}.fsdb"
              ];
              postInstall = ''
                cp -v ${testCase.pname}.fsdb $out/
              '';
              inherit testCase;
            };

            vcs-prof-vcd = runFsdb2vcd (runEmu {
              emulator = vcs-emu-trace;
              emuDriverArgs = [
                "-exitstatus"
                "-assert"
                "global_finish_maxfail=10000"
                "+t1_elf_file=${testCase}/bin/${testCase.pname}.elf"
                "+t1_wave_path=$out/${testCase.pname}.fsdb"
              ];
              inherit testCase;
            });
          };
        in
        # Now we have { caseName = "hello", case = <derivation> }
        (lib.mapAttrs innerMapper cleanCaseSet)))
  ];
  # cases is now { mlir = { hello = <verilator-emu-result>, ... = <verilator-emu-result> }; ... }

  _getAllResult = { testPlanDir, emuType, postCopied ? "", ... }@args:
    let
      testPlan = builtins.fromJSON
        (lib.readFile ../../../.github/${testPlanDir}/${configName}/${topName}.json);
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
        allCasesResult)
      + postCopied;
      runCommandArgs = builtins.removeAttrs args [ "testPlanDir" "emuType" "postCopied" ];
    in
    runCommand "catch-${configName}-all-emu-result-for-ci" runCommandArgs script;

  _vcsEmuResult = _getAllResult {
    testPlanDir = "designs";
    emuType = "vcs-emu-cover";
    postCopied = ''
      ${vcs-emu.snps-fhs-env}/bin/snps-fhs-env -c "urg -dir $out/*/cm.vdb -format text -metric assert -show summary"
      cp -vr urgReport $out/
    '';
    __noChroot = true;
  };

  _verilatorEmuResult = _getAllResult {
    testPlanDir = "verilator";
    emuType = "verilator-emu";
  };
in
emuAttrs // { inherit _vcsEmuResult _verilatorEmuResult; }
