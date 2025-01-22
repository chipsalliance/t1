{ stdenv, fetchFromGitHub }:
stdenv.mkDerivation rec {
  pname = "softfloat";
  version = "080c31c72d5c3fd813584ea990e8a3aa10e902eb";
  src = fetchFromGitHub {
    owner = "ucb-bar";
    repo = "berkeley-softfloat-3";
    rev = version;
    sha256 = "sha256-0/xmH5ku2ftUIAU5k6/yydCC2uDv2MKh/Nel1MiPSOE=";
  };
  buildPhase = ''
    make -C build/Linux-x86_64-GCC SPECIALIZE_TYPE=RISCV TESTFLOAT_OPTS="-DFLOAT64 -DFLOAT_ROUND_ODD" softfloat.a
  '';
  installPhase = ''
    mkdir -p $out/lib
    mkdir -p $out/include
    mv build/Linux-x86_64-GCC/softfloat.a $out/lib/
    cp source/include/* $out/include
  '';
}
