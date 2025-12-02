// vid_v vd, vm
// eew(vd) = sew, optionally masked by vm
func Execute_VID_V(instruction: bits(32)) => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end
  if VTYPE.ill then
    return IllegalInstruction();
  end
  if !IsZero(VSTART) then
    return IllegalInstruction();
  end

  let vd: VRegIdx = UInt(GetRD(instruction));
  let vs2: VRegIdx = UInt(GetRS2(instruction));
  let vm: bit = GetVM(instruction);

  let vl: integer = VL;
  let sew: integer{8, 16, 32, 64} = VTYPE.sew;
  let vreg_align: integer{1, 2, 4, 8} = getAlign(VTYPE);

  if vm == '0' && vd == 0 then
    // overlap with mask
    return IllegalInstruction();
  end
  if vd MOD vreg_align != 0 then
    // vd is not aligned with lmul group
    return IllegalInstruction();
  end

  // always undisturbed
  // TOOD: support agnostic to reduce VRF read
  case sew of
    when 8 => begin
      for idx = 0 to vl - 1 do
        if vm != '0' || V0_MASK[idx] then
          VRF_8[vd, idx] = idx[7:0];
        end
      end
    end

    when 16 => begin
      for idx = 0 to vl - 1 do
        if vm != '0' || V0_MASK[idx] then
          VRF_16[vd, idx] = idx[15:0];
        end
      end
    end

    when 32 => begin
      for idx = 0 to vl - 1 do
        if vm != '0' || V0_MASK[idx] then
          VRF_32[vd, idx] = idx[31:0];
        end
      end
    end

    when 64 => Todo("support sew=64");

    otherwise => Unreachable();
  end

  logWrite_VREG_elmul(vd, vreg_align);

  makeDirty_VS();
  clear_VSTART();
  PC = PC + 4;
  return Retired();
end
