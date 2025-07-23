// vxor.vx vd, vs2, imm, vm
// eew(vd, vs2) = sew
// compute vd = vs2 ^ sext(imm), optionally masked by vm

let vd : VREG_TYPE = UInt(GetRD(instruction));
let vs2 : VREG_TYPE = UInt(GetRS2(instruction));
let imm : bits(5) = GetRS1(instruction);
let vm : bit = GetVM(instruction);

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
    let imm_sext = SInt(imm)[7:0];

    for idx = 0 to vl - 1 do
      if vm == '0' && V0_MASK[idx] then
        let src2 = VRF_8[vs2, idx];

        let res = src2 XOR imm_sext;

        VRF_8[vd, idx] = res;
      end
    end
  end

  when 16 => begin
    let imm_sext = SInt(imm)[15:0];

    for idx = 0 to vl - 1 do
      if vm == '0' && V0_MASK[idx] then
        let src2 = VRF_16[vs2, idx];

        let res = src2 XOR imm_sext;

        VRF_16[vd, idx] = res;
      end
    end
  end

  when 32 => begin
    let imm_sext = SInt(imm)[31:0];

    for idx = 0 to vl - 1 do
      if vm == '0' && V0_MASK[idx] then
        let src2 = VRF_32[vs2, idx];

        let res = src2 XOR imm_sext;

        VRF_32[vd, idx] = res;
      end
    end
  end
  
  otherwise => assert FALSE; // TODO
end

ClearVSTART();

PC = PC + 4;

return Retired();
