{ fetchFromGitHub, buildGoModule }:
buildGoModule {
  pname = "riscv-vector-test";
  version = "unstable-2024-12-16";
  src = fetchFromGitHub {
    owner = "chipsalliance";
    repo = "riscv-vector-tests";
    rev = "52ccb798b355d4442f5d86a710e6bfa0eeb20cc2";
    hash = "sha256-vfr7iMkqy3QUjkfM1by4RYUMpPHIsOk0XZSW8Big39s=";
    fetchSubmodules = true;
  };
  doCheck = false;
  vendorHash = "sha256-1A5yCj9AJHp9tcUIpLKamXn4Tl3KPFEtzke5X7h6V+4=";
  # Get rid of copying the whole source
  postInstall = ''
    cp -r $src/configs $out/configs

    mkdir $out/include
    cp env/riscv-test-env/encoding.h $out/include
  '';
}
