// BUDDY-OPT
// --lower-affine --convert-scf-to-cf --convert-math-to-llvm
// --lower-vector-exp --lower-rvv=rv32
// --convert-vector-to-llvm --convert-memref-to-llvm
// --convert-arith-to-llvm --convert-func-to-llvm
// --reconcile-unrealized-casts
// BUDDY-OPT-END

// This implementation is based on [this file](https://github.com/buddy-compiler/buddy-mlir/blob/main/examples/RVVExperiment/rvv-vp-intrinsic-add.mlir) from buddy-mlir.

memref.global "private" @gv_i32 : memref<20xi32> = dense<[0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
                                                          10, 11, 12, 13, 14, 15, 16, 17, 18, 19]>
func.func @test() -> i32 {
  %mem_i32 = memref.get_global @gv_i32 : memref<20xi32>
  %c0 = arith.constant 0 : index
  %c1 = arith.constant 1 : index
  %c1_i32 = arith.constant 1 : i32
  %mask14 = arith.constant dense<[1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0]> : vector<16xi1>
  %mask16 = arith.constant dense<1> : vector<16xi1>
  %evl14 = arith.constant 14 : i32
  %evl16 = arith.constant 16 : i32

  %mask = arith.constant dense<1> : vector<16xi1>
  %evl = arith.constant 16 : i32
  %output0 = arith.constant 0 : i32

  //===---------------------------------------------------------------------------===//
  // Case 1: VP Intrinsic Add Operation + Fixed Vector Type + Mask Driven
  //===---------------------------------------------------------------------------===//

  %vec1 = vector.load %mem_i32[%c0] : memref<20xi32>, vector<16xi32>
  %vec2 = vector.load %mem_i32[%c0] : memref<20xi32>, vector<16xi32>
  %res_add_mask_driven = "llvm.intr.vp.add" (%vec2, %vec1, %mask14, %evl16) :
         (vector<16xi32>, vector<16xi32>, vector<16xi1>, i32) -> vector<16xi32>

  %res_add_mask_driven_reduce_add = "llvm.intr.vp.reduce.add" (%c1_i32, %res_add_mask_driven, %mask, %evl) :
        (i32, vector<16xi32>, vector<16xi1>, i32) -> i32
  %output1 = arith.addi %output0, %res_add_mask_driven_reduce_add : i32

  //===---------------------------------------------------------------------------===//
  // Case 2: VP Intrinsic Add Operation + Fixed Vector Type + EVL Driven
  //===---------------------------------------------------------------------------===//

  %vec3 = vector.load %mem_i32[%c0] : memref<20xi32>, vector<16xi32>
  %vec4 = vector.load %mem_i32[%c0] : memref<20xi32>, vector<16xi32>
  %res_add_evl_driven = "llvm.intr.vp.add" (%vec4, %vec3, %mask16, %evl14) :
         (vector<16xi32>, vector<16xi32>, vector<16xi1>, i32) -> vector<16xi32>

  %res_add_evl_driven_reduce_add = "llvm.intr.vp.reduce.add" (%c1_i32, %res_add_evl_driven, %mask, %evl) :
        (i32, vector<16xi32>, vector<16xi1>, i32) -> i32
  %output2 = arith.addi %output1, %res_add_evl_driven_reduce_add : i32
 
  //===---------------------------------------------------------------------------===//
  // Case 3: VP Intrinsic Reduce Add Operation + Fixed Vector Type + Mask Driven
  //===---------------------------------------------------------------------------===//

  %vec9 = vector.load %mem_i32[%c0] : memref<20xi32>, vector<16xi32>
  %res_reduce_add_mask_driven = "llvm.intr.vp.reduce.add" (%c1_i32, %vec9, %mask14, %evl16) :
         (i32, vector<16xi32>, vector<16xi1>, i32) -> i32
  %output3 = arith.addi %output2, %res_reduce_add_mask_driven : i32
  
  //===---------------------------------------------------------------------------===//
  // Case 4: VP Intrinsic Reduce Add Operation + Fixed Vector Type + EVL Driven
  //===---------------------------------------------------------------------------===//

  %vec10 = vector.load %mem_i32[%c0] : memref<20xi32>, vector<16xi32>
  %res_reduce_add_evl_driven = "llvm.intr.vp.reduce.add" (%c1_i32, %vec10, %mask16, %evl14) :
         (i32, vector<16xi32>, vector<16xi1>, i32) -> i32
  %output4 = arith.addi %output3, %res_reduce_add_evl_driven : i32

  return %output4 : i32
}
