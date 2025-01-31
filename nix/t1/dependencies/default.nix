{ pkgs
, fetchMillDeps
, publishMillJar
, git
, makeSetupHook
, writeText
, lib
, newScope
}:


let
  submodules = lib.filterAttrs (_: v: v ? src) (pkgs.callPackage ./_sources/generated.nix { });
  makeRemote = module: "git@github.com:${module.src.owner}/${module.src.repo}.git";
in
lib.makeScope newScope (scope: {
  setupHook = makeSetupHook { name = "submodules-setup.sh"; } (writeText "submodules-setup.sh" (''
    _setupOneSubmodule() {
      src="$1"
      name="$2"

      echo "[nix-shell] linking '$src' to 'dependencies/$name'"
      ln -sfT "$src" "dependencies/$name"
    }

    _setupOneSubmoduleEditable() {
      name="$1"; shift
      remote="$1"; shift
      rev="$1"; shift
      depDir="dependencies/$name"

      if [ -d "$depDir" -a ! -L "$depDir" ]; then
        echo "[nix-shell] ignored existing submodule directory '$depDir', remove them if you want a update"
      else 
        if [ -L "$depDir" ]; then
          echo "[nix-shell] replacing symlink '$depDir' with full git worktree"
          rm "$depDir"
        else
          echo "[nix-shell] fetching submodule $name"
        fi

        git clone $remote $depDir
        git -C $depDir -c advice.detachedHead=false checkout $rev
      fi
    }

    setupSubmodules() {
      mkdir -p dependencies
  '' + lib.concatLines (lib.mapAttrsToList (k: v: "_setupOneSubmodule '${v.src}' '${k}'") submodules) + ''
    }

    # for use of shellHook
    setupSubmodulesEditable() {
      mkdir -p dependencies
  '' + lib.concatLines (lib.mapAttrsToList (k: v: "_setupOneSubmoduleEditable '${k}' '${makeRemote v}' '${v.src.rev}'") submodules) + ''
    }
    prePatchHooks+=(setupSubmodules)
  ''));
  sources = submodules;

  ivy-chisel =
    let
      chiselDeps = fetchMillDeps {
        name = "chisel-snapshot";
        src = submodules.chisel.src;
        millDepsHash = "sha256-0uiuY4UlR4Kfwz6AhQ4njc5cg0b3S2AYoeQxmmKPr0k=";
      };
    in
    publishMillJar {
      name = "chisel-snapshot";
      src = submodules.chisel.src;

      publishTargets = [
        "unipublish"
      ];

      buildInputs = [
        chiselDeps.setupHook
      ];

      nativeBuildInputs = [
        # chisel requires git to generate version
        git
      ];

      passthru = {
        inherit chiselDeps;
      };
    };

  ivy-arithmetic =
    let
      arithmeticDeps = fetchMillDeps {
        name = "arithmetic-snapshot";
        src = submodules.arithmetic.src;
        buildInputs = [ scope.ivy-chisel.setupHook ];
        millDepsHash = "sha256-WNfY4zlLk+/sUoRXQsL0PBHZ5Fz8bFnpAFueJjNiSYI=";
      };
    in
    publishMillJar {
      name = "arithmetic-snapshot";
      src = submodules.arithmetic.src;

      publishTargets = [
        "arithmetic[snapshot]"
      ];

      buildInputs = [
        arithmeticDeps.setupHook
        scope.ivy-chisel.setupHook
      ];

      passthru = {
        inherit arithmeticDeps;
      };
    };

  ivy-chisel-interface =
    let
      chiselInterfaceDeps = fetchMillDeps {
        name = "chisel-interface-snapshot";
        src = submodules.chisel-interface.src;
        buildInputs = [ scope.ivy-chisel.setupHook ];
        millDepsHash = "sha256-Ktow0COOz+HDHOU2AIVaqsidHCPGjT7J+pdgpSGH0DM=";
      };
    in
    publishMillJar {
      name = "chiselInterface-snapshot";
      src = submodules.chisel-interface.src;

      publishTargets = [
        "jtag[snapshot]"
        "axi4[snapshot]"
        "dwbb[snapshot]"
      ];

      nativeBuildInputs = [ git ];

      buildInputs = [
        chiselInterfaceDeps.setupHook
        scope.ivy-chisel.setupHook
      ];

      passthru = {
        inherit chiselInterfaceDeps;
      };
    };

  ivy-rvdecoderdb =
    let
      rvdecoderdbDeps = fetchMillDeps {
        name = "rvdecoderdb-snapshot";
        src = submodules.rvdecoderdb.src;
        millDepsHash = "sha256-j6ixFkxLsm8ihDLFkkJDePVuS2yVFmX3B1gOUVguCHQ=";
      };
    in
    publishMillJar {
      name = "rvdecoderdb-snapshot";
      src = submodules.rvdecoderdb.src;

      publishTargets = [
        "rvdecoderdb.jvm"
      ];

      buildInputs = [
        rvdecoderdbDeps.setupHook
      ];

      nativeBuildInputs = [
        # rvdecoderdb requires git to generate version
        git
      ];

      passthru = {
        inherit rvdecoderdbDeps;
      };
    };

  ivy-hardfloat =
    let
      hardfloatSrc = ../../../dependencies/berkeley-hardfloat;
      hardfloatDeps = fetchMillDeps {
        name = "hardfloat-snapshot";
        src = hardfloatSrc;
        buildInputs = [ scope.ivy-chisel.setupHook ];
        millDepsHash = "sha256-lYV/BHKXpX1ssI3pZIBlzfsBclgwxVCE/TQJq/eeOcY=";
      };
    in
    publishMillJar {
      name = "hardfloat-snapshot";
      src = hardfloatSrc;

      publishTargets = [
        "hardfloat[snapshot]"
      ];

      buildInputs = [
        hardfloatDeps.setupHook
        scope.ivy-chisel.setupHook
      ];

      nativeBuildInputs = [
        # hardfloat requires git to generate version
        git
      ];

      passthru = {
        inherit hardfloatDeps;
      };
    };
})
