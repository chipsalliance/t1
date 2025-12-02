// vmv.v.v vd, vs1
// eew(vd, vs1) = sew
// compute vd[i] = vs1[i]
func Execute_VMV_V_V(instruction: bits(32)) => Result
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
  let vs1: VRegIdx = UInt(GetRS1(instruction));

  let vl: integer = VL;
  let sew: integer{8, 16, 32, 64} = VTYPE.sew;
  let vreg_align: integer{1, 2, 4, 8} = getAlign(VTYPE);

  if vd MOD vreg_align != 0 then
    // vd is not aligned with lmul group
    return IllegalInstruction();
  end
  if vs1 MOD vreg_align != 0 then
    // vs2 is not aligned with elmul group
    return IllegalInstruction();
  end

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
    
    when 64 => Todo("support sew=64");

    otherwise => Unreachable();
  end

  logWrite_VREG_elmul(vd, vreg_align);

  makeDirty_VS();
  clear_VSTART();
  PC = PC + 4;
  return Retired();
end
