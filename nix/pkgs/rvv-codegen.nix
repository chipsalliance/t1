{ fetchFromGitHub, buildGoModule }:
let
  riscv-test-env = fetchFromGitHub {
    owner = "riscv";
    repo = "riscv-test-env";
    rev = "1c577dc7c7d6aee27b8d5cb0e2e87c8473e3ad12";
    hash = "sha256-JZ7Yn4cTel9uVo6uGu0zs3IMMySoGzjSMW544YLYips=";
  };
in
buildGoModule {
  pname = "riscv-vector-test";
  version = "unstable-2023-04-12";
  src = fetchFromGitHub {
    owner = "chipsalliance";
    repo = "riscv-vector-tests";
    rev = "52cd47332565d21685127b670077a080faffc7f8";
    hash = "sha256-c9zByCQzSHCUfwtDoWV7CxEuyFMhX/5GmkulpQZi1QQ=";
  };
  doCheck = false;
  vendorHash = "sha256-1A5yCj9AJHp9tcUIpLKamXn4Tl3KPFEtzke5X7h6V+4=";
  # Get rid of copying the whole source
  postInstall = ''
    cp -r $src/configs $out/configs

    mkdir $out/include
    cp ${riscv-test-env}/encoding.h $out/include
  '';
}

