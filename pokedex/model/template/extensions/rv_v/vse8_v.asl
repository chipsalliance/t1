// vse8.v vs3, (rs1), vm
// store unit stride with EEW=8, optionally masked by vm
//
// NOTE: this instruction supports non-zero vstart

let vs3: VREG_TYPE = UInt(GetRD(instruction));
let rs1: XREG_TYPE = UInt(GetRS1(instruction));
let vm : bit = GetVM(instruction);

if VTYPE.ill then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end


if invalid_emul(VTYPE, 8) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let emul = get_emul(VTYPE, 8);

if invalid_vreg(emul, vs3) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let vl = VL;
let vstart = UInt(VSTART);

if vstart > vl then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let src1 = X[rs1];

for idx = vstart to vl - 1 do
  if vm != '0' || V0_MASK[idx] then
    let addr = src1 + idx;
    let data : bits(8) = VRF_8[vs3, idx];
    let result = WriteMemory(addr, data);

    if !result.is_ok then
      VSTART = idx[LOG2_VLEN-1:0];
      return result;
    end
  end
end

ClearVSTART();

PC = PC + 4;

return Retired();
