// viota_m vd, vs2, vm
// eew(vd) = sew, eew(vs2) = 1, vs2 are masks, optionally masked by vm
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

let sew = VTYPE.sew;
let lmul = VTYPE.lmul;
let vlmax = VLMAX;
let vl = VL;

if invalid_vreg(lmul, vd) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if vm == '0' && vd == 0 then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if invalid_overlap_src_m_1(lmul, vs2, vd) then
  // though vd is destination, the spec says any overlap is forbidden
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

var sum : integer = 0;

// always undisturbed
// TOOD: support agnostic to reduce VRF read
case sew of
  when 8 => begin
    for idx = 0 to vl - 1 do
      if vm != '0' || V0_MASK[idx] then
        VRF_8[vd, idx] = sum[7:0];

        if (VRF_MASK[vs2, idx]) as boolean then
          sum = sum + 1;
        end
      end
    end
  end

  when 16 => begin
    for idx = 0 to vl - 1 do
      if vm != '0' || V0_MASK[idx] then
        VRF_16[vd, idx] = sum[15:0];

        if (VRF_MASK[vs2, idx]) as boolean then
          sum = sum + 1;
        end
      end
    end
  end

  when 32 => begin
    for idx = 0 to vl - 1 do
      if vm != '0' || V0_MASK[idx] then
        VRF_32[vd, idx] = sum[31:0];

        if (VRF_MASK[vs2, idx]) as boolean then
          sum = sum + 1;
        end
      end
    end
  end

  otherwise => Unreachable(); // TODO
end

ClearVSTART();

PC = PC + 4;

return Retired();
