let rs1 : freg_index = UInt(GetRS1(instruction));
let rs2 : freg_index = UInt(GetRS2(instruction));
let rs3 : freg_index = UInt(GetRS3(instruction));
let rd : freg_index = UInt(GetRD(instruction));

let rm_result : RM_Result = RM_from_bits(GetRM(instruction));
if !rm_result.valid then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, instruction);
end

// update signed bit
var nsrc1 : bits(32) = F[rs1];
nsrc1[31] = nsrc1[31] XOR '1';

var nsrc3 : bits(32) = F[rs3];
nsrc3[31] = nsrc3[31] XOR '1';

// FNMADD = -(A*B) - C = (-A) * B + (-C)
let op_result : FloatOpResult = f32_multiply_add(nsrc1, F[rs2], nsrc3, rm_result.mode);
F[rd] = op_result.data;
set_fflags_from_softfloat(op_result.xcpt);

PC = PC + 4;
return Retired();
