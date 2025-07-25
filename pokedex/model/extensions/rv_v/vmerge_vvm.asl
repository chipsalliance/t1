// vmerge.v.v vd, vs2, vs1, v0
// eew(vd, vs2, vs1) = sew
// compute vd[i] = v0m[i] ? vs1[i] : v2[i]

let vd : VREG_TYPE = UInt(GetRD(instruction));
let vs2 : VREG_TYPE = UInt(GetRS2(instruction));
let vs1 : VREG_TYPE = UInt(GetRS1(instruction));

if VTYPE.ill then
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

if invalid_vreg(lmul, vs1) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if vd == 0 then
  // overlap with mask
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

// uarch
if UInt(VSTART) != 0 then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

// always undisturbed
// TOOD: support agnostic to reduce VRF read
case sew of
  when 8 => begin
    for idx = 0 to vl - 1 do
      if V0_MASK[idx] then
        VRF_8[vd, idx] = VRF_8[vs1, idx];
      else
        VRF_8[vd, idx] = VRF_8[vs2, idx];
      end
    end
  end

  when 16 => begin
    for idx = 0 to vl - 1 do
      if V0_MASK[idx] then
        VRF_16[vd, idx] = VRF_16[vs1, idx];
      else
        VRF_16[vd, idx] = VRF_16[vs2, idx];
      end
    end
  end

  when 32 => begin
    for idx = 0 to vl - 1 do
      if V0_MASK[idx] then
        VRF_32[vd, idx] = VRF_32[vs1, idx];
      else
        VRF_32[vd, idx] = VRF_32[vs2, idx];
      end
    end
  end
  
  otherwise => assert FALSE; // TODO
end

ClearVSTART();

PC = PC + 4;

return Retired();
