// vlse32.v vd, (rs1), rs2, vm
// load strided with EEW=32, rs2 is byte stride, optionally masked by vm
//
// NOTE: this instruction supports non-zero vstart
// NOTE: curently we do not handle rs2=x0 specially

let vd: VREG_TYPE = UInt(GetRD(instruction));
let rs1: XREG_TYPE = UInt(GetRS1(instruction));
let rs2: XREG_TYPE = UInt(GetRS2(instruction));
let vm : bit = GetVM(instruction);

if VTYPE.ill then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if vm == '0' && vd == 0 then
  // overlap with mask
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let vreg_align = vreg_eew_alignment(VTYPE, 32);

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
let src2 = X[rs2];

for idx = vstart to vl - 1 do
  if vm != '0' || V0_MASK[idx] then
    let addr = src1 + src2 * idx;
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
