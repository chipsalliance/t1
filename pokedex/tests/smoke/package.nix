{
  rv32-stdenv,
  mkDiffEnv,
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

  passthru.diff =
    [
      "mul"
      "addi"
    ]
    |> map (case: {
      name = case;
      value = mkDiffEnv {
        caseName = "${finalAttrs.name}.${case}";
        casePath = "${finalAttrs.finalPackage}/bin/${case}.elf";
        caseDump = "${finalAttrs.finalPackage}/share/${case}.objdump";
      };
    })
    |> builtins.listToAttrs;
})
