{ stdenv, fetchurl }:

stdenv.mkDerivation rec {
  name = "rvv-testcase-unwrapped";
  version = "latest";
  src = fetchurl {
    url = "https://github.com/sequencer/vector/releases/download/${version}/rvv-testcase.tar.gz";
    sha256 = "sha256-zqDs+blxLrpDTVaRR9GFwu4t8E39T52rsc2eQhXLgpc=";
  };
  dontUnpack = true;
  installPhase = ''
    mkdir $out
    tar xzf $src -C $out
  '';
}
