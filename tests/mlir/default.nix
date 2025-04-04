{
  linkerScript,
  buddy-mlir,
  makeBuilder,
  findAndBuild,
  getTestRequiredFeatures,
  t1main,
  jq,
}:

let
  builder = makeBuilder { casePrefix = "mlir"; };
  build =
    { caseName, sourcePath }:
    builder {
      inherit caseName;

      src = sourcePath;

      passthru.featuresRequired = getTestRequiredFeatures sourcePath;

      nativeBuildInputs = [ buddy-mlir ];

      buddyOptArgs = [
        "--lower-affine"
        "--convert-scf-to-cf"
        "--convert-math-to-llvm"
        "--lower-vector-exp"
        "--lower-rvv=rv32"
        "--convert-vector-to-llvm"
        "--finalize-memref-to-llvm"
        "--convert-arith-to-llvm"
        "--convert-func-to-llvm"
        "--reconcile-unrealized-casts"
      ];
      buddyTranslateArgs = [ "--buddy-to-llvmir" ];
      buddyLLCArgs = [
        "-mtriple=riscv32"
        "-target-abi=ilp32f"
        "-mattr=+m,+f,+zve32f"
        "-riscv-v-vector-bits-min=128"
      ];

      buildPhase = ''
        runHook preBuild

        echo "Running buddy-opt with args $buddyOptsArgs"
        buddy-opt ${caseName}.mlir $buddyOptArgs -o $pname-opt.mlir

        echo "Running buddy-translate with args $buddyTranslateArgs"
        buddy-translate $pname-opt.mlir $buddyTranslateArgs -o $pname.llvm

        echo "Running buddy-llc with args $buddyLLCArgs"
        buddy-llc $pname.llvm $buddyLLCArgs --filetype=asm -o $pname.S

        $CC -T${linkerScript} \
          ${caseName}.c $pname.S ${t1main} \
          -o $pname.elf

        if [ -f ${caseName}.json ]; then
          ${jq}/bin/jq -r '[.assert[] | "+assert " + .name] + [.tree[] | "+tree " + .name] + [.module[] | "+module " + .name] | .[]' \
              ${caseName}.json > $pname.cover
        else 
          echo "-assert *" > $pname.cover
        fi

        runHook postBuild
      '';

      meta.description = "testcase '${caseName}', written in MLIR";
    };
in
findAndBuild ./. build
