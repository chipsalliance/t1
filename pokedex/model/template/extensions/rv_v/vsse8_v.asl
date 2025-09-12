// vsse8.v vs3, (rs1), rs2, vm
// store strided with EEW=8, rs2 is byte stride, optionally masked by vm
//
// NOTE: this instruction supports non-zero vstart
// NOTE: curently we do not handle rs2=x0 specially

let vs3: VREG_TYPE = UInt(GetRD(instruction));
let rs1: XREG_TYPE = UInt(GetRS1(instruction));
let rs2: XREG_TYPE = UInt(GetRS2(instruction));
let vm : bit = GetVM(instruction);

if VTYPE.ill then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let vreg_align = vreg_eew_alignment(VTYPE, 8);

if vreg_align == 0 then
  // emul is invalid
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if vs3 MOD vreg_align != 0 then
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
