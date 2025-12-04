// vmerge.v.i vd, vs2, imm, v0
// eew(vd, vs2) = sew, w(imm) = sew, imm is sext from imm5
// compute vd[i] = v0m[i] ? imm : v2[i]
func Execute_VMERGE_VIM(instruction: bits(32)) => Result
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
  let imm5: bits(5) = GetRS1(instruction);

  let vl: integer = VL;
  let sew: integer{8, 16, 32, 64} = VTYPE.sew;
  let vreg_align: integer{1, 2, 4, 8} = getAlign(VTYPE);

  if vd == 0 then
    // overlap with mask
    return IllegalInstruction();
  end
  if vd MOD vreg_align != 0 then
    // vd is not aligned with lmul group
    return IllegalInstruction();
  end
  if vs2 MOD vreg_align != 0 then
    // vs2 is not aligned with lmul group
    return IllegalInstruction();
  end

  case sew of
    when 8 => begin
      let src1 = SInt(imm5)[7:0];

      for idx = 0 to vl - 1 do
        if V0_MASK[idx] then
          VRF_8[vd, idx] = src1;
        else
          VRF_8[vd, idx] = VRF_8[vs2, idx];
        end
      end
    end

    when 16 => begin
      let src1 = SInt(imm5)[15:0];

      for idx = 0 to vl - 1 do
        if V0_MASK[idx] then
          VRF_16[vd, idx] = src1;
        else
          VRF_16[vd, idx] = VRF_16[vs2, idx];
        end
      end
    end

    when 32 => begin
      let src1 = SInt(imm5)[31:0];

      for idx = 0 to vl - 1 do
        if V0_MASK[idx] then
          VRF_32[vd, idx] = src1;
        else
          VRF_32[vd, idx] = VRF_32[vs2, idx];
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