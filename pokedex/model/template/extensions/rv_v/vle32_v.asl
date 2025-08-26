// vle32.v vd, (rs1), vm
// load unit stride with EEW=32, optionally masked by vm
//
// NOTE: this instruction supports non-zero vstart

let vd: VREG_TYPE = UInt(GetRD(instruction));
let rs1: XREG_TYPE = UInt(GetRS1(instruction));
let vm : bit = GetVM(instruction);

if VTYPE.ill then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if vm == '0' && vd == 0 then
  // overlap with mask
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if invalid_emul(VTYPE, 32) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let emul = get_emul(VTYPE, 32);

if invalid_vreg(emul, vd) then
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
    let (data, result) = ReadMemory(addr, 32);

    if !result.is_ok then
      VSTART = idx[LOG2_VLEN-1:0];
      return result;
    end

    VRF_32[vd, idx] = data;
  end
end

ClearVSTART();

PC = PC + 4;

return Retired();
