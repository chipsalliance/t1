let rd : integer{0..31} = UInt(GetRD(instruction));
let ret_pc  : bits(32) = PC + 4;

let offset : bits(21) = [GetJIMM(instruction), '0'];
let target : bits(32) = PC + SignExtend(offset, 32);

PC = target;

X[rd] = ret_pc;

return Retired();
