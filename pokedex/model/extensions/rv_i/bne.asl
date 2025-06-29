let bimm_hi : bits(7) = GetArg_BIMM12HI(instruction);
let bimm_lo : bits(5) = GetArg_BIMM12LO(instruction);
let bimm    : bits(32) = SignExtend([bimm_hi, bimm_lo], 32);

let rs1_idx : integer{0..31} = UInt(GetArg_RS1(instruction));
let rs2_idx : integer{0..31} = UInt(GetArg_RS2(instruction));

if X[rs1_idx] != X[rs2_idx] then
  let target : bits(32) = PC + bimm;
  if target[1:0] != '00' then
    return Exception(CAUSE_MISALIGNED_FETCH, target);
  end

  PC = target;
else
  PC = PC + 4;
end

return Retired();
