let rs1 : freg_index = UInt(GetRS1(instruction));
let rs2 : freg_index = UInt(GetRS2(instruction));
let rd : freg_index = UInt(GetRD(instruction));

let op_result : FloatOpResult = f32_max(F[rs1], F[rs2]);
F[rd] = op_result.data;
set_fflags_from_softfloat(op_result.xcpt);

PC = PC + 4;

return Retired();
