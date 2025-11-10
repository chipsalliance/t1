{ rv32-stdenv, fetchFromGitHub }:
let
  suite_src = fetchFromGitHub {
    repo = "riscv-arch-test";
    owner = "riscv-non-isa";
    rev = "241b1fe294567751bd3ae6f338b1dbfc40c41a20";
    hash = "sha256-lWGmu9olZ/0IDZn9DquL/tlRXpbH/35BYZA3fMtys3Q=";
    postFetch = ''
      # Remove all the file except riscv-test-suite
      for f in "$out"/*; do
        if [[ "$f" != "$out/riscv-test-suite" ]]; then
          rm -r "$f"
        fi
      done
    '';
  };
in
rv32-stdenv.mkDerivation (finalAttr: {
  name = "riscv-arch-test-rv32-for-pokedex";

  src = ./.;

  env = {
    SUITE_SRC_DIR = "${suite_src}/riscv-test-suite";
  };

  passthru = { inherit suite_src; };

  installPhase = ''
    cp -r build "$out"
  '';
})
