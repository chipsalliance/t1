// vslidedown.vi vd, vs2, uimm, vm
// eew(vd, vs2) = sew
// vd[i] = vs2[i+uimm], optionally masked by vm, vm is mask for vd
// NOTE: read from vs2 could be beyond vl
func Execute_VSLIDEDOWN_VI(instruction: bits(32)) => Result
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
  let uimm5: bits(5) = GetRS1(instruction);
  let vm: bit = GetVM(instruction);

  let vlmax: integer = VLMAX;
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

  // vslidedown allow overlap vd with vs2

  let offset = UInt(uimm5);

  case sew of
    when 8 => begin
      for idx = 0 to vl - 1 do
        if vm != '0' || V0_MASK[idx] then
          var src : bits(8) = Zeros(8);
          if idx + offset < vlmax then
            src = VRF_8[vs2, idx + offset];
          end
          
          VRF_8[vd, idx] = src;
        end
      end
    end

    when 16 => begin
      for idx = 0 to vl - 1 do
        if vm != '0' || V0_MASK[idx] then
          var src : bits(16) = Zeros(16);
          if idx + offset < vlmax then
            src = VRF_16[vs2, idx + offset];
          end
          
          VRF_16[vd, idx] = src;
        end
      end
    end

    when 32 => begin
      for idx = 0 to vl - 1 do
        if vm != '0' || V0_MASK[idx] then
          var src : bits(32) = Zeros(32);
          if idx + offset < vlmax then
            src = VRF_32[vs2, idx + offset];
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