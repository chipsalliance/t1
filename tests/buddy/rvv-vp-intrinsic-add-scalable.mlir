// This implementation is based on [this file](https://github.com/buddy-compiler/buddy-mlir/blob/main/examples/RVVExperiment/rvv-vp-intrinsic-add-scalable.mlir) from buddy-mlir.

memref.global "private" @gv_i32 : memref<20xi32> = dense<[0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
                                                          10, 11, 12, 13, 14, 15, 16, 17, 18, 19]>

func.func @test() -> i32 {
  %mem_i32 = memref.get_global @gv_i32 : memref<20xi32>
  %c0 = arith.constant 0 : index
  %c1 = arith.constant 1 : index
  %evl16 = arith.constant 16 : i32
  %evl14 = arith.constant 14 : i32
  %c1_i32 = arith.constant 1 : i32

  // Configure the register.
  // SEW = 32
  %sew = arith.constant 2 : i32
  // LMUL = 4
  %lmul = arith.constant 2 : i32
  // AVL = 14 / 16
  %avl14 = arith.constant 14 : i32
  %avl16 = arith.constant 16 : i32

  // Load vl elements.
  %vl14 = rvv.setvl %avl14, %sew, %lmul : i32
  %vl14_idx = arith.index_cast %vl14 : i32 to index
  %vl16 = rvv.setvl %avl16, %sew, %lmul : i32
  %vl16_idx = arith.index_cast %vl16 : i32 to index
  %load_vec1_i32 = rvv.load %mem_i32[%c0], %vl16 : memref<20xi32>, vector<[8]xi32>, i32
  %load_vec2_i32 = rvv.load %mem_i32[%c0], %vl16 : memref<20xi32>, vector<[8]xi32>, i32

  // Create the mask.
  %mask_scalable14 = vector.create_mask %vl14_idx : vector<[8]xi1>
  %mask_scalable16 = vector.create_mask %vl16_idx : vector<[8]xi1>

  %mask_reduce_sum = arith.constant dense<1> : vector<16xi1>
  %evl_reduce_sum = arith.constant 16 : i32
  %output0 = arith.constant 0 : i32

  //===---------------------------------------------------------------------------===//
  // Case 1: VP Intrinsic Add Operation + Scalable Vector Type + Mask Driven
  //===---------------------------------------------------------------------------===//

  %res_add_mask_driven = "llvm.intr.vp.add" (%load_vec2_i32, %load_vec1_i32, %mask_scalable14, %vl16) :
      (vector<[8]xi32>, vector<[8]xi32>, vector<[8]xi1>, i32) -> vector<[8]xi32>

  %res_add_mask_driven_mem = memref.get_global @gv_i32 : memref<20xi32>
  rvv.store %res_add_mask_driven, %res_add_mask_driven_mem[%c0], %vl16 : vector<[8]xi32>, memref<20xi32>, i32

  %res_add_mask_driven_vec = vector.load %res_add_mask_driven_mem[%c0] : memref<20xi32>, vector<16xi32>
  %res_add_mask_driven_reduce_add = "llvm.intr.vp.reduce.add" (%c1_i32, %res_add_mask_driven_vec, %mask_reduce_sum, %evl_reduce_sum) :
        (i32, vector<16xi32>, vector<16xi1>, i32) -> i32
  %output1 = arith.addi %output0, %res_add_mask_driven_reduce_add : i32

  //===---------------------------------------------------------------------------===//
  // Case 2: VP Intrinsic Add Operation + Scalable Vector Type + EVL Driven
  //===---------------------------------------------------------------------------===//

  %res_add_evl_driven = "llvm.intr.vp.add" (%load_vec2_i32, %load_vec1_i32, %mask_scalable16, %vl14) :
      (vector<[8]xi32>, vector<[8]xi32>, vector<[8]xi1>, i32) -> vector<[8]xi32>

  %res_add_evl_driven_mem = memref.get_global @gv_i32 : memref<20xi32>
  rvv.store %res_add_evl_driven, %res_add_evl_driven_mem[%c0], %vl16 : vector<[8]xi32>, memref<20xi32>, i32

  %res_add_evl_driven_vec = vector.load %res_add_evl_driven_mem[%c0] : memref<20xi32>, vector<16xi32>
  %res_add_evl_driven_reduce_add = "llvm.intr.vp.reduce.add" (%c1_i32, %res_add_evl_driven_vec, %mask_reduce_sum, %evl_reduce_sum) :
        (i32, vector<16xi32>, vector<16xi1>, i32) -> i32
  %output2 = arith.addi %output1, %res_add_evl_driven_reduce_add : i32

  //===---------------------------------------------------------------------------===//
  // Case 3: VP Intrinsic Reduce Add Operation + Scalable Vector Type + Mask Driven
  //===---------------------------------------------------------------------------===//

  %res_reduce_add_mask_driven = "llvm.intr.vp.reduce.add" (%c1_i32, %load_vec1_i32, %mask_scalable14, %vl16) :
      (i32, vector<[8]xi32>, vector<[8]xi1>, i32) -> i32

  %output3 = arith.addi %output2, %res_reduce_add_mask_driven : i32

  //===-------------------------------------------------------------------------===//
  // Case 4: VP Intrinsic Reduce Add Operation + Scalable Vector Type + EVL Driven
  //===-------------------------------------------------------------------------===//

  %res_reduce_add_evl_driven = "llvm.intr.vp.reduce.add" (%c1_i32, %load_vec1_i32, %mask_scalable16, %vl14) :
      (i32, vector<[8]xi32>, vector<[8]xi1>, i32) -> i32

  %output4 = arith.addi %output3, %res_reduce_add_evl_driven : i32

  return %output4 : i32
}
