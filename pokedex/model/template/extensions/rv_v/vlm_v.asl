// vlm.v vd, (rs1)
// mask unit stride with EEW=8, evl = ceil(vl/8)
//
// NOTE: this instruction supports non-zero vstart

let vd: VREG_TYPE = UInt(GetRD(instruction));
let rs1: XREG_TYPE = UInt(GetRS1(instruction));

if VTYPE.ill then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let evl = divCeil(VL, 8);
let vstart = UInt(VSTART);

if vstart > evl then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let src1 = X[rs1];

for idx = vstart to evl - 1 do
  let addr = src1 + idx;
  let (data, result) = ReadMemory(addr, 8);

  if !result.is_ok then
    VSTART = idx[LOG2_VLEN-1:0];
    return result;
  end

  VRF_8[vd, idx] = data;
end

ClearVSTART();

PC = PC + 4;

return Retired();
