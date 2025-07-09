let imm : bits(12) = GetIMM(instruction);
let rs1 : integer{0 .. 31} = UInt(GetRS1(instruction));
let rd : integer{0 .. 31} = UInt(GetRD(instruction));

let src1 : integer = SInt(X[rs1]);
let imm_ext : integer = SInt(SignExtend(imm, 32));
if src1 < imm_ext then
  // convert integer 1 to 32-bits representation
  X[rd] = 1[31:0];
else
  X[rd] = Zeros(32);
end

PC = PC + 4;

return Retired();
