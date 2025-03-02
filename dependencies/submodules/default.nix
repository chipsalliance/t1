{ pkgs
, mill-ivy-fetcher
, publishMillJar
, git
, makeSetupHook
, writeText
, lib
, newScope
, circt-full
, jextract-21
}:


let
  sources = lib.filterAttrs (_: v: v ? src) (pkgs.callPackage ./_sources/generated.nix { });
in
lib.makeScope newScope (scope: {
  inherit sources;

  ivy-chisel =
    let
      ivyCache = mill-ivy-fetcher.generateIvyCache {
        name = "chisel";
        src = sources.chisel.src;
        hash = "sha256-cbkWLoNqYizz8doTK1gZQdEGvCMk/TKJf05ITup20xA=";
        targets = [ "unipublish" ];
      };
    in
    publishMillJar {
      name = "chisel-snapshot";
      src = sources.chisel.src;

      publishTargets = [
        "unipublish"
      ];

      buildInputs = ivyCache.cache.ivyDepsList;

      nativeBuildInputs = [
        # chisel requires git to generate version
        git
      ];

      passthru = {
        inherit ivyCache;
      };
    };

  ivy-chisel-panama =
    publishMillJar {
      name = "chisel-panama-snapshot";
      src = sources.chisel.src;

      publishTargets = [
        "panamaconverter.cross[2.13.15]"
        "panamaom.cross[2.13.15]"
        "panamalib.cross[2.13.15]"
        "circtpanamabinding"
      ];

      buildInputs = [
        scope.ivy-chisel.setupHook
      ] ++ scope.ivy-chisel.ivyCache.cache.ivyDepsList;

      env = {
        CIRCT_INSTALL_PATH = circt-full;
        JEXTRACT_INSTALL_PATH = jextract-21;
      };

      nativeBuildInputs = [
        # chisel requires git to generate version
        git

        circt-full
        jextract-21
      ];
    };

  ivy-arithmetic =
    let
      ivyCache = mill-ivy-fetcher.generateIvyCache {
        name = "arithmetic";
        src = sources.arithmetic.src;
        hash = "sha256-Sb5rwRnlZUBoKGKw7Sh8XOirKS15c7VE9HM61BupMTc=";
        targets = [ "arithmetic[snapshot]" ];
        extraBuildInputs = [ scope.ivy-chisel.setupHook ];
      };
    in
    publishMillJar {
      name = "arithmetic-snapshot";
      src = sources.arithmetic.src;

      publishTargets = [
        "arithmetic[snapshot]"
      ];

      buildInputs = [
        scope.ivy-chisel.setupHook
      ] ++ ivyCache.cache.ivyDepsList;

      passthru = {
        inherit ivyCache;
      };
    };

  ivy-chisel-interface =
    let
      ivyCache = mill-ivy-fetcher.generateIvyCache {
        name = "chisel-interface";
        src = sources.chisel-interface.src;
        hash = "sha256-Mvm4sqNB3RTZTH+cgUetigFytLOTX52x9O1CffNc4P4=";
        targets = [
          "jtag[snapshot]"
          "axi4[snapshot]"
          "dwbb[snapshot]"
        ];
        extraBuildInputs = [ scope.ivy-chisel.setupHook ];
      };
    in
    publishMillJar {
      name = "chiselInterface-snapshot";
      src = sources.chisel-interface.src;

      publishTargets = [
        "jtag[snapshot]"
        "axi4[snapshot]"
        "dwbb[snapshot]"
      ];

      nativeBuildInputs = [ git ];

      buildInputs = [
        scope.ivy-chisel.setupHook
      ] ++ ivyCache.cache.ivyDepsList;

      passthru = {
        inherit ivyCache;
      };
    };

  ivy-rvdecoderdb =
    let
      ivyCache = mill-ivy-fetcher.generateIvyCache {
        name = "rvdecoderdb";
        src = sources.rvdecoderdb.src;
        hash = "sha256-7eabXxM+THNIZ1ph8bncSXJOkva97bnQ5Vsv/UnIuhI=";
        targets = [ "rvdecoderdb.jvm" ];
      };
    in
    publishMillJar {
      name = "rvdecoderdb-snapshot";
      src = sources.rvdecoderdb.src;

      publishTargets = [
        "rvdecoderdb.jvm"
      ];

      buildInputs = ivyCache.cache.ivyDepsList;

      nativeBuildInputs = [
        # rvdecoderdb requires git to generate version
        git
      ];

      passthru = {
        inherit ivyCache;
      };
    };

  ivy-hardfloat =
    let
      hardfloatSrc = ../berkeley-hardfloat;
      ivyCache = mill-ivy-fetcher.generateIvyCache {
        name = "hardfloat";
        src = hardfloatSrc;
        hash = "sha256-UeDo8LwyfLGOWH5FvjfIXC3fLLlFCJLhzQIUi7GUeWg=";
        targets = [ "hardfloat[snapshot]" ];
        extraBuildInputs = [ scope.ivy-chisel.setupHook ];
      };
    in
    publishMillJar {
      name = "hardfloat-snapshot";
      src = hardfloatSrc;

      publishTargets = [
        "hardfloat[snapshot]"
      ];

      buildInputs = [
        scope.ivy-chisel.setupHook
      ] ++ ivyCache.cache.ivyDepsList;

      nativeBuildInputs = [
        # hardfloat requires git to generate version
        git
      ];

      passthru = {
        inherit ivyCache;
      };
    };

  riscv-opcodes = makeSetupHook { name = "setup-riscv-opcodes-src"; } (writeText "setup-riscv-opcodes-src.sh" ''
    setupRiscvOpcodes() {
      mkdir -p dependencies
      ln -sfT "${sources.riscv-opcodes.src}" "dependencies/riscv-opcodes"
    }
    prePatchHooks+=(setupRiscvOpcodes)
  '');
})
