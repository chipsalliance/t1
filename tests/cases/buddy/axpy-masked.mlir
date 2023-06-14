// BUDDY-OPT
// --lower-affine --convert-scf-to-cf --convert-math-to-llvm
// --lower-vector-exp --lower-rvv=rv32
// --convert-vector-to-llvm --finalize-memref-to-llvm
// --convert-arith-to-llvm --convert-func-to-llvm
// --reconcile-unrealized-casts
// BUDDY-OPT-END

#map0 = affine_map<(d0) -> (d0)>
#map1 = affine_map<(d0) -> (d0 ceildiv 64)>

memref.global "private" @gv_i32 : memref<4100xi32> // 4100 = 128 * 32 + 4

func.func @test() -> i32 {
  
  %input1 = memref.get_global @gv_i32 : memref<4100xi32>
  
  %input2 = memref.get_global @gv_i32 : memref<4100xi32>
  %output = memref.get_global @gv_i32 : memref<4100xi32>

  %c0 = arith.constant 0 : index
  %c0_i32 = arith.constant 0 : i32
  %c0_vector = arith.constant dense<0> : vector<64xi32>
  %c64 = arith.constant 64 : index
  %dim = memref.dim %input1, %c0 : memref<4100xi32>

  %a_vector = affine.vector_load %input1[%c0] : memref<4100xi32>, vector<64xi32>

  affine.for %idx = #map0(%c0) to #map1(%dim) {
    %curlen = arith.muli %idx, %c64 : index
    %remain = arith.subi %dim, %curlen : index
    %cmp = arith.cmpi sge, %remain, %c64 : index
    scf.if %cmp {
      %x_vector = affine.vector_load %input1[%idx * 64] : memref<4100xi32>, vector<64xi32>
      %y_vector = affine.vector_load %input2[%idx * 64] : memref<4100xi32>, vector<64xi32>
      %mul_vector = arith.muli %x_vector, %a_vector : vector<64xi32>
      %result_vector = arith.addi %mul_vector, %y_vector : vector<64xi32>
      affine.vector_store %result_vector, %output[%idx * 64] : memref<4100xi32>, vector<64xi32>
    } else {
      // TODO: `vector.create_mask` operation will result in the error "spike trapped with trap_illegal_instruction", which needs further analysis.
      // %mask64 = vector.create_mask %remain : vector<64xi1>
      %mask64 = arith.constant dense<1> : vector<64xi1>
      %remain_i32 = arith.index_cast %remain : index to i32
      %x_vector = vector.maskedload %input1[%curlen], %mask64, %c0_vector : memref<4100xi32>, vector<64xi1>, vector<64xi32> into vector<64xi32>
      %y_vector = vector.maskedload %input2[%curlen], %mask64, %c0_vector : memref<4100xi32>, vector<64xi1>, vector<64xi32> into vector<64xi32>
      %mul_vector = arith.muli %x_vector, %a_vector : vector<64xi32>
      %result_vector = arith.addi %mul_vector, %y_vector : vector<64xi32>
      vector.maskedstore %output[%curlen], %mask64, %result_vector : memref<4100xi32>, vector<64xi1>, vector<64xi32>
    }
  }

  %result = vector.load %output[%c0] : memref<4100xi32>, vector<8xi32>

  %mask_res = arith.constant dense<1> : vector<8xi1>
  %c1_i32 = arith.constant 1 : i32
  %evl = arith.constant 8 : i32
  %res_reduce_add_mask_driven = "llvm.intr.vp.reduce.add" (%c1_i32, %result, %mask_res, %evl) :
        (i32, vector<8xi32>, vector<8xi1>, i32) -> i32

  return %res_reduce_add_mask_driven : i32
}
