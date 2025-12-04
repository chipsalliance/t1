// viota_m vd, vs2, vm
// eew(vd) = sew, eew(vs2) = 1, vs2 are masks, optionally masked by vm
func Execute_VIOTA_M(instruction: bits(32)) => Result
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

  if vd <= vs2 && vs2 < vd + vreg_align then
    // The spec says vd must not overlap with vs2
    return IllegalInstruction();
  end

  var sum : integer = 0;

  case sew of
    when 8 => begin
      for idx = 0 to vl - 1 do
        if vm != '0' || V0_MASK[idx] then
          VRF_8[vd, idx] = sum[7:0];

          if (VRF_MASK[vs2, idx]) as boolean then
            sum = sum + 1;
          end
        end
      end
    end

    when 16 => begin
      for idx = 0 to vl - 1 do
        if vm != '0' || V0_MASK[idx] then
          VRF_16[vd, idx] = sum[15:0];

          if (VRF_MASK[vs2, idx]) as boolean then
            sum = sum + 1;
          end
        end
      end
    end

    when 32 => begin
      for idx = 0 to vl - 1 do
        if vm != '0' || V0_MASK[idx] then
          VRF_32[vd, idx] = sum[31:0];

          if (VRF_MASK[vs2, idx]) as boolean then
            sum = sum + 1;
          end
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