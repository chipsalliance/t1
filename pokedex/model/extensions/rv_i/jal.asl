let rd : integer{0..31} = UInt(GetArg_RD(instruction));
let ret_pc  : bits(32) = PC + 4;

var offset : bits(21) = Zeros(21);
offset[20] = instruction[31];
offset[10:1] = instruction[30:21];
offset[11] = instruction[20];
offset[19:12] = instruction[19:12];

let target : bits(32) = PC + SignExtend(offset, 32);
if target[1] != '0' then
  return Exception(CAUSE_MISALIGNED_FETCH, target);
end

PC = target;

X[rd] = ret_pc;

return Retired();
