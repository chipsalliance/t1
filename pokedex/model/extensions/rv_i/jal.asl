let rd : integer{0..31} = UInt(GetArg_RD(instruction));
let ret_pc  : bits(32) = PC + 4;

let jimm : bits(32) = SignExtend(GetArg_JIMM20(instruction), 32);
PC = PC + jimm;

X[rd] = ret_pc;
