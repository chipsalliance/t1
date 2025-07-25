// vmerge.vxm vd, vs2, rs1, v0
// eew(vd, vs2) = sew, w(rs1) = sew, rs1 is sext/truncated from X[rs1]
// compute vd[i] = v0m[i] ? rs1 : v2[i]

let vd : VREG_TYPE = UInt(GetRD(instruction));
let vs2 : VREG_TYPE = UInt(GetRS2(instruction));
let rs1 : XREG_TYPE = UInt(GetRS1(instruction));

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
    let src1 = SInt(X[rs1])[7:0];

    for idx = 0 to vl - 1 do
      if V0_MASK[idx] then
        VRF_8[vd, idx] = src1;
      else
        VRF_8[vd, idx] = VRF_8[vs2, idx];
      end
    end
  end

  when 16 => begin
    let src1 = SInt(X[rs1])[15:0];

    for idx = 0 to vl - 1 do
      if V0_MASK[idx] then
        VRF_16[vd, idx] = src1;
      else
        VRF_16[vd, idx] = VRF_16[vs2, idx];
      end
    end
  end

  when 32 => begin
    let src1 = SInt(X[rs1])[31:0];

    for idx = 0 to vl - 1 do
      if V0_MASK[idx] then
        VRF_32[vd, idx] = src1;
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
