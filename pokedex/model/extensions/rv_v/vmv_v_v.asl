// vmv.v.v vd, vs1
// eew(vd, vs1) = sew
// compute vd[i] = vs1[i]

let vd : VREG_TYPE = UInt(GetRD(instruction));
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

if invalid_vreg(lmul, vs1) then
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
      VRF_8[vd, idx] = VRF_8[vs1, idx];
    end
  end

  when 16 => begin
    for idx = 0 to vl - 1 do
      VRF_16[vd, idx] = VRF_16[vs1, idx];
    end
  end

  when 32 => begin
    for idx = 0 to vl - 1 do
      VRF_32[vd, idx] = VRF_32[vs1, idx];
    end
  end
  
  otherwise => assert FALSE; // TODO
end

ClearVSTART();

PC = PC + 4;

return Retired();
