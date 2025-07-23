// vwadd.wv vd, vs2, vs1, vm
// eew(vd, vs2) = 2 * sew, eew(vs1) = sew
// compute vd = vs2 + zext(vs1), optionally masked by vm

let vd : VREG_TYPE = UInt(GetRD(instruction));
let vs2 : VREG_TYPE = UInt(GetRS2(instruction));
let vs1 : VREG_TYPE = UInt(GetRS1(instruction));
let vm : bit = GetVM(instruction);

if VTYPE.ill then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let sew = VTYPE.sew;
let lmul = VTYPE.lmul;
let vlmax = VLMAX;
let vl = VL;

if invalid_double_lmul(lmul) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if invalid_vreg_2sew(lmul, vd) then
  // eew(vd) = 2 * sew
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if invalid_vreg_2sew(lmul, vs2) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if invalid_vreg(lmul, vs1) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if invalid_overlap_dst_src_2_1(lmul, vd, vs1) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if invalid_overlap_src_2_1(lmul, vs2, vs1) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if vm == '0' && vd == 0 then
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
      if vm == '0' && V0_MASK[idx] then
        let src2 = VRF_16[vs2, idx];
        let src1 = VRF_8[vs1, idx];

        let res = src2 + UInt(src1)[15:0];

        VRF_16[vd, idx] = res[15:0];
      end
    end
  end

  when 16 => begin
    for idx = 0 to vl - 1 do
      if vm == '0' && V0_MASK[idx] then
        let src2 = VRF_32[vs2, idx];
        let src1 = VRF_16[vs1, idx];

        let res = src2 + UInt(src1)[31:0];

        VRF_32[vd, idx] = res[31:0];
      end
    end
  end

  when 32 => begin
    assert ELEN == 32;
    return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
  end
  
  otherwise => assert FALSE; // TODO
end

ClearVSTART();

PC = PC + 4;

return Retired();
