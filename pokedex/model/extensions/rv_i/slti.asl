let imm : bits(32) = SignExtend(GetArg_IMM12(instruction), 32);
let rs1 : integer{0 .. 31} = UInt(GetArg_RS1(instruction));
let rd : integer{0 .. 31} = UInt(GetArg_RD(instruction));

let rs1_value : integer = SInt(X[rs1]);
let imm_value : integer = SInt(imm);
if rs1_value < imm_value then
  X[rd] = ZeroExtend('0001', 32);
else
  X[rd] = Zeros(32);
end

PC = PC + 4;
