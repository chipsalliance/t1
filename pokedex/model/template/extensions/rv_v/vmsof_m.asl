// vmsof_m vd, vs2, vm
// eew(vd, vs2) = 1, vd and vs2 are masks, optionally masked by vm
//

let vd : VREG_TYPE = UInt(GetRD(instruction));
let vs2 : VREG_TYPE = UInt(GetRS2(instruction));
let vm : bit = GetVM(instruction);

if VTYPE.ill then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if UInt(VSTART) != 0 then
  // explicitly required by the instruction
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if vm == '0' && vd == 0 then
  // the instruction explicitly says vd can not overlap with vm
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if vd == vs2 then
  // the instruction explicitly says vd can not overlap with vs2
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let vl = VL;

var before_first = TRUE;

for idx = 0 to vl - 1 do
  if vm != '0' || V0_MASK[idx] then
    var res : bit;

    if before_first then
      if !((VRF_MASK[vs2, idx]) as boolean) then
        res = '0'; // before first one is '0'
      else
        before_first = FALSE;
        res = '1'; // the exact first one is '1'
      end
    else
      res = '0'; // after frist one is '0'
    end

    VRF_MASK[vd, idx] = res;
  end
end

ClearVSTART();

PC = PC + 4;

return Retired();
