{ lib
, fetchMillDeps
, stdenvNoCC
, mill
, makeSetupHook
, writeText

, submodules
}:

let
  self = stdenvNoCC.mkDerivation rec {
    name = "t1-compile-cache";
    meta.description = "T1 mill compile cache. Internal build stage, don't use it.";

    src = (with lib.fileset; toSource {
      root = ./../..;
      fileset = unions [
        ./../../build.sc
        ./../../common.sc
        ./../../t1
        ./../../subsystem
        ./../../rocket
        ./../../emuhelper
        ./../../ipemu/src
        ./../../subsystememu
        ./../../fpga
        ./../../elaborator
      ];
    }).outPath;

    passthru = {
      millDeps = fetchMillDeps {
        inherit name;
        src = (with lib.fileset; toSource {
          root = ./../..;
          fileset = unions [
            ./../../build.sc
            ./../../common.sc
          ];
        }).outPath;
        millDepsHash = "sha256-3ueeJddftivvV5jQtg58sKKwXv0T2vkGxblenYFjrso=";
        nativeBuildInputs = [ submodules.setupHook ];
      };

      # Automatically copy and setup mill build output directory in this derivation into caller's source directory
      setupHook = makeSetupHook
        {
          name = "mill-output-cache-hook";
          propagatedBuildInputs = [ mill ];
        }
        (writeText "mill-output-cache-setup-hook" ''
          setupMillOut() {
            echo "Running setupHook from ${self.name}"

            mkdir -p $MILL_HOME/.mill/ammonite/
            cp ${self}/rt-*.jar $MILL_HOME/.mill/ammonite/

            cp -r ${self}/build-output "$sourceRoot"/out
            # Make sure the out directory is writable
            chmod -R u+w -- "$sourceRoot"/out
          }

          postUnpackHooks+=(setupMillOut)
        '');
    };

    nativeBuildInputs = [
      mill
      submodules.setupHook
      passthru.millDeps.setupHook
    ];

    shellHook = ''
      setupSubmodules
    '';

    buildPhase = ''
      runHook preBuild

      # --jobs 0 use all core
      mill -i --jobs 0 __.compile

      runHook postBuild
    '';

    dontFixup = true;

    installPhase = ''
      runHook preInstall

      mkdir -p $out
      mv out $out/build-output

      cp $MILL_HOME/.mill/ammonite/rt-*.jar $out/

      runHook postInstall
    '';
  };
in
self
