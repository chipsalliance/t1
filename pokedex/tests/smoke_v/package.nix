{
  lib,
  rv32-stdenv,
  pokedex-compile-stubs,
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
  passthru.casesInfo =
    [ "vsetvl" ]
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
