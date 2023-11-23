{ stdenv, fetchurl }:

stdenv.mkDerivation rec {
  name = "rvv-testcases-prebuilt";
  version = "2023-11-23+41e999a";
  src = fetchurl {
    url = "https://github.com/chipsalliance/t1/releases/download/${version}/rvv-testcases.tar.gz";
    sha256 = "sha256-dAfImndVAFPJfqcGEaPZCvkZlumenz4SXwOr2lKCvsM=";
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
