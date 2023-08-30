{ stdenv, fetchurl }:

stdenv.mkDerivation rec {
  name = "rvv-testcase-unwrapped";
  version = "latest";
  src = fetchurl {
    url = "https://github.com/sequencer/vector/releases/download/${version}/rvv-testcase.tar.gz";
    sha256 = "sha256-hitMGx2ZZH/qzXZtPpgd9p7IgviDDzqska4jhSShsZk=";
  };
  dontUnpack = true;
  installPhase = ''
    mkdir $out
    tar xzf $src -C $out
  '';
}
