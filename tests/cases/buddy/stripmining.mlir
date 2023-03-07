// Copied from https://github.com/xlinsist/buddy-mlir/blob/8b7ad2a79d05273e0e398dab7ae6c309fc60c811/examples/RVVDialect/test-i32-rvv-intr.mlir

module {
  memref.global "private" @gv_i32 : memref<20xi32> = dense<[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19]>
  memref.global "private" @gv2_i32 : memref<20xi32>
  func.func @test() -> i32 {
    %0 = memref.get_global @gv_i32 : memref<20xi32>
    %1 = builtin.unrealized_conversion_cast %0 : memref<20xi32> to !llvm.struct<(ptr<i32>, ptr<i32>, i64, array<1 x i64>, array<1 x i64>)>
    %c0 = arith.constant 0 : index
    %c0_i32 = arith.constant 0 : i32
    %c2_i32 = arith.constant 2 : i32
    %c2_i32_0 = arith.constant 2 : i32
    %c1_i32 = arith.constant 1 : i32
    %dim = memref.dim %0, %c0 : memref<20xi32>
    %2 = arith.index_cast %dim : index to i32
    %c0_1 = arith.constant 0 : index
    %3 = memref.get_global @gv2_i32 : memref<20xi32>
    %4 = builtin.unrealized_conversion_cast %3 : memref<20xi32> to !llvm.struct<(ptr<i32>, ptr<i32>, i64, array<1 x i64>, array<1 x i64>)>
    %5:2 = scf.while (%arg0 = %2, %arg1 = %c0_1) : (i32, index) -> (i32, index) {
      %8 = arith.cmpi sgt, %arg0, %c0_i32 : i32
      scf.condition(%8) %arg0, %arg1 : i32, index
    } do {
    ^bb0(%arg0: i32, %arg1: index):
      %8 = builtin.unrealized_conversion_cast %arg1 : index to i64
      %9 = "rvv.intr.vsetvli"(%arg0, %c2_i32_0, %c1_i32) : (i32, i32, i32) -> i32
      %10 = llvm.mlir.undef : vector<[8]xi32>
      %11 = llvm.extractvalue %1[1] : !llvm.struct<(ptr<i32>, ptr<i32>, i64, array<1 x i64>, array<1 x i64>)> 
      %12 = llvm.getelementptr %11[%8] : (!llvm.ptr<i32>, i64) -> !llvm.ptr<i32>
      %13 = llvm.bitcast %12 : !llvm.ptr<i32> to !llvm.ptr<vector<[8]xi32>>
      %14 = builtin.unrealized_conversion_cast %9 : i32 to i32
      %15 = "rvv.intr.vle"(%10, %13, %14) : (vector<[8]xi32>, !llvm.ptr<vector<[8]xi32>>, i32) -> vector<[8]xi32>
      %16 = llvm.mlir.undef : vector<[8]xi32>
      %17 = "rvv.intr.vadd"(%16, %15, %c2_i32, %9) : (vector<[8]xi32>, vector<[8]xi32>, i32, i32) -> vector<[8]xi32>
      %18 = llvm.extractvalue %4[1] : !llvm.struct<(ptr<i32>, ptr<i32>, i64, array<1 x i64>, array<1 x i64>)> 
      %19 = llvm.getelementptr %18[%8] : (!llvm.ptr<i32>, i64) -> !llvm.ptr<i32>
      %20 = llvm.bitcast %19 : !llvm.ptr<i32> to !llvm.ptr<vector<[8]xi32>>
      %21 = builtin.unrealized_conversion_cast %9 : i32 to i32
      "rvv.intr.vse"(%17, %20, %21) : (vector<[8]xi32>, !llvm.ptr<vector<[8]xi32>>, i32) -> ()
      %22 = arith.index_cast %9 : i32 to index
      %23 = arith.addi %arg1, %22 : index
      %24 = arith.subi %arg0, %9 : i32
      scf.yield %24, %23 : i32, index
    }
    %6 = vector.load %3[%c0] : memref<20xi32>, vector<20xi32>
    %cst = arith.constant dense<true> : vector<20xi1>
    %c1_i32_2 = arith.constant 1 : i32
    %c20_i32 = arith.constant 20 : i32
    %7 = "llvm.intr.vp.reduce.add"(%c1_i32_2, %6, %cst, %c20_i32) : (i32, vector<20xi32>, vector<20xi1>, i32) -> i32
    return %7 : i32
  }
}
