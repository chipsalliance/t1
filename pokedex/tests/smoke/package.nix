{
  rv32-stdenv,
  pokedex-compile-stubs,
}:
rv32-stdenv.mkDerivation (finalAttrs: {
  name = "pokedex-smoke-tests";

  src = ./.;

  # Variable that can be reused in nix develop
  env = {
    POKEDEX_COMPILE_STUBS = "${pokedex-compile-stubs}";
  };

  makeFlags = [
    "RISCV_PREFIX=${rv32-stdenv.targetPlatform.config}"
    "PREFIX=${placeholder "out"}"
  ];
  dontFixup = true;

  passthru.casesInfo =
    [
      "mul"
      "addi"
    ]
    |> map (
      case:
      let
        fileName = "${case}.elf";
      in
      {
        caseName = "${finalAttrs.name}/${fileName}";
        path = "${finalAttrs.finalPackage}/bin/${fileName}";
      }
    );
})
