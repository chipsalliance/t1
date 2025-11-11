{
  lib,
  riscv-vector-tests,
  riscv-tests,
  rv32-stdenv,
  pokedex-compile-stubs,
  vlen ? 256,
}:
rv32-stdenv.mkDerivation (finalAttrs: {
  name = "riscv-vector-tests@testset";

  src = ./.;

  # Variable that can be reused in nix develop
  env = {
    CODEGEN_INSTALL_DIR = "${riscv-vector-tests}";
    POKEDEX_COMPILE_STUBS = "${pokedex-compile-stubs}";
    RISCV_TESTS_SRC = "${riscv-tests}";
    VLEN = "${toString vlen}";
  };

  makeFlags = [
    "RISCV_PREFIX=${rv32-stdenv.targetPlatform.config}"
    "PREFIX=${placeholder "out"}"
  ];

  dontFixup = true;
  passthru.casesInfo =
    lib.readFile ./case_list.txt
    |> lib.splitString "\n"
    |> lib.filter (line: line != "" && !(lib.hasPrefix "#" line))
    |> map (
      case:
      let
        fileName = "${lib.replaceStrings [ "." ] [ "_" ] case}.elf";
      in
      {
        caseName = "${finalAttrs.name}@${toString vlen}b/${fileName}";
        path = "${finalAttrs.finalPackage}/bin/${fileName}";
      }
    );
})
