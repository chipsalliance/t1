// vmv.v.v vd, vs2
// eew(vd, vs2) = sew
// move the whole register, vd = vs2, emul = 1

let vd : VREG_TYPE = UInt(GetRD(instruction));
let vs2 : VREG_TYPE = UInt(GetRS2(instruction));

if VTYPE.ill then
  // the spec does say it depends on vtype
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

// uarch
if UInt(VSTART) != 0 then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

__VRF[vd * VLEN +: VLEN] = __VRF[vs2 * VLEN +: VLEN];

ClearVSTART();

PC = PC + 4;

return Retired();