{ stdenv, fetchurl }:

stdenv.mkDerivation rec {
  name = "rvv-testcase-unwrapped";
  version = "2023-10-30+a8c7e76";
  src = fetchurl {
    url = "https://github.com/chipsalliance/t1/releases/download/${version}/rvv-testcase.tar.gz";
    sha256 = "sha256-fVBB8o38bc6pk93wTVusu6y+auhn47qQjDv1zVi3p3I=";
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
