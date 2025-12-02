// vcompress_vm vd, vs2, vs1
// eew(vd, vs2) = sew, eew(vs1) = 1, vs1 are masks
func Execute_VCOMPRESS_VM(instruction: bits(32)) => Result
begin
  if !isEnabled_VS() then
    return IllegalInstruction();
  end
  if VTYPE.ill then
    return IllegalInstruction();
  end
  if !IsZero(VSTART) then
    // explicitly required by the instruction
    return IllegalInstruction();
  end

  let vd: VRegIdx = UInt(GetRD(instruction));
  let vs2: VRegIdx = UInt(GetRS2(instruction));
  let vs1: VRegIdx = UInt(GetRS1(instruction));

  let vl: integer = VL;
  let sew: integer{8, 16, 32, 64} = VTYPE.sew;
  let vreg_align: integer{1, 2, 4, 8} = getAlign(VTYPE);

  if vd MOD vreg_align != 0 then
    // vd is not aligned with lmul group
    return IllegalInstruction();
  end
  if vs2 MOD vreg_align != 0 then
    // vs2 is not aligned with elmul group
    return IllegalInstruction();
  end
  if vd == vs2 then
    // The spec says vd must not overlap with vs2
    return IllegalInstruction();
  end
  if vd <= vs1 && vs1 < vd + vreg_align then
    // The spec says vd must not overlap with vs1
    return IllegalInstruction();
  end

  // NOTE: vs1 is a mask source, allowed to overlap with vs2.
  //       Confirmed by reading spike source code.

  var vd_idx : integer = 0;

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

    when 64 => Todo("support sew=64");

    otherwise => Unreachable();
  end

  logWrite_VREG_elmul(vd, vreg_align);

  makeDirty_VS();
  clear_VSTART();
  PC = PC + 4;
  return Retired();
end
