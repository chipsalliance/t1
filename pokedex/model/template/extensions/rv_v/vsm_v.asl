// vsm.v vs3, (rs1)
// mask unit stride with EEW=8, evl = ceil(vl/8)
//
// NOTE: this instruction supports non-zero vstart

let vs3: VREG_TYPE = UInt(GetRD(instruction));
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
  let data : bits(8) = VRF_8[vs3, idx];
  let result = WriteMemory(addr, data);

  if !result.is_ok then
    VSTART = idx[LOG2_VLEN-1:0];
    return result;
  end
end

ClearVSTART();

PC = PC + 4;

return Retired();
