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
    rev = "6f24d9aab1e64a452e3493c85297e9bde805e30c";
    hash = "sha256-5Ie5gZMOqxl8L8tuzpYdrqb/7F6o5UzOMGhn4xzPvQo=";
  };
  doCheck = false;
  vendorHash = "sha256-9cQlivpHg6IDYpmgBp34n6BR/I0FIYnmrXCuiGmAhNE=";
  # Get rid of copying the whole source
  postInstall = ''
    cp -r $src/configs $out/configs

    mkdir $out/include
    cp ${riscv-test-env}/encoding.h $out/include
  '';
}

