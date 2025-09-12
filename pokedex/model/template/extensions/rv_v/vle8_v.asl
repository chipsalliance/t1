// vle8.v vd, (rs1), vm
// load unit stride with EEW=8, optionally masked by vm
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

let vreg_align = vreg_eew_alignment(VTYPE, 8);

if vreg_align == 0 then
  // emul is invalid
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if vd MOD vreg_align != 0 then
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
    let (data, result) = ReadMemory(addr, 8);

    if !result.is_ok then
      VSTART = idx[LOG2_VLEN-1:0];
      return result;
    end

    VRF_8[vd, idx] = data;
  end
end

ClearVSTART();

PC = PC + 4;

return Retired();
