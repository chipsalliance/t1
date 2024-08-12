{ lib
, linkerScript
, buddy-mlir
, makeBuilder
, findAndBuild
, getTestRequiredFeatures
, t1main
}:

let

  builder = makeBuilder { casePrefix = "mlir"; };
  build = { caseName, sourcePath }:
    let
      buddyBuildConfig = import (sourcePath + "/config.nix");
      defaultBuddyTranslateArgs = [ "--buddy-to-llvmir" ];
      defaultBuddyLLCArgs = [
        "-mtriple=riscv32"
        "-target-abi=ilp32f"
        "-mattr=+m,+f,+zve32f"
        "-riscv-v-vector-bits-min=128"
      ];
    in
    builder rec {
      inherit caseName;

      src = sourcePath;

      passthru.featuresRequired = getTestRequiredFeatures sourcePath;

      nativeBuildInputs = [ buddy-mlir.pyenv buddy-mlir ];

      pythonArgs = buddyBuildConfig.pythonArgs or [ ];
      buddyTranslateArgs = buddyBuildConfig.buddyTranslateArgs or defaultBuddyTranslateArgs;
      buddyLLCArgs = buddyBuildConfig.buddyLLCArgs or defaultBuddyLLCArgs;
      buddyIncludes = buddyBuildConfig.includes or [ ];

      postUnpack = ''
        buddyIncludeDir="."
        if [ "x$buddyIncludes" != "x" ]; then
          mkdir -p buddyInclude
          _buddyHeaderArray=( $buddyIncludes )
          for h in "''${_buddyHeaderArray}"; do
            cp -v "$h" buddyInclude/"$(stripHash $h)"
          done

          buddyIncludeDir=$PWD/buddyInclude
        fi
      '';

      buildPhase = ''
        runHook preBuild

        echo "Running python with args $pythonArgs"
        python $pythonArgs ${caseName}.py

        # Generate multiple buddy-opt call, each will read input from former pipeline
        # For example, for buddyOptArgs = [ [ "--arg-a" ], [ "--arg-b" ], [ "--arg-c" ] ]
        # This will generate
        #
        #   echo "..."
        #   buddy-opt forward.mlir --arg-a -o forward-1.mlir
        #   echo "..."
        #   buddy-opt forward-1.mlir --arg-b -o forward-2.mlir
        #   echo "..."
        #   buddy-opt forward-2.mlir --arg-c -o forward-3.mlir
        #
        ${lib.concatStringsSep "\n" (
          lib.imap0
          (idx: args: ''
            echo "Running buddy-opt with args ${lib.escapeShellArgs args}"
            buddy-opt \
              forward${if idx == 0 then "" else "-${toString idx}"}.mlir \
              ${lib.escapeShellArgs args} \
              -o forward-${toString (idx+1)}.mlir
          '')
          buddyBuildConfig.buddyOptArgs
        )}

        # Pick up the last optimized MLIR file
        echo "Running buddy-translate with args $buddyTranslateArgs"
        buddy-translate forward-${with builtins; toString (length buddyBuildConfig.buddyOptArgs)}.mlir \
          $buddyTranslateArgs -o forward.ll

        echo "Running buddy-llc with args $buddyLLCArgs"
        buddy-llc forward.ll $buddyLLCArgs --filetype=obj -o forward.o

        echo "Using include dir $buddyIncludeDir"
        $CXX -nostdlib -I$buddyIncludeDir -c ${caseName}.cc -o host.o
        $CC -T${linkerScript} \
          host.o forward.o ${t1main} \
          -o $pname.elf

        runHook postBuild
      '';

      meta.description = "testcase '${caseName}', written in MLIR";
    };
in
findAndBuild ./. build
