// vgatherei16.vv vd, vs2, vs1, vm
// eew(vd, vs2) = sew, eew(vs1) = 16
// vd[i] = vs2[vs1[i]], optionally masked by vm
// NOTE: read from vs2 may beyond vl
func Execute_VRGATHEREI16_VV(instruction: bits(32)) => Result
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
  let vs1: VRegIdx = UInt(GetRS1(instruction));
  let vm: bit = GetVM(instruction);

  let vlmax: integer = VLMAX;
  let vl: integer = VL;
  let sew: integer{8, 16, 32, 64} = VTYPE.sew;
  let vreg_align: integer{1, 2, 4, 8} = getAlign(VTYPE);
  let (vs1_align, valid) = getEewAlign(VTYPE, 16);

  if !valid then
    // elmul for vs1 is invalid
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
  if vs1 MOD vs1_align != 0 then
    // vs1 is not aligned with lmul group
    return IllegalInstruction();
  end
  if vd == vs2 then
    // vgather vd cannot overlap with source
    return IllegalInstruction();
  end
  if !(vd + vreg_align <= vs1 || vs1 + vs1_align <= vd) then
    // vgather vd cannot overlap with source (v1)
    return IllegalInstruction();
  end

  if FALSE then
    // TODO: when eew(vs1) != eew(vs2), they cannot overlap?
    // FIXME: spike seems does not perform this check?
    return IllegalInstruction();
  end

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
    
    when 64 => Todo("support sew=64");

    otherwise => Unreachable();
  end

  logWrite_VREG_elmul(vd, vreg_align);

  makeDirty_VS();
  clear_VSTART();
  PC = PC + 4;
  return Retired();
end
