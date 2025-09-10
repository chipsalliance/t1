let rs1 : freg_index = UInt(GetRS1(instruction));
let rs2 : freg_index = UInt(GetRS2(instruction));
let rd : XREG_TYPE = UInt(GetRD(instruction));

let result : FloatCmpResult = f32_lt(F[rs1], F[rs2]);
set_fflags_from_softfloat(result.xcpt);

if result.hold then
  X[rd] = 1[31:0];
else
  X[rd] = Zeros(32);
end

PC = PC + 4;

return Retired();
