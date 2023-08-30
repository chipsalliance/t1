{ stdenv, fetchurl }:

stdenv.mkDerivation rec {
  name = "rvv-testcase-unwrapped";
  version = "latest";
  src = fetchurl {
    url = "https://github.com/sequencer/vector/releases/download/${version}/rvv-testcase.tar.gz";
    sha256 = "sha256-Dbs22BnNaX+EEeWH+sEy4e7LIUv2pxW89cEsHW0Op2Q=";
  };
  dontUnpack = true;
  installPhase = ''
    mkdir $out
    tar xzf $src -C $out
  '';
}
