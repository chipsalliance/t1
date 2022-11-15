{
  description = "vector";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }@inputs:
    let
      overlay = import ./overlay.nix;
    in
    flake-utils.lib.eachDefaultSystem
      (system:
      let
        pkgs = import nixpkgs { inherit system; overlays = [ overlay ]; };
        deps = with pkgs; [
          my-cc-wrapper  # note: this should be put before bintools, otherwise clang may found incorrect ld
          rv32-clang
          myLLVM.clang
          myLLVM.llvm
          myLLVM.bintools

          libargs glog fmt libspike

          mill python3
          gnused coreutils gnumake gnugrep which
          parallel protobuf ninja verilator antlr4 numactl dtc glibc_multi cmake
          espresso
          circt

          git cacert # make cmake fetchContent happy
        ];
      in
        {
          devShell = pkgs.mkShellNoCC {
            buildInputs = deps;
            shellHook = ''
              # waiting for https://github.com/NixOS/nixpkgs/pull/192943
              export NIX_CC=" "
              export RT="${pkgs.rv32-compilerrt}"
              export MUSL="${pkgs.rv32-musl}"
              export SPIKE="${pkgs.libspike}"
            '';
          };
        }
      )
    // { inherit inputs; overlays.default = overlay; };
}
