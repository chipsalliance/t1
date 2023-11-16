{ pkgs, makeSetupHook, writeText, lib }:

let
  submodules = lib.filterAttrs (_: v: v ? src) (pkgs.callPackage ./_sources/generated.nix { });
  makeRemote = module: "git@github.com:${module.src.owner}/${module.src.repo}.git";
in
{
  setupHook = makeSetupHook { name = "submodules-setup.sh"; } (writeText "submodules-setup.sh" (''
    _setupOneSubmodule() {
      src="$1"
      name="$2"

      echo "[nix-shell] linking '$src' to 'dependencies/$name'"
      ln -sfT "$src" "dependencies/$name"
    }

    _setupOneSubmoduleEditable() {
      src="$1"
      name="$2"
      remote="$3"
      depDir="dependencies/$name"

      if [ -d "$depDir" -a ! -L "$depDir" ]; then
        echo "[nix-shell] ignored existing submodule directory '$depDir', remove them if you want a update"
      else 
        if [ -L "$depDir" ]; then
          echo "[nix-shell] replacing symlink '$depDir' with '$src'"
          rm "$depDir"
        else
          echo "[nix-shell] copying '$src' to '$depDir'"
        fi
        cp -rT "$src" "$depDir"
        chmod +w -R "$depDir"
      fi
    }

    setupSubmodules() {
      mkdir -p dependencies
  '' + lib.concatLines (lib.mapAttrsToList (k: v: "_setupOneSubmodule '${v.src}' '${k}'") submodules) + ''
    }

    # for use of shellHook
    setupSubmodulesEditable() {
      mkdir -p dependencies
  '' + lib.concatLines (lib.mapAttrsToList (k: v: "_setupOneSubmoduleEditable '${v.src}' '${k}' '${makeRemote v}'") submodules) + ''
    }
    prePatchHooks+=(setupSubmodules)
  ''));
}
