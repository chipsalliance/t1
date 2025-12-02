// vslide1down.vx vd, vs2, rs1, vm
// eew(vd, vs2) = sew, w(rs1) = sew, rs1 is truncate/sext from X[rs1]
// vd[vl-1]  = rs1, vd[i] = vs2[i+1], optionally masked by vm, vm is mask for vd
func Execute_VSLIDE1DOWN_VX(instruction: bits(32)) => Result
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
  let rs1: XRegIdx = UInt(GetRS1(instruction));
  let vm: bit = GetVM(instruction);

  let vl: integer = VL;
  let sew: integer{8, 16, 32, 64} = VTYPE.sew;
  let vreg_align: integer{1, 2, 4, 8} = getAlign(VTYPE);

  if vm == '0' && vd == 0 then
    // vd overlap with mask
    return IllegalInstruction();
  end
  if vd MOD vreg_align != 0 then
    // vd is not aligned with lmul group
    return IllegalInstruction();
  end
  if vs2 MOD vreg_align != 0 then
    // vs1 is not aligned with lmul group
    return IllegalInstruction();
  end

  // vslide1down allow overlap vd with vs2

  case sew of
    when 8 => begin
      for idx = 0 to vl - 1 do
        if vm != '0' || V0_MASK[idx] then
          if idx+1 == vl then
            VRF_8[vd, idx] = SInt(X[rs1])[7:0];
          else
            VRF_8[vd, idx] = VRF_8[vs2, idx+1];
          end
        end
      end
    end

    when 16 => begin
      for idx = 0 to vl - 1 do
        if vm != '0' || V0_MASK[idx] then
          if idx+1 == vl then
            VRF_16[vd, idx] = SInt(X[rs1])[15:0];
          else
            VRF_16[vd, idx] = VRF_16[vs2, idx+1];
          end
        end
      end
    end

    when 32 => begin
      for idx = 0 to vl - 1 do
        if vm != '0' || V0_MASK[idx] then
          if idx+1 == vl then
            VRF_32[vd, idx] = SInt(X[rs1])[31:0];
          else
            VRF_32[vd, idx] = VRF_32[vs2, idx+1];
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