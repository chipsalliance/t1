// BUDDY-OPT
// --lower-affine --convert-scf-to-cf --convert-math-to-llvm
// --lower-vector-exp --lower-rvv=rv32
// --convert-vector-to-llvm --finalize-memref-to-llvm
// --convert-arith-to-llvm --convert-func-to-llvm
// --reconcile-unrealized-casts
// BUDDY-OPT-END

memref.global "private" @input_A : memref<1500xi32>
memref.global "private" @input_B : memref<1500xi32>
memref.global "private" @output : memref<1500xi32>

#map_1 = affine_map<(d)[B, N] -> (N*d + B)>

func.func @test() -> i32 {
  // for (i = 0; i < n; i++) C[i] = A[i] + B[i]
  // use MAXVL as a fix vector length
  // use setvl to do tail-processing
  %A = memref.get_global @input_A : memref<1500xi32>
  %B = memref.get_global @input_B : memref<1500xi32>
  %C = memref.get_global @output : memref<1500xi32>
  %n = arith.constant 1500 : i32

  // just need 2 vec-reg, use SEV=32, LMUL=8
  // e32 = 0b010, m8 = 0b011, vscale = [16] 
  %sew = arith.constant 2 : i32
  %lmul = arith.constant 3 : i32
  %maxvl = "rvv.setvl"(%n, %sew, %lmul) : (i32, i32, i32) -> i32
  %maxvl_idx = arith.index_cast %maxvl : i32 to index


  %iter_end = arith.divui %n, %maxvl : i32
  %rem = arith.remui %n, %maxvl : i32

  %c0 = arith.constant 0 : i32
  %c0_idx = arith.constant 0 : index
  
  %tail = arith.cmpi ne, %rem, %c0 : i32
  scf.if %tail {
    %new_vl = "rvv.setvl"(%rem, %sew, %lmul) : (i32, i32, i32) -> i32

    %A_vec = "rvv.load"(%A, %c0_idx, %new_vl) : (memref<1500xi32>, index, i32) -> vector<[16]xi32>
    %B_vec = "rvv.load"(%B, %c0_idx, %new_vl) : (memref<1500xi32>, index, i32) -> vector<[16]xi32>
    %sum = "rvv.add"(%A_vec, %B_vec, %new_vl) : (vector<[16]xi32>, vector<[16]xi32>, i32) -> vector<[16]xi32>
    "rvv.store"(%sum, %C, %c0_idx, %new_vl) : (vector<[16]xi32>, memref<1500xi32>, index, i32) -> ()
  }

  %new_maxvl = "rvv.setvl"(%n, %sew, %lmul) : (i32, i32, i32) -> i32
  %new_maxvl_idx = arith.index_cast %new_maxvl : i32 to index
  %iter_end_idx = arith.index_cast %iter_end : i32 to index
  %rem_idx = arith.index_cast %rem : i32 to index
  affine.for %i_ = 0 to %iter_end_idx step 1 {
    // i = REM + i_ * MAXVL, this make loop for i_ be a normalized loop 
    %i = affine.apply #map_1(%i_)[%rem_idx, %new_maxvl_idx]

    %A_vec = "rvv.load"(%A, %i, %new_maxvl) : (memref<1500xi32>, index, i32) -> vector<[16]xi32>
    %B_vec = "rvv.load"(%B, %i, %new_maxvl) : (memref<1500xi32>, index, i32) -> vector<[16]xi32>
    %sum = "rvv.add"(%A_vec, %B_vec, %new_maxvl) : (vector<[16]xi32>, vector<[16]xi32>, i32) -> vector<[16]xi32>
    "rvv.store"(%sum, %C, %i, %new_maxvl) : (vector<[16]xi32>, memref<1500xi32>, index, i32) -> ()
  }

  %ret = arith.constant 0 : i32
  return %ret : i32
}
