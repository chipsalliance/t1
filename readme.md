# Vector

This is a RISC-V Vector RTL generator, supporting Zvl1024b,Zve32x extension for now.
More documentation will be released after the first release.

## Nix setup

We use nix flake to setup test environment. If you have not installed nix, install it following the [guide](https://nixos.org/manual/nix/stable/installation/installing-binary.html), and enable flake following the [wiki](https://nixos.wiki/wiki/Flakes#Enable_flakes). For example:

Install with package manager e.g. on ArchLinux:
```shell
pacman -S nix
```
or with installation script:
```
sh <(curl -L https://nixos.org/nix/install)
```

Add flake to configurations:
```shell
echo "experimental-features = nix-command flakes" >> ~/.config/nix/nix.conf
```

Enable nix-daemon systemd service (if your package manager has not enabled it automatically):
```shell
systemd start nix-daemon.service
```

After nix is installed, run `nix develop` to enter the development shell, all environment variables and dependencies is included. If you want a pure environment without system packages, use `env -i nix develop` instead.

### Note on “I have no name” Problem
If you are using dynamic user, you may experience bash or other programs complaining about “I have no name” when entering nix shell. This is caused by that when `nscd` started inside nix environments for `getent` query, glibc will query `nsswtich`, but nss modules from outside nix is not visible to nix, causing glibc failing to find the user.

As a workaround, you can start `nscd` or `nsncd` outside nix environment, such as:
```shell
systemd start nscd.service
```
or use `nsncd` in Arch Linux instead
```shell
paru -S nsncd-git
systemd start nsncd.service
```

## Test
We use [spike](https://github.com/riscv/riscv-isa-sim) for reference model.  
In nix development shell, run with `mill -i tests.smoketest.run --cycles 100` for a single test.  
The simulator record events of vector register file and memory load store to perform online difftest.  
The simulator use spike to emulate the RISC-V core, which means this vector generator doesn't provide a hart implmentation.  

## Patches
<!-- BEGIN-PATCH -->
musl https://github.com/sequencer/musl/compare/master...riscv32.diff
chisel3 https://github.com/chipsalliance/chisel3/compare/master...circt_aop.diff
<!-- END-PATCH -->
