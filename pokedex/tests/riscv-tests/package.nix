{
  lib,
  riscv-tests,
  rv32-stdenv,
  pokedex-compile-stubs,
  mkDiffEnv,
  vlen ? 256,
}:
rv32-stdenv.mkDerivation (finalAttrs: {
  name = "riscv-tests-bins";

  src = ./.;

  # Variable that can be reused in nix develop
  env = {
    POKEDEX_COMPILE_STUBS = "${pokedex-compile-stubs}";
    RISCV_TESTS_SRC = "${riscv-tests}";
    VLEN = "${toString vlen}";
  };

  makeFlags = [
    "RISCV_PREFIX=${rv32-stdenv.targetPlatform.config}"
    "PREFIX=${placeholder "out"}"
  ];

  enableParallelBuilding = true;
  dontFixup = true;

  passthru.diff =
    lib.readFile ./case_list.txt
    |> lib.splitString "\n"
    |> lib.filter (line: line != "" && !(lib.hasPrefix "#" line))
    |> map (
      case:
      let
        fileName = lib.replaceStrings [ ".S" ] [ "" ] case;
        sanitizedName = lib.replaceStrings [ "/" ] [ "_" ] fileName;
      in
      {
        name = sanitizedName;
        value = mkDiffEnv {
          caseName = "${finalAttrs.name}+VLEN=${toString vlen}b.${sanitizedName}";
          casePath = "${finalAttrs.finalPackage}/bin/${fileName}.elf";
          caseDump = "${finalAttrs.finalPackage}/share/${fileName}.objdump";
        };
      }
    )
    |> lib.listToAttrs;
})
