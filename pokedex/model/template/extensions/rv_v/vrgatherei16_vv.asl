// vgatherei16.vv vd, vs2, vs1, vm
// eew(vd, vs2) = sew, eew(vs1) = 16
// vd[i] = vs2[vs1[i]], optionally masked by vm
// NOTE: read from vs2 may beyond vl

let vd : VREG_TYPE = UInt(GetRD(instruction));
let vs2 : VREG_TYPE = UInt(GetRS2(instruction));
let vs1 : VREG_TYPE = UInt(GetRS1(instruction));
let vm : bit = GetVM(instruction);

if VTYPE.ill then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

// uarch
if UInt(VSTART) != 0 then
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

let vreg_align_16 = vreg_eew_alignment(VTYPE, 16);

if vreg_align_16 == 0 then
  // emul(vs1) is invalid
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if vs1 MOD vreg_align_16 != 0 then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if invalid_vreg(lmul, vs1) then
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if vm == '0' && vd == 0 then
  // overlap with mask
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if vd == vs2 then
  // vgather vd cannot overlap with source
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if FALSE then
  // TODO: vgather vd cannot overlap with source
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if FALSE then
  // TODO: when eew(vs1) != eew(vs2), they cannot overlap
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

// always undisturbed
// TODO: support agnostic to reduce VRF read
case sew of
  when 8 => begin
    for idx = 0 to vl - 1 do
      if vm != '0' || V0_MASK[idx] then
        let index = UInt(VRF_16[vs1, idx]);

        var src : bits(8) = Zeros(8);
        if index < vlmax then
          src = VRF_8[vs2, index];
        end

        VRF_8[vd, idx] = src;
      end
    end
  end

  when 16 => begin
    for idx = 0 to vl - 1 do
      if vm != '0' || V0_MASK[idx] then
        let index = UInt(VRF_16[vs1, idx]);

        var src : bits(16) = Zeros(16);
        if index < vlmax then
          src = VRF_16[vs2, index];
        end

        VRF_16[vd, idx] = src;
      end
    end
  end

  when 32 => begin
    for idx = 0 to vl - 1 do
      if vm != '0' || V0_MASK[idx] then
        let index = UInt(VRF_16[vs1, idx]);

        var src : bits(32) = Zeros(32);
        if index < vlmax then
          src = VRF_32[vs2, index];
        end

        VRF_32[vd, idx] = src;
      end
    end
  end
  
  otherwise => Unreachable(); // TODO
end

ClearVSTART();

PC = PC + 4;

return Retired();
