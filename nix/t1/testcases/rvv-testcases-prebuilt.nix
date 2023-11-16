{ stdenv, fetchurl }:

stdenv.mkDerivation rec {
  name = "rvv-testcases-prebuilt";
  version = "2023-09-03+25d3212";
  src = fetchurl {
    url = "https://github.com/chipsalliance/t1/releases/download/${version}/rvv-testcases.tar.gz";
    sha256 = "sha256-zqDs+blxLrpDTVaRR9GFwu4t8E39T52rsc2eQhXLgpc=";
  };
  dontUnpack = true;
  installPhase = ''
    mkdir $out
    tar xzf $src -C $out
  '';

  # Nix provided utilities doesn't recognize this pre-built static linked ELF.
  dontStrip = true;
  dontPatchELF = true;
}
