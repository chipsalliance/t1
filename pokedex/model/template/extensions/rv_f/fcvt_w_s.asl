let rs1 : freg_index = UInt(GetRS1(instruction));
let rd : XREG_TYPE = UInt(GetRD(instruction));

let rm_result : RM_Result = RM_from_bits(GetRM(instruction));
if !rm_result.valid then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

let result : FloatCvtResult = f32_to_i32(F[rs1], rm_result.mode);
X[rd] = result.data[31:0];
set_fflags_from_softfloat(result.xcpt);

PC = PC + 4;

return Retired();
