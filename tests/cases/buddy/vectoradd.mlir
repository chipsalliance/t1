// BUDDY-OPT
// --lower-affine --convert-scf-to-cf --convert-math-to-llvm
// --lower-vector-exp --lower-rvv=rv32
// --convert-vector-to-llvm --convert-memref-to-llvm
// --convert-arith-to-llvm --convert-func-to-llvm
// --reconcile-unrealized-casts
// BUDDY-OPT-END

memref.global "private" @gv_i32 : memref<32768xi32>

func.func @test() -> i32 {
  %c0 = arith.constant 0 : index

  %input1 = memref.get_global @gv_i32 : memref<32768xi32>
  %input2 = memref.get_global @gv_i32 : memref<32768xi32>
  %output = memref.get_global @gv_i32 : memref<32768xi32>

  %c0_i32 = arith.constant 0 : i32
  %dim = memref.dim %input1, %c0 : memref<32768xi32>
  %dim_i32 = arith.index_cast %dim : index to i32

  // Configure the register.
  // SEW = 32
  %sew = arith.constant 2 : i32
  // LMUL = 2
  %lmul = arith.constant 1 : i32

  // Constant mask configuration.
  %mask4 = arith.constant dense<1> : vector<[4]xi1>

  // While loop for strip-mining.
  %tmpAVL, %tmpIdx = scf.while (%avl = %dim_i32, %idx = %c0) : (i32, index) -> (i32, index) {
    // If avl greater than zero.
    %cond = arith.cmpi sgt, %avl, %c0_i32 : i32
    // Pass avl, idx to the after region.
    scf.condition(%cond) %avl, %idx : i32, index
  } do {
  ^bb0(%avl : i32, %idx : index):
    // Perform the calculation according to the vl.
    %vl = rvv.setvl %avl, %sew, %lmul : i32
    %vec_input1 = vector_exp.predication %mask4, %vl : vector<[4]xi1>, i32 {
      %ele = vector.load %input1[%idx] : memref<32768xi32>, vector<[4]xi32>
      vector.yield %ele : vector<[4]xi32>
    } : vector<[4]xi32>
    %vec_input2 = vector_exp.predication %mask4, %vl : vector<[4]xi1>, i32 {
      %ele = vector.load %input2[%idx] : memref<32768xi32>, vector<[4]xi32>
      vector.yield %ele : vector<[4]xi32>
    } : vector<[4]xi32>
    %result_vector = rvv.add %vec_input1, %vec_input2, %vl : vector<[4]xi32>, vector<[4]xi32>, i32
    vector_exp.predication %mask4, %vl : vector<[4]xi1>, i32 {
      vector.store %result_vector, %output[%idx] : memref<32768xi32>, vector<[4]xi32>
      vector.yield
    } : () -> ()
    // Update idx and avl.
    %vl_ind = arith.index_cast %vl : i32 to index
    %new_idx = arith.addi %idx, %vl_ind : index
    %new_avl = arith.subi %avl, %vl : i32
    scf.yield %new_avl, %new_idx : i32, index
  }

  %result = vector.load %output[%c0] : memref<32768xi32>, vector<8xi32>

  %mask = arith.constant dense<1> : vector<8xi1>
  %c1_i32 = arith.constant 1 : i32
  %evl = arith.constant 8 : i32
  %res_reduce_add_mask_driven = "llvm.intr.vp.reduce.add" (%c1_i32, %result, %mask, %evl) :
        (i32, vector<8xi32>, vector<8xi1>, i32) -> i32

  return %res_reduce_add_mask_driven : i32
}
