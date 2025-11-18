# TODO:
#   1. Spike argument should be configurable
{
  lib,
  runCommand,

  # Test Suites
  smoke-tests,
  smoke-v-tests,
  riscv-tests-bins,
  riscv-vector-tests-bins,
}:
let
  testSuites =
    [
      smoke-tests
      smoke-v-tests
      riscv-tests-bins
      riscv-vector-tests-bins
    ]
    |> map (
      drv:
      let
        unpackDrvFromAttrs = lib.mapAttrsToList (
          _: value:
          if builtins.typeOf value != "set" then
            [ ]
          else if lib.isDerivation value then
            [ value ]
          else
            unpackDrvFromAttrs value
        );
      in
      assert drv ? diff;
      unpackDrvFromAttrs drv.diff
    )
    |> lib.flatten;
in
runCommand "all-pokedex-tests-diffs"
  {
    buildInputs =
      assert lib.all (d: lib.isDerivation d) testSuites;
      testSuites;
  }
  ''
    echo "all tests pass" | tee $out
  ''
