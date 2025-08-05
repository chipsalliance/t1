// vcompress_vm vd, vs2, vs1
// eew(vd, vs2) = sew, eew(vs1) = 1, vs1 are masks
//

let vd : VREG_TYPE = UInt(GetRD(instruction));
let vs2 : VREG_TYPE = UInt(GetRS2(instruction));
let vs1 : VREG_TYPE = UInt(GetRS2(instruction));

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

if invalid_vreg(lmul, vs2) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if vd == vs2 then
  // the instruction requires vd does not overlap with vs2
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if invalid_overlap_src_m_1(lmul, vs1, vd) then
  // though vd is destination, the spec says any overlap is forbidden
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if invalid_overlap_src_m_1(lmul, vs1, vs2) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

var vd_idx : integer = 0;

// always undisturbed
// TOOD: support agnostic to reduce VRF read
case sew of
  when 8 => begin
    for idx = 0 to vl - 1 do
      if (VRF_MASK[vs1, idx]) as boolean then
        VRF_8[vd, vd_idx] = VRF_8[vs2, idx];

        vd_idx = vd_idx + 1;
      end
    end
  end

  when 16 => begin
    for idx = 0 to vl - 1 do
      if (VRF_MASK[vs1, idx]) as boolean then
        VRF_16[vd, vd_idx] = VRF_16[vs2, idx];

        vd_idx = vd_idx + 1;
      end
    end
  end

  when 32 => begin
    for idx = 0 to vl - 1 do
      if (VRF_MASK[vs1, idx]) as boolean then
        VRF_32[vd, vd_idx] = VRF_32[vs2, idx];

        vd_idx = vd_idx + 1;
      end
    end
  end

  otherwise => Unreachable(); // TODO
end

ClearVSTART();

PC = PC + 4;

return Retired();
