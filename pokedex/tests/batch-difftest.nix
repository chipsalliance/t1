# TODO:
#   1. Spike argument should be configurable
{
  lib,
  callPackage,
  runCommand,

  # Test Suites
  smoke-tests,
  smoke-v-tests,
  riscv-tests-bins,
  riscv-vector-tests-bins,
}:
let
  runner = callPackage ./runner.nix { };

  allTests =
    [
      smoke-tests
      smoke-v-tests
      riscv-tests-bins
      riscv-vector-tests-bins
    ]
    |> map (
      drv:
      assert drv ? casesInfo;
      drv.casesInfo
    )
    |> lib.flatten;

  startBuild =
    _:
    allTests
    |> map (caseInfo: runner { inherit caseInfo; })
    |> map (drv: "echo '${drv} build success'\n")
    |> builtins.toString;
in
runCommand "batch-run-all-cases-for-pokedex" { } ''
  ${startBuild true}

  echo "all tests pass"
  echo "pass" > $out
''
