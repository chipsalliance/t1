let rs1 : XREG_TYPE = UInt(GetRS1(instruction));
let rd : freg_index = UInt(GetRD(instruction));
let rm_result : RM_Result = RM_from_bits(GetRM(instruction));
if !rm_result.valid then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

let op_result : FloatOpResult = i32_to_f32(X[rs1], rm_result.mode);
F[rd] = op_result.data;
set_fflags_from_softfloat(op_result.xcpt);

PC = PC + 4;

return Retired();
