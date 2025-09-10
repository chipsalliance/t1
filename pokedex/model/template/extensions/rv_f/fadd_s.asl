let rs1 : freg_index = UInt(GetRS1(instruction));
let rs2 : freg_index = UInt(GetRS2(instruction));
let rd : freg_index = UInt(GetRD(instruction));
let rm_result : RM_Result = RM_from_bits(GetRM(instruction));
if !rm_result.valid then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

let op_result : FloatOpResult = f32_add(F[rs1], F[rs2], rm_result.mode);
set_fflags_from_softfloat(op_result.xcpt);
F[rd] = op_result.data;

PC = PC + 4;

return Retired();
