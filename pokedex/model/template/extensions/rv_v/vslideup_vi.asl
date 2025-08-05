// vslideup.vi vd, vs2, uimm, vm
// eew(vd, vs2) = sew
// vd[i+uimm] = vs2[i], optionally masked by vm, vm is mask for vd

let vd : VREG_TYPE = UInt(GetRD(instruction));
let vs2 : VREG_TYPE = UInt(GetRS2(instruction));
let uimm : bits(5) = GetRS1(instruction);
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

if vm == '0' && vd == 0 then
  // overlap with mask
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

if vd == vs2 then
  // vslideup cannot overlap vd with vs
  return Exception(CAUSE_ILLEGAL_INSTRUCTION, Zeros(32));
end

let offset = UInt(uimm);

// always undisturbed
// TOOD: support agnostic to reduce VRF read
case sew of
  when 8 => begin
    for idx = 0 to vl - 1 do
      // vd[idx] is unchanged for idx < offset 
      if idx >= offset then
        if vm != '0' || V0_MASK[idx] then
          VRF_8[vd, idx] = VRF_8[vs2, idx - offset];
        end
      end
    end
  end

  when 16 => begin
    for idx = 0 to vl - 1 do
      // vd[idx] is unchanged for idx < offset 
      if idx >= offset then
        if vm != '0' || V0_MASK[idx] then
          VRF_16[vd, idx] = VRF_16[vs2, idx - offset];
        end
      end
    end
  end

  when 32 => begin
    for idx = 0 to vl - 1 do
      // vd[idx] is unchanged for idx < offset 
      if idx >= offset then
        if vm != '0' || V0_MASK[idx] then
          VRF_32[vd, idx] = VRF_32[vs2, idx - offset];
        end
      end
    end
  end
  
  otherwise => assert FALSE; // TODO
end

ClearVSTART();

PC = PC + 4;

return Retired();
