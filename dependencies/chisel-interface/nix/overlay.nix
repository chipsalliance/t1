{ self }: final: prev: {
  circt = self.inputs.nixpkgs-for-circt.legacyPackages."${final.system}".circt;
  espresso = final.callPackage ./pkgs/espresso.nix { };
  fetchMillDeps = final.callPackage ./pkgs/mill-builder.nix { };
  circt-full = final.callPackage ./pkgs/circt-full.nix { };
  submodules = final.callPackage ./submodules.nix { };
  chisel-interface = final.callPackage ./chisel-interface.nix { };
  mill = let jre = final.jdk21; in
    (prev.mill.override { inherit jre; }).overrideAttrs (_: {
      passthru = { inherit jre; };
    });
}
