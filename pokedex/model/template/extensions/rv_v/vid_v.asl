// vid_v vd, vm
// eew(vd) = sew, optionally masked by vm
//

let vd : VREG_TYPE = UInt(GetRD(instruction));
let vs2 : VREG_TYPE = UInt(GetRS2(instruction));
let vm : bit = GetVM(instruction);

if VTYPE.ill then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if UInt(VSTART) != 0 then
  // uarch
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let sew = VTYPE.sew;
let lmul = VTYPE.lmul;
let vl = VL;

if invalid_vreg(lmul, vd) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if vm == '0' && vd == 0 then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

// always undisturbed
// TOOD: support agnostic to reduce VRF read
case sew of
  when 8 => begin
    for idx = 0 to vl - 1 do
      if vm != '0' || V0_MASK[idx] then
        VRF_8[vd, idx] = idx[7:0];
      end
    end
  end

  when 16 => begin
    for idx = 0 to vl - 1 do
      if vm != '0' || V0_MASK[idx] then
        VRF_16[vd, idx] = idx[15:0];
      end
    end
  end

  when 32 => begin
    for idx = 0 to vl - 1 do
      if vm != '0' || V0_MASK[idx] then
        VRF_32[vd, idx] = idx[31:0];
      end
    end
  end

  otherwise => Unreachable(); // TODO
end

ClearVSTART();

PC = PC + 4;

return Retired();
