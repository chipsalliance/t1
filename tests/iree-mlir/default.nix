{ linkerScript
, iree
, makeBuilder
, findAndBuild
, getTestRequiredFeatures
, t1main
, jq
}:

let
  builder = makeBuilder { casePrefix = "iree-mlir"; };
  build = { caseName, sourcePath }:
    builder {
      inherit caseName;

      src = sourcePath;

      passthru.featuresRequired = getTestRequiredFeatures sourcePath;

      nativeBuildInputs = [ iree ];

      ireeCompileArgs = [
          "--iree-stream-partitioning-favor=min-peak-memory"
          "--iree-hal-target-backends=llvm-cpu"
          "--iree-llvmcpu-target-triple=riscv32-pc-linux-elf"
          "--iree-llvmcpu-target-cpu=generic-rv32"
          "--iree-llvmcpu-target-cpu-features=+m,+f"
          "--iree-llvmcpu-target-abi=ilp32"
          "--iree-llvmcpu-debug-symbols=false"
      ];

      buildPhase = ''
        runHook preBuild

        echo "Running iree-compile"
        iree-compile $ireeCompileArgs ${caseName}.mlir -o ${caseName}.vmfb

        runHook postBuild
      '';

      meta.description = "testcase '${caseName}', written in MLIR";
    };
in
findAndBuild ./. build
