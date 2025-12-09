{
  lib,
  riscv-vector-tests,
  riscv-tests,
  rv32-stdenv,
  pokedex-compile-stubs,
  mkDiffEnv,
  pokedex-configs,
}:
rv32-stdenv.mkDerivation (finalAttrs: {
  name = "riscv-vector-tests-bins";

  src = ./.;

  # Variable that can be reused in nix develop
  env = {
    CODEGEN_INSTALL_DIR = "${riscv-vector-tests}";
    POKEDEX_COMPILE_STUBS = "${pokedex-compile-stubs}";
    RISCV_TESTS_SRC = "${riscv-tests}";
    VLEN = "${toString pokedex-configs.profile.vlen}";
    XLEN = "${toString pokedex-configs.profile.xlen}";
    MARCH = "${pokedex-configs.profile.march}";
    ABI = "${pokedex-configs.profile.abi}";
  };

  makeFlags =
    [
      "RISCV_PREFIX=${rv32-stdenv.targetPlatform.config}"
      "PREFIX=${placeholder "out"}"
    ]
    ++ (
      if (pokedex-configs.profile.ext ? zve32f) then
        [ "CODEGEN_TARGETS_DEFINE=case_list.txt fp_case_list.txt" ]
      else
        [ "CODEGEN_TARGETS_DEFINE=case_list.txt" ]
    );

  enableParallelBuilding = true;
  dontFixup = true;

  passthru.diff =
    lib.readFile ./case_list.txt
    |> lib.splitString "\n"
    |> lib.filter (line: line != "" && !(lib.hasPrefix "#" line))
    |> map (
      case:
      let
        fileName = "${lib.replaceStrings [ "." ] [ "_" ] case}";
      in
      {
        name = fileName;
        value = mkDiffEnv {
          caseName = "${finalAttrs.name}+VLEN=${finalAttrs.env.VLEN}b.${fileName}";
          casePath = "${finalAttrs.finalPackage}/bin/${fileName}.elf";
          caseDump = "${finalAttrs.finalPackage}/share/${fileName}.objdump";
        };
      }
    )
    |> lib.listToAttrs;
})
