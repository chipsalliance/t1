memref.global "private" @gv_i32 : memref<32768xi32>

func.func @test() -> i32 {
  %mem_i32 = memref.get_global @gv_i32 : memref<32768xi32>
  %c0 = arith.constant 0 : index
  %c0_i32 = arith.constant 0 : i32
  %i2 = arith.constant 2 : i32

  // Configure the register.
  // SEW = 32
  %sew = arith.constant 2 : i32
  // LMUL = 2
  %lmul = arith.constant 1 : i32

  %init_avl = memref.dim %mem_i32, %c0 : memref<32768xi32>
  %init_avl_i32 = arith.index_cast %init_avl : index to i32
  %init_idx = arith.constant 0 : index
  %res = memref.get_global @gv_i32 : memref<32768xi32>

  // While loop for strip-mining.
  %a1, %a2 = scf.while (%avl = %init_avl_i32, %idx = %init_idx) : (i32, index) -> (i32, index) {
    // If avl greater than zero.
    %cond = arith.cmpi sgt, %avl, %c0_i32 : i32
    // Pass avl, idx to the after region.
    scf.condition(%cond) %avl, %idx : i32, index
  } do {
  ^bb0(%avl : i32, %idx : index):
    // Perform the calculation according to the vl.
    %vl = rvv.setvl %avl, %sew, %lmul : i32
    %input_vector = rvv.load %mem_i32[%idx], %vl : memref<32768xi32>, vector<[8]xi32>, i32
    %result_vector = rvv.add %input_vector, %i2, %vl : vector<[8]xi32>, i32, i32
    rvv.store %result_vector, %res[%idx], %vl : vector<[8]xi32>, memref<32768xi32>, i32
    // Update idx and avl.
    %vl_ind = arith.index_cast %vl : i32 to index
    %new_idx = arith.addi %idx, %vl_ind : index
    %new_avl = arith.subi %avl, %vl : i32
    scf.yield %new_avl, %new_idx : i32, index
  }
  %result = vector.load %res[%c0] : memref<32768xi32>, vector<8xi32>

  %mask = arith.constant dense<1> : vector<8xi1>
  %c1_i32 = arith.constant 1 : i32
  %evl = arith.constant 8: i32
  %res_reduce_add_mask_driven = "llvm.intr.vp.reduce.add" (%c1_i32, %result, %mask, %evl) :
         (i32, vector<8xi32>, vector<8xi1>, i32) -> i32

  return %res_reduce_add_mask_driven : i32
}
