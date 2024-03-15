{ lib
, fetchFromGitHub
, callPackage
, runCommand
, makeWrapper
, symlinkJoin
, writeTextDir
, writeShellScriptBin

, python3
, openssh

, ip-emulator
, subsystem-rtl
, pkgsCross
, elaborateConfigJson
}:

rec {
  soc-simulator = callPackage ./soc-simulator.nix { inherit subsystem-rtl; };

  ip-emulator-wrapped = runCommand "ip-emulator"
    {
      nativeBuildInputs = [ makeWrapper ];
      src = (with lib.fileset; toSource {
        root = ./../../../scripts;
        fileset = unions [
          ./../../../scripts/run-test.py
          ./../../../scripts/_utils.py
        ];
      }).outPath;
    }
    ''
      mkdir -p $out/bin $out/share/python
      cp -r $src $out/share/python/t1-helper

      makeWrapper ${python3}/bin/python3 $out/bin/ip-emulator \
        --add-flags "$out/share/python/t1-helper/run-test.py" \
        --add-flags "ip" \
        --add-flags "--config ${elaborateConfigJson}" \
        --add-flags "--emulator-path ${ip-emulator}/bin/emulator"
    '';

  nobodyOpenssh = openssh.overrideAttrs (old: {
    configureFlags = old.configureFlags ++ [ "--with-privsep-user=nobody" ];
    doCheck = false;
  });

  fake-nss = symlinkJoin {
    name = "fake-nss";
    paths = [
      (writeTextDir "etc/passwd" ''
        root:x:0:0:root user:/workspace:/bin/bash
        nobody:x:65534:65534:nobody:/var/empty:/bin/sh
      '')
      (writeTextDir "etc/group" ''
        root:x:0:
        nobody:x:65534:
      '')
      (writeTextDir "etc/nsswitch.conf" ''
        hosts: files dns
      '')
      (runCommand "var-empty" { } ''
        mkdir -p $out/var/empty
      '')
    ];
  };

  demo-project = runCommand "patch-cyy-source"
    {
      src = fetchFromGitHub {
        owner = "cyyself";
        repo = "simple-sw-workbench";
        rev = "rv32-t1soc-v";
        hash = "sha256-Px1Nl4Vp+pe3Tgif3ByIGQdChep271LyfvEizCuHcHQ=";
      };
    }
    ''
      cp -r $src $out
      chmod u+w -R $out
      sed -i 's|riscv32-unknown-elf-|riscv32-none-elf-|' $out/Makefile
    '';

  addGHUserKey = writeShellScriptBin "add-gh-user-key" ''
    GHUSERS="$@"

    if [[ -z "$GHUSERS" ]]; then
      echo "No github username given"
      exit 1
    fi

    ghUserArray=($GHUSERS)
    for user in "''${ghUserArray[@]}"; do
      echo "Appending user $user into root account"
      curl -L "https://github.com/$user.keys" >> /etc/ssh/authorized_keys.d/root
    done
  '';

  docker-env = callPackage ./docker-env.nix {
    inherit subsystem-rtl soc-simulator elaborateConfigJson fake-nss demo-project addGHUserKey;

    ip-emulator = ip-emulator-wrapped;
    openssh = nobodyOpenssh;

    rv32-gcc = pkgsCross.riscv32-embedded.buildPackages.gcc;
  };
}
