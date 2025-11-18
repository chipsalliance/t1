{
  lib,
  rv32-stdenv,
  pokedex-compile-stubs,
  mkDiffEnv,
  vlen ? 256,
}:
rv32-stdenv.mkDerivation (finalAttrs: {
  name = "pokedex-smoke-v-tests";

  src = ./.;

  # Variable that can be reused in nix develop
  env = {
    POKEDEX_COMPILE_STUBS = "${pokedex-compile-stubs}";
    VLEN = "${toString vlen}";
  };

  makeFlags = [
    "RISCV_PREFIX=${rv32-stdenv.targetPlatform.config}"
    "PREFIX=${placeholder "out"}"
  ];

  dontFixup = true;
  passthru.diff =
    [ "vsetvl" ]
    |> map (
      case:
      let
        fileName = "${lib.replaceStrings [ "." ] [ "_" ] case}";
      in
      {
        name = case;
        value = mkDiffEnv {
          caseName = "${finalAttrs.name}+VLEN=${toString vlen}b.${fileName}";
          casePath = "${finalAttrs.finalPackage}/bin/${fileName}.elf";
          caseDump = "${finalAttrs.finalPackage}/share/${fileName}.objdump";
        };
      }
    )
    |> builtins.listToAttrs;
})
